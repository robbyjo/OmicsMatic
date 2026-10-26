"""Installer policy tests; fake artifacts are never executed as scientific programs."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock
import install_beagle as installer


class BeagleInstallPolicyTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='jlinalg-beagle-policy-')
        self.root = Path(self.temporary.name).resolve()
        self.contents = {'one.jar': b'fake-one-not-executable', 'two.jar': b'fake-two-not-executable', 'LICENSE': b'fake-license'}
        hashes = {name: hashlib.sha256(data).hexdigest() for name,data in self.contents.items()}
        self.lock = dict(profile='unit-test-only', files=hashes, source_scope='Unit fixtures only',
            profiles={'one':dict(jar='one.jar',version_line='beagle-one'), 'two':dict(jar='two.jar',version_line='beagle-two')},
            artifacts={name:dict(url='https://example.invalid/'+name,sha256=hashes[name],cache_name=name,maximum_bytes=1000) for name in hashes})
        self.lock_path = self.root/'lock.json'
        self.lock_path.write_text(json.dumps(self.lock))
        self.patches = [mock.patch.object(installer,'LOCK',self.lock), mock.patch.object(installer,'LOCK_PATH',self.lock_path)]
        for patch in self.patches:
            patch.start()
        self.args = argparse.Namespace(home=self.root/'home',cache=self.root/'cache',report=self.root/'report',
            java=self.root/'java.exe',allow_install=False,allow_download=False)
        self.args.java.write_bytes(b'fake-java-not-executable')

    def tearDown(self):
        for patch in reversed(self.patches):
            patch.stop()
        self.temporary.cleanup()

    def cache(self):
        self.args.cache.mkdir(exist_ok=True)
        for name,data in self.contents.items():
            (self.args.cache/name).write_bytes(data)

    def home(self):
        self.args.home.mkdir(exist_ok=True)
        for name,data in self.contents.items():
            (self.args.home/name).write_bytes(data)
        (self.args.home/installer.MARKER).write_text(json.dumps(installer.identity()))

    @staticmethod
    def fake_probe(*_):
        return dict(scope='Mock admission only, no scientific program execution')

    def test_missing_install_permission_skips_and_explains_exact_recovery(self):
        with mock.patch.object(installer,'download') as download,mock.patch.object(installer,'probe') as probe:
            record,code=installer.install(self.args)
        self.assertEqual(code,3);self.assertEqual(record['status'],'skipped-install-not-permitted')
        self.assertFalse(self.args.home.exists());download.assert_not_called();probe.assert_not_called()
        warning=(self.args.report/'WARNINGS.md').read_text(encoding='utf-8')
        self.assertIn('SKIPPED',warning);self.assertIn('--allow-install',warning);self.assertIn('skip the dependent analysis',warning)

    def test_download_permission_does_not_grant_install_permission(self):
        self.args.allow_download=True
        with mock.patch.object(installer,'download') as download:
            record,code=installer.install(self.args)
        self.assertEqual(code,3);download.assert_not_called();self.assertFalse(self.args.home.exists())

    def test_missing_cache_without_download_permission_skips_before_probe(self):
        self.args.allow_install=True
        with mock.patch.object(installer,'download') as download,mock.patch.object(installer,'probe') as probe:
            record,code=installer.install(self.args)
        self.assertEqual(code,3);self.assertIn('--allow-download',record['error']);download.assert_not_called();probe.assert_not_called();self.assertFalse(self.args.home.exists())

    def test_unmanaged_existing_home_is_preserved_even_with_both_flags(self):
        self.args.home.mkdir();sentinel=self.args.home/'user-data';sentinel.write_text('retain me')
        self.args.allow_install=self.args.allow_download=True
        with mock.patch.object(installer,'probe') as probe:
            record,code=installer.install(self.args)
        self.assertEqual(code,3);self.assertEqual(sentinel.read_text(),'retain me');probe.assert_not_called();self.assertIn('preserved',record['error'])

    def test_cached_install_is_atomic_and_requires_no_network(self):
        self.cache();self.args.allow_install=True
        with mock.patch.object(installer,'download') as download,mock.patch.object(installer,'probe',side_effect=self.fake_probe):
            record,code=installer.install(self.args)
        self.assertEqual(code,0);self.assertTrue(record['status'].startswith('installed'));download.assert_not_called();self.assertTrue(installer.available(self.args.home))
        self.assertEqual(set(p.name for p in self.args.home.iterdir()),set(self.contents)|{installer.MARKER})

    def test_valid_home_reuse_needs_neither_permission_flag(self):
        self.home()
        with mock.patch.object(installer,'download') as download,mock.patch.object(installer,'probe',side_effect=self.fake_probe):
            record,code=installer.install(self.args)
        self.assertEqual(code,0);self.assertTrue(record['runtime_reused']);download.assert_not_called()

    def test_changed_home_is_never_resealed_or_overwritten(self):
        self.home();marker=(self.args.home/installer.MARKER).read_bytes();(self.args.home/'one.jar').write_bytes(b'changed')
        self.args.allow_install=self.args.allow_download=True
        record,code=installer.install(self.args)
        self.assertEqual(code,3);self.assertEqual((self.args.home/'one.jar').read_bytes(),b'changed');self.assertEqual((self.args.home/installer.MARKER).read_bytes(),marker)

    def test_corrupt_cache_is_preserved_without_probe_or_download(self):
        self.cache();(self.args.cache/'one.jar').write_bytes(b'changed');self.args.allow_install=self.args.allow_download=True
        with mock.patch.object(installer,'probe') as probe,mock.patch.object(installer,'download') as download:
            record,code=installer.install(self.args)
        self.assertEqual(code,3);self.assertIn('cached artifact preserved',record['error']);probe.assert_not_called();download.assert_not_called();self.assertEqual((self.args.cache/'one.jar').read_bytes(),b'changed')

    def test_failed_version_probe_never_activates_new_home(self):
        self.cache();self.args.allow_install=True
        with mock.patch.object(installer,'probe',side_effect=RuntimeError('probe differs')):
            record,code=installer.install(self.args)
        self.assertEqual(code,3);self.assertFalse(self.args.home.exists());self.assertIn('probe differs',record['error'])

    def test_extra_file_directory_and_invalid_marker_fail_admission(self):
        self.home();extra=self.args.home/'extra';extra.write_bytes(b'extra');self.assertFalse(installer.available(self.args.home));extra.unlink()
        extra.mkdir();self.assertFalse(installer.available(self.args.home));extra.rmdir();self.assertTrue(installer.available(self.args.home))
        (self.args.home/installer.MARKER).write_text('{}');self.assertFalse(installer.available(self.args.home))

    def test_overlapping_paths_reject_before_creating_home_or_report(self):
        self.args.report=self.args.home/'report'
        with self.assertRaisesRegex(ValueError,'non-overlapping'):
            installer.install(self.args)
        self.assertFalse(self.args.home.exists());self.assertFalse(self.args.report.exists())

    def test_injected_java_options_are_removed_only_from_child_environment(self):
        with mock.patch.dict(installer.os.environ,{'JAVA_TOOL_OPTIONS':'injected','_JAVA_OPTIONS':'injected','JDK_JAVA_OPTIONS':'injected','CLASSPATH':'injected'}):
            child=installer.child_environment()
            for key in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH']:
                self.assertNotIn(key,child);self.assertEqual(installer.os.environ[key],'injected')

    def test_original_probe_checks_java_version_and_every_profile_without_executing_fake_jars(self):
        self.home();attempt=self.root/'probe';attempt.mkdir()
        def run(command,**_):
            stdout=b'' if command[-1]=='-version' else (self.lock['profiles'][Path(command[-1]).stem]['version_line']+'\n').encode()
            return subprocess.CompletedProcess(command,0,stdout,b'openjdk version "21.0.4"\n' if command[-1]=='-version' else b'')
        with mock.patch.object(installer.subprocess,'run',side_effect=run) as mocked:
            result=installer.probe(self.args.home,self.args.java,attempt)
        self.assertEqual(mocked.call_count,3);self.assertEqual(set(result['profiles']),{'one','two'})

    def test_old_java_probe_failure_does_not_launch_any_profile(self):
        self.home();attempt=self.root/'probe';attempt.mkdir()
        with mock.patch.object(installer.subprocess,'run',return_value=subprocess.CompletedProcess([],0,b'',b'java version "1.7.0"')) as run:
            with self.assertRaisesRegex(RuntimeError,'unsupported version'):
                installer.probe(self.args.home,self.args.java,attempt)
        self.assertEqual(run.call_count,1)


if __name__=='__main__':
    unittest.main()
