"""No-network STAR build authorization, preservation and embedded-byte checks."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import install_star as installer

SCRIPT=Path(__file__).with_name('install_star.py')


class StarInstallationPolicyTest(unittest.TestCase):
    def invoke(self,root,*extra):
        p=subprocess.run([sys.executable,str(SCRIPT),'--environment',str(root/'environment'),'--cache',str(root/'cache'),
            '--toolchain',str(root/'toolchain'),'--report',str(root/'report'),*extra],capture_output=True,text=True,timeout=30)
        self.assertEqual(3,p.returncode,p.stdout+p.stderr)
        self.assertIn('Required action',(root/'report/WARNINGS.md').read_text(encoding='utf-8'))
        return json.loads((root/'report/dependency-status.json').read_text(encoding='utf-8'))

    def test_forbidden_download_never_creates_runtime_cache_or_toolchain(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);r=self.invoke(root)
            self.assertEqual('skipped-install-not-permitted',r['status'])
            for name in ['environment','cache','toolchain']:self.assertFalse((root/name).exists())

    def test_source_build_requires_explicit_authorization(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);r=self.invoke(root,'--allow-download')
            self.assertEqual('skipped-source-build-not-permitted',r['status'])
            self.assertFalse((root/'cache').exists());self.assertFalse((root/'toolchain').exists())

    def test_existing_unmanaged_runtime_is_preserved(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'environment').mkdir();sentinel=root/'environment/user-file';sentinel.write_text('keep')
            r=self.invoke(root,'--allow-download','--allow-source-build')
            self.assertEqual('skipped-existing-environment',r['status']);self.assertEqual('keep',sentinel.read_text())
            self.assertFalse((root/'cache').exists());self.assertFalse((root/'toolchain').exists())

    def test_corrupt_managed_runtime_is_preserved_without_repair_or_download(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);env=root/'environment';env.mkdir();binary=env/'runtime.exe';binary.write_bytes(b'original')
            identity=installer.STAR['sha256']+':'+installer.digest(installer.LOCK)+':'+installer.FLAGS+':sse4.1:embed-v1:quant-init:'+installer.digest(installer.PATCH)
            installer.mark(env,installer.MARKER,identity);binary.write_bytes(b'tampered')
            r=self.invoke(root,'--allow-download','--allow-source-build')
            self.assertEqual('skipped-existing-environment',r['status']);self.assertEqual(b'tampered',binary.read_bytes())
            self.assertFalse((root/'cache').exists());self.assertFalse((root/'toolchain').exists())

    @unittest.skipUnless(sys.platform=='win32','Private Windows installer')
    def test_corrupt_archive_is_retained_without_extraction_or_toolchain_creation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'cache').mkdir();archive=root/'cache'/installer.STAR['filename'];archive.write_bytes(b'corrupt source')
            r=self.invoke(root,'--allow-download','--allow-source-build')
            self.assertEqual('skipped-dependency-unavailable',r['status']);self.assertIn('integrity mismatch',r['error'])
            self.assertEqual(b'corrupt source',archive.read_bytes());self.assertFalse((root/'environment').exists());self.assertFalse((root/'toolchain').exists())

    def test_embedded_default_bytes_are_exact_including_newlines_and_nonascii(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);data=b'abc\n\r\n\x00\x80\xff';(root/'parametersDefault').write_bytes(data);installer.embed_defaults(root)
            text=(root/'parametersDefault.xxd').read_text(encoding='ascii')
            import re
            self.assertEqual(data,bytes(int(s,16) for s in re.findall(r'0x([0-9a-f]{2})',text)))
            self.assertIn('parametersDefault_len = '+str(len(data))+';',text)

    def test_initialization_patch_rejects_nonpinned_source_without_changing_it(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);p=root/'ParametersGenome.cpp';p.write_bytes(b'user source')
            with self.assertRaisesRegex(ValueError,'pinned source'):installer.initialize_transform_quant(root)
            self.assertEqual(b'user source',p.read_bytes())


if __name__=='__main__':unittest.main()
