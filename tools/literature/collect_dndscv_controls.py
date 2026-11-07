# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture complete original dndscv outputs with unchanged existing libraries; no tool credit."""
import argparse,json,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();base=ROOT/'build/literature-review/dndscv-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();reference=base/'reference-setup-3';setup=json.loads((reference/'COMPLETED.json').read_bytes());assert setup['status']=='installed-source-bound-original-dndscv-reference-only';start=json.loads((reference/'STARTED.json').read_bytes());libs=[reference/'R-lib',*map(Path,start['libraries'])];source=base/'source-intake-1/source';receipt=json.loads((base/'source-intake-1/SOURCE.json').read_bytes());assert all(pin(source/n)==v for n,v in receipt['source_members'].items());script=ROOT/'tools/literature/collect_dndscv_controls.R';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),script]};before=inventory(libs);save(out/'LIBRARIES-BEFORE.json',before)
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    command=[start['command'][0],'--vanilla',str(script),str(out),str(source),*map(str,libs)];save(out/'STARTED.json',dict(command=command,libraries=list(map(str,libs)),source_pins=pins,source_commit=receipt['commit'],reference_profile=setup['profile']));state=dict(status='FAIL2_ORIGINAL_DNDSCV_CONTROLS_INCOMPLETE',analysis_admitted=False,implementation_credit=False,source_pins=pins)
    try:
        env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en',R_LIBS=';'.join(map(str,libs)),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',TMPDIR=str(out),R_USER_CACHE_DIR=str(out))
        with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=900)
        save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Inspect retained stdout/stderr';controls=json.loads((out/'controls.json').read_bytes());assert controls['source_bodies_and_formals_checked_before_and_after'] and len(controls['cases'])==7;state.update(status='captured-original-dndscv-controls-not-adapter-validation',cases=7,original_returns=sum(c['returned'] for c in controls['cases']),controls=pin(out/'controls.json'),coding_controls=pin(out/'coding-controls.json'),original_inputs=pin(out/'inputs.rds'))
    except Exception as error:state['detail']=str(error)
    finally:
        after=inventory(libs);save(out/'LIBRARIES-AFTER.json',after);state['preexisting_libraries_unchanged']=before==after;assert before==after and all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(source/n)==v for n,v in receipt['source_members'].items());save(out/'COMPLETED.json',state)
    print(json.dumps(state));return 0 if state['status']=='captured-original-dndscv-controls-not-adapter-validation' else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/reference checks')
    raise SystemExit(main())
