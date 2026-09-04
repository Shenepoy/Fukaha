# Local iOS builds with xtool

This directory is the Linux-friendly SwiftPM build path for Fukaha. It keeps
the existing XcodeGen project as the canonical KMP build, while allowing the
Swift app and both share extensions to be rebuilt locally with
[xtool](https://github.com/xtool-org/xtool).

## One-time Linux setup

Install:

- Swift 6.3
- `usbmuxd` and the libimobiledevice tools
- the latest xtool Linux AppImage

Then run `xtool setup`. xtool asks for an Apple login and an Xcode `.xip` so it
can install the Darwin Swift SDK. See the official [Linux installation
guide](https://xtool.sh/documentation/xtool/installation-linux/).

## Prepare the local build

The Kotlin `Shared` framework is a device-only binary produced by the private
GitHub workflow. Run **Actions → iOS Shared Framework → Run workflow** in
[`Zyzto/iOS-Setup`](https://github.com/Zyzto/iOS-Setup), then fetch its latest
successful artifact:

```bash
cd /path/to/iOS-Setup
bash iosXtool/prepare.sh --latest
```

You can also pass a workflow run ID or a downloaded `.zip`/`.xcframework`:

```bash
bash iosXtool/prepare.sh --run 123456789
bash iosXtool/prepare.sh /path/to/Shared.xcframework.zip
```

The script creates ignored `.xtool-input/` and `.xtool-sources/` directories.
It copies the current Swift sources from the canonical iOS targets, so Swift
UI changes do not need to be duplicated.

## Build an IPA locally

```bash
cd iosXtool
xtool dev build --configuration release --ipa
```

The output is `iosXtool/xtool/Fukaha.ipa`. Use your jailbreak-side installer
or signer to install it. To use xtool’s Apple signing flow, add `--sign` after
completing `xtool auth`.

Refresh the framework artifact when Kotlin code or iOS dependencies in
`shared` change. Swift-only changes can use the same prepared framework.

The two extensions are declared using xtool’s SwiftPM extension format; the
action extension is the `com.apple.ui-services` target named **Clean with
Fukaha**. See xtool’s [app extension guide](https://xtool.sh/documentation/xtool/appex/).
