# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture explicit retained-sample and hg38 original controls with complete lossless result exports."""
import argparse,json,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/dndscv-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();ref=base/'reference-setup-3';libs=[ref/'R-lib',*map(Path,json.loads((ref/'STARTED.json').read_bytes())['libraries'])];before=inventory(libs);save(out/'LIBRARIES-BEFORE.json',before);script=ROOT/'tools/literature/collect_dndscv_edge_controls.R';exporter=ROOT/'tools/literature/export_dndscv_saved_reference.R';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),script,exporter]}
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(out),str(base/'source-intake-1/source'),str(base/'original-controls-1'),*map(str,libs)];save(out/'STARTED.json',dict(command=command,source_pins=pins,libraries=list(map(str,libs))));state=dict(status='FAIL2_DNDSCV_EDGE_REFERENCE_INCOMPLETE',source_pins=pins,analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',TMPDIR=str(out),R_USER_CACHE_DIR=str(out))
        with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=900)
        save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0;controls=json.loads((out/'controls.json').read_bytes());assert controls['source_bodies_and_formals_checked_before_and_after'] and len(controls['cases'])==2
        for case in controls['cases']:
            if not case['returned']:continue
            home=out/case['id'];cmd=[command[0],'--vanilla',str(exporter),str(home),str(home/'original-return.rds'),*map(str,libs)];r=subprocess.run(cmd,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=120);(home/'export.stdout').write_bytes(r.stdout);(home/'export.stderr').write_bytes(r.stderr);save(home/'export-process.json',dict(command=cmd,exit_code=r.returncode));assert r.returncode==0
        state.update(status='captured-original-dndscv-edge-controls-not-adapter-validation',cases=2,original_returns=sum(c['returned'] for c in controls['cases']),controls=pin(out/'controls.json'))
    except Exception as error:state['detail']=str(error)
    finally:
        after=inventory(libs);save(out/'LIBRARIES-AFTER.json',after);state['preexisting_libraries_unchanged']=before==after;assert before==after and all(pin(ROOT/n)==v for n,v in pins.items());save(out/'COMPLETED.json',state)
    print(json.dumps(state));return 0 if state['status']=='captured-original-dndscv-edge-controls-not-adapter-validation' else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/reference checks')
    raise SystemExit(main())
