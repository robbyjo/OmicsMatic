# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the exact checked-in FIT and VERIFY JSON examples, preserving their output."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,pin
BASE=ROOT/'build/literature-review/signac-runtime'
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='exact-checked-in-example-validation-in-progress'))
    try:
        build=json.loads((BASE/'lsi-request-build-3/BUILD.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        destination=ROOT/'build/signac-lsi-request-demo-NEW';assert not destination.exists(),'Do not overwrite an existing documented example output'
        files=[ROOT/'examples/signac-lsi/request.json',ROOT/'examples/signac-lsi/verify-request.json',Path(__file__)];sources={p.relative_to(ROOT).as_posix():pin(p) for p in files}
        for mode,file in [('fit',files[0]),('verify',files[1])]:
            cmd=[str(JAVA21/'java.exe'),'-Xmx512m','-cp',str(jar),'org.jlinalg.cli.SignacLsiRequestCli','--request',str(file)]
            r=subprocess.run(cmd,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(mode+'.stdout')).write_bytes(r.stdout);(out/(mode+'.stderr')).write_bytes(r.stderr);save(out/(mode+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==0
        reference=BASE/'lsi-workflow-5/1.17.1-package-rspectra-scaled'
        for name in ['result-embeddings.tsv','result-loadings.tsv','result-singular-values.tsv']:assert (destination/name).read_bytes()==(reference/name).read_bytes()
        assert all(pin(ROOT/n)==v for n,v in sources.items())
        save(out/'COMPLETED.json',dict(status='passed-exact-checked-in-FIT-and-VERIFY-requests',source_pins=sources,artifact=build['artifact'],analysis_path=destination.relative_to(ROOT).as_posix(),fresh_actual_fit=True,independent_reference_tables_identical=True))
        (out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-exact-checked-in-FIT-and-VERIFY-requests',analysis_path=destination.relative_to(ROOT).as_posix())))
    except BaseException as e:save(out/'FAILURE.json',dict(status='FAILED_EXACT_CHECKED_IN_REQUEST_EXAMPLE',error=str(e)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Do not disable exact example checks')
    main()
