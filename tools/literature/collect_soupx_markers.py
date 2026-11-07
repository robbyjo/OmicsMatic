# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture actual separate original SoupX marker calls without modifying existing libraries."""
import argparse,json,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--version',choices=['1.6.2','1.3.0'],required=True);p.add_argument('--reference',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/soupx-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir()
    reference=a.reference.resolve();assert reference.is_relative_to(base);setup=json.loads((reference/'COMPLETED.json').read_bytes());assert setup['status']=='installed-source-bound-original-SoupX-reference-only' and setup['preexisting_libraries_unchanged'] and setup['version']==a.version;source=base/('source-intake-1/1.6.2' if a.version=='1.6.2' else 'source-recovery-1/1.3.0');receipt=json.loads((source/'SOURCE.json').read_bytes());assert all(pin(source/'source'/n)==v for n,v in receipt['source_members'].items())
    libraries=[reference/'R-lib',*map(Path,json.loads((reference/'STARTED.json').read_bytes())['libraries'])];before=inventory(libraries);save(out/'LIBRARIES-BEFORE.json',before)
    script=ROOT/'tools/literature/collect_soupx_markers.R';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),script,reference/'COMPLETED.json',source/'SOURCE.json']}
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(out),str(source/'source'),a.version,*map(str,libraries)];env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en',R_LIBS=';'.join(map(str,libraries)),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',TMPDIR=str(out),R_USER_CACHE_DIR=str(out));save(out/'STARTED.json',dict(command=command,libraries=list(map(str,libraries)),source_pins=pins))
    with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=300)
    save(out/'process.json',dict(command=command,exit_code=r.returncode));after=inventory(libraries);save(out/'LIBRARIES-AFTER.json',after)
    assert before==after and all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(source/'source'/n)==v for n,v in receipt['source_members'].items());assert r.returncode==0,'Original controls failed; preserve this attempt and inspect stdout/stderr'
    controls=json.loads((out/'controls.json').read_bytes());assert controls['source_bodies_and_formals_checked_before_and_after'] and controls['version']==a.version and len(controls['cases'])==83
    save(out/'COMPLETED.json',dict(status='captured-original-SoupX-marker-controls',version=a.version,source_pins=pins,cases=len(controls['cases']),controls=pin(out/'controls.json'),preexisting_libraries_unchanged=True,preexisting_files=sum(map(len,before.values())),software_installed=False,implementation_credit=False,whole_goal_complete=False));print(a.version,len(controls['cases']),'original controls; existing libraries unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original/source checks')
    main()
