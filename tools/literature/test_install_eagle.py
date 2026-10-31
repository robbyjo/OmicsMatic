"""Scientific-contract, extraction, refusal and actual owned-process tests."""
import contextlib
import copy
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import platform
import signal
import subprocess
import sys
import tarfile
import tempfile
import time
import unittest
from unittest import mock

SPEC = importlib.util.spec_from_file_location('install_eagle', Path(__file__).with_name('install_eagle.py'))
E = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(E)


class TemporaryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='eagle-setup-test-')
        self.root = Path(self.temp.name).resolve()

    def tearDown(self):
        self.temp.cleanup()


class SetupTests(TemporaryTests):
    def fixture(self, name):
        return E.HERE / 'eagle-runtime/health' / name

    def call(self, extra=(), actual_host=None):
        args = ['--profile', 'linux-x86_64-eagle-2.4.1', '--home', str(self.root/'home'),
                '--cache', str(self.root/'cache'), '--report', str(self.root/'report'), *extra]
        with contextlib.redirect_stdout(io.StringIO()) as out, contextlib.redirect_stderr(io.StringIO()) as err:
            with mock.patch.object(E, 'host', return_value=actual_host or dict(os='Linux', architecture='x86_64', kernel='test')):
                code = E.main(args)
        return code, out.getvalue(), err.getvalue()

    def test_no_install_flag_skips_with_action_and_no_home(self):
        code, out, err = self.call()
        self.assertEqual(3, code)
        self.assertIn('SKIP3', out)
        self.assertIn('Required action:', err)
        self.assertFalse((self.root/'home').exists())
        self.assertIn('Required action:', (self.root/'report/WARNINGS.md').read_text())

    def test_missing_download_flag_skips_without_network(self):
        with mock.patch.object(E.urllib.request, 'urlopen', side_effect=AssertionError('Network forbidden')):
            code, _, _ = self.call(['--allow-install'])
        self.assertEqual(3, code)
        self.assertFalse((self.root/'home').exists())

    def test_unmanaged_home_is_preserved(self):
        (self.root/'home').mkdir()
        sentinel = self.root/'home/keep.txt'
        sentinel.write_text('retain')
        code, _, _ = self.call(['--allow-install','--allow-download'])
        self.assertEqual(3, code)
        self.assertEqual('retain', sentinel.read_text())

    def test_existing_report_never_overwritten(self):
        (self.root/'report').mkdir()
        sentinel = self.root/'report/WARNINGS.md'
        sentinel.write_text('previous evidence')
        code, _, err = self.call()
        self.assertEqual(2, code)
        self.assertEqual('previous evidence', sentinel.read_text())
        self.assertIn('Required action:', err)

    def test_historical_profile_never_substituted(self):
        code, out, _ = self.call(['--profile','linux-x86_64-eagle-2.3.5'])
        self.assertEqual(3, code)
        self.assertIn('no edition is substituted', out)

    def test_overlapping_paths_fail(self):
        code, _, _ = self.call(['--home',str(self.root/'report/home')])
        self.assertEqual(2, code)
        self.assertFalse((self.root/'report').exists())

    def test_windows_setup_is_explicitly_unavailable(self):
        with mock.patch.object(E.platform, 'system', return_value='Windows'):
            with self.assertRaisesRegex(E.Unavailable, 'Linux x86_64'):
                E.host()

    def test_corrupt_cache_is_retained(self):
        cache = self.root/'cache'
        cache.mkdir()
        name = 'Eagle_v2.4.1.tar.gz'
        corrupt = cache/name
        corrupt.write_bytes(b'corrupt')
        with self.assertRaisesRegex(ValueError,'changed pinned'):
            E.obtain(name, cache, [], True)
        self.assertEqual(b'corrupt', corrupt.read_bytes())

    def test_partial_download_is_retained(self):
        cache = self.root/'cache'
        cache.mkdir()
        part = cache/'Eagle_v2.4.1.tar.gz.part'
        part.write_bytes(b'partial')
        with self.assertRaisesRegex(ValueError,'partial download'):
            E.obtain('Eagle_v2.4.1.tar.gz', cache, [], True)
        self.assertEqual(b'partial', part.read_bytes())

    def output(self, label):
        contract = E.LOCK['health_contracts'][label]
        rows = self.fixture('cohort-target.vcf').read_text().splitlines()
        lines = []
        for line in rows:
            if line.startswith('#'):
                lines.append(line)
                continue
            fields = line.split('\t')
            for i, call in enumerate(fields[9:],9):
                if '.' in call:
                    fields[i] = '1|0' if contract['missing']=='impute' else call if contract['mode']=='cohort' else './.'
                else:
                    # Deliberately reverse orientation: the contract must test
                    # dosage without inventing known biological phase.
                    fields[i] = '|'.join(reversed(call.replace('|','/').split('/')))
            lines.append('\t'.join(fields))
        path = self.root/(label+'.vcf')
        path.write_text('\n'.join(lines)+'\n',encoding='utf-8')
        return path, contract

    def test_all_four_independent_health_contracts(self):
        for label in E.LOCK['health_contracts']:
            with self.subTest(label=label):
                output, contract = self.output(label)
                self.assertEqual(contract, E.audit_health(self.fixture('cohort-target.vcf'),output,contract))

    def mutate(self, edit, label='cohort-keep'):
        output, contract = self.output(label)
        lines = output.read_text().splitlines()
        edit(lines)
        output.write_text('\n'.join(lines)+'\n',encoding='utf-8')
        with self.assertRaises(ValueError):
            E.audit_health(self.fixture('cohort-target.vcf'),output,contract)

    def test_health_rejects_dosage_change(self):
        self.mutate(lambda lines: lines.__setitem__(4,lines[4].replace('1|0','1|1',1)))

    def test_health_rejects_unphased_observed_call(self):
        self.mutate(lambda lines: lines.__setitem__(4,lines[4].replace('1|0','1/0',1)))

    def test_health_rejects_changed_partial_call(self):
        self.mutate(lambda lines: lines.__setitem__(6,lines[6].replace('0/.','./.',1)))

    def test_reference_keep_requires_whole_missing_call(self):
        self.mutate(lambda lines: lines.__setitem__(6,lines[6].replace('./.','0/.',1)), 'reference-keep')

    def test_imputed_health_requires_complete_call(self):
        self.mutate(lambda lines: lines.__setitem__(6,lines[6].replace('1|0','.|0',1)), 'cohort-impute')

    def test_health_rejects_truncated_population(self):
        self.mutate(lambda lines: lines.pop())

    def test_health_rejects_extra_population(self):
        self.mutate(lambda lines: lines.append(lines[-1]))

    def test_health_rejects_reordered_population(self):
        self.mutate(lambda lines: lines.__setitem__(slice(4,6),reversed(lines[4:6])))

    def test_health_rejects_sample_identity(self):
        self.mutate(lambda lines: lines.__setitem__(3,lines[3].replace('\tT0','\tOTHER')))

    def test_health_rejects_non_gt_format(self):
        self.mutate(lambda lines: lines.__setitem__(4,lines[4].replace('\tGT\t','\tDS\t')))

    def test_health_bounds_line_read(self):
        output = self.root/'oversized.vcf'
        output.write_text('##'+('x'*16384)+'\n')
        with self.assertRaisesRegex(ValueError,'bound exceeded'):
            E.read_vcf(output)

    def test_archive_traversal_and_absolute_paths_are_refused(self):
        for name in ['../escape','/absolute','foo/../../escape','foo\\escape']:
            with self.subTest(name=name), self.assertRaises(ValueError):
                E.tar_path(name)

    def test_zstd_corrupt_payload_is_refused(self):
        if platform.system() != 'Linux':
            self.skipTest('Real libzstd extraction is Linux-only')
        with self.assertRaisesRegex(ValueError,'invalid or exceeds'):
            E.zstd_tar(b'not zstd',1024)

    def test_structured_console_is_utf8_under_ascii_locale(self):
        output=io.BytesIO()
        wrapper=io.TextIOWrapper(output,encoding='ascii')
        args=['--profile','linux-x86_64-eagle-2.4.1','--home',str(self.root/'home-α-测'),
              '--cache',str(self.root/'cache'),'--report',str(self.root/'report')]
        try:
            with mock.patch.object(E.sys,'stdout',wrapper),contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(3,E.main(args))
            parsed=json.loads(output.getvalue().decode('utf-8'))
            self.assertTrue(parsed['home'].endswith('home-α-测'))
        finally:
            wrapper.detach()


