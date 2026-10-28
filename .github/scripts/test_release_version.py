"""Exercise the release workflow's tag guard and prerelease routing."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


class ReleaseVersionTest(unittest.TestCase):
    def resolve(self, tag):
        workflow = (Path(__file__).resolve().parents[1] / "workflows/release.yml").read_text(encoding="utf-8")
        block = workflow.split("      - name: Resolve the version under release\n", 1)[1]
        block = block.split("      # A tag", 1)[0]
        script = textwrap.dedent(block.split("        run: |\n", 1)[1]).strip()
        script = script.replace("${{ github.event.inputs.tag || github.ref_name }}", "${NODYSSEY_TEST_TAG}")
        bash = shutil.which("bash")
        if os.name == "nt":
            bash = str(Path(shutil.which("git")).resolve().parents[1] / "bin/bash.exe")
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "outputs.txt"
            output.touch()
            output_path = output.as_posix()
            if os.name == "nt":
                output_path = "/" + output.drive[0].lower() + output_path[2:]
            result = subprocess.run(
                [bash, "--noprofile", "--norc", "-e", "-c", script],
                env=dict(os.environ, NODYSSEY_TEST_TAG=tag, GITHUB_OUTPUT=output_path),
                capture_output=True, text=True,
            )
            return result, output.read_text(encoding="utf-8")

    def test_test_and_legacy_dev_tags_are_prereleases_while_stable_tags_are_not(self):
        for tag, prerelease in [("v1.3.5", False), ("v1.3.5-test.1", True),
                                ("v1.3.5-test.10", True), ("v1.3.5-dev.2", True)]:
            with self.subTest(tag=tag):
                result, output = self.resolve(tag)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn(f"version={tag[1:]}", output.splitlines())
                self.assertIn(f"is_dev={str(prerelease).lower()}", output.splitlines())

    def test_malformed_test_tags_and_other_products_cannot_be_released(self):
        for tag in ("v1.3.5-test", "v1.3.5-test.x", "ios-1.3.5-test.1"):
            with self.subTest(tag=tag):
                result, output = self.resolve(tag)
                self.assertNotEqual(0, result.returncode)
                self.assertNotIn("is_dev=", output)


if __name__ == "__main__":
    unittest.main()
