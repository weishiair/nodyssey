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


def clean_release(version="1.3.0", test=False):
    tag = f"release-{version}"
    name = f"nodyssey-{version}-release.apk"
    return {"tag_name": tag, "draft": False, "prerelease": test,
            "body": "Release notes", "published_at": "2026-10-21T01:00:00Z",
            "assets": [{"name": name, "size": 1024, "digest": "sha256:" + "b" * 64,
                        "browser_download_url": f"https://github.com/{REPO}/releases/download/{tag}/{name}"}]}


class EchUpdateManifestTest(unittest.TestCase):
    def test_clean_versions_replace_legacy_releases_without_losing_history(self):
        files = module.build([release(7), clean_release(), clean_release("1.3.1-dev.1", test=True)], REPO)
        self.assertEqual("1.3.0", files["stable.json"]["versionName"])
        self.assertEqual("1.3.1-dev.1", files["dev.json"]["versionName"])
        self.assertEqual(["1.3.1-dev.1", "1.3.0", "1.2.22-ech.7"],
                         [entry["versionName"] for entry in files["changelog.json"]["versions"]])

    def test_clean_stable_versions_outrank_their_dev_builds_and_dev_numbers_compare_numerically(self):
        builds = [release(7), clean_release("1.3.0-dev.9", test=True), clean_release("1.3.0-dev.10", test=True)]
        files = module.build(builds, REPO)
        self.assertEqual("1.2.22-ech.7", files["stable.json"]["versionName"])
        self.assertEqual("1.3.0-dev.10", files["dev.json"]["versionName"])
        files = module.build([*builds, clean_release()], REPO)
        self.assertEqual("1.3.0", files["dev.json"]["versionName"])

    def test_clean_release_identity_digest_and_channel_must_agree(self):
        for change in ("digest", "url", "tag", "prerelease", "duplicate"):
            with self.subTest(change=change):
                bad = clean_release()
                if change == "digest":
                    bad["assets"][0]["digest"] = ""
                elif change == "url":
                    bad["assets"][0]["browser_download_url"] = "https://github.com/5151561/nodyssey/releases/download/v1.3.0/nodyssey-1.3.0-release.apk"
                elif change == "tag":
                    bad["tag_name"] = "release-1.3.1"
                elif change == "prerelease":
                    bad["prerelease"] = True
                else:
                    bad["assets"].append(copy.deepcopy(bad["assets"][0]))
                with self.assertRaises(ValueError):
                    module.build([bad, release(7)], REPO)

    def test_clean_drafts_and_unsigned_builds_do_not_replace_a_published_release(self):
        draft = dict(clean_release("1.4.0"), draft=True)
        unsigned = clean_release("1.5.0")
        unsigned["assets"][0]["name"] = "nodyssey-1.5.0-unsigned.apk"
        files = module.build([draft, unsigned, clean_release()], REPO)
        self.assertEqual(["1.3.0"], [entry["versionName"] for entry in files["changelog.json"]["versions"]])

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
