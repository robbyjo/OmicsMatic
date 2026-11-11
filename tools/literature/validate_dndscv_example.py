# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the documented example from a separately exported and privately installed source."""
import argparse,json,subprocess,sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory
from validate_mashr_native import JAVA21
from validate_dndscv_runtime import compare


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--reference-home',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();base=ROOT/'build/literature-review/dndscv-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};helper=ROOT/'tools/literature/prepare_dndscv_example.py';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),helper,ROOT/'tools/literature/validate_dndscv_runtime.py']};command=[sys.executable,str(helper),'--reference-home',str(a.reference_home.resolve()),'--out',str(out/'example')];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=60);(out/'prepare.stdout').write_bytes(r.stdout);(out/'prepare.stderr').write_bytes(r.stderr);save(out/'prepare-process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0;rpath=out/'example/request.json';request=json.loads(rpath.read_bytes());libs=list(map(Path,request['r_libraries']));before=inventory(libs);save(out/'LIBRARIES-BEFORE.json',before);command=[str(JAVA21/'java.exe'),'-Xmx2g','-cp',str(jar),'org.jlinalg.cli.DndscvWorkflowCli','--request',str(rpath)]
    with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=900)
    save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0;result=out/'example/result';assert json.loads((result/'analysis-status.json').read_bytes())['result_admitted'];comparison=compare(result/'results/targeted-2',base/'original-controls-1/targeted-2');after=inventory(libs);save(out/'LIBRARIES-AFTER.json',after);assert before==after and all(pin(ROOT/n)==v for n,v in [*pins.items(),*build['source_pins'].items()]);save(out/'COMPLETED.json',dict(status='passed-documented-original-dndscv-example',artifact=build['artifact'],source_pins=pins,reference_comparison=comparison,preexisting_libraries_unchanged=True,result_admitted=True,whole_paper_reproduced=False,whole_goal_complete=False));print('Documented example matched every original result table and both complete arrays')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable example/source checks')
    main()
