# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute an actual guarded Java-to-R scan request and audit its capture on three Java environments."""
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
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--build',default='scan-workflow-build-1');args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='actual-guarded-original-scan-request-probe-in-progress'))
    try:
        build=read(BASE/args.build/'BUILD.json');assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};names=['tools/literature/probe_signac_scan_request.py','tools/literature/prepare_signac_scan_request.py','tools/literature/validate_signac_motif_workflow.py'];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for name in sources:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        data=read(BASE/'scan-original-7/cases.json');original=read(BASE/'scan-original-7/original.json');case=next(c for c in data['cases'] if c['id']=='matched');target=out/'Unicode β analysis';request=prepare(data,case,'1.17.1-package',out/'Unicode α inputs',target);path=out/'request.json';save(path,request)
        def command(name,argv):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-2000:])
        command('FIT',[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',path]);reference=next(r['result']['value'] for r in original['results'] if r['id']=='matched' and r['profile']=='1.17.1-package');compare(target/'enrichment.tsv',dict(rows=reference['enrichment']),False)
        for file,key in [('rng-before.tsv','seed_before'),('rng-after.tsv','seed_after')]:
            rows=list(csv.DictReader((target/'worker'/file).read_text(encoding='utf8').splitlines(),delimiter='\t'));assert [int(r['value']) for r in rows]==reference[key]
        for platform,java,convert in [('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]:
            path=out/(platform+'-verify.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command(platform,[*java,'-Xmx768m','-cp',convert(jar),CLI,'--request',convert(path)])
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());save(out/'COMPLETED.json',dict(status='passed-actual-guarded-original-scan-request-probe',source_pins=sources,artifact=build['artifact'],actual_R_fit=True,original_enrichment_and_RNG_states_match=True,captured_verifications=3,full_Signac_workflow_available=False,catalog_implementation_credit=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-actual-guarded-original-scan-request-probe',actual_R_fit=True,captured_verifications=3)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_GUARDED_SCAN_REQUEST_PROBE',error=str(error)));raise

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable real scan request checks')
    main()
