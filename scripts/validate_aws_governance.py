#!/usr/bin/env python3
"""Validate an implementation repository's AWS governance manifest."""

from __future__ import annotations

import argparse
import fnmatch
import json
import re
import subprocess
import sys
from datetime import date
from pathlib import Path, PurePosixPath
from typing import Any


POLICY_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SCHEMA = POLICY_ROOT / "catalog" / "aws-governance.schema.json"
DEFAULT_MANIFEST = ".cognimark/aws-governance.json"
REPOSITORY_PATTERN = re.compile(r"^cognimark/[a-z0-9]+(?:-[a-z0-9]+)*$")
NAME_PATTERN = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
CLOUDFORMATION_PATTERN = re.compile(r"^\s*AWSTemplateFormatVersion\s*:", re.MULTILINE)
IGNORED_DIRECTORIES = {
    ".aws-sam",
    ".git",
    "cdk.out",
    "dist",
    "node_modules",
    "target",
}


def load_json(path: Path) -> dict[str, Any]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("top-level value must be an object")
    return data


def schema_enums(schema: dict[str, Any]) -> dict[str, set[str]]:
    properties = schema["$defs"]["deployment"]["properties"]
    return {
        field: set(properties[field]["enum"])
        for field in ("system", "component", "managedBy", "dataClassification")
    }


def repository_files(root: Path) -> set[str]:
    try:
        result = subprocess.run(
            [
                "git",
                "-C",
                str(root),
                "ls-files",
                "--cached",
                "--others",
                "--exclude-standard",
                "-z",
            ],
            check=True,
            capture_output=True,
        )
        return {item.decode("utf-8") for item in result.stdout.split(b"\0") if item}
    except (FileNotFoundError, subprocess.CalledProcessError):
        return {
            path.relative_to(root).as_posix()
            for path in root.rglob("*")
            if path.is_file() and not any(part in IGNORED_DIRECTORIES for part in path.parts)
        }


def is_cloudformation_template(path: Path) -> bool:
    if path.suffix.lower() not in {".json", ".yaml", ".yml"}:
        return False
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return False
    if CLOUDFORMATION_PATTERN.search(text):
        return True
    if path.suffix.lower() == ".json":
        try:
            value = json.loads(text)
        except json.JSONDecodeError:
            return False
        return isinstance(value, dict) and "AWSTemplateFormatVersion" in value
    return False


def discovered_iac_sources(root: Path, files: set[str]) -> set[str]:
    discovered: set[str] = set()
    for relative in files:
        parts = PurePosixPath(relative).parts
        if any(part in IGNORED_DIRECTORIES for part in parts):
            continue
        if relative == "cdk.json" or relative.endswith(".tf"):
            discovered.add(relative)
            continue
        if len(parts) >= 3 and parts[0] == "copilot" and parts[-1] == "manifest.yml":
            discovered.add(relative)
            continue
        if is_cloudformation_template(root / relative):
            discovered.add(relative)
    return discovered


def safe_source_pattern(source: str) -> bool:
    path = PurePosixPath(source)
    return bool(source) and not source.startswith("/") and ".." not in path.parts


