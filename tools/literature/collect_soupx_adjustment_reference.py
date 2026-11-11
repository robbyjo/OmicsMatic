# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture direct original correction references for varying rho and complete manual/automatic pipelines."""
import argparse,json,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--version',choices=['1.6.2','1.3.0'],required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/soupx-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();reference=base/('reference-1.6.2-1' if a.version=='1.6.2' else 'reference-1.3.0-2');controls=base/('ambient-controls-1.6.2-2' if a.version=='1.6.2' else 'ambient-controls-1.3.0-3');source=base/('source-intake-1/1.6.2' if a.version=='1.6.2' else 'source-recovery-1/1.3.0');receipt=json.loads((source/'SOURCE.json').read_bytes());assert all(pin(source/'source'/n)==v for n,v in receipt['source_members'].items());script=ROOT/'tools/literature/collect_soupx_adjustment_reference.R';names=['fixed-rho-0','manual-supplied-mask','manual-automatic-mask-supplied-clusters','auto-P145-overrides' if a.version=='1.6.2' else 'auto-legacy-declared-apply'];paths=[Path(__file__).resolve(),script,source/'SOURCE.json',controls/'inputs.rds',*[controls/n/'original-return.rds' for n in names]];pins={p.relative_to(ROOT).as_posix():pin(p) for p in paths};libraries=[reference/'R-lib',*map(Path,json.loads((reference/'STARTED.json').read_bytes())['libraries'])];before=inventory(libraries);save(out/'LIBRARIES-BEFORE.json',before)
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(out),str(source/'source'),str(controls),a.version,*map(str,libraries)];save(out/'STARTED.json',dict(command=command,source_pins=pins,libraries=list(map(str,libraries))));env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en',R_LIBS=';'.join(map(str,libraries)),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',TMPDIR=str(out),R_USER_CACHE_DIR=str(out))
    with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=300)
    save(out/'process.json',dict(command=command,exit_code=r.returncode));after=inventory(libraries);save(out/'LIBRARIES-AFTER.json',after);assert before==after and all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(source/'source'/n)==v for n,v in receipt['source_members'].items());assert r.returncode==0,'Keep failed reference attempt and inspect logs';report=json.loads((out/'controls.json').read_bytes());assert len(report['cases'])==6 and report['source_bodies_and_formals_checked_before_and_after'];save(out/'COMPLETED.json',dict(status='captured-direct-original-SoupX-adjustment-references',version=a.version,source_pins=pins,controls=pin(out/'controls.json'),rho_input=pin(out/'rho-by-cell.tsv'),preexisting_libraries_unchanged=True,original_channels_and_source_preserved=True,ambient_workflow_admitted=False,implementation_credit=False,whole_goal_complete=False));print(a.version,'6 direct original adjustment references captured')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original/reference checks')
    main()
