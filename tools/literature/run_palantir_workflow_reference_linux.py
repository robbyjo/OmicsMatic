# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Supervise complete original-component references through both exact full-package imports."""
import argparse,json,os,signal,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save
from inventory_hail_runtime_linux import inventory
from prepare_palantir_reference_linux import members,Unavailable

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--setup',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.absolute();assert out==out.resolve() and out.is_relative_to(ROOT/'build/literature-review/palantir-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_PALANTIR_FULL_WORKFLOW_ORIGINAL_REFERENCES',implementation_credit=False,whole_goal_complete=False,analysis_admitted=False,processes=[]);before={};binding=None
    try:
        if not (a.setup/'COMPLETED.json').is_file():raise Unavailable('Required complete original Palantir setup receipt is missing; skip dependent references and prepare permitted dependencies on NEW paths')
        binding=json.loads((a.setup/'COMPLETED.json').read_bytes())
        if binding['status'].startswith('SKIP3'):binding=None;raise Unavailable('Required original Palantir runtime preparation was skipped; retain SKIP3 and resolve the named installation/download permission cause before dependent analysis')
        assert binding['status']=='PASS_COMPLETE_PRIVATE_ORIGINAL_PALANTIR_IMPORTS_ONLY' and binding['shared_runtime_unchanged'] and set(binding['editions'])=={'1.4.4','1.4.6'};pins={str(p):pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/collect_palantir_workflow_reference.py',ROOT/'tools/literature/prepare_palantir_reference_linux.py',a.setup/'COMPLETED.json',a.setup/'dependency-locks.json']};roots={'shared':Path(binding['shared_prefix']),'dependencies':Path(binding['dependencies']),**{'edition-'+v:Path(d['path']) for v,d in binding['editions'].items()}}
        for label,root in roots.items():
            tree=inventory(root,progress=out/(label+'-before.progress.json'));save(out/(label+'-before.json'),tree);expected=a.setup/('shared-after.json' if label=='shared' else 'dependencies.json' if label=='dependencies' else label+'.json');assert tree==json.loads(expected.read_bytes()),'Admitted runtime tree changed: '+label;before[label]=tree
        env=dict(os.environ)
        for key in list(env):
            if key.startswith(('PYTHON','CONDA_','PIP_','JAX_','R_')) or key in ['LD_PRELOAD','LD_LIBRARY_PATH']:env.pop(key,None)
        for name in ['private-home','private-cache','private-config','private-temp']:(out/name).mkdir()
        env.update(HOME=str(out/'private-home'),TMPDIR=str(out/'private-temp'),XDG_CACHE_HOME=str(out/'private-cache'),XDG_CONFIG_HOME=str(out/'private-config'),MPLCONFIGDIR=str(out/'private-config/matplotlib'),NUMBA_CACHE_DIR=str(out/'private-cache/numba'),PYTHONDONTWRITEBYTECODE='1',PYTHONHASHSEED='0',JAX_PLATFORMS='cpu',MPLBACKEND='Agg',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1',LANG='C.UTF-8',LC_ALL='C.UTF-8');reports={}
        for edition,d in binding['editions'].items():
            command=[binding['python'],'-B','-P','-s',str(ROOT/'tools/literature/collect_palantir_workflow_reference.py'),'--edition',edition,'--dependencies',binding['dependencies'],'--package',d['path'],'--out',str(out/edition)];child=None;record=dict(edition=edition,command=command);started=time.monotonic()
            try:
                with (out/(edition+'.stdout')).open('xb') as first,(out/(edition+'.stderr')).open('xb') as second:
                    child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,start_new_session=True);record['owned_process_group']=child.pid;save(out/(edition+'.STARTED.json'),record);code=child.wait(timeout=300)
                record['exit_code']=code;assert code==0,'Original references failed; retain full '+edition+' logs';report=json.loads((out/edition/'COMPLETED.json').read_bytes());assert report['status']=='PASS_COMPLETE_ORIGINAL_PALANTIR_WORKFLOW_SOURCE_DISCOVERY_ONLY' and report['edition']==edition and not report['namespace_functions_patched'] and not report['pickle_loaded'];reports[edition]=report
            finally:
                if child is not None:
                    for sig in [signal.SIGTERM,signal.SIGKILL]:
                        if not members(child.pid):break
                        try:os.killpg(child.pid,sig)
                        except ProcessLookupError:pass
                        end=time.monotonic()+3
                        while members(child.pid) and time.monotonic()<end:time.sleep(.05)
                    child.wait(timeout=10);record['owned_live_after_cleanup']=members(child.pid);assert not record['owned_live_after_cleanup']
                record['elapsed_seconds']=time.monotonic()-started;save(out/(edition+'.process.json'),record);state['processes'].append(record)
        assert all(pin(Path(n))==v for n,v in pins.items());state.update(status='PASS_TWO_FULL_ORIGINAL_PALANTIR_WORKFLOW_DISCOVERY_FAMILIES_ONLY',source_pins=pins,setup=pin(a.setup/'COMPLETED.json'),reports=reports)
    except Unavailable as failure:state.update(status='SKIP3_PALANTIR_ORIGINAL_REFERENCE_RUNTIME_UNAVAILABLE',error=str(failure))
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        unchanged=True
        if binding:
            for label,tree in before.items():
                after=inventory(roots[label],progress=out/(label+'-after.progress.json'));save(out/(label+'-after.json'),after);unchanged &= after==tree
        state['all_runtime_trees_unchanged']=unchanged
        if not unchanged:state.update(status='FAIL2_PALANTIR_REFERENCE_RUNTIME_CHANGED',error='Shared/dependency/edition runtime changed')
        save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\nSKIP3 skips all dependent original-reference work when the required runtime is missing or installation/download was forbidden. Read the setup error and warnings; prepare the exact permitted software on NEW paths, otherwise keep analysis skipped. FAIL2 admits no component reference family; preserve all originals/provisionals and logs, correct the named runtime/source/numeric cause, and retry NEW paths. These are software controls, not paper or biological analyses.\n',encoding='utf8')
    print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original/runtime checks')
    raise SystemExit(main())
