"""Actual owned-write rollback and concurrent-edit preservation controls."""
from pathlib import Path
import tempfile,unittest
from unittest.mock import patch
from seurat_catalog.catalog import install

class TransactionTests(unittest.TestCase):
    def fixture(self,root):
        (root/'old').write_bytes(b'before');out=root/'logs';out.mkdir();return out,{'old':b'before','new':None},{'old':b'after','new':b'new'}
    def test_successful_owned_install(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);out,before,after=self.fixture(root);result=install(root,out,before,after,lambda:'checked');self.assertEqual('checked',result);self.assertEqual(b'after',(root/'old').read_bytes());self.assertEqual(b'new',(root/'new').read_bytes())
    def test_stale_predecessor_refused_without_write(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);out,before,after=self.fixture(root);(root/'old').write_bytes(b'foreign')
            with self.assertRaises(ValueError):install(root,out,before,after,lambda:None)
            self.assertEqual(b'foreign',(root/'old').read_bytes());self.assertFalse((root/'new').exists())
    def test_postcheck_failure_restores_owned_old_and_removes_owned_new(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);out,before,after=self.fixture(root)
            def fail():raise ValueError('retained postcheck failure')
            with self.assertRaises(ValueError):install(root,out,before,after,fail)
            self.assertEqual(b'before',(root/'old').read_bytes());self.assertFalse((root/'new').exists())
    def test_concurrent_foreign_old_edit_is_preserved(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);out,before,after=self.fixture(root)
            def fail():(root/'old').write_bytes(b'foreign');raise ValueError('concurrent edit')
            with self.assertRaises(Exception):install(root,out,before,after,fail)
            self.assertEqual(b'foreign',(root/'old').read_bytes())
    def test_concurrent_foreign_new_edit_is_preserved(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);out,before,after=self.fixture(root)
            def fail():(root/'new').write_bytes(b'foreign');raise ValueError('concurrent new edit')
            with self.assertRaises(Exception):install(root,out,before,after,fail)
            self.assertEqual(b'foreign',(root/'new').read_bytes());self.assertEqual(b'before',(root/'old').read_bytes())
    def test_escape_from_workspace_is_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);out=root/'logs';out.mkdir()
            with self.assertRaises(ValueError):install(root,out,{'../escape':None},{'../escape':b'bad'},lambda:None)

if __name__=='__main__':unittest.main()
