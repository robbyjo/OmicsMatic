from pathlib import Path
import argparse
import importlib.util
import io
import json
import os
import platform
import stat
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import urllib.error
import zipfile

R=Path(__file__).resolve().parents[3]
SOURCE=R/'src/main/resources/org/jlinalg/omics/repeat-runtime-setup/install_repeat_payload.py'
spec=importlib.util.spec_from_file_location('repeat_payload_installer',SOURCE)
installer=importlib.util.module_from_spec(spec);spec.loader.exec_module(installer)


class InstallerTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='repeat-installer-')
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name).resolve()
        self.data=b'complete selected source\n'
        self.pin=installer.fingerprint(self.data)

    def archive(self,entries):
        target=self.root/'source.tar.gz'
        with tarfile.open(target,'w:gz') as out:
            for name,data,kind in entries:
                member=tarfile.TarInfo(name);member.mode=0o755
                if kind=='link':member.type=tarfile.SYMTYPE;member.linkname='/outside'
                else:member.size=len(data)
                out.addfile(member,io.BytesIO(data) if kind!='link' else None)
        return target

    def test_safe_paths(self):
        for name in ['', '.', '../outside', '/outside', 'a/../../outside', r'a\outside','C:/outside','a\x00b']:
            with self.subTest(name=name),self.assertRaises(ValueError):installer.safe_name(name)
        self.assertEqual('source/inside.txt',installer.safe_name('./source/inside.txt'))

    def test_stream_full_bytes_and_mode(self):
        path=self.root/'source';installer.copy_stream(io.BytesIO(self.data),path,self.pin,True)
        installer.check_file(path,self.pin)
        if platform.system()=='Linux':self.assertTrue(os.access(path,os.X_OK))

    def test_stream_excess_never_admitted(self):
        path=self.root/'partial'
        with self.assertRaises(ValueError):installer.copy_stream(io.BytesIO(self.data+b'X'),path,self.pin)
        self.assertTrue(path.exists())

    def test_stream_truncation_and_same_length_change(self):
        for i,data in enumerate([self.data[:-1],b'X'+self.data[1:]]):
            with self.assertRaises(ValueError):installer.copy_stream(io.BytesIO(data),self.root/str(i),self.pin)

    def test_existing_output_never_overwritten(self):
        path=self.root/'source';path.write_bytes(b'existing')
        with self.assertRaises(FileExistsError):installer.copy_stream(io.BytesIO(self.data),path,self.pin)
        self.assertEqual(b'existing',path.read_bytes())

    def test_tar_streams_selected_and_ignores_unselected_link(self):
        archive=self.archive([('source/data',self.data,'regular'),('source/alias',b'','link')]);home=self.root/'home'
        installer.extract_tar(archive,{'source/data':('data',self.pin)},home,1024,10)
        self.assertEqual(self.data,(home/'data').read_bytes());self.assertFalse((home/'alias').exists())

    def test_tar_selected_link_refused(self):
        archive=self.archive([('source/data',b'','link')])
        with self.assertRaises(ValueError):installer.extract_tar(archive,{'source/data':('data',self.pin)},self.root/'home',1024,10)

    def test_tar_duplicate_and_unsafe_unselected_paths_refused(self):
        for entries in [[('source/data',self.data,'regular')]*2,[('../outside',b'','regular')]]:
            archive=self.archive(entries)
            with self.assertRaises(ValueError):installer.extract_tar(archive,{},self.root/'home',1024,10)

    def test_tar_missing_member_and_expansion_capacities(self):
        archive=self.archive([('source/data',self.data,'regular')])
        for selection,maximum,count in [({'missing':('data',self.pin)},1024,10),({},1,10),({},1024,0)]:
            with self.assertRaises(ValueError):installer.extract_tar(archive,selection,self.root/'home',maximum,count)

    def test_wheel_complete_member_and_crc(self):
        archive=self.root/'source.whl'
        with zipfile.ZipFile(archive,'w') as out:out.writestr('package/data',self.data)
        home=self.root/'home';installer.extract_wheel(archive,{'package/data':('data',self.pin)},home,1024,10)
        self.assertEqual(self.data,(home/'data').read_bytes())
        with self.assertRaises(ValueError):installer.extract_wheel(archive,{},home,1024,10)
        with zipfile.ZipFile(archive) as zipped:
            member=zipped.getinfo('package/data');offset=member.header_offset+30+len(member.filename.encode('utf-8'))+len(member.extra)
        raw=bytearray(archive.read_bytes());raw[offset]^=1;archive.write_bytes(raw)
        with self.assertRaises(zipfile.BadZipFile):installer.extract_wheel(archive,{'package/data':('data',self.pin)},self.root/'crc-failed',1024,10)

    def test_wheel_symlink_refused(self):
        archive=self.root/'source.whl'
        with zipfile.ZipFile(archive,'w') as out:
            member=zipfile.ZipInfo('package/data');member.create_system=3;member.external_attr=(stat.S_IFLNK|0o777)<<16;out.writestr(member,self.data)
        with self.assertRaises(ValueError):installer.extract_wheel(archive,{'package/data':('data',self.pin)},self.root/'home',1024,10)

    def test_inventory_complete_and_changed_bytes_refused(self):
        home=self.root/'home';home.mkdir();(home/'data').write_bytes(self.data)
        self.assertEqual(dict(files=1,members=2,bytes=len(self.data)),installer.inventory(home,{'data':self.pin},1024,10))
        (home/'data').write_bytes(b'X'+self.data[1:])
        with self.assertRaises(ValueError):installer.inventory(home,{'data':self.pin},1024,10)

    def test_inventory_extra_empty_directory_refused(self):
        home=self.root/'home';home.mkdir();(home/'extra').mkdir()
        with self.assertRaises(ValueError):installer.inventory(home,{},1024,10)

    def test_inventory_link_refused(self):
        home=self.root/'home';home.mkdir();(self.root/'outside').write_bytes(self.data)
        (home/'data').symlink_to(self.root/'outside')
        with self.assertRaises(ValueError):installer.inventory(home,{'data':self.pin},1024,10)

    def test_obtain_offline_exact_copy_and_existing_cache(self):
        reference=self.root/'reference';reference.mkdir();cache=self.root/'cache';cache.mkdir()
        (reference/'archive').write_bytes(self.data);lock={'archives':{'archive':self.pin}}
        path=installer.obtain('archive',lock,cache,[reference],False)
        self.assertEqual(self.data,path.read_bytes());self.assertEqual(path,installer.obtain('archive',lock,cache,[],False))

    def test_obtain_missing_skips_and_corruption_fails(self):
        cache=self.root/'cache';cache.mkdir();lock={'archives':{'archive':self.pin}}
        with self.assertRaises(installer.Unavailable):installer.obtain('archive',lock,cache,[],False)
        (cache/'archive').write_bytes(b'bad')
        with self.assertRaises(ValueError):installer.obtain('archive',lock,cache,[],False)
        self.assertEqual(b'bad',(cache/'archive').read_bytes())

    def test_no_permission_means_skip_without_mutation(self):
        arguments=['--home',str(self.root/'home'),'--cache',str(self.root/'cache'),'--report',str(self.root/'report'),'--profile','none']
        with patch('sys.stderr',io.StringIO()):self.assertEqual(3,installer.main(arguments))
        self.assertEqual([],list(self.root.iterdir()))

    def test_non_linux_setup_skips_without_mutation(self):
        arguments=['--home',str(self.root/'home'),'--cache',str(self.root/'cache'),'--report',str(self.root/'report'),'--profile','none','--allow-install']
        with patch.object(platform,'system',return_value='Windows'),patch('sys.stderr',io.StringIO()):self.assertEqual(3,installer.main(arguments))
        self.assertEqual([],list(self.root.iterdir()))

    def test_preflight_capacity_before_payload_creation(self):
        lock_path=SOURCE.parent.parent/'repeat-runtime-lock.json';raw=lock_path.read_bytes();lock=json.loads(raw)
        args=argparse.Namespace(profile='linux-x86_64-repeatmasker-4.2.6-custom-rmblast',python=None,workspace_bytes=1048576,file_count=8192,allow_download=False)
        with self.assertRaisesRegex(ValueError,'capacities'):
            installer.install(args,SOURCE.parent,raw,lock,self.root/'home',self.root/'report',self.root/'cache',[])
        self.assertEqual([],list(self.root.iterdir()))

    def test_missing_historical_interpreter_is_unavailable_before_payload_creation(self):
        lock_path=SOURCE.parent.parent/'repeat-runtime-lock.json';raw=lock_path.read_bytes();lock=json.loads(raw)
        args=argparse.Namespace(profile='linux-x86_64-repeatmasker-4.1.5-dfam3.7-rmblast',python=str(self.root/'missing-python'),workspace_bytes=17179869184,file_count=8192,allow_download=False)
        with self.assertRaisesRegex(installer.Unavailable,'interpreter is missing'):
            installer.install(args,SOURCE.parent,raw,lock,self.root/'home',self.root/'report',self.root/'cache',[])
        self.assertEqual([],list(self.root.iterdir()))

    def test_unavailable_https_transport_skips_without_changing_integrity_classification(self):
        cache=self.root/'cache';cache.mkdir();lock={'archives':{'archive':dict(self.pin,url='https://example.invalid/pinned')}}
        with patch.object(installer.urllib.request,'urlopen',side_effect=urllib.error.URLError('controlled unavailable transport')):
            with self.assertRaisesRegex(installer.Unavailable,'HTTPS archive is unavailable'):installer.obtain('archive',lock,cache,[],True)
        self.assertEqual([],list(cache.iterdir()))


if __name__=='__main__':unittest.main(verbosity=2)
