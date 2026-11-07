# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run every original full-core protocol through native typed requests and portable captured verification."""
import argparse,json,subprocess
from pathlib import Path
from prepare_chromvar_request import prepare,ROOT,BASE,save
from probe_chromvar_request import compare,scalar
from validate_mashr_native import JAVA21,linux,pin
CLI='org.jlinalg.cli.ChromVarRequestCli'
def read(p):return json.loads(p.read_bytes())
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--build',default='chromvar-workflow-build-1');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='native-chromVAR-user-workflow-matrix-in-progress'))
    try:
        build=read(BASE/a.build/'BUILD.json');jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());names=['tools/literature/validate_chromvar_workflow.py','tools/literature/prepare_chromvar_request.py','tools/literature/probe_chromvar_request.py'];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        inputs=out/'Unicode α inputs';inputs.mkdir();analyses=out/'Unicode β analyses';analyses.mkdir();runs=[];failures=[]
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        def command(name,argv,code):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==code,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-1600:])
        index=0
        for family,folder in [('basic','chromvar-deviations-original-3'),('extended','chromvar-extended-original-1')]:
            data=read(BASE/folder/'cases.json');original=read(BASE/folder/'original.json')
            for record in original['results']:
                id=scalar(record['id']);profile=scalar(record['profile']);case=next(v for v in data['cases'] if v['id']==id);name=f'{index:03d}-{family}-{profile}-{id}';target=analyses/name;path=out/(f'request-{index:03d}.json');save(path,prepare(data['datasets'][case['dataset']],case,profile,inputs/name,target));before={p.name:pin(p) for p in (inputs/name).iterdir()};error=isinstance(record['core']['value'],dict) and 'error' in record['core']['value'];command(name,[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',path],2 if error else 0)
                if error:
                    assert not (target/'deviations.tsv').exists() and read(target/'analysis-status.json')['status']=='failed' and (target/'WARNINGS.md').read_text(encoding='utf8').startswith('# FAIL2');failures.append(dict(family=family,case=id,profile=profile,original_error=record['core']['value']['error'],analysis_path=target.relative_to(ROOT).as_posix(),no_result_admitted=True))
                else:
                    compare(target,record)
                    for platform,java,convert in platforms:
                        q=out/(f'verify-{index:03d}-'+platform+'.json');save(q,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command(name+'-'+platform,[*java,'-Xmx768m','-cp',convert(jar),CLI,'--request',convert(q)],0)
                    runs.append(dict(family=family,case=id,profile=profile,analysis_path=target.relative_to(ROOT).as_posix(),original_statistics_match=True,captured_verifications=3))
                assert before=={p.name:pin(p) for p in (inputs/name).iterdir()};print(('retained source failure ' if error else 'matched native request ')+name,flush=True);index+=1
        assert index==102 and len(runs)+len(failures)==102 and all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());save(out/'COMPLETED.json',dict(status='passed-native-chromVAR-full-source-control-request-matrix',source_pins=sources,artifact=build['artifact'],original_statistical_comparisons=runs,retained_source_failures=failures,captured_verifications=3*len(runs),inputs_and_requests_unchanged=True,native_analysis_requires_no_R=True,background_sampling_performed=False,full_Signac_workflow_available=False,catalog_implementation_credit=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-native-chromVAR-full-source-control-request-matrix',matches=len(runs),retained_failures=len(failures),captured_verifications=3*len(runs))),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_NATIVE_CHROMVAR_USER_WORKFLOW_MATRIX',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable actual native workflow comparisons')
    main()
