# Cognimark HAPI FHIR Build

## Ownership

This repository is Cognimark's fork of
`hapifhir/hapi-fhir-jpaserver-starter`. It is the source for the HAPI FHIR
container consumed by `core-stack`; `core-stack` owns the ECS services and only
references the published ECR image.

The production customization branch is:

```text
cognimark/8.4.0-aurora-pg16
```

It is based on upstream commit `9512ce1ddffca4f9266a9b402348ffbab8cec5c2`
and carries the Aurora PostgreSQL/Flyway patch introduced by commit
`d2e177f2a028c60bf07f4c2e027edbb6d6a37654`.

## Customization

The Cognimark patch:

- pins the HAPI FHIR parent to 8.4.0;
- uses Flyway 11.8.2 with the PostgreSQL database module;
- registers an Aurora PostgreSQL database type with Flyway; and
- includes WAR dependencies on the Spring Boot loader path.

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
825765386069.dkr.ecr.us-east-1.amazonaws.com/core-fhir/hapi
```

Never overwrite a deployed tag. Promote an image by updating `core-stack` to
an immutable tag or image digest after validation.
