#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Supervise an exact trusted R script using the original velociraptor 1.20.0 runtime."""
from pathlib import Path
import argparse,hashlib,os,re,shutil,signal,stat,subprocess,sys,tempfile,time,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,Unavailable,environment,load,pin,provider,regular,same,save
from RValues import load_tree
from FinalRecords import finalize

def text(value):
 if not isinstance(value,str) or not value or len(value)>32768 or any(ord(c)<32 or ord(c)==127 for c in value):raise ValueError('Nonempty literal UTF-8 text without control characters required')
 value.encode('utf-8',errors='strict');return value

def member(value):
 value=text(value)
 if len(value)>1024 or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*(/[A-Za-z0-9][A-Za-z0-9_.-]*)*',value):raise ValueError('Portable logical filenames required; scientific identifiers inside files remain literal')
 if value.split('/')[0]=='result-value-tree':raise ValueError('result-value-tree is reserved for the complete original return')
 return value

def integer(value,lower,upper):
 if type(value)!=int or not lower<=value<=upper:raise ValueError('Whole-number request capacity outside allowed range')
 return value

def canonical(value,parent):
 path=Path(text(value));path=path if path.is_absolute() else parent/path;path=path.absolute()
 if path!=path.resolve():raise ValueError('Canonical path without symbolic-link aliases required: '+str(path))
 return path

def tree(root,limits,hash_files=False,retain_links=False,*,budget=None,permit_ipc=False):
 result={};pending=[root];budget=[0,0] if budget is None else budget
 while pending:
  folder=pending.pop()
  try:iterator=os.scandir(folder)
  except FileNotFoundError:
   if folder==root:raise
   continue
  for entry in iterator:
   try:info=entry.stat(follow_symlinks=False)
   except FileNotFoundError:continue
   budget[0]+=1
   if budget[0]>limits['maximum_files']:raise ValueError('Complete workspace entry capacity exceeded; no prefix admitted')
   path=Path(entry.path);name=path.relative_to(root).as_posix()
   if stat.S_ISLNK(info.st_mode):
    if not retain_links:raise ValueError('Unexpected owned-output link: '+name)
    result[name]=dict(kind='symlink',target=os.readlink(path));continue
   if stat.S_ISDIR(info.st_mode):pending.append(path);continue
   if not stat.S_ISREG(info.st_mode):
    if permit_ipc and (stat.S_ISSOCK(info.st_mode) or stat.S_ISFIFO(info.st_mode)):
     result[name]=dict(kind='socket' if stat.S_ISSOCK(info.st_mode) else 'FIFO',mode=info.st_mode,uid=info.st_uid,gid=info.st_gid,inode=info.st_ino,device=info.st_dev,mtime_ns=info.st_mtime_ns,serialized_stream_or_live_state=False);continue
    if not retain_links:raise ValueError('Unexpected owned-output special node: '+name)
    result[name]=dict(kind='special',mode=info.st_mode);continue
   budget[1]+=info.st_size
   if info.st_size>limits['maximum_file_bytes'] or budget[1]>limits['maximum_workspace_bytes']:raise ValueError('Complete workspace byte capacity exceeded; no prefix admitted')
   result[name]=pin(path) if hash_files else dict(bytes=info.st_size)
 return result

def capture_native(native,out):
 """Retain regular bytes and non-file metadata; never read IPC streams or links."""
 limits=dict(maximum_files=1000000,maximum_file_bytes=137438953472,maximum_workspace_bytes=137438953472)
 before=tree(native,limits,True,retain_links=True,permit_ipc=True);destination=out/'native-temporary';destination.mkdir(exist_ok=False)
 pending=[native]
 while pending:
  folder=pending.pop()
  for entry in os.scandir(folder):
   path=Path(entry.path);relative=path.relative_to(native);target=destination/relative;mode=entry.stat(follow_symlinks=False).st_mode
   if stat.S_ISDIR(mode):target.mkdir();pending.append(path)
   elif stat.S_ISREG(mode):shutil.copyfile(path,target)
   # Sockets/FIFOs have no ordinary file payload to copy. Links are captured as
   # literal metadata without following targets. Their original nodes remain in
   # the owned native root. Live process/stream state is not recoverable here.
 after=tree(native,limits,True,retain_links=True,permit_ipc=True)
 if before!=after:raise ValueError('Native temporary file/link/IPC population changed during post-exit capture')
 captured=tree(destination,limits,True)
 regular_files={name:value for name,value in before.items() if 'kind' not in value}
 if captured!=regular_files:raise ValueError('Complete native regular-file bytes/population differ after copying')
 save(out/'native-temporary-files.json',dict(original_native_root=str(native),complete_available_files=before,regular_file_payloads_copied=len(regular_files),nonfile_metadata_retained=sum('kind' in row for row in before.values()),original_native_root_deleted=False,live_IPC_streams_or_process_memory_serialized=False,required_action='Inspect complete available regular bytes plus literal link/IPC metadata. Original nodes remain in the recorded owned native root. No live socket, FIFO stream or terminated process memory has been serialized or reconstructed.'))

