"""REGENIE permission/integrity/reuse guards; these tests execute no scientific binary."""
import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import install_regenie as installer

class RegenieDependencyPolicyTest(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory();self.root=Path(self.temporary.name)
        self.args=argparse.Namespace(environment=self.root/'environment',cache=self.root/'cache',report=self.root/'report',allow_download=False,allow_source_build=False,cygwin_root=self.root/'cygwin',build_threads=1,build_timeout=60)
    def tearDown(self):self.temporary.cleanup()
    def install(self,selected='windows-cygwin-source'):
        with patch.object(installer,'profile',return_value=selected),patch.object(installer,'download',side_effect=AssertionError('Network must not be used in policy checks')):
            return installer.install(self.args)
    def test_forbidden_download_skips_without_creating_a_runtime(self):
        record,code=self.install();self.assertEqual(3,code);self.assertEqual('skipped-install-not-permitted',record['status']);self.assertFalse(self.args.environment.exists())
        text=(self.args.report/'WARNINGS.md').read_text();self.assertIn('Required action',text);self.assertIn('skip dependent REGENIE analyses',text)
    def test_source_build_requires_its_own_explicit_permission(self):
        self.args.allow_download=True;record,code=self.install();self.assertEqual(3,code);self.assertEqual('skipped-source-build-not-permitted',record['status']);self.assertFalse(self.args.environment.exists())
    def test_unmanaged_environment_is_preserved_even_when_downloads_are_authorized(self):
        self.args.allow_download=True;self.args.allow_source_build=True;self.args.environment.mkdir();sentinel=self.args.environment/'owned-by-user';sentinel.write_text('preserve')
        record,code=self.install();self.assertEqual(3,code);self.assertEqual('skipped-existing-environment',record['status']);self.assertEqual('preserve',sentinel.read_text())
    def test_wrong_architecture_is_explicitly_skipped(self):
        self.assertIsNone(installer.profile('linux','aarch64'));record,code=self.install(None);self.assertEqual(3,code);self.assertEqual('skipped-platform-unavailable',record['status'])
    def test_verified_reuse_retains_source_patch_provenance_without_download_or_build_permission(self):
        binary=self.args.environment/'bin/regenie.exe';binary.parent.mkdir(parents=True);binary.write_bytes(b'controlled test fixture, never executed')
        marker={'identity':installer.identity('windows-cygwin-source'),'files':{'bin/regenie.exe':hashlib.sha256(binary.read_bytes()).hexdigest()},'provenance':{'source_patches':[{'name':'preserved-build-compatibility'}]}}
        (self.args.environment/installer.MARKER).write_text(json.dumps(marker))
        with patch.object(installer,'execute',return_value='v4.1.3\n'):
            record,code=self.install()
        self.assertEqual(0,code);self.assertTrue(record['runtime_reused']);self.assertEqual(marker['provenance'],record['installed_provenance']);self.assertFalse(record['download_authorized'])
    def test_corrupt_runtime_is_not_reused_or_replaced(self):
        binary=self.args.environment/'bin/regenie.exe';binary.parent.mkdir(parents=True);binary.write_bytes(b'changed')
        (self.args.environment/installer.MARKER).write_text(json.dumps({'identity':installer.identity('windows-cygwin-source'),'files':{'bin/regenie.exe':'wrong'}}))
        self.args.allow_download=True;self.args.allow_source_build=True;record,code=self.install();self.assertEqual(3,code);self.assertEqual('skipped-existing-environment',record['status']);self.assertEqual(b'changed',binary.read_bytes())
    def test_unknown_source_before_patch_is_preserved(self):
        source=self.root/'source';source.mkdir();file=source/'wscript';file.write_text('unexpected source')
        with self.assertRaisesRegex(ValueError,'Unexpected source'):
            installer.apply_bgen_patches(source)
        self.assertEqual('unexpected source',file.read_text())
    def test_failed_reuse_probe_is_dependency_unavailable_and_does_not_overwrite_runtime(self):
        binary=self.args.environment/'bin/regenie.exe';binary.parent.mkdir(parents=True);binary.write_bytes(b'unchanged')
        marker={'identity':installer.identity('windows-cygwin-source'),'files':{'bin/regenie.exe':hashlib.sha256(binary.read_bytes()).hexdigest()}}
        (self.args.environment/installer.MARKER).write_text(json.dumps(marker))
        with patch.object(installer,'execute',side_effect=RuntimeError('runtime dependency missing')):
            record,code=self.install()
        self.assertEqual(3,code);self.assertEqual('skipped-runtime-unavailable',record['status']);self.assertEqual(b'unchanged',binary.read_bytes());self.assertIn('runtime dependency missing',record['error'])

if __name__=='__main__':unittest.main()
