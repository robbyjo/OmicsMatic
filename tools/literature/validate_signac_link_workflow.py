# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run guarded LinkPeaks requests against every original profile control and verify portable captured audits."""
import argparse
import csv
import json
import math
from pathlib import Path
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,linux,pin
from prepare_signac_link_probe import prepare
BASE=ROOT/'build/literature-review/signac-runtime'
CLI='org.jlinalg.cli.SignacLinksRequestCli'
def read(path):return json.loads(path.read_bytes())
def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
def array(value):return [] if value is None else value if isinstance(value,list) else [value]
def links(path):return list(csv.DictReader(path.read_text(encoding='utf8').splitlines(),delimiter='\t'))
def compare(target,expected):
    actual=links(target/'result-links.tsv');want=array(expected['links']);assert len(actual)==len(want)
    for a,b in zip(actual,want,strict=True):
        assert (a['chromosome'],int(a['start']),int(a['end']),a['gene'],a['peak'])==(b['seqnames'],b['start'],b['end'],b['gene'],b['peak'])
        for key,source in [('coefficient','score'),('zscore','zscore'),('pvalue','pvalue')]:
            x=float(a[key]);y=float(b[source]);assert x==y or math.isclose(x,y,rel_tol=2e-11 if key!='pvalue' else 1e-9,abs_tol=2e-12 if key!='pvalue' else 1e-300),(key,x,y)
    for file,key in [('rng-before.tsv','seed_before'),('rng-after.tsv','seed_after')]:
        rows=list(csv.DictReader((target/'worker'/file).read_text().splitlines(),delimiter='\t'));assert [int(r['index']) for r in rows]==list(range(1,627)) and [int(r['value']) for r in rows]==expected[key]
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--build',default='links-workflow-build-3');args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists() and '/' not in args.build and '\\' not in args.build;out.mkdir();save(out/'INCOMPLETE.json',dict(status='full-original-LinkPeaks-request-control-matrix-in-progress'))
    try:
        build=read(BASE/args.build/'BUILD.json');assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};data=read(BASE/'links-original-4/cases.json');original=read(BASE/'links-original-4/original.json');names=['tools/literature/validate_signac_link_workflow.py','tools/literature/prepare_signac_link_probe.py'];sources={n:pin(ROOT/n) for n in names}
        for name in ['cases.json','original.json']:sources[(BASE/'links-original-4'/name).relative_to(ROOT).as_posix()]=pin(BASE/'links-original-4'/name)
        save(out/'SOURCE-PINS.json',sources)
        for name in sources:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        def command(name,argv,code=0):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==code,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-1200:])
        inputs=out/'Unicode α inputs';inputs.mkdir();analyses=out/'Unicode β analyses';analyses.mkdir();runs=[];failures=[]
        for index,reference in enumerate(original['results']):
            case=next(c for c in data['cases'] if c['id']==reference['id']);name=f"{index:02d}-{reference['profile']}-{reference['id']}";target=analyses/name;request=prepare(data,case,reference['profile'],inputs/name,target);path=out/(f'request-{index:02d}.json');save(path,request);before={p.name:pin(p) for p in (inputs/name).iterdir()};expected=reference['result']['value'];code=2 if 'error' in expected else 0;command(name,[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',path],code)
            if code:
                assert not (target/'result-links.tsv').exists()
                if target.exists():assert read(target/'analysis-status.json')['status']=='failed' and 'FAIL2' in (target/'WARNINGS.md').read_text(encoding='utf8')
                failures.append(dict(case=case['id'],profile=reference['profile'],expected_original_error=expected['error'],analysis_directory_created=target.exists(),no_result_admitted=True))
            else:
                assert read(target/'analysis-status.json')['status']=='completed-with-warnings';compare(target,expected)
                for platform,java,convert in platforms:
                    verify=out/(f'verify-{index:02d}-'+platform+'.json');save(verify,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command(name+'-verify-'+platform,[*java,'-Xmx512m','-cp',convert(jar),CLI,'--request',convert(verify)])
                runs.append(dict(case=case['id'],profile=reference['profile'],analysis_path=target.relative_to(ROOT).as_posix(),request_path=path.relative_to(ROOT).as_posix(),actual_original_fit=True,original_link_tables_match=True,original_RNG_states_match=True,captured_verifications=3))
            assert before=={p.name:pin(p) for p in (inputs/name).iterdir()};print(('passed original fit ' if code==0 else 'retained expected failure ')+name,flush=True)
        assert len(runs)==44 and len(failures)==16 and all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());result=dict(status='passed-full-original-LinkPeaks-request-control-matrix',source_pins=sources,artifact=build['artifact'],actual_original_fits=runs,expected_source_failure_gates=failures,captured_verifications=132,inputs_unchanged=True,full_current_package_and_historical_source_profiles_executed=True,historical_packages_executed=False,fresh_R_execution_platform='Windows-R4.6.1',captured_native_audit_platforms=[v[0] for v in platforms],complete_runtime_DLL_host_closure_certified=False,full_Signac_workflow_available=False,catalog_implementation_credit=False);save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],actual_original_fits=44,expected_source_failure_gates=16,captured_verifications=132)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_ORIGINAL_LINKPEAKS_REQUEST_CONTROL_MATRIX',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full original LinkPeaks workflow comparison')
    main()
