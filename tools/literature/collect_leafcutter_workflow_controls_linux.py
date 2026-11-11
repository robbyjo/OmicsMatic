# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run every unchanged original program in the complete requested pipeline; retain whole batches, raw output families and immutable runtime/source/input bindings."""
import argparse,json,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save
from inventory_hail_runtime_linux import inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime',required=True,type=Path);p.add_argument('--fixtures',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert sys.platform=='linux' and out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='SKIP3_ORIGINAL_LEAFCUTTER_WORKFLOW_RUNTIME_UNAVAILABLE',implementation_credit=False,whole_goal_complete=False);before={};roots={};resource=ROOT/'src/main/resources/org/jlinalg/omics/leafcutter-workflow';sources={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),ROOT/'tools/literature/inventory_hail_runtime_linux.py',ROOT/'tools/literature/setup_hail_reference_linux.py']}
    try:
        runtime=json.loads(a.runtime.read_bytes());assert runtime['status']=='PASS_BOUND_ORIGINAL_LEAFCUTTER_WORKFLOW_RUNTIME_ONLY' and runtime['runtime_trees_unchanged'] and pin(resource/'profiles.json')==runtime['profile'];roots=dict(runtime=Path(runtime['runtime_environment']),library=Path(runtime['R_library']))
        for name,path in roots.items():before[name]=inventory(path,progress=out/(name+'-before.progress.json'));save(out/(name+'-before.json'),before[name]);assert before[name]==json.loads((a.runtime.parent/(name+'-before.json')).read_bytes())
        source=out/'source';source.mkdir();profiles={}
        for name in ['profiles.json','worker-profile.json']:
            profiles.update(json.loads((resource/name).read_bytes())['files']);(source/name).write_bytes((resource/name).read_bytes());sources[(resource/name).relative_to(ROOT).as_posix()]=pin(resource/name)
        for name,want in profiles.items():
            original=resource/name;assert pin(original)==want;dest=source/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(original.read_bytes());sources[original.relative_to(ROOT).as_posix()]=want
        fixture=json.loads((a.fixtures/'request.json').read_bytes());pins=json.loads((a.fixtures/'input-pins.json').read_bytes());assert all(pin(a.fixtures/name)==want for name,want in pins.items());save(out/'fixture-input-pins.json',pins);(out/'request.original.json').write_bytes((a.fixtures/'request.json').read_bytes());worker=out/'worker';worker.mkdir();(worker/'runtime.original.json').write_bytes(a.runtime.read_bytes());(worker/'request.json').write_bytes((a.fixtures/'request.json').read_bytes())
        for name in ['worker.py','supervisor.py']:(worker/name).write_bytes((resource/name).read_bytes())
        state['status']='FAIL2_ORIGINAL_LEAFCUTTER_FULL_WORKFLOW_CONTROLS_INCOMPLETE';command=[runtime['runtime_python'],'-I','-B',str(worker/'supervisor.py'),'--runtime',str(worker/'runtime.original.json'),'--out',str(worker),'--worker',str(worker/'worker.py'),'--request',str(worker/'request.json'),'--timeout','600','--maximum-output-bytes','536870912','--maximum-files','100000']
        with (out/'supervisor.stdout').open('xb') as stdout,(out/'supervisor.stderr').open('xb') as stderr:result=subprocess.run(command,cwd=out,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
        save(out/'supervisor.process.json',dict(command=command,exit_code=result.returncode));assert result.returncode==0,'Original full programs failed; inspect worker/original-error.traceback and every original stderr';report=json.loads((worker/'COMPLETED.json').read_bytes());process=json.loads((worker/'original.process.json').read_bytes());assert report['status']=='PASS_COMPLETE_ORIGINAL_LEAFCUTTER_WORKFLOWS_ONLY' and len(report['cases'])==len(fixture['analyses']) and process['exit_code']==0 and process['owned_live_after_cleanup']==[];assert all(pin(a.fixtures/name)==want for name,want in pins.items());state.update(status='PASS_COMPLETE_ORIGINAL_LEAFCUTTER_FULL_WORKFLOW_CONTROLS_ONLY',cases=report['cases'],runtime_manifest=pin(a.runtime),source_pins=sources,input_pins=pins,namespace_patched=False,analysis_installed_software=False)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    finally:
        unchanged=True
        for name,was in before.items():after=inventory(roots[name],progress=out/(name+'-after.progress.json'));save(out/(name+'-after.json'),after);unchanged&=was==after
        state['runtime_trees_unchanged']=unchanged and bool(before)
        if before and not unchanged:state.update(status='FAIL2_BOUND_RUNTIME_CHANGED',error='Complete runtime/library changed during original workflow controls')
        assert all(pin(ROOT/name)==want for name,want in sources.items());state['required_action']='Required action: keep all cases in this atomic batch unadmitted until complete original/native workflow validation passes. Preserve source counts/IDs, metadata order, original files, binary fits, warnings, process groups and failure receipts. Correct the named input/source/runtime/model/numeric/process/capacity cause and retry NEW paths. Missing/forbidden required software keeps dependent analysis skipped; no historical-paper or full-tool credit is inferred.';save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Complete original program controls only; native audits and public adapter admission remain separate.')+'\n\n'+state['required_action']+'\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original workflow checks')
    raise SystemExit(main())
