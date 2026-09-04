# Local iOS builds with xtool

This directory is the Linux-friendly SwiftPM build path for Fukaha. It keeps
the existing XcodeGen project as the canonical KMP build, while allowing the
Swift app and both share extensions to be rebuilt locally with
[xtool](https://github.com/xtool-org/xtool).

## One-time Linux setup

Install:

- Swift 6.3 or newer (the current toolchain is Swift 6.3.3)
- `usbmuxd` and the libimobiledevice tools
- the latest xtool Linux AppImage

Then run `xtool setup`. xtool asks for an Apple login and an Xcode `.xip` so it
can install the Darwin Swift SDK. See the official [Linux installation
guide](https://xtool.sh/documentation/xtool/installation-linux/).

### NixOS

Swiftly does not auto-detect NixOS. This setup uses the Ubuntu 24.04
compatibility profile together with NixOS's `nix-ld` support:

```bash
nix profile add \
  nixpkgs#gnupg nixpkgs#ncurses.out nixpkgs#sqlite.out \
  nixpkgs#curl.out nixpkgs#libedit.out nixpkgs#icu.out nixpkgs#zlib.out \
  nixpkgs#usbmuxd nixpkgs#libimobiledevice
nix profile add --priority 4 nixpkgs#libxml2_13.out

mkdir -p "$HOME/.local/share/swiftly"
cd /tmp
curl -fLO https://download.swift.org/swiftly/linux/swiftly-x86_64.tar.gz
tar -xzf swiftly-x86_64.tar.gz
export SWIFTLY_HOME_DIR="$HOME/.local/share/swiftly"
export SWIFTLY_BIN_DIR="$HOME/.local/bin"
./swiftly init --no-modify-profile --assume-yes --quiet-shell-followup \
  --platform ubuntu24.04

cd /path/to/iOS-Setup
source iosXtool/nixos-env.sh
swift --version
xtool --version
```

If `libxml2.out` is already in your user profile, add `libxml2_13.out` with a
higher priority (`--priority 4`) so the Swift 6.3 runtime can find
`libxml2.so.2`. The helper sets the runtime and linker paths needed by the
upstream Swift toolchain; the xtool AppImage uses its own bundled libraries.

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
