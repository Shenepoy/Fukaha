#!/usr/bin/env bash
set -euo pipefail
umask 077
[[ $# -eq 1 && -f "$1" ]] || { echo "Usage: upload-testflight.sh /path/to/Fukaha.ipa" >&2; exit 2; }
for name in ASC_KEY_ID ASC_ISSUER_ID ASC_PRIVATE_KEY_BASE64; do
    [[ -n "${!name:-}" ]] || { echo "Missing $name" >&2; exit 1; }
done
[[ "$ASC_KEY_ID" =~ ^[A-Za-z0-9]+$ ]] || { echo "Invalid ASC_KEY_ID" >&2; exit 1; }
keys="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/fukaha-asc.XXXXXX")"
trap 'rm -rf "$keys"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
python3 - "$keys" <<'PY'
import base64, os, sys
from pathlib import Path
value = base64.b64decode(''.join(os.environ['ASC_PRIVATE_KEY_BASE64'].split()), validate=True)
if b'-----BEGIN PRIVATE KEY-----' not in value:
    raise SystemExit('ASC_PRIVATE_KEY_BASE64 must encode the downloaded App Store Connect .p8 key')
(Path(sys.argv[1]) / f"AuthKey_{os.environ['ASC_KEY_ID']}.p8").write_bytes(value)
PY
export API_PRIVATE_KEYS_DIR="$keys"
xcrun altool --validate-app -f "$1" --type ios --apiKey "$ASC_KEY_ID" --apiIssuer "$ASC_ISSUER_ID"
xcrun altool --upload-app -f "$1" --type ios --apiKey "$ASC_KEY_ID" --apiIssuer "$ASC_ISSUER_ID"
echo "Upload submitted. Wait for processing in App Store Connect before inviting testers."