@unittest.skipUnless(platform.system()=='Linux' and Path('/usr/bin/prlimit').is_file(),'Real Linux process tests')
class ProcessTests(TemporaryTests):
    def limits(self, **changes):
        return dict(E.LOCK['health_limits'], **changes)

    def process(self, code, limits=None):
        work = self.root/'process'
        work.mkdir()
        return E.run([sys.executable,'-c',code],work,'child',E.environment(self.root,work),limits or self.limits())

    def test_real_normal_process(self):
        result = self.process('print("ok")')
        self.assertEqual(0,result['exit_code'])
        self.assertEqual([],result['owned_live_after_cleanup'])
        self.assertEqual('ok\n',(self.root/'process/child.stdout').read_text())

    def test_real_failure_retains_stderr(self):
        with self.assertRaisesRegex(ValueError,'exit 7'):
            self.process('import sys; print("retained",file=sys.stderr);sys.exit(7)')
        record = json.loads((self.root/'process/child.process.json').read_text())
        self.assertEqual('failed-retained',record['status'])
        self.assertEqual([],record['owned_live_after_cleanup'])
        self.assertIn('retained',(self.root/'process/child.stderr').read_text())

    def test_real_wall_timeout_kills_descendant(self):
        code = 'import subprocess,time,sys; subprocess.Popen([sys.executable,"-c","import time;time.sleep(20)"]);time.sleep(20)'
        with self.assertRaisesRegex(ValueError,'wall-time'):
            self.process(code,self.limits(wall_seconds=.25))
        record=json.loads((self.root/'process/child.process.json').read_text())
        self.assertGreaterEqual(len(record['cleanup_observed_pids']),2)
        self.assertEqual([],record['owned_live_after_cleanup'])

    def test_real_orphan_descendant_cleaned_after_success(self):
        code='import subprocess,sys;subprocess.Popen([sys.executable,"-c","import time;time.sleep(20)"])'
        result = self.process(code)
        self.assertEqual([],result['owned_live_after_cleanup'])
        self.assertTrue(result['cleanup_observed_pids'])

    def test_real_file_size_hard_limit(self):
        with self.assertRaises(ValueError):
            self.process('open("large","wb").write(b"x"*65536)',self.limits(file_bytes=4096))
        self.assertLessEqual((self.root/'process/large').stat().st_size,4096)

    def test_real_address_space_hard_limit(self):
        with self.assertRaises(ValueError):
            self.process('a=bytearray(268435456)',self.limits(address_space_bytes=67108864))
        self.assertIn('MemoryError',(self.root/'process/child.stderr').read_text())

    def test_real_workspace_monitor(self):
        with self.assertRaisesRegex(ValueError,'workspace byte bound'):
            self.process('import time;open("large","wb").write(b"x"*32768);time.sleep(20)',self.limits(workspace_bytes=16384))

    def test_real_cpu_hard_limit(self):
        with self.assertRaisesRegex(ValueError,'Original process failed'):
            self.process('while True: pass',self.limits(cpu_seconds=1))

    def test_real_sigterm_cleans_owned_descendant_and_retains_diagnostics(self):
        work=self.root/'interrupted'
        work.mkdir()
        code=('import importlib.util,signal;from pathlib import Path;'
            's=importlib.util.spec_from_file_location("e",'+repr(str(E.HERE/'install_eagle.py'))+');'
            'e=importlib.util.module_from_spec(s);s.loader.exec_module(e);signal.signal(signal.SIGTERM,e.interrupted);'
            'w=Path('+repr(str(work))+');'
            'e.run(['+repr(sys.executable)+',"-c",'
            +repr('import subprocess,sys,time;subprocess.Popen([sys.executable,"-c","import time;time.sleep(20)"]);print("ready",flush=True);time.sleep(20)')
            +'],w,"child",e.environment(w,w),e.LOCK["health_limits"])')
        parent=subprocess.Popen([sys.executable,'-c',code],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        try:
            deadline=time.monotonic()+5
            while time.monotonic()<deadline:
                if (work/'child.stdout').exists() and b'ready' in (work/'child.stdout').read_bytes():
                    break
                time.sleep(.01)
            else:
                self.fail('Owned child did not become ready')
            parent.send_signal(signal.SIGTERM)
            parent.communicate(timeout=10)
            self.assertNotEqual(0,parent.returncode)
            record=json.loads((work/'child.process.json').read_text())
            self.assertEqual('failed-retained',record['status'])
            self.assertIn('KeyboardInterrupt',record['error'])
            self.assertEqual([],record['owned_live_after_cleanup'])
            self.assertGreaterEqual(len(record['cleanup_observed_pids']),2)
        finally:
            if parent.poll() is None:
                parent.kill();parent.communicate(timeout=5)

    def test_signal_during_launch_cannot_leave_an_unassigned_child(self):
        original=E.subprocess.Popen
        def launch_then_interrupt(*args,**kwargs):
            child=original(*args,**kwargs)
            os.kill(os.getpid(),signal.SIGTERM)
            return child
        with mock.patch.object(E.subprocess,'Popen',side_effect=launch_then_interrupt):
            with self.assertRaisesRegex(KeyboardInterrupt,'during owned launch'):
                self.process('import time;time.sleep(20)')
        record=json.loads((self.root/'process/child.process.json').read_text())
        self.assertEqual([],record['owned_live_after_cleanup'])
        self.assertEqual('failed-retained',record['status'])


if __name__ == '__main__':
    unittest.main()
