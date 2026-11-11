# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Permission/integrity/finalization policy tests; no mock constitutes scientific admission."""
import argparse
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import unittest
from unittest import mock

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'tools/literature/install_salmon.py'
KIT=ROOT/'src/main/resources/org/jlinalg/omics/salmon-runtime-setup'
OUT=None


class InstallerPolicy(unittest.TestCase):
    def setUp(self):
        self.area=OUT/self._testMethodName;self.assertFalse(self.area.exists());self.area.mkdir()
        spec=importlib.util.spec_from_file_location('salmon_policy_target',SOURCE);self.module=importlib.util.module_from_spec(spec);spec.loader.exec_module(self.module)
        self.home=self.area/'home';self.report=self.area/'report'
    def call(self,extra=(),kit=KIT,profile='linux-x86_64-salmon-1.8.0',install=None):
        argv=[str(SOURCE),'--profile',profile,'--home',str(self.home),'--report',str(self.report),'--kit',str(kit),*map(str,extra)]
        out,err=io.StringIO(),io.StringIO()
        with mock.patch.object(sys,'argv',argv),mock.patch.object(self.module.platform,'system',return_value='Linux'),mock.patch.object(self.module.platform,'machine',return_value='x86_64'),mock.patch.object(self.module,'helper_pins',return_value={}),contextlib.redirect_stdout(out),contextlib.redirect_stderr(err):
            if install is None:code=self.module.main()
            else:
                with mock.patch.object(self.module,'install',side_effect=install):code=self.module.main()
        return code,out.getvalue(),err.getvalue()
    def failure(self,code,word):
        value=json.loads((self.report/'FAILURE.json').read_bytes());self.assertEqual(code,value['exit_code']);self.assertFalse(value['user_analysis_admitted']);self.assertIn(word,value['error']);self.assertIn('Required action:',value['required_action']);self.assertIn('SKIP3' if code==3 else 'FAIL2',(self.report/'WARNINGS.md').read_text(encoding='utf-8').splitlines()[0]);self.assertFalse((self.report/'COMPLETED.json').exists())
    def copied_kit(self):
        target=self.area/'kit';shutil.copytree(KIT,target);return target

    def test_missing_install_authorization_skips_without_home_or_process(self):
        code,_,err=self.call();self.assertEqual(3,code);self.assertIn('SKIP3',err);self.failure(3,'installation is not authorized');self.assertFalse(self.home.exists());self.assertFalse(list(self.report.glob('*.launch.json')))
    def test_missing_archive_and_download_authorization_skips(self):
        archives=self.area/'archives';archives.mkdir();code,_,_=self.call(['--allow-install','--archive-dir',archives]);self.assertEqual(3,code);self.failure(3,'downloading is not authorized');self.assertFalse(self.home.exists())
    def test_unknown_exact_profile_fails(self):
        code,_,_=self.call(profile='linux-x86_64-salmon-UNKNOWN');self.assertEqual(2,code);self.failure(2,'Unknown exact');self.assertFalse(self.home.exists())
    def test_changed_lock_is_not_an_unavailable_dependency(self):
        kit=self.copied_kit();p=kit/'salmon-runtime-lock.json';p.write_bytes(p.read_bytes()+b'\n');code,_,_=self.call(kit=kit);self.assertEqual(2,code);self.failure(2,'hash differs');self.assertFalse(self.home.exists())
    def test_changed_health_input_fails_before_installation(self):
        kit=self.copied_kit();p=kit/'health-inputs/gentrome.fa';p.write_bytes(p.read_bytes()+b'changed\n');code,_,_=self.call(kit=kit);self.assertEqual(2,code);self.failure(2,'health input differs');self.assertFalse(self.home.exists())
    def test_corrupt_offline_archive_fails_and_preserves_bytes(self):
        archives=self.area/'archives';archives.mkdir();p=archives/'salmon-1.8.0_linux_x86_64.tar.gz';p.write_bytes(b'not the original archive');code,_,_=self.call(['--allow-install','--archive-dir',archives]);self.assertEqual(2,code);self.failure(2,'archive differs');self.assertEqual(b'not the original archive',p.read_bytes());self.assertFalse(self.home.exists())
    def test_existing_report_is_not_modified(self):
        self.report.mkdir();(self.report/'WARNINGS.md').write_bytes(b'prior warning');(self.report/'sentinel').write_bytes(b'original');before={p.name:p.read_bytes() for p in self.report.iterdir()};code,_,err=self.call();self.assertEqual(2,code);self.assertIn('Preserve existing',err);self.assertEqual(before,{p.name:p.read_bytes() for p in self.report.iterdir()});self.assertFalse(self.home.exists())
    def test_unregistered_home_is_not_modified(self):
        self.home.mkdir();(self.home/'sentinel').write_bytes(b'original');code,_,_=self.call(['--allow-install']);self.assertEqual(2,code);self.failure(2,'unregistered/partial');self.assertEqual(['sentinel'],[p.name for p in self.home.iterdir()]);self.assertEqual(b'original',(self.home/'sentinel').read_bytes())
    def test_missing_report_parent_skips_without_creating_it(self):
        self.report=self.area/'absent'/'report';code,_,err=self.call();self.assertEqual(3,code);self.assertIn('canonical report parent',err);self.assertFalse(self.report.parent.exists())
    def test_failure_after_marker_revokes_completion_and_keeps_prefinal_warning(self):
        def finalization(args,report):
            (report/'WARNINGS.md').write_text('original upstream warning',encoding='utf-8');self.module.write(report/'COMPLETED.json',dict(status='COMPLETE_SETUP_ONLY'));raise RuntimeError('controlled finalization failure')
        code,_,_=self.call(install=finalization);self.assertEqual(2,code);self.failure(2,'controlled finalization');self.assertTrue((self.report/'COMPLETED-REVOKED.json').exists());self.assertEqual('original upstream warning',(self.report/'WARNINGS-PREFINAL.md').read_text())
    def test_cancellation_has_named_failure_and_no_completion(self):
        code,_,_=self.call(install=KeyboardInterrupt());self.assertEqual(2,code);self.failure(2,'KeyboardInterrupt')
    def test_unavailable_host_has_required_skip_action(self):
        code,_,_=self.call(install=self.module.Unavailable('controlled missing permitted helper'));self.assertEqual(3,code);self.failure(3,'missing permitted helper')
    def test_duplicate_json_keys_fail(self):
        p=self.area/'duplicate.json';p.write_bytes(b'{"version":1,"version":2}');self.assertRaisesRegex(ValueError,'Duplicate',self.module.document,p)
    def test_archive_changed_during_consumption_cannot_pass(self):
        archive=self.area/'source.tgz';data=b'original program bytes'
        with tarfile.open(archive,'w:gz') as t:
            for name in ['root','root/bin']:
                m=tarfile.TarInfo(name);m.type=tarfile.DIRTYPE;m.mode=0o755;t.addfile(m)
            m=tarfile.TarInfo('root/bin/tool');m.mode=0o755;m.size=len(data);t.addfile(m,io.BytesIO(data))
        profile=dict(archive=self.module.pin(archive),archive_root='root',members={'bin':dict(kind='directory',mode=0o755),'bin/tool':dict(kind='file',mode=0o755,bytes=len(data),sha256=__import__('hashlib').sha256(data).hexdigest())})
        original=self.module.pin;calls=0
        def race(path,maximum=1073741824):
            nonlocal calls
            if path==archive:
                calls+=1
                if calls==2:
                    with archive.open('ab') as stream:stream.write(b'changed transport')
            return original(path,maximum)
        with mock.patch.object(self.module,'pin',side_effect=race):self.assertRaisesRegex(ValueError,'changed while being consumed',self.module.extract,archive,self.home,profile)
        self.assertEqual(data,(self.home/'bin/tool').read_bytes());self.assertFalse((self.home/self.module.MARKER).exists())
    @unittest.skipUnless(os.name=='posix','Real local process ownership requires Linux')
    def test_real_timeout_cleans_owned_group(self):
        code='import subprocess,time;subprocess.Popen(["/usr/bin/python3","-c","import time;time.sleep(30)"]);time.sleep(30)'
        self.assertRaises(subprocess.TimeoutExpired,self.module.execute,['/usr/bin/python3','-I','-B','-c',code],dict(PATH='/usr/bin:/bin',LANG='C',LC_ALL='C'),self.area,'timeout',timeout_seconds=.2)
        ledger=json.loads((self.area/'timeout.process.json').read_bytes());self.assertEqual([],ledger['owned_live_after_cleanup']);self.assertTrue(ledger['forced_cleanup']);self.assertEqual('FAIL2-retained-no-result',ledger['status'])
    @unittest.skipUnless(os.name=='posix','Real local process ownership requires Linux')
    def test_real_parent_zero_with_unfinished_worker_is_not_success(self):
        code='import subprocess;subprocess.Popen(["/usr/bin/python3","-c","import time;time.sleep(30)"])'
        self.assertRaisesRegex(RuntimeError,'owned workers remained',self.module.execute,['/usr/bin/python3','-I','-B','-c',code],dict(PATH='/usr/bin:/bin',LANG='C',LC_ALL='C'),self.area,'worker')
        ledger=json.loads((self.area/'worker.process.json').read_bytes());self.assertEqual(0,ledger['exit_code']);self.assertEqual([],ledger['owned_live_after_cleanup']);self.assertEqual('FAIL2-retained-no-result',ledger['status'])


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();OUT=a.out.resolve();assert OUT.is_relative_to((ROOT/'build/literature-review/salmon-runtime').resolve()) and not OUT.exists();OUT.mkdir(parents=True)
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(InstallerPolicy));(OUT/'RESULT.json').write_text(json.dumps(dict(tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),skips=len(result.skipped),successful=result.wasSuccessful(),scope='Policy/finalization controls; only two Linux cases run real generic owned processes. Mock cases do not admit an original runtime or scientific result.'),indent=2)+'\n',encoding='utf-8');sys.exit(0 if result.wasSuccessful() else 1)
