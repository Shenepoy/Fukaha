import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


def load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


version = load("testing_version").testing_version
hosting = load("testing_hosting").testing_config


class TestingConfigTest(unittest.TestCase):
    def test_retries_and_new_runs_increase_installable_versions(self):
        first, retry, next_run = [version("26.10.0", *args, "v26.10.0-testing.1") for args in [(1, 1), (1, 99), (2, 1)]]
        self.assertLess(int(first['android_code']), int(retry['android_code']))
        self.assertLess(int(retry['android_code']), int(next_run['android_code']))
        self.assertEqual(first['ios_build'], '1001.1')
        self.assertEqual(first['display_version'], '26.10.0-testing.1')
        self.assertEqual(first['base_version'], '26.10.0')
        last = version('26.10.0', 8999, 99, 'v26.10.0-testing.1')
        self.assertEqual(last['ios_build'], '9999.99')

    def test_invalid_input_cannot_produce_uninstallable_builds(self):
        for run, attempt in [(0, 1), (9000, 1), (1, 0), (1, 100)]:
            with self.assertRaises(ValueError):
                version('26.10.0', run, attempt, 'v26.10.0-testing.1')
        with self.assertRaises(ValueError):
            version('bad\nvalue', 1, 1, 'v26.10.0-testing.1')

    def test_only_matching_testing_tags_are_accepted(self):
        for tag in ['testing', 'v26.10.0', 'v26.9.0-testing.1',
                    'v26.10.0-testing.0', 'v26.10.0-testing.1-extra']:
            with self.assertRaises(ValueError):
                version('26.10.0', 1, 1, tag)
        self.assertEqual(version('26.10.0', 2, 1, 'v26.10.0-testing.2')['testing_build'], '2')

    def test_testing_deploy_cannot_target_production(self):
        source = {'hosting': {'target': 'production', 'site': 'fukaha-shenepoy',
                              'public': 'webApp/build/dist/js/productionExecutable', 'headers': []}}
        result = hosting(source)
        self.assertEqual(result['hosting']['site'], 'fukaha-shenepoy-testing')
        self.assertNotIn('target', result['hosting'])
        self.assertEqual(result['hosting']['public'], source['hosting']['public'])
        self.assertEqual(source['hosting']['site'], 'fukaha-shenepoy')
        self.assertEqual(source['hosting']['headers'], [])
        self.assertEqual(result['hosting']['headers'][0]['headers'][0]['key'], 'X-Robots-Tag')
