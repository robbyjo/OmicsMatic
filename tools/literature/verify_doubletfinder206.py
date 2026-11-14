#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify source-bound workflow evidence and recompare complete objects using original R."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,os,subprocess,sys,traceback,zipfile
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature/doubletfinder206'))
from Runtime import load,save,pin,Unavailable,check,environment
DATA='docs/literature/doubletfinder206-workflow-development'

def safe(name):
 p=PurePosixPath(name)
 if p.is_absolute() or not p.parts or any(x in ['.','..'] for x in p.parts) or '\\' in name:raise ValueError('Unsafe frozen evidence path')
 return p

def verify(out,runtime):
 proof=load(ROOT/DATA/'VALIDATION.json')
 for name,wanted in proof['sources'].items():
  if pin(ROOT/name)!=wanted:raise ValueError('Qualified current source differs: '+name)
 if pin(ROOT/DATA/'evidence.zip')!=proof['archive'] or pin(ROOT/DATA/'MEMBERS.json')!=proof['members']:raise ValueError('Current frozen original evidence differs')
 target=out/'evidence';target.mkdir();members=load(ROOT/DATA/'MEMBERS.json')
 with zipfile.ZipFile(ROOT/DATA/'evidence.zip') as z:
  names=z.namelist()
  if len(names)!=len(set(names)) or set(names)!=set(members['files'])|{'MEMBERS.json'}:raise ValueError('Incomplete/duplicate current evidence census')
  if z.read('MEMBERS.json')!=(ROOT/DATA/'MEMBERS.json').read_bytes():raise ValueError('Inside/outside evidence inventories differ')
  for name in members['directories']:
   path=target.joinpath(*safe(name).parts);path.mkdir(parents=True,exist_ok=True)
  if members['links'] or members['special_files']:raise ValueError('Unexpected executable/link/stream evidence in this fixed checkpoint')
  for name in names:
   raw=z.read(name)
   if name!='MEMBERS.json' and dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())!=members['files'][name]:raise ValueError('Original evidence payload differs: '+name)
   path=target.joinpath(*safe(name).parts);path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
 component=load(target/'qualified-workflow-comparisons/COMPLETED.json')
 if (component['complete_original_returns'],component['complete_original_R_objects_compared'],component['complete_PNG_files_compared'],component['matching_original_errors'])!=(10,20,2,2):raise ValueError('Source comparison population differs')
 for relative,count in [('windows-controls',4),('linux-controls',8),('recording-controls',6)]:
  result=load(target/relative/'COMPLETED.json');actual=result.get('controls_count',result.get('count'))
  if actual!=count or len(result['controls'])!=count:raise ValueError('Executed control census differs: '+relative)
 for case in component['cases']:
  folder=target/'qualified-workflow-comparisons'/case['name'];analysis=folder/'analysis';status=load(analysis/'analysis-status.json');reference=target/'original-workflow-references'/case['name']
  manifest=load(analysis/'MANIFEST.json')['files']
  for name,wanted in manifest.items():
   if wanted['kind']!='file' or pin(analysis/name)!={k:wanted[k] for k in ['bytes','sha256']}:raise ValueError('Complete original analysis file inventory differs: '+case['name']+'/'+name)
  if pin(analysis/'request.rds')!=pin(reference/'request.rds'):raise ValueError('Complete original typed request changed')
  expected={p.name:pin(p) for p in reference.glob('original-plot-*.png')};actual={p.name:pin(p) for p in (analysis/'original').glob('original-plot-*.png')}
  if expected!=actual:raise ValueError('Complete original PNG bytes differ')
  if case['status']=='PASS_COMPLETE_ORIGINAL_RETURN_RNG_AND_PNG':
   if not status['result_admitted'] or status['exit_code']!=0:raise ValueError('Complete original return was not admitted')
  elif status['result_admitted'] or status['exit_code']!=2:raise ValueError('An original error case was admitted')
  if status['raw_console_diagnostic_counts_complete'] or status['convergence_qualified']:raise ValueError('Unsupported raw diagnostic/convergence claim')
 sct=target/'qualified-workflow-comparisons/SCTransform/analysis';status=load(sct/'analysis-status.json');summary=load(sct/'original/condition-summary.json')
 if status['R_handler_counts']!={'warning':3706,'message':17,'error':0} or status['recognized_iteration_limit_warnings']!=12:raise ValueError('Original SCT warnings changed or were suppressed')
 if sum(g['count'] for g in summary['groups'] if g['kind']=='warning')!=3706 or 'Do not treat this output as a converged SCT analysis' not in (sct/'WARNINGS.md').read_text():raise ValueError('Original SCT warning/recovery notice missing')
 # Public extraction supplies the exact requests already executed by these controls.
 examples=load(ROOT/'examples/doubletfinder206/FILES.json')
 if pin(ROOT/'examples/doubletfinder206/synthetic-requests.zip')!=examples['archive']:raise ValueError('Self-contained public requests changed')
 with zipfile.ZipFile(ROOT/'examples/doubletfinder206/synthetic-requests.zip') as z:
  for case in component['cases']:
   raw=z.read('requests/'+case['name']+'.rds')
   if raw!=(target/'original-workflow-references'/case['name']/'request.rds').read_bytes():raise ValueError('Prepared public request differs from actual original input')
 artifact=dict(status='PASS_SOURCE_BOUND_ARTIFACTS_FULL_OBJECT_RECOMPARISON_STILL_REQUIRED',complete_original_cases=12,original_return_cases=10,original_error_cases=2,complete_PNG_files=2,actual_protocol_and_recording_controls=18,regular_files=len(members['files']),directories=len(members['directories']),convergence_qualified=False,new_family_credit=0,whole_goal_complete=False)
 save(out/'ARTIFACTS.json',artifact)
 if runtime is None or sys.platform!='linux':raise Unavailable('Complete-object recomparison requires --runtime with the exact permitted Linux/WSL original R provider; dependent R verification skipped')
 ready=load(runtime/'READY.json');check(ready)
 for n in ['home','tmp']:(out/n).mkdir()
 command=[str(Path(ready['r_prefix'])/'bin/Rscript'),'--vanilla',str(ROOT/'tools/literature/VerifyDoubletFinder206Captured.R'),ready['library'],str(target),str(out/'COMPLETE-R-COMPARISON.json')]
 with (out/'original-comparison.stdout').open('xb') as so,(out/'original-comparison.stderr').open('xb') as se:child=subprocess.run(command,cwd=out,env=environment(ready,out),stdin=subprocess.DEVNULL,stdout=so,stderr=se)
 save(out/'PROCESS.json',dict(command=command,exit_code=child.returncode))
 if child.returncode:raise ValueError('Complete original object recomparison failed; inspect full comparator logs')
 result=load(out/'COMPLETE-R-COMPARISON.json')
 if result['complete_R_objects_compared']!=22 or result['matching_original_errors']!=2:raise ValueError('Complete original object population differs')
 check(ready)
 return dict(artifact,status='PASS_ORIGINAL_WORKFLOW_EVIDENCE_AND_ALL22_COMPLETE_R_OBJECTS',complete_original_R_objects_recompared=22,original_error_classes_and_messages_rechecked=2,models_fit_in_this_verifier=False,original_condition_identity_claimed=False,whole_tool_qualified=False,whole_goal_complete=False)

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--runtime',type=Path);a=p.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a NEW monocle-runtime verification output')
  out.mkdir(exist_ok=False);owned=True;result=verify(out,None if a.runtime is None else a.runtime.resolve());save(out/'COMPLETED.json',result);print(json.dumps(result),flush=True);return 0
 except BaseException as e:
  code=3 if isinstance(e,Unavailable) else 2;status='SKIP3_ORIGINAL_R_VERIFICATION_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_WORKFLOW_UNADMITTED'
  action='Retain all original evidence and raw logs. Supply the exact permitted original runtime for complete-object comparisons, resolve the named source/input/recording cause and retry NEW paths. Do not change old hashes or waive field/cell/condition differences. Review all SCT warnings; convergence and paper reproduction are not qualified.'
  if owned:
   save(out/'FAILURE.json',dict(status=status,cause=repr(e),traceback=traceback.format_exc(),result_admitted=False,required_action=action));(out/'WARNINGS.md').write_text('# '+status+'\n\n'+repr(e)+'\n\n'+action+'\n',encoding='utf-8')
  print(status,repr(e),action,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
