# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Own an actual seed0 original-component reference process and recheck complete shared/overlay trees. Full tool remains unavailable."""
import argparse,json,os,signal,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save
from inventory_hail_runtime_linux import inventory


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.absolute();assert sys.platform=='linux' and out==out.resolve() and out.is_relative_to(ROOT/'build/literature-review/spyrrow-runtime') and not out.exists();out.mkdir(parents=True);setup=ROOT/'build/literature-review/spyrrow-runtime/component-reference-setup-1';receipt=json.loads((setup/'COMPLETED.json').read_bytes());assert receipt['status']=='PASS_SPYRROW_ORIGINAL_BASIC_COMPONENT_REFERENCE_ONLY' and receipt['shared_runtime_unchanged'];prefix=Path(receipt['shared_prefix']);overlay=Path(receipt['overlay']);source=ROOT/'build/literature-review/spyrrow-runtime/source-discovery-1/source';state=dict(status='FAIL2_SPYRROW_ORIGINAL_COMPONENT_REFERENCES',implementation_credit=False,whole_goal_complete=False);child=None;before=None;overlay_before=None;pins={str(p):pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/collect_spyrrow_communication_reference.py',setup/'COMPLETED.json',*source.rglob('*')] if p.is_file()}
    try:
        before=inventory(prefix,progress=out/'runtime-before.progress.json');save(out/'runtime-before.json',before);assert before==json.loads((setup/'runtime-after.json').read_bytes());overlay_before=inventory(overlay,progress=out/'overlay-before.progress.json');save(out/'overlay-before.json',overlay_before);assert overlay_before==json.loads((setup/'overlay.json').read_bytes());env=dict(os.environ)
        for key in list(env):
            if key.startswith(('PYTHON','CONDA_','PIP_')) or key in ['LD_PRELOAD','LD_LIBRARY_PATH']:env.pop(key,None)
        for name in ['private-home','private-temp','private-cache','private-config']:(out/name).mkdir()
        env.update(HOME=str(out/'private-home'),TMPDIR=str(out/'private-temp'),XDG_CACHE_HOME=str(out/'private-cache'),XDG_CONFIG_HOME=str(out/'private-config'),PYTHONHASHSEED='0',PYTHONDONTWRITEBYTECODE='1',MPLBACKEND='Agg',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1');command=[receipt['python'],'-B','-P','-s',str(ROOT/'tools/literature/collect_spyrrow_communication_reference.py'),'--out',str(out/'original'),'--overlay',str(overlay),'--source',str(source)];state['command']=command
        with (out/'original.stdout').open('xb') as first,(out/'original.stderr').open('xb') as second:
            child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,start_new_session=True);save(out/'STARTED.json',dict(pid=child.pid,owned_process_group=child.pid,command=command));code=child.wait(timeout=600)
        state['exit_code']=code;assert code==0,'Original component reference failed; inspect full diagnostics';original=json.loads((out/'original/COMPLETED.json').read_bytes());assert original['status']=='PASS_UNCHANGED_SPYRROW_COMMUNICATION_COMPONENT_REFERENCES_ONLY' and original['hash_seed']==0 and not original['pickle_file_loaded'] and not original['functions_or_code_objects_patched'];state.update(status='PASS_EXACT_SPYRROW_ORIGINAL_COMPONENT_REFERENCES_ONLY',cases=original['cases'],complete_numeric_cells=original['complete_numeric_cells'],original_errors=original['original_errors'],cn_module_import_error=original['cn_module_import_error'],hash_cases=original['hash_cases'],set_groups=original['set_groups'],largest_set_input=original['largest_set_input'],distance_cases=original['distance_cases'])
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    finally:
        if child is not None and child.poll() is None:
            os.killpg(child.pid,signal.SIGTERM)
            try:child.wait(timeout=10)
            except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait(timeout=10)
        state['owned_root_alive']=child is not None and child.poll() is None
        if before is not None:
            after=inventory(prefix,progress=out/'runtime-after.progress.json');save(out/'runtime-after.json',after);state['shared_runtime_unchanged']=before==after
            if before!=after:state.update(status='FAIL2_SPYRROW_SHARED_RUNTIME_CHANGED',error='Shared runtime changed')
        if overlay_before is not None:
            overlay_after=inventory(overlay,progress=out/'overlay-after.progress.json');save(out/'overlay-after.json',overlay_after);state['overlay_unchanged']=overlay_before==overlay_after
            if overlay_before!=overlay_after:state.update(status='FAIL2_SPYRROW_OVERLAY_CHANGED',error='Private component import overlay changed')
        state['source_inputs_unchanged']=all(pin(Path(n))==want for n,want in pins.items());state['source_pins']=pins
        if not state['source_inputs_unchanged']:state.update(status='FAIL2_SPYRROW_REFERENCE_SOURCE_CHANGED',error='Bound source changed')
        save(out/'COMPLETED.json',state)
    print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source/runtime checks')
    raise SystemExit(main())
