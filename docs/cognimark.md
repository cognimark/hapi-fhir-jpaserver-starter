# Cognimark HAPI FHIR Build

## Ownership

This repository is Cognimark's fork of
`hapifhir/hapi-fhir-jpaserver-starter`. It is the source for the HAPI FHIR
container consumed by `core-stack`; `core-stack` owns the ECS services and only
references the published ECR image.

The versioned customization branch for this source tree is:

```text
cognimark/8.10.0-aurora-pg16
```

It is based on upstream tag `image/v8.10.0-3` and carries Cognimark's Aurora
PostgreSQL/Flyway patch. A version branch is not production until its image has
passed Aurora migration testing and `core-stack` references the promoted image.

## Customization

The Cognimark patch:

- pins the HAPI FHIR parent to 8.10.0;
- uses Flyway 11.20.3 with the PostgreSQL database module;
- registers an Aurora PostgreSQL database type with Flyway; and
- includes WAR dependencies on the Spring Boot loader path;
- removes the upstream MCP server endpoint and dependencies because Cognimark
  exposes FHIR only through the REST API; and
- pins compatible security updates for Spring Boot, Jackson, OpenTelemetry,
  Spring Retry, Jakarta Mail, PostgreSQL JDBC, and Logback.

## Remotes

Local clones use `origin` for the Cognimark fork and `upstream` for the HAPI
project:

```text
origin    https://github.com/cognimark/hapi-fhir-jpaserver-starter.git
upstream  https://github.com/hapifhir/hapi-fhir-jpaserver-starter.git
```

Do not merge `upstream/master` into the production branch. HAPI upgrades must
use a new version branch and pass Aurora PostgreSQL migration testing before
`core-stack` changes its image reference.

## Publishing

Run the `Publish Cognimark HAPI Image` GitHub workflow with a new image tag.
The workflow rejects tags that already exist in ECR and publishes only
`linux/amd64` images to:

```text
825765386069.dkr.ecr.us-east-1.amazonaws.com/core/fhir
```

Never overwrite a deployed tag. Promote an image by updating `core-stack` to
an immutable tag or image digest after validation.
