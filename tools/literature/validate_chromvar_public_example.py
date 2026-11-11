# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the documented root-CLI background/native scoring pair and portable whole-capture checks."""
import json,subprocess
from pathlib import Path
from validate_mashr_native import JAVA21,linux,pin
from prepare_chromvar_background_request import ROOT,BASE,save
from probe_chromvar_background import comparison
from validate_chromvar_background_examples import score_comparison
def read(p):return json.loads(p.read_bytes())
def inventory(root):return {p.relative_to(root).as_posix():pin(p) for p in root.rglob('*') if p.is_file()}
def main():
    out=BASE/'chromvar-public-example-1';assert not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='documented-root-chromvar-example-in-progress'))
    try:
        build=read(BASE/'chromvar-public-workflow-1/BUILD.json');jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());examples=ROOT/'examples/chromvar-tools';names=['tools/literature/validate_chromvar_public_example.py','tools/literature/prepare_chromvar_public_example.py','tools/literature/probe_chromvar_background.py','tools/literature/validate_chromvar_background_examples.py','docs/literature/CHROMVAR_TOOLS.md']+[p.relative_to(ROOT).as_posix() for p in examples.rglob('*') if p.is_file()];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            p=out/'source-snapshot'/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((ROOT/n).read_bytes())
        def command(label,argv,expected=0):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==expected,(label,r.returncode,r.stderr.decode('utf8',errors='replace')[-1000:])
        reference=read(BASE/'background-reference-chain-1/original-reference/original.json');platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)];runs=[]
        for name,verify,kind in [('background.json','verify-background.json','BACKGROUND'),('deviations.json','verify-deviations.json','DEVIATIONS')]:
            request=read(examples/name);target=(examples/request['out']).resolve();assert not target.exists();command(name,[JAVA21/'java.exe','-Xmx768m','-jar',jar,'chromvar-tools','--request',examples/name]);comparison(target,reference) if kind=='BACKGROUND' else score_comparison(target,reference)
            for label,java,convert in platforms:command(name+'-'+label,[*java,'-Xmx768m','-jar',convert(jar),'chromvar-tools','--request',convert(examples/verify)])
            before=inventory(target);command(name+'-existing-output-refused',[JAVA21/'java.exe','-jar',jar,'chromvar-tools','--request',examples/name],2);assert inventory(target)==before;runs.append(dict(request_path=(examples/name).relative_to(ROOT).as_posix(),analysis_path=target.relative_to(ROOT).as_posix(),kind=kind,original_comparison=True,actual_main_verifications=3,existing_output_preserved=True))
        command('literal-help',[JAVA21/'java.exe','-jar',jar,'chromvar-tools','--help']);command('literal-source-report',[JAVA21/'java.exe','-jar',jar,'chromvar-tools','--source-report']);assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());save(out/'COMPLETED.json',dict(status='passed-documented-root-chromvar-background-and-native-deviation-example',source_pins=sources,artifact=build['artifact'],runs=runs,literal_original_BACKGROUND=1,literal_native_DEVIATIONS=1,literal_VERIFY_requests=2,literal_help_and_source_report=True,actual_main_verifications=6,existing_output_refusals=2,inputs_and_requests_unchanged=True,catalog_implementation_credit=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-documented-root-chromvar-background-and-native-deviation-example',actual_main_verifications=6,existing_output_refusals=2)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_DOCUMENTED_ROOT_CHROMVAR_EXAMPLE',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable documented root example checks')
    main()
