# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the bounded synthetic original-package readiness probe; preserve every existing library."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--setup',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    args=p.parse_args();setup=args.setup.resolve(strict=True);out=args.out.resolve();base=ROOT/'build/literature-review/monocle-runtime'
    assert setup.is_relative_to(base) and out.is_relative_to(base) and not out.exists();out.mkdir(parents=True)
    proof=json.loads((setup/'RESULT.json').read_bytes());assert proof['status']=='INSTALLED_ORIGINAL_MONOCLE3_REFERENCE_ONLY' and proof['preexisting_libraries_unchanged']
    assert proof['exact_Monocle3']=='1.4.27'
    started=json.loads((setup/'STARTED.json').read_bytes());libraries=[setup/'R-lib',*map(Path,started['libraries'])]
    before=inventory(libraries);save(out/'LIBRARIES-BEFORE.json',before)
    worker=ROOT/'tools/literature/probe_monocle_current_v2.R';wanted=pin(worker);(out/'source-used.R').write_bytes(worker.read_bytes())
    command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(worker),str(out),*map(str,libraries)]
    env=dict(os.environ,LC_ALL='C',LANG='C',LANGUAGE='en',OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',MKL_NUM_THREADS='1')
    with (out/'stdout.txt').open('xb') as a,(out/'stderr.txt').open('xb') as b:
        run=subprocess.run(command,cwd=ROOT,env=env,stdout=a,stderr=b,stdin=subprocess.DEVNULL,timeout=300)
    save(out/'process.json',dict(command=command,exit_code=run.returncode,source_pin=wanted))
    assert pin(worker)==wanted
    after=inventory(libraries);save(out/'LIBRARIES-AFTER.json',after);assert before==after
    result=json.loads((out/'RESULT.json').read_bytes())
    save(out/'PROVENANCE.json',dict(source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),worker,setup/'RESULT.json',setup/'STARTED.json']},
        libraries_unchanged=True,library_files_checked=sum(map(len,before.values())),original_profile='1.4.27',Java_workflow_admitted=False))
    print(json.dumps({k:v for k,v in result.items() if k!='messages'}),flush=True)
    if (out/'summary.json').exists():print((out/'summary.json').read_text(encoding='utf8'),flush=True)
    return run.returncode


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable runtime/source preservation checks')
    raise SystemExit(main())
