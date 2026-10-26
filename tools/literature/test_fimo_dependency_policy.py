"""Explicit permission, immutable-profile and source-cache policy for original FIMO."""
import contextlib,io,json,shutil,tempfile,unittest
from pathlib import Path
from unittest import mock
import install_fimo as fimo

class FimoDependencyPolicyTest(unittest.TestCase):
    def setUp(self):
        # These are admission/permission controls, never replacement scientific
        # engines. Exercise the supported-host branch independently of CI host;
        # the unsupported-host test overrides this boundary explicitly.
        for name,value in [('system','Windows'),('machine','AMD64')]:
            patch=mock.patch.object(fimo.platform,name,return_value=value);patch.start();self.addCleanup(patch.stop)
        self.temporary=tempfile.TemporaryDirectory(prefix='jlinalg-fimo-policy-');self.root=Path(self.temporary.name)
        self.lock=json.loads(fimo.LOCK.read_text());self.reference=fimo.ROOT/'build/omics-fimo-5.5.9-gnurx-reference';self.installed=fimo.ROOT/'build/omics-fimo-5.5.9-authenticated'
    def tearDown(self):self.temporary.cleanup()
    def run_helper(self,name,*extra):
        args=['--home',str(self.root/'new-home'),'--cache',str(self.root/'cache'),'--report-dir',str(self.root/name),*extra]
        with contextlib.redirect_stdout(io.StringIO()),contextlib.redirect_stderr(io.StringIO()):return fimo.main(args)
    def test_installation_requires_explicit_allow_and_keeps_actionable_skip(self):
        with mock.patch.object(fimo,'fetch') as fetch:
            self.assertEqual(3,self.run_helper('denied'));fetch.assert_not_called()
        self.assertFalse((self.root/'new-home').exists());warning=(self.root/'denied/WARNINGS.md').read_text();self.assertIn('not allowed',warning);self.assertIn('Required action',warning);self.assertIn('SKIP',warning)
    def test_unsupported_host_skips_without_provisioning_or_execution(self):
        with mock.patch.object(fimo.platform,'system',return_value='Linux'),mock.patch.object(fimo,'smoke') as smoke:
            self.assertEqual(3,self.run_helper('unsupported','--allow-install','--intended-use','educational-research-nonprofit'));smoke.assert_not_called()
        self.assertFalse((self.root/'new-home').exists());self.assertIn('only validated on Windows',(self.root/'unsupported/WARNINGS.md').read_text())
    def test_intended_use_is_required_before_reference_or_download_access(self):
        with mock.patch.object(fimo,'verify_files') as verify,mock.patch.object(fimo,'fetch') as fetch:
            self.assertEqual(3,self.run_helper('undeclared','--allow-install'));verify.assert_not_called();fetch.assert_not_called()
        self.assertFalse((self.root/'new-home').exists());self.assertIn('intended-use declaration',(self.root/'undeclared/WARNINGS.md').read_text())
    def test_unrecognized_reference_is_never_executed_or_replaced(self):
        reference=self.root/'reference';reference.mkdir();(reference/fimo.REFERENCE_MARKER).write_text('{}')
        with mock.patch.object(fimo,'smoke') as smoke:
            self.assertEqual(3,self.run_helper('bad-reference','--allow-install','--intended-use','educational-research-nonprofit','--reference-home',str(reference)));smoke.assert_not_called()
        self.assertEqual('{}',(reference/fimo.REFERENCE_MARKER).read_text());self.assertFalse((self.root/'new-home').exists())
    def test_absent_archive_without_permission_has_no_network_or_file_mutation(self):
        artifact=self.lock['source_archives'][0]
        with mock.patch.object(fimo.urllib.request,'urlopen') as download:
            with self.assertRaisesRegex(RuntimeError,'download not permitted'):fimo.fetch(artifact,self.root/'absent-cache',False)
            download.assert_not_called()
        self.assertFalse((self.root/'absent-cache').exists())
    def test_changed_cached_archive_is_retained_and_never_replaced(self):
        artifact=self.lock['source_archives'][0];cache=self.root/'cache';cache.mkdir();target=cache/artifact['archive'];target.write_bytes(b'changed source archive')
        with mock.patch.object(fimo.urllib.request,'urlopen') as download:
            with self.assertRaisesRegex(ValueError,'integrity failed'):fimo.fetch(artifact,cache,True)
            download.assert_not_called()
        self.assertEqual(b'changed source archive',target.read_bytes())
    def test_member_paths_cannot_escape_the_profile(self):
        for name in ['../outside','bin/../../outside','/absolute','C:/absolute','bin\\fimo.exe','bin//fimo.exe','bin/./fimo.exe']:
            with self.subTest(name=name),self.assertRaises(ValueError):fimo.contained(self.root,name)
    def copy_installed(self):
        if not (self.installed/fimo.MARKER).is_file():self.skipTest('Actual installed FIMO profile is unavailable')
        fimo.validate(self.installed,self.lock)
        target=self.root/'profile';shutil.copytree(self.installed,target);return target
    def test_actual_fixed_profile_verifies_and_extra_members_are_rejected(self):
        home=self.copy_installed();fimo.validate(home,self.lock);(home/'bin/unlisted.dll').write_bytes(b'foreign')
        with self.assertRaisesRegex(ValueError,'unlisted'):fimo.validate(home,self.lock)
    def test_changed_actual_binary_is_rejected_without_launch(self):
        home=self.copy_installed();binary=home/'bin/fimo.exe';binary.write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError,'integrity failed'):fimo.validate(home,self.lock)
    def test_oversized_marker_is_rejected_before_parsing(self):
        home=self.copy_installed();(home/fimo.MARKER).write_bytes(b' '*1_048_577)
        with self.assertRaisesRegex(ValueError,'oversized runtime marker'):fimo.validate(home,self.lock)
    def test_profile_version_and_eligibility_are_bound_to_the_lock(self):
        for field in ['version','eligibility','runtime_lock_sha256']:
            home=self.copy_installed();path=home/fimo.MARKER;data=json.loads(path.read_text());data[field]='changed';path.write_text(json.dumps(data))
            with self.subTest(field=field),self.assertRaises(ValueError):fimo.validate(home,self.lock)
            self.assertEqual(self.root.resolve(),home.resolve().parent);self.assertEqual('profile',home.name)
            shutil.rmtree(home)
    def test_existing_changed_home_is_preserved_when_installation_is_allowed(self):
        home=self.root/'new-home';home.mkdir();(home/'retained.txt').write_text('keep this')
        self.assertEqual(3,self.run_helper('preserved','--allow-install','--intended-use','educational-research-nonprofit'));self.assertEqual('keep this',(home/'retained.txt').read_text())
    def test_existing_actual_profile_can_be_verified_without_install_or_download(self):
        home=self.copy_installed()
        with mock.patch.object(fimo,'fetch') as fetch,mock.patch.object(fimo,'smoke') as smoke:
            self.assertEqual(0,self.run_helper('reuse','--home',str(home)));fetch.assert_not_called();smoke.assert_not_called()
        self.assertEqual('existing-profile-verified',json.loads((self.root/'reuse/status.json').read_text())['status'])

if __name__=='__main__':unittest.main()
