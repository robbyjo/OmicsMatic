"""Installer control-flow tests: no network, environment creation or native process."""
import argparse
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import install_scrublet as installer


class ScrubletPolicyTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.args=argparse.Namespace(environment=self.root/'env',report=self.root/'report',allow_install=False,allow_source_build=False)
    def tearDown(self):self.temp.cleanup()
    def call(self,probe=None):
        with contextlib.redirect_stdout(io.StringIO()),patch.object(installer,'probe',probe or (lambda *a: {'scrublet':'0.2.3'})),patch.object(installer.venv.EnvBuilder,'create',side_effect=AssertionError('No installation in policy tests')),patch.object(installer,'run',side_effect=AssertionError('No subprocess in policy tests')):
            code=installer.install(self.args)
        return code,json.loads((self.args.report/'dependency-status.json').read_text())
    def marker(self,identity=None):
        self.args.environment.mkdir();(self.args.environment/installer.MARKER).write_text(json.dumps({'lock_sha256':identity or installer.digest(installer.LOCK)}))
    def test_forbidden_install_skips_with_prominent_recovery(self):
        code,data=self.call();self.assertEqual(3,code);self.assertEqual('skipped-install-not-permitted',data['status']);self.assertFalse(self.args.environment.exists());self.assertIn('ANALYSIS SKIPPED',(self.args.report/'WARNINGS.md').read_text());self.assertIn('--allow-source-build',data['required_action'])
    def test_source_build_permission_is_required_separately(self):
        self.args.allow_install=True
        with patch.object(installer.sys,'platform','win32'),patch.object(installer.sys,'version_info',(3,12)),patch.object(installer.platform,'machine',return_value='AMD64'):
            code,data=self.call()
        self.assertEqual(3,code);self.assertEqual('skipped-source-build-not-permitted',data['status']);self.assertFalse(self.args.environment.exists())
    def test_wrong_platform_skips_without_creating_environment(self):
        self.args.allow_install=True;self.args.allow_source_build=True
        with patch.object(installer.sys,'platform','linux'):code,data=self.call()
        self.assertEqual(3,code);self.assertEqual('skipped-platform-unavailable',data['status'])
    def test_existing_unmanaged_environment_is_preserved(self):
        self.args.environment.mkdir();sentinel=self.args.environment/'keep';sentinel.write_text('original')
        self.args.allow_install=True;code,data=self.call();self.assertEqual(3,code);self.assertIn('unmanaged',data['error']);self.assertEqual('original',sentinel.read_text())
    def test_reuse_needs_no_install_or_build_permission(self):
        self.marker();code,data=self.call();self.assertEqual(0,code);self.assertTrue(data['runtime_reused']);self.assertFalse(data['install_authorized']);self.assertFalse(data['source_build_authorized'])
    def test_changed_lock_preserves_environment_and_skips(self):
        self.marker('changed');before=(self.args.environment/installer.MARKER).read_bytes();code,data=self.call();self.assertEqual(3,code);self.assertIn('lock differs',data['error']);self.assertEqual(before,(self.args.environment/installer.MARKER).read_bytes())
    def test_failed_probe_is_skip_and_attempts_are_retained(self):
        self.marker()
        def bad(*args):raise RuntimeError('Native extension DLL failed to load')
        code,data=self.call(bad);self.assertEqual(3,code);self.assertIn('DLL',data['error']);first=data['attempt'];code,data=self.call();self.assertEqual(0,code);self.assertTrue(Path(first).is_dir());self.assertEqual(1,len(data['history']));self.assertNotEqual(first,data['attempt'])


if __name__=='__main__':unittest.main()
