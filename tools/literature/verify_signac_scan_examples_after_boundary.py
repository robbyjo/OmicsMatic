# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the literal example captures with the corrected candidate; reuse their unchanged original R fits."""
import argparse,json,subprocess
from pathlib import Path
from prepare_signac_scan_request import ROOT,BASE,save
from validate_mashr_native import JAVA21,linux,pin
def read(p):return json.loads(p.read_bytes())
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='corrected-candidate-checked-example-verification-in-progress'))
    try:
        parent=BASE/'scan-pfm-examples-1';examples=read(parent/'COMPLETED.json');build=read(BASE/'scan-workflow-build-4/BUILD.json');jar=ROOT/build['artifact']['path'];assert examples['captured_verifications']==22 and all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
        assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        names=['tools/literature/verify_signac_scan_examples_after_boundary.py','docs/literature/SIGNAC_SCAN_DEVELOPMENT.md','examples/signac-scan/README.md',*[p.relative_to(ROOT).as_posix() for p in (ROOT/'examples/signac-scan').rglob('*') if p.is_file()]];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)];cli='org.jlinalg.cli.SignacScanRequestCli'
        def command(name,argv):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.stderr.decode('utf8',errors='replace')[-1200:])
        for i,run in enumerate(examples['runs']+examples['native_runs']):
            target=ROOT/run['analysis_path'];method=cli if i<len(examples['runs']) else 'org.jlinalg.cli.SignacMotifsRequestCli'
            for platform,java,convert in platforms:
                name=f'{i:02d}-'+platform;path=out/(name+'.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command(name,[*java,'-Xmx768m','-cp',convert(jar),method,'--request',convert(path)])
        command('literal-VERIFY',[JAVA21/'java.exe','-Xmx768m','-cp',jar,cli,'--request',ROOT/'examples/signac-scan/verify-request.json'])
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());result=dict(examples,status='passed-checked-examples-on-boundary-corrected-candidate',source_pins=sources,artifact=build['artifact'],original_example_checkpoint=pin(parent/'COMPLETED.json'),source_R_fits_reused_without_reexecution=True,captured_verifications=22,only_independent_native_capture_verification_repeated=True);save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],captured_verifications=22,original_R_fits_reexecuted=False)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_CORRECTED_CANDIDATE_EXAMPLES',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable example capture verification')
    main()
