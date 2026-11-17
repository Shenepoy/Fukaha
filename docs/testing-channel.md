# Testing channel

Create a testing tag on the reviewed commit, then push the **same tag** to
**Shenepoy/Fukaha** and **Shenepoy/iOS-Setup**. No special branch is needed.

```sh
git tag v26.10.0-testing.1
git push origin v26.10.0-testing.1
git push private v26.10.0-testing.1
```

Tags must be `v<VERSION>-testing.<positive number>` and match the commit's VERSION
file. Increment the final number for each testing release; never move an existing
tag. Manual dispatch is accepted only when its ref is a matching testing tag.
The two repositories run independently: check both workflow results before
announcing that all platforms are available.

| Platform | Destination | Identity |
| --- | --- | --- |
| Android | Download APK from the public Testing run's artifacts | `app.fukaha.testing`, launcher label **Fukaha Testing** |
| Web | https://test.fukaha.shenepoy.com | Separate PWA, storage, service worker, testing banner and version |
| iOS | TestFlight, through the private repository | Existing `com.shenepoy.fukaha` app and extensions |

Android testing installs alongside production, uses the existing release signing
key, and never downloads a production APK through the in-app updater. Its version
row opens the testing workflow's downloads. Testing data stays in its own app
sandbox. Install each subsequent APK to update it; there is no automatic testing
APK updater. Artifacts expire after 30 days.

iOS testing uses the existing TestFlight app rather than a second App Store app.
Apple processing still applies, and external testing may require Beta App Review.
Group assignment and tester access remain managed in App Store Connect. The upload
workflow does not submit a public App Store release or invite testers.

## Versions and checks

`VERSION` remains the shared release version. Testing Android/web builds add
`-testing.<tag number>` (for example `26.10.0-testing.1`), matching the tag. Run counters are
per repository/workflow, so the same tag/commit identifies matching platforms. Retries keep the displayed version and increase the numeric build.
Android's version code is `run * 100 + attempt`; iOS keeps the numeric marketing
version and uses `(1000 + run).attempt`. This reserves build numbers above 1000
for the Testing workflow. Keep manual TestFlight builds below 1000. The generator
fails explicitly after run 8999 or attempt 99 rather than creating an invalid
Apple build number; revise the numbering scheme before reaching that limit.

Android publishes only after shared and app unit tests, staging lint, a minified
build, and signature verification. Web deploys only after browser and static PWA
tests. iOS validates configuration, Swift tests and native shared tests before
archiving, exporting and uploading. Publishing runs are serialized per repository.

Stable release jobs exclude testing tags. Production web deploys only from `main` in the public repository. Testing uses a
separate Firebase Hosting site in project `fukaha-shenepoy`, with no search indexing.
Production `firebase.json` is never rewritten: `testing_hosting.py` creates an
ignored `firebase.testing.json` selecting only the testing site. Both sites share
hosting header rules; their browser storage is isolated by origin.

## Custom testing domain

`test.fukaha.shenepoy.com` is attached to Firebase Hosting site
`fukaha-shenepoy-testing`. In the `shenepoy.com` DNS zone, configure:

| Type | Host | Value | TTL |
| --- | --- | --- | --- |
| CNAME | `test.fukaha` | `fukaha-shenepoy-testing.web.app` | Automatic |

Firebase provisions HTTPS after the DNS record is verified. The Firebase-provided
URL remains an alias; use the custom domain for testing so preferences remain on
one origin. Preferences saved on the earlier `web.app` origin do not transfer
automatically to the custom domain.

## Credentials

Public repository: existing `FIREBASE_SERVICE_ACCOUNT` and `RELEASE_*` signing
secrets. Web deployment uses the `testing-web` environment. The service account
must have Hosting deploy access to the testing site in the existing project.

Private repository: existing `testflight` environment, `APPLE_TEAM_ID` variable,
`IOS_*` signing/profile secrets and `ASC_*` upload credentials. The reusable iOS
workflow receives inherited secrets and explicitly selects that environment.
No Apple credentials are copied into the public repository.

## Local validation

```sh
python3 -m unittest discover -s scripts/ci/tests
./gradlew :shared:testAndroidHostTest :composeApp:testDebugUnitTest \
  :composeApp:lintStaging :composeApp:assembleStaging \
  :webApp:jsBrowserTest :webApp:pwaStaticTest :webApp:jsBrowserDistribution \
  -PtestingBuild=1 -PtestingVersionCode=101
```

The Android Gradle variant is called `staging` because Android reserves build type
names beginning with `test`. Local builds use `key.properties` when present; CI
requires explicit signing secrets. Builds without `testingBuild` keep production
web metadata; the staging Android variant defaults to `-testing.local`.
