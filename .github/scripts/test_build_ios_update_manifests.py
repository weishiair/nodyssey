"""Check the publishing half of IPA detection without contacting GitHub."""

import importlib.util
from pathlib import Path
import unittest


spec = importlib.util.spec_from_file_location("ios_updates", Path(__file__).with_name("build-ios-update-manifests.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
REPO = "owner/app"


def release(version, extension="ipa", state="uploaded", draft=False):
    tag = f"v{version}"
    name = f"nodyssey-{tag}.{extension}"
    return {"tag_name": tag, "draft": draft, "prerelease": "-" in version,
            "body": "Release notes", "published_at": "2026-11-16T01:00:00Z",
            "assets": [{"name": name, "state": state, "size": 12345,
                        "browser_download_url": f"https://github.com/{REPO}/releases/download/{tag}/{name}"}]}


class IosUpdateManifestTest(unittest.TestCase):
    def test_only_completed_ipa_uploads_are_published(self):
        manifest = module.build([
            release("1.3.10"), release("1.3.9"), release("1.4.0", extension="apk"),
            release("1.5.0", extension="zip"), release("1.6.0", state="new"), release("1.7.0", draft=True),
        ], REPO)
        self.assertEqual(["1.3.10", "1.3.9"], [entry["versionName"] for entry in manifest["versions"]])
        self.assertEqual("nodyssey-v1.3.10.ipa", manifest["versions"][0]["ipa"]["name"])

    def test_ios_only_tests_are_ordered_numerically_and_marked_as_prereleases(self):
        manifest = module.build([release("1.3.12-ios-test.9"), release("1.3.12-ios-test.10"), release("1.3.12")], REPO)
        self.assertEqual(["1.3.12", "1.3.12-ios-test.10", "1.3.12-ios-test.9"], [entry["versionName"] for entry in manifest["versions"]])
        self.assertEqual([False, True, True], [entry["preRelease"] for entry in manifest["versions"]])

    def test_empty_history_cannot_overwrite_the_last_working_inventory(self):
        with self.assertRaises(ValueError):
            module.build([release("1.4.0", extension="apk")], REPO)

    def test_wrong_repository_empty_asset_and_duplicate_ipa_are_rejected(self):
        for problem in ("url", "size", "duplicate"):
            with self.subTest(problem=problem):
                bad = release("1.3.12")
                if problem == "url":
                    bad["assets"][0]["browser_download_url"] = "https://example.invalid/update.ipa"
                elif problem == "size":
                    bad["assets"][0]["size"] = 0
                else:
                    bad["assets"].append(dict(bad["assets"][0]))
                with self.assertRaises(ValueError):
                    module.build([bad], REPO)


if __name__ == "__main__":
    unittest.main()
