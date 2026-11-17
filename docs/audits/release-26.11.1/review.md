# Release 26.11.1 review — 2026-11-17

Version: 26.11.1; Android production version code 18; iOS marketing version 26.11.1. Testing builds retain independent monotonically increasing platform build numbers.

Standards review: no confirmed documented standards violations. One nonblocking observation: the server metadata endpoint deliberately allows only observed redirect hosts, while native direct requests permit a wider TikTok CDN suffix. The server is a public endpoint and keeps its narrower allowlist; this is intentional rather than a guarantee that every native redirect works on the server.

Spec review: fixed the named Firebase codebase selector (`functions:preview:previewMetadata`). Added App Store Connect processing, exact version/build selection, release notes, external beta review submission, existing external group assignment and relationship verification. Group selection rejects ambiguity and internal-only groups. An upload alone is not treated as external distribution success.

Validation: merged both public release history and the private Linux CI preflight history without rewriting remote commits. The reviewed source tree already incorporated the upstream features; preserving that snapshot avoids duplicating prior changes during the history merge. Disposable local build passed shared host tests, Android unit tests/lint, browser tests/static PWA tests and production web compilation. Six iOS configuration tests, four release channel tests, four metadata endpoint tests and two distribution tool tests passed. Apple SDK compilation, simulator integration, signing and upload are performed by macOS CI.

Release status and remote URLs will be recorded after CI and distribution verification.

Verified Android production release: https://github.com/Shenepoy/Fukaha/releases/tag/v26.11.1 — package app.fukaha, version 26.11.1/code 18; SHA-256 232dd4293424274779fdb1cfd7cd5da5c1d74335e427127775461b4b11598acb. Production and testing APKs have release signer SHA-256 608d1abc3cf1f7141318016c7a3d6205a6f79d03fec62cb98cf5d6822debc861. Testing package app.fukaha.testing, version 26.11.1-testing.1/code 301. Both tagged Android jobs passed.

Testing web is deployed at https://test.fukaha.shenepoy.com and its served JavaScript contains 26.11.1-testing.1. The preview backend is not deployed: Firebase rejects Cloud Functions on the project's current free Spark plan. Production Hosting remains 26.11.0 because its job stopped before Hosting deployment. A billing upgrade requires the user's answer; it has not been performed.

Apple's filtered betaGroups collection verified the existing external group AppTesters (ab38edaf-baa7-4acc-ab6c-1b813a723307), alongside Fukaha Internal. The chosen group ID is saved in the private testflight environment's TESTFLIGHT_EXTERNAL_GROUP variable. Review contact fields and both English/Arabic beta descriptions are present. API group lookup was retried after an Apple 500 and an empty relationship response; no new groups or testers were created.

All required macOS checks passed: native shared code, iPhone/iPad sharing, iOS 17 extension discovery, signed archive/export, and Apple upload. IPA and dSYMs are retained in https://github.com/Shenepoy/iOS-Setup/actions/runs/44129500432. The old post-upload distribution step was canceled after upload, and corrected distribution was retried separately without rebuilding.

Apple processed 26.11.1 (1003.1), build 9a22b53f-eb27-4997-a784-b675fb47f13f. English test notes were saved. External state is READY_FOR_BETA_SUBMISSION. Both external beta review submission and AppTesters group attachment return 403; Apple explicitly says the API key does not allow the request. **AppTesters is configured but not attached to this build.** The current Developer upload key must be replaced with an appropriately authorized key, or an authorized user must sign in to App Store Connect to complete these operations. No new signing/upload is needed. The API verification report is in testflight/testflight-distribution.json; retry run: https://github.com/Shenepoy/iOS-Setup/actions/runs/44135286379.

General CI passed on both public and private repositories at ab84226. Temporary local validation and deployment checkouts were removed by with-temp-build. Existing user-requested localhost preview services and emulator installation were preserved. Editor/device artifacts and local AGENTS.md were excluded from commits.
