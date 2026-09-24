# Cognimark shared HAPI runtime

Status, September 24, 2026: the existing starter fork is being reused for the
approved long-resource-ID implementation. This branch establishes its clean
8.12 baseline; the customized build and production activation are still pending.

## Source ownership

| Repository | Responsibility |
| --- | --- |
| `cognimark/hapi-fhir` | The actual storage-library source changes and their tests |
| `cognimark/hapi-fhir-jpaserver-starter` | Version-pinned library consumption, application packaging and runtime tests |
| `cognimark/core-stack` | Infrastructure, explicit schema rollout, ingest/readback contracts and deployment |

The starter baseline is upstream tag `image/v8.12.0-1`, commit
`a01027b91ed07f41194524fd0dd59623905cbe2d`. The core library baseline is upstream
`v8.12.0`, commit `7ab4c7c7c49281496faa1b3c2498a652457669ae`.
Both forks use the development branch `cognimark/8.12.0-long-resource-ids`.

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

Follow the [core-fork release gates](https://github.com/cognimark/hapi-fhir/blob/cognimark/8.12.0-long-resource-ids/cognimark/README.md).
The prior disposable class-overlay experiment is evidence for the design, not
acceptance of a production artifact. The new packaged runtime needs its own
tests, populated-database migration/restart checks and real-source verification.
Do not change the production image or activate patient refresh just because
this branch exists.

This fork setup and its documentation were prepared with Codex assistance.
