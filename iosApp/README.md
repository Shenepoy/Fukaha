# iOS

Fukaha’s iOS app, Share Extension, and UI Action Extension compile against the KMP `Shared` framework. You do **not** need a paid Apple Developer account to develop or to let CI verify the build.

## What you can do without paying

| Goal | What you need | Limits |
|------|----------------|--------|
| **CI compile check** | Nothing extra. GitHub Actions builds an unsigned Simulator `.app`. | Cannot install that artifact on a phone. |
| **Run in Simulator** | A Mac + Xcode (free from the Mac App Store). No Apple ID required. | Simulator only. |
| **Run on your iPhone** | Free Apple ID signed in to Xcode (Personal Team). | Build expires after **7 days**. You re-install from Xcode. **App Groups** (`group.app.fukaha`, Settings ↔ Share Extension) usually **do not work** on a free team. |
| **TestFlight / App Store** | [Apple Developer Program](https://developer.apple.com/programs/) (~US$99 / year). | Needed for distribution and for a working App Group. |

CI never signs with a certificate. That is intentional until you have a team.

## Updates

iOS uses Apple's distribution channels. App Store installations look up the
app's bundle identifier in Apple's catalog for the App Store account's region
(falling back to the device region), compare numeric release versions, and open
the App Store to install an available update. Releases requiring a newer iOS
version explain the requirement on manual checks and do not trigger install prompts.
Automatic checks run at most once a day after a successful response; manual
checks bypass the interval and skipped-version preference. Failed or cancelled
checks do not advance the timestamp. An empty listing is reported separately
from an up-to-date installation.

TestFlight builds open TestFlight from the button beside Version. Debug and
manually installed builds explain that a newer build must be installed from the
same source. Distribution is detected with StoreKit's verified app transaction,
with an existing receipt as an offline fallback. iOS does not query GitHub
releases. When the source cannot be determined, the Version button retries
detection instead of assuming the app was installed manually.
An explicit retry can refresh an unavailable or unverified Apple transaction;
Apple may ask the user to sign in. Automatic launch checks never request this refresh.
Its App Store check history and skipped version use separate settings
keys so old GitHub results cannot suppress an Apple release.

Run `bash iosApp/scripts/test-app-updates.sh` for the native Swift update tests.
The unsigned Simulator build script also runs these tests before building.
On macOS, the test script additionally typechecks the StoreKit release path
against the iOS Simulator SDK, since the host tests exclude iOS-only APIs.

## Local build (Mac + Xcode, no paid account)

1. Install Xcode, JDK 17, and [XcodeGen](https://github.com/yonaskolb/XcodeGen):

   ```bash
   brew install xcodegen openjdk@17
   ```

2. Generate the Xcode project and open it:

   ```bash
   cd iosApp
   xcodegen generate
   open Fukaha.xcodeproj
   ```

3. In Xcode, pick an **iPhone Simulator** and press Run. Signing can stay empty.

4. To try a physical device later: Xcode → target **Fukaha** → Signing & Capabilities → Team → **Add an Account…** → your Apple ID. Repeat for **FukahaShare** and **FukahaAction**. This uses a free Personal Team.

`Fukaha.xcodeproj` is generated. Do not commit it; regenerate after `project.yml` changes.

## CI

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) has an **iOS Simulator (unsigned)** job on `macos-15`:

1. Generate `Fukaha.xcodeproj` from `project.yml`
2. `xcodebuild` for `iphonesimulator` with signing disabled
3. Upload the `.app` as a workflow artifact (for inspection only)

Trigger: push / PR to `main`, or **Actions → CI → Run workflow**.

For a jailbroken device, run **Actions → iOS Unsigned IPA → Run workflow**. The workflow builds an arm64 Release archive against the `iphoneos` SDK with signing disabled, packages it as `fukaha-<version>-unsigned.ipa`, and uploads it as an artifact. Download it from the workflow run and use your jailbreak-side signer/installer as needed. This artifact is not suitable for TestFlight or the App Store.

## When you get a paid Developer account

Do this on a Mac with Xcode. GitHub cannot create the account for you.

1. Enroll at [developer.apple.com/programs](https://developer.apple.com/programs/).
2. In [Certificates, Identifiers & Profiles](https://developer.apple.com/account/resources/identifiers/list):
   - App ID `app.fukaha`
   - App ID `app.fukaha.share`
   - App ID `app.fukaha.action`
   - App Group `group.app.fukaha` — enable it on **all three** App IDs
3. Xcode → all three targets → Signing & Capabilities → your **Team**. Enable **App Groups** and tick `group.app.fukaha`.
4. Archive → Distribute App → TestFlight or App Store.

A signed device IPA is still not wired into the release workflow. After you have a distribution certificate and profiles, we can add secrets (`IOS_CERTIFICATE_BASE64`, provisioning profiles, and an export options plist) for normal device distribution. Until then, ship signed iOS builds from Xcode on your Mac.

## Project layout

| Path | Role |
|------|------|
| `iosApp/project.yml` | XcodeGen spec (app + Share/Action Extensions + Kotlin framework scripts) |
| `iosApp/Fukaha/` | SwiftUI Settings / About |
| `iosShareExtension/` | Share sheet extension |
| `iosActionExtension/` | UI Action extension shown in the share sheet’s Actions area |
| `shared` | Kotlin Multiplatform `Shared.framework` |

The Xcode pre-build script runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`. Framework search path:

```
$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)
```
