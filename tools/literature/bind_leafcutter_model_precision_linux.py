# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind retained original binary fits to lossless 17-significant-digit exports; no model refitting or scientific tolerance change."""
import argparse,json,os,signal,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save,group_members
from inventory_hail_runtime_linux import inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reference',required=True,type=Path);p.add_argument('--runtime',required=True,type=Path);p.add_argument('--library',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert sys.platform=='linux' and out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True)
    state=dict(status='SKIP3_LOSSLESS_ORIGINAL_MODEL_BINDING_UNAVAILABLE',implementation_credit=False,whole_goal_complete=False,fits_refitted=False,analysis_admitted=False);sources={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),ROOT/'tools/literature/reference/leafcutter_model_precision.R',ROOT/'tools/literature/inventory_hail_runtime_linux.py',ROOT/'tools/literature/setup_hail_reference_linux.py']}
    for name in sources:target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    before={};roots={};child=None;code=None;process={};started=time.monotonic()
    try:
        original=json.loads((a.reference/'COMPLETED.json').read_bytes());runtime=json.loads((a.runtime/'COMPLETED.json').read_bytes());binding=json.loads((a.library/'COMPLETED.json').read_bytes())
        assert original['status']=='PASS_COMPLETE_ORIGINAL_LEAFCUTTER_MODEL_AND_DIFFERENTIAL_CONTROLS_ONLY' and original['runtime_unchanged'] and original['library_unchanged']
        assert all(pin(ROOT/n)==want for n,want in {**original['source_pins'],**runtime['source_pins'],**binding['source_pins']}.items());prefix=Path(runtime['runtime_environment']);library=Path(binding['R_library']);roots=dict(runtime=prefix,library=library,reference=a.reference.resolve())
        for name,path in roots.items():before[name]=inventory(path,progress=out/(name+'-before.progress.json'));save(out/(name+'-before.json'),before[name])
        assert before['runtime']==json.loads((a.runtime/'runtime-before.json').read_bytes()) and before['library']==json.loads((a.library/'library-inventory.json').read_bytes());(out/'cases.json').write_bytes((a.reference/'cases.json').read_bytes())
        for name in ['private-temp','private-config','private-cache']:(out/name).mkdir()
        env=dict(os.environ)
        for key in list(env):
            if key.startswith(('R_','PYTHON','CONDA_')) or key in ['LD_LIBRARY_PATH','LD_PRELOAD']:env.pop(key,None)
        env.update(PATH=str(prefix/'bin')+':'+env.get('PATH',''),CONDA_PREFIX=str(prefix),R_LIBS_USER=str(library),R_LIBS_SITE=str(prefix/'lib/R/library'),R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',TMPDIR=str(out/'private-temp'),TMP=str(out/'private-temp'),TEMP=str(out/'private-temp'),XDG_CONFIG_HOME=str(out/'private-config'),XDG_CACHE_HOME=str(out/'private-cache'),LANG='C.UTF-8',LC_ALL='C.UTF-8',OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1')
        command=list(map(str,[prefix/'bin/Rscript','--vanilla',ROOT/'tools/literature/reference/leafcutter_model_precision.R',library,a.reference.resolve(),out]));process=dict(command=command,started_at=time.time());save(out/'original.STARTED.json',process);state['status']='FAIL2_LOSSLESS_ORIGINAL_MODEL_BINDING_INCOMPLETE'
        with (out/'original.stdout').open('xb') as stdout,(out/'original.stderr').open('xb') as stderr:
            child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True);process['owned_process_group']=child.pid;code=child.wait(timeout=180)
        assert code==0,'Lossless fixed-fit binding failed; inspect retained stderr';report=json.loads((out/'ORIGINAL-COMPLETED.json').read_bytes());assert report['status']=='PASS_LOSSLESS_ORIGINAL_LEAFCUTTER_MODEL_CONTROL_BINDING_ONLY' and report['fits_refitted'] is False and report['lossless_numeric_roundtrip'] is True
        state.update(status=report['status'],source_pins=sources,original_namespace_patched=False,original_reference=dict(path=a.reference.resolve().relative_to(ROOT).as_posix(),**pin(a.reference/'COMPLETED.json')),runtime_manifest=pin(a.runtime/'COMPLETED.json'),library_manifest=pin(a.library/'COMPLETED.json'),cases=original['cases'],lossless_numeric_roundtrip=True,fixed_parameter_Stan_objectives_repeated=16)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    finally:
        if child is not None:
            process['owned_before_cleanup']=group_members(child.pid)
            for sig,seconds in [(signal.SIGTERM,3),(signal.SIGKILL,3)]:
                if not any(r['state']!='Z' for r in group_members(child.pid)):break
                try:os.killpg(child.pid,sig)
                except ProcessLookupError:pass
                deadline=time.monotonic()+seconds
                while any(r['state']!='Z' for r in group_members(child.pid)) and time.monotonic()<deadline:time.sleep(.05)
            child.wait(timeout=10);process['owned_live_after_cleanup']=[r for r in group_members(child.pid) if r['state']!='Z']
            if process['owned_live_after_cleanup']:state.update(status='FAIL2_OWNED_PRECISION_EXPORT_PROCESS_REMAINS_LIVE',error='Owned process cleanup incomplete')
        process.update(exit_code=code,elapsed_seconds=time.monotonic()-started);save(out/'original.process.json',process)
        for name,was in before.items():
            after=inventory(roots[name],progress=out/(name+'-after.progress.json'));save(out/(name+'-after.json'),after);state[name+'_unchanged']=was==after
            if was!=after:state.update(status='FAIL2_BOUND_ORIGINAL_REFERENCE_OR_RUNTIME_CHANGED',error='Complete '+name+' tree changed during fixed-fit export')
        assert all(pin(ROOT/name)==want for name,want in sources.items());state['required_action']='Required action: retain the original binary fits, rounded JSON, failed sensitive-tail comparison and this binding attempt. Correct the named precision/source/runtime/process cause and repeat NEW paths. Do not refit unchanged models, widen tolerances, repair input/parameter states or overwrite original outputs. Missing/forbidden required software keeps dependent analyses skipped; full workflow remains unadmitted.';save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Lossless fixed-fit export only; no model refitting.')+'\n\n'+state['required_action']+'\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact original fit/runtime checks')
    raise SystemExit(main())
