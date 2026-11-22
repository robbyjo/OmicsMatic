"""Ensure failed metadata admission restores old bytes and preserves failed writes."""
from pathlib import Path
import json
import tempfile
import unittest
from genomicranges_catalog.primary import ROOT
from seurat_catalog.catalog import install


class TransactionTests(unittest.TestCase):
    def fixture(self):
        parent = ROOT / 'build/genomicranges-qualification-1/transaction-tests'
        parent.mkdir(parents=True, exist_ok=True)
        root = Path(tempfile.mkdtemp(prefix='owned-', dir=parent)).resolve()
        self.assertTrue(root.is_relative_to((ROOT / 'build').resolve()))
        (root / 'out').mkdir()
        (root / 'old.txt').write_bytes(b'original\x00bytes')
        return root

    def test_postcondition_failure_restores_old_and_retains_failed_new_metadata(self):
        root = self.fixture()
        def refuse():
            raise ValueError('deliberate failed admission postcondition')
        with self.assertRaisesRegex(ValueError, 'postcondition'):
            install(root, root / 'out', {'old.txt': b'original\x00bytes', 'new.txt': None},
                    {'old.txt': b'replacement', 'new.txt': b'unadmitted-new'}, refuse)
        self.assertEqual(b'original\x00bytes', (root / 'old.txt').read_bytes())
        self.assertFalse((root / 'new.txt').exists())
        self.assertEqual(b'unadmitted-new', (root / 'out/retained-failed-metadata/new.txt').read_bytes())
        self.assertEqual([], json.loads((root / 'out/ROLLBACK.json').read_bytes())['concurrent_changes_preserved'])

    def test_concurrent_writer_is_preserved_during_rollback(self):
        root = self.fixture()
        def concurrent_refusal():
            (root / 'old.txt').write_bytes(b'concurrent-newer-owner')
            raise ValueError('deliberate concurrent refusal')
        with self.assertRaisesRegex(ValueError, 'concurrent'):
            install(root, root / 'out', {'old.txt': b'original\x00bytes'}, {'old.txt': b'ours'}, concurrent_refusal)
        self.assertEqual(b'concurrent-newer-owner', (root / 'old.txt').read_bytes())
        self.assertEqual(['old.txt'], json.loads((root / 'out/ROLLBACK.json').read_bytes())['concurrent_changes_preserved'])

    def test_changed_precondition_is_refused_without_any_write(self):
        root = self.fixture()
        with self.assertRaises(ValueError):
            install(root, root / 'out', {'old.txt': b'wrong-predecessor'}, {'old.txt': b'replacement'}, lambda: None)
        self.assertEqual(b'original\x00bytes', (root / 'old.txt').read_bytes())


if __name__ == '__main__':
    unittest.main()
