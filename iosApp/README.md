# iOS

Fukaha’s iOS app, Share Extension, and UI Action Extension compile against the KMP `Shared` framework. You do **not** need a paid Apple Developer account to develop in the Simulator.

## What you can do without paying

| Goal | What you need | Limits |
|------|----------------|--------|
| **Run in Simulator** | A Mac + Xcode (free from the Mac App Store). No Apple ID required. | Simulator only. |
| **Run on your iPhone** | Free Apple ID signed in to Xcode (Personal Team). | Build expires after **7 days**. You re-install from Xcode. **App Groups** (`group.com.shenepoy.fukaha`, Settings ↔ Share Extension) usually **do not work** on a free team. |
| **TestFlight / App Store** | [Apple Developer Program](https://developer.apple.com/programs/) (~US$99 / year). | Needed for distribution and for a working App Group. |

The Shared Framework and Unsigned IPA workflows build unsigned artifacts. The
separate TestFlight workflow below uses your team's distribution credentials.

## TestFlight preparation

The manual **iOS TestFlight** workflow builds a signed App Store Connect IPA using
Xcode 26.6 on `macos-26`. Its upload option defaults to **false**. It builds the
Kotlin framework from the checkout, not the cached xtool framework. The existing
unsigned IPA workflow is not a TestFlight distribution path.

### First-time Apple setup

1. In [Certificates, Identifiers & Profiles](https://developer.apple.com/account/resources/identifiers/list),
   register explicit identifiers `com.shenepoy.fukaha`, `com.shenepoy.fukaha.share`, and
   `com.shenepoy.fukaha.action`. Register `group.com.shenepoy.fukaha` and enable it for all three.
   If an identifier is unavailable in your team, update all bundle identifiers,
   entitlements, signing validation, and profile mappings together before building.
2. In [App Store Connect → Apps](https://appstoreconnect.apple.com/apps), use
   **+ → New App**. Choose iOS, name **Fukaha** (subject to availability), primary
   language **English**, bundle ID **com.shenepoy.fukaha**, and a unique SKU such as
   **fukaha-ios**. Only the containing app needs an App Store Connect app record;
   the extensions need registered identifiers and profiles. Complete your legal
   developer name and agreements using your own account details.
3. Create an **Apple Distribution** certificate and export it with its private
   key as a password-protected `.p12`. Create three **App Store Connect**
   distribution provisioning profiles, one per identifier, using that certificate.
   Each profile must include the App Group. Development/ad-hoc profiles are rejected.
4. For automated upload, create an App Store Connect **team API key** with
   Developer access in **Users and Access → Integrations → App Store Connect API**.
   Developer access is sufficient for this workflow’s build validation and upload.
   Team keys apply across all apps in the account; they cannot be restricted to Fukaha.
   Save the downloaded `.p8` privately, along with its Key ID and Issuer ID.

### Repository configuration

Configure the repository that will run the workflow (public Fukaha or private
iOS-Setup), preferably in its GitHub **testflight** environment:

| Setting | Type | Value |
|---|---|---|
| `APPLE_TEAM_ID` | Variable | Ten-character Apple Developer Team ID |
| `IOS_DISTRIBUTION_P12_BASE64` | Secret | Base64 of the exported distribution certificate/private key |
| `IOS_DISTRIBUTION_P12_PASSWORD` | Secret | Password protecting that `.p12` |
| `IOS_APP_PROFILE_BASE64` | Secret | Base64 of the `com.shenepoy.fukaha` profile |
| `IOS_SHARE_PROFILE_BASE64` | Secret | Base64 of the `com.shenepoy.fukaha.share` profile |
| `IOS_ACTION_PROFILE_BASE64` | Secret | Base64 of the `com.shenepoy.fukaha.action` profile |
| `ASC_KEY_ID` | Secret | App Store Connect team API Key ID; upload only |
| `ASC_ISSUER_ID` | Secret | App Store Connect Issuer ID; upload only |
| `ASC_PRIVATE_KEY_BASE64` | Secret | Base64 of the `.p8` file; upload only |

Use the GitHub environment's secret editor or pipe file contents to `gh secret set`;
do not put private keys in chat or source control. For example, with your actual
repository selected:

```bash
base64 < /secure/path/distribution.p12 | gh secret set IOS_DISTRIBUTION_P12_BASE64 --env testflight
```

The script validates team, bundle IDs, expiration, App Group and distribution
profiles; installs signing material in a temporary keychain; and removes it on
exit. The exported IPA and debug symbols remain as workflow artifacts.

### Build and upload

Commit/push the reviewed source and workflow before dispatching it. In GitHub
Actions, select **iOS TestFlight → Run workflow** on that revision. Start with
upload unchecked. The workflow runs shared/native tests, archives the app and both
extensions, verifies versions/manifests/signatures, then exports the IPA.

The marketing version comes from `VERSION`, normalized to Apple's numeric form
(currently `26.10.0`). Build numbers default to `run_number.run_attempt`, or can be
supplied explicitly. Use a number higher than any prior build for that version,
especially when switching repositories or another build system.

After a successful validation build, run the workflow with upload checked and a
fresh build number. Apple processing must finish before the build appears in
TestFlight. Add internal testers in App Store Connect; external testing also needs
TestFlight review information and review. Uploading does not invite testers or
submit an App Store release automatically.

Prepared copy is in [beta description](testflight/beta-description.en.txt),
[what to test](testflight/what-to-test.en.txt), and
[review notes](testflight/review-notes.en.txt). Add your actual feedback email,
review contact and privacy policy URL in App Store Connect. These fields have not
been submitted or invented.

### Privacy and validation

The privacy manifest is included in all three Xcode targets and declares local
and App Group UserDefaults access (`CA92.1`, `1C8F.1`). It records no tracking or
app analytics collection, matching the current source. This is not a substitute
for reviewing App Store Connect's privacy answers: short-link resolution contacts
the original site, update checks contact GitHub, and configured media downloads
send the link/API key to the selected Cobalt server. Those services' practices must
be considered before publishing a privacy policy or data-use declaration.

`ITSAppUsesNonExemptEncryption=false` reflects the current use of standard system
HTTPS without custom encryption; reassess if cryptography changes. The app icon
is 1024×1024 RGB without alpha. iPad supports all four orientations.

Local checks:

```bash
python3 -m unittest discover -s iosApp/tests -p 'test_*.py'
bash iosApp/scripts/test-source-preferences.sh
```

On macOS, validate sharing at both levels:

```bash
# Controller behavior and outgoing share sheets on iPhone and iPad.
bash iosApp/scripts/test-sharing.sh
# Actual extension discovery and launch through the iOS 17 system share sheet.
xcodebuild -downloadPlatform iOS -buildVersion 17.5
bash iosApp/scripts/test-share-discovery.sh
```

The discovery script creates and deletes its own iOS 17 simulator. It installs the
real app and embedded extensions, then uses a test-only source app to share plain
URLs, text, and a URL alongside an image. An image-only case must not offer Fukaha.
It opens the full More/Apps list when needed and saves an XCTest result bundle to
`iosApp/build/ShareDiscovery.xcresult`. The source app is not included in the
shipping app. These controlled payloads do not replace a physical-device check
from Instagram or X.

A signed macOS archive/export and physical-device smoke test are still required.
Linux checks cannot prove signing, App Store acceptance, or system share-sheet
behavior.

References: [Apple upload requirements](https://developer.apple.com/news/upcoming-requirements/),
[new app records](https://developer.apple.com/help/app-store-connect/create-an-app-record/add-a-new-app/),
[TestFlight information](https://developer.apple.com/help/app-store-connect/test-a-beta-version/provide-test-information/),
[required-reason APIs](https://developer.apple.com/documentation/bundleresources/app-privacy-configuration/nsprivacyaccessedapitypes/nsprivacyaccessedapitype),
[export compliance](https://developer.apple.com/help/app-store-connect/manage-app-information/overview-of-export-compliance/).

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

Automatic PR CI currently checks Android and web. To validate iOS on a Mac, run:

```bash
bash iosApp/scripts/ci-build.sh
```

The script generates the Xcode project and builds the app plus both extensions for the Simulator with signing disabled. Test both iPhone and iPad sharing after building. Manual Shared Framework and Unsigned IPA workflows remain available in Actions.

The build script also runs the Swift preference-codec and source-health cache
regression checks. To run the Kotlin iOS cache, facade, and source-check tests on
a Mac with an Apple Silicon Simulator:

```bash
./gradlew :shared:iosSimulatorArm64Test
```

Swift syntax checks and Kotlin iOS compilation on Linux do not replace a signed
device or Simulator run. The iOS interface supports English and Arabic. Source
checks can be refreshed from the source list or picker; results and their last
checked time are stored locally. Unchecked or blocked sources remain unverified,
and failed runs preserve the last usable snapshot. Checks never change preferred
sources. The picker also supports starred favorites and drag ranking through
Reorder, saved separately per platform. Favorites appear first; changing their
order does not change the selected source. The per-link preview check is not yet
exposed by the iOS UI.

For a jailbroken device, run **Actions → iOS Unsigned IPA → Run workflow**. The workflow builds an arm64 Release archive against the `iphoneos` SDK with signing disabled, packages it as `fukaha-<version>-unsigned.ipa`, and uploads it as an artifact. Download it from the workflow run and use your jailbreak-side signer/installer as needed. This artifact is not suitable for TestFlight or the App Store.

## When you get a paid Developer account

Do this on a Mac with Xcode. GitHub cannot create the account for you.

1. Enroll at [developer.apple.com/programs](https://developer.apple.com/programs/).
2. In [Certificates, Identifiers & Profiles](https://developer.apple.com/account/resources/identifiers/list):
   - App ID `com.shenepoy.fukaha`
   - App ID `com.shenepoy.fukaha.share`
   - App ID `com.shenepoy.fukaha.action`
   - App Group `group.com.shenepoy.fukaha` — enable it on **all three** App IDs
3. Xcode → all three targets → Signing & Capabilities → your **Team**. Enable **App Groups** and tick `group.com.shenepoy.fukaha`.
4. Archive → Distribute App → TestFlight or App Store.

Use the TestFlight workflow described above for signed beta uploads. The general
tag-based release workflow remains separate and does not publish an iOS App Store
release.

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

Media downloads use the App Group cache shared by all three targets, so the app can clear extension downloads. A working `group.com.shenepoy.fukaha` entitlement is required for this storage. Cobalt endpoints must use HTTPS.
