# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify real setup evidence, CLI dependency failures and no-install rechecks."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'tools/literature'


def read(path):return json.loads(path.read_bytes())
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def check_tree(root,rows):
    actual={}
    for path in sorted(root.rglob('*')):
        info=path.lstat()
        if stat.S_ISDIR(info.st_mode):continue
        name=path.relative_to(root).as_posix()
        if stat.S_ISLNK(info.st_mode):actual[name]=dict(kind='symlink',target=os.readlink(path));assert path.resolve().is_relative_to(root)
        else:assert stat.S_ISREG(info.st_mode);actual[name]=dict(kind='file',**pin(path),mode=stat.S_IMODE(info.st_mode))
    assert actual==rows,'Complete tree/bytes/link/mode inventory differs: '+str(root)


def check_report(report,home,profile,locked,installer_pin):
    completion=read(report/'COMPLETED.json');assert completion['status']=='COMPLETE_SETUP_ONLY' and completion['user_analysis_admitted'] is False and pin(report/'manifest.json')==completion['manifest']
    manifest=read(report/'manifest.json');assert manifest['home']==str(home) and manifest['profile']==profile and manifest['user_analysis_admitted'] is False
    assert not (report/'FAILURE.json').exists() and pin(home/'.omicsmatic-nextflow.json')==manifest['marker']
    expected=dict(manifest['files']);expected['manifest.json']=dict(kind='file',**pin(report/'manifest.json'),mode=stat.S_IMODE((report/'manifest.json').stat().st_mode));expected['COMPLETED.json']=dict(kind='file',**pin(report/'COMPLETED.json'),mode=stat.S_IMODE((report/'COMPLETED.json').stat().st_mode));check_tree(report,expected)
    request=read(report/'request.json');assert request['installer']==installer_pin and request['user_analysis_admitted'] is False
    marker=read(home/'.omicsmatic-nextflow.json');payload=dict(marker['payload']);payload['.omicsmatic-nextflow.json']=dict(kind='file',**pin(home/'.omicsmatic-nextflow.json'),mode=stat.S_IMODE((home/'.omicsmatic-nextflow.json').stat().st_mode));check_tree(home,payload)
    expected_engine={k:locked['profiles'][profile]['engine'][k] for k in ['bytes','sha256']};assert pin(home/'nextflow')==expected_engine
    java={name[5:]:{k:v for k,v in row.items() if k!='mode'} for name,row in marker['payload'].items() if name.startswith('java/')};expected_java={name:{k:v for k,v in row.items() if k!='mode'} for name,row in locked['java']['members'].items()};assert java==expected_java
    for name,row in manifest['helpers'].items():
        path=Path(name);real=path.resolve(strict=True);assert str(real)==row['real_path'] and pin(real)=={k:row[k] for k in ['bytes','sha256']}
    health=read(report/'health.json');assert health['status']=='passed-three-task-original-Nextflow-DAG' and health['task_count']==3 and health['user_analysis_admitted'] is False
    assert (report/'health/published/result.txt').read_bytes()==b'ACGT\nTTAA\n'
    for path in report.rglob('*.process.json'):assert read(path)['exit_code']==0 and read(path)['owned_live_after_cleanup']==[]
    assert 'Required action:' in (report/'WARNINGS.md').read_text()
    if marker['permission_mode_departures']:assert 'Effective filesystem modes differ' in (report/'WARNINGS.md').read_text()
    return dict(profile=profile,home=str(home),report=str(report),marker=pin(home/'.omicsmatic-nextflow.json'),completion=pin(report/'COMPLETED.json'),permission_departures=len(marker['permission_mode_departures']))


