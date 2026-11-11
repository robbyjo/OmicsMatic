# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Dependency/permission, integrity and real owned-process setup regressions."""
import argparse
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tarfile
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parent))
import install_nextflow as target

OUTPUT=None


class InstallerTests(unittest.TestCase):
    def setUp(self):
        self.root=OUTPUT/self._testMethodName;self.root.mkdir()
    def args(self,**values):
        result=dict(profile='linux-x86_64-nextflow-21.04.1',home=self.root/'home',report=self.root/'report',cache=self.root/'cache',archive_dir=None,allow_install=False,allow_download=False);result.update(values);return argparse.Namespace(**result)
    def install(self,args):
        with contextlib.redirect_stdout(io.StringIO()),contextlib.redirect_stderr(io.StringIO()),patch.object(target,'helpers',return_value={}):return target.install(args)
    def failure(self,args,code,phrase):
        self.assertEqual(self.install(args),code);proof=target.json_read(args.report/'FAILURE.json');self.assertEqual(proof['exit_code'],code);self.assertFalse(proof['user_analysis_admitted']);self.assertIn(phrase,proof['error']);self.assertIn('Skip dependent Nextflow analysis',proof['required_action']);self.assertIn('Required action:',(args.report/'FAILURE.md').read_text());self.assertFalse((args.report/'COMPLETED.json').exists())
    def test_install_forbidden_skips_without_creating_home(self):
        args=self.args();self.failure(args,3,'Installation is not allowed');self.assertFalse(args.home.exists())
    def test_download_forbidden_skips_exact_missing_dependency(self):
        args=self.args(allow_install=True);self.failure(args,3,'download is not allowed');self.assertFalse(args.home.exists())
    def test_unreported_patch_is_not_substituted(self):
        args=self.args(profile='linux-x86_64-nextflow-21.04.99');self.failure(args,3,'unsupported');self.assertFalse(args.home.exists())
    def test_unsupported_host_skips(self):
        args=self.args()
        with patch.object(target.platform,'machine',return_value='aarch64'):self.failure(args,3,'Linux x86_64')
    def test_changed_lock_fails_without_installing(self):
        wrong=self.root/'changed-lock.json';wrong.write_text('{}')
        with patch.object(target,'LOCK',wrong):self.failure(self.args(allow_install=True),2,'lock changed')
    def test_corrupt_cache_is_preserved_without_download(self):
        args=self.args(allow_install=True);args.cache.mkdir();path=args.cache/'nextflow-21.04.1-all';path.write_bytes(b'bad archive')
        self.failure(args,2,'corrupt cached asset');self.assertEqual(path.read_bytes(),b'bad archive');self.assertFalse(args.home.exists())
    def test_changed_existing_home_is_preserved(self):
        args=self.args();args.home.mkdir();marker=args.home/target.MARKER
        value=dict(schema_version=1,profile=args.profile,version='21.04.1',lock_sha256=target.LOCK_SHA256,payload={'nextflow':{}});target.write(marker,value);before=marker.read_bytes()
        self.failure(args,2,'payload changed');self.assertEqual(marker.read_bytes(),before)
    def test_duplicate_and_nonfinite_json_rejected(self):
        for i,raw in enumerate([b'{"a":1,"a":2}',b'{"a":NaN}']):
            path=self.root/str(i);path.write_bytes(raw)
            with self.assertRaises(ValueError):target.json_read(path)
    def test_linked_private_ancestor_rejected(self):
        real=self.root/'real';real.mkdir();link=self.root/'linked';os.symlink(real,link)
        with self.assertRaises(ValueError):target.private(link/'home')
    def test_runtime_link_cannot_escape_root(self):
        inside=self.root/'inside';inside.mkdir();outside=self.root/'outside';outside.write_bytes(b'x');os.symlink(outside,inside/'bad')
        with self.assertRaises(ValueError):target.inventory(inside)
    def test_java_archive_traversal_cannot_write_outside_home(self):
        archive=self.root/'bad.tar.gz';data=b'not java'
        with tarfile.open(archive,'w:gz') as out:
            member=tarfile.TarInfo('runtime/../escape');member.size=len(data);out.addfile(member,io.BytesIO(data))
        home=self.root/'home';home.mkdir()
        with self.assertRaises(ValueError):target.extract_java(archive,home,dict(package={'archive_root':'runtime'},members={}))
        self.assertFalse((home/'escape').exists());self.assertFalse((self.root/'escape').exists())
    def test_mode_audit_does_not_relax_bytes_or_link_targets(self):
        a={'f':dict(kind='file',bytes=1,sha256='a',mode=0o755),'s':dict(kind='symlink',target='f')};b={'f':dict(kind='file',bytes=1,sha256='a',mode=0o777),'s':dict(kind='symlink',target='f')}
        self.assertEqual(target.content_inventory(a),target.content_inventory(b));b['f']['sha256']='b';self.assertNotEqual(target.content_inventory(a),target.content_inventory(b));b['f']['sha256']='a';b['s']['target']='other';self.assertNotEqual(target.content_inventory(a),target.content_inventory(b))
    def test_missing_permitted_helper_is_unavailable(self):
        with self.assertRaises(target.Unavailable):target.helpers({'required_helpers':[str(self.root/'absent')]})
    def test_failing_real_child_retains_exit_and_logs(self):
        with self.assertRaises(RuntimeError):target.execute(['/usr/bin/false'],dict(PATH='/usr/bin:/bin'),self.root,'child',1)
        proof=target.json_read(self.root/'child.process.json');self.assertEqual(proof['exit_code'],1);self.assertEqual(proof['owned_live_after_cleanup'],[]);self.assertEqual(proof['status'],'FAIL2-retained-no-result')
    def test_orphan_ignoring_term_is_killed_after_parent_success(self):
        program="import os,signal,time,pathlib;pid=os.fork();\nif pid==0:\n signal.signal(signal.SIGTERM,signal.SIG_IGN);pathlib.Path('orphan.pid').write_text(str(os.getpid()));time.sleep(30)\nelse:\n while not pathlib.Path('orphan.pid').exists():time.sleep(.01)\n"
        target.execute(['/usr/bin/python3','-c',program],dict(PATH='/usr/bin:/bin'),self.root,'orphan',10)
        proof=target.json_read(self.root/'orphan.process.json');launch=target.json_read(self.root/'orphan.launch.json');self.assertEqual(proof['exit_code'],0);self.assertEqual(target.group_live(launch['process_group'],launch['start_ticks']),[])
    def test_timeout_retains_failure_and_no_live_owned_process(self):
        with self.assertRaises(subprocess.TimeoutExpired):target.execute(['/usr/bin/python3','-c','import time;time.sleep(30)'],dict(PATH='/usr/bin:/bin'),self.root,'timeout',1)
        proof=target.json_read(self.root/'timeout.process.json');self.assertEqual(proof['status'],'FAIL2-retained-no-result');self.assertEqual(proof['owned_live_after_cleanup'],[])


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();OUTPUT=args.out.resolve();OUTPUT.mkdir(parents=True,exist_ok=False)
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(InstallerTests))
    target.write(OUTPUT/'COMPLETED.json' if result.wasSuccessful() else OUTPUT/'FAILURE.json',dict(status='passed-Nextflow-installer-dependency-integrity-and-owned-process-controls' if result.wasSuccessful() else 'failed',tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),skipped=len(result.skipped)))
    sys.exit(0 if result.wasSuccessful() else 2)
