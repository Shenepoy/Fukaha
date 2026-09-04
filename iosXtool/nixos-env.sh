#!/usr/bin/env bash
# Source this file in a NixOS shell before using SwiftPM or xtool.

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
    echo "Usage: source iosXtool/nixos-env.sh" >&2
    exit 2
fi

user_home="${HOME:?HOME must be set}"
swiftly_home="${SWIFTLY_HOME_DIR:-$user_home/.local/share/swiftly}"
swiftly_env="$swiftly_home/env.sh"
if [ -f "$swiftly_env" ]; then
    # shellcheck disable=SC1090
    source "$swiftly_env"
fi

nix_profile="$user_home/.nix-profile"
if [ -d "$nix_profile/lib" ]; then
    export LD_LIBRARY_PATH="$nix_profile/lib:/run/current-system/sw/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
fi

# Swift's bundled clang needs the Nix C runtime objects when linking package
# manifests and command-line tools.
swift_crt_begin="$(cc -print-file-name=crtbeginS.o 2>/dev/null || true)"
swift_crt_start="$(cc -print-file-name=crti.o 2>/dev/null || true)"
if [ -f "$swift_crt_begin" ] && [ -f "$swift_crt_start" ]; then
    swift_gcc_lib="$(dirname "$swift_crt_begin")"
    swift_glibc_lib="$(dirname "$swift_crt_start")"
    export COMPILER_PATH="$swift_gcc_lib:$swift_glibc_lib${COMPILER_PATH:+:$COMPILER_PATH}"
    export LIBRARY_PATH="$swift_gcc_lib:$swift_glibc_lib${LIBRARY_PATH:+:$LIBRARY_PATH}"
fi
