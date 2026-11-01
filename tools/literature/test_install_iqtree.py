# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import signal
import struct
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch
import install_iqtree as setup


class IqtreeSetupTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()

    def arguments(self, **changes):
        values = dict(profile='linux-x86_64-iqtree-2.3.6', home=self.root/'home', cache=self.root/'cache', report=self.root/'report')
        values.update(changes)
        args = []
        for key, value in values.items():
            args.extend(['--'+key.replace('_','-'), str(value)])
        return args

    def invoke(self, arguments):
        stdout, stderr = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            code = setup.main(arguments)
        return code, json.loads(stdout.getvalue()), stderr.getvalue()

    def test_explicit_install_permission_is_required_without_a_home_or_cache(self):
        code, value, stderr = self.invoke(self.arguments())
        self.assertEqual(code, 3);self.assertFalse(value['scientific_analysis_admitted'])
        self.assertIn('--allow-install',value['error']);self.assertIn('SKIP3',stderr);self.assertIn('Required action:',stderr)
        self.assertFalse((self.root/'home').exists());self.assertFalse((self.root/'cache').exists());self.assertFalse((self.root/'report/COMPLETED.json').exists())
        self.assertIn('ANALYSIS MUST BE SKIPPED',(self.root/'report/WARNINGS.md').read_text())

    def test_unsupported_native_host_skips_before_creating_runtime_files(self):
        with patch.object(setup.platform,'system',return_value='Windows'):
            code,value,stderr=self.invoke(self.arguments()+['--allow-install'])
        self.assertEqual(code,3);self.assertIn('WSL',value['error']);self.assertIn('SKIP DEPENDENT ANALYSIS',stderr);self.assertFalse((self.root/'home').exists())

    def test_unknown_exact_edition_is_skipped_without_a_substitute(self):
        with patch.object(setup,'host',return_value=dict(os='Linux',architecture='x86_64',kernel='test',helpers={})):
            code,value,_=self.invoke(self.arguments(profile='linux-x86_64-iqtree-1.6.619')+['--allow-install'])
        self.assertEqual(code,3);self.assertIn('no other edition',value['error']);self.assertFalse((self.root/'home').exists());self.assertFalse((self.root/'cache').exists())

    def test_existing_report_is_preserved_before_provision_can_run(self):
        report=self.root/'report';report.mkdir();(report/'sentinel').write_text('keep')
        with patch.object(setup,'provision') as provision:
            code,value,stderr=self.invoke(self.arguments()+['--allow-install'])
        self.assertEqual(code,2);provision.assert_not_called();self.assertEqual((report/'sentinel').read_text(),'keep');self.assertEqual(list(report.iterdir()),[report/'sentinel']);self.assertIn('FAIL2',stderr)

    def test_overlapping_report_is_refused_without_creating_any_home(self):
        with patch.object(setup,'provision') as provision:
            code,_,_=self.invoke(self.arguments(report=self.root/'home')+['--allow-install'])
        self.assertEqual(code,2);provision.assert_not_called();self.assertFalse((self.root/'home').exists())

    def test_overlapping_cache_and_home_are_refused_without_touching_existing_files(self):
        home=self.root/'home';home.mkdir();(home/'sentinel').write_text('keep')
        code,value,_=self.invoke(self.arguments(cache=home/'cache')+['--allow-install'])
        self.assertEqual(code,2);self.assertIn('nonoverlapping',value['error']);self.assertEqual((home/'sentinel').read_text(),'keep');self.assertFalse((home/'cache').exists())

    def test_unmanaged_existing_home_is_a_retained_integrity_failure(self):
        home=self.root/'home';home.mkdir();(home/'sentinel').write_text('keep')
        with patch.object(setup,'host',return_value=dict(os='Linux',architecture='x86_64',kernel='test',helpers={})),patch.object(setup,'smoke') as smoke:
            code,value,_=self.invoke(self.arguments()+['--allow-install'])
        self.assertEqual(code,2);self.assertIn('unmanaged/provisional',value['error']);smoke.assert_not_called();self.assertEqual((home/'sentinel').read_text(),'keep')

    def test_cache_lock_is_preserved_without_claiming_its_owner_is_alive_or_dead(self):
        cache=self.root/'cache';cache.mkdir();lock=cache/'.iqtree-install.lock';lock.write_text('old attempt\n')
        with patch.object(setup,'host',return_value=dict(os='Linux',architecture='x86_64',kernel='test',helpers={})),patch.object(setup,'obtain') as obtain:
            code,value,_=self.invoke(self.arguments()+['--allow-install'])
        self.assertEqual(code,3);self.assertIn('Existing cache lock preserved',value['error']);obtain.assert_not_called();self.assertEqual(lock.read_text(),'old attempt\n');self.assertFalse((self.root/'home').exists())

    def test_missing_smoke_output_is_failure_not_dependency_skip(self):
        def missing(args,report,record):
            record['stage']='verify-original-runtime';raise FileNotFoundError('missing completed source output')
        with patch.object(setup,'provision',side_effect=missing):code,value,stderr=self.invoke(self.arguments()+['--allow-install'])
        self.assertEqual(code,2);self.assertIn('missing completed',value['error']);self.assertIn('FAIL2',stderr);self.assertFalse((self.root/'report/COMPLETED.json').exists())

    def test_permission_failure_after_launch_is_failure_with_retained_diagnostics(self):
        def denied(args,report,record):
            record['stage']='verify-original-runtime';raise PermissionError('source output changed permissions')
        with patch.object(setup,'provision',side_effect=denied):code,value,_=self.invoke(self.arguments()+['--allow-install'])
        self.assertEqual(code,2);self.assertIn('permissions',value['error']);self.assertFalse((self.root/'report/COMPLETED.json').exists())

    def test_prelaunch_installation_denial_is_an_explicit_skip(self):
        with patch.object(setup,'provision',side_effect=PermissionError('installation forbidden')):code,value,_=self.invoke(self.arguments()+['--allow-install'])
        self.assertEqual(code,3);self.assertIn('installation forbidden',value['error']);self.assertFalse((self.root/'report/COMPLETED.json').exists())

    def test_report_write_failure_cannot_publish_a_completion(self):
        def ready(args,report,record):record.update(status='installed-smoke-tested-runtime-only',home=str(args.home),runtime_marker_sha256='a'*64)
        with patch.object(setup,'provision',side_effect=ready),patch.object(setup,'write_json',side_effect=OSError('injected report disk failure')):
            code,value,stderr=self.invoke(self.arguments()+['--allow-install'])
        self.assertEqual(code,2);self.assertEqual(value['status'],'FAIL2-report-write-failed');self.assertIn('injected report disk failure',stderr);self.assertFalse((self.root/'report/COMPLETED.json').exists())

    def test_duplicate_and_oversized_json_metadata_are_not_admitted(self):
        path=self.root/'metadata.json';path.write_text('{"profile":1,"profile":2}')
        with self.assertRaisesRegex(ValueError,'Duplicate'):setup.read_json(path)
        path.write_bytes(b' '*262145)
        with self.assertRaisesRegex(ValueError,'capacity'):setup.read_json(path)

    def test_lock_and_every_bundled_input_are_pinned(self):
        lock=setup.load_kit();self.assertEqual(len(lock['profiles']),8);self.assertEqual(len(lock['bundled']),3)
        changed=self.root/'lock.json';changed.write_text('{}')
        with patch.object(setup,'LOCK_PATH',changed):
            with self.assertRaisesRegex(ValueError,'lock changed'):setup.load_kit()

    def test_unsafe_member_names_are_rejected_before_extraction(self):
        for name in ['/bin/tool','../tool','a/../b','a//b','a\\b','a\nb','']:
            with self.subTest(name=name),self.assertRaises(ValueError):setup.member_name(name)

    def test_fingerprints_reject_symlinks_hardlinks_and_oversized_files(self):
        path=self.root/'file';path.write_bytes(b'original')
        with self.assertRaisesRegex(ValueError,'oversized'):setup.fingerprint(path,3)
        linked=self.root/'linked';os.link(path,linked)
        with self.assertRaisesRegex(ValueError,'Linked'):setup.fingerprint(path)

    @staticmethod
    def elf():
        raw=bytearray(256);raw[:7]=b'\x7fELF\x02\x01\x01'
        struct.pack_into('<HHIQQQIHHHHHH',raw,16,2,62,1,128,64,0,0,64,56,1,0,0,0)
        struct.pack_into('<IIQQQQQQ',raw,64,1,5,0,0,0,256,256,4096)
        return raw

    def test_static_elf_shape_is_checked_independently_of_a_version_string(self):
        self.assertTrue(setup.static_elf(self.elf())['PT_DYNAMIC_absent'])
        cases=[]
        for offset,fmt,value in [(0,'B',0),(4,'B',1),(5,'B',2),(18,'H',183),(32,'Q',2**63),(52,'H',0),(54,'H',8),(56,'H',65),(64,'I',2),(64,'I',3),(96,'Q',257),(104,'Q',128)]:
            raw=self.elf();struct.pack_into('<'+fmt,raw,offset,value);cases.append(raw)
        raw=self.elf();struct.pack_into('<II',raw,64,0x6474e551,7);cases.append(raw)
        for raw in cases:
            with self.subTest(raw=bytes(raw[:24])),self.assertRaises(ValueError):setup.static_elf(raw)

    def pin(self,raw):
        return dict(name='example.tar.gz',bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest(),url='https://github.com/iqtree/iqtree2/releases/download/test/example.tar.gz')

    def test_absent_archive_requires_download_permission_before_network_access(self):
        cache=self.root/'cache';cache.mkdir()
        with patch.object(setup.urllib.request,'urlopen') as urlopen:
            with self.assertRaisesRegex(setup.Unavailable,'allow-download'):setup.obtain(self.pin(b'archive'),cache,[],False)
        urlopen.assert_not_called();self.assertEqual(list(cache.iterdir()),[])

    def test_offline_archive_is_verified_without_network_or_cache_copy(self):
        cache=self.root/'cache';cache.mkdir();offline=self.root/'offline';offline.mkdir();raw=b'offline archive';path=offline/'example.tar.gz';path.write_bytes(raw)
        with patch.object(setup.urllib.request,'urlopen') as urlopen:self.assertEqual(setup.obtain(self.pin(raw),cache,[offline],False),path)
        urlopen.assert_not_called();self.assertEqual(list(cache.iterdir()),[])

    def test_corrupt_cache_is_preserved_without_falling_back_to_another_archive(self):
        cache=self.root/'cache';cache.mkdir();path=cache/'example.tar.gz';path.write_bytes(b'wrong')
        with patch.object(setup.urllib.request,'urlopen') as urlopen:
            with self.assertRaisesRegex(ValueError,'changed pinned'):setup.obtain(self.pin(b'correct'),cache,[],True)
        urlopen.assert_not_called();self.assertEqual(path.read_bytes(),b'wrong')

    def test_bounded_download_and_prior_partials_are_retained_without_publication(self):
        cache=self.root/'cache';cache.mkdir();pin=self.pin(b'good')
        with patch.object(setup.urllib.request,'urlopen',return_value=io.BytesIO(b'too long')):
            with self.assertRaisesRegex(ValueError,'byte capacity'):setup.obtain(pin,cache,[],True)
        self.assertFalse((cache/pin['name']).exists());partial=cache/(pin['name']+'.partial');self.assertTrue(partial.exists());before=partial.read_bytes()
        with patch.object(setup.urllib.request,'urlopen') as urlopen:
            with self.assertRaisesRegex(ValueError,'Prior partial'):setup.obtain(pin,cache,[],True)
        urlopen.assert_not_called();self.assertEqual(partial.read_bytes(),before)

    def test_complete_download_is_exclusively_published_then_reused(self):
        cache=self.root/'cache';cache.mkdir();raw=b'complete archive';pin=self.pin(raw)
        with patch.object(setup.urllib.request,'urlopen',return_value=io.BytesIO(raw)):
            path=setup.obtain(pin,cache,[],True)
        self.assertEqual(path.read_bytes(),raw);self.assertFalse((cache/(pin['name']+'.partial')).exists());self.assertEqual(path.stat().st_nlink,1)
        with patch.object(setup.urllib.request,'urlopen') as urlopen:self.assertEqual(setup.obtain(pin,cache,[],False),path)
        urlopen.assert_not_called()

    def test_likelihood_reconciliation_uses_each_printed_decimal_quantization(self):
        setup.rounded(-41.470338030601056,'-41.4703');setup.rounded(-2.7763588787874234,'-2.77636')
        for text in ['NaN','Infinity','-40.0','0x1.0p2']:
            with self.subTest(text=text),self.assertRaises(ValueError):setup.rounded(-41.470338030601056,text)

    def test_marker_without_completed_report_is_not_a_completed_installation(self):
        home=self.root/'home';home.mkdir();(home/setup.MARKER).write_text('{}');report=self.root/'report';report.mkdir()
        with self.assertRaises(FileNotFoundError):setup.verify_completion(report,home,'linux-x86_64-iqtree-2.3.6')