def validate_repository(
    root: Path,
    manifest_path: Path,
    schema_path: Path,
    expected_repository: str | None = None,
) -> tuple[list[str], dict[str, int]]:
    errors: list[str] = []
    stats = {"deployments": 0, "governedSources": 0, "detectedSources": 0}

    try:
        schema = load_json(schema_path)
    except (OSError, ValueError, json.JSONDecodeError, KeyError) as exc:
        return [f"{schema_path}: invalid governance schema: {exc}"], stats
    enums = schema_enums(schema)

    if not manifest_path.exists():
        return [f"{manifest_path}: governance manifest is missing"], stats
    try:
        manifest = load_json(manifest_path)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        return [f"{manifest_path}: invalid JSON: {exc}"], stats

    allowed_top_level = {"schemaVersion", "repository", "deployments"}
    extra_top_level = set(manifest) - allowed_top_level
    if extra_top_level:
        errors.append(f"manifest has unsupported fields: {sorted(extra_top_level)}")
    if manifest.get("schemaVersion") != 1:
        errors.append("schemaVersion must be 1")

    repository = manifest.get("repository")
    if not isinstance(repository, str) or not REPOSITORY_PATTERN.fullmatch(repository):
        errors.append("repository must match cognimark/<kebab-case-name>")
    elif expected_repository and repository != expected_repository:
        errors.append(f"repository is {repository}, expected {expected_repository}")

    deployments = manifest.get("deployments")
    if not isinstance(deployments, list) or not deployments:
        errors.append("deployments must be a non-empty array")
        return errors, stats
    stats["deployments"] = len(deployments)

    files = repository_files(root)
    ownership: dict[str, str] = {}
    seen_names: set[str] = set()
    allowed_deployment_fields = {
        "name",
        "system",
        "component",
        "managedBy",
        "dataClassification",
        "sources",
        "expiresAt",
    }

    for index, deployment in enumerate(deployments, start=1):
        prefix = f"deployment {index}"
        if not isinstance(deployment, dict):
            errors.append(f"{prefix} must be an object")
            continue
        extra_fields = set(deployment) - allowed_deployment_fields
        if extra_fields:
            errors.append(f"{prefix} has unsupported fields: {sorted(extra_fields)}")

        name = deployment.get("name")
        if not isinstance(name, str) or not NAME_PATTERN.fullmatch(name):
            errors.append(f"{prefix} name must be kebab case")
            name = f"#{index}"
        elif name in seen_names:
            errors.append(f"duplicate deployment name: {name}")
        seen_names.add(name)

        for field, allowed in enums.items():
            value = deployment.get(field)
            if value not in allowed:
                errors.append(f"deployment {name} has invalid {field}: {value}")

        expires_at = deployment.get("expiresAt")
        if deployment.get("managedBy") == "manual":
            if not isinstance(expires_at, str):
                errors.append(f"deployment {name} is manual and requires expiresAt")
            else:
                try:
                    date.fromisoformat(expires_at)
                except ValueError:
                    errors.append(f"deployment {name} expiresAt must be an ISO 8601 date")
        elif expires_at is not None:
            errors.append(f"deployment {name} may use expiresAt only when managedBy is manual")

        sources = deployment.get("sources")
        if not isinstance(sources, list) or not sources:
            errors.append(f"deployment {name} sources must be a non-empty array")
            continue
        if len(sources) != len(set(item for item in sources if isinstance(item, str))):
            errors.append(f"deployment {name} contains duplicate source patterns")

        for source in sources:
            if not isinstance(source, str) or not safe_source_pattern(source):
                errors.append(f"deployment {name} has unsafe source pattern: {source}")
                continue
            matches = sorted(file for file in files if fnmatch.fnmatchcase(file, source))
            if not matches:
                errors.append(f"deployment {name} source matches no files: {source}")
            for match in matches:
                previous = ownership.get(match)
                if previous:
                    errors.append(f"source {match} is owned by both {previous} and {name}")
                else:
                    ownership[match] = name

    discovered = discovered_iac_sources(root, files)
    stats["governedSources"] = len(ownership)
    stats["detectedSources"] = len(discovered)
    for source in sorted(discovered - ownership.keys()):
        errors.append(f"detected IaC source has no deployment owner: {source}")

    return errors, stats


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--schema", type=Path, default=DEFAULT_SCHEMA)
    parser.add_argument("--expected-repository")
    args = parser.parse_args()

    root = args.root.resolve()
    manifest = args.manifest or root / DEFAULT_MANIFEST
    errors, stats = validate_repository(root, manifest, args.schema, args.expected_repository)
    if errors:
        print("AWS governance validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        "Validated "
        f"{stats['deployments']} deployment units, "
        f"{stats['governedSources']} governed sources, and "
        f"{stats['detectedSources']} detected IaC sources."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
