import pathlib
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from launch import MARKER, launch


class LaunchTests(unittest.TestCase):
    def run_launch(self, outcome):
        with tempfile.TemporaryDirectory() as tmp:
            logfile = pathlib.Path(tmp) / "app.log"
            with patch("launch.subprocess.run", side_effect=outcome) as run:
                try:
                    launch("TEST-UDID", "test.bundle", logfile)
                    error = None
                except RuntimeError as exc:
                    error = str(exc)
            self.assertEqual(run.call_args.args[0], ["xcrun", "simctl", "launch", "--console", "--terminate-running-process", "TEST-UDID", "test.bundle"])
            self.assertEqual(run.call_args.kwargs["timeout"], 120)
            return error, logfile.read_text(encoding="utf-8")

    def test_success_needs_complete_marker_and_zero_exit(self):
        error, log = self.run_launch([subprocess.CompletedProcess([], 0, "starting\n" + MARKER + "\n")])
        self.assertIsNone(error)
        self.assertIn(MARKER, log)

    def test_zero_exit_without_marker_fails(self):
        error, _ = self.run_launch([subprocess.CompletedProcess([], 0, "app exited\n")])
        self.assertIn("exact success marker", error)

    def test_nonzero_exit_even_with_marker_fails(self):
        error, _ = self.run_launch([subprocess.CompletedProcess([], 5, MARKER + "\n")])
        self.assertIn("exit=5", error)

    def test_partial_or_embedded_marker_fails(self):
        error, _ = self.run_launch([subprocess.CompletedProcess([], 0, "prefix " + MARKER + "\n")])
        self.assertIn("exact success marker", error)

    def test_timeout_is_failure_and_captures_partial_log(self):
        error, log = self.run_launch(subprocess.TimeoutExpired("xcrun", 120, output=b"partial\n"))
        self.assertIn("timed out", error)
        self.assertEqual(log, "partial\n")


if __name__ == "__main__":
    unittest.main()
