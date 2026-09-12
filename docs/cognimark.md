# Cognimark HAPI retirement

As of September 12, 2026, Cognimark has removed HAPI serving and switched to
native EHR acquisition, immutable source storage and its independent KG. This
repository is historical source, not an active production runtime or fallback.

## Publishing retirement

The Cognimark image-publishing workflow, `core-fhir-ci` CloudFormation template
and deployment script are removed. Do not recreate the retired image repository
or publishing role. Cloud deletion and backup-verification receipts are recorded
in Core's internal HAPI retirement inventory, not in this public source fork.

Both previously published image versions have been archived to Core's private,
versioned, encrypted recovery storage. The archive includes all six OCI
manifests, configuration objects, image layers and attestations. All 74 unique
blobs (863,306,262 bytes) were read back and checked against their SHA-256
digests. This is artifact preservation, not an enabled deployment path.

The governance manifest records stewardship of this retirement archive only;
its bucket and encryption key remain owned by Core. No AWS infrastructure is
declared by this repository. Upstream Java sources, Docker/Helm examples and
Git history remain available for historical inspection.

## Historical customization

The `cognimark/8.10.0-aurora-pg16` branch was based on upstream
`image/v8.10.0-3`. It added Aurora PostgreSQL/Flyway support, packaged WAR
dependencies on the Spring Boot loader path, removed upstream MCP exposure and
carried dependency security updates. The earlier 8.4.0 branch remains historical
source; retaining a branch does not make its deployment instructions current.

Restoring an archived image would require a separately approved recovery plan
and new infrastructure. Historical clinical databases and original source
records are governed by Core's data-retention process; this retirement does
not authorize deleting them.
