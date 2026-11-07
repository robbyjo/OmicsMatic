# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture exact original normal-fitting controls without installing or altering shared libraries."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT, pin, save
from setup_chipseeker_reference import inventory


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    base=ROOT/'build/literature-review/fitdistrplus-runtime';out=args.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    intake=base/'source-intake-1';receipt=json.loads((intake/'COMPLETED.json').read_bytes())
    assert all(pin(intake/'source'/n)==v for n,v in receipt['source_members'].items())
    libraries=[ROOT/'build/omics-sctransform-r',Path('C:/Program Files/R/R-4.6.1/library')]
    before=inventory(libraries);save(out/'LIBRARIES-BEFORE.json',before)
    script=ROOT/'tools/literature/probe_fitdistrplus_reference.R'
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),script,intake/'COMPLETED.json']}
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    action='Preserve this attempt and original outputs. If exact permitted dependencies are unavailable, skip dependent source-bound analysis; provision only in a NEW private library where allowed. Do not alter shared libraries or infer the paper method/version. Reference controls alone admit no user analysis.'
    status=dict(status='SKIP3_ORIGINAL_REFERENCE_UNAVAILABLE',analysis_admitted=False,required_action=action,source_pins=pins)
    try:
        command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(out),str(intake/'source'),*map(str,libraries)]
        save(out/'STARTED.json',dict(command=command,source_pins=pins))
        with (out/'stdout.log').open('xb') as stdout,(out/'stderr.log').open('xb') as stderr:
            run=subprocess.run(command,cwd=ROOT,env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',TMPDIR=str(out),R_USER_CACHE_DIR=str(out)),stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
        save(out/'process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,'Inspect retained original stdout/stderr'
        controls=json.loads((out/'controls.json').read_bytes());assert controls['profile']['source_bodies_and_formals_checked_before_and_after']
        status.update(status='captured-original-normal-fit-and-tail-BH-controls',profile=controls['profile'],fitting_cases=len(controls['fits']),original_fit_calls=2*len(controls['fits']),probability_families=len(controls['probabilities']),controls=pin(out/'controls.json'))
    except Exception as error:
        status['detail']=str(error)
    finally:
        after=inventory(libraries);save(out/'LIBRARIES-AFTER.json',after);status.update(preexisting_libraries_unchanged=before==after,preexisting_files=sum(map(len,before.values())),software_installed=False)
        assert all(pin(ROOT/n)==v for n,v in pins.items())
        if before!=after:status.update(status='FAIL2_SHARED_LIBRARY_CHANGED',detail='Inspect before/after inventories')
        save(out/'COMPLETED.json',status);(out/'WARNINGS.md').write_text('# '+status['status']+'\n\n'+status.get('detail','')+'\n\nREQUIRED ACTION: '+action+'\n',encoding='utf8',newline='\n')
    print(json.dumps({k:v for k,v in status.items() if k!='source_pins'}));return 0 if status['status']=='captured-original-normal-fit-and-tail-BH-controls' else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/reference checks')
    raise SystemExit(main())
