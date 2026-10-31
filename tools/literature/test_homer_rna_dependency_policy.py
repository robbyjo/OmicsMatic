#!/usr/bin/env python3
"""Real setup policy/preservation checks; no network, compiler or science is invoked."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


@unittest.skipUnless(sys.platform.startswith('linux'), 'Original RNA setup policy requires Linux; native Windows runtime is unavailable')
class RnaDependencyPolicy(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory(prefix='jlinalg-rna-policy-')
        self.addCleanup(self.temporary.cleanup)
        self.root=Path(self.temporary.name)
        self.home=self.root/'home';self.resources=self.root/'resources';self.cache=self.root/'cache';self.report=self.root/'report'

    def invoke(self,expected,*extra):
        command=[sys.executable,str(Path(__file__).with_name('install_homer_rna.py')),
                 '--home',str(self.home),'--resource-root',str(self.resources),'--cache',str(self.cache),'--report',str(self.report),*extra]
        result=subprocess.run(command,capture_output=True,text=True,timeout=30)
        self.assertEqual(expected,result.returncode,result.stdout+result.stderr)
        record=json.loads(result.stderr if expected else result.stdout)
        self.assertIn('Required action:',record['required_action'])
        if expected:self.assertTrue(record['cause'])
        if self.report.exists() and (self.report/'WARNINGS.md').exists():
            warning=(self.report/'WARNINGS.md').read_text()
            self.assertIn('Required action:',warning)
            self.assertIn('SETUP SKIPPED' if expected==3 else 'SETUP FAILED',warning)
        self.assertFalse((self.home/'.jlinalg-homer-rna.json').exists())
        return record

    def test_explicit_install_permission_required(self):
        value=self.invoke(3);self.assertIn('--allow-install',value['cause']);self.assertFalse(self.home.exists());self.assertFalse(self.cache.exists())

    def test_missing_original_reference_skips_without_partial_install(self):
        value=self.invoke(3,'--allow-install');self.assertIn('resource',value['cause']);self.assertFalse(self.home.exists());self.assertFalse(self.cache.exists())

    def test_insufficient_rss_allowance_is_explicit_capability_skip(self):
        value=self.invoke(3,'--allow-install','--maximum-upstream-memory-bytes','1073741824');self.assertIn('4GiB',value['cause']);self.assertFalse(self.home.exists())

    def test_existing_incomplete_home_is_preserved(self):
        self.home.mkdir();(self.home/'preserve.txt').write_text('user-runtime\n');self.invoke(3);self.assertEqual('user-runtime\n',(self.home/'preserve.txt').read_text());self.assertEqual(['preserve.txt'],[p.name for p in self.home.iterdir()])

    def test_nesting_rejected_before_report_or_home_creation(self):
        self.cache=self.home/'cache';self.invoke(2);self.assertFalse(self.home.exists());self.assertFalse(self.report.exists())

    def test_existing_report_is_preserved(self):
        self.report.mkdir();(self.report/'preserve.txt').write_text('user-report\n');self.invoke(2);self.assertEqual(['preserve.txt'],[p.name for p in self.report.iterdir()]);self.assertFalse(self.home.exists())

    def test_unsafe_original_shell_path_skips_before_mutation(self):
        self.home=self.root/'unsafe home';value=self.invoke(3);self.assertIn('ASCII',value['cause']);self.assertFalse(self.home.exists());self.assertFalse(self.report.exists())

    def test_no_repair_or_deletion_of_incomplete_resources(self):
        self.resources.mkdir();(self.resources/'preserve.txt').write_text('user-reference\n');self.invoke(3,'--allow-install');self.assertEqual(['preserve.txt'],[p.name for p in self.resources.iterdir()]);self.assertEqual('user-reference\n',(self.resources/'preserve.txt').read_text());self.assertFalse(self.home.exists())

    def test_corrupt_software_is_unavailable_and_retained(self):
        import install_homer_rna as installer
        offline=self.root/'offline';offline.mkdir();archive=offline/'homer.v5.1.zip';archive.write_bytes(b'corrupt archive retained\n')
        with self.assertRaises(installer.core.Unavailable) as failure:installer.obtain_archive(self.cache,offline,False)
        self.assertIn('dependency rejected',str(failure.exception));self.assertEqual(b'corrupt archive retained\n',archive.read_bytes());self.assertFalse(self.home.exists())


if __name__=='__main__':unittest.main()
