"""Exercise the reused atomic installer under refusal and concurrent writers."""
from pathlib import Path
import json,tempfile,unittest
from unittest.mock import patch
from impute_catalog.primary import ROOT
from seurat_catalog import catalog as transaction

class TransactionTests(unittest.TestCase):
    def fixture(self):
        parent=ROOT/'build/impute-catalog-574/transaction-tests';parent.mkdir(parents=True,exist_ok=True)
        root=Path(tempfile.mkdtemp(prefix='owned-',dir=parent)).resolve();(root/'out').mkdir();(root/'old.txt').write_bytes(b'original\x00bytes');return root
    def test_postcondition_failure_restores_and_keeps_failed_bytes(self):
        root=self.fixture()
        def refuse():raise ValueError('deliberate postcondition refusal')
        with self.assertRaisesRegex(ValueError,'postcondition'):transaction.install(root,root/'out',{'old.txt':b'original\x00bytes','new.txt':None},{'old.txt':b'replacement','new.txt':b'unadmitted'},refuse)
        self.assertEqual(b'original\x00bytes',(root/'old.txt').read_bytes());self.assertFalse((root/'new.txt').exists());self.assertEqual(b'unadmitted',(root/'out/retained-failed-metadata/new.txt').read_bytes())
    def test_postcondition_concurrent_writer_is_preserved(self):
        root=self.fixture()
        def writer():(root/'old.txt').write_bytes(b'concurrent-owner');raise ValueError('concurrent refusal')
        with self.assertRaisesRegex(ValueError,'concurrent'):transaction.install(root,root/'out',{'old.txt':b'original\x00bytes'},{'old.txt':b'ours'},writer)
        self.assertEqual(b'concurrent-owner',(root/'old.txt').read_bytes());self.assertEqual(['old.txt'],json.loads((root/'out/ROLLBACK.json').read_bytes())['concurrent_changes_preserved'])
    def test_change_between_target_writes_is_detected(self):
        root=self.fixture();(root/'second.txt').write_bytes(b'second');original=transaction.os.replace
        def interleave(source,target):
            original(source,target)
            if Path(target)==root/'old.txt':(root/'second.txt').write_bytes(b'newer-second')
        with patch.object(transaction.os,'replace',side_effect=interleave),self.assertRaisesRegex(ValueError,'Concurrent'):transaction.install(root,root/'out',{'old.txt':b'original\x00bytes','second.txt':b'second'},{'old.txt':b'ours','second.txt':b'ours-second'},lambda:None)
        self.assertEqual(b'original\x00bytes',(root/'old.txt').read_bytes());self.assertEqual(b'newer-second',(root/'second.txt').read_bytes())
    def test_path_escape_and_changed_precondition_are_refused(self):
        root=self.fixture()
        with self.assertRaises(ValueError):transaction.install(root,root/'out',{'../escape':None},{'../escape':b'ours'},lambda:None)
        with self.assertRaises(ValueError):transaction.install(root,root/'out',{'old.txt':b'wrong'},{'old.txt':b'ours'},lambda:None)
        self.assertEqual(b'original\x00bytes',(root/'old.txt').read_bytes());self.assertFalse((root.parent/'escape').exists())

if __name__=='__main__':unittest.main()
