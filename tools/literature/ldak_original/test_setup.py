# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Offline policy/integrity tests; original scientific execution is never mocked."""
from pathlib import Path
import hashlib
import importlib.util
import io
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('ldak_setup_under_test', Path(__file__).with_name('setup.py'))
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


class SetupPolicyTest(unittest.TestCase):
    def test_forbidden_download_does_not_open_network_or_create_archive(self):
        with tempfile.TemporaryDirectory() as name, patch.object(setup.urllib.request, 'urlopen') as network:
            target = Path(name)/'archive'
            with self.assertRaises(setup.Prerequisite):
                setup.obtain('https://example.invalid/runtime', target, dict(bytes=1, sha256='0'*64), False)
            network.assert_not_called()
            self.assertFalse(target.exists())

    def test_offline_bytes_are_preserved_and_exactly_verified(self):
        with tempfile.TemporaryDirectory() as name, patch.object(setup.urllib.request, 'urlopen') as network:
            root = Path(name)
            raw = b'original offline bytes'
            (root/'input').write_bytes(raw)
            expected = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
            setup.obtain('https://example.invalid/runtime', root/'accepted', expected, False, root/'input')
            self.assertEqual((root/'accepted').read_bytes(), raw)
            with self.assertRaises(ValueError):
                setup.obtain('https://example.invalid/runtime', root/'mismatch', dict(bytes=len(raw), sha256='0'*64), False, root/'input')
            self.assertEqual((root/'mismatch').read_bytes(), raw)
            network.assert_not_called()

    def test_permitted_transfers_are_bounded_and_excess_is_not_executed(self):
        with tempfile.TemporaryDirectory() as name, patch.object(setup.urllib.request, 'urlopen', return_value=io.BytesIO(b'x'*1000)):
            target = Path(name)/'oversized'
            with self.assertRaises(ValueError):
                setup.obtain('https://example.invalid/runtime', target, dict(bytes=10, sha256='0'*64), True)
            self.assertEqual(target.stat().st_size, 11)

    def test_extraction_refuses_unexpected_members_before_writing_a_runtime(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            archive = root/'unexpected.zip'
            with zipfile.ZipFile(archive, 'x') as saved:
                saved.writestr('../outside', b'untrusted')
            with self.assertRaises(ValueError):
                setup.unpack(archive, root/'runtime', dict(binary=dict(bytes=9, sha256='0'*64)))
            self.assertFalse((root/'runtime').exists())


if __name__ == '__main__':
    unittest.main()
