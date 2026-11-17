# Release 26.11.1 review — 2026-11-17

Version: 26.11.1; Android production version code 18; iOS marketing version 26.11.1. Testing builds retain independent monotonically increasing platform build numbers.

Standards review: no confirmed documented standards violations. One nonblocking observation: the server metadata endpoint deliberately allows only observed redirect hosts, while native direct requests permit a wider TikTok CDN suffix. The server is a public endpoint and keeps its narrower allowlist; this is intentional rather than a guarantee that every native redirect works on the server.

Spec review: fixed the named Firebase codebase selector (`functions:preview:previewMetadata`). Added App Store Connect processing, exact version/build selection, release notes, external beta review submission, existing external group assignment and relationship verification. Group selection rejects ambiguity and internal-only groups. An upload alone is not treated as external distribution success.

Validation: merged both public release history and the private Linux CI preflight history without rewriting remote commits. The reviewed source tree already incorporated the upstream features; preserving that snapshot avoids duplicating prior changes during the history merge. Disposable local build passed shared host tests, Android unit tests/lint, browser tests/static PWA tests and production web compilation. Six iOS configuration tests, four release channel tests, four metadata endpoint tests and two distribution tool tests passed. Apple SDK compilation, simulator integration, signing and upload are performed by macOS CI.

Release status and remote URLs will be recorded after CI and distribution verification.
