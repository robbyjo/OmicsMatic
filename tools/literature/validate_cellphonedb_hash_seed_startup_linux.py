# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independent stdlib hash baselines, forbidden startup modes, and owned cancellation."""
import argparse,json,os,signal,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();assert sys.platform=='linux' and out==out.resolve() and out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);control=ROOT/'build/literature-review/cellphonedb-runtime/hash-seed-controls-1';proof=json.loads((control/'COMPLETED.json').read_bytes());assert proof['status']=='PASS_FRESH_EFFECTIVE_SEED_CELLPHONEDB_CONTROLS_ONLY';runtime=ROOT/'build/cellphonedb-workflow-case-sensitive-1/setup-1/COMPLETED.json';binding=json.loads(runtime.read_bytes());python=binding['runtime_python'];new=ROOT/'src/main/resources/org/jlinalg/omics/cellphonedb-hash-seed';probe=control/'startup-only.py';request=json.loads((control/'request.json').read_bytes());env=dict(os.environ)
    for key in list(env):
        if key.startswith(('PYTHON','PIP_','CONDA_','R_')) or key in ['LD_PRELOAD','LD_LIBRARY_PATH']:env.pop(key,None)
    pins={str(p):pin(p) for p in [Path(__file__).resolve(),runtime,probe,new/'supervisor.py',control/'request.json',control/'COMPLETED.json']};state=dict(status='FAIL2_EFFECTIVE_STARTUP_BASELINES_AND_CANCELLATION',processes=[],whole_goal_complete=False,implementation_credit=False)
    def execute(name,command,expected,seed):
        run=subprocess.run(command,env=dict(env,**({} if seed is None else dict(PYTHONHASHSEED=seed))),capture_output=True,timeout=30);(out/(name+'.stdout')).write_bytes(run.stdout);(out/(name+'.stderr')).write_bytes(run.stderr);record=dict(name=name,command=command,environment_seed=seed,exit_code=run.returncode,expected_exit=expected);state['processes'].append(record);assert run.returncode==expected,name+' unexpected exit';return run.stdout
    try:
        for i in [0,2,4]:
            wanted=proof['startup_receipts'][i]['receipt'];seed=str(wanted['requested_seed']);code='import json,sys;print(json.dumps(dict(version=list(sys.version_info[:3]),flags=[sys.flags.ignore_environment,sys.flags.safe_path,sys.flags.no_user_site,sys.flags.hash_randomization],hashes=[[s,hash(s)] for s in '+repr([v['text'] for v in wanted['hashes']])+'])))';raw=execute('baseline-'+seed,[python,'-B','-P','-s','-c',code],0,seed);actual=json.loads(raw);assert actual['version']==[3,11,17] and actual['hashes']==[[v['text'],v['hash']] for v in wanted['hashes']] and actual['flags']==[0,True,1,0 if seed=='0' else 1]
        for name,flags,environment,declared in [('ignore-environment',['-B','-P','-s','-E'],'0',0),('isolated',['-I','-B'],'0',0),('unsafe-path',['-B','-s'],'0',0),('user-site-enabled',['-B','-P'],'0',0),('optimized',['-B','-P','-s','-O'],'0',0),('bytecode-enabled',['-P','-s'],'0',0),('missing-seed',['-B','-P','-s'],None,0),('mismatched-seed',['-B','-P','-s'],'123',0)]:
            home=out/name;home.mkdir();save(home/'request.json',dict(request,python_hash_seed=declared));flags=['-X','pycache_prefix='+str(home/'private-pycache'),*flags];execute(name,[python,*flags,str(probe),str(home),str(home/'request.json')],1,environment);assert not (home/'python-startup.json').exists()
        # Cancel the owned original producer before any case can be admitted.
        home=out/'owned-cancellation';home.mkdir();(home/'CANCEL_REQUESTED').write_bytes(b'Explicit cancellation control\n');(home/'supervisor.py').write_bytes((new/'supervisor.py').read_bytes());(home/'runtime.original.json').write_bytes(runtime.read_bytes());save(home/'request.json',dict(request,analyses=request['analyses'][:1]));(home/'worker.py').write_bytes((new/'worker.py').read_bytes());(home/'codec.py').write_bytes((ROOT/'src/main/resources/org/jlinalg/omics/cellphonedb-workflow/codec.py').read_bytes());command=[python,'-I','-B',str(home/'supervisor.py'),'--runtime',str(home/'runtime.original.json'),'--out',str(home),'--worker',str(home/'worker.py'),'--request',str(home/'request.json'),'--timeout','30','--maximum-output-bytes','2147483648','--maximum-files','100000'];execute('owned-cancellation',command,2,'0');record=json.loads((home/'original.process.json').read_bytes());assert record['status']=='FAIL2_ORIGINAL_WORKFLOW_SUPERVISION' and not record['owned_live_after_cleanup'] and 'cancellation' in record['error'] and not (home/'COMPLETED.json').exists();state.update(status='PASS_INDEPENDENT_EFFECTIVE_STARTUP_BASELINES_AND_CANCELLATION_ONLY',independent_baseline_seeds=[0,123,4294967295],startup_modes_rejected_before_scientific_imports=8,owned_cancellation=record)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    state['private_bytecode_control_files']=len(list((out/'bytecode-enabled/private-pycache').rglob('*.pyc')))
    if state['status'].startswith('PASS_') and state['private_bytecode_control_files']==0:state.update(status='FAIL2_NO_PRIVATE_BYTECODE_CONTROL',error='Bytecode-enabled negative control did not demonstrate private cache writes')
    state['source_inputs_unchanged']=all(pin(Path(n))==want for n,want in pins.items());state['source_pins']=pins
    if not state['source_inputs_unchanged']:state.update(status='FAIL2_STARTUP_SOURCE_CHANGED',error='Bound source changed')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable startup/cancellation checks')
    raise SystemExit(main())
