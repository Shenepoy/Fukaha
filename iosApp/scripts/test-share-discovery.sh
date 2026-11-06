#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT/iosApp"
xcodegen generate --spec project.yml
mkdir -p build
xcrun simctl list runtimes -j > build/discovery-runtimes.json
runtime=$(python3 - <<'PY'
import json
items=[r for r in json.load(open('build/discovery-runtimes.json'))['runtimes'] if r.get('isAvailable') and r['version'].startswith('17.') and '.iOS-' in r['identifier']]
assert items, 'iOS 17 runtime is required: newer OS versions do not validate this regression'
print(max(items,key=lambda r:tuple(map(int,r['version'].split('.'))))['identifier'])
PY
)
simulator=$(xcrun simctl create 'Fukaha iOS17 Discovery' com.apple.CoreSimulator.SimDeviceType.iPhone-15 "$runtime")
trap 'xcrun simctl delete "$simulator" >/dev/null 2>&1 || true' EXIT
xcrun simctl boot "$simulator"
xcrun simctl bootstatus "$simulator" -b
xcodebuild build-for-testing -project Fukaha.xcodeproj -scheme FukahaShareDiscoveryTests \
    -destination "platform=iOS Simulator,id=$simulator" -derivedDataPath build/Discovery \
    CODE_SIGNING_ALLOWED=NO
xcrun simctl install "$simulator" build/Discovery/Build/Products/Debug-iphonesimulator/Fukaha.app
xcrun simctl launch "$simulator" com.shenepoy.fukaha
xcrun simctl terminate "$simulator" com.shenepoy.fukaha
xcodebuild test-without-building -project Fukaha.xcodeproj -scheme FukahaShareDiscoveryTests \
    -destination "platform=iOS Simulator,id=$simulator" -derivedDataPath build/Discovery \
    -resultBundlePath build/ShareDiscovery.xcresult CODE_SIGNING_ALLOWED=NO
