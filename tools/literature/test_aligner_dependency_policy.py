"""No-network aligner installation/build-policy and archive-boundary checks."""
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest

SCRIPT=Path(__file__).with_name('install_aligner_dependencies.py')
spec=importlib.util.spec_from_file_location('aligner_installer',SCRIPT)
installer=importlib.util.module_from_spec(spec);spec.loader.exec_module(installer)


class AlignerInstallationPolicyTest(unittest.TestCase):
    def invoke(self,root,profile,*extra):
        p=subprocess.run([sys.executable,str(SCRIPT),'--profile',profile,'--environment',str(root/'environment'),
            '--cache',str(root/'cache'),'--toolchain',str(root/'toolchain'),'--report',str(root/'report'),*extra],capture_output=True,text=True,timeout=30)
        self.assertEqual(3,p.returncode,p.stdout+p.stderr)
        self.assertIn('Required action',(root/'report/WARNINGS.md').read_text(encoding='utf-8'))
        return json.loads((root/'report/dependency-status.json').read_text(encoding='utf-8'))

    def test_forbidden_download_does_not_create_runtime_cache_or_toolchain(self):
        for profile in ['bowtie2','bwa']:
            with tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp);r=self.invoke(root,profile)
                self.assertEqual('skipped-install-not-permitted',r['status'])
                for name in ['environment','cache','toolchain']:self.assertFalse((root/name).exists())

    def test_unmanaged_runtime_is_preserved_for_both_profiles(self):
        for profile in ['bowtie2','bwa']:
            with tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp);(root/'environment').mkdir();sentinel=root/'environment/user-data';sentinel.write_text('keep')
                r=self.invoke(root,profile,'--allow-download','--allow-source-build')
                self.assertEqual('skipped-existing-environment',r['status']);self.assertEqual('keep',sentinel.read_text())
                self.assertFalse((root/'cache').exists());self.assertFalse((root/'toolchain').exists())

    @unittest.skipUnless(sys.platform=='win32','Windows-only private build runtime')
    def test_bwa_requires_explicit_source_build_authorization(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);r=self.invoke(root,'bwa','--allow-download')
            self.assertEqual('skipped-source-build-not-permitted',r['status'])
            self.assertFalse((root/'cache').exists());self.assertFalse((root/'toolchain').exists())

    @unittest.skipUnless(sys.platform=='win32','Windows-only private binary runtime')
    def test_corrupt_cached_archive_is_retained_and_never_extracted(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'cache').mkdir();archive=root/'cache'/installer.BOWTIE['filename'];archive.write_bytes(b'corrupt release')
            r=self.invoke(root,'bowtie2','--allow-download')
            self.assertEqual('skipped-dependency-unavailable',r['status']);self.assertIn('integrity mismatch',r['error'])
            self.assertEqual(b'corrupt release',archive.read_bytes());self.assertFalse((root/'environment').exists())

    def test_archive_paths_cannot_escape_staging(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp).resolve()
            for name in ['../outside','/absolute','C:/outside','usr/../../outside','usr\\..\\outside']:
                with self.assertRaises(ValueError):installer.target(root,name)
            self.assertEqual(root/'directory with spaces/file',installer.target(root,'directory with spaces/file'))

    def test_archive_links_cannot_escape_staging(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp).resolve();raw=io.BytesIO()
            with tarfile.open(fileobj=raw,mode='w') as tar:
                entry=tarfile.TarInfo('usr/bin/unsafe');entry.type=tarfile.SYMTYPE;entry.linkname='../../../outside';tar.addfile(entry)
            raw.seek(0)
            with tarfile.open(fileobj=raw,mode='r:') as tar:
                with self.assertRaisesRegex(ValueError,'escapes'):installer.extract_tar(tar,root)
            self.assertFalse((root/'usr/bin/unsafe').exists())


if __name__=='__main__':unittest.main()
