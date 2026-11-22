"""Exercise forbidden installation, preservation and integrity failures without network."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("install_fastqc.py")


class FastqcInstallationPolicyTest(unittest.TestCase):
    def invoke(self, root, *arguments):
        result = subprocess.run([sys.executable, str(SCRIPT), "--environment", str(root / "environment"),
                                 "--report", str(root / "report"), *arguments], capture_output=True, text=True, timeout=30)
        record = json.loads((root / "report/dependency-status.json").read_text(encoding="utf-8"))
        self.assertEqual(3, result.returncode, result.stdout + result.stderr)
        self.assertIn("Required action", (root / "report/WARNINGS.md").read_text(encoding="utf-8"))
        return record

    def test_forbidden_download_skips_without_creating_environment(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            record = self.invoke(root)
            self.assertEqual("skipped-install-not-permitted", record["status"])
            self.assertFalse((root / "environment").exists())

    def test_existing_environment_is_preserved_even_with_download_authorization(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); environment = root / "environment"; environment.mkdir()
            sentinel = environment / "user-data.txt"; sentinel.write_text("preserve this")
            record = self.invoke(root, "--allow-download")
            self.assertEqual("skipped-existing-environment", record["status"])
            self.assertEqual("preserve this", sentinel.read_text())

    def test_corrupt_cached_release_is_rejected_and_retained(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); archive = root / "corrupt.zip"; archive.write_bytes(b"invalid release cache")
            record = self.invoke(root, "--allow-download", "--archive", str(archive))
            self.assertEqual("skipped-dependency-unavailable", record["status"])
            self.assertIn("SHA-256", record["error"])
            self.assertEqual(b"invalid release cache", archive.read_bytes())
            self.assertFalse((root / "environment").exists())


if __name__ == "__main__":
    unittest.main()
