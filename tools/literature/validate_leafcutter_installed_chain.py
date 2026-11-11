# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Observe the already-running packaged installation, then validate its exact runtime once; never restart or repair an installer."""
import argparse,json,subprocess,sys,time,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/leafcutter-runtime';assert out.is_relative_to(base) and not out.exists();out.mkdir();setup=ROOT/'build/leafcutter-case-sensitive-1/setup-1';receipt=setup/'COMPLETED.json';state=dict(status='FAIL2_INSTALLED_LEAFCUTTER_CHAIN_INCOMPLETE',installation_restarted=False,installation_repaired=False,implementation_credit=False,whole_goal_complete=False,processes=[]);source=pin(Path(__file__));save(out/'STARTED.json',dict(status='WAITING_FOR_EXISTING_PACKAGED_INSTALL_TERMINAL_RECEIPT',setup=str(setup),source_pin=source));(out/'supervisor-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    try:
        deadline=time.monotonic()+3600
        while not receipt.exists():
            assert time.monotonic()<deadline,'Existing installation has no terminal receipt; preserve and inspect owned process rather than restart';time.sleep(5)
        binding=json.loads(receipt.read_bytes());assert binding['status']=='PASS_BOUND_ORIGINAL_LEAFCUTTER_WORKFLOW_RUNTIME_ONLY' and binding['mode']=='INSTALL' and binding['runtime_trees_unchanged'] and binding['shared_installer_unchanged'] and binding['namespace_patched'] is False,'Packaged installation did not pass; dependent analysis remains skipped'
        commands=[('public-science',['validate_leafcutter_workflow.py','--build',str(base/'workflow-build-3'),'--fixtures',str(base/'workflow-fixtures-2'),'--reference',str(base/'workflow-original-1'),'--out',str(base/'workflow-validation-2')]),('public-protocols',['validate_leafcutter_workflow_protocols.py','--build',str(base/'workflow-build-3'),'--fixtures',str(base/'workflow-fixtures-2'),'--successful-analysis',str(base/'workflow-validation-2/analysis'),'--setup-kit',str(base/'workflow-exported-setup-2'),'--out',str(base/'workflow-protocols-2')]),('retained-output-equations',['audit_leafcutter_workflow_outputs.py','--analysis',str(base/'workflow-validation-2/analysis'),'--out',str(base/'workflow-output-audit-4')])]
        sources={str(Path(__file__).resolve().relative_to(ROOT).as_posix()):source}
        for label,args in commands:
            helper=ROOT/'tools/literature'/args[0];sources[helper.relative_to(ROOT).as_posix()]=pin(helper);command=[sys.executable,'-B',str(helper),*args[1:]];save(out/(label+'.STARTED.json'),dict(command=command,source_pin=pin(helper)))
            with (out/(label+'.stdout')).open('xb') as first,(out/(label+'.stderr')).open('xb') as second:result=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,creationflags=subprocess.CREATE_NO_WINDOW)
            record=dict(label=label,command=command,exit_code=result.returncode);save(out/(label+'.process.json'),record);state['processes'].append(record);assert result.returncode==0,label+' failed; retain the complete new attempt and original output';print(label+': passed',flush=True)
        assert all(pin(ROOT/name)==want for name,want in sources.items());state.update(status='PASS_FRESH_PACKAGED_LEAFCUTTER_RUNTIME_SCIENCE_PROTOCOL_AND_RETAINED_EQUATIONS_ONLY',source_pins=sources,runtime_receipt=pin(receipt))
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete installed-runtime checks')
    raise SystemExit(main())
