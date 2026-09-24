# Cognimark shared HAPI runtime

Status, September 24, 2026: the approved source customization and reproducible
packaged runtime are implemented and locally tested. Production activation is
still pending; this branch does not change the deployed service by itself.

## Source ownership

| Repository | Responsibility |
| --- | --- |
| `cognimark/hapi-fhir` | The actual storage-library source changes and their tests |
| `cognimark/hapi-fhir-jpaserver-starter` | Version-pinned library consumption, application packaging and runtime tests |
| `cognimark/core-stack` | Infrastructure, explicit schema rollout, ingest/readback contracts and deployment |

The starter baseline is upstream tag `image/v8.12.0-1`, commit
`a01027b91ed07f41194524fd0dd59623905cbe2d`. The core library baseline is upstream
`v8.12.1`, commit `c8cc1dbbb2568610b68de626ea00c21080e0b8f8`.
These are the latest stable releases of the respective official repositories
verified on September 24, 2026. The starter's HAPI parent/dependencies are
updated to 8.12.1; it does not retain 8.12.0 libraries as a compatibility fallback.
Both forks use the development branch `cognimark/8.12.1-long-resource-ids`.

The Dockerfile builds core fork revision
`58f3e8a121c8cbc2545fafb147dc90114af107b8`. The model and storage modules use
version `8.12.1-cognimark.1`; all other HAPI libraries use official 8.12.1.
The starter is also versioned `8.12.1-cognimark.1`, not published under an upstream
artifact version. Maven dependency management selects the custom modules
throughout the application dependency graph, and packaging tests reject mixed
versions or duplicate storage classes. The builder and default runtime base
images are digest-pinned, and the OpenTelemetry agent download is SHA-256 checked.

The starter must build against an exact qualified core-fork revision. Do not
use a floating branch at image-build time, duplicate the source patch in Core,
or load loose replacement classes ahead of the packaged libraries. The intended
compatibility bound is 512 characters with the existing resource-ID alphabet;
original identities and references stay unchanged. The standard FHIR validator
continues to enforce the standard's 64-character rule.

## Historical branches

The `cognimark/8.4.0-aurora-pg16` and `cognimark/8.10.0-aurora-pg16` branches
remain historical source. Their September 12 retirement record describes the
old deployment and publishing path. It does not mean the newly introduced
shared source runtime is retired. Conversely, reusing this repository does not
reactivate the old per-database fleet, Aurora-specific adapter, image repository
or publishing role.

This branch starts from upstream 8.12 rather than automatically carrying all
historical customizations. Review any necessary packaging or dependency change
on its merits. Core remains the only owner of AWS runtime resources. Never
commit deployment secrets, private source inventories or patient fixtures here.

## Qualification

Follow the [core-fork release gates](https://github.com/cognimark/hapi-fhir/blob/cognimark/8.12.1-long-resource-ids/cognimark/README.md).
The prior disposable class-overlay experiment is evidence for the design, not
acceptance of a production artifact. The new source-built amd64 image passed
21 core-library tests, two starter dependency/column-contract tests, and a Core
ingest/materialization regression against real disposable PostgreSQL and HAPI:
1,535 passed, eight unrelated opt-in skips. This includes long-ID transactions,
reference searches, original-body/version readback and durable recovery.

Reproduce the library/package checks with `.github/workflows/cognimark-runtime.yml`,
or build the default Docker target. Core owns the actual HTTP/PostgreSQL tests
under `tests/shared/lib/fhir/test_hapi_batch_recovery_pg.py` and the TLS/populated
upgrade test `tests/test_hapi_tls_runtime_docker.py`. Run the latter with
`CORE_TEST_HAPI_TLS_DOCKER=1` and an exact `CORE_TEST_HAPI_LONG_ID_IMAGE` digest.
Fixtures contain only synthetic data and remove their owned containers/volumes.
The packaged-contract and upstream HTTP smoke workflows share the same pinned
source dependency build. Custom artifacts are not expected in Maven Central.
The upstream Docker Hub publishing job is restricted to the official repository;
this fork does not publish under `hapiproject/hapi` or re-enable historical AWS roles.

The populated official 8.12.0-to-fork upgrade test passed in 146.60 seconds using
the packaged amd64 runtime, production-shaped TLS and explicit Core-owned DDL.
It preserved old exact versions, rejected incorrect schemas atomically, safely
replayed migration, wrote/referenced IDs through 512 characters and restarted
HAPI/PostgreSQL with Hibernate schema validation rather than automatic DDL.

The ARM64 image also builds with all 23 library/package checks passing. Running
the same populated TLS upgrade/restart test under amd64-hosted QEMU passed in
947.56 seconds after allowing longer local-only emulation budgets. The initial
five-minute emulated startup attempt timed out and was not counted as a pass.
Both runs removed their owned test containers and volumes. These timings are
not native ARM64 performance measurements, and production timeouts are unchanged.

Native ARM64 deployment and real Epic/OCHIN-NP readback remain release gates. Do
not activate patient refresh or retire retained source evidence just because the
local architecture qualification succeeds.

This implementation, its tests and documentation were prepared with Codex assistance.
