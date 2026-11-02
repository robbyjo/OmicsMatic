#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Dependency SKIP3, cache integrity, owned termination and temp-file churn controls."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
import mashr_preparation as bridge
from mashr_original_runtime import ProcessFailure,environment,pin,run_owned,write

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/mashr-runtime'


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--runtime',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir()
    try:
        rt=bridge.runtime(args.runtime);source_files=[Path(__file__),ROOT/'tools/literature/install_mashr_runtime.py',ROOT/'tools/literature/mashr_original_runtime.py',bridge.LOCK];bound={p.relative_to(ROOT).as_posix():pin(p) for p in source_files};controls=[]
        for name,expected,detail in [('no-install',3,'--allow-install'),('offline-missing',3,'download not permitted'),('corrupt-cache',2,'cache file is corrupt')]:
            root=out/name;root.mkdir();cache=root/'cache';cache.mkdir();home=root/'home';report=root/'setup'
            if name=='corrupt-cache':(cache/rt['lock']['packages'][0]['file']).write_bytes(b'corrupt')
            command=[sys.executable,'-I','-B',ROOT/'tools/literature/install_mashr_runtime.py','--r-home',rt['rhome'],'--home',home,'--report',report,'--cache',cache]
            if name!='no-install':command+=['--allow-install']
            try:record=run_owned(command,root,'controller',environment(rt['rhome'],rt['home'],root/'scratch'),rt['monitor'])
            except ProcessFailure as failure:record=failure.result
            assert record['exit_code']==expected and not home.exists() and not (report/'COMPLETED.json').exists();warning=(report/'WARNINGS.md').read_text();assert detail in warning and ('SKIP3' if expected==3 else 'FAIL2') in warning and 'Required action' in warning
            controls.append(dict(name=name,expected_exit_code=expected,process=record));print(name+': '+str(expected)+' with prominent recovery and no installation',flush=True)
        for name,kind in [('timeout','wall_seconds'),('log-limit','log_bytes'),('rss-limit','rss_bytes'),('temp-churn',None)]:
            root=out/name;root.mkdir();script=root/'probe.py'
            if name=='temp-churn':code='import pathlib,time\np=pathlib.Path("churn");p.mkdir()\nstop=time.monotonic()+2\nwhile time.monotonic()<stop:\n f=p/"transient";f.write_bytes(b"x");f.unlink()\nprint("complete")\n'
            else:code='import subprocess,sys,time\np=subprocess.Popen([sys.executable,"-I","-B","-c","import time;time.sleep(30)"])\nprint(p.pid,flush=True)\n'+('print("x"*200000,flush=True)\n' if name=='log-limit' else '')+'time.sleep(30)\n'
            script.write_text(code,encoding='utf-8',newline='\n');limits=dict(bridge.DEFAULT_PROCESS);limits['poll_seconds']=.05
            if name=='timeout':limits['wall_seconds']=.4
            if name=='log-limit':limits['log_bytes']=1024
            if name=='rss-limit':limits['rss_bytes']=1_000_000
            failure=None
            try:record=run_owned([sys.executable,'-I','-B',script],root,'probe',environment(rt['rhome'],rt['home'],root/'scratch'),rt['monitor'],limits)
            except Exception as error:failure=error;record=json.loads((root/'probe.process.json').read_text())
            assert (failure is None)==(kind is None),(name,str(failure))
            for identity in record['observed_process_identities']:
                try:p=rt['monitor'].Process(identity['pid']);assert p.create_time()!=identity['creation_time'] or not p.is_running(),(name,'orphaned owned process')
                except rt['monitor'].NoSuchProcess:pass
            assert name!='log-limit' or 'log capacity' in str(failure)
            assert name!='rss-limit' or record['peaks']['rss_bytes']>limits['rss_bytes']
            assert name!='timeout' or record['wall_seconds']>=limits['wall_seconds']
            controls.append(dict(name=name,expected_failure=kind is not None,process=record));print(name+': expected outcome; all observed owned processes terminal',flush=True)
        root=out/'linux-unavailable';root.mkdir();home=root/'home';report=root/'setup';cache=root/'cache';cache.mkdir()
        def linux(p):return '/mnt/'+p.drive[0].lower()+'/'+p.as_posix()[3:]
        command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','python3','-I','-B',linux(ROOT/'tools/literature/install_mashr_runtime.py'),'--r-home',linux(rt['rhome']),'--home',linux(home),'--report',linux(report),'--cache',linux(cache),'--allow-install']
        with (root/'controller.stdout').open('xb') as stdout,(root/'controller.stderr').open('xb') as stderr:result=subprocess.run(command,cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=120,check=False)
        assert result.returncode==3 and not home.exists() and 'SKIP3' in (report/'WARNINGS.md').read_text();controls.append(dict(name='linux-unavailable',expected_exit_code=3,argv=command));print('linux profile: SKIP3, no installation',flush=True)
        assert all(pin(ROOT/n)==v for n,v in bound.items());write(out/'COMPLETED.json',dict(schema_version=1,status='passed-mashr-runtime-dependency-and-owned-process-controls',controls=controls,source_pins=bound,files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}));return 0
    except Exception as failure:write(out/'FAILURE.json',dict(status='FAIL2_MASHR_RUNTIME_CONTROLS',error=str(failure)));raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_MASHR_RUNTIME_CONTROLS: '+str(failure),file=sys.stderr);sys.exit(2)
