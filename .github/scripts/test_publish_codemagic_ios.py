"""Exercise release routing without creating or editing a GitHub release."""

import importlib.util
import os
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location(
    "publish_codemagic_ios", Path(__file__).with_name("publish-codemagic-ios.py")
)
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class PublishCodemagicIosTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.output = Path(self.directory.name) / "build" / "ios-unsigned"
        self.output.mkdir(parents=True)
        (self.output / "nodyssey-1.2.3.ipa").write_bytes(b"verified test package")
        (self.output / "nodyssey-1.2.3-ios-test.1.ipa").write_bytes(b"verified test package")
        (self.output / "build-info.txt").write_text("version=1.2.3\n", encoding="utf-8")
        (self.output / "source-commit.txt").write_text("01234567\n", encoding="utf-8")

    def test_version_mismatch_cannot_publish_an_ipa_under_the_wrong_tag(self):
        with patch.object(publisher, "gh") as gh:
            with self.assertRaises(ValueError):
                publisher.publish("v1.2.4", "1.2.3", self.directory.name)
        gh.assert_not_called()

    def test_github_error_explains_the_failure_without_exposing_the_token(self):
        failure = types.SimpleNamespace(returncode=1, stdout="", stderr="forbidden: example-secret")
        with patch.dict(os.environ, {"GH_TOKEN": "example-secret"}), \
                patch.object(publisher.subprocess, "run", return_value=failure):
            with self.assertRaises(RuntimeError) as error:
                publisher.gh("release", "view", "v1.2.3")
        self.assertIn("forbidden", str(error.exception))
        self.assertNotIn("example-secret", str(error.exception))

    def test_missing_android_release_is_not_created_by_the_ios_build(self):
        calls = []

        def gh(*args, **_):
            calls.append(args)
            return types.SimpleNamespace(returncode=0, stdout="[]")

        with patch.object(publisher, "gh", side_effect=gh), patch.object(publisher.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "Android release"):
                publisher.publish("v1.2.3", "1.2.3", self.directory.name)
        self.assertFalse(any(call[:2] in (("release", "create"), ("release", "upload")) for call in calls))

    def test_ios_only_test_tag_creates_a_prerelease_and_attaches_the_ipa(self):
        calls = []

        def gh(*args, **_):
            calls.append(args)
            is_initial_view = args == ("release", "view", "v1.2.3-ios-test.1")
            return types.SimpleNamespace(returncode=int(is_initial_view), stdout="iOS 测试版")

        with patch.object(publisher, "gh", side_effect=gh):
            publisher.publish("v1.2.3-ios-test.1", "1.2.3-ios-test.1", self.directory.name)
        create = next(call for call in calls if call[:2] == ("release", "create"))
        self.assertIn("--prerelease", create)
        self.assertIn("--verify-tag", create)
        upload = next(call for call in calls if call[:2] == ("release", "upload"))
        self.assertTrue(any(arg.endswith("nodyssey-v1.2.3-ios-test.1.ipa") for arg in upload))
        notes = (self.output / "github-release" / "release-notes-with-ios.md").read_text(encoding="utf-8")
        self.assertIn(publisher.MARKER, notes)
        self.assertIn("01234567", notes)

    def test_existing_ios_release_note_is_not_appended_twice(self):
        calls = []

        def gh(*args, **_):
            calls.append(args)
            if args[:2] == ("run", "list"):
                return types.SimpleNamespace(
                    returncode=0,
                    stdout='[{"databaseId":1,"headSha":"01234567","status":"completed","conclusion":"success"}]',
                )
            if args[:2] == ("run", "view"):
                return types.SimpleNamespace(
                    returncode=0,
                    stdout='{"jobs":[{"name":"release","status":"completed","conclusion":"success"}]}',
                )
            return types.SimpleNamespace(returncode=0, stdout=publisher.MARKER)

        with patch.object(publisher, "gh", side_effect=gh):
            publisher.publish("v1.2.3", "1.2.3", self.directory.name)
        self.assertTrue(any(call[:2] == ("release", "upload") for call in calls))
        self.assertFalse(any(call[:2] == ("release", "edit") for call in calls))

    def test_queued_github_mac_does_not_block_completed_android_release(self):
        calls = []

        def gh(*args, **_):
            calls.append(args)
            if args[:2] == ("run", "list"):
                return types.SimpleNamespace(
                    stdout='[{"databaseId":1,"headSha":"01234567","status":"in_progress","conclusion":null}]',
                )
            if args[:2] == ("run", "view"):
                return types.SimpleNamespace(
                    stdout='{"jobs":[{"name":"release","status":"completed","conclusion":"success"},'
                           '{"name":"ios","status":"queued","conclusion":null}]}',
                )
            return types.SimpleNamespace(stdout=publisher.MARKER)

        with patch.object(publisher, "gh", side_effect=gh), patch.object(publisher.time, "sleep") as sleep:
            publisher.publish("v1.2.3", "1.2.3", self.directory.name)
        sleep.assert_not_called()
        self.assertTrue(any(call[:2] == ("release", "upload") for call in calls))

    def test_failed_android_job_cannot_publish_ipa(self):
        calls = []

        def gh(*args, **_):
            calls.append(args)
            if args[:2] == ("run", "list"):
                return types.SimpleNamespace(
                    stdout='[{"databaseId":1,"headSha":"01234567","status":"completed","conclusion":"failure"}]',
                )
            if args[:2] == ("run", "view"):
                return types.SimpleNamespace(
                    stdout='{"jobs":[{"name":"release","status":"completed","conclusion":"failure"}]}',
                )
            return types.SimpleNamespace(stdout="")

        with patch.object(publisher, "gh", side_effect=gh):
            with self.assertRaisesRegex(RuntimeError, "Android release job"):
                publisher.publish("v1.2.3", "1.2.3", self.directory.name)
        self.assertFalse(any(call[:2] == ("release", "upload") for call in calls))


if __name__ == "__main__":
    unittest.main()
