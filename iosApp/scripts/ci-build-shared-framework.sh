#!/usr/bin/env bash
# Build the device-only Shared.xcframework used by the Linux xtool workflow.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
IOS="$ROOT/iosApp"
BUILD_DIR="$IOS/build"
ARCHIVE="$BUILD_DIR/Fukaha-shared-framework.xcarchive"
DERIVED_DATA="$BUILD_DIR/DerivedData-shared-framework"
DIST="$ROOT/dist"

eval "$(bash "$ROOT/scripts/ci/app_version.sh")"
ARCHIVE_NAME="$DIST/fukaha-${name}-Shared.xcframework.zip"

if ! command -v xcodegen >/dev/null 2>&1; then
  echo "xcodegen is required. Install with: brew install xcodegen" >&2
  exit 1
fi

if ! command -v xcodebuild >/dev/null 2>&1; then
  echo "xcodebuild is required. Run this on macOS with Xcode installed." >&2
  exit 1
fi

cd "$IOS"
xcodegen generate --spec "$IOS/project.yml"

rm -rf "$ARCHIVE" "$DERIVED_DATA"
xcodebuild \
  -project "$IOS/Fukaha.xcodeproj" \
  -scheme Fukaha \
  -configuration Release \
  -destination "generic/platform=iOS" \
  -sdk iphoneos \
  -derivedDataPath "$DERIVED_DATA" \
  -archivePath "$ARCHIVE" \
  ARCHS=arm64 \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY=- \
  PROVISIONING_PROFILE_SPECIFIER= \
  archive

FRAMEWORK="$(find "$ROOT/shared/build/xcode-frameworks" -type d -path '*/Release/iphoneos*/Shared.framework' -print -quit)"
if [ -z "$FRAMEWORK" ]; then
  echo "Could not locate the Release device Shared.framework" >&2
  find "$ROOT/shared/build/xcode-frameworks" -maxdepth 5 -print >&2 || true
  exit 1
fi

rm -rf "$DIST"
mkdir -p "$DIST"
xcodebuild \
  -create-xcframework \
  -framework "$FRAMEWORK" \
  -output "$DIST/Shared.xcframework"

ditto -c -k --sequesterRsrc --keepParent \
  "$DIST/Shared.xcframework" \
  "$ARCHIVE_NAME"

test -f "$ARCHIVE_NAME"
echo "Created $ARCHIVE_NAME"
unzip -l "$ARCHIVE_NAME"
