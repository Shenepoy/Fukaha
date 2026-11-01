#!/usr/bin/env bash
# Unsigned iOS Simulator build — no Apple Developer account required.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
IOS="$ROOT/iosApp"
cd "$IOS"

bash "$IOS/scripts/test-source-preferences.sh"

if ! command -v xcodegen >/dev/null 2>&1; then
  echo "xcodegen is required. Install with: brew install xcodegen" >&2
  exit 1
fi

xcodegen generate --spec "$IOS/project.yml"

xcodebuild \
  -project "$IOS/Fukaha.xcodeproj" \
  -scheme Fukaha \
  -configuration Debug \
  -sdk iphonesimulator \
  -arch arm64 \
  -derivedDataPath "$IOS/build/DerivedData" \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY=- \
  ONLY_ACTIVE_ARCH=YES \
  build
