"""Permission and preservation controls; these synthetic tests are not runtime validation."""
import contextlib,hashlib,importlib.util,io,json,os,tempfile,unittest
from pathlib import Path
from unittest.mock import patch

SPEC=importlib.util.spec_from_file_location('admixture_installer',Path(__file__).with_name('install_admixture.py'))
setup=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(setup)
HOST=dict(os='Linux',architecture='x86_64',kernel='test-only-kernel')

class DependencyPolicy(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
    def args(self,*extra):return ['--profile','linux-x86_64-1.4.0','--home',str(self.root/'home'),'--cache',str(self.root/'cache'),'--report',str(self.root/'report'),*extra]
    def run_setup(self,*extra):
        with contextlib.redirect_stdout(io.StringIO()),contextlib.redirect_stderr(io.StringIO()):return setup.main(self.args(*extra))
    def test_no_install_permission_skips_without_home_or_cache(self):
        with patch.object(setup,'host',return_value=HOST),patch.object(setup,'obtain',side_effect=AssertionError('must not fetch')):self.assertEqual(3,self.run_setup())
        self.assertFalse((self.root/'home').exists());self.assertFalse((self.root/'cache').exists());self.assertIn('Required action',(self.root/'report/WARNINGS.md').read_text())
    def test_download_permission_does_not_authorize_install(self):
        with patch.object(setup,'host',return_value=HOST),patch.object(setup,'obtain',side_effect=AssertionError('must not fetch')):self.assertEqual(3,self.run_setup('--allow-download'))
        self.assertFalse((self.root/'home').exists())
    def test_missing_offline_archive_never_contacts_network(self):
        cache=self.root/'cache';cache.mkdir()
        with patch.object(setup.urllib.request,'urlopen',side_effect=AssertionError('network forbidden')):
            with self.assertRaises(setup.Unavailable):setup.obtain('admixture_linux-1.4.0.tar.gz',cache,[],False)
        self.assertEqual([],list(cache.iterdir()))
    def test_corrupt_archive_is_preserved(self):
        cache=self.root/'cache';cache.mkdir();p=cache/'admixture_linux-1.4.0.tar.gz';p.write_bytes(b'bad')
        with self.assertRaises(ValueError):setup.obtain(p.name,cache,[],True)
        self.assertEqual(b'bad',p.read_bytes())
    def test_existing_report_is_never_modified(self):
        report=self.root/'report';report.mkdir();(report/'keep').write_text('original')
        with patch.object(setup,'host',return_value=HOST):self.assertEqual(2,self.run_setup('--allow-install'))
        self.assertEqual(['keep'],[p.name for p in report.iterdir()]);self.assertEqual('original',(report/'keep').read_text())
    def test_unmanaged_existing_home_skips_without_mutation(self):
        home=self.root/'home';home.mkdir();(home/'keep').write_text('original')
        with patch.object(setup,'host',return_value=HOST):self.assertEqual(3,self.run_setup('--allow-install'))
        self.assertEqual(['keep'],[p.name for p in home.iterdir()])
    def test_unsupported_host_skips_before_creating_runtime(self):
        with patch.object(setup,'host',side_effect=setup.Unavailable('unsupported test host')):self.assertEqual(3,self.run_setup('--allow-install'))
        self.assertFalse((self.root/'home').exists());self.assertFalse((self.root/'cache').exists())
    def test_nested_output_paths_fail_without_install(self):
        args=self.args('--allow-install');args[args.index('--report')+1]=str(self.root/'home/report')
        with contextlib.redirect_stdout(io.StringIO()),contextlib.redirect_stderr(io.StringIO()):self.assertEqual(2,setup.main(args))
        self.assertFalse((self.root/'home').exists())
    def test_missing_qemu_extractor_is_explicit_dependency_skip(self):
        package=self.root/'package.deb';package.write_bytes(b'test')
        with patch.object(setup.shutil,'which',return_value=None):
            with self.assertRaises(setup.Unavailable):setup.extract_selected(package,{},self.root/'home',self.root)
    def test_private_rejects_linked_paths_where_supported(self):
        target=self.root/'target';target.mkdir();link=self.root/'link'
        try:link.symlink_to(target,target_is_directory=True)
        except OSError:self.skipTest('OS does not permit creating this test symlink')
        with self.assertRaises(ValueError):setup.private(link/'child')
    def test_profile_versions_and_legacy_emulator_are_explicit(self):
        current=setup.LOCK['profiles']['linux-x86_64-1.4.0'];legacy=setup.LOCK['profiles']['linux-x86_64-1.3.0-qemu8.2.2'];self.assertFalse(current['emulated']);self.assertTrue(legacy['emulated']);self.assertEqual('1.3.0',legacy['version']);self.assertIn('bin/qemu-x86_64-static',legacy['members']);self.assertNotIn('bin/qemu-x86_64-static',current['members'])
    def test_offline_copy_is_hash_checked_and_exact(self):
        reference=self.root/'reference';reference.mkdir();cache=self.root/'cache';cache.mkdir();raw=b'unit-test archive only';(reference/'fixture').write_bytes(raw);pin=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest(),url='https://invalid.test/never')
        with patch.dict(setup.LOCK['archives'],{'fixture':pin}),patch.object(setup.urllib.request,'urlopen',side_effect=AssertionError('network forbidden')):result=setup.obtain('fixture',cache,[reference],False)
        self.assertEqual(raw,result.read_bytes())

if __name__=='__main__':unittest.main()