@unittest.skipUnless(sys.platform.startswith('linux'),'Owned process controls require Linux waitid/WNOWAIT and process groups')
class IqtreeOwnedProcessTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name).resolve()

    def work(self,name):
        work=self.root/name;work.mkdir();return work

    def command(self,code):return setup.limited([sys.executable,'-c',code])

    def test_clean_environment_success_has_no_live_owned_processes(self):
        work=self.work('success')
        with patch.dict(os.environ,{'IQTREE_TEST_PRIVATE_ENV':'must not enter child'}):
            value=setup.owned_run(self.command("import os; print(os.environ.get('IQTREE_TEST_PRIVATE_ENV','absent'))"),work)
        self.assertEqual(value['exit_code'],0);self.assertEqual(value['owned_live_after_cleanup'],[]);self.assertEqual((work/'process.stdout').read_text().strip(),'absent')

    def test_timeout_cleans_only_the_owned_tree_including_term_ignoring_descendants(self):
        work=self.work('timeout');sentinel=subprocess.Popen([sys.executable,'-c','import time; time.sleep(30)'],start_new_session=True)
        try:
            code="import os,signal,time; signal.signal(signal.SIGTERM,signal.SIG_IGN); child=os.fork(); open('child.pid','w').write(str(child)); time.sleep(30)"
            with self.assertRaisesRegex(RuntimeError,'wall-time'):setup.owned_run(self.command(code),work,wall_seconds=.2)
            record=json.loads((work/'process.json').read_text());self.assertEqual(record['owned_live_after_cleanup'],[]);self.assertIsNone(sentinel.poll())
        finally:sentinel.terminate();sentinel.wait(timeout=5)

    def test_successful_leader_with_live_descendants_is_refused_and_cleaned(self):
        work=self.work('orphan');code="import os,time; child=os.fork(); time.sleep(30) if child==0 else None; os._exit(0)"
        with self.assertRaisesRegex(RuntimeError,'live owned descendants'):setup.owned_run(self.command(code),work)
        record=json.loads((work/'process.json').read_text());self.assertEqual(record['exit_code'],0);self.assertEqual(record['owned_live_after_cleanup'],[]);self.assertEqual(record['status'],'failed-process-only')

    def test_complete_output_capacity_is_enforced_and_failure_files_are_retained(self):
        work=self.work('capacity')
        with self.assertRaisesRegex(ValueError,'capacity'):setup.owned_run(self.command("import time; open('large','wb').write(b'x'*4096); time.sleep(30)"),work,maximum_bytes=1024)
        self.assertEqual((work/'large').stat().st_size,4096);self.assertEqual(json.loads((work/'process.json').read_text())['owned_live_after_cleanup'],[])

    def test_unavailable_program_launch_retains_actionable_process_evidence(self):
        work=self.work('missing')
        with self.assertRaisesRegex(setup.Unavailable,'launch unavailable'):setup.owned_run(['/not/an/existing/program'],work)
        record=json.loads((work/'process.json').read_text());self.assertIsNone(record['exit_code']);self.assertEqual(record['owned_live_after_cleanup'],[])

    def test_sigill_is_an_unavailable_cpu_capability_without_admitted_results(self):
        work=self.work('sigill')
        with self.assertRaisesRegex(setup.Unavailable,'CPU'):setup.owned_run(self.command('import os,signal; os.kill(os.getpid(),signal.SIGILL)'),work)
        self.assertEqual(json.loads((work/'process.json').read_text())['exit_code'],-signal.SIGILL)

    def test_sigterm_cancellation_cleans_the_worker_and_preserves_unrelated_sentinel(self):
        work=self.work('cancel');script=self.root/'cancel.py'
        source="import sys,signal\nfrom pathlib import Path\nsys.path.insert(0,"+repr(str(setup.HERE))+" )\nimport install_iqtree as setup\nsignal.signal(signal.SIGTERM,setup.interrupted)\ntry:setup.owned_run(setup.limited([sys.executable,'-c','import time; time.sleep(30)']),Path("+repr(str(work))+"))\nexcept KeyboardInterrupt:sys.exit(2)\n"
        script.write_text(source);sentinel=subprocess.Popen([sys.executable,'-c','import time; time.sleep(30)'],start_new_session=True)
        child=subprocess.Popen([sys.executable,str(script)],start_new_session=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        try:
            deadline=time.monotonic()+5
            while not (work/'running.json').exists():
                self.assertIsNone(child.poll());self.assertLess(time.monotonic(),deadline);time.sleep(.02)
            child.send_signal(signal.SIGTERM);self.assertEqual(child.wait(timeout=5),2);self.assertIsNone(sentinel.poll());self.assertEqual(json.loads((work/'process.json').read_text())['owned_live_after_cleanup'],[])
        finally:
            if child.poll() is None:child.kill();child.wait(timeout=5)
            sentinel.terminate();sentinel.wait(timeout=5)


if __name__=='__main__':unittest.main()
