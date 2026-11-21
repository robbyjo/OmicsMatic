from pathlib import Path
import io,tempfile,unittest,zipfile
from twosamplemr_catalog import catalog as c
from seurat_catalog.catalog import install

class TransactionTests(unittest.TestCase):
    def setUp(self):
        base=c.ROOT/'build/twosamplemr-catalog-1/unit-fixtures';base.mkdir(parents=True,exist_ok=True);self.root=Path(tempfile.mkdtemp(prefix='owned-',dir=base));self.out=self.root/'receipts';self.out.mkdir();(self.root/'a').write_bytes(b'old');(self.root/'sub').mkdir();self.expected={'a':b'old','sub/new':None};self.updates={'a':b'new','sub/new':b'created'}
    def test_exact_owned_install(self):
        self.assertEqual('ok',install(self.root,self.out,self.expected,self.updates,lambda:'ok'));self.assertEqual(b'new',(self.root/'a').read_bytes())
    def test_changed_before_state_refused_without_writes(self):
        (self.root/'a').write_bytes(b'foreign')
        with self.assertRaises(ValueError):install(self.root,self.out,self.expected,self.updates,lambda:None)
        self.assertEqual(b'foreign',(self.root/'a').read_bytes());self.assertFalse((self.root/'sub/new').exists())
    def test_postfailure_restores_old_and_retains_new(self):
        def fail():raise ValueError('controlled post-write failure')
        with self.assertRaises(ValueError):install(self.root,self.out,self.expected,self.updates,fail)
        self.assertEqual(b'old',(self.root/'a').read_bytes());self.assertEqual(b'created',(self.out/'retained-failed-metadata/sub/new').read_bytes())
    def test_concurrent_foreign_edit_is_never_overwritten(self):
        def fail():(self.root/'a').write_bytes(b'concurrent');raise ValueError('controlled concurrent edit')
        with self.assertRaises(ValueError):install(self.root,self.out,self.expected,self.updates,fail)
        self.assertEqual(b'concurrent',(self.root/'a').read_bytes());self.assertTrue((self.out/'ROLLBACK.json').is_file())
    def test_escaping_owned_target_refused(self):
        with self.assertRaises(ValueError):install(self.root,self.out,{'../outside':None},{'../outside':b'x'},lambda:None)
    def test_before_archive_requires_exact_eight_documents(self):
        path=self.root/'wrong.zip'
        with zipfile.ZipFile(path,'w') as archive:archive.writestr('unexpected',b'{}')
        with self.assertRaises(ValueError):c.read_before(path)
if __name__=='__main__':unittest.main()
