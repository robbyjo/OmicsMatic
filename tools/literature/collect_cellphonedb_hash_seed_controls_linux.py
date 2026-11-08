# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Fresh effective-seed original-method comparisons and repeated real interpreter startup receipts."""
import argparse,json,os,signal,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save
from inventory_hail_runtime_linux import inventory

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();assert sys.platform=='linux' and out==out.resolve() and out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);old=ROOT/'src/main/resources/org/jlinalg/omics/cellphonedb-workflow';new=ROOT/'src/main/resources/org/jlinalg/omics/cellphonedb-hash-seed';setup=ROOT/'build/cellphonedb-workflow-case-sensitive-1/setup-1';binding=json.loads((setup/'COMPLETED.json').read_bytes());assert binding['status']=='PASS_BOUND_ORIGINAL_CELLPHONEDB_WORKFLOW_RUNTIME_ONLY';prefix=Path(binding['runtime_environment']);python=prefix/'bin/python';source=ROOT/'build/literature-review/cellphonedb-runtime/safe-worker-controls-3/request.json';request=json.loads(source.read_bytes());assert len(request['analyses'])==19 and request['python_hash_seed']==0;save(out/'request.json',request);state=dict(status='FAIL2_CELLPHONEDB_EFFECTIVE_SEED_CONTROLS',implementation_credit=False,whole_goal_complete=False,processes=[]);before=None
    files=[Path(__file__).resolve(),source,setup/'COMPLETED.json',ROOT/'tools/literature/cellphonedb_direct_reference_worker.py',ROOT/'tools/literature/compare_cellphonedb_safe_worker.py',ROOT/'tools/literature/validate_cellphonedb_seeded_sampling.py',ROOT/'tools/literature/inventory_hail_runtime_linux.py',*new.iterdir(),old/'codec.py',old/'profiles.json'];files+=[Path(v['path']) for case in request['analyses'] for k,v in case.items() if k in ['counts','metadata','database','microenvironments','degs','active_tfs']];pins={str(path):pin(path) for path in files if path.is_file()};env=dict(os.environ)
    for key in list(env):
        if key.startswith(('PYTHON','CONDA_','PIP_','R_')) or key in ['LD_PRELOAD','LD_LIBRARY_PATH']:env.pop(key,None)
    for name in ['private-home','private-cache','private-config','private-temp']:(out/name).mkdir()
    env.update(HOME=str(out/'private-home'),XDG_CACHE_HOME=str(out/'private-cache'),XDG_CONFIG_HOME=str(out/'private-config'),TMPDIR=str(out/'private-temp'),NUMBA_CACHE_DIR=str(out/'private-cache/numba'),PYTHONDONTWRITEBYTECODE='1',PYTHONHASHSEED='0',PATH=str(prefix/'bin')+':'+env.get('PATH',''),LANG='C.UTF-8',LC_ALL='C.UTF-8',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1')
    def members(group):
        result=[]
        for path in Path('/proc').iterdir():
            if not path.name.isdigit():continue
            try:
                raw=(path/'stat').read_text();fields=raw[raw.rindex(')')+2:].split()
                if int(fields[2])==group and fields[0]!='Z':result.append(int(path.name))
            except (OSError,ValueError,IndexError):pass
        return result
    def execute(name,command,timeout=1200,seed='0',expected=0):
        command=list(map(str,command));record=dict(name=name,command=command,seed=seed,expected_exit=expected);child=None;started=time.monotonic()
        try:
            with (out/(name+'.stdout')).open('xb') as first,(out/(name+'.stderr')).open('xb') as second:
                child=subprocess.Popen(command,cwd=out,env=dict(env,PYTHONHASHSEED=seed),stdin=subprocess.DEVNULL,stdout=first,stderr=second,start_new_session=True);record['owned_process_group']=child.pid;save(out/(name+'.STARTED.json'),record);code=child.wait(timeout=timeout)
            record['exit_code']=code;assert code==expected,name+' unexpected exit; inspect retained logs'
        finally:
            if child is not None:
                for sig in [signal.SIGTERM,signal.SIGKILL]:
                    if not members(child.pid):break
                    try:os.killpg(child.pid,sig)
                    except ProcessLookupError:pass
                    deadline=time.monotonic()+3
                    while members(child.pid) and time.monotonic()<deadline:time.sleep(.05)
                child.wait(timeout=10);record['owned_live_after_cleanup']=members(child.pid);assert not record['owned_live_after_cleanup']
            record['elapsed_seconds']=time.monotonic()-started;save(out/(name+'.process.json'),record);state['processes'].append(record)
    try:
        before=inventory(prefix,progress=out/'runtime-before.progress.json');save(out/'runtime-before.json',before);assert before==json.loads((setup/'runtime-after.json').read_bytes()),'Existing runtime changed';direct=out/'direct';direct.mkdir();execute('direct',[python,'-B','-P','-s',ROOT/'tools/literature/cellphonedb_direct_reference_worker.py',direct,out/'request.json',old]);candidate=out/'candidate';candidate.mkdir()
        for name in ['worker.py','supervisor.py','codec.py']:(candidate/name).write_bytes(((new if (new/name).exists() else old)/name).read_bytes())
        (candidate/'request.json').write_bytes((out/'request.json').read_bytes());(candidate/'runtime.original.json').write_bytes((setup/'COMPLETED.json').read_bytes());execute('candidate',[python,'-I','-B',candidate/'supervisor.py','--runtime',candidate/'runtime.original.json','--out',candidate,'--worker',candidate/'worker.py','--request',candidate/'request.json','--timeout','1200','--maximum-output-bytes','2147483648','--maximum-files','100000'],1260);completed=json.loads((candidate/'COMPLETED.json').read_bytes());assert completed['status']=='PASS_COMPLETE_ORIGINAL_CELLPHONEDB_WORKFLOWS_ONLY' and len(completed['cases'])==19
        execute('compare-data-only',[python,'-I','-B',ROOT/'tools/literature/compare_cellphonedb_safe_worker.py',out],120);execute('seeded-sampling',[python,'-I','-B',ROOT/'tools/literature/validate_cellphonedb_seeded_sampling.py',out,old],120)
        # Startup-only launchers contain precisely the new gate and stdlib imports; no scientific import is executed.
        worker=(new/'worker.py').read_text(encoding='utf8');marker='sys.path.insert(0,str(Path(__file__).resolve().parent))';startup=worker[:worker.index(marker)];probe=out/'startup-only.py';probe.write_text(startup,encoding='utf8',newline='\n');receipts=[]
        for seed in [0,0,123,123,4294967295,4294967295]:
            home=out/('seed-'+str(seed)+'-'+str(len(receipts)));home.mkdir();value=dict(request,python_hash_seed=seed);save(home/'request.json',value);execute(home.name,[python,'-B','-P','-s',probe,home,home/'request.json'],30,seed=str(seed));receipt=json.loads((home/'python-startup.json').read_bytes());assert receipt['requested_seed']==seed and receipt['environment_seed']==str(seed);receipts.append(dict(path=str(home/'python-startup.json'),receipt=receipt))
        for i in [0,2,4]:assert receipts[i]['receipt']==receipts[i+1]['receipt']
        assert receipts[0]['receipt']==json.loads((candidate/'python-startup.json').read_bytes());assert len({json.dumps(receipts[i]['receipt']['hashes']) for i in [0,2,4]})==3
        negative=out/'isolated-seed-rejected';negative.mkdir();save(negative/'request.json',request);execute(negative.name,[python,'-I','-B',probe,negative,negative/'request.json'],30,expected=1);assert not (negative/'python-startup.json').exists()
        state.update(status='PASS_FRESH_EFFECTIVE_SEED_CELLPHONEDB_CONTROLS_ONLY',cases=19,serial_complete_byte_and_rng_comparisons=17,entropy_seeded_full_method_sampling_selection_checks=1,parallel_real_null_checks=1,startup_receipts=receipts,old_isolated_launch_rejected_before_scientific_imports=True,pickle_deserialized=False,original_namespace_patched=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    finally:
        if before is not None:
            after=inventory(prefix,progress=out/'runtime-after.progress.json');save(out/'runtime-after.json',after);state['runtime_unchanged']=before==after
            if before!=after:state.update(status='FAIL2_EFFECTIVE_SEED_RUNTIME_CHANGED',error='Bound dependency prefix changed')
        state['source_inputs_unchanged']=all(pin(Path(name))==want for name,want in pins.items());state['source_pins']=pins
        if not state['source_inputs_unchanged']:state.update(status='FAIL2_EFFECTIVE_SEED_SOURCE_CHANGED',error='Bound source/input changed')
        save(out/'COMPLETED.json',state)
    print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full source/control assertions')
    raise SystemExit(main())
