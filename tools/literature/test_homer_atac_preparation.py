"""Real shared transport regression checks with synthetic content pins; no compiler/network/science."""
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import install_homer_atac as atac


class AtacArchivePreparationTest(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory();self.addCleanup(self.temporary.cleanup);self.root=Path(self.temporary.name);self.cache=self.root/'cache';self.cache.mkdir();self.reference=self.root/'offline';self.reference.mkdir();self.content=b'synthetic transport fixture;not an upstream archive\n';self.pin=dict(bytes=len(self.content),sha256=hashlib.sha256(self.content).hexdigest(),url='https://invalid.example/never-contact');self.name='homer.v5.1.zip';(self.reference/self.name).write_bytes(self.content)
        self.addCleanup(patch.stopall);patch.dict(atac.core.LOCK['archives'],{self.name:self.pin}).start();patch.dict(atac.LOCK['archives'],{self.name:dict(self.pin)}).start()

    def test_offline_cache_miss_uses_real_shared_copy_with_iterable_references(self):
        source=self.reference/self.name;before=source.read_bytes();result=atac.obtain_archive(self.cache,self.reference,False);self.assertEqual(result,self.cache/self.name);self.assertEqual(result.read_bytes(),self.content);self.assertEqual(source.read_bytes(),before)

    def test_missing_reference_with_download_forbidden_is_unavailable_without_network(self):
        with patch.object(atac.core.urllib.request,'urlopen',side_effect=AssertionError('Network must not be contacted')):
            with self.assertRaises(atac.core.Unavailable):atac.obtain_archive(self.cache,None,False)
        self.assertFalse((self.cache/self.name).exists())

    def test_separate_atac_container_pin_rejects_a_different_shared_core_pin(self):
        atac.LOCK['archives'][self.name]=dict(self.pin,sha256='0'*64)
        with self.assertRaisesRegex(ValueError,'separate ATAC content lock'):atac.obtain_archive(self.cache,self.reference,False)
        self.assertEqual((self.cache/self.name).read_bytes(),self.content)

    def test_corrupt_existing_cache_is_preserved_instead_of_replaced(self):
        target=self.cache/self.name;target.write_bytes(b'preserve-corrupt-cache\n')
        with self.assertRaisesRegex(ValueError,'Corrupt/linked pinned archive'):atac.obtain_archive(self.cache,self.reference,False)
        self.assertEqual(target.read_bytes(),b'preserve-corrupt-cache\n');self.assertEqual((self.reference/self.name).read_bytes(),self.content)


if __name__=='__main__':unittest.main()
