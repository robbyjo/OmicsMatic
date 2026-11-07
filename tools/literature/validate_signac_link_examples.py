# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the literal checked LinkPeaks requests, compare originals and verify on three JVMs."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,linux,pin
from validate_signac_link_workflow import compare
BASE=ROOT/'build/literature-review/signac-runtime'
EXAMPLES=ROOT/'examples/signac-links'
CLI='org.jlinalg.cli.SignacLinksRequestCli'

def read(path):return json.loads(path.read_bytes())
def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='literal-LinkPeaks-examples-in-progress'))
    try:
        build=read(BASE/'links-workflow-build-3/BUILD.json');assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        names=['tools/literature/prepare_signac_link_examples.py','tools/literature/validate_signac_link_examples.py','tools/literature/validate_signac_link_workflow.py','docs/literature/SIGNAC_LINKS_DEVELOPMENT.md'];names += [p.relative_to(ROOT).as_posix() for p in EXAMPLES.rglob('*') if p.is_file()];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for name in sources:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        def command(name,argv):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-1600:])
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        original=read(BASE/'links-original-4/original.json');runs=[]
        for name,case in [('request.json','pearson-6-1'),('historical-request.json','spearman-10-0.05'),('empty-request.json','zero-p-cutoff')]:
            path=EXAMPLES/name;request=read(path);target=(EXAMPLES/request['out']).resolve();assert target.is_relative_to((ROOT/'build').resolve()) and not target.exists()
            command(name+'-FIT',[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',path]);reference=next(v['result']['value'] for v in original['results'] if v['profile']==request['profile'] and v['id']==case);compare(target,reference)
            if name=='empty-request.json':assert len((target/'result-links.tsv').read_text(encoding='utf8').splitlines())==1
            for platform,java,convert in platforms:
                verify=out/(name+'-'+platform+'.json');save(verify,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command(name+'-'+platform,[*java,'-Xmx512m','-cp',convert(jar),CLI,'--request',convert(verify)])
            runs.append(dict(request_path=path.relative_to(ROOT).as_posix(),analysis_path=target.relative_to(ROOT).as_posix(),profile=request['profile'],original_case=case,actual_original_fit=True,original_link_numbers_and_RNG_states_match=True,captured_verifications=3))
        command('literal-VERIFY-request',[JAVA21/'java.exe','-Xmx512m','-cp',jar,CLI,'--request',EXAMPLES/'verify-request.json'])
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());result=dict(status='passed-literal-LinkPeaks-examples',source_pins=sources,artifact=build['artifact'],runs=runs,actual_original_fits=3,fresh_original_comparisons=3,captured_verifications=10,literal_VERIFY_request_executed=True,inputs_and_requests_unchanged=True,full_Signac_workflow_available=False,catalog_implementation_credit=False);save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],actual_original_fits=3,captured_verifications=10)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_LITERAL_LINKPEAKS_EXAMPLES',error=str(error)));raise

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable literal example checks')
    main()
