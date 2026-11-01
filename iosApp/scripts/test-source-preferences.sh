#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
test_source="$(mktemp "${TMPDIR:-/tmp}/fukaha-ios-preferences.XXXXXX.swift")"
trap 'rm -f "$test_source"' EXIT
cat "$ROOT/iosApp/Fukaha/FukahaSourcePreferences.swift" \
    "$ROOT/iosApp/tests/SourcePreferencesTests.swift" > "$test_source"
"${SWIFT_EXECUTABLE:-swift}" "$test_source"
cat "$ROOT/iosApp/Fukaha/FukahaSourceHealthSnapshot.swift" \
    "$ROOT/iosApp/Fukaha/FukahaSourceRanking.swift" \
    "$ROOT/iosApp/tests/SourceRankingTests.swift" > "$test_source"
"${SWIFT_EXECUTABLE:-swift}" "$test_source"
cat "$ROOT/iosApp/Fukaha/FukahaSourceHealthSnapshot.swift" \
    "$ROOT/iosApp/tests/SourceHealthTests.swift" > "$test_source"
"${SWIFT_EXECUTABLE:-swift}" "$test_source"
