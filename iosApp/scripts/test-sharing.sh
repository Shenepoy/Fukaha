#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT/iosApp"
xcodegen generate --spec project.yml
mkdir -p build
# Use an available runtime, creating isolated simulators so tests cannot alter a developer's devices.
xcrun simctl list runtimes -j > build/sharing-runtimes.json
xcrun simctl list devices available -j > build/sharing-devices.json
xcrun simctl list devicetypes -j > build/sharing-devicetypes.json
for family in iPhone iPad; do
    read -r runtime device_type < <(FAMILY="$family" python3 - <<'PYTHON'
import json,os
runtimes=[r for r in json.load(open('build/sharing-runtimes.json'))['runtimes'] if r.get('isAvailable') and '.iOS-' in r['identifier']]
devices=json.load(open('build/sharing-devices.json'))['devices']
types=json.load(open('build/sharing-devicetypes.json'))['devicetypes']
family=os.environ['FAMILY']
for runtime in sorted(runtimes,key=lambda r:tuple(map(int,r['version'].split('.'))),reverse=True):
    for device in devices.get(runtime['identifier'],[]):
        if not device.get('isAvailable') or not device['name'].startswith(family):
            continue
        device_type=device.get('deviceTypeIdentifier') or next((t['identifier'] for t in types if t['name']==device['name']),None)
        if device_type:
            print(runtime['identifier'],device_type)
            raise SystemExit(0)
raise SystemExit('No installed '+family+' simulator/runtime combination found')
PYTHON
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
