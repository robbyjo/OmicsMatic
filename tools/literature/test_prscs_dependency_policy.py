"""PRS-CS setup policy tests. Fake artifacts are never executed as scientific programs."""
import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import install_prscs as setup


class PrsCsDependencyPolicyTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='jlinalg-prscs-policy-')
        self.root = Path(self.temporary.name).resolve()
        self.lock_path = self.root/'lock.json'
        self.source = b'fake source; never executed'
        self.pth = b'python310.zip\n.\nLib/site-packages\nimport site\n'
        self.item = dict(cache_name='fake.py',url='https://example.invalid/fake.py',sha256=hashlib.sha256(self.source).hexdigest(),maximum_bytes=1000)
        self.lock = dict(profile='unit-policy-only',profiles={'old':dict(python='legacy',files={'PRScs.py':self.item})},packages={'legacy':[]},legacy_python=dict(derived_pth_sha256=hashlib.sha256(self.pth).hexdigest()))
        self.lock_path.write_text(json.dumps(self.lock))
        self.patches = [mock.patch.object(setup,'LOCK',self.lock),mock.patch.object(setup,'LOCK_PATH',self.lock_path)]
        for patch in self.patches:
            patch.start()
        self.args = argparse.Namespace(home=self.root/'home',cache=self.root/'cache',report=self.root/'report',python=self.root/'python.exe',allow_install=False,allow_download=False)
        self.report = self.root/'attempt';self.report.mkdir()

    def tearDown(self):
        for patch in reversed(self.patches):
            patch.stop()
        self.temporary.cleanup()

    def home(self):
        home = self.args.home
        (home/'sources/old').mkdir(parents=True)
        (home/'sources/old/PRScs.py').write_bytes(self.source)
        (home/'legacy').mkdir()
        (home/'legacy/python.exe').write_bytes(b'not a real interpreter; never executed')
        (home/'legacy/python310._pth').write_bytes(self.pth)
        metadata = dict(profile=self.lock['profile'],runtime_lock_sha256=setup.sha(self.lock_path),profiles=['old'],files=setup.tree(home))
        (home/setup.MARKER).write_text(json.dumps(metadata))
        return home

    def test_download_permission_does_not_grant_install_permission(self):
        self.args.allow_download = True
        with mock.patch.object(setup,'artifact') as artifact:
            with self.assertRaisesRegex(RuntimeError,'Installation permission absent'):
                setup.install(self.args,setup.paths(self.args),['old'],self.report)
            artifact.assert_not_called()
        self.assertFalse(self.args.home.exists())

    def test_missing_cache_without_download_permission_preserves_absent_home(self):
        self.args.allow_install = True
        with self.assertRaisesRegex(RuntimeError,'download permission absent'):
            setup.install(self.args,setup.paths(self.args),['old'],self.report)
        self.assertFalse(self.args.home.exists())

    def test_cached_artifact_reuse_requires_no_download(self):
        self.args.cache.mkdir();path=self.args.cache/'fake.py';path.write_bytes(self.source)
        with mock.patch('urllib.request.urlopen') as download:
            self.assertEqual(path,setup.artifact(self.args.cache,self.item,False));download.assert_not_called()

    def test_changed_cache_is_preserved_even_with_download_permission(self):
        self.args.cache.mkdir();path=self.args.cache/'fake.py';path.write_bytes(b'changed')
        with mock.patch('urllib.request.urlopen') as download:
            with self.assertRaisesRegex(RuntimeError,'preserved'):
                setup.artifact(self.args.cache,self.item,True)
            download.assert_not_called()
        self.assertEqual(b'changed',path.read_bytes())

    def test_cache_report_home_overlap_rejects_before_mutation(self):
        self.args.cache = self.args.home/'cache'
        with self.assertRaisesRegex(ValueError,'nonoverlapping'):
            setup.paths(self.args)
        self.assertFalse(self.args.home.exists())

    def test_unmanaged_home_is_never_overwritten_or_resealed(self):
        self.args.allow_install = self.args.allow_download = True
        self.args.home.mkdir();sentinel=self.args.home/'sentinel';sentinel.write_text('retain')
        with self.assertRaisesRegex(RuntimeError,'preserved'):
            setup.install(self.args,setup.paths(self.args),['old'],self.report)
        self.assertEqual('retain',sentinel.read_text());self.assertFalse((self.args.home/setup.MARKER).exists())

    def test_verified_home_reuse_needs_neither_permission_flag(self):
        home=self.home();self.assertTrue(setup.available(home,['old']))
        with mock.patch.object(setup,'probe',return_value=[{'policy_mock_only':True}]) as probe, mock.patch.object(setup,'artifact') as artifact:
            self.assertTrue(setup.install(self.args,setup.paths(self.args),['old'],self.report)['reused'])
            probe.assert_called_once();artifact.assert_not_called()

    def test_changed_source_extra_file_and_changed_pth_fail_admission(self):
        home=self.home();source=home/'sources/old/PRScs.py';source.write_bytes(b'changed');self.assertFalse(setup.available(home))
        source.write_bytes(self.source);self.assertTrue(setup.available(home))
        extra=home/'notes.txt';extra.write_text('unmanaged');self.assertFalse(setup.available(home));extra.unlink()
        pth=home/'legacy/python310._pth';pth.write_bytes(b'changed');metadata=json.loads((home/setup.MARKER).read_text());metadata['files']=setup.tree(home);(home/setup.MARKER).write_text(json.dumps(metadata))
        self.assertFalse(setup.available(home),'The bundled path configuration pin prevents local resealing')

    def test_missing_executable_and_changed_lock_fail_admission(self):
        home=self.home();executable=home/'legacy/python.exe';executable.unlink();self.assertFalse(setup.available(home))
        executable.write_bytes(b'not a real interpreter; never executed');self.assertTrue(setup.available(home))
        self.lock_path.write_text(self.lock_path.read_text()+' ');self.assertFalse(setup.available(home))

    def test_injected_module_caches_are_bypassed_by_explicit_interpreter_flags(self):
        home=self.home();cache=home/'sources/old/__pycache__';cache.mkdir();(cache/'ignored.pyc').write_bytes(b'not executed')
        self.assertTrue(setup.available(home))
        flags=setup.python_flags(self.args.python,self.report,'cache-control')
        self.assertIn('-I',flags);self.assertIn('-B',flags)
        prefix=next(v for v in flags if v.startswith('pycache_prefix='));directory=Path(prefix.split('=',1)[1])
        self.assertEqual([],list(directory.iterdir()))

    def test_reuse_probe_failure_preserves_original_home(self):
        home=self.home();before=setup.tree(home)
        with mock.patch.object(setup,'probe',side_effect=RuntimeError('Actual import/version probe failed')):
            with self.assertRaisesRegex(RuntimeError,'probe failed'):
                setup.install(self.args,setup.paths(self.args),['old'],self.report)
        self.assertEqual(before,setup.tree(home))

    def test_runtime_change_during_reuse_is_detected(self):
        home=self.home()
        def changed(*args):
            (home/'extra.txt').write_text('changed during probe')
            return []
        with mock.patch.object(setup,'probe',side_effect=changed):
            with self.assertRaisesRegex(RuntimeError,'changed during reuse'):
                setup.install(self.args,setup.paths(self.args),['old'],self.report)


if __name__ == '__main__':
    unittest.main()
