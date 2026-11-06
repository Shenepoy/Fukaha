#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT/iosApp"
xcodegen generate --spec project.yml
mkdir -p build
# Use an available runtime, creating isolated simulators so tests cannot alter a developer's devices.
xcrun simctl list runtimes -j > build/sharing-runtimes.json
runtime=$(python3 - <<'PY'
import json
runtimes=[r for r in json.load(open('build/sharing-runtimes.json'))['runtimes'] if r.get('isAvailable') and '.iOS-' in r['identifier']]
assert runtimes, 'No iOS simulator runtime is available'
print(max(runtimes,key=lambda r:tuple(map(int,r['version'].split('.'))))['identifier'])
PY
)
xcrun simctl list devicetypes -j > build/sharing-devicetypes.json
for family in iPhone iPad; do
    device_type=$(FAMILY="$family" python3 - <<'PY'
import json,os
items=[d for d in json.load(open('build/sharing-devicetypes.json'))['devicetypes'] if d.get('productFamily')==os.environ['FAMILY']]
assert items, 'No compatible simulator device type found'
print(items[-1]['identifier'])
PY
)
    simulator=$(xcrun simctl create "Fukaha Sharing $family" "$device_type" "$runtime")
    trap 'xcrun simctl delete "$simulator" >/dev/null 2>&1 || true' EXIT
    xcrun simctl boot "$simulator"
    xcrun simctl bootstatus "$simulator" -b
    xcodebuild test -project Fukaha.xcodeproj -scheme FukahaSharingTests \
        -destination "platform=iOS Simulator,id=$simulator" \
        -derivedDataPath build/SharingTests -resultBundlePath "build/Sharing-$family.xcresult" \
        CODE_SIGNING_ALLOWED=NO
    xcrun simctl delete "$simulator"
    trap - EXIT
done
