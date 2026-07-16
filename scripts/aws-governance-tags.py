#!/usr/bin/env python3
"""Render required AWS tags from the repository governance manifest."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / ".cognimark" / "aws-governance.json"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("deployment")
    parser.add_argument(
        "--format",
        choices=("cloudformation", "copilot", "aws-list", "json"),
        default="cloudformation",
    )
    args = parser.parse_args()

    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    matches = [item for item in manifest["deployments"] if item["name"] == args.deployment]
    if len(matches) != 1:
        raise SystemExit(f"Expected exactly one deployment named {args.deployment}")
    deployment = matches[0]

    tags = [
        ("cognimark:system", deployment["system"]),
        ("cognimark:component", deployment["component"]),
        ("cognimark:environment", "production"),
        ("cognimark:managed-by", deployment["managedBy"]),
        ("cognimark:repository", manifest["repository"]),
        ("cognimark:data-classification", deployment["dataClassification"]),
    ]

    if args.format == "cloudformation":
        print("\n".join(f"{key}={value}" for key, value in tags))
    elif args.format == "copilot":
        print(",".join(f"{key}={value}" for key, value in tags))
    elif args.format == "aws-list":
        print("\n".join(f"Key={key},Value={value}" for key, value in tags))
    else:
        print(json.dumps(dict(tags), separators=(",", ":"), sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

