"""Verify Picard download policy and preservation without making network calls."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("install_picard.py")


class PicardInstallationPolicyTest(unittest.TestCase):
    def invoke(self, root, *arguments):
        result = subprocess.run([sys.executable, str(SCRIPT), "--environment", str(root / "environment"),
                                 "--report", str(root / "report"), *arguments], capture_output=True, text=True, timeout=30)
        record = json.loads((root / "report/dependency-status.json").read_text(encoding="utf-8"))
        self.assertEqual(3, result.returncode, result.stdout + result.stderr)
        self.assertIn("Required action", (root / "report/WARNINGS.md").read_text(encoding="utf-8"))
        return record

    def test_forbidden_download_does_not_create_installation(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.assertEqual("skipped-install-not-permitted", self.invoke(root)["status"])
            self.assertFalse((root / "environment").exists())

    def test_unmanaged_environment_is_preserved_even_when_authorized(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); environment = root / "environment"; environment.mkdir()
            sentinel = environment / "keep.txt"; sentinel.write_text("user files")
            self.assertEqual("skipped-existing-environment", self.invoke(root, "--allow-download")["status"])
            self.assertEqual("user files", sentinel.read_text())

    def test_corrupt_cached_jar_is_rejected_and_preserved(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); archive = root / "corrupt.jar"; archive.write_bytes(b"bad jar")
            record = self.invoke(root, "--allow-download", "--archive", str(archive))
            self.assertEqual("skipped-dependency-unavailable", record["status"])
            self.assertIn("SHA-256", record["error"])
            self.assertEqual(b"bad jar", archive.read_bytes())
            self.assertFalse((root / "environment").exists())


if __name__ == "__main__":
    unittest.main()
