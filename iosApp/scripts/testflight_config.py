#!/usr/bin/env python3
"""Validate signing inputs and generate Xcode export configuration without logging secrets."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import plistlib
import re
import subprocess

BUNDLES = {"app": "com.shenepoy.fukaha", "share": "com.shenepoy.fukaha.share", "action": "com.shenepoy.fukaha.action"}


def version_strings(version, build):
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("VERSION must contain three numeric components")
    if not re.fullmatch(r"[1-9]\d{0,3}(?:\.\d{1,2}){0,2}", build):
        raise ValueError("Build number must be 1–9999 with up to two optional 0–99 components")
    return ".".join(str(int(part)) for part in version.split(".")), build


def validate_profile(profile, bundle, team, now=None):
    now = now or dt.datetime.now(dt.timezone.utc)
    expiration = profile.get("ExpirationDate")
    if not isinstance(expiration, dt.datetime):
        raise ValueError(f"{bundle}: profile has no expiration date")
    if expiration.replace(tzinfo=dt.timezone.utc) <= now:
        raise ValueError(f"{bundle}: provisioning profile expired")
    entitlements = profile.get("Entitlements", {})
    if "iOS" not in profile.get("Platform", []):
        raise ValueError(f"{bundle}: use an iOS provisioning profile")
    if team not in profile.get("TeamIdentifier", []) or entitlements.get("com.apple.developer.team-identifier") != team:
        raise ValueError(f"{bundle}: profile belongs to another team")
    prefixes = profile.get("ApplicationIdentifierPrefix", [])
    if not any(entitlements.get("application-identifier") == f"{prefix}.{bundle}" for prefix in prefixes):
        raise ValueError(f"{bundle}: profile must use this explicit bundle identifier")
    if profile.get("ProvisionedDevices") is not None or profile.get("ProvisionsAllDevices") or entitlements.get("get-task-allow"):
        raise ValueError(f"{bundle}: use an App Store Connect distribution profile")
    if not entitlements.get("beta-reports-active"):
        raise ValueError(f"{bundle}: profile is not enabled for App Store distribution")
    if "group.com.shenepoy.fukaha" not in entitlements.get("com.apple.security.application-groups", []):
        raise ValueError(f"{bundle}: enable group.com.shenepoy.fukaha and regenerate the profile")
    uuid = profile.get("UUID", "")
    if not re.fullmatch(r"[0-9a-fA-F-]{36}", uuid):
        raise ValueError(f"{bundle}: invalid profile UUID")
    return uuid


def export_options(team, profiles):
    return {
        "method": "app-store-connect", "destination": "export", "teamID": team,
        "signingStyle": "manual", "signingCertificate": "Apple Distribution",
        "provisioningProfiles": {BUNDLES[key]: value for key, value in profiles.items()},
        "manageAppVersionAndBuildNumber": False, "uploadSymbols": True,
    }


def validate_archive(archive, version, build):
    app = archive / "Products/Applications/Fukaha.app"
    bundles = [app, app / "PlugIns/FukahaShare.appex", app / "PlugIns/FukahaAction.appex"]
    for path, expected_id in zip(bundles, BUNDLES.values()):
        with (path / "Info.plist").open("rb") as handle:
            info = plistlib.load(handle)
        if (info.get("CFBundleIdentifier"), info.get("CFBundleShortVersionString"), info.get("CFBundleVersion")) != (expected_id, version, build):
            raise ValueError(f"{path.name}: bundle identifier/version/build mismatch")
        if not (path / "embedded.mobileprovision").is_file():
            raise ValueError(f"{path.name}: missing signed provisioning profile")
        with (path / "PrivacyInfo.xcprivacy").open("rb") as handle:
            manifest = plistlib.load(handle)
        reasons = {entry["NSPrivacyAccessedAPIType"]: entry["NSPrivacyAccessedAPITypeReasons"]
                   for entry in manifest.get("NSPrivacyAccessedAPITypes", [])}
        if "1C8F.1" not in reasons.get("NSPrivacyAccessedAPICategoryUserDefaults", []):
            raise ValueError(f"{path.name}: missing App Group UserDefaults declaration")
    with (app / "Info.plist").open("rb") as handle:
        info = plistlib.load(handle)
    if info.get("ITSAppUsesNonExemptEncryption") is not False:
        raise ValueError("Missing encryption declaration")
    if not info.get("CFBundleIcons", {}).get("CFBundlePrimaryIcon", {}).get("CFBundleIconName"):
        raise ValueError("App archive has no compiled app icon")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    parser.add_argument("--archive", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    version, build = version_strings((root / "VERSION").read_text().strip(), os.environ.get("IOS_BUILD_NUMBER", ""))
    if args.archive:
        validate_archive(args.archive, version, build)
        print("Archive app, extensions, versions, privacy manifests and icon validated")
        return
    team = os.environ.get("APPLE_TEAM_ID", "")
    if not re.fullmatch(r"[A-Z0-9]{10}", team):
        raise ValueError("APPLE_TEAM_ID must be the 10-character Developer Team ID")
    profiles = {}
    for role, bundle in BUNDLES.items():
        decoded = subprocess.check_output(["security", "cms", "-D", "-i", str(args.directory / f"{role}.mobileprovision")])
        profiles[role] = validate_profile(plistlib.loads(decoded), bundle, team)
    (args.directory / "signing.json").write_text(json.dumps({"version": version, "build": build, "profiles": profiles}))
    with (args.directory / "ExportOptions.plist").open("wb") as handle:
        plistlib.dump(export_options(team, profiles), handle)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error))
