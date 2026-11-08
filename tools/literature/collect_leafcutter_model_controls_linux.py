# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run complete unchanged original Stan objectives, optimized ANOVAs and differential-splicing controls in a bound private runtime."""
import argparse, json, os, signal, subprocess, sys, time, traceback
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT, pin, save, group_members
from inventory_hail_runtime_linux import inventory

ACTION = 'Required action: admit no result from an incomplete model-control batch. Preserve original fits, parameters, input axes, filter dispositions, warnings, errors and process receipts. Correct the named runtime/model/numeric/optimizer/process cause and retry NEW paths; never replace original functions, omit failed clusters or refresh frozen scientific hashes. Missing or forbidden exact software keeps dependent analysis skipped (SKIP3). Complete workflow, paper review and full LeafCutter credit remain separate.'


def fixtures():
    y = [[10,20],[12,18],[9,21],[11,19],[20,10],[18,12],[22,8],[19,11]]
    null = [[1] for _ in y]; full = [[1,int(i>=4)] for i in range(8)]
    grids = []
    for robust in [False, True]:
        for k in [2,3]:
            points = []
            for i, scale in enumerate([0.0, 1.7, -3.1, 12.0]):
                points.append(dict(id='point-'+str(i), beta_raw=[[1/k]*k, [v/sum(range(1,k+1)) for v in range(1,k+1)]], beta_scale=[.8,scale], concentration=[.2,20,500][:k], outlier_probability=[.001,.05,.3,.9][i]))
            grids.append(dict(id='grid-'+('robust' if robust else 'ordinary')+'-'+str(k), robust=robust, counts=[r if k==2 else [r[0],r[1],3+i%4] for i,r in enumerate(y)], design=full, shape=1.0001, rate=.0001, outlier_a=1.01, outlier_b=100.0, points=points, seed=4711))
    fits = [dict(id='fit-'+name, counts=y, full=full, null=null, introns=['intron-B','intron-A'], full_names=['intercept','group'], null_names=['intercept'], robust=robust, init=init, seed=4711) for name,robust,init in [('ordinary-smart',False,'smart'),('robust-smart',True,'smart'),('ordinary-random',False,'random')]]
    y3 = [[8+i%3,17-i%3,5+i%2] if i<6 else [17-i%3,7+i%3,6+i%2] for i in range(12)]
    x = [int(i>=6) for i in range(12)]; conf = [(i%3)-1 for i in range(12)]
    fits.append(dict(id='fit-three-introns-covariate', counts=y3, full=[[1,x[i],conf[i]] for i in range(12)], null=[[1,conf[i]] for i in range(12)], introns=['intron-Z','intron-X','intron-Y'], full_names=['intercept','group','covariate'], null_names=['intercept','covariate'], robust=False, init='smart', seed=4711))
    rows = []; ids = []; statuses = {}
    patterns = {
        1: [[10+i%3 if i<6 else 20-i%3 for i in range(12)], [20-i%3 if i<6 else 10+i%3 for i in range(12)]],
        2: list(map(list,zip(*y3))),
        3: [[20]*12],
        4: [[0]*12,[0]*12],
        5: [[1]*12,[1]*12],
        6: [[30]*12,[1]+[0]*11],
        7: [[15]*6+[1]*6,[15]*6+[1]*6],
        8: [[5]*12 for _ in range(4)],
        9: [[10+i%3 if i<6 else 20-i%3 for i in range(12)], [20-i%3 if i<6 else 10+i%3 for i in range(12)], [1]+[0]*11],
        10: [[10]+[0]*11,[20]+[0]*11],
    }
    for cluster, values in patterns.items():
        for j, row in enumerate(values): ids.append('chr1:'+str(1000+cluster*100+j*10)+':'+str(2000+cluster*100)+':clu_'+str(cluster)+'_+'); rows.append(row)
    for cluster in sorted(patterns, key=lambda c:'chr1:clu_'+str(c)+'_+'):
        statuses['chr1:clu_'+str(cluster)+'_+'] = {1:'Success',2:'Success',3:'<=1 junction in cluster',4:'<=1 sample with coverage>0',5:'<=1 sample with coverage>min_coverage',6:'<2 introns used in >=min_samples_per_intron samples',7:'Not enough valid samples',8:'Too many introns in cluster',9:'Success',10:'<=1 sample with coverage>0'}[cluster]
    differential = [dict(id='differential-'+name, counts=rows, introns=ids, samples=['S'+str(12-i) for i in range(12)], group=x, confounders=values, expected_statuses=statuses, seed=4711) for name, values in [('no-covariates',None),('covariate-and-constant',[[conf[i],7] for i in range(12)])]]
    return dict(grids=grids, fits=fits, differential=differential)


