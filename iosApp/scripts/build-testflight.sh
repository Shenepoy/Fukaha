#!/usr/bin/env bash
# Signed archive/export only. Upload is a separate, explicitly selected step.
set -euo pipefail
umask 077
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
[[ "$(uname -s)" == Darwin ]] || { echo "TestFlight archives require macOS with Xcode." >&2; exit 1; }
for name in APPLE_TEAM_ID IOS_BUILD_NUMBER IOS_DISTRIBUTION_P12_BASE64 IOS_DISTRIBUTION_P12_PASSWORD \
    IOS_APP_PROFILE_BASE64 IOS_SHARE_PROFILE_BASE64 IOS_ACTION_PROFILE_BASE64; do
    [[ -n "${!name:-}" ]] || { echo "Missing $name" >&2; exit 1; }
done
for tool in xcodebuild xcodegen security python3; do
    command -v "$tool" >/dev/null || { echo "Missing $tool" >&2; exit 1; }
done
xcode_major="$(xcodebuild -version | awk '/^Xcode / {split($2,v,"."); print v[1]}')"
sdk_major="$(xcrun --sdk iphoneos --show-sdk-version | cut -d. -f1)"
[[ "$xcode_major" -ge 26 && "$sdk_major" -ge 26 ]] || { echo "Xcode 26+ and iOS SDK 26+ are required." >&2; exit 1; }

work="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/fukaha-signing.XXXXXX")"
keychain="$work/signing.keychain-db"
original_keychains=()
while IFS= read -r path; do original_keychains+=("$path"); done < <(
    security list-keychains -d user | python3 -c 'import shlex,sys; print("\n".join(shlex.split(sys.stdin.read())))'
)
touch "$work/installed-profiles"
cleanup() {
    set +e
    security list-keychains -d user -s "${original_keychains[@]}" >/dev/null 2>&1
    security delete-keychain "$keychain" >/dev/null 2>&1
    while IFS= read -r path; do rm -f "$path"; done < "$work/installed-profiles"
    rm -rf "$work"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

python3 - "$work" <<'PY'
import base64, os, sys
from pathlib import Path
directory = Path(sys.argv[1])
for name, filename in {
    'IOS_DISTRIBUTION_P12_BASE64': 'distribution.p12',
    'IOS_APP_PROFILE_BASE64': 'app.mobileprovision',
    'IOS_SHARE_PROFILE_BASE64': 'share.mobileprovision',
    'IOS_ACTION_PROFILE_BASE64': 'action.mobileprovision',
}.items():
    try:
        (directory / filename).write_bytes(base64.b64decode(''.join(os.environ[name].split()), validate=True))
    except ValueError:
        raise SystemExit(f'{name} must contain valid base64')
PY
python3 "$ROOT/iosApp/scripts/testflight_config.py" "$work"
read_config() { python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); print(d[sys.argv[2]][sys.argv[3]] if len(sys.argv)>3 else d[sys.argv[2]])' "$work/signing.json" "$@"; }
version="$(read_config version)"
app_profile="$(read_config profiles app)"
share_profile="$(read_config profiles share)"
action_profile="$(read_config profiles action)"

keychain_password="$(openssl rand -base64 32)"
security create-keychain -p "$keychain_password" "$keychain"
security set-keychain-settings -lut 21600 "$keychain"
security unlock-keychain -p "$keychain_password" "$keychain"
security import "$work/distribution.p12" -P "$IOS_DISTRIBUTION_P12_PASSWORD" -k "$keychain" -T /usr/bin/codesign -T /usr/bin/security >/dev/null
security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$keychain_password" "$keychain" >/dev/null
security list-keychains -d user -s "$keychain" "${original_keychains[@]}"
# Xcode can print build-phase environments. Keep private inputs out of that process.
unset IOS_DISTRIBUTION_P12_BASE64 IOS_DISTRIBUTION_P12_PASSWORD
unset IOS_APP_PROFILE_BASE64 IOS_SHARE_PROFILE_BASE64 IOS_ACTION_PROFILE_BASE64

profile_dir="$HOME/Library/Developer/Xcode/UserData/Provisioning Profiles"
mkdir -p "$profile_dir"
for role in app share action; do
    uuid="$(read_config profiles "$role")"
    destination="$profile_dir/$uuid.mobileprovision"
    if [[ -f "$destination" ]]; then
        cmp -s "$work/$role.mobileprovision" "$destination" || { echo "Conflicting installed $role profile" >&2; exit 1; }
    else
        cp "$work/$role.mobileprovision" "$destination"
        printf '%s\n' "$destination" >> "$work/installed-profiles"
    fi
done

cd "$ROOT"
bash iosApp/scripts/test-source-preferences.sh
(cd iosApp && xcodegen generate --spec project.yml)
archive="$ROOT/iosApp/build/TestFlight/Fukaha-$IOS_BUILD_NUMBER.xcarchive"
export_dir="$ROOT/dist/testflight/$IOS_BUILD_NUMBER"
[[ ! -e "$archive" && ! -e "$export_dir" ]] || { echo "Build output already exists; choose a new IOS_BUILD_NUMBER." >&2; exit 1; }
mkdir -p "$(dirname "$archive")"
xcodebuild -project iosApp/Fukaha.xcodeproj -scheme Fukaha -configuration Release \
    -destination 'generic/platform=iOS' -sdk iphoneos \
    -derivedDataPath iosApp/build/DerivedData-TestFlight -archivePath "$archive" \
    ARCHS=arm64 CODE_SIGN_STYLE=Manual CODE_SIGN_IDENTITY='Apple Distribution' \
    DEVELOPMENT_TEAM="$APPLE_TEAM_ID" MARKETING_VERSION="$version" CURRENT_PROJECT_VERSION="$IOS_BUILD_NUMBER" \
    FUKAHA_APP_PROFILE="$app_profile" FUKAHA_SHARE_PROFILE="$share_profile" FUKAHA_ACTION_PROFILE="$action_profile" \
    archive
python3 iosApp/scripts/testflight_config.py "$work" --archive "$archive"
codesign --verify --deep --strict "$archive/Products/Applications/Fukaha.app"
xcodebuild -exportArchive -archivePath "$archive" -exportPath "$export_dir" -exportOptionsPlist "$work/ExportOptions.plist"
test -f "$export_dir/Fukaha.ipa"
echo "Exported $export_dir/Fukaha.ipa (not uploaded)"
