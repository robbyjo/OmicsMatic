"""Exercise transactional failures and exact metadata-only source inverses."""
from pathlib import Path
import contextlib,hashlib,io,json,os,unittest,uuid,zipfile
from unittest import mock
from gsea_catalog import catalog as c,model as m,evidence as e
from gsea_dispatch_transition import DISPATCH,BEFORE,dispatcher_before
from seurat_catalog.catalog import install
from wire_signac_catalog import delta
from catalog_source_integrity import verify_source

class GseaAdmissionControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.folder=Path(os.environ.get('OMICSMATIC_GSEA_CATALOG_TEST_ROOT',str(c.ROOT/'build'/('gsea-admission-tests-'+uuid.uuid4().hex)))).resolve()
        if not cls.folder.is_relative_to((c.ROOT/'build').resolve()):raise ValueError('Keep test artifacts inside workspace build')
        cls.folder.mkdir(parents=True,exist_ok=False)

    def setUp(self):
        self.case=self.folder/self.id().rsplit('.',1)[-1];self.root=self.case/'root';self.out=self.case/'receipt';self.root.mkdir(parents=True);self.out.mkdir();(self.root/'sub').mkdir();(self.root/'a.txt').write_bytes(b'original')

    def transaction(self,post):return install(self.root,self.out,{'a.txt':b'original','sub/b.txt':None},{'a.txt':b'replacement','sub/b.txt':b'new retained'},post)

    def test_successful_owned_transaction(self):
        self.assertEqual('complete',self.transaction(lambda:'complete'));self.assertEqual(b'replacement',(self.root/'a.txt').read_bytes())

    def test_stale_before_preserves_foreign_bytes(self):
        (self.root/'a.txt').write_bytes(b'concurrent')
        with self.assertRaises(ValueError):self.transaction(lambda:None)
        self.assertEqual(b'concurrent',(self.root/'a.txt').read_bytes());self.assertFalse((self.root/'sub/b.txt').exists())

    def test_failed_postcheck_rolls_back_owned_writes_and_retains_new_data(self):
        def fail():raise ValueError('controlled failure')
        with self.assertRaises(ValueError):self.transaction(fail)
        self.assertEqual(b'original',(self.root/'a.txt').read_bytes());self.assertFalse((self.root/'sub/b.txt').exists());self.assertEqual(b'new retained',(self.out/'retained-failed-metadata/sub/b.txt').read_bytes())

    def test_failed_postcheck_preserves_concurrent_foreign_edit(self):
        def fail():
            (self.root/'a.txt').write_bytes(b'foreign');raise ValueError('controlled foreign edit')
        with self.assertRaises(ValueError):self.transaction(fail)
        self.assertEqual(b'foreign',(self.root/'a.txt').read_bytes());self.assertEqual(['a.txt'],json.loads((self.out/'ROLLBACK.json').read_bytes())['concurrent_changes_preserved'])

    def test_escape_target_is_refused(self):
        outside=self.case/'outside.txt';outside.write_bytes(b'preserve')
        with self.assertRaises(ValueError):install(self.root,self.out,{'../outside.txt':b'preserve'},{'../outside.txt':b'altered'},lambda:None)
        self.assertEqual(b'preserve',outside.read_bytes())

    def test_existing_new_target_is_preserved(self):
        (self.root/'sub/b.txt').write_bytes(b'foreign')
        with self.assertRaises(ValueError):self.transaction(lambda:None)
        self.assertEqual(b'original',(self.root/'a.txt').read_bytes());self.assertEqual(b'foreign',(self.root/'sub/b.txt').read_bytes())

    def test_before_archive_population_must_be_exact(self):
        path=self.case/'before.zip'
        with zipfile.ZipFile(path,'x') as z:z.writestr(m.META[0],b'[]')
        with self.assertRaises(ValueError):c.read_before(path)

    def test_scientific_source_cannot_use_metadata_compatibility(self):
        relative='src/main/java/org/jlinalg/enrichment/BroadGseaEngine.java';target=self.root/relative;target.parent.mkdir(parents=True);target.write_bytes(b'changed scientific source\n');data=self.root/'data';data.mkdir()
        change=delta(relative,b'original scientific source\n',target.read_bytes());(data/'fake-shared-source-compatibility.json').write_bytes(m.encoded(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',paths=[change])))
        with self.assertRaisesRegex(ValueError,'scientific/unreviewed'):verify_source(self.root,data,relative,hashlib.sha256(b'original scientific source\n').hexdigest())

    def test_exact_qualified_dispatch_inverse_and_unrelated_edit_refusal(self):
        raw=(c.ROOT/DISPATCH).read_bytes();before=dispatcher_before(raw);self.assertEqual(BEFORE,e.pin(before))
        with self.assertRaises(ValueError):dispatcher_before(raw+b'\n')

    def test_wrong_plan_status_is_refused(self):
        (self.root/'PLAN.json').write_bytes(m.encoded(dict(schema_version=1,status='unreviewed')))
        with self.assertRaises(ValueError):c.inspect(self.root)

    def test_existing_cli_output_is_preserved(self):
        (self.out/'sentinel').write_bytes(b'preserve')
        with mock.patch('sys.argv',['close_gsea_catalog','--verify','--out',str(self.out)]),contextlib.redirect_stderr(io.StringIO()):self.assertEqual(2,c.main())
        self.assertEqual(b'preserve',(self.out/'sentinel').read_bytes())

    def test_cli_output_outside_build_is_refused(self):
        target=c.ROOT/'docs'/('forbidden-gsea-'+uuid.uuid4().hex)
        with mock.patch('sys.argv',['close_gsea_catalog','--verify','--out',str(target)]),contextlib.redirect_stderr(io.StringIO()):self.assertEqual(2,c.main())
        self.assertFalse(target.exists())

if __name__=='__main__':unittest.main()
