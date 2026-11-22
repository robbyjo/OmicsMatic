"""Actual R installer policy/reuse checks; no download or package installation."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]


class SctDependencyPolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rscript=os.environ.get('JLINALG_SCT_RSCRIPT')
        cls.library=os.environ.get('JLINALG_SCT_R_LIBRARY')
        if not cls.rscript or not cls.library:raise unittest.SkipTest('Set actual pinned JLINALG_SCT_RSCRIPT and JLINALG_SCT_R_LIBRARY')
    def call(self,lib,report,policy,profile):
        return subprocess.run([self.rscript,'--vanilla',str(ROOT/'tools/literature/install_sctransform_dependencies.R'),str(lib),str(report),policy,profile],capture_output=True,text=True,timeout=120)
    def test_forbidden_install_skips_and_does_not_create_library(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);lib=root/'missing';report=root/'report';p=self.call(lib,report,'forbid','vst-v2')
            self.assertEqual(3,p.returncode,p.stdout+p.stderr);self.assertFalse(lib.exists());self.assertIn('ANALYSIS SKIPPED',(report/'WARNINGS.md').read_text());self.assertIn('Required action',(report/'WARNINGS.md').read_text());self.assertIn('skipped-install-not-permitted',(report/'dependency-status.yaml').read_text())
    def test_unmanaged_library_is_preserved_before_any_repository_access(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);lib=root/'existing';lib.mkdir();(lib/'keep').write_text('original');report=root/'report'
            p=self.call(lib,report,'allow','seurat-v2');self.assertEqual(3,p.returncode,p.stdout+p.stderr);self.assertEqual('original',(lib/'keep').read_text());self.assertIn('skipped-unmanaged-library',(report/'dependency-status.yaml').read_text())
    def test_actual_pinned_library_reuse_succeeds_with_install_forbidden(self):
        with tempfile.TemporaryDirectory() as t:
            report=Path(t)/'report';p=self.call(self.library,report,'forbid','seurat-v2');self.assertEqual(0,p.returncode,p.stdout+p.stderr);self.assertIn('available-not-yet-analysis-validated',(report/'dependency-status.yaml').read_text());self.assertIn('install_authorized: false',(report/'dependency-status.yaml').read_text());self.assertFalse(any(report.rglob('downloads')))


if __name__=='__main__':unittest.main()
