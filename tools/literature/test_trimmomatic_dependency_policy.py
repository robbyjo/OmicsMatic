"""Runtime policy without network, installation, or application execution."""
import argparse,contextlib,io,json,tempfile,unittest,zipfile
from pathlib import Path
from unittest.mock import patch
import install_trimmomatic as installer

class TrimmomaticPolicyTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.args=argparse.Namespace(version='0.41',environment=self.root/'env',report=self.root/'report',cache=self.root/'cache',java=self.root/'java.exe',private_java=False,java_home=self.root/'jre',allow_download=False)
    def tearDown(self):self.temp.cleanup()
    def call(self,reused=False,probe=None):
        self.args.java.write_bytes(b'synthetic non-executable')
        with contextlib.redirect_stdout(io.StringIO()),patch.object(installer,'download',side_effect=AssertionError('Network forbidden in policy test')),patch.object(installer,'available',return_value=reused),patch.object(installer,'probe',probe or (lambda *a:25)):
            code=installer.install(self.args)
        return code,json.loads((self.args.report/'dependency-status.json').read_text())
    def test_absent_and_forbidden_runtime_skips_with_exact_action(self):
        code,d=self.call();self.assertEqual(3,code);self.assertEqual('skipped-install-not-permitted',d['status']);self.assertFalse(self.args.environment.exists());self.assertIn('ANALYSIS SKIPPED',(self.args.report/'WARNINGS.md').read_text());self.assertIn('--version 0.41 --allow-download',d['required_action']);self.assertIn('Native quality/length trimming',d['required_action'])
    def test_verified_existing_runtime_reuses_without_permission(self):
        code,d=self.call(True);self.assertEqual(0,code);self.assertTrue(d['runtime_reused']);self.assertFalse(d['download_authorized']);self.assertIn('not-yet-analysis-validated',d['status'])
    def test_unmanaged_home_preserved(self):
        self.args.environment.mkdir();keep=self.args.environment/'keep';keep.write_text('retain');self.args.allow_download=True;code,d=self.call();self.assertEqual(3,code);self.assertEqual('retain',keep.read_text());self.assertIn('preserved',d['error'])
    def test_actual_probe_failure_not_marked_available(self):
        def fail(*a):raise RuntimeError('Actual Java too old')
        code,d=self.call(True,fail);self.assertEqual(3,code);self.assertIn('Actual Java too old',d['error'])
    def test_java_member_escape_fails_before_reading_or_executing(self):
        home=self.args.java_home;home.mkdir();outside=self.root/'outside';outside.write_text('retain')
        profile={'sha256':'a'*64};(home/installer.JAVA_MARKER).write_text(json.dumps(dict(archive_sha256=profile['sha256'],files={'../outside':installer.digest(outside)})))
        with patch.object(installer,'java_version',side_effect=AssertionError('Must not execute')),self.assertRaisesRegex(ValueError,'Unsafe'):installer.private_java(home,profile,self.args.cache,False,self.root)
        self.assertEqual('retain',outside.read_text())
    def test_changed_cache_preserved_without_extraction(self):
        self.args.cache.mkdir();profile=json.loads(installer.LOCK.read_text())['profiles']['0.39'];p=self.args.cache/profile['archive'];p.write_bytes(b'not the original archive')
        with self.assertRaisesRegex(ValueError,'integrity failed'):installer.fetch(profile,self.args.cache,True)
        self.assertEqual(b'not the original archive',p.read_bytes())
    def test_unsafe_and_unexpected_archive_members_rejected(self):
        for i,names in enumerate([['../outside'],['C:/outside'],['folder\\outside'],['safe','extra']]):
            archive=self.root/f'{i}.zip';dest=self.root/f'dest{i}';dest.mkdir()
            with zipfile.ZipFile(archive,'w') as z:
                for name in names:z.writestr(name,'synthetic')
            with self.assertRaises(ValueError):installer.extract(archive,dest,'',{'safe':installer.digest(self.args.java)} if self.args.java.exists() else {})
        self.assertFalse((self.root/'outside').exists())
    def test_previous_attempt_and_diagnostics_retained(self):
        self.call();code,d=self.call(True);self.assertEqual(0,code);self.assertEqual(1,len(d['history']));self.assertEqual('skipped-install-not-permitted',d['history'][0]['status']);self.assertEqual(2,len(list((self.args.report/'attempts').iterdir())))

if __name__=='__main__':unittest.main()
