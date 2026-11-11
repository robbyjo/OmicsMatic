# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
import contextlib,io,json,os,signal,stat,subprocess,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
import install_mafft as setup


class MafftSetupTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name).resolve()

    def args(self,**changes):
        values=dict(profile='linux-x86_64-mafft-7.526',home=self.root/'home',cache=self.root/'cache',report=self.root/'report');values.update(changes)
        return [a for key,value in values.items() for a in ['--'+key.replace('_','-'),str(value)]]

    def invoke(self,args):
        out,err=io.StringIO(),io.StringIO()
        with contextlib.redirect_stdout(out),contextlib.redirect_stderr(err):code=setup.main(args)
        return code,json.loads(out.getvalue()),err.getvalue()

    def test_new_install_without_authorization_skips_and_admits_no_result(self):
        code,value,error=self.invoke(self.args())
        self.assertEqual(code,3);self.assertIn('--allow-install',value['error']);self.assertFalse(value['scientific_analysis_admitted'])
        self.assertIn('SKIP DEPENDENT ANALYSIS',error);self.assertIn('Required action:',error)
        self.assertFalse((self.root/'home').exists());self.assertFalse((self.root/'cache').exists());self.assertFalse((self.root/'report/COMPLETED.json').exists())
        self.assertIn('SKIP3',(self.root/'report/WARNINGS.md').read_text(encoding='utf-8'))

    def test_unsupported_native_host_skips_before_installing_any_files(self):
        with patch.object(setup.platform,'system',return_value='Windows'):
            code,value,error=self.invoke(self.args()+['--allow-install'])
        self.assertEqual(code,3);self.assertIn('WSL',value['error']);self.assertIn('Required action:',error);self.assertFalse((self.root/'home').exists())

    def test_major_only_paper_edition_never_selects_another_release(self):
        with patch.object(setup,'host',return_value={}),patch.object(setup,'ascii_owned'):
            code,value,_=self.invoke(self.args(profile='linux-x86_64-mafft-7')+['--allow-install'])
        self.assertEqual(code,3);self.assertIn('no other edition',value['error']);self.assertFalse((self.root/'home').exists());self.assertFalse((self.root/'cache').exists())

    def test_existing_report_is_never_replaced_even_on_failure(self):
        path=self.root/'report';path.mkdir();(path/'original.txt').write_bytes(b'preserve')
        code,value,error=self.invoke(self.args())
        self.assertEqual(code,2);self.assertEqual(sorted(p.name for p in path.iterdir()),['original.txt']);self.assertEqual((path/'original.txt').read_bytes(),b'preserve')
        self.assertIn('FAIL2',error);self.assertIn('report_error',value)

    def test_failed_completion_has_a_prominent_human_readable_failure_record(self):
        real_write=setup.write_json
        def provision(args,report,record):
            args.home.mkdir();real_write(args.home/setup.MARKER,dict(provisional=True))
            record.update(home=str(args.home),status='installed-smoke-tested-runtime-only',runtime_marker_sha256=setup.sha(args.home/setup.MARKER))
        def write(path,value):
            if Path(path).name=='COMPLETED.json':raise OSError('injected final publication failure')
            return real_write(path,value)
        with patch.object(setup,'provision',side_effect=provision),patch.object(setup,'write_json',side_effect=write):
            code,value,error=self.invoke(self.args()+['--allow-install'])
        self.assertEqual(code,2);self.assertIn('FAIL2',error);self.assertFalse((self.root/'report/COMPLETED.json').exists())
        text=(self.root/'report/FAILURE.md').read_text(encoding='utf-8')
        for phrase in ['FAIL2','provisional','Required action:','NEW home/report paths']:self.assertIn(phrase,text)
        self.assertEqual(json.loads((self.root/'report/FAILURE.json').read_bytes())['exit_code'],2)

    def test_overlapping_report_does_not_create_a_runtime_or_directory(self):
        code,value,_=self.invoke(self.args(report=self.root/'home/report'))
        self.assertEqual(code,2);self.assertFalse((self.root/'home').exists());self.assertFalse(value['scientific_analysis_admitted'])

    def test_unmanaged_home_is_preserved_and_no_original_program_is_started(self):
        home=self.root/'home';home.mkdir();(home/'supplied.txt').write_bytes(b'keep')
        with patch.object(setup,'host',return_value={}),patch.object(setup,'ascii_owned'),patch.object(setup,'smoke',side_effect=AssertionError('must not launch')):
            code,value,_=self.invoke(self.args())
        self.assertEqual(code,2);self.assertIn('unmanaged/provisional',value['error']);self.assertEqual((home/'supplied.txt').read_bytes(),b'keep');self.assertFalse((home/setup.MARKER).exists())

    def test_closed_kit_checks_every_health_resource_and_notice(self):
        kit=setup.load_kit();self.assertEqual(set(kit['profiles']),{'linux-x86_64-mafft-'+v for v in setup.VERSIONS});self.assertEqual(len(kit['bundled']),5)
        self.assertEqual([r['name'] for r in kit['setup_health']],['DNA-auto','AA-local-1000','AA-add-keep-map'])
        self.assertIs(kit['public_analysis_adapter_available'],False)

    def test_changed_lock_fails_before_any_original_runtime_is_created(self):
        path=self.root/'changed-lock.json';path.write_bytes(b'{}')
        with patch.object(setup,'LOCK_PATH',path):self.assertRaisesRegex(ValueError,'lock changed',setup.load_kit)

    def test_json_duplicate_keys_are_not_silently_overwritten(self):
        path=self.root/'duplicate.json';path.write_bytes(b'{"profile":"7.313","profile":"7.526"}')
        self.assertRaisesRegex(ValueError,'Duplicate JSON key',setup.read_json,path)

    def test_complete_metadata_capacity_does_not_return_a_prefix(self):
        path=self.root/'large.json';path.write_bytes(b' '*262145)
        self.assertRaisesRegex(ValueError,'capacity',setup.read_json,path)

    def test_missing_offline_archive_does_not_download_without_authorization(self):
        pin=setup.load_kit()['profiles']['linux-x86_64-mafft-7.526']['archive']
        with patch.object(setup.urllib.request,'urlopen',side_effect=AssertionError('not authorized')):
            self.assertRaisesRegex(setup.Unavailable,'--allow-download',setup.obtain,pin,self.root,[],False)

    def test_corrupt_existing_archive_is_retained_instead_of_redownloaded(self):
        pin=setup.load_kit()['profiles']['linux-x86_64-mafft-7.526']['archive'];path=self.root/pin['filename'];path.write_bytes(b'wrong-complete-archive')
        with patch.object(setup.urllib.request,'urlopen',side_effect=AssertionError('do not replace')):
            self.assertRaisesRegex(ValueError,'pinned file',setup.obtain,pin,self.root,[],True)
        self.assertEqual(path.read_bytes(),b'wrong-complete-archive')

    def test_prior_partial_download_is_preserved(self):
        pin=setup.load_kit()['profiles']['linux-x86_64-mafft-7.526']['archive'];partial=self.root/(pin['filename']+'.partial');partial.write_bytes(b'partial')
        self.assertRaisesRegex(ValueError,'partial download preserved',setup.obtain,pin,self.root,[],True);self.assertEqual(partial.read_bytes(),b'partial')

    def test_nonofficial_download_url_is_rejected_before_network_access(self):
        pin=dict(setup.load_kit()['profiles']['linux-x86_64-mafft-7.526']['archive'],url='https://invalid.example/other.tgz')
        with patch.object(setup.urllib.request,'urlopen',side_effect=AssertionError('not official')):
            self.assertRaisesRegex(ValueError,'official MAFFT',setup.obtain,pin,self.root,[],True)

    def test_original_archive_member_names_cannot_escape(self):
        for name in ['/tmp/escape','../escape','a/../escape','a//escape','a\\escape','a/./escape','a\nname']:
            with self.subTest(name=name):self.assertRaises(ValueError,setup.member_name,name)

    def test_managed_shell_paths_are_explicit_and_do_not_admit_metacharacters(self):
        for name in ['/tmp/name space','/tmp/caf\u00e9','/tmp/a;false','/tmp/$HOME','/tmp/a:b']:
            with self.subTest(name=name):self.assertRaises(ValueError,setup.ascii_owned,Path(name))

    def test_static_elf_refuses_truncated_or_foreign_programs(self):
        for raw in [b'',b'#! /bin/sh\n',b'\x7fELF'+b'\0'*60]:
            with self.subTest(raw=raw[:8]):self.assertRaises(ValueError,setup.static_elf,raw)

    @unittest.skipUnless(os.name=='posix','POSIX aliases/modes require Linux')
    def test_private_runtime_hardlinks_remain_refused_while_declared_system_aliases_can_be_hashed(self):
        path=self.root/'a';alias=self.root/'b';path.write_bytes(b'same-system-image');os.link(path,alias)
        self.assertRaisesRegex(ValueError,'Linked',setup.fingerprint,path)
        self.assertEqual(setup.fingerprint(path,allow_system_hardlinks=True),setup.fingerprint(alias,allow_system_hardlinks=True))

    @unittest.skipUnless(os.name=='posix','Private directory modes require Linux')
    def test_every_new_intermediate_parent_has_private_mode(self):
        home=self.root/'home';home.mkdir(mode=0o700);setup.make_parents(home,home/'one/two/three')
        for name in ['one','one/two','one/two/three']:self.assertEqual(stat.S_IMODE((home/name).stat().st_mode),0o700)

    @unittest.skipUnless(os.name=='posix','Private directory modes require Linux')
    def test_existing_permissive_parent_is_not_silently_repaired(self):
        home=self.root/'home';home.mkdir(mode=0o700);(home/'existing').mkdir(mode=0o755)
        self.assertRaisesRegex(ValueError,'unsafe mode',setup.make_parents,home,home/'existing/child');self.assertEqual(stat.S_IMODE((home/'existing').stat().st_mode),0o755)

    @unittest.skipUnless(os.name=='posix','Owned process sessions require Linux')
    def test_forbidden_launch_records_failure_and_never_admits_a_process(self):
        with patch.object(setup.subprocess,'Popen',side_effect=PermissionError('forbidden')):
            self.assertRaises(setup.Unavailable,setup.owned_run,['/bin/true'],self.root)
        value=json.loads((self.root/'process.json').read_bytes());self.assertEqual(value['status'],'failed-process-only');self.assertIsNone(value['exit_code']);self.assertEqual(value['owned_live_after_cleanup'],[])

    @unittest.skipUnless(os.name=='posix','Owned process sessions require Linux')
    def test_nonzero_exit_is_a_failure_after_owned_group_cleanup(self):
        self.assertRaisesRegex(RuntimeError,'exit code 9',setup.owned_run,['/bin/sh','-c','exit 9'],self.root)
        value=json.loads((self.root/'process.json').read_bytes());self.assertEqual(value['exit_code'],9);self.assertEqual(value['owned_live_after_cleanup'],[]);self.assertIs(value['leader_pid_reserved_until_cleanup'],True)

    @unittest.skipUnless(os.name=='posix','Owned process sessions require Linux')
    def test_wall_timeout_cleans_the_actual_owned_descendant_group(self):
        self.assertRaisesRegex(RuntimeError,'wall-time',setup.owned_run,['/bin/sh','-c','sleep 30 & wait'],self.root,wall_seconds=.1)
        value=json.loads((self.root/'process.json').read_bytes());self.assertEqual(value['owned_live_after_cleanup'],[]);self.assertIs(value['leader_pid_reserved_until_cleanup'],True)

    @unittest.skipUnless(os.name=='posix','Owned process sessions require Linux')
    def test_terminal_leader_with_live_owned_descendants_is_not_a_completed_process(self):
        self.assertRaisesRegex(RuntimeError,'live owned descendants',setup.owned_run,['/bin/sh','-c','sleep 30 &'],self.root)
        self.assertEqual(json.loads((self.root/'process.json').read_bytes())['owned_live_after_cleanup'],[])

    @unittest.skipUnless(os.environ.get('JLINALG_MAFFT_SETUP_ROOT'),'Exact private setup matrix not supplied')
    def test_all_six_real_installs_keep_complete_original_files_and_setup_receipts(self):
        root=Path(os.environ['JLINALG_MAFFT_SETUP_ROOT']);lock=setup.load_kit()
        for version in setup.VERSIONS:
            with self.subTest(version=version):
                profile='linux-x86_64-mafft-'+version;home=root/'homes'/version;marker=setup.read_json(home/setup.MARKER)
                self.assertEqual(setup.verify_files(home,lock['profiles'][profile],lock,True),marker['elf'])
                self.assertEqual(setup.verify_completion(marker['setup_report_directory'],home,profile)['scientific_analysis_admitted'],False)


if __name__=='__main__':unittest.main()
