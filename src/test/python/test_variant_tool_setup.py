"""Installer checks use synthetic archives, never redistributed ANNOVAR scripts."""
import argparse
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

ROOT = Path(__file__).resolve().parents[3]
RESOURCE = ROOT / "src/main/resources/org/jlinalg/cli"
spec = importlib.util.spec_from_file_location("variant_setup", RESOURCE / "variant_tool_setup.py")
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


class SetupTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.home = self.root / "home"
        self.home.mkdir()
        self.lock = json.loads((RESOURCE / "variant-tool-runtime-lock.json").read_text(encoding="utf-8"))
        self.source = self.root / "synthetic.tar.gz"

    def archive(self, extra=None):
        with tarfile.open(self.source, "w:gz") as archive:
            for name in ["annovar/table_annovar.pl", "annovar/annotate_variation.pl"]:
                body = b"Synthetic installer fixture, not ANNOVAR.\n"
                entry = tarfile.TarInfo(name)
                entry.size = len(body)
                archive.addfile(entry, io.BytesIO(body))
            if extra is not None:
                entry, body = extra
                archive.addfile(entry, io.BytesIO(body) if body is not None else None)

    def args(self, **changes):
        values = dict(accept_license=True, source_file=str(self.source), source_url_env=None, expected_sha256=None)
        values.update(changes)
        return argparse.Namespace(**values)

    def test_registered_local_archive_can_be_imported_without_network(self):
        self.archive()
        with patch.object(setup, "download", side_effect=AssertionError("No software network request is allowed")):
            result = setup.annovar(self.home, self.lock, self.args())
        self.assertTrue((self.home / result["executable"]).is_file())
        self.assertEqual(setup.sha(self.source), result["archive_sha256"])
        self.assertEqual("user_supplied_registered_archive", result["source"]["kind"])

    def test_licence_must_be_acknowledged_before_reading_an_archive(self):
        with self.assertRaisesRegex(ValueError, "licence acknowledgement"):
            setup.annovar(self.home, self.lock, self.args(accept_license=False))
        self.assertFalse((self.home / "annovar.tar.gz").exists())

    def test_parent_traversal_cannot_write_outside_the_private_package(self):
        entry = tarfile.TarInfo("../../outside.txt")
        entry.size = 1
        self.archive((entry, b"x"))
        with self.assertRaisesRegex(ValueError, "unsafe path"):
            setup.annovar(self.home, self.lock, self.args())
        self.assertFalse((self.root / "outside.txt").exists())

    def test_archive_links_are_rejected(self):
        entry = tarfile.TarInfo("annovar/link")
        entry.type = tarfile.SYMTYPE
        entry.linkname = "../../outside.txt"
        self.archive((entry, None))
        with self.assertRaisesRegex(ValueError, "unsafe path"):
            setup.annovar(self.home, self.lock, self.args())

    def test_wrong_source_checksum_cannot_extract_a_package(self):
        self.archive()
        with self.assertRaisesRegex(ValueError, "checksum differs"):
            setup.annovar(self.home, self.lock, self.args(expected_sha256="0" * 64))
        self.assertFalse((self.home / "package").exists())

    def test_extraction_member_budget_is_enforced(self):
        self.archive()
        self.lock["annovar"]["maximum_members"] = 1
        with self.assertRaisesRegex(ValueError, "extraction limits"):
            setup.annovar(self.home, self.lock, self.args())

    def test_download_requires_official_https_without_embedded_credentials(self):
        for url in ["http://www.openbioinformatics.org/file", "https://example.org/file", "https://name:secret@www.openbioinformatics.org/file"]:
            with self.subTest(url=url), patch.object(setup.urllib.request, "build_opener", side_effect=AssertionError("Network must not be attempted")):
                with self.assertRaises(ValueError):
                    setup.download(url, self.root / "unused", 1024, {"www.openbioinformatics.org"})

    def test_personal_download_url_is_not_reflected_in_http_error(self):
        url = "https://www.openbioinformatics.org/file?token=PERSONAL_DOWNLOAD_SECRET"
        class Opener:
            def open(self, request, timeout):
                raise urllib.error.HTTPError(url, 403, "Forbidden", {}, None)
        with patch.object(setup.urllib.request, "build_opener", return_value=Opener()):
            with self.assertRaisesRegex(ValueError, "HTTP 403") as error:
                setup.download(url, self.root / "unused", 1024, {"www.openbioinformatics.org"})
        self.assertNotIn("PERSONAL_DOWNLOAD_SECRET", str(error.exception))


if __name__ == "__main__":
    unittest.main()
