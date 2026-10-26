"""Installer/admission policy; fake archives/probes never represent scientific runs."""
from pathlib import Path
import argparse
import contextlib
import copy
import hashlib
import io
import json
import tempfile
import unittest
import warnings
from unittest.mock import patch
import zipfile
import install_plink as installer


class PlinkDependencyPolicyTest(unittest.TestCase):
    def args(self, root, install=False, download=False):
        return argparse.Namespace(home=root/'home', archives=root/'archives', report=root/'report', allow_install=install, allow_download=download)

    def call(self, args):
        with contextlib.redirect_stdout(io.StringIO()):
            return installer.install(args)

    def record(self, args):
        return json.loads((args.report/'dependency-status.json').read_text())

    def fake(self, args):
        lock=copy.deepcopy(installer.LOCK)
        bodies={'plink1/plink.exe':b'fake-admission-bytes-not-a-program-1',
                'plink2/plink2.exe':b'fake-admission-bytes-not-a-program-2', 'LICENSE':b'fake-license-policy-only'}
        lock['files']={n:hashlib.sha256(b).hexdigest() for n,b in bodies.items()}
        for name,body in bodies.items():
            path=args.home/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(body)
        with patch.object(installer,'LOCK',lock):
            (args.home/installer.MARKER).write_text(json.dumps(installer.identity()))
        return lock

    def archives(self, args):
        lock=copy.deepcopy(installer.LOCK);args.archives.mkdir()
        for engine,values in lock['archives'].items():
            path=args.archives/values['filename']
            with zipfile.ZipFile(path,'w') as stream:
                for name in values['members']:stream.writestr(name,('policy-only '+name).encode())
            with zipfile.ZipFile(path) as stream:values['members']={n:hashlib.sha256(stream.read(n)).hexdigest() for n in stream.namelist()}
            values['sha256']=installer.digest(path)
        lock['files']={relative:lock['archives'][engine]['members'][member]
                       for engine,values in lock['archives'].items() for member,relative in values['extract'].items()}
        return lock

    def test_new_install_requires_permission_even_with_cached_archives(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer,'download') as network,patch.object(installer,'probe') as probe:
            args=self.args(Path(folder));self.archives(args)
            self.assertEqual(3,self.call(args));self.assertFalse(args.home.exists());network.assert_not_called();probe.assert_not_called()
            self.assertIn('Required action:',(args.report/'WARNINGS.md').read_text());self.assertEqual('skipped-install-not-permitted',self.record(args)['status'])

    def test_install_permission_does_not_authorize_download(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer,'download') as network,patch.object(installer,'probe') as probe:
            args=self.args(Path(folder),install=True);self.assertEqual(3,self.call(args));network.assert_not_called();probe.assert_not_called();self.assertFalse(args.home.exists())
            self.assertIn('--allow-download',self.record(args)['error'])

    def test_download_permission_does_not_authorize_install(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer,'download') as network:
            args=self.args(Path(folder),download=True);self.assertEqual(3,self.call(args));network.assert_not_called();self.assertFalse(args.home.exists())

    def test_verified_existing_home_reuses_without_install_or_network_permission(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer,'download') as network,patch.object(installer,'probe',return_value={}) as probe:
            args=self.args(Path(folder));lock=self.fake(args)
            with patch.object(installer,'LOCK',lock):self.assertEqual(0,self.call(args))
            network.assert_not_called();probe.assert_called_once();self.assertTrue(self.record(args)['runtime_reused'])

    def test_changed_executable_is_preserved_and_never_probed(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer,'probe') as probe:
            args=self.args(Path(folder),True,True);lock=self.fake(args);path=args.home/'plink2/plink2.exe';path.write_bytes(b'changed')
            with patch.object(installer,'LOCK',lock):self.assertEqual(3,self.call(args))
            probe.assert_not_called();self.assertEqual(b'changed',path.read_bytes());self.assertIn('new --home',self.record(args)['error'])

    def test_marker_identity_change_prevents_execution(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer,'probe') as probe:
            args=self.args(Path(folder));lock=self.fake(args);marker=args.home/installer.MARKER;data=json.loads(marker.read_text());data['runtime_lock_sha256']='0'*64;marker.write_text(json.dumps(data))
            with patch.object(installer,'LOCK',lock):self.assertEqual(3,self.call(args))
            probe.assert_not_called()

    def test_extra_file_prevents_reuse(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer,'probe') as probe:
            args=self.args(Path(folder));lock=self.fake(args);(args.home/'extra.dll').write_bytes(b'extra')
            with patch.object(installer,'LOCK',lock):self.assertEqual(3,self.call(args))
            probe.assert_not_called()

    def test_extra_empty_directory_prevents_reuse(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer,'probe') as probe:
            args=self.args(Path(folder));lock=self.fake(args);(args.home/'extra').mkdir()
            with patch.object(installer,'LOCK',lock):self.assertEqual(3,self.call(args))
            probe.assert_not_called()

    def test_wrong_host_skips_before_download_or_execution(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer.sys,'platform','linux'),patch.object(installer,'download') as network,patch.object(installer,'probe') as probe:
            args=self.args(Path(folder),True,True);self.assertEqual(3,self.call(args));self.assertEqual('skipped-platform-unavailable',self.record(args)['status']);network.assert_not_called();probe.assert_not_called()

    def test_corrupt_cached_archive_is_not_replaced_or_executed(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer,'download') as network,patch.object(installer,'probe') as probe:
            args=self.args(Path(folder),True,True);args.archives.mkdir();path=args.archives/installer.LOCK['archives']['plink1']['filename'];path.write_bytes(b'corrupt')
            self.assertEqual(3,self.call(args));self.assertEqual(b'corrupt',path.read_bytes());network.assert_not_called();probe.assert_not_called();self.assertFalse(args.home.exists())

    def test_downloaded_bad_hash_is_not_published_or_executed(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer,'download',side_effect=lambda url,path:path.write_bytes(b'bad')),patch.object(installer,'probe') as probe:
            args=self.args(Path(folder),True,True);self.assertEqual(3,self.call(args));self.assertFalse(args.home.exists());self.assertEqual([],list(args.archives.glob('*.zip')));probe.assert_not_called()

    def test_cached_install_can_run_without_network_authorization(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer,'download') as network,patch.object(installer,'probe',return_value={}):
            args=self.args(Path(folder),True,False);lock=self.archives(args)
            with patch.object(installer,'LOCK',lock):
                self.assertEqual(0,self.call(args));self.assertTrue(installer.available(args.home))
            network.assert_not_called();self.assertEqual(set(lock['files']),{p.relative_to(args.home).as_posix() for p in args.home.rglob('*') if p.is_file() and p.name!=installer.MARKER})

    def test_traversal_symlink_and_duplicate_members_fail_before_extraction(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);dest=root/'destination';dest.mkdir()
            for index,(name,linked,duplicate) in enumerate([('../escape.exe',False,False),('plink.exe',True,False),('plink.exe',False,True)]):
                path=root/(str(index)+'.zip');info=zipfile.ZipInfo(name)
                if linked:info.create_system=3;info.external_attr=0o120777<<16
                with zipfile.ZipFile(path,'w') as stream:
                    stream.writestr(info,b'policy')
                    if duplicate:
                        with warnings.catch_warnings():
                            warnings.simplefilter('ignore');stream.writestr(name,b'policy')
                lock=copy.deepcopy(installer.LOCK);lock['archives']['plink1'].update(sha256=installer.digest(path),members={name:hashlib.sha256(b'policy').hexdigest()})
                with patch.object(installer,'LOCK',lock):
                    with self.assertRaises(ValueError):installer.extract(path,'plink1',dest)
                self.assertEqual([],list(dest.iterdir()))

    def test_failure_diagnostics_retain_attempt_history(self):
        with tempfile.TemporaryDirectory() as folder:
            args=self.args(Path(folder));self.assertEqual(3,self.call(args));self.assertEqual(3,self.call(args));record=self.record(args)
            self.assertEqual(1,len(record['history']));self.assertEqual(2,len(list((args.report/'attempts').iterdir())))


if __name__=='__main__':unittest.main()
