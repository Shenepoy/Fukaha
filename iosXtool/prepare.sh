#!/usr/bin/env bash
# Prepare the generated SwiftPM sources and a device Shared.xcframework for xtool.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
XTOOL_ROOT="$ROOT/iosXtool"
INPUT_ROOT="$XTOOL_ROOT/.xtool-input"
SOURCE_ROOT="$XTOOL_ROOT/.xtool-sources"
FRAMEWORK_DEST="$INPUT_ROOT/Shared.xcframework"

usage() {
    cat >&2 <<'EOF'
Usage:
  bash iosXtool/prepare.sh --latest
  bash iosXtool/prepare.sh --run <github-run-id>
  bash iosXtool/prepare.sh /path/to/Shared.xcframework.zip
  bash iosXtool/prepare.sh /path/to/Shared.xcframework

The GitHub options download the fukaha-shared-xcframework artifact from
Zyzto/iOS-Setup. A local path can be used when the artifact is already present.
EOF
    exit 2
}

temp_dir="$(mktemp -d)"
trap 'rm -rf "$temp_dir"' EXIT

find_framework() {
    local search_root="$1"
    find "$search_root" -type d -name "Shared.xcframework" -print -quit
}

unpack_framework_zip() {
    local archive="$1"
    local unpack_root="$temp_dir/unpacked"
    rm -rf "$unpack_root"
    mkdir -p "$unpack_root"
    unzip -q "$archive" -d "$unpack_root"
    find_framework "$unpack_root"
}

framework_source=""
if [ "$#" -eq 0 ]; then
    if [ -d "$FRAMEWORK_DEST" ]; then
        framework_source="$FRAMEWORK_DEST"
    else
        usage
    fi
elif [ "$1" = "--latest" ]; then
    command -v gh >/dev/null 2>&1 || {
        echo "gh is required for --latest. Pass a local framework path instead." >&2
        exit 1
    }
    run_id="$(gh run list \
        --repo Zyzto/iOS-Setup \
        --workflow ios-shared-framework.yml \
        --status success \
        --limit 1 \
        --json databaseId \
        --jq '.[0].databaseId')"
    if [ -z "$run_id" ]; then
        echo "No successful ios-shared-framework workflow run was found." >&2
        echo "Run Actions → iOS Shared Framework in Zyzto/iOS-Setup first." >&2
        exit 1
    fi
    artifact_root="$temp_dir/artifact"
    gh run download "$run_id" \
        --repo Zyzto/iOS-Setup \
        --name fukaha-shared-xcframework \
        --dir "$artifact_root"
    framework_zip="$(find "$artifact_root" -type f -name "*.zip" -print -quit)"
    [ -n "$framework_zip" ] || {
        echo "The workflow artifact did not contain a framework zip." >&2
        exit 1
    }
    framework_source="$(unpack_framework_zip "$framework_zip")"
elif [ "$1" = "--run" ]; then
    [ "$#" -eq 2 ] || usage
    command -v gh >/dev/null 2>&1 || {
        echo "gh is required for --run. Pass a local framework path instead." >&2
        exit 1
    }
    artifact_root="$temp_dir/artifact"
    gh run download "$2" \
        --repo Zyzto/iOS-Setup \
        --name fukaha-shared-xcframework \
        --dir "$artifact_root"
    framework_zip="$(find "$artifact_root" -type f -name "*.zip" -print -quit)"
    [ -n "$framework_zip" ] || {
        echo "The workflow artifact did not contain a framework zip." >&2
        exit 1
    }
    framework_source="$(unpack_framework_zip "$framework_zip")"
else
    input_path="$1"
    if [ -d "$input_path" ]; then
        framework_source="$(find_framework "$input_path")"
        if [ -z "$framework_source" ] && [ -f "$input_path/Shared.xcframework.zip" ]; then
            framework_source="$(unpack_framework_zip "$input_path/Shared.xcframework.zip")"
        fi
    elif [ -f "$input_path" ]; then
        framework_source="$(unpack_framework_zip "$input_path")"
    else
        echo "Framework input does not exist: $input_path" >&2
        exit 1
    fi
fi

[ -n "$framework_source" ] && [ -d "$framework_source" ] || {
    echo "Could not locate Shared.xcframework in the supplied input." >&2
    exit 1
}

# Stage the source before replacing the prepared copy. This also makes a
# no-argument refresh safe when the source is already .xtool-input/.
staged_framework="$temp_dir/Shared.xcframework"
cp -R "$framework_source" "$staged_framework"

rm -rf "$FRAMEWORK_DEST"
mkdir -p "$INPUT_ROOT"
cp -R "$staged_framework" "$FRAMEWORK_DEST"
test -f "$FRAMEWORK_DEST/Info.plist"

rm -rf "$SOURCE_ROOT"
mkdir -p \
    "$SOURCE_ROOT/Fukaha" \
    "$SOURCE_ROOT/FukahaShare" \
    "$SOURCE_ROOT/FukahaAction"

cp "$ROOT"/iosApp/Fukaha/*.swift "$SOURCE_ROOT/Fukaha/"
cp "$ROOT"/iosShareExtension/FukahaShareScreen.swift "$SOURCE_ROOT/Fukaha/"

for extension in FukahaShare FukahaAction; do
    cp "$ROOT"/iosShareExtension/ShareViewController.swift "$SOURCE_ROOT/$extension/"
    cp "$ROOT"/iosShareExtension/FukahaShareScreen.swift "$SOURCE_ROOT/$extension/"
    cp "$ROOT"/iosApp/Fukaha/FukahaDesign.swift "$SOURCE_ROOT/$extension/"
done

echo "Prepared $FRAMEWORK_DEST"
echo "Prepared SwiftPM sources in $SOURCE_ROOT"
echo "Build with: cd iosXtool && xtool dev build --configuration release --ipa"
