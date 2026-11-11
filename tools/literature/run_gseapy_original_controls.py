# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute all five original API families under source-bound process supervision; no Java adapter admission."""
import argparse,concurrent.futures,hashlib,importlib.util,json,os,subprocess,sys,traceback
from pathlib import Path

def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['runtime','fixtures','out']:parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--pilot',action='store_true',help='Diagnostic original calls only; no complete runtime verification or scientific admission')
    a=parser.parse_args();out=a.out.resolve();assert not out.exists();out.mkdir(parents=True);root=Path(__file__).resolve().parents[2];resources=root/'src/main/resources/org/jlinalg/omics/gseapy-workflow';state=dict(status='FAIL2_ORIGINAL_GSEAPY_CONTROL_BATCH',implementation_credit=False,full_adapter_admitted=False,whole_goal_complete=False);before=None;base_before=None
    try:
        runtime=a.runtime.resolve();binding=json.loads(runtime.read_bytes());assert binding['status']=='PASS_BOUND_ORIGINAL_GSEAPY_WORKFLOW_RUNTIME_ONLY' and binding['runtime_trees_unchanged'] and binding['shared_inputs_unchanged'] and not binding['namespace_patched'];prefix=Path(binding['runtime_environment']);base=Path(binding['base_environment']);assert Path(sys.executable)==Path(binding['runtime_python']);inventory_source=resources/'setup/inventory.py';spec=importlib.util.spec_from_file_location('exact_inventory',inventory_source);inventory=importlib.util.module_from_spec(spec);spec.loader.exec_module(inventory)
        if not a.pilot:
            before=inventory.inventory(prefix,out/'runtime-before.progress.json');base_before=inventory.inventory(base,out/'base-before.progress.json');save(out/'runtime-before.json',before);save(out/'base-before.json',base_before);assert before==json.loads((runtime.parent/'runtime-before.json').read_bytes()) and base_before==json.loads((runtime.parent/'base-before.json').read_bytes());print('Complete original runtime/base inventories verified',flush=True)
        else:state['diagnostic_pilot_without_scientific_admission']=True
        profile=json.loads((resources/'worker-profile.json').read_bytes());assert all(pin(resources/n)==v for n,v in profile['files'].items());files=[Path(__file__).resolve(),resources/'worker-profile.json',resources/'profiles.json',*[resources/n for n in profile['files']]];source_pins={p.relative_to(root).as_posix():pin(p) for p in files};inputs={p.relative_to(root).as_posix():pin(p) for p in a.fixtures.rglob('*.json')};runtime_pin=pin(runtime);results={}
        def run(edition):
            home=out/edition;home.mkdir()
            for name in profile['files']:(home/name).write_bytes((resources/name).read_bytes())
            (home/'runtime.original.json').write_bytes(runtime.read_bytes());(home/'request.json').write_bytes((a.fixtures/edition/'request.json').read_bytes())
            command=[sys.executable,'-I','-B',str(home/'supervisor.py'),'--runtime',str(home/'runtime.original.json'),'--out',str(home),'--worker',str(home/'worker.py'),'--request',str(home/'request.json'),'--timeout','300','--maximum-output-bytes','134217728','--maximum-files','100000'];env=dict(os.environ)
            for key in list(env):
                if key.startswith('PYTHON') or key in ['LD_PRELOAD','LD_LIBRARY_PATH']:env.pop(key,None)
            with (home/'supervisor.stdout').open('xb') as first,(home/'supervisor.stderr').open('xb') as second:result=subprocess.run(command,cwd=home,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=340)
            save(home/'reference.process.json',dict(command=command,exit_code=result.returncode));report=json.loads((home/'COMPLETED.json').read_bytes());receipt=json.loads((home/'original.process.json').read_bytes());assert not receipt['owned_live_after_cleanup'];return edition,dict(exit_code=result.returncode,report=report,process=receipt)
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            for edition,report in pool.map(run,['1.0.4','1.1.2','1.1.3','1.1.8']):results[edition]=report;print(edition,report['report']['status'],flush=True)
        state['editions']=results
        assert all(v['exit_code']==0 and v['report']['status']=='PASS_COMPLETE_ORIGINAL_GSEAPY_WORKFLOWS_ONLY' for v in results.values()),'Original complete batch failed; retain all earlier cases as provisional'
        assert sum(len(v['report']['cases']) for v in results.values())==33
        assert all(pin(root/n)==v for n,v in source_pins.items()) and all(pin(root/n)==v for n,v in inputs.items()) and pin(runtime)==runtime_pin
        state.update(status='passed-diagnostic-pilot-only-without-runtime-or-scientific-admission' if a.pilot else 'passed-33-complete-original-GSEApy-workflow-controls-only',source_pins=source_pins,input_pins=inputs,runtime_receipt=runtime_pin,total_original_cases=33)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        if before is not None:
            after=inventory.inventory(prefix,out/'runtime-after.progress.json');save(out/'runtime-after.json',after);base_after=inventory.inventory(base,out/'base-after.progress.json');save(out/'base-after.json',base_after);state['complete_runtime_trees_unchanged']=before==after and base_before==base_after
            if not state['complete_runtime_trees_unchanged']:state.update(status='FAIL2_ORIGINAL_RUNTIME_CHANGED',error='No original control batch admitted')
        save(out/'COMPLETED.json',state)
    print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original control gates')
    raise SystemExit(main())
