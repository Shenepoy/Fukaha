#!/usr/bin/env python3
"""Create a test-only Firebase config using the production caching rules."""
import json
from pathlib import Path

TESTING_SITE = "fukaha-shenepoy-testing"


def testing_config(config):
    # Deep copy: preparing testing must never change the production configuration.
    result = json.loads(json.dumps(config))
    hosting = result["hosting"]
    hosting.pop("target", None)
    hosting["site"] = TESTING_SITE
    hosting.setdefault("headers", []).append({"source": "**", "headers": [
        {"key": "X-Robots-Tag", "value": "noindex, nofollow"},
    ]})
    return result


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[2]
    source = json.loads((root / "firebase.json").read_text())
    (root / "firebase.testing.json").write_text(json.dumps(testing_config(source), indent=2) + "\n")
