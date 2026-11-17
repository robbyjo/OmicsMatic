# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Offline setup policy and integrity controls; no scientific execution is mocked."""
from pathlib import Path
import hashlib
import importlib.util
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('snpeff_setup_under_test', Path(__file__).with_name('setup.py'))
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)

class SetupPolicyTest(unittest.TestCase):
    def test_forbidden_network_never_opens_a_connection(self):
        with tempfile.TemporaryDirectory() as name, patch.object(setup.urllib.request, 'urlopen') as connection:
            target = Path(name)/'upstream.zip'
            with self.assertRaises(setup.Prerequisite):
                setup.obtain('https://example.invalid/upstream.zip', target, '0'*64, 100, False)
            connection.assert_not_called()
            self.assertFalse(target.exists())

    def test_permitted_offline_bytes_are_still_hash_checked(self):
        with tempfile.TemporaryDirectory() as name, patch.object(setup.urllib.request, 'urlopen') as connection:
            root = Path(name)
            (root/'cached').write_bytes(b'original bytes')
            with self.assertRaises(ValueError):
                setup.obtain('https://example.invalid/source', root/'captured', '0'*64, 100, False, root/'cached')
            self.assertEqual((root/'captured').read_bytes(), b'original bytes')
            connection.assert_not_called()

    def test_matching_offline_input_needs_no_network_permission(self):
        with tempfile.TemporaryDirectory() as name, patch.object(setup.urllib.request, 'urlopen') as connection:
            root = Path(name)
            raw = b'permitted original bytes'
            (root/'cached').write_bytes(raw)
            setup.obtain('https://example.invalid/source', root/'captured', hashlib.sha256(raw).hexdigest(), len(raw), False, root/'cached')
            self.assertEqual((root/'captured').read_bytes(), raw)
            connection.assert_not_called()

if __name__ == '__main__':
    unittest.main()
