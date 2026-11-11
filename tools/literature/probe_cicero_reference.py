# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run complete original Cicero aggregation/penalty/glasso/reconciliation controls in private libraries."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/monocle-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    setup=base/'cicero-reference-setup-1';record=json.loads((setup/'RESULT.json').read_bytes());assert record['status']=='INSTALLED_ORIGINAL_CICERO_REFERENCE_ONLY' and record['preexisting_libraries_unchanged']
    libraries=[setup/'R-lib',*[Path(p) for p in json.loads((setup/'STARTED.json').read_bytes())['libraries']]];before=inventory(libraries);save(out/'LIBRARIES-BEFORE.json',before)
    script=ROOT/'tools/literature/probe_cicero_reference.R';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),script,setup/'RESULT.json',setup/'health.json']}
    for n in pins:
        target=out/'source-snapshot'/n;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/n).read_bytes())
    command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(out),*map(str,libraries)];save(out/'STARTED.json',dict(command=command,source_pins=pins,analysis_admitted=False))
    with (out/'stdout.log').open('xb') as stdout,(out/'stderr.log').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',TMPDIR=str(out),R_USER_CACHE_DIR=str(out)),stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=300)
    save(out/'process.json',dict(command=command,exit_code=r.returncode));after=inventory(libraries);save(out/'LIBRARIES-AFTER.json',after);report=json.loads((out/'R-RESULT.json').read_bytes());result=dict(report,source_pins=pins,libraries_unchanged=before==after,library_files_checked=sum(map(len,before.values())),software_installed=False)
    if before!=after:result['status']='FAIL2_LIBRARY_CHANGED'
    assert all(pin(ROOT/n)==v for n,v in pins.items());save(out/'RESULT.json',result)
    (out/'WARNINGS.md').write_text('# '+result['status']+'\n\n'+result['detail']+'\n\n'+'\n'.join(result['warnings'])+'\n\nREQUIRED ACTION: preserve all original objects and warnings. Original reference controls alone admit no user analysis. Do not treat unresolved NA connections as zero or valid absence. A typed Java adapter, source/axis/model audits and recovery controls remain required. If dependencies are missing or forbidden, skip dependent analysis; provision only into NEW private libraries where permitted.\n',encoding='utf8',newline='\n');print(json.dumps({k:v for k,v in result.items() if k not in ['source_pins','warnings']}));return 0 if r.returncode==0 and before==after else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/library/original-fit checks')
    raise SystemExit(main())
