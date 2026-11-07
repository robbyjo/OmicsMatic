# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Generate and execute the documented upstream fixed-rho example, comparing every corrected coordinate."""
import argparse,json,subprocess,sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21
from probe_soupx_workflow import sparse


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/soupx-runtime';assert out.is_relative_to(base) and not out.exists();out.mkdir();build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};helper=ROOT/'tools/literature/prepare_soupx_workflow_example.py';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),helper,ROOT/'tools/literature/probe_soupx_workflow.py']};command=[sys.executable,str(helper),'--reference-home',str(base/'reference-1.6.2-1'),'--out',str(out/'example')];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=30);(out/'prepare.stdout').write_bytes(r.stdout);(out/'prepare.stderr').write_bytes(r.stderr);assert r.returncode==0;command=[str(JAVA21/'java.exe'),'-Xmx768m','-cp',str(jar),'org.jlinalg.cli.SoupXWorkflowCli','--request',str(out/'example/request.json')]
    with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
    save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0;result=out/'example/result';state=json.loads((result/'analysis-status.json').read_bytes());assert state['result_admitted'];rows,cells,values=sparse(result/'results/toy-fixed-rho/corrected-counts.json');original=next(r['corrected'] for r in json.loads((base/'ambient-controls-1.6.2-2/controls.json').read_bytes())['cases'] if r['id']=='fixed-cell-only-subtraction-FALSE');assert rows==original['genes'] and cells==original['cells'] and values=={(e['gene'],e['cell']):e['value'] for e in original['entries']};assert all(pin(ROOT/n)==v for n,v in [*pins.items(),*build['source_pins'].items()]);save(out/'COMPLETED.json',dict(status='passed-documented-original-SoupX-workflow-example',artifact=build['artifact'],source_pins=pins,corrected_coordinates_compared=len(values),fixed_rho_demonstration_not_biological_estimate=True,ambient_workflow_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Executed documented original SoupX example;',len(values),'corrected coordinates match saved original exactly')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable example/source checks')
    main()
