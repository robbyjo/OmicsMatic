# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run every original scan/enrichment control through the guarded user workflow and portable capture checks."""
import argparse
import csv
import json
from pathlib import Path
import subprocess
from prepare_signac_scan_request import prepare
from validate_signac_motif_workflow import compare
from validate_mashr_native import JAVA21,linux,pin
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
CLI='org.jlinalg.cli.SignacScanRequestCli'

def read(path):return json.loads(path.read_bytes())
def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
def compare_original(target,expected,legacy):
    compare(target/'enrichment.tsv',dict(rows=expected['enrichment']),legacy)
    for name,key in [('rng-before.tsv','seed_before'),('rng-after.tsv','seed_after')]:
        rows=list(csv.DictReader((target/'worker'/name).read_text(encoding='utf8').splitlines(),delimiter='\t'));assert [int(r['index']) for r in rows]==list(range(1,627)) and [int(r['value']) for r in rows]==expected[key]

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--build',default='scan-workflow-build-2');args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists() and '/' not in args.build and '\\' not in args.build;out.mkdir();save(out/'INCOMPLETE.json',dict(status='full-original-scan-enrichment-request-matrix-in-progress'))
    try:
        build=read(BASE/args.build/'BUILD.json');assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};data=read(BASE/'scan-original-7/cases.json');original=read(BASE/'scan-original-7/original.json');names=['tools/literature/validate_signac_scan_workflow.py','tools/literature/prepare_signac_scan_request.py','tools/literature/validate_signac_motif_workflow.py'];names += [(BASE/'scan-original-7'/name).relative_to(ROOT).as_posix() for name in ['cases.json','original.json']];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for name in sources:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        def command(name,argv,code=0):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==code,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-2200:])
        inputs=out/'Unicode α inputs';inputs.mkdir();analyses=out/'Unicode β analyses';analyses.mkdir();runs=[];failures=[]
        for index,record in enumerate(original['results']):
            case=next(c for c in data['cases'] if c['id']==record['id']);name=f"{index:03d}-{record['profile']}-{record['id']}";target=analyses/name;request=prepare(data,case,record['profile'],inputs/name,target);path=out/(f'request-{index:03d}.json');save(path,request);before={p.name:pin(p) for p in (inputs/name).iterdir()};expected=record['result']['value'];failed='error' in expected;command(name,[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',path],2 if failed else 0)
            if failed:
                assert not (target/'enrichment.tsv').exists() and not (target/'motif-presence.mtx').exists()
                if target.exists():assert read(target/'analysis-status.json')['status']=='failed' and 'FAIL2' in (target/'WARNINGS.md').read_text(encoding='utf8')
                failures.append(dict(case=record['id'],profile=record['profile'],original_error=expected['error'],analysis_directory_created=target.exists(),no_result_admitted=True));print('retained original scan failure '+name,flush=True)
            else:
                assert read(target/'analysis-status.json')['status']=='completed-with-warnings';compare_original(target,expected,record['profile']=='1.6.0-source')
                for platform,java,convert in platforms:
                    path=out/(f'verify-{index:03d}-'+platform+'.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command(name+'-verify-'+platform,[*java,'-Xmx768m','-cp',convert(jar),CLI,'--request',convert(path)])
                runs.append(dict(case=record['id'],profile=record['profile'],analysis_path=target.relative_to(ROOT).as_posix(),actual_original_R_fit=True,original_enrichment_and_RNG_states_match=True,captured_verifications=3));print('passed original scan fit '+name,flush=True)
            assert before=={p.name:pin(p) for p in (inputs/name).iterdir()}
        assert len(runs)==75 and len(failures)==21 and all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());result=dict(status='passed-full-original-scan-enrichment-user-workflow-matrix',source_pins=sources,artifact=build['artifact'],actual_original_fits=runs,retained_original_failure_gates=failures,captured_verifications=225,source_inputs_unchanged=True,historical_whole_packages_executed=False,motifmatchr_profile='1.34.0-package',independent_MOODS_KDE_RNG_replay=False,full_Signac_workflow_available=False,catalog_implementation_credit=False);save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],actual_original_fits=75,retained_failure_gates=21,captured_verifications=225)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_FULL_ORIGINAL_SCAN_USER_WORKFLOW_MATRIX',error=str(error)));raise

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full original scan user-workflow checks')
    main()
