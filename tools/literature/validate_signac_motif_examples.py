# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute literal checked motif requests plus fresh native Java25/Linux runs and original comparisons."""
import argparse
import json
from pathlib import Path
import subprocess
from validate_mashr_native import JAVA21,linux,pin
from validate_signac_motif_workflow import compare
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
EXAMPLES=ROOT/'examples/signac-motifs'
CLI='org.jlinalg.cli.SignacMotifsRequestCli'

def read(path):return json.loads(path.read_bytes())
def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='literal-native-motif-examples-in-progress'))
    try:
        build=read(BASE/'motif-workflow-build-1/BUILD.json');assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};names=['tools/literature/prepare_signac_motif_examples.py','tools/literature/validate_signac_motif_examples.py','tools/literature/validate_signac_motif_workflow.py','docs/literature/SIGNAC_MOTIFS_DEVELOPMENT.md'];names += [p.relative_to(ROOT).as_posix() for p in EXAMPLES.rglob('*') if p.is_file()];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for name in sources:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        def command(name,argv):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-1600:])
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        def verify(name,target):
            for platform,java,convert in platforms:
                path=out/(name+'-'+platform+'.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command(name+'-'+platform,[*java,'-Xmx512m','-cp',convert(jar),CLI,'--request',convert(path)])
        original=read(BASE/'motif-original-2/original.json');runs=[]
        for name,case in [('request.json','explicit-BH'),('legacy-request.json','explicit-BH'),('all-background-request.json','all-background')]:
            path=EXAMPLES/name;r=read(path);target=(EXAMPLES/r['out']).resolve();assert target.is_relative_to((ROOT/'build').resolve()) and not target.exists();command(name+'-ENRICH',[JAVA21/'java.exe','-Xmx512m','-cp',jar,CLI,'--request',path]);reference=next(v['result']['value'] for v in original['results'] if v['id']==case and v['profile']==r['profile']);compare(target/'enrichment.tsv',reference,r['profile']=='1.6.0-source');verify(name,target);runs.append(dict(request_path=path.relative_to(ROOT).as_posix(),analysis_path=target.relative_to(ROOT).as_posix(),actual_native_run=True,original_statistical_comparison=True,captured_verifications=3))
        command('literal-VERIFY',[JAVA21/'java.exe','-Xmx512m','-cp',jar,CLI,'--request',EXAMPLES/'verify-request.json'])
        for platform,java,convert in platforms[1:]:
            r=read(EXAMPLES/'request.json');target=out/('fresh-'+platform);r['out']=convert(target)
            for field in ['motif_matrix','peak_features','motif_labels','query','background']:r[field]=convert((EXAMPLES/r[field]).resolve())
            path=out/(platform+'-fresh-request.json');save(path,r);command(platform+'-fresh-ENRICH',[*java,'-Xmx512m','-cp',convert(jar),CLI,'--request',convert(path)]);reference=next(v['result']['value'] for v in original['results'] if v['id']=='explicit-BH' and v['profile']=='1.17.1-package');compare(target/'enrichment.tsv',reference,False);verify(platform,target);runs.append(dict(analysis_path=target.relative_to(ROOT).as_posix(),actual_native_run=True,platform=platform,original_statistical_comparison=True,captured_verifications=3))
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());result=dict(status='passed-literal-native-motif-examples-and-additional-platform-runs',source_pins=sources,artifact=build['artifact'],runs=runs,literal_ENRICH_requests=3,additional_fresh_platform_runs=2,original_statistical_comparisons=5,captured_verifications=16,literal_VERIFY_request_executed=True,native_enrichment_requires_no_R=True,inputs_and_requests_unchanged=True,full_Signac_workflow_available=False,catalog_implementation_credit=False);save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],native_runs=5,captured_verifications=16)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_LITERAL_NATIVE_MOTIF_EXAMPLES',error=str(error)));raise

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable literal motif example checks')
    main()
