# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the exact checked-in synthetic fragment requests and optional pinned LSI request."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,linux,pin
BASE=ROOT/'build/literature-review/signac-runtime'
def read(path):return json.loads(path.read_bytes())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='exact-checked-fragment-examples-in-progress'))
    try:
        build=read(BASE/'fragment-workflow-build-3/BUILD.json');assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        examples=ROOT/'examples/signac-fragments';sources={p.relative_to(ROOT).as_posix():pin(p) for p in examples.iterdir() if p.is_file()};sources['tools/literature/validate_signac_fragment_examples.py']=pin(Path(__file__));save(out/'SOURCE-PINS.json',sources)
        def command(name,argv):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode)
        runs=[]
        for name in ['feature','count','qc','binary','verify','lsi']:
            path=examples/(name+'-request.json');request=read(path);cli='org.jlinalg.cli.SignacLsiRequestCli' if name=='lsi' else 'org.jlinalg.cli.SignacFragmentsRequestCli'
            command(name,[JAVA21/'java.exe','-Xmx512m','-cp',jar,cli,'--request',path])
            if name=='verify':continue
            target=(examples/(request['options']['out'] if name=='lsi' else request['out'])).resolve();assert target.is_relative_to(ROOT/'build')
            for platform,java,convert in [('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]:
                verify=out/(name+'-'+platform+'.json');save(verify,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command(name+'-'+platform,[*java,'-cp',convert(jar),cli,'--request',convert(verify)])
            runs.append(dict(name=name,analysis_path=target.relative_to(ROOT).as_posix(),actual_run=True,captured_verifications=3))
        # The feature example uses the exact random original quantification input and regions.
        reference=BASE/'fragment-workflow-5/matrix-random-discover-False-2000';actual=ROOT/runs[0]['analysis_path']
        for name in ['matrix.mtx','features.tsv','cells.tsv']:assert (actual/name).read_bytes()==(reference/name).read_bytes()
        assert all(pin(ROOT/n)==v for n,v in sources.items());save(out/'COMPLETED.json',dict(status='passed-exact-checked-fragment-and-LSI-examples',source_pins=sources,artifact=build['artifact'],runs=runs,native_runs=4,actual_LSI_fits=1,original_matrix_identical=True,full_Signac_workflow_available=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-exact-checked-fragment-and-LSI-examples',runs=len(runs))),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_CHECKED_FRAGMENT_EXAMPLE',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable checked example validation')
    main()
