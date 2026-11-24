"""File-boundary refusal tests. Set OMICS_GENOMICSEM_TEST_OUT to a NEW path.

Fixtures are deliberately retained for the task's bounded, receipted cleanup.
These tests never install R packages or run a scientific analysis.
"""
from pathlib import Path
import hashlib,io,os,tarfile,unittest
from genomicsem.runtime import Unavailable,artifact,extract_source,new_output,pin,source_inventory

class RuntimeBoundaries(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        name=os.environ.get('OMICS_GENOMICSEM_TEST_OUT')
        if not name:raise RuntimeError('Set OMICS_GENOMICSEM_TEST_OUT to a NEW output; fixtures are retained for explicit cleanup')
        cls.root=new_output(Path(name))

    def setUp(self):self.work=self.root/self._testMethodName;self.work.mkdir()

    def archive(self,entries):
        p=self.work/'source.tar.gz'
        with tarfile.open(p,'w:gz') as t:
            for name,data,kind in entries:
                m=tarfile.TarInfo(name)
                if kind=='link':m.type=tarfile.SYMTYPE;m.linkname='../../outside'
                else:m.size=len(data)
                t.addfile(m,io.BytesIO(data) if kind!='link' else None)
        return p

    @staticmethod
    def expected(data=b'original'):
        return {'a.txt':dict(bytes=len(data),sha256=hashlib.sha256(data).hexdigest())}

    def test_exact_closed_source(self):
        p=self.archive([('repo/a.txt',b'original','file')]);d=self.work/'source';extract_source(p,d,self.expected())
        self.assertEqual(source_inventory(d),self.expected())

    def test_source_traversal_refused_without_escape(self):
        p=self.archive([('repo/../outside',b'original','file')])
        with self.assertRaises(ValueError):extract_source(p,self.work/'source',self.expected())
        self.assertFalse((self.work/'outside').exists())

    def test_source_link_refused(self):
        p=self.archive([('repo/a.txt',b'','link')])
        with self.assertRaises(ValueError):extract_source(p,self.work/'source',self.expected())
        self.assertFalse((self.work/'source/a.txt').exists())

    def test_duplicate_source_member_refused(self):
        p=self.archive([('repo/a.txt',b'original','file'),('repo/a.txt',b'original','file')])
        with self.assertRaises(ValueError):extract_source(p,self.work/'source',self.expected())
        self.assertEqual((self.work/'source/a.txt').read_bytes(),b'original')

    def test_changed_source_bytes_refused(self):
        p=self.archive([('repo/a.txt',b'changed!','file')])
        with self.assertRaises(ValueError):extract_source(p,self.work/'source',self.expected())
        self.assertFalse((self.work/'source/a.txt').exists())

    def test_existing_output_bytes_preserved(self):
        d=self.work/'existing';d.mkdir();(d/'unique-input').write_bytes(b'unique')
        with self.assertRaises(ValueError):new_output(d)
        self.assertEqual((d/'unique-input').read_bytes(),b'unique')

    def test_offline_missing_artifact_skips(self):
        p=self.work/'never-downloaded'
        with self.assertRaises(Unavailable):artifact('https://example.invalid/private',self.expected()['a.txt'],p,offline=True)
        self.assertFalse(p.exists())

    def test_supplied_bad_digest_never_copied(self):
        p=self.work/'provided';p.write_bytes(b'changed!');before=pin(p)
        with self.assertRaises(ValueError):artifact('https://example.invalid/private',self.expected()['a.txt'],self.work/'destination',p,True)
        self.assertEqual(pin(p),before);self.assertFalse((self.work/'destination').exists())

if __name__=='__main__':unittest.main()
