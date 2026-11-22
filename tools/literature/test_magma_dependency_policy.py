"""Private MAGMA download/reuse/integrity policy; no network or executable invocation."""
import argparse
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import install_magma as installer

class MagmaDependencyPolicyTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name);self.args=argparse.Namespace(environment=self.root/'env',archive=self.root/'archive.zip',report=self.root/'report',allow_download=False)
    def tearDown(self):self.temp.cleanup()
    def call(self,available=False,probe=None):
        with contextlib.redirect_stdout(io.StringIO()),patch.object(installer,'download',side_effect=AssertionError('No network in policy tests')),patch.object(installer,'probe',probe or (lambda *a:None)),patch.object(installer,'available',return_value=available):code=installer.install(self.args)
        return code,json.loads((self.args.report/'dependency-status.json').read_text())
    def test_forbidden_download_skips_and_names_recovery_and_upstream_terms(self):
        c,d=self.call();self.assertEqual(3,c);self.assertEqual('skipped-install-not-permitted',d['status']);self.assertFalse(self.args.environment.exists());self.assertIn('ANALYSIS SKIPPED',(self.args.report/'WARNINGS.md').read_text());self.assertIn('install_magma.py --allow-download',d['required_action']);self.assertIn('may not be redistributed or modified',d['upstream_terms'])
    def test_unmanaged_or_changed_install_is_preserved(self):
        self.args.environment.mkdir();f=self.args.environment/'keep';f.write_text('original');self.args.allow_download=True;c,d=self.call();self.assertEqual(3,c);self.assertIn('preserved',d['error']);self.assertEqual('original',f.read_text())
    def test_verified_reuse_needs_no_download_permission(self):
        c,d=self.call(True);self.assertEqual(0,c);self.assertTrue(d['runtime_reused']);self.assertFalse(d['download_authorized'])
    def test_failed_version_probe_skips_instead_of_claiming_available(self):
        def fail(*a):raise RuntimeError('Exact version/load probe failed')
        c,d=self.call(True,fail);self.assertEqual(3,c);self.assertIn('version/load',d['error'])
    def test_bad_archive_is_not_extracted_or_executed(self):
        self.args.allow_download=True;self.args.archive.write_bytes(b'HTML redirect, not upstream ZIP');c,d=self.call();self.assertEqual(3,c);self.assertIn('failed integrity',d['error']);self.assertFalse(self.args.environment.exists());self.assertEqual(b'HTML redirect, not upstream ZIP',self.args.archive.read_bytes())
    def test_wrong_host_is_skipped(self):
        self.args.allow_download=True
        with patch.object(installer.sys,'platform','linux'):c,d=self.call()
        self.assertEqual(3,c);self.assertEqual('skipped-platform-unavailable',d['status'])
    def test_changed_or_extra_archive_member_is_rejected_before_extraction(self):
        for member in ['../outside','magma.exe']:
            archive=self.root/(str(len(member))+'.zip')
            with zipfile.ZipFile(archive,'w') as z:z.writestr(member,'not an executable')
            dest=self.root/('dest'+str(len(member)));dest.mkdir()
            with self.assertRaisesRegex(ValueError,'Unexpected archive members'):installer.extract(archive,dest)
            self.assertEqual([],list(dest.iterdir()))
    def test_previous_failures_and_distinct_attempts_are_retained(self):
        self.call();c,d=self.call(True);self.assertEqual(0,c);self.assertEqual(1,len(d['history']));self.assertEqual('skipped-install-not-permitted',d['history'][0]['status']);self.assertEqual(2,len(list((self.args.report/'attempts').iterdir())))

if __name__=='__main__':unittest.main()
