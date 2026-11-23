"""Failed admission preserves predecessors, failed metadata and concurrent owners."""
from pathlib import Path
import json,tempfile,unittest
from unittest.mock import patch
from squidpy_catalog.primary import ROOT
from seurat_catalog import catalog as transaction


class TransactionTests(unittest.TestCase):
    def fixture(self):
        parent=ROOT/'build/squidpy-catalog-149/transaction-tests';parent.mkdir(parents=True,exist_ok=True)
        root=Path(tempfile.mkdtemp(prefix='owned-',dir=parent)).resolve()
        self.assertTrue(root.is_relative_to((ROOT/'build').resolve()));(root/'out').mkdir()
        (root/'old.txt').write_bytes(b'original\x00bytes');return root

    def test_postcondition_failure_restores_and_retains_unadmitted_new_file(self):
        root=self.fixture()
        def refuse():raise ValueError('deliberate failed postcondition')
        with self.assertRaisesRegex(ValueError,'postcondition'):
            transaction.install(root,root/'out',{'old.txt':b'original\x00bytes','new.txt':None},
                {'old.txt':b'replacement','new.txt':b'unadmitted-new'},refuse)
        self.assertEqual(b'original\x00bytes',(root/'old.txt').read_bytes());self.assertFalse((root/'new.txt').exists())
        self.assertEqual(b'unadmitted-new',(root/'out/retained-failed-metadata/new.txt').read_bytes())
        self.assertEqual([],json.loads((root/'out/ROLLBACK.json').read_bytes())['concurrent_changes_preserved'])

    def test_concurrent_postcondition_writer_is_preserved(self):
        root=self.fixture()
        def concurrent():
            (root/'old.txt').write_bytes(b'concurrent-newer-owner');raise ValueError('concurrent refusal')
        with self.assertRaisesRegex(ValueError,'concurrent'):
            transaction.install(root,root/'out',{'old.txt':b'original\x00bytes'},{'old.txt':b'ours'},concurrent)
        self.assertEqual(b'concurrent-newer-owner',(root/'old.txt').read_bytes())
        self.assertEqual(['old.txt'],json.loads((root/'out/ROLLBACK.json').read_bytes())['concurrent_changes_preserved'])

    def test_interleaved_change_to_next_target_is_detected_and_preserved(self):
        root=self.fixture();(root/'second.txt').write_bytes(b'second-old');original=transaction.os.replace
        def interleave(source,target):
            original(source,target)
            if Path(target)==root/'old.txt':(root/'second.txt').write_bytes(b'concurrent-second')
        with patch.object(transaction.os,'replace',side_effect=interleave),self.assertRaisesRegex(ValueError,'Concurrent'):
            transaction.install(root,root/'out',{'old.txt':b'original\x00bytes','second.txt':b'second-old'},
                {'old.txt':b'ours','second.txt':b'ours-second'},lambda:None)
        self.assertEqual(b'original\x00bytes',(root/'old.txt').read_bytes())
        self.assertEqual(b'concurrent-second',(root/'second.txt').read_bytes())

    def test_changed_precondition_is_refused_before_any_write(self):
        root=self.fixture()
        with self.assertRaises(ValueError):transaction.install(root,root/'out',{'old.txt':b'wrong'},{'old.txt':b'ours'},lambda:None)
        self.assertEqual(b'original\x00bytes',(root/'old.txt').read_bytes())

    def test_path_escape_is_refused_before_writing(self):
        root=self.fixture()
        with self.assertRaises(ValueError):transaction.install(root,root/'out',{'../escape.txt':None},{'../escape.txt':b'ours'},lambda:None)
        self.assertFalse((root.parent/'escape.txt').exists())

    def test_success_checks_installed_bytes_and_returns_postcondition(self):
        root=self.fixture()
        def post():
            self.assertEqual(b'new\x00bytes',(root/'old.txt').read_bytes());return dict(passed=True)
        self.assertEqual(dict(passed=True),transaction.install(root,root/'out',{'old.txt':b'original\x00bytes'},{'old.txt':b'new\x00bytes'},post))


if __name__=='__main__':unittest.main()
