#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if [[ "$(uname -s)" == Darwin ]]; then
    export MACOSX_DEPLOYMENT_TARGET="${MACOSX_DEPLOYMENT_TARGET:-13.0}"
fi
work="$(mktemp -d "${TMPDIR:-/tmp}/fukaha-update-tests.XXXXXXXX")"
trap 'rm -rf "$work"' EXIT
"${SWIFTC_EXECUTABLE:-swiftc}" -parse-as-library \
    -module-cache-path "$work/module-cache" \
    "$ROOT/iosApp/Fukaha/FukahaAppUpdate.swift" \
    "$ROOT/iosApp/tests/AppUpdateTests.swift" \
    -o "$work/update-tests"
"$work/update-tests"

# Typecheck the actual iOS release path, which host CLI tests exclude.
if [[ "$(uname -s)" == Darwin ]]; then
    sdk="$(xcrun --sdk iphonesimulator --show-sdk-path)"
    xcrun --sdk iphonesimulator swiftc -typecheck \
        -target arm64-apple-ios16.0-simulator -sdk "$sdk" \
        -module-cache-path "$work/ios-module-cache" \
        "$ROOT/iosApp/Fukaha/FukahaAppUpdate.swift"
fi