def terminate_group(child):
 # Only this controller's new process group is targeted; no unrelated jobs.
 try:os.killpg(child.pid,signal.SIGTERM)
 except ProcessLookupError:pass
 try:child.wait(timeout=3)
 except subprocess.TimeoutExpired:
  try:os.killpg(child.pid,signal.SIGKILL)
  except ProcessLookupError:pass
  child.wait(timeout=5)
 try:os.killpg(child.pid,signal.SIGKILL)
 except ProcessLookupError:pass

def execute(command,out,env,limits,native):
 started=time.monotonic();child=None;failure=None;code=None
 with (out/'process.stdout').open('xb') as so,(out/'process.stderr').open('xb') as se:
  try:
   child=subprocess.Popen(list(map(str,command)),cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True)
   save(out/'process-live.json',dict(pid=child.pid,process_group=child.pid,command=list(map(str,command)),started_unix=time.time()))
   last_report=started
   while True:
    budget=[0,0];tree(out,limits,budget=budget);tree(native,limits,budget=budget,permit_ipc=True)
    if time.monotonic()-started>limits['timeout_seconds']:raise ValueError('Original worker exceeded the declared wall-time capacity')
    code=child.poll()
    if code is not None:break
    if time.monotonic()-last_report>=20:
     print('SAME_ORIGINAL_R_WORKER_RUNNING',child.pid,round(time.monotonic()-started),flush=True);last_report=time.monotonic()
    time.sleep(.1)
  except BaseException as error:failure=error
  finally:
   if child is not None:
    terminate_group(child);save(out/'process-exit.json',dict(exit_code=child.returncode,elapsed_seconds=time.monotonic()-started,owned_process_group_terminated=True))
 if failure is not None:raise failure
 return code

