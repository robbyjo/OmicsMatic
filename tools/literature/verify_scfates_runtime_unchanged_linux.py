# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Recheck both complete bound runtime trees after all workflow controls; no imports or analysis."""
import argparse,json,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save
from inventory_hail_runtime_linux import inventory

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--runtime',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();out=a.out.absolute();assert sys.platform=='linux' and out==out.resolve() and not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_SCFATES_FINAL_RUNTIME_RECHECK',implementation_credit=False,whole_goal_complete=False,analysis_executed=False,software_installed=False)
    try:
        receipt_pin=pin(a.runtime);binding=json.loads(a.runtime.read_bytes());assert binding['status']=='PASS_BOUND_ORIGINAL_SCFATES_WORKFLOW_RUNTIME_ONLY' and binding['runtime_trees_unchanged'] and binding['shared_inputs_unchanged'];counts={}
        for label,key in [('base','base_environment'),('runtime','runtime_environment')]:
            root=Path(binding[key]);assert root==root.resolve();expected_path=a.runtime.parent/(label+'-before.json');expected=json.loads(expected_path.read_bytes());actual=inventory(root,progress=out/(label+'.progress.json'));save(out/(label+'-final.json'),actual);assert actual==expected,'Complete original runtime changed: '+label;counts[label]=len(actual)
        assert pin(a.runtime)==receipt_pin;state.update(status='PASS_COMPLETE_SCFATES_BASE_AND_PRIVATE_RUNTIME_UNCHANGED_AFTER_CONTROLS_ONLY',runtime_receipt=receipt_pin,members=counts,source_pins={str(Path(__file__).resolve()):pin(Path(__file__).resolve())})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
