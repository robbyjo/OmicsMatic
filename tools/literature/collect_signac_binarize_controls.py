# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Collect original sparse BinarizeCounts controls without modifying frozen normalization fixtures."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import pin
BASE=ROOT/'build/literature-review/signac-runtime'
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='original-binarization-controls-in-progress'))
    try:
        reference=BASE/'normalization-original-2';record=json.loads((reference/'COMPLETED.json').read_bytes());assert pin(reference/'cases.json')==record['cases'];(out/'cases.json').write_bytes((reference/'cases.json').read_bytes())
        names=['tools/literature/collect_signac_binarize_controls.py','tools/literature/collect_signac_binarize_controls.R'];pins={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',pins)
        for name in names:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        env=dict(os.environ)
        for key in list(env):
            if key.startswith('LC_') or key in ['LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS']:env.pop(key,None)
        env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
        cmd=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(ROOT/names[1]),str(out/'cases.json'),str(out/'original.json')]
        r=subprocess.run(cmd,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/'original.stdout').write_bytes(r.stdout);(out/'original.stderr').write_bytes(r.stderr);save(out/'original.process.json',dict(command=cmd,exit_code=r.returncode));assert r.returncode==0
        results=json.loads((out/'original.json').read_bytes());assert len(results['results'])==36 and all(pin(ROOT/n)==v for n,v in pins.items())
        save(out/'COMPLETED.json',dict(status=results['status'],source_pins=pins,cases=pin(out/'cases.json'),original=pin(out/'original.json'),cases_count=36));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=results['status'],cases=36)))
    except BaseException as e:save(out/'FAILURE.json',dict(status='FAILED_ORIGINAL_BINARIZE_CONTROLS',error=str(e)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Do not disable original fixture checks')
    main()