def condition_counts(status):
 values=[status[key] for key in ['warning_count','message_count','error_count']]
 if any(type(value)!=int or value<0 for value in values):raise ValueError('Complete nonnegative R condition totals required')
 if status['raw_console_diagnostic_counts_complete'] is not False:raise ValueError('Native/Python raw console totals must remain explicitly unavailable')
 return values

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--request',type=Path,required=True);args=parser.parse_args()
 out=None;native=None;owned=False;admitted=False;code=2;cause='FAIL2_INVALID_REQUEST';failure_traceback=None;counts=[None,None,None];counts_complete=False;value_nodes=None
 try:
  request=args.request.absolute();regular(request)
  if request!=request.resolve():raise ValueError('Canonical request required')
  if request.stat().st_size>1048576:raise ValueError('Complete request exceeds 1 MiB')
  raw=request.read_bytes();document=load(request);request_pin=pin(request)
  if request_pin!=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()):raise ValueError('Request changed during complete capture')
  fields={'schema_version','version','mode','script','provider_directory','inputs','expected_outputs','required_exports','required_packages','expected_return','input_provenance','analysis_population','execution_policy','trusted_code_policy','out','limits'}
  if not isinstance(document,dict) or set(document)!=fields:raise ValueError('Require exactly the documented request fields')
  integer(document['schema_version'],1,1)
  if document['version']!='1.20.0' or document['mode']!='R_SCRIPT':raise ValueError('Explicit original velociraptor 1.20.0 R_SCRIPT required')
  parent=request.parent;out=canonical(document['out'],parent);script=canonical(document['script'],parent);directory=canonical(document['provider_directory'],parent)
  if not isinstance(document['inputs'],dict) or not isinstance(document['expected_outputs'],dict):raise ValueError('Complete logical input/output objects required')
  inputs={member(name):canonical(value,parent) for name,value in document['inputs'].items()}
  outputs={member(name):integer(value,0,137438953472) for name,value in document['expected_outputs'].items()}
  for names in [inputs,outputs]:
   if len(names)>4096 or len({name.casefold() for name in names})!=len(names):raise ValueError('Duplicate portable filename or excessive file population')
   for name in names:
    if any(other.casefold().startswith(name.casefold()+'/') for other in names):raise ValueError('Logical files overlap as directories')
  if not inputs:raise ValueError('Declare original input files explicitly')
  exports=document['required_exports'];packages=document['required_packages']
  if not isinstance(exports,list) or not exports or any(not isinstance(name,str) for name in exports) or len(exports)!=len(set(exports)):raise ValueError('Unique required original exports required')
  if not isinstance(packages,dict) or any(not re.fullmatch(r'[A-Za-z][A-Za-z0-9.]*',name) or not isinstance(version,str) or not re.fullmatch(r'[A-Za-z0-9.+_-]+',version) for name,version in packages.items()):raise ValueError('Literal original dependency names and versions required')
  for name in ['input_provenance','analysis_population','expected_return']:text(document[name])
  limits=document['limits'];capacities={'timeout_seconds':(1,604800),'maximum_files':(32,1000000),'maximum_workspace_bytes':(1024,137438953472),'maximum_file_bytes':(1,137438953472),'maximum_R_nodes':(1,1000000),'maximum_R_value_bytes':(1,137438953472)}
  if not isinstance(limits,dict) or set(limits)!=set(capacities):raise ValueError('Declare every documented capacity')
  for name,(lower,upper) in capacities.items():integer(limits[name],lower,upper)
  for path in [request,script,directory,*inputs.values(),HERE]:
   if out.is_relative_to(path) or path.is_relative_to(out):raise ValueError('NEW output overlaps protected request/source/provider/input')
  out.mkdir(exist_ok=False);owned=True;(out/'source-request.json').write_bytes(raw)
  for name in ['execution_policy','trusted_code_policy']:
   if document[name] not in ['ALLOW','FORBID']:raise ValueError('Explicit ALLOW or FORBID required: '+name)
   if document[name]=='FORBID':raise Unavailable('Policy FORBID: '+name+'; dependent analysis skipped before launch')
  if sys.platform!='linux':raise Unavailable('Use the qualified Linux/WSL original runtime')
  print('VERIFYING_COMPLETE_ORIGINAL_RUNTIME_BEFORE_ANALYSIS',flush=True);ready=provider(directory)
  profile=load(HERE/'source-profile.json')
  if set(exports)-set(profile['namespace_exports']):raise Unavailable('Required original export unavailable; no spelling repair')
  regular(script)
  if script.stat().st_size>16777216:raise ValueError('Complete trusted R script exceeds 16 MiB')
  script_raw=script.read_bytes();script_raw.decode('utf-8',errors='strict');protected={script:pin(script),request:request_pin,directory/'READY.json':pin(directory/'READY.json')}
  source_names=['run.py','FinalRecords.py','Runtime.py','OriginalWorker.R','AuditRuntime.R','RValues.R','RValues.py','source-profile.json']
  own_sources={name:pin(HERE/name) for name in source_names};protected.update({HERE/name:wanted for name,wanted in own_sources.items()})
  for name in ['inputs','results','diagnostics','owned-home','tmp','matplotlib-cache','numba-cache','unused-external-cache','adapter-source']:(out/name).mkdir()
  native=Path(tempfile.mkdtemp(prefix='ovr-',dir='/tmp'))
  if native!=native.resolve() or len(str(native).encode())>48:raise ValueError('Short canonical owned native TMPDIR required')
  save(out/'native-temporary.json',dict(path=str(native),created_only_for_this_request=True,retained_without_deletion=True,durable_copy='native-temporary',purpose='Short native Linux paths for original multiprocessing Unix sockets; no scientific data/settings changed'))
  for name in source_names:
   target=out/'adapter-source'/name;shutil.copyfile(HERE/name,target);same(target,own_sources[name]);protected[target]=own_sources[name]
  (out/'script.R').write_bytes(script_raw);same(out/'script.R',protected[script]);protected[out/'script.R']=protected[script]
  input_pins={}
  for name,path in inputs.items():
   regular(path);wanted=pin(path);target=out/'inputs'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,target);same(target,wanted);same(path,wanted)
   protected[path]=wanted;protected[target]=wanted;input_pins[name]=wanted
  save(out/'input-pins.json',input_pins);shutil.copyfile(directory/'READY.json',out/'original-provider.json')
  control=dict(library=ready['library'],base_library=ready['base_library'],source_root=ready['source_root'],backend=ready['backend'],
   required_exports=exports,required_packages=packages,script=str(out/'script.R'),inputs=str(out/'inputs'),results=str(out/'results'),diagnostics=str(out/'diagnostics'),
   audit_script=str(out/'adapter-source/AuditRuntime.R'),source_profile=str(out/'adapter-source/source-profile.json'),value_capture_script=str(out/'adapter-source/RValues.R'),value_directory=str(out/'results/result-value-tree'),
   expected_return=document['expected_return'],input_provenance=document['input_provenance'],analysis_population=document['analysis_population'],maximum_R_nodes=limits['maximum_R_nodes'],maximum_R_value_bytes=limits['maximum_R_value_bytes'],native_temporary_directory=str(native))
  save(out/'worker-control.json',control);protected[out/'worker-control.json']=pin(out/'worker-control.json');tree(out,limits)
  result=execute([ready['rscript'],'--vanilla',out/'adapter-source/OriginalWorker.R',out/'worker-control.json'],out,environment(ready,out,temporary_directory=native),limits,native)
  for path,wanted in protected.items():same(path,wanted)
  print('VERIFYING_COMPLETE_ORIGINAL_RUNTIME_AFTER_ANALYSIS',flush=True);provider(directory)
  status_path=out/'diagnostics/worker-status.json'
  if not status_path.is_file():raise ValueError('Worker ended without complete original diagnostics; inspect all partial objects and raw consoles')
  regular(status_path);status=load(status_path);counts=condition_counts(status);counts_complete=True
  if result==3 and status['exit_code']==3 and status['phase']=='READINESS':raise Unavailable('Required original dependency unavailable; see retained original conditions and raw logs')
  if result!=0 or status['status']!='COMPLETE_ORIGINAL_VELOCIRAPTOR120_R_SCRIPT' or status['exit_code']!=0 or counts[2]!=0 or not status['returned_marker_matches'] or not status['complete_value_capture'] or not status['original_R_temporary_tree_retained']:raise ValueError('Original worker did not return its complete declared population and complete diagnostics')
  actual=tree(out/'results',limits,True)
  caller_files={name:value for name,value in actual.items() if not name.startswith('result-value-tree/')}
  if set(caller_files)!=set(outputs):raise ValueError('Missing or extra caller result files; no prefix/subset admitted')
  for name,minimum in outputs.items():
   if caller_files[name]['bytes']<minimum:raise ValueError('Incomplete caller result file: '+name)
  value=load_tree(out/'results/result-value-tree',max_nodes=limits['maximum_R_nodes'],max_total_bytes=limits['maximum_R_value_bytes'])
  value_nodes=value['index']['node_count']
  if set(tree(out/'inputs',limits,True))!=set(inputs):raise ValueError('Captured complete input population changed')
  save(out/'result-files.json',actual);admitted=True;code=0;cause='PASS_COMPLETE_DECLARED_ORIGINAL_VELOCIRAPTOR120_R_SCRIPT_OUTPUTS'
 except Unavailable as error:
  code=2 if owned and (out/'diagnostics/analysis-started.json').is_file() else 3
  cause=('FAIL2: ' if code==2 else 'SKIP3: ')+str(error);failure_traceback=traceback.format_exc()
 except OSError as error:
  code=2 if isinstance(error,FileExistsError) or (owned and (out/'process-live.json').is_file()) else 3
  cause=('FAIL2: ' if code==2 else 'SKIP3: ')+repr(error);failure_traceback=traceback.format_exc()
 except BaseException as error:code=2;cause='FAIL2: '+repr(error);failure_traceback=traceback.format_exc()
 finally:
  if owned:
   if native is not None:
    try:
     capture_native(native,out)
    except BaseException as error:code=2;admitted=False;cause='FAIL2_NATIVE_TEMPORARY_RETENTION_INCOMPLETE: '+repr(error)+'; '+cause;print(cause,ACTION,flush=True)
   if failure_traceback:
    try:(out/'SUPERVISOR_ERROR.txt').write_text(failure_traceback,encoding='utf-8',newline='\n')
    except OSError as error:code=2;admitted=False;cause='FAIL2_SUPERVISOR_ERROR_RECORDING_UNAVAILABLE: '+repr(error)+'; '+cause;print(cause,ACTION,flush=True)
   status_path=out/'diagnostics/worker-status.json'
   if status_path.is_file():
    try:regular(status_path);counts=condition_counts(load(status_path));counts_complete=True
    except BaseException as error:counts=[None,None,None];counts_complete=False;code=2;admitted=False;cause='FAIL2: unreadable worker diagnostics: '+repr(error)+'; '+cause
   code,cause=finalize(out,code=code,cause=cause,admitted=admitted,counts=counts,counts_complete=counts_complete,action=ACTION,tree=tree,save=save,fields=dict(complete_R_value_nodes=value_nodes,whole_R_wrapper_qualified=False,new_family_credit=0,paper_reproduced=False,whole_goal_complete=False))
 print(cause+'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
