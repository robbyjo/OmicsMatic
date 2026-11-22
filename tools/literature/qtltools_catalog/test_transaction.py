"""Exercise owned metadata writes,rollback and concurrent-edit preservation in retained fixtures."""
import json,unittest,uuid
from qtltools_catalog import primary as p
from seurat_catalog.catalog import install

class TransactionTests(unittest.TestCase):
    def setUp(self):
        self.root=p.ROOT/'build/qtltools-catalog-1/transaction-cases'/uuid.uuid4().hex;self.root.mkdir(parents=True);self.out=self.root/'evidence';self.out.mkdir();(self.root/'old.txt').write_bytes(b'original\n')

    def test_complete_owned_install(self):
        result=install(self.root,self.out,{'old.txt':b'original\n','new.txt':None},{'old.txt':b'changed\n','new.txt':b'new\n'},lambda:'complete');self.assertEqual('complete',result);self.assertEqual(b'changed\n',(self.root/'old.txt').read_bytes());self.assertEqual(b'new\n',(self.root/'new.txt').read_bytes())

    def test_foreign_prewrite_change_is_refused(self):
        (self.root/'old.txt').write_bytes(b'foreign\n')
        with self.assertRaises(ValueError):install(self.root,self.out,{'old.txt':b'original\n'},{'old.txt':b'changed\n'},lambda:None)
        self.assertEqual(b'foreign\n',(self.root/'old.txt').read_bytes())

    def test_postwrite_failure_restores_old_and_retains_new(self):
        def fail():raise ValueError('intentional postwrite refusal')
        with self.assertRaises(ValueError):install(self.root,self.out,{'old.txt':b'original\n','new.txt':None},{'old.txt':b'changed\n','new.txt':b'new\n'},fail)
        self.assertEqual(b'original\n',(self.root/'old.txt').read_bytes());self.assertFalse((self.root/'new.txt').exists());self.assertEqual(b'new\n',(self.out/'retained-failed-metadata/new.txt').read_bytes());self.assertEqual([],json.loads((self.out/'ROLLBACK.json').read_bytes())['concurrent_changes_preserved'])

    def test_foreign_postwrite_change_is_preserved(self):
        def fail():(self.root/'old.txt').write_bytes(b'foreign-after\n');raise ValueError('intentional postwrite refusal')
        with self.assertRaises(ValueError):install(self.root,self.out,{'old.txt':b'original\n','new.txt':None},{'old.txt':b'changed\n','new.txt':b'new\n'},fail)
        self.assertEqual(b'foreign-after\n',(self.root/'old.txt').read_bytes());self.assertEqual(['old.txt'],json.loads((self.out/'ROLLBACK.json').read_bytes())['concurrent_changes_preserved']);self.assertTrue((self.out/'retained-failed-metadata/new.txt').is_file())

    def test_escaping_target_is_refused_before_writes(self):
        with self.assertRaises(ValueError):install(self.root,self.out,{'../foreign.txt':None},{'../foreign.txt':b'forbidden\n'},lambda:None)
        self.assertEqual(b'original\n',(self.root/'old.txt').read_bytes())

if __name__=='__main__':unittest.main()
