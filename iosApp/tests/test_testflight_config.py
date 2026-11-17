import copy
import datetime as dt
import importlib.util
from pathlib import Path
import plistlib
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("testflight_config", ROOT / "iosApp/scripts/testflight_config.py")
config = importlib.util.module_from_spec(spec)
spec.loader.exec_module(config)


class TestFlightConfigTests(unittest.TestCase):
    def setUp(self):
        self.now = dt.datetime(2026, 10, 30, tzinfo=dt.timezone.utc)
        self.profile = {
            "UUID": "12345678-1234-1234-1234-123456789abc",
            "ExpirationDate": dt.datetime(2027, 10, 30),
            "TeamIdentifier": ["ABCDE12345"],
            "Platform": ["iOS"],
            "ApplicationIdentifierPrefix": ["OLDPREFIX1"],
            "Entitlements": {
                "application-identifier": "OLDPREFIX1.com.shenepoy.fukaha",
                "com.apple.developer.team-identifier": "ABCDE12345",
                "com.apple.security.application-groups": ["group.com.shenepoy.fukaha"],
                "get-task-allow": False,
                "beta-reports-active": True,
            },
        }

    def validate(self, profile):
        return config.validate_profile(profile, "com.shenepoy.fukaha", "ABCDE12345", self.now)

    def test_valid_profile_supports_legacy_app_identifier_prefix(self):
        self.assertEqual(self.profile["UUID"], self.validate(self.profile))

    def test_expired_wrong_team_and_wrong_bundle_rejected(self):
        for field, value in [("ExpirationDate", dt.datetime(2025, 1, 1)), ("TeamIdentifier", ["OTHERTEAM1"]), ("Platform", ["OSX"])]:
            profile = copy.deepcopy(self.profile)
            profile[field] = value
            with self.assertRaises(ValueError): self.validate(profile)
        for value in ["OLDPREFIX1.*", "OLDPREFIX1.com.shenepoy.fukaha.share"]:
            profile = copy.deepcopy(self.profile)
            profile["Entitlements"]["application-identifier"] = value
            with self.assertRaises(ValueError): self.validate(profile)

    def test_development_adhoc_enterprise_and_missing_app_group_rejected(self):
        for field, value in [("ProvisionedDevices", ["device"]), ("ProvisionsAllDevices", True)]:
            profile = copy.deepcopy(self.profile)
            profile[field] = value
            with self.assertRaises(ValueError): self.validate(profile)
        for field, value in [("get-task-allow", True), ("beta-reports-active", False), ("com.apple.security.application-groups", [])]:
            profile = copy.deepcopy(self.profile)
            profile["Entitlements"][field] = value
            with self.assertRaises(ValueError): self.validate(profile)

    def test_versions_are_normalized_and_build_input_is_bounded(self):
        self.assertEqual(("26.9.0", "16.2"), config.version_strings("26.09.0", "16.2"))
        for build in ["", "0", "10000", "1.100", "1.2.3.4", "../16", "1;echo bad"]:
            with self.assertRaises(ValueError): config.version_strings("26.09.0", build)

    def test_export_maps_all_three_targets_and_preserves_versions(self):
        profiles = {role: str(index) for index, role in enumerate(config.BUNDLES)}
        options = config.export_options("ABCDE12345", profiles)
        self.assertEqual(set(config.BUNDLES.values()), set(options["provisioningProfiles"]))
        self.assertEqual("app-store-connect", options["method"])
        self.assertEqual("export", options["destination"])
        self.assertFalse(options["manageAppVersionAndBuildNumber"])

    def test_archive_checks_extension_versions_and_privacy(self):
        with tempfile.TemporaryDirectory() as temp:
            archive = Path(temp)
            app = archive / "Products/Applications/Fukaha.app"
            paths = [app, app / "PlugIns/FukahaShare.appex", app / "PlugIns/FukahaAction.appex"]
            for path, bundle in zip(paths, config.BUNDLES.values()):
                path.mkdir(parents=True)
                info = {"CFBundleIdentifier": bundle, "CFBundleShortVersionString": "26.9.0", "CFBundleVersion": "16.1",
                        "ITSAppUsesNonExemptEncryption": False, "CFBundleIcons": {"CFBundlePrimaryIcon": {"CFBundleIconName": "AppIcon"}}}
                (path / "Info.plist").write_bytes(plistlib.dumps(info))
                (path / "embedded.mobileprovision").write_bytes(b"fixture")
                (path / "PrivacyInfo.xcprivacy").write_bytes((ROOT / "iosApp/Fukaha/PrivacyInfo.xcprivacy").read_bytes())
            config.validate_archive(archive, "26.9.0", "16.1")
            info = plistlib.loads((paths[1] / "Info.plist").read_bytes())
            info["CFBundleVersion"] = "15"
            (paths[1] / "Info.plist").write_bytes(plistlib.dumps(info))
            with self.assertRaisesRegex(ValueError, "mismatch"): config.validate_archive(archive, "26.9.0", "16.1")
            info["CFBundleVersion"] = "16.1"
            (paths[1] / "Info.plist").write_bytes(plistlib.dumps(info))
            (paths[2] / "PrivacyInfo.xcprivacy").unlink()
            with self.assertRaises(FileNotFoundError): config.validate_archive(archive, "26.9.0", "16.1")


if __name__ == "__main__":
    unittest.main()
