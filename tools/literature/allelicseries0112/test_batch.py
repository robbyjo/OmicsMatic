#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare complete single-process original batches, failure populations, literal IDs and RNG streams."""
from pathlib import Path
import argparse,concurrent.futures,json,os,shutil,subprocess,sys,time,traceback
sys.dont_write_bytecode=True
from run import pin,save
HERE=Path(__file__).resolve().parent

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--provider',required=True,type=Path);p.add_argument('--reference',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  for protected in [HERE,a.provider.resolve(),a.reference.resolve()]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected sources/provider/reference')
  out.mkdir(parents=True,exist_ok=False);owned=True;ready=json.loads((a.provider/'READY.json').read_bytes());prefix=Path(ready['r_prefix'])
  names=['batch.py','run.py','OriginalRun.R','OriginalBatch.R','OriginalProtocol.R','BatchFixtures.R','BatchCompare.R','TableControl.R','test_batch.py','source-profile.json'];before={n:pin(HERE/n) for n in names};(out/'source-snapshot').mkdir()
  for n in names:shutil.copyfile(HERE/n,out/'source-snapshot'/n)
  save(out/'BEFORE.json',dict(sources=before,provider_ready=pin(a.provider/'READY.json'),reference_files={p.name:pin(p) for p in sorted(a.reference.iterdir()) if p.is_file()}))
  env={k:v for k,v in os.environ.items() if k not in ['R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_DEFAULT_PACKAGES','R_PROFILE_USER','R_ENVIRON_USER']};env.update(PATH=str(prefix/'bin')+':/usr/bin:/bin',R_HOME=str(prefix/'lib/R'),LD_LIBRARY_PATH=str(prefix/'lib'),R_PROFILE_USER='/dev/null',R_ENVIRON_USER='/dev/null',LC_ALL='C.UTF-8',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
  def run(label,command,expected=0):
   command=list(map(str,command));start=time.time()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    child=subprocess.Popen(command,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/(label+'.live.json'),dict(command=command,pid=child.pid,started_unix=start));code=child.wait()
   row=dict(label=label,command=command,exit_code=code,expected_exit=expected,elapsed_seconds=time.time()-start);calls.append(row);save(out/(label+'.process.json'),row);print(label,'exit',code,flush=True)
   if code!=expected:raise ValueError('Actual source-backed control exit differs at '+label)
  actual=out/'actual';actual.mkdir()
  def batch(label,fun,source,expected=0,flags=(),target=None,require_complete=False):
   dest=target or actual/label;cmd=[sys.executable,HERE/'batch.py','--provider',a.provider,'--function',fun,'--arguments-rds',a.reference/source,'--seed','97744','--allow-analysis','--out',dest,*flags];run(label,cmd,expected)
   if (dest/'BATCH_STATUS.txt').exists():assert len(json.loads((dest/'original.process.json').read_bytes())['command'])>0
   if require_complete:assert (dest/'BATCH_STATUS.txt').is_file(),'Expected a complete source-call population, not a prevalidation refusal'
   if expected==0:
    result=json.loads((dest/'COMPLETED.json').read_bytes());assert result['one_original_R_process'] and result['analysis_admitted'] and len(result['calls'])==1
    manifest=json.loads((dest/'MANIFEST.json').read_bytes())['files'];assert set(manifest)=={p.relative_to(dest).as_posix() for p in dest.rglob('*') if p.is_file() and p.name!='MANIFEST.json'}
    for name,wanted in manifest.items():assert pin(dest/name)==wanted
   else:assert not json.loads((dest/'FAILURE.json').read_bytes())['analysis_admitted']
  groups=[('coast1000','COASTSS',0),('mixed','COASTSS',2),('stochastic','DGP',0),('baseline-zero','BaselineSS',2),('sum-zero','SumCountSS',2)]
  with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:list(pool.map(lambda r:batch(r[0],r[1],r[0]+'.rds',r[2],require_complete=True),groups))
  run('independent-complete-batch-RDS-comparison',[ready['rscript'],'--vanilla',HERE/'BatchCompare.R',ready['library'],ready['base_library'],a.reference,actual])
  for name in ['empty','duplicate','missing','unnamed','bad_arguments']:
   batch('invalid-'+name,'COASTSS','invalid-'+name+'.rds',2)
   assert not (actual/('invalid-'+name)/'entries').exists(),'Invalid schema executed a prefix'
  batch('capacity-refusal','COASTSS','capacity.rds',2,['--maximum-calls','2']);assert not (actual/'capacity-refusal/entries').exists()
  run('analysis-policy-skip',[sys.executable,HERE/'batch.py','--provider',a.provider,'--function','COASTSS','--arguments-rds',a.reference/'coast1000.rds','--out',actual/'policy-skip'],3)
  run('protected-output-refusal',[sys.executable,HERE/'batch.py','--provider',a.provider,'--function','COASTSS','--arguments-rds',a.reference/'coast1000.rds','--allow-analysis','--out',a.provider/'refused-batch-child'],2);assert not (a.provider/'refused-batch-child').exists()
  single=out/'single';single.mkdir()
  for label,fun,source,status in [('baseline-scalar','BaselineSS','single-baseline-zero.rds',2),('sum-scalar','SumCountSS','single-sum-zero.rds',2),('partial-se-name','BaselineSS','single-baseline-partial-name.rds',2),('valid-zero-p','BaselineSS','single-baseline-positive-underflow.rds',0),('ambiguous-zero','BaselineSS','ambiguous-zero.rds',2)]:
   run(label,[sys.executable,HERE/'run.py','--provider',a.provider,'--function',fun,'--arguments-rds',a.reference/source,'--allow-analysis','--out',single/label],status)
  assert 'ORIGINAL_SCALAR_ZERO_NO_ANALYSIS_ADMISSION' in (single/'baseline-scalar/RETURN_STATUS.txt').read_text()
  assert 'This is not an association p-value' in (out/'baseline-scalar.stdout').read_text()
  assert 'ORIGINAL_ZERO_UNPROVEN_NO_ANALYSIS_ADMISSION' in (single/'ambiguous-zero/RETURN_STATUS.txt').read_text()
  table=out/'table';table.mkdir();run('lossless-table-controls',[ready['rscript'],'--vanilla',HERE/'TableControl.R',HERE/'OriginalProtocol.R',table])
  assert all(pin(HERE/n)==wanted for n,wanted in before.items())
  result=dict(status='PASS_ORIGINAL_ALLELICSERIES_BATCH_COMPLETE_OBJECTS_IDENTIFIERS_RNG_AND_ADMISSION',complete_original_batch_calls=1026,largest_complete_batch=1000,batch_groups=5,invalid_schema_capacity_policy_or_overlap_controls=8,single_scalar_zero_and_real_zero_controls=5,lossless_table_controls=1,sources=before,calls=calls,new_tool_credit=0,whole_goal_complete=False);save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;v=dict(status='SKIP3_BATCH_CONTROLS_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_BATCH_CONTROLS',cause=repr(e),traceback=traceback.format_exc(),calls=calls,analysis_admitted=False)
  if owned:save(out/'FAILURE.json',v)
  print(v['status'],repr(e),flush=True);return code
if __name__=='__main__':raise SystemExit(main())
