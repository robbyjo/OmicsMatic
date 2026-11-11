# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Supervise original CPU arithmetic and complete coordinate workflows, binding both complete runtime trees."""
import argparse,json,os,signal,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save
from inventory_hail_runtime_linux import inventory
from prepare_scfates_reference_linux import members,Unavailable

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--setup',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--timeout',type=int,default=1800);a=p.parse_args();out=a.out.absolute();assert out==out.resolve() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime') and not out.exists() and 1<=a.timeout<=7200;out.mkdir(parents=True);state=dict(status='FAIL2_SCFATES_ORIGINAL_REFERENCE_FAMILY',implementation_credit=False,whole_goal_complete=False,analysis_admitted=False,processes=[]);before={};binding=None
 try:
  if not (a.setup/'COMPLETED.json').is_file():raise Unavailable('Exact admitted original scFates runtime receipt is missing; skip analysis and prepare permitted dependencies on NEW paths')
  binding=json.loads((a.setup/'COMPLETED.json').read_bytes())
  if binding['status'].startswith('SKIP3'):binding=None;raise Unavailable('Runtime setup was skipped; resolve its named permission/dependency cause before dependent analysis')
  assert binding['status']=='PASS_COMPLETE_PRIVATE_ORIGINAL_SCFATES_SIMPLEPPT_IMPORTS_ONLY' and binding['shared_runtime_unchanged'] and binding['original_scientific_sources_unchanged'];pins={str(p):pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/collect_scfates_reference.py',ROOT/'tools/literature/prepare_scfates_reference_linux.py',a.setup/'COMPLETED.json',a.setup/'dependency-locks.json']};roots={'shared':Path(binding['shared_prefix']),'dependencies':Path(binding['dependencies'])}
  for label,root in roots.items():
   tree=inventory(root,progress=out/(label+'-before.progress.json'));save(out/(label+'-before.json'),tree);expected=a.setup/('shared-after.json' if label=='shared' else 'dependencies-after.json');assert tree==json.loads(expected.read_bytes()),'Bound runtime tree changed: '+label;before[label]=tree
  env=dict(os.environ)
  for key in list(env):
   if key.startswith(('PYTHON','CONDA_','PIP_','JAX_','R_')) or key in ['LD_PRELOAD','LD_LIBRARY_PATH']:env.pop(key,None)
  for name in ['private-home','private-cache','private-config','private-temp']:(out/name).mkdir()
  env.update(HOME=str(out/'private-home'),TMPDIR=str(out/'private-temp'),XDG_CACHE_HOME=str(out/'private-cache'),XDG_CONFIG_HOME=str(out/'private-config'),MPLCONFIGDIR=str(out/'private-config/matplotlib'),NUMBA_CACHE_DIR=str(out/'private-cache/numba'),PYTHONDONTWRITEBYTECODE='1',PYTHONHASHSEED='0',JAX_PLATFORMS='cpu',MPLBACKEND='Agg',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1',NUMBA_NUM_THREADS='1',LANG='C.UTF-8',LC_ALL='C.UTF-8');command=[binding['python'],'-B','-P','-s',str(ROOT/'tools/literature/collect_scfates_reference.py'),'--dependencies',binding['dependencies'],'--out',str(out/'original')];record=dict(command=command);child=None;started=time.monotonic()
  try:
   with (out/'original.stdout').open('xb') as first,(out/'original.stderr').open('xb') as second:child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,start_new_session=True);record['owned_process_group']=child.pid;save(out/'original.STARTED.json',record);code=child.wait(timeout=a.timeout)
   record['exit_code']=code;assert code==0,'Original family failed; retain all full original logs/provisional cases';report=json.loads((out/'original/COMPLETED.json').read_bytes());assert report['status']=='PASS_COMPLETE_ORIGINAL_SCFATES_CPU_ARITHMETIC_AND_WORKFLOWS_ONLY' and not report['namespace_patched'] and not report['user_pickle_loaded'];state['report']=report
  finally:
   if child:
    for sig in [signal.SIGTERM,signal.SIGKILL]:
     if not members(child.pid):break
     try:os.killpg(child.pid,sig)
     except ProcessLookupError:pass
     end=time.monotonic()+3
     while members(child.pid) and time.monotonic()<end:time.sleep(.05)
    child.wait(timeout=10);record['owned_live_after_cleanup']=members(child.pid);assert not record['owned_live_after_cleanup']
   record['elapsed_seconds']=time.monotonic()-started;save(out/'original.process.json',record);state['processes'].append(record)
  assert all(pin(Path(n))==v for n,v in pins.items());state.update(status='PASS_FULL_ORIGINAL_SCFATES_CPU_REFERENCE_FAMILY_ONLY',source_pins=pins,setup=pin(a.setup/'COMPLETED.json'))
 except Unavailable as error:state.update(status='SKIP3_SCFATES_ORIGINAL_RUNTIME_UNAVAILABLE',error=str(error))
 except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
 finally:
  unchanged=True
  if binding:
   for label,tree in before.items():after=inventory(roots[label],progress=out/(label+'-after.progress.json'));save(out/(label+'-after.json'),after);unchanged &= after==tree
  state['all_runtime_trees_unchanged']=unchanged
  if not unchanged:state.update(status='FAIL2_SCFATES_ORIGINAL_RUNTIME_CHANGED',error='Complete runtime tree changed; no family admitted')
  save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\nSKIP3 skips dependent references when exact permitted dependencies are missing or forbidden. Read setup warnings and resolve the named cause on NEW paths, otherwise retain the skip. FAIL2 admits no reference family: all earlier cases stay provisional. Preserve originals, logs and failed outputs, correct the named cause and retry NEW paths. Source diagnostics, NaN scores, zero assignment rows, not-converged messages and generated-version differences are retained, not repaired. Native arithmetic controls are not a complete Java tree fitter or public adapter. Neither bound version nor test settings reproduce an unreported paper edition or its full analysis.\n',encoding='utf8')
 print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable original/runtime integrity checks')
 raise SystemExit(main())
