#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install the locked private Windows mashr preparation runtime, never user analyses."""
import argparse
from datetime import datetime,timezone
import json
from pathlib import Path
import shutil
import sys
import urllib.error
import urllib.request
# Explicit trusted sibling modules also work with Python -I (no user PYTHONPATH).
sys.path.insert(0,str(Path(__file__).resolve().parent))
from mashr_original_runtime import ACTION,LOCK,Unavailable,ProcessFailure,canonical,census,disjoint,environment,extract,load_lock,load_monitor,pin,platform_admission,run_owned,verify_home,verify_rhome,write


def acquire(item,cache,allow_download,report):
    target=cache/item['file'];expected={k:item[k] for k in ['bytes','sha256']}
    if target.exists():
        if pin(target)!=expected:raise ValueError('Existing exact cache file is corrupt; preserve it and select a NEW cache: '+str(target))
        return target,dict(file=item['file'],source='verified-existing-cache',**expected)
    if not allow_download:raise Unavailable('Required exact cached artifact unavailable and download not permitted: '+item['file'])
    partial=cache/(item['file']+'.partial-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'))
    try:
        with urllib.request.urlopen(urllib.request.Request(item['url'],headers={'User-Agent':'OmicsMatic-mashr-runtime-setup/1'}),timeout=60) as source,partial.open('xb') as out:
            total=0
            while block:=source.read(1_048_576):
                total+=len(block)
                if total>item['bytes']:raise ValueError('Official artifact exceeded exact locked size: '+item['file'])
                out.write(block)
    except (OSError,urllib.error.URLError) as error:raise Unavailable('Official exact artifact download unavailable; partial retained '+str(partial)+': '+str(error)) from error
    if pin(partial)!=expected:raise ValueError('Downloaded exact artifact checksum/size mismatch; preserve '+str(partial))
    # Exclusive creation never overwrites another setup's cache result; keep raw partial.
    try:
        with partial.open('rb') as source,target.open('xb') as out:shutil.copyfileobj(source,out,1_048_576)
    except FileExistsError:
        if pin(target)!=expected:raise ValueError('Concurrent cache publication differs from the lock')
    if pin(target)!=expected:raise ValueError('Published cache bytes changed')
    return target,dict(file=item['file'],source='downloaded-official-exact-artifact',url=item['url'],raw_download=partial.name,**expected)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--r-home',required=True,type=Path);parser.add_argument('--home',required=True,type=Path);parser.add_argument('--report',required=True,type=Path);parser.add_argument('--cache',required=True,type=Path);parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true');args=parser.parse_args()
    rhome,home,report,cache=[canonical(getattr(args,n)) for n in ['r_home','home','report','cache']]
    disjoint(rhome,home,report,cache)
    if report.exists():raise ValueError('Preserve previous setup evidence; require a NEW report directory')
    report.mkdir(parents=True)
    record=dict(schema_version=1,status='SETUP_PENDING',user_analysis_admitted=False,profile='windows-x86_64-R4.6.1-mashr0.2.79',recorded_utc=datetime.now(timezone.utc).isoformat(),home=str(home),rhome=str(rhome),install_authorized=args.allow_install,download_authorized=args.allow_download)
    try:
        if not args.allow_install:raise Unavailable('Private installation permission not supplied; require explicit --allow-install')
        platform_admission();lock=load_lock();record['lock']=pin(LOCK)
        if home.exists():raise ValueError('Private runtime home must be NEW; preserve existing installed files')
        before=verify_rhome(rhome,lock);write(report/'rhome-before.json',before)
        cache.mkdir(parents=True,exist_ok=True);receipts=[];artifacts={}
        for item in [*lock['packages'],*lock['monitor']['artifacts']]:
            path,receipt=acquire(item,cache,args.allow_download,report);artifacts[item['file']]=path;receipts.append(receipt)
        write(report/'download-receipts.json',receipts)
        try:
            home.mkdir(parents=True);(home/'library').mkdir();(home/'monitor').mkdir()
            for item in lock['packages']:extract(artifacts[item['file']],home/'library',item['package'])
            extract(artifacts[lock['monitor']['artifacts'][0]['file']],home/'monitor')
        except PermissionError as denied:raise Unavailable('Private dependency installation is forbidden at the selected home; choose a permitted location or skip dependent analysis: '+str(denied)) from denied
        installed=verify_home(home,lock);monitor=load_monitor(home,lock)
        shutil.copyfile(LOCK,report/'runtime-lock.json');health=Path(__file__).with_name('mashr-runtime-health.R');shutil.copyfile(health,report/'health.R')
        versions={p['package']:p['version'] for p in lock['packages']};versions.update(lock['R_base_dependencies']);(report/'expected-versions.tsv').write_text('package\tversion\n'+''.join(k+'\t'+v+'\n' for k,v in sorted(versions.items())),encoding='utf-8',newline='\n')
        env=environment(rhome,home,report/'scratch')
        try:process=run_owned([rhome/'bin/Rscript.exe','--vanilla',report/'health.R',home/'library',report/'expected-versions.tsv',report],report,'health',env,monitor)
        except ProcessFailure as failed:
            if failed.result['exit_code']==3:raise Unavailable('Exact private package/DLL capability unavailable; inspect complete health.stderr') from failed
            raise
        assert int((report/'R-process-pid.txt').read_text()) in {p['pid'] for p in process['observed_process_identities']},'Observed R PID is not an admitted owned process identity'
        after=verify_rhome(rhome,lock);write(report/'rhome-after.json',after)
        if before!=after or installed!=verify_home(home,lock):raise ValueError('R/private package/monitor files changed during setup health check')
        record.update(status='COMPLETED_MASHR_PREPARATION_RUNTIME_SETUP_ONLY',R_version='4.6.1',mashr_version='0.2.79',ashr_version='2.2-63',rscript=dict(path=str(rhome/'bin/Rscript.exe'),**pin(rhome/'bin/Rscript.exe')),
            python=dict(executable=sys.executable,version=sys.version),packages=lock['packages'],runtime_files=installed,R_runtime_files=lock['rhome_runtime_files'],health='two-feature analytic Gaussian mean/SD/LFSR and all exact package versions passed',health_process=process,source_to_binary_reproduction_verified=False,required_action=ACTION)
        write(home/'RUNTIME.json',dict(schema_version=1,profile=lock['profile'],lock=pin(LOCK),setup_report=str(report/'COMPLETED.json'),library='library',monitor='monitor'))
        (report/'WARNINGS.md').write_text('# Runtime setup only: no analysis admitted\n\nPrivate R packages and process monitor installed; the two-feature analytic health check passed. Original-source preparation and native analysis remain separate actions.\n\n'+ACTION+'\n',encoding='utf-8',newline='\n')
        record['source_pins']={p.name:pin(p) for p in [Path(__file__),Path(__file__).with_name('mashr_original_runtime.py'),LOCK,health]}
        record['report_files']=census(report)
        write(report/'COMPLETED.json',record)
        if verify_home(home,lock)!=installed or verify_rhome(rhome,lock)!=before:raise ValueError('Runtime changed at setup finalization')
        print(json.dumps(dict(status=record['status'],home=str(home),packages=len(lock['packages']),library_files=len(installed['library']),monitor_files=len(installed['monitor']),user_analysis_admitted=False)));return 0
    except (Exception,KeyboardInterrupt) as failure:
        code=3 if isinstance(failure,Unavailable) else 2
        if (report/'COMPLETED.json').exists():(report/'COMPLETED.json').rename(report/'REJECTED-COMPLETION.json')
        record.update(status='SKIP3_DEPENDENCY_UNAVAILABLE' if code==3 else 'FAIL2_NO_RUNTIME_ADMITTED',error=str(failure),required_action=ACTION)
        write(report/'FAILURE.json',record);(report/'WARNINGS.md').write_text('# mashr '+record['status']+'\n\n'+str(failure)+'\n\n'+ACTION+'\n',encoding='utf-8',newline='\n');print('mashr '+record['status']+': '+str(failure)+'\n'+ACTION,file=sys.stderr);return code


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('mashr FAIL2: '+str(failure)+'\n'+ACTION,file=sys.stderr);sys.exit(2)
