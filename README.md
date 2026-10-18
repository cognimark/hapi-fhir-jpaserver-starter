# Archived: HAPI application integrated into Core

As of October 18, 2026, the maintained HAPI application lives in
[`cognimark/core-stack/services/hapi`](https://github.com/cognimark/core-stack/tree/main/services/hapi).
Core owns its Java service extensions, dependency selection, Docker image,
native tests, infrastructure and release workflow. Contribute application
changes there. This repository is retained only as source history.

The imported application baseline is
`285aa8db53e5bd83ac79fd30c83455a644e86331`. The underlying HAPI Java library fork
remains maintained in [`cognimark/hapi-fhir`](https://github.com/cognimark/hapi-fhir).
Core builds a pinned library revision directly and does not depend on this
application repository or its workflows.

Historical source, branches and release records remain available here under
[the Apache 2.0 license](LICENSE). Source integration does not move or delete
clinical data, replace a running HAPI service, or rebuild an index.
