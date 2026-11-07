# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run explicit original SoupX ambient controls in existing exact private references."""
import argparse,json,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--version',choices=['1.6.2','1.3.0'],required=True);p.add_argument('--reference',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/soupx-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();reference=a.reference.resolve();assert reference.is_relative_to(base);setup=json.loads((reference/'COMPLETED.json').read_bytes());assert setup['status']=='installed-source-bound-original-SoupX-reference-only' and setup['preexisting_libraries_unchanged'] and setup['version']==a.version;source=base/('source-intake-1/1.6.2' if a.version=='1.6.2' else 'source-recovery-1/1.3.0');receipt=json.loads((source/'SOURCE.json').read_bytes());assert all(pin(source/'source'/n)==v for n,v in receipt['source_members'].items());libraries=[reference/'R-lib',*map(Path,json.loads((reference/'STARTED.json').read_bytes())['libraries'])];before=inventory(libraries);save(out/'LIBRARIES-BEFORE.json',before);script=ROOT/'tools/literature/collect_soupx_ambient.R';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),script,reference/'COMPLETED.json',source/'SOURCE.json']}
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    fixture=base/'source-intake-1/1.6.2';fixture_receipt=json.loads((fixture/'SOURCE.json').read_bytes());assert all(pin(fixture/'source'/n)==v for n,v in fixture_receipt['source_members'].items());pins[(fixture/'SOURCE.json').relative_to(ROOT).as_posix()]=pin(fixture/'SOURCE.json');target=out/'source-snapshot'/(fixture/'SOURCE.json').relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((fixture/'SOURCE.json').read_bytes())
    command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(out),str(source/'source'),a.version,str(fixture/'source'),*map(str,libraries)];env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en',R_LIBS=';'.join(map(str,libraries)),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',TMPDIR=str(out),R_USER_CACHE_DIR=str(out));save(out/'STARTED.json',dict(command=command,libraries=list(map(str,libraries)),source_pins=pins,fixture_source='SoupX1.6.2 official toyData reused identically across both reference APIs'));status=dict(status='FAIL2_ORIGINAL_SOUPX_AMBIENT_CONTROLS_UNFINISHED',version=a.version,source_pins=pins,analysis_admitted=False)
    try:
        with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
        save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Original ambient controls failed; preserve this attempt and inspect logs';controls=json.loads((out/'controls.json').read_bytes());assert controls['version']==a.version and controls['source_bodies_and_formals_checked_before_and_after'];status.update(status='captured-original-SoupX-ambient-controls-not-adapter-validation',cases=len(controls['cases']),controls=pin(out/'controls.json'),original_inputs=pin(out/'inputs.json'),original_input_objects=pin(out/'inputs.rds'))
    except Exception as failure:status['cause']=str(failure)
    finally:
        after=inventory(libraries);save(out/'LIBRARIES-AFTER.json',after);status.update(preexisting_libraries_unchanged=before==after,preexisting_files=sum(map(len,before.values())),implementation_credit=False,whole_goal_complete=False);assert before==after and all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(source/'source'/n)==v for n,v in receipt['source_members'].items()) and all(pin(fixture/'source'/n)==v for n,v in fixture_receipt['source_members'].items());save(out/'COMPLETED.json',status)
    print(json.dumps({k:v for k,v in status.items() if k!='source_pins'}));return 0 if status['status'].startswith('captured-original-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/reference checks')
    raise SystemExit(main())
