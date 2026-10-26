"""No-network HTS installation policy and archive-boundary checks."""
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest
import zstandard

SCRIPT = Path(__file__).with_name("install_hts_dependencies.py")
spec = importlib.util.spec_from_file_location("hts_installer", SCRIPT)
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)


class HtsInstallationPolicyTest(unittest.TestCase):
    def invoke(self, root, *arguments):
        result = subprocess.run([sys.executable, str(SCRIPT), "--environment", str(root / "environment"),
            "--cache", str(root / "cache"), "--report", str(root / "report"), *arguments], capture_output=True, text=True, timeout=30)
        self.assertEqual(3, result.returncode, result.stdout + result.stderr)
        self.assertIn("Required action", (root / "report/WARNINGS.md").read_text(encoding="utf-8"))
        return json.loads((root / "report/dependency-status.json").read_text(encoding="utf-8"))

    def test_forbidden_download_does_not_create_runtime_or_cache(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.assertEqual("skipped-install-not-permitted", self.invoke(root)["status"])
            self.assertFalse((root / "environment").exists()); self.assertFalse((root / "cache").exists())

    def test_unmanaged_runtime_is_preserved(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); (root / "environment").mkdir(); sentinel = root / "environment/user-data"
            sentinel.write_text("retain")
            self.assertEqual("skipped-existing-environment", self.invoke(root, "--allow-download")["status"])
            self.assertEqual("retain", sentinel.read_text()); self.assertFalse((root / "cache").exists())

    @unittest.skipUnless(sys.platform == "win32", "Windows-only binary installer")
    def test_corrupt_pinned_cache_is_rejected_without_network_and_retained(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); (root / "cache").mkdir()
            package = json.loads(installer.LOCK.read_text(encoding="utf-8"))["packages"][0]
            archive = root / "cache" / package["filename"]; archive.write_bytes(b"corrupt")
            result = self.invoke(root, "--allow-download")
            self.assertEqual("skipped-dependency-unavailable", result["status"])
            self.assertIn("integrity mismatch", result["error"]); self.assertEqual(b"corrupt", archive.read_bytes())
            self.assertFalse((root / "environment").exists())

    def archive(self, root, names):
        raw = io.BytesIO()
        with tarfile.open(fileobj=raw, mode="w") as tar:
            for name in names:
                info = tarfile.TarInfo(name); data = b"fixture"; info.size = len(data); tar.addfile(info, io.BytesIO(data))
        path = root / "package.zst"; path.write_bytes(zstandard.ZstdCompressor().compress(raw.getvalue())); return path

    def test_archive_traversal_is_rejected_before_writing_outside_staging(self):
        for name in ["../outside", "/absolute", "ucrt64/../../outside", "ucrt64\\..\\outside", "C:/outside"]:
            with tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary); staging = root / "stage"; staging.mkdir()
                with self.assertRaises(ValueError): installer.extract_package(self.archive(root, [name]), staging.resolve(), {"name": "fixture"}, zstandard)
                self.assertEqual([], list(staging.iterdir()))

    def test_package_metadata_is_namespaced_and_duplicate_paths_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); staging = root / "stage"; staging.mkdir()
            archive = self.archive(root, [".PKGINFO", "ucrt64/bin/fixture.dll"])
            installer.extract_package(archive, staging.resolve(), {"name": "fixture"}, zstandard)
            self.assertEqual(b"fixture", (staging / "package-metadata/fixture/.PKGINFO").read_bytes())
            with self.assertRaises(ValueError): installer.extract_package(archive, staging.resolve(), {"name": "another"}, zstandard)
            self.assertEqual(b"fixture", (staging / "ucrt64/bin/fixture.dll").read_bytes())


if __name__ == "__main__":
    unittest.main()
