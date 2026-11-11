#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Resealed corruption, target separation, and portable-child controls."""
import argparse
import concurrent.futures
from datetime import datetime
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
import mashr_preparation as bridge
from mashr_original_runtime import ProcessFailure,census,environment,pin,run_owned,write

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/mashr-runtime'
JAVA=Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe').resolve()


def replace_json(path,value):
    path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def reseal(root):
    manifest=json.loads((root/'manifest.json').read_text())
    manifest['files']={n:v for n,v in census(root,536_870_912,20000).items() if n not in ['manifest.json','COMPLETED.json']}
    replace_json(root/'manifest.json',manifest)
    marker=json.loads((root/'COMPLETED.json').read_text());marker['manifest']=pin(root/'manifest.json');replace_json(root/'COMPLETED.json',marker)


def change_numeric(path):
    rows=path.read_text(encoding='utf-8').splitlines();values=rows[0].split('\t');values[0]=str(float(values[0])+.25);rows[0]='\t'.join(values);path.write_text('\n'.join(rows)+'\n',encoding='utf-8',newline='\n')


def linux(path):
    return '/mnt/'+path.drive[0].lower()+'/'+path.as_posix()[3:]


def ed_log_without_run_timestamp(path):
    rows=path.read_bytes().splitlines(keepends=True)
    assert len(rows)>3
    datetime.strptime(rows[2].decode('ascii').rstrip('\r\n'),'#%a %b %d %H:%M:%S %Y')
    rows[2]=b'#<ORIGINAL_ED_RUN_TIMESTAMP>\n'
    return b''.join(rows)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--runtime',required=True,type=Path);parser.add_argument('--controls',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args()
    out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir()
    try:
        source=args.controls.resolve();proof=json.loads((source/'COMPLETED.json').read_text());assert proof['status']=='passed-original-preparation-and-native-inference-controls'
        assert all(pin(ROOT/n)==v for n,v in proof['source_pins'].items())
        rt=bridge.runtime(args.runtime);app=bridge.native(None,JAVA)
        rt.update(policy=dict(input_bytes=16_777_216,bundle_bytes=536_870_912,entries=20000,process=dict(bridge.DEFAULT_PROCESS)),process_limits=dict(bridge.DEFAULT_PROCESS),scratch_root=out,native_limits=None)
        sources=[Path(__file__),ROOT/'tools/literature/mashr_preparation.py',ROOT/'tools/literature/mashr_original_runtime.py',bridge.SCRIPT,bridge.LOCK]
        bound={p.relative_to(ROOT).as_posix():pin(p) for p in sources};original=source/'pca-ed/result';original_before=census(original);controls=[]
        folder=out/'workspace-junction';folder.mkdir();target=out/'junction-target';target.mkdir();link=folder/'linked'
        env=environment(rt['rhome'],rt['home'],folder/'scratch');env.update(MASH_TEST_LINK=str(link),MASH_TEST_TARGET=str(target))
        command=['C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe','-NoProfile','-NonInteractive','-Command','New-Item -ItemType Junction -Path $env:MASH_TEST_LINK -Target $env:MASH_TEST_TARGET -ErrorAction Stop | Out-Null']
        with (folder/'setup.stdout').open('xb') as stdout,(folder/'setup.stderr').open('xb') as stderr:setup=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=30,check=False)
        assert setup.returncode==0 and link.is_junction();failure=None
        try:run_owned([sys.executable,'-I','-B','-c','import time; time.sleep(30)'],folder,'probe',env,rt['monitor'])
        except ValueError as error:failure=error
        assert failure is not None and 'Linked process workspace member' in str(failure)
        process=json.loads((folder/'probe.process.json').read_text());assert process['error'] is not None
        for identity in process['observed_process_identities']:
            try:p=rt['monitor'].Process(identity['pid']);assert p.create_time()!=identity['creation_time'] or not p.is_running()
            except rt['monitor'].NoSuchProcess:pass
        assert link.resolve()==target and link.is_junction();os.rmdir(link);assert target.is_dir() and not link.exists()
        record=dict(name='workspace-junction',observed_junction_rejected=True,target_untouched=True,only_test_junction_removed=True,process=process);write(folder/'CONTROL.json',record);controls.append(record)
        print('workspace junction: rejected and owned process terminated',flush=True)
        cases=[('pca','numeric replay differs'),('ed-history','numeric replay differs'),('untrusted-code','no untrusted captured code is executed'),('process-history','No successfully monitored'),('mode','covariance mode'),('warnings','parent warnings'),('producer-runtime','producer profile'),('posterior','Parent verification failed')]
        def execute(row):
            name,detail=row;folder=out/name;folder.mkdir();bundle=folder/'bundle';shutil.copytree(original,bundle);sentinel=folder/'UNTRUSTED-CODE-EXECUTED'
            if name=='pca':change_numeric(bundle/'preparation/original/covariances/pca-1.tsv')
            if name=='ed-history':change_numeric(bundle/'preparation/original/covariances/ed-logged-likelihoods.tsv')
            if name=='untrusted-code':
                for relative in ['source-prepare.R','preparation/trusted-prepare.R']:
                    (bundle/relative).write_text('write("UNTRUSTED",file='+json.dumps(sentinel.as_posix())+')\n',encoding='utf-8',newline='\n')
            if name=='process-history':
                path=bundle/'native-child.process.json';value=json.loads(path.read_text());value['exit_code']=9;replace_json(path,value)
            if name=='mode':
                path=bundle/'session.json';value=json.loads(path.read_text());value['covariance_mode']='canonical';replace_json(path,value)
            if name=='warnings':(bundle/'WARNINGS.md').write_text('COMPLETED\n',encoding='utf-8')
            if name=='producer-runtime':
                path=bundle/'producer-runtime-setup.json';value=json.loads(path.read_text());key=next(iter(value['runtime_files']['monitor']));value['runtime_files']['monitor'][key]['sha256']='0'*64;replace_json(path,value)
                path=bundle/'session.json';value=json.loads(path.read_text());value['runtime_setup']=pin(bundle/'producer-runtime-setup.json');replace_json(path,value)
            if name=='posterior':change_numeric(bundle/'native-analysis/execution/posterior/mean.tsv');reseal(bundle/'native-analysis')
            reseal(bundle);before=census(bundle)
            command=[sys.executable,'-I','-B',ROOT/'tools/literature/mashr_preparation.py','--runtime',args.runtime.resolve(),'--java',JAVA,'--verify',bundle,'--scratch-root',out]
            try:process=run_owned(command,folder,'controller',environment(rt['rhome'],rt['home'],folder/'scratch'),rt['monitor'])
            except ProcessFailure as failed:process=failed.result
            error=(folder/'controller.stderr').read_text(encoding='utf-8')
            assert process['exit_code']==2 and 'FAIL2' in error and 'Required action' in error and detail in error,(name,error)
            assert not sentinel.exists() and before==census(bundle) and not (bundle/'FAILURE.json').exists()
            record=dict(name=name,exit_code=2,source_unchanged=True,all_outer_hashes_recomputed=True,child_hashes_recomputed=name=='posterior',untrusted_code_executed=False,process=process);write(folder/'CONTROL.json',record)
            print(name+': resealed alteration rejected; source unchanged',flush=True);return record
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:controls.extend(pool.map(execute,cases))
        folder=out/'target-separation';folder.mkdir();inputs=folder/'input';shutil.copytree(source/'pca-ed/input',inputs)
        flags=(inputs/'populations.tsv').read_text().splitlines()[-1].split('\t');assert flags[1:]==['0','0','1']
        for name in ['effects.tsv','standard-errors.tsv']:
            path=inputs/name;rows=path.read_text().splitlines();fields=rows[-1].split('\t');fields[1:]=[str(float(x)*(3 if name=='effects.tsv' else 2)) for x in fields[1:]];rows[-1]='\t'.join(fields);path.write_text('\n'.join(rows)+'\n',encoding='utf-8',newline='\n')
        result=folder/'result';command=[sys.executable,'-I','-B',ROOT/'tools/literature/mashr_preparation.py','--runtime',args.runtime.resolve(),'--java',JAVA,'--input',inputs,'--out',result,'--scratch-root',out]
        process=run_owned(command,folder,'controller',environment(rt['rhome'],rt['home'],folder/'scratch'),rt['monitor'])
        compared={}
        for name in ['preparation/original/covariances','preparation/original/prepared/basis','native-analysis/execution/fit','native-analysis/execution/model']:
            actual,expected=census(result/name),census(original/name)
            if name=='preparation/original/covariances':
                # The original ED log's third line is its run timestamp. Check
                # its exact date format, then compare every other byte. All
                # matrices/RDS/names/numeric histories remain byte-exact.
                assert ed_log_without_run_timestamp(result/name/'ed_c.log')==ed_log_without_run_timestamp(original/name/'ed_c.log')
                actual.pop('ed_c.log');expected.pop('ed_c.log')
            assert actual==expected,name;compared[name]=len(actual)
        assert pin(result/'native-analysis/execution/posterior/mean.tsv')!=pin(original/'native-analysis/execution/posterior/mean.tsv')
        record=dict(name='target-separation',process=process,all_covariances_fit_histories_and_model_unchanged=compared,target_posterior_changed=True,original_ED_log_run_timestamp_only_normalized=True);write(folder/'CONTROL.json',record);controls.append(record);print('target-only changes: covariance, complete fit and model unchanged; only ED log timestamp normalized',flush=True)
        folder=out/'linux';folder.mkdir();copied=folder/'copied-bundle';shutil.copytree(original,copied);before=census(copied)
        parent=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','python3','-I','-B',linux(ROOT/'tools/literature/mashr_preparation.py'),'--runtime',linux(args.runtime.resolve()),'--verify',linux(copied)]
        child=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','java','-Xmx128m','-XX:ActiveProcessorCount=2','-jar',linux(app['jar']),'mashr-tools','--no-config','--verify',linux(copied/'native-analysis')]
        for name,command,expected in [('parent',parent,3),('child',child,0)]:
            with (folder/(name+'.stdout')).open('xb') as stdout,(folder/(name+'.stderr')).open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=180,check=False)
            assert process.returncode==expected,(name,process.returncode)
            if expected:assert 'SKIP3' in (folder/(name+'.stderr')).read_text(encoding='utf-8') and 'Required action' in (folder/(name+'.stderr')).read_text(encoding='utf-8')
            controls.append(dict(name='linux-'+name,expected_exit_code=expected,argv=command,source_unchanged=before==census(copied)))
        assert before==census(copied) and original_before==census(original);bridge.checked_state(rt,app)
        assert all(pin(ROOT/n)==v for n,v in bound.items()) and all(pin(ROOT/n)==v for n,v in proof['source_pins'].items())
        write(out/'COMPLETED.json',dict(schema_version=1,status='passed-original-preparation-evidence-and-target-separation-controls',controls=controls,source_pins=bound,original_source_unchanged=True,files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}));return 0
    except Exception as failure:
        write(out/'FAILURE.json',dict(status='FAIL2_PREPARATION_EVIDENCE_CONTROLS',error=str(failure)));raise


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_PREPARATION_EVIDENCE_CONTROLS: '+str(failure),file=sys.stderr);sys.exit(2)
