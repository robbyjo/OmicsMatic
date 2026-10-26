"""Installer policy only: mocked probes never establish scientific validity."""
import argparse,hashlib,json,tempfile,unittest,zipfile
from pathlib import Path
from unittest.mock import patch
import install_kallisto as installer


class KallistoDependencyPolicyTest(unittest.TestCase):
    def args(self,root,allowed=False):return argparse.Namespace(environment=root/'home',archive=root/'cached.zip',report=root/'report',allow_download=allowed)
    def record(self,args):return json.loads((args.report/'dependency-status.json').read_text())
    def fake(self,args,changed=False):
        args.environment.mkdir();path=args.environment/'kallisto.exe';path.write_bytes(b'fixture-not-a-program');lock=dict(installer.LOCK);lock['files']={'kallisto.exe':hashlib.sha256(path.read_bytes()).hexdigest()}
        metadata={key:lock[key] for key in ['version','profile','archive_sha256','files']};(args.environment/installer.MARKER).write_text(json.dumps(metadata))
        if changed:path.write_bytes(b'changed-fixture')
        return lock
    def test_missing_runtime_without_permission_skips_without_download_or_execution(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer,'download') as download,patch.object(installer,'probe') as probe:
            args=self.args(Path(folder));self.assertEqual(3,installer.install(args));download.assert_not_called();probe.assert_not_called();self.assertFalse(args.environment.exists());self.assertEqual('skipped-install-not-permitted',self.record(args)['status']);self.assertIn('Required action',(args.report/'WARNINGS.md').read_text())
    def test_verified_existing_profile_reuses_with_download_forbidden(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer,'download') as download,patch.object(installer,'probe') as probe:
            args=self.args(Path(folder));lock=self.fake(args)
            with patch.object(installer,'LOCK',lock):self.assertEqual(0,installer.install(args))
            download.assert_not_called();probe.assert_called_once();self.assertTrue(self.record(args)['runtime_reused']);self.assertFalse(self.record(args)['download_authorized'])
    def test_unmanaged_home_is_preserved_even_when_download_allowed(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer,'download') as download,patch.object(installer,'probe') as probe:
            args=self.args(Path(folder),True);args.environment.mkdir();original=args.environment/'original';original.write_text('retained');self.assertEqual(3,installer.install(args));self.assertEqual('retained',original.read_text());download.assert_not_called();probe.assert_not_called();self.assertIn('new --environment',self.record(args)['error'])
    def test_changed_runtime_member_is_not_executed_or_overwritten(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer,'probe') as probe:
            args=self.args(Path(folder),True);lock=self.fake(args,True)
            with patch.object(installer,'LOCK',lock):self.assertEqual(3,installer.install(args))
            probe.assert_not_called();self.assertEqual(b'changed-fixture',(args.environment/'kallisto.exe').read_bytes())
    def test_extra_runtime_file_prevents_reuse(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer,'probe') as probe:
            args=self.args(Path(folder));lock=self.fake(args);(args.environment/'extra.dll').write_text('retained')
            with patch.object(installer,'LOCK',lock):self.assertEqual(3,installer.install(args))
            probe.assert_not_called();self.assertTrue((args.environment/'extra.dll').exists())
    def test_wrong_host_skips_before_download_or_probe(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer.sys,'platform','linux'),patch.object(installer,'download') as download,patch.object(installer,'probe') as probe:
            args=self.args(Path(folder),True);self.assertEqual(3,installer.install(args));self.assertEqual('skipped-platform-unavailable',self.record(args)['status']);download.assert_not_called();probe.assert_not_called()
    def test_bad_cached_archive_is_preserved_without_execution(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer,'download') as download,patch.object(installer,'probe') as probe:
            args=self.args(Path(folder),True);args.archive.write_bytes(b'corrupt');self.assertEqual(3,installer.install(args));self.assertEqual(b'corrupt',args.archive.read_bytes());download.assert_not_called();probe.assert_not_called();self.assertFalse(args.environment.exists())
    def test_downloaded_bad_hash_is_not_published_or_executed(self):
        with tempfile.TemporaryDirectory() as folder,patch.object(installer,'download',side_effect=lambda path:path.write_bytes(b'corrupt')),patch.object(installer,'probe') as probe:
            args=self.args(Path(folder),True);self.assertEqual(3,installer.install(args));self.assertFalse(args.environment.exists());self.assertFalse(args.archive.exists());probe.assert_not_called();self.assertIn('hash differs',self.record(args)['error'])
    def test_archive_paths_and_symlinks_are_rejected_before_extraction(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);dest=root/'dest';dest.mkdir()
            for name,link in [('kallisto/../escape',False),('kallisto/kallisto.exe',True)]:
                archive=root/('link.zip' if link else 'escape.zip');info=zipfile.ZipInfo(name)
                if link:info.create_system=3;info.external_attr=0o120777<<16
                with zipfile.ZipFile(archive,'w') as stream:stream.writestr(info,b'fixture')
                with patch.object(installer,'LOCK',dict(installer.LOCK,files={'kallisto.exe':'0'*64})):
                    with self.assertRaises(ValueError):installer.extract(archive,dest)
                self.assertEqual([],list(dest.iterdir()))


if __name__=='__main__':unittest.main()