def main():
    if not __debug__:raise RuntimeError('Never disable setup evidence assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--run',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args()
    run=a.run.resolve();out=a.out.resolve();base=ROOT/'build/literature-review/nextflow-runtime';assert os.uname().sysname=='Linux' and run.is_relative_to(base.resolve()) and out.is_relative_to(base.resolve()) and not out.exists();out.mkdir(parents=True)
    script=SOURCE/'install_nextflow.py';installer_pin=pin(script);locked=read(SOURCE/'nextflow-managed-runtime-lock.json');initial=[];rechecks=[];calls=[]
    for suffix,version in [('21-v2','21.04.1'),('22','22.10.6'),('24','24.04.4')]:
        profile='linux-x86_64-nextflow-'+version;home=run/('home-'+suffix);report=run/('report-'+suffix)
        initial.append(check_report(report,home,profile,locked,installer_pin))
        next_report=out/('recheck-'+version)
        command=[sys.executable,str(script),'--profile',profile,'--home',str(home),'--report',str(next_report),'--cache',str(run/'cache')]
        with (out/('recheck-'+version+'.stdout')).open('xb') as stdout,(out/('recheck-'+version+'.stderr')).open('xb') as stderr:process=subprocess.run(command,stdout=stdout,stderr=stderr,timeout=600)
        calls.append(dict(command=command,exit_code=process.returncode));assert process.returncode==0,'Inspect retained recheck logs'
        rechecks.append(check_report(next_report,home,profile,locked,installer_pin));assert rechecks[-1]['marker']==initial[-1]['marker']
        print(version+': real offline install and no-install recheck pass with unchanged runtime payload',flush=True)
    negative=[]
    for case,code,extra in [('install-forbidden',3,[]),('download-forbidden',3,['--allow-install']),('unknown-edition',3,[]) ,('corrupt-cache',2,['--allow-install'])]:
        stage=out/case;stage.mkdir();home=stage/'home';report=stage/'report';cache=stage/'cache';cache.mkdir();profile='linux-x86_64-nextflow-21.04.99' if case=='unknown-edition' else 'linux-x86_64-nextflow-21.04.1'
        if case=='corrupt-cache':(cache/'nextflow-21.04.1-all').write_bytes(b'deliberately corrupt cache')
        command=[sys.executable,str(script),'--profile',profile,'--home',str(home),'--report',str(report),'--cache',str(cache),*extra]
        with (stage/'stdout.log').open('xb') as stdout,(stage/'stderr.log').open('xb') as stderr:process=subprocess.run(command,stdout=stdout,stderr=stderr,timeout=60)
        assert process.returncode==code and not home.exists() and not (report/'COMPLETED.json').exists();failure=read(report/'FAILURE.json');assert failure['exit_code']==code and failure['user_analysis_admitted'] is False and 'Skip dependent Nextflow analysis' in failure['required_action']
        if case=='corrupt-cache':assert (cache/'nextflow-21.04.1-all').read_bytes()==b'deliberately corrupt cache'
        row=dict(case=case,exit_code=process.returncode,status=failure['status'],home_not_created=True,user_analysis_admitted=False);negative.append(row);calls.append(dict(command=command,exit_code=process.returncode));print(json.dumps(row),flush=True)
    unit=read(run/'installer-tests-1/COMPLETED.json');assert unit['tests']==16 and unit['failures']==unit['errors']==unit['skipped']==0
    download=read(run/'download-probe-1/COMPLETED.json');assert download['installer']==installer_pin and download['asset']=={k:locked['profiles']['linux-x86_64-nextflow-21.04.1']['engine'][k] for k in ['bytes','sha256']}
    assert pin(script)==installer_pin
    result=dict(status='passed-three-exact-Nextflow-runtime-setups-and-rechecks',initial=initial,rechecks=rechecks,negative_CLI_controls=negative,unit_controls=unit,permitted_download_control=download,calls=calls,synthetic_DAG_runs=6,synthetic_tasks=18,source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [script,SOURCE/'nextflow-managed-runtime-lock.json',SOURCE/'test_install_nextflow.py',Path(__file__)]},user_analysis_admitted=False,public_analysis_adapter_available=False,catalog_promoted=False)
    write(out/'COMPLETED.json',result);return 0


if __name__=='__main__':sys.exit(main())
