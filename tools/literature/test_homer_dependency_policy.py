"""Permission, retention and dependency-failure controls; not scientific parity tests."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('install_homer_under_test',Path(__file__).with_name('install_homer.py'))
installer=importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)

class HomerDependencyPolicyTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='homer-policy-')
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.home=self.root/'home';self.cache=self.root/'cache';self.report=self.root/'report'
        self.host=dict(os='Linux',architecture='x86_64',kernel='policy-test-only')
    def args(self,*extra):
        return ['--profile','linux-x86_64-5.1','--home',str(self.home),'--cache',str(self.cache),'--report',str(self.report),*extra]
    def run_main(self,*extra):
        with contextlib.redirect_stdout(io.StringIO()),contextlib.redirect_stderr(io.StringIO()):
            return installer.main(self.args(*extra))
    def record(self):
        return json.loads((self.report/'installation.json').read_text(encoding='utf-8'))
    def test_missing_install_flag_skips_before_creating_home_or_reading_cache(self):
        with patch.object(installer,'host',return_value=self.host),patch.object(installer,'obtain') as obtain:
            self.assertEqual(3,self.run_main());obtain.assert_not_called()
        self.assertFalse(self.home.exists());self.assertFalse(self.cache.exists());self.assertIn('--allow-install absent',self.record()['error'])
    def test_install_flag_does_not_imply_network_permission(self):
        with patch.object(installer,'host',return_value=self.host),patch.object(installer.urllib.request,'urlopen') as network:
            self.assertEqual(3,self.run_main('--allow-install'));network.assert_not_called()
        self.assertFalse(self.home.exists());self.assertIn('--allow-download absent',self.record()['error'])
    def test_unsupported_host_skips_with_required_recovery(self):
        with patch.object(installer.platform,'system',return_value='Windows'):
            self.assertEqual(3,self.run_main('--allow-install'))
        self.assertFalse(self.home.exists());warning=(self.report/'WARNINGS.md').read_text(encoding='utf-8');self.assertIn('SETUP SKIPPED',warning);self.assertIn('Required action:',warning);self.assertIn('WSL',warning)
    def test_unsupported_architecture_skips(self):
        with patch.object(installer.platform,'system',return_value='Linux'),patch.object(installer.platform,'machine',return_value='aarch64'):
            self.assertEqual(3,self.run_main('--allow-install'))
        self.assertFalse(self.home.exists())
    def test_existing_unmanaged_home_is_preserved_and_not_repaired(self):
        self.home.mkdir();sentinel=self.home/'existing-data';sentinel.write_bytes(b'preserve')
        with patch.object(installer,'host',return_value=self.host),patch.object(installer,'obtain') as obtain:
            self.assertEqual(3,self.run_main('--allow-install'));obtain.assert_not_called()
        self.assertEqual(b'preserve',sentinel.read_bytes());self.assertEqual([sentinel],list(self.home.iterdir()))
    def test_existing_report_is_not_overwritten(self):
        self.report.mkdir();sentinel=self.report/'sentinel';sentinel.write_bytes(b'preserve')
        self.assertEqual(2,self.run_main('--allow-install'));self.assertEqual(b'preserve',sentinel.read_bytes());self.assertFalse((self.report/'installation.json').exists())
    def test_nested_paths_fail_without_creating_runtime(self):
        self.cache=self.home/'cache'
        self.assertEqual(2,self.run_main('--allow-install'));self.assertFalse(self.home.exists())
    def test_corrupt_archive_is_retained_without_download_or_replacement(self):
        self.cache.mkdir();archive=self.cache/'homer.v5.1.zip';archive.write_bytes(b'corrupt')
        with patch.object(installer,'host',return_value=self.host),patch.object(installer.urllib.request,'urlopen') as network:
            self.assertEqual(2,self.run_main('--allow-install','--allow-download'));network.assert_not_called()
        self.assertEqual(b'corrupt',archive.read_bytes());self.assertFalse(self.home.exists());self.assertIn('Corrupt/linked pinned archive',self.record()['error'])
    def test_cached_offline_fixture_bytes_need_no_download_permission(self):
        # This is a tiny transport-policy fixture, not an admitted HOMER binary.
        source=self.root/'offline';source.mkdir();content=b'offline-policy-fixture';(source/'fixture.zip').write_bytes(content);self.cache.mkdir()
        pin=dict(bytes=len(content),sha256=__import__('hashlib').sha256(content).hexdigest(),url='http://invalid.example/never-called')
        with patch.dict(installer.LOCK['archives'],{'fixture.zip':pin}),patch.object(installer.urllib.request,'urlopen') as network:
            result=installer.obtain('fixture.zip',self.cache,[source],False);network.assert_not_called()
        self.assertEqual(content,result.read_bytes());self.assertEqual(content,(source/'fixture.zip').read_bytes())
    def test_dependency_error_is_classified_as_skip_with_diagnostics(self):
        with patch.object(installer,'host',return_value=self.host),patch.object(installer,'obtain',side_effect=installer.Unavailable('named missing dependency')):
            self.assertEqual(3,self.run_main('--allow-install'))
        self.assertEqual('skipped-unavailable-dependency',self.record()['status']);self.assertIn('named missing dependency',(self.report/'WARNINGS.md').read_text(encoding='utf-8'));self.assertFalse(self.home.exists())
    def test_failed_health_preserves_partial_home_and_never_writes_admission_marker(self):
        with patch.object(installer,'host',return_value=self.host),patch.object(installer,'obtain',return_value=self.root/'archive'),patch.object(installer,'extract'),patch.object(installer,'closure',return_value={}),patch.object(installer,'health',side_effect=installer.Unavailable('original scientific health failed')):
            self.assertEqual(3,self.run_main('--allow-install'))
        self.assertTrue(self.home.is_dir());self.assertFalse((self.home/installer.MARKER).exists());self.assertIn('scientific health failed',self.record()['error'])
    def test_existing_admission_reuse_does_not_install_or_download(self):
        self.home.mkdir()
        (self.home/installer.MARKER).write_text('policy-only-marker')
        with patch.object(installer,'host',return_value=self.host),patch.object(installer,'verify_home',return_value=dict(health=dict(status='policy-only-health'))),patch.object(installer,'obtain') as obtain:
            self.assertEqual(0,self.run_main());obtain.assert_not_called()
        self.assertEqual('reused-verified-admitted-runtime',self.record()['status'])

if __name__=='__main__':
    unittest.main()
