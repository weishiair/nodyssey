"""Guard against shipping upstream, unsigned, mislabelled or unverified APK updates."""
import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("ech_updates", Path(__file__).with_name("build-ech-update-manifests.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
REPO = "weishiair/nodyssey"


def release(revision=5, test=False):
    tag = f"ech-1.2.22{'-test' if test else ''}.{revision}"
    name = f"nodyssey-1.2.22-ech{'-test' if test else ''}.{revision}-release.apk"
    return {"tag_name": tag, "draft": False, "prerelease": test,
            "body": "ECH release notes", "published_at": "2026-10-19T01:00:00Z",
            "assets": [{"name": name, "size": 1024, "digest": "sha256:" + "a" * 64,
                        "browser_download_url": f"https://github.com/{REPO}/releases/download/{tag}/{name}"}]}


class EchUpdateManifestTest(unittest.TestCase):
    def test_numeric_revision_order_and_separate_test_channel(self):
        files = module.build([release(9), release(10), release(11, test=True)], REPO)
        self.assertEqual("1.2.22-ech.10", files["stable.json"]["versionName"])
        self.assertEqual("1.2.22-ech-test.11", files["dev.json"]["versionName"])
        self.assertTrue(files["changelog.json"]["versions"][0]["preRelease"])

    def test_upstream_drafts_and_unsigned_builds_are_not_offered(self):
        upstream = dict(release(99), tag_name="v99.0.0")
        draft = dict(release(88), draft=True)
        unsigned = copy.deepcopy(release(77))
        unsigned["assets"][0]["name"] = "nodyssey-1.2.22-ech.77-unsigned.apk"
        files = module.build([upstream, draft, unsigned, release()], REPO)
        self.assertEqual(["1.2.22-ech.5"], [v["versionName"] for v in files["changelog.json"]["versions"]])

    def test_bad_digest_external_asset_and_mismatched_tag_are_refused(self):
        for change in ["digest", "url", "tag", "prerelease"]:
            with self.subTest(change=change):
                bad = release()
                if change == "digest":
                    bad["assets"][0]["digest"] = ""
                elif change == "url":
                    bad["assets"][0]["browser_download_url"] = "https://github.com/5151561/nodyssey/releases/download/v1/official.apk"
                elif change == "tag":
                    bad["tag_name"] = "ech-1.2.22.6"
                else:
                    bad["prerelease"] = True
                with self.assertRaises(ValueError):
                    module.build([bad], REPO)

    def test_empty_history_cannot_replace_an_existing_feed(self):
        with self.assertRaises(ValueError):
            module.build([], REPO)


if __name__ == "__main__":
    unittest.main()
