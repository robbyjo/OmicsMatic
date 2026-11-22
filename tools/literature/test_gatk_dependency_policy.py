"""No-network GATK installer policy checks."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT=Path(__file__).with_name('install_gatk.py')
spec=importlib.util.spec_from_file_location('gatk_installer',SCRIPT);installer=importlib.util.module_from_spec(spec);spec.loader.exec_module(installer)


class GatkInstallationPolicyTest(unittest.TestCase):
    def invoke(self,root,*extra):
        p=subprocess.run([sys.executable,str(SCRIPT),'--environment',str(root/'environment'),'--archive',str(root/'cache/gatk.zip'),'--report',str(root/'report'),*extra],capture_output=True,text=True,timeout=30)
        self.assertEqual(3,p.returncode,p.stdout+p.stderr);self.assertIn('Required action',(root/'report/WARNINGS.md').read_text())
        return json.loads((root/'report/dependency-status.json').read_text())

    def test_forbidden_install_does_not_create_runtime_or_cache(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);r=self.invoke(root);self.assertEqual('skipped-install-not-permitted',r['status']);self.assertFalse((root/'environment').exists());self.assertFalse((root/'cache').exists())

    def test_unmanaged_runtime_is_preserved(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'environment').mkdir();sentinel=root/'environment/user-data';sentinel.write_text('keep')
            r=self.invoke(root,'--allow-download');self.assertEqual('skipped-existing-environment',r['status']);self.assertEqual('keep',sentinel.read_text());self.assertFalse((root/'cache').exists())

    def test_corrupt_archive_is_retained_without_extraction(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'cache').mkdir();archive=root/'cache/gatk.zip';archive.write_bytes(b'invalid release')
            r=self.invoke(root,'--allow-download');self.assertEqual('skipped-dependency-unavailable',r['status']);self.assertIn('integrity',r['error']);self.assertEqual(b'invalid release',archive.read_bytes());self.assertFalse((root/'environment').exists())

    def test_forged_or_corrupt_managed_runtime_is_not_trusted(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);e=root/'environment';e.mkdir();(e/'gatk.jar').write_bytes(b'wrong application');(e/installer.MARKER).write_text(json.dumps({'version':installer.VERSION,'archive_sha256':installer.ARCHIVE_SHA256,'jar_sha256':installer.JAR_SHA256}))
            r=self.invoke(root,'--allow-download');self.assertEqual('skipped-existing-environment',r['status']);self.assertEqual(b'wrong application',(e/'gatk.jar').read_bytes())


if __name__=='__main__':unittest.main()
