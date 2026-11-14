#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise public original-script policies, conditions, completeness, identity and owned-worker limits."""
from pathlib import Path
import argparse,copy,hashlib,json,os,shutil,subprocess,sys,time
HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE))
from make_example import request
def save(p,x):p.write_text(json.dumps(x,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
def load(p):return json.loads(p.read_bytes())
def pin(p):
 raw=p.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--provider',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();provider=a.provider.resolve()
 for protected in [provider,HERE]:
  if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected provider/source')
 out.mkdir(parents=True,exist_ok=False);inputs=out/'control-inputs';inputs.mkdir();(inputs/'literal.txt').write_text('α\tNA\tempty-label-is-literal\n',encoding='utf8',newline='\n');input_before=pin(inputs/'literal.txt')
 snapshot=out/'source-snapshot';snapshot.mkdir()
 for name in ['test_workflow.py','run.py','OriginalWorker.R','make_example.py','supervisor-profile.json']:shutil.copyfile(HERE/name,snapshot/name)
 ready_before=pin(provider/'READY.json');calls=[];cases=[]
 def execute(name,value,expected,analysis=None,process=None,warning_file_expected=True):
  target=Path(value['out']);old_status=pin(target/'analysis-status.json') if (target/'analysis-status.json').is_file() else None
  path=out/(name+'.request.json');save(path,value);command=list(map(str,[sys.executable,HERE/'run.py','--request',path]));start=time.time()
  r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=180);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);row=dict(case=name,command=command,exit_code=r.returncode,expected_exit=expected,elapsed_seconds=time.time()-start);calls.append(row);save(out/(name+'.process.json'),row)
  assert r.returncode==expected,(name,row,r.stdout[-1800:],r.stderr[-1800:])
  target=Path(value['out']);status=load(target/'analysis-status.json') if (target/'analysis-status.json').is_file() else None
  if old_status is not None:
   assert pin(target/'analysis-status.json')==old_status,(name,'Existing result overwritten')
  elif status is not None:
   assert status['exit_code']==expected and status['result_admitted']==(expected==0),(name,status)
   if warning_file_expected:assert 'SKIP3' in (target/'WARNINGS.md').read_text() and 'FAIL2' in (target/'WARNINGS.md').read_text(),name
   else:assert 'Could not write WARNINGS.md' in r.stdout.decode('utf8'),name
  if analysis is not None:assert (target/'diagnostics/analysis-started.json').is_file()==analysis,name
  if process is not None:assert (target/'process-live.json').is_file()==process,name
  cases.append(dict(id=name,expected_exit=expected,analysis_started=(target/'diagnostics/analysis-started.json').is_file(),original_process_launched=(target/'process-live.json').is_file(),result_admitted=expected==0))
  print(name,'expected exit',expected,flush=True);return target,status
 def normal(name,code="writeLines('complete',file.path(result_directory,'result.txt')); analysis_complete <- 'COMPLETE'\n"):
  script=inputs/(name+'.R');script.write_text(code,encoding='utf8',newline='\n')
  value=request(provider,script,{'literal.txt':inputs/'literal.txt'},{'result.txt':1},'COMPLETE',out/name)
  value['analysis_population']='One explicit protocol-control case; no paper data or scientific completion inferred.'
  value['required_exports']=[];return value
 if sys.platform!='linux':
  execute('unsupported-platform',normal('unsupported-platform'),3,False,False)
 else:
  for name,key in [('execution-forbidden','execution_policy'),('trusted-code-forbidden','trusted_code_policy')]:
   value=normal(name);value[key]='FORBID';execute(name,value,3,False,False)
  value=normal('literal-missing-export');value['required_exports']=['wilcoxon_test'];execute('literal-missing-export',value,3,False,False)
  value=normal('missing-provider');value['provider_directory']=str(out/'absent-provider');execute('missing-provider',value,3,False,False)
  value=normal('missing-script');value['script']=str(out/'absent-script.R');execute('missing-script',value,3,False,False)
  value=normal('missing-input');value['inputs']={'literal.txt':str(out/'absent-input.txt')};execute('missing-input',value,3,False,False)
  value=normal('missing-required-package');value['required_packages']={'OmicsMaticMissingDependencyForControl':'0.0.0'};execute('missing-required-package',value,3,False,True)
  value=normal('wrong-required-package-version');value['required_packages']={'car':'0.0.0'};execute('wrong-required-package-version',value,3,False,True)
  target,status=execute('warnings-visible',normal('warnings-visible',"message('literal original message α'); warning('original warning line1\\noriginal warning line2'); writeLines('complete',file.path(result_directory,'result.txt')); analysis_complete <- 'COMPLETE'\n"),0,True,True)
  assert status['original_warnings']==1 and status['original_messages']==1 and status['warning_review_required']
  conditions=load(target/'diagnostics/conditions.json');assert [c['kind'] for c in conditions]==['message','warning']
  assert conditions[1]['message']=='original warning line1\noriginal warning line2'
  assert 'original warning line1' in (target/'process.stderr').read_text() and 'original warning line2' in (target/'process.stderr').read_text()
  target,status=execute('warning-as-error',normal('warning-as-error',"options(warn=2);warning('must remain an original error');writeLines('UNREACHED',file.path(result_directory,'result.txt'));analysis_complete <- 'COMPLETE'\n"),2,True,True)
  assert status['original_warnings']==1 and status['original_errors']==1 and not (target/'results/result.txt').exists()
  for name,code in [
   ('original-package-error',"writeLines('before error',file.path(result_directory,'result.txt'));rstatix::shapiro_test(rep(1,6));analysis_complete <- 'COMPLETE'\n"),
   ('explicit-original-error',"writeLines('partial exact α',file.path(result_directory,'result.txt'));stop('original explicit failure α')\n"),
   ('zero-exit-early-quit',"writeLines('partial exact α',file.path(result_directory,'result.txt'));quit(save='no',status=0,runLast=FALSE)\n"),
   ('missing-marker',"writeLines('complete',file.path(result_directory,'result.txt'))\n"),
   ('wrong-marker',"writeLines('complete',file.path(result_directory,'result.txt'));analysis_complete <- 'WRONG'\n"),
   ('missing-result',"analysis_complete <- 'COMPLETE'\n"),
   ('extra-result',"writeLines('complete',file.path(result_directory,'result.txt'));writeLines('extra',file.path(result_directory,'extra.txt'));analysis_complete <- 'COMPLETE'\n"),
   ('changed-captured-input',"writeLines('changed',file.path(input_directory,'literal.txt'));writeLines('complete',file.path(result_directory,'result.txt'));analysis_complete <- 'COMPLETE'\n"),
   ('extra-captured-input',"writeLines('extra',file.path(input_directory,'extra.txt'));writeLines('complete',file.path(result_directory,'result.txt'));analysis_complete <- 'COMPLETE'\n"),
   ('unexpected-root-output',"writeLines('extra','undeclared-result.txt');writeLines('complete',file.path(result_directory,'result.txt'));analysis_complete <- 'COMPLETE'\n"),
   ('workspace-symlink',"file.symlink(file.path(input_directory,'literal.txt'),file.path(result_directory,'result.txt'));analysis_complete <- 'COMPLETE'\n")]:
   target,status=execute(name,normal(name,code),2,True,True)
   if name=='zero-exit-early-quit':assert load(target/'process-exit.json')['exit_code']==0 and (target/'results/result.txt').read_text()=='partial exact α\n'
   if name=='original-package-error':assert (target/'diagnostics/original-error.rds').is_file() and 'identical' in load(target/'diagnostics/worker-status.json')['error']['message']
   if name=='workspace-symlink':assert load(target/'MANIFEST.json')['files']['results/result.txt']['kind']=='symlink'
  value=normal('truncated-result',"writeChar('x',file.path(result_directory,'result.txt'),eos=NULL);analysis_complete <- 'COMPLETE'\n");value['expected_outputs']['result.txt']=2;execute('truncated-result',value,2,True,True)
  value=normal('wall-time-cap',"Sys.sleep(30);writeLines('complete',file.path(result_directory,'result.txt'));analysis_complete <- 'COMPLETE'\n");value['limits']['timeout_seconds']=6;target,status=execute('wall-time-cap',value,2,None,True)
  pid=load(target/'process-live.json')['pid']
  try:os.kill(pid,0);raise AssertionError('Owned timed-out worker still live')
  except ProcessLookupError:pass
  for name,script,changes in [
   ('file-byte-cap',"writeBin(raw(8*1024*1024),file.path(result_directory,'result.txt'));Sys.sleep(3);analysis_complete <- 'COMPLETE'\n",{'maximum_file_bytes':4*1024*1024}),
   ('workspace-byte-cap',"writeBin(raw(40*1024*1024),file.path(result_directory,'result.txt'));Sys.sleep(3);analysis_complete <- 'COMPLETE'\n",{'maximum_workspace_bytes':32*1024*1024,'maximum_file_bytes':64*1024*1024}),
   ('workspace-file-cap',"for(i in 1:120)writeLines('x',file.path(result_directory,paste0(i,'.txt')));Sys.sleep(3);analysis_complete <- 'COMPLETE'\n",{'maximum_files':64})]:
   value=normal(name,script);value['limits'].update(changes);target,status=execute(name,value,2,True,True)
   assert 'capacity' in status['status'],(name,status)
  fake=out/'changed-provider';shutil.copytree(provider,fake);ready=load(fake/'READY.json');ready.update(library=str(fake/'library'),source_root=str(fake/'source/rstatix'));save(fake/'READY.json',ready)
  member=next(iter(load(HERE.parent/'rstatix072/source-profile.json')['source_files']));path=fake/'source'/member;path.write_bytes(path.read_bytes()+b'\n# isolated deliberate source change\n')
  value=normal('changed-provider-source');value['provider_directory']=str(fake);execute('changed-provider-source',value,2,False,False)
  value=normal('invalid-script-utf8');Path(value['script']).write_bytes(bytes([0xff]));execute('invalid-script-utf8',value,2,False,False)
  value=normal('wrong-edition');value['version']='0.7.3';execute('wrong-edition',value,2,False,False)
  value=normal('complete-positive');target,status=execute('complete-positive',value,0,True,True);before=pin(target/'analysis-status.json');execute('existing-output-refusal',value,2,None,None);assert pin(target/'analysis-status.json')==before
  # New diagnostics controls preserve each literal malformed worker report.
  malformed=[('truncated-worker-status','{"status":'),
   ('negative-count-worker-status','{"warning_count":-1,"message_count":0,"error_count":0}'),
   ('boolean-count-worker-status','{"warning_count":false,"message_count":0,"error_count":0}'),
   ('fractional-count-worker-status','{"warning_count":0.0,"message_count":0,"error_count":0}'),
   ('missing-count-worker-status','{"warning_count":0,"message_count":0}'),
   ('duplicate-count-worker-status','{"warning_count":0,"warning_count":1,"message_count":0,"error_count":0}'),
   ('nonfinite-count-worker-status','{"warning_count":NaN,"message_count":0,"error_count":0}'),
   ('nonobject-worker-status','[]')]
  for name,raw in malformed:
   # JSON string syntax is valid R string syntax for these ASCII reports.
   script="writeLines('partial exact α',file.path(result_directory,'result.txt'));writeChar("+json.dumps(raw)+",file.path(result_directory,'..','diagnostics','worker-status.json'),eos=NULL);quit(save='no',status=0,runLast=FALSE)\n"
   target,status=execute(name,normal(name,script),2,True,True)
   assert load(target/'process-exit.json')['exit_code']==0
   assert (target/'diagnostics/worker-status.json').read_bytes()==raw.encode('ascii')
   assert (target/'results/result.txt').read_text()=='partial exact α\n'
   assert status['supervisor_revision']==2 and not status['condition_counts_complete'] and status['warning_review_required']
   assert all(status[k] is None for k in ['original_warnings','original_messages','original_errors'])
   assert 'unavailable' in (target/'WARNINGS.md').read_text() and (target/'WORKER_DIAGNOSTIC_ERROR.txt').is_file()
   assert (target/'MANIFEST.json').is_file() and not (target/'result-files.json').exists()
  # No worker report is not evidence that the original warning count was zero.
  target=out/'zero-exit-early-quit';status=load(target/'analysis-status.json')
  assert not status['condition_counts_complete'] and status['original_warnings'] is None and status['warning_review_required']
  assert (target/'WORKER_DIAGNOSTIC_ERROR.txt').is_file()
  target,status=execute('warning-report-path-blocked',normal('warning-report-path-blocked',"dir.create(file.path(result_directory,'..','WARNINGS.md'));writeLines('complete',file.path(result_directory,'result.txt'));analysis_complete <- 'COMPLETE'\n"),2,True,True,warning_file_expected=False)
  assert (target/'WARNINGS.md').is_dir() and status['manifest_complete'] and status['warning_review_required']
  assert not status['condition_counts_complete'] and (target/'MANIFEST.json').is_file()
  target,status=execute('status-report-path-blocked',normal('status-report-path-blocked',"dir.create(file.path(result_directory,'..','analysis-status.json'));writeLines('complete',file.path(result_directory,'result.txt'));analysis_complete <- 'COMPLETE'\n"),2,True,True)
  assert status is None and (target/'analysis-status.json').is_dir() and (target/'WARNINGS.md').is_file()
  assert 'Could not write analysis-status.json' in (out/'status-report-path-blocked.stdout').read_text()
  assert 'FAIL2' in (target/'WARNINGS.md').read_text() and (target/'MANIFEST.json').is_file()
  # All final inventories cover complete regular data and link identities.
  for item in cases:
   folder=out/item['id'];manifest=folder/'MANIFEST.json'
   if not manifest.is_file():continue
   records=load(manifest)['files']
   for n,wanted in records.items():
    f=folder/n
    if wanted.get('kind')=='symlink':assert f.is_symlink() and os.readlink(f)==wanted['target']
    else:assert pin(f)==wanted,(item['id'],n)
 assert pin(provider/'READY.json')==ready_before and pin(inputs/'literal.txt')==input_before
 result=dict(status='PASS_ORIGINAL_RSTATIX_REVISION2_DIAGNOSTICS_AND_ALL_PREVIOUS_WORKFLOW_CONTROLS',supervisor_revision=2,new_malformed_worker_status_controls=8,new_recording_failure_controls=2,controls=len(cases),cases=cases,calls=calls,
  protected_provider_and_input_unchanged=True,failed_partial_outputs_retained=True,original_warning_as_error_semantics_preserved=True,
  whole_tool_qualified=False,paper_reproduced=False,new_tool_credit=0,whole_goal_complete=False)
 save(out/'COMPLETED.json',result);print(result['status'],'controls='+str(len(cases)),flush=True)
if __name__=='__main__':main()
