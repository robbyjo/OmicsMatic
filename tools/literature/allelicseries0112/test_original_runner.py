#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run all16 original exports through the public adapter and compare complete independent original objects."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,json,os,shutil,subprocess,sys,time,traceback
HERE=Path(__file__).resolve().parent

def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--provider',required=True,type=Path);p.add_argument('--reference',required=True,type=Path);p.add_argument('--historical-reference',required=True,type=Path);p.add_argument('--jobs',type=int,choices=range(1,5),default=1);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  for protected in [HERE,a.provider.resolve(),a.reference.resolve(),a.historical_reference.resolve()]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected evidence/provider')
  out.mkdir(parents=True,exist_ok=False);owned=True
  ready=json.loads((a.provider/'READY.json').read_bytes());profile=json.loads((HERE/'source-profile.json').read_bytes());before={n:pin(HERE/n) for n in ['run.py','OriginalRun.R','CompareOriginal.R','MakeRefusalInputs.R','test_original_runner.py','source-profile.json']}
  prefix=Path(ready['r_prefix']);env={k:v for k,v in os.environ.items() if k not in ['R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_DEFAULT_PACKAGES','R_PROFILE_USER','R_ENVIRON_USER']}
  env.update(PATH=str(prefix/'bin')+':/usr/bin:/bin',R_HOME=str(prefix/'lib/R'),LD_LIBRARY_PATH=str(prefix/'lib'),R_PROFILE_USER='/dev/null',R_ENVIRON_USER='/dev/null',LC_ALL='C.UTF-8',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
  snapshot=out/'source-snapshot';snapshot.mkdir()
  for n in before:shutil.copyfile(HERE/n,snapshot/n)
  save(out/'BEFORE.json',dict(sources=before,provider_ready=pin(a.provider/'READY.json'),independent_reference=pin(a.reference/'complete-namespace-reference.rds')))
  def run(label,command,expected=0):
   command=list(map(str,command));start=time.time()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    proc=subprocess.Popen(command,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/(label+'.live.json'),dict(label=label,pid=proc.pid,command=command,started_unix=start));code=proc.wait()
   row=dict(label=label,command=command,exit_code=code,expected_exit=expected,elapsed_seconds=time.time()-start);calls.append(row);save(out/(label+'.process.json'),row)
   if code!=expected:raise ValueError('Actual public original runner exit differs at '+label)
   print(label,'exit',code,flush=True)
  outputs=out/'actual';outputs.mkdir()
  def command(name,args,outpath,allow=True):
   cmd=[sys.executable,HERE/'run.py','--provider',a.provider,'--function',name,'--arguments-rds',args,'--seed','88201','--out',outpath]
   return cmd+(['--allow-analysis'] if allow else [])
  def api_case(name):
   run(name,command(name,a.reference/'namespace-requests'/(name+'.rds'),outputs/name))
   result=json.loads((outputs/name/'COMPLETED.json').read_bytes());assert result['analysis_admitted'] and not result['paper_build_equivalence_proven'] and not result['paper_reproduced'] and result['original_warning_count']==0 and not result['warning_review_required']
   manifest=json.loads((outputs/name/'MANIFEST.json').read_bytes())['files']
   assert set(manifest)=={p.relative_to(outputs/name).as_posix() for p in (outputs/name).rglob('*') if p.is_file() and p.name!='MANIFEST.json'}
   for n,wanted in manifest.items():assert pin(outputs/name/n)==wanted,n
  with concurrent.futures.ThreadPoolExecutor(max_workers=a.jobs) as executor:list(executor.map(api_case,profile['function_exports']))
  run('complete-independent-RDS-comparison',[ready['rscript'],'--vanilla',HERE/'CompareOriginal.R',ready['library'],ready['base_library'],a.reference,outputs])
  sample=a.reference/'namespace-requests/COASTSS.rds'
  run('policy-skip',command('COASTSS',sample,out/'policy-skip',False),3)
  run('unknown-export',command('not_an_original_export',sample,out/'unknown-export'),2)
  # The initial fixture omitted Counts' mandatory n_anno. Keep and replay the actual original error.
  run('original-missing-argument',command('Counts',a.historical_reference/'namespace-requests/Counts.rds',out/'original-missing-argument'),2)
  assert (out/'original-missing-argument/complete-original-return.rds').is_file() and (out/'original-missing-argument/original-error.txt').is_file()
  protected=a.provider/'refused-output-child'
  run('protected-provider-output',command('COASTSS',sample,protected),2);assert not protected.exists()
  run('create-explicit-refusal-inputs',[ready['rscript'],'--vanilla',HERE/'MakeRefusalInputs.R',out])
  run('original-warning-visible',command('ASBTSS',out/'original-warning-arguments.rds',out/'original-warning-visible'))
  warning=json.loads((out/'original-warning-visible/COMPLETED.json').read_bytes());assert warning['original_warning_count']==1 and warning['warning_review_required']
  assert 'WARNING:' in (out/'original-warning-visible.stdout').read_text() and 'If LD is not provided' in (out/'original-warning-visible/WARNINGS.md').read_text()
  run('original-nonfinite-return',command('GenomicControl',out/'original-nonfinite-arguments.rds',out/'original-nonfinite-return'),2)
  assert (out/'original-nonfinite-return/RETURN_STATUS.txt').read_text().splitlines()[0]=='ORIGINAL_NONFINITE_RETURN_NO_ANALYSIS_ADMISSION'
  assert (out/'original-nonfinite-return/original-value.rds').is_file()
  run('invalid-argument-object',command('COASTSS',out/'invalid-arguments.rds',out/'invalid-argument-object'),2)
  absent=out/'absent-provider'
  run('missing-provider',[sys.executable,HERE/'run.py','--provider',absent,'--function','COASTSS','--arguments-rds',sample,'--allow-analysis','--out',out/'missing-provider'],3)
  assert all(pin(HERE/n)==wanted for n,wanted in before.items())
  result=dict(status='PASS_ORIGINAL_ALLELICSERIES0112_FULL_EXPORT_RUNNER_AND_INDEPENDENT_OBJECTS',function_exports=16,complete_original_object_comparisons=16,request_and_warning_populations_compared=True,refusal_or_skip_controls=7,original_warning_visibility_controls=1,independent_process_workers=a.jobs,calls=calls,sources=before,original_source_modified=False,paper_build_equivalence_proven=False,new_tool_credit=0,whole_goal_complete=False);save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;v=dict(status='SKIP3_ORIGINAL_RUNNER_CONTROLS_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_RUNNER_CONTROLS',cause=repr(e),traceback=traceback.format_exc(),calls=calls,analysis_admitted=False)
  if owned:save(out/'FAILURE.json',v)
  print(v['status'],repr(e),flush=True);return code
if __name__=='__main__':raise SystemExit(main())
