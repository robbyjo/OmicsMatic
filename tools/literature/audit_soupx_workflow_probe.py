# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only completion of saved packaged probes; preserves failed validator snapshots and never reruns analysis."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from probe_soupx_workflow import reference_check


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--probe',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/soupx-runtime';probe=a.probe.resolve();out=a.out.resolve();assert probe.is_relative_to(base) and out.is_relative_to(base) and not out.exists();out.mkdir();start=json.loads((probe/'STARTED.json').read_bytes());process=json.loads((probe/'process.json').read_bytes());request=json.loads((probe/'request.json').read_bytes());state=json.loads((probe/'result/analysis-status.json').read_bytes());assert process['exit_code']==0 and state['result_admitted'] and state['status']=='PASS_ORIGINAL_SOUPX_AMBIENT_WORKFLOW';assert (probe/'LIBRARIES-BEFORE.json').read_bytes()==(probe/'LIBRARIES-AFTER.json').read_bytes()
    for name,value in start['source_pins'].items():
        assert pin(probe/'source-snapshot'/name)==value
        if name.startswith(probe.relative_to(ROOT).as_posix()+'/'):assert pin(ROOT/name)==value
    reports=reference_check(probe/'result',request['version']);pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/probe_soupx_workflow.py',probe/'STARTED.json',probe/'process.json',probe/'result/analysis-status.json',probe/'LIBRARIES-BEFORE.json',probe/'LIBRARIES-AFTER.json']}
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    save(out/'COMPLETED.json',dict(status='audited-saved-completed-SoupX-probe-without-rerunning-analysis',version=request['version'],probe=probe.relative_to(ROOT).as_posix(),artifact=start['artifact'],source_pins=pins,reference_comparisons=reports,preexisting_libraries_unchanged=True,original_process_exit_code=0,original_analysis_admitted=True,original_failed_checker_preserved=True,logical_representation_normalization='Only saved JSON Boolean values are represented as exact R TRUE/FALSE tokens for comparison; no scientific values or files change.',ambient_workflow_admitted=False,implementation_credit=False,whole_goal_complete=False));print(request['version'],len(reports),'saved-reference comparisons passed; no analysis rerun')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable saved process/reference checks')
    main()