def main():
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('--runtime',required=True,type=Path); p.add_argument('--library',required=True,type=Path); p.add_argument('--out',required=True,type=Path); a=p.parse_args()
    out=a.out.resolve(); assert sys.platform=='linux' and out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists(); out.mkdir(parents=True)
    state=dict(status='SKIP3_ORIGINAL_LEAFCUTTER_MODEL_RUNTIME_UNAVAILABLE',implementation_credit=False,whole_goal_complete=False,analysis_admitted=False)
    sources={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(), ROOT/'tools/literature/reference/leafcutter_model_controls.R', ROOT/'tools/literature/setup_hail_reference_linux.py', ROOT/'tools/literature/inventory_hail_runtime_linux.py']}
    for name in sources:
        target=out/'source-snapshot'/name; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes((ROOT/name).read_bytes())
    prefix=None; library=None; before=None; lib_before=None; child=None; code=None; process={}; started=time.monotonic()
    try:
        runtime=json.loads((a.runtime/'COMPLETED.json').read_bytes()); binding=json.loads((a.library/'COMPLETED.json').read_bytes())
        assert runtime['status']=='PASS_PRIVATE_LEAFCUTTER_DEPENDENCY_CANDIDATE_ONLY' and binding['status']=='PASS_ORIGINAL_LEAFCUTTER_PRIVATE_R_LIBRARY_AND_LITERAL_FIT_ONLY' and binding['dependency_runtime_unchanged'] is True and binding['namespace_patched'] is False
        assert all(pin(ROOT/n)==want for n,want in {**runtime['source_pins'],**binding['source_pins']}.items())
        prefix=Path(runtime['runtime_environment']); library=Path(binding['R_library'])
        before=inventory(prefix,progress=out/'runtime-before.progress.json'); save(out/'runtime-before.json',before); assert before==json.loads((a.runtime/'runtime-before.json').read_bytes())
        lib_before=inventory(library,progress=out/'library-before.progress.json'); save(out/'library-before.json',lib_before); assert lib_before==json.loads((a.library/'library-inventory.json').read_bytes())
        request=fixtures(); save(out/'cases.json',request)
        for name in ['private-temp','private-config','private-cache']: (out/name).mkdir()
        env=dict(os.environ)
        for key in list(env):
            if key.startswith(('R_','PYTHON','CONDA_')) or key in ['LD_LIBRARY_PATH','LD_PRELOAD']: env.pop(key,None)
        env.update(PATH=str(prefix/'bin')+':'+env.get('PATH',''), CONDA_PREFIX=str(prefix), R_LIBS_USER=str(library), R_LIBS_SITE=str(prefix/'lib/R/library'), R_ENVIRON_USER='/dev/null', R_PROFILE_USER='/dev/null', TMPDIR=str(out/'private-temp'), TMP=str(out/'private-temp'), TEMP=str(out/'private-temp'), XDG_CONFIG_HOME=str(out/'private-config'), XDG_CACHE_HOME=str(out/'private-cache'), LANG='C.UTF-8',LC_ALL='C.UTF-8',OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1')
        command=list(map(str,[prefix/'bin/Rscript','--vanilla',ROOT/'tools/literature/reference/leafcutter_model_controls.R',library,out/'cases.json',out])); process=dict(command=command,started_at=time.time()); save(out/'original.STARTED.json',process); state['status']='FAIL2_ORIGINAL_LEAFCUTTER_MODEL_CONTROLS_INCOMPLETE'
        with (out/'original.stdout').open('xb') as stdout,(out/'original.stderr').open('xb') as stderr:
            child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True); process['owned_process_group']=child.pid; code=child.wait(timeout=600)
        assert code==0,'Original model controls failed; inspect full retained original stderr/stdout'
        completed=json.loads((out/'ORIGINAL-COMPLETED.json').read_bytes()); assert completed['status']=='PASS_COMPLETE_ORIGINAL_LEAFCUTTER_MODEL_AND_DIFFERENTIAL_CONTROLS_ONLY' and len(completed['cases'])==10
        state.update(status='PASS_COMPLETE_ORIGINAL_LEAFCUTTER_MODEL_AND_DIFFERENTIAL_CONTROLS_ONLY',cases=completed['cases'],source_pins=sources,runtime_manifest=pin(a.runtime/'COMPLETED.json'),library_manifest=pin(a.library/'COMPLETED.json'),original_namespace_patched=False)
    except Exception as failure:
        state['error']=repr(failure); (out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    finally:
        if child is not None:
            process['owned_before_cleanup']=group_members(child.pid)
            for sig,seconds in [(signal.SIGTERM,3),(signal.SIGKILL,3)]:
                if not any(r['state']!='Z' for r in group_members(child.pid)): break
                try: os.killpg(child.pid,sig)
                except ProcessLookupError: pass
                deadline=time.monotonic()+seconds
                while any(r['state']!='Z' for r in group_members(child.pid)) and time.monotonic()<deadline: time.sleep(.05)
            child.wait(timeout=10); process['owned_live_after_cleanup']=[r for r in group_members(child.pid) if r['state']!='Z']
            if process['owned_live_after_cleanup']: state.update(status='FAIL2_OWNED_MODEL_PROCESSES_REMAIN_LIVE',error='Owned original process cleanup incomplete')
        process.update(exit_code=code,elapsed_seconds=time.monotonic()-started); save(out/'original.process.json',process)
        if before is not None:
            after=inventory(prefix,progress=out/'runtime-after.progress.json'); save(out/'runtime-after.json',after); state['runtime_unchanged']=before==after
            if before!=after: state.update(status='FAIL2_ORIGINAL_MODEL_DEPENDENCY_TREE_CHANGED',error='Complete dependency tree changed')
        if lib_before is not None:
            after=inventory(library,progress=out/'library-after.progress.json'); save(out/'library-after.json',after); state['library_unchanged']=lib_before==after
            if lib_before!=after: state.update(status='FAIL2_ORIGINAL_MODEL_LIBRARY_TREE_CHANGED',error='Complete original library tree changed')
        assert all(pin(ROOT/name)==want for name,want in sources.items()); state['required_action']=ACTION; save(out/'COMPLETED.json',state); (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Original model controls only; independent audits and the execution adapter remain separate.')+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('error','')); return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3') else 2


if __name__=='__main__':
    if not __debug__: raise RuntimeError('Never disable exact original model/runtime checks')
    raise SystemExit(main())
