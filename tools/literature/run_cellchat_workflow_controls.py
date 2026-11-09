# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run a six-sample original-package workflow control; no user analysis is admitted."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,environment,pin,save
from setup_chipseeker_reference import inventory

BASE=ROOT/'build/literature-review/cellchat-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    libraries=[BASE/'presto-reference-setup-2/R-lib',BASE/'reference-setup-1/R-lib',*[Path(p) for p in json.loads((BASE/'reference-setup-1/STARTED.json').read_bytes())['libraries']]]
    for folder,status in [('reference-setup-1','INSTALLED_ORIGINAL_CELLCHAT_REFERENCE_ONLY'),('presto-reference-setup-2','INSTALLED_ORIGINAL_PRESTO_REFERENCE_ONLY')]:assert json.loads((BASE/folder/'COMPLETED.json').read_bytes())['status']==status
    before=inventory(libraries);save(out/'LIBRARIES-BEFORE.json',before);source=BASE/'source-intake-1/2.2.0-2025-release/source'
    state=dict(status='FAIL2_CELLCHAT_ORIGINAL_WORKFLOW_CONTROLS',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    files=[Path(__file__).resolve(),ROOT/'tools/literature/create_cellchat_workflow_controls.R',ROOT/'tools/literature/cellchat_original_workflow.R',ROOT/'tools/literature/cellchat-workflow-source-lock.json'];pins={p.relative_to(ROOT).as_posix():pin(p) for p in files}
    try:
        for file in files:
            target=out/'source-snapshot'/file.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(file.read_bytes())
        def run(id,script,args):
            command=[str(RSCRIPT),'--vanilla',str(out/'source-snapshot'/script),*map(str,args),*map(str,libraries)]
            with (out/(id+'.stdout')).open('xb') as stdout,(out/(id+'.stderr')).open('xb') as stderr:
                proc=subprocess.run(command,cwd=ROOT,env=environment(),stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=1800)
            save(out/(id+'-process.json'),dict(command=command,exit_code=proc.returncode));return proc.returncode
        assert run('fixture','tools/literature/create_cellchat_workflow_controls.R',[out/'inputs',source])==0,'Read fixture.stderr'
        code=run('workflow','tools/literature/cellchat_original_workflow.R',[out/'inputs/request.json',source,out/'source-snapshot/tools/literature/cellchat-workflow-source-lock.json',out/'worker'])
        report=json.loads((out/'worker/worker-report.json').read_bytes());assert code==0 and report['status']=='COMPLETED_ORIGINAL_CELLCHAT_WORKER_ONLY',report.get('detail','Read workflow.stderr')
        assert len(report['cases'])==8 and all(c['worker_case_complete'] for c in report['cases'].values())
        state.update(status='PASS_ORIGINAL_CELLCHAT_TWO_CONDITION_SIX_SAMPLE_WORKER_CONTROLS_ONLY',cases=8,worker_report=pin(out/'worker/worker-report.json'),original_function_formal_body_guards=True,package_version='2.2.0',presto_version='1.0.0',scientific_reference_independently_refitted=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    after=inventory(libraries);save(out/'LIBRARIES-AFTER.json',after);state.update(preexisting_libraries_unchanged=before==after,source_pins=pins)
    if before!=after:state.update(status='FAIL2_PREEXISTING_LIBRARY_CHANGED',error='Inspect library inventories')
    if any(pin(ROOT/n)!=v for n,v in pins.items()):state.update(status='FAIL2_WORKFLOW_CONTROL_SOURCE_CHANGED',error='A current control source changed during this attempt; preserve outputs and rerun fresh')
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Synthetic original-package worker controls only. No public typed Java controller, complete admission gate or whole-paper reproduction is established.')+'\n\nRequired action: skip dependent analysis when an exact runtime is unavailable or forbidden. Preserve failed attempts and use NEW paths. Original-source nonfinite results and centrality fallbacks must remain visible. Worker completion alone admits no user analysis.\n',encoding='utf8')
    print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/runtime preservation checks')
    raise SystemExit(main())
