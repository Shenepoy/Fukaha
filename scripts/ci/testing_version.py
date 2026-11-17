#!/usr/bin/env python3
"""One testing build identity per workflow run and retry; VERSION stays unchanged."""
import os
import re
from pathlib import Path


def testing_version(version, run, attempt, tag):
    if not re.fullmatch(r"[0-9]{2}\.[0-9]{2}\.[0-9]+", version):
        raise ValueError("Invalid VERSION")
    match = re.fullmatch(r"v" + re.escape(version) + r"-testing\.([1-9][0-9]*)", tag)
    if not match:
        raise ValueError("Testing tag must match VERSION: v<version>-testing.<positive number>")
    # Reserve iOS build numbers 1001+ for this workflow, below 1000 for manual archives.
    if not 1 <= run <= 8999 or not 1 <= attempt <= 99:
        raise ValueError("Testing run must be 1..8999 and attempt 1..99; rotate the build numbering before this limit")
    return {
        "base_version": version,
        "testing_build": match[1],
        "display_version": tag[1:],
        "android_code": str(run * 100 + attempt),
        "ios_build": f"{1000 + run}.{attempt}",
    }


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[2]
    values = testing_version((root / "VERSION").read_text().strip(),
                             int(os.environ["GITHUB_RUN_NUMBER"]), int(os.environ["GITHUB_RUN_ATTEMPT"]),
                             os.environ["GITHUB_REF_NAME"])
    output = "".join(f"{key}={value}\n" for key, value in values.items())
    with open(os.environ["GITHUB_OUTPUT"], "a") as target:
        target.write(output)
    print(output, end="")
