#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run a trusted complete-population script in unchanged original rstatix0.7.2; preserve all outputs and conditions."""
from pathlib import Path
import argparse,hashlib,json,os,re,shutil,signal,stat,subprocess,sys,time,traceback
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent;SOURCE=HERE.parent/'rstatix072'
ACTION='Required action: supply a ready unchanged original rstatix0.7.2 Linux/WSL provider, exact trusted UTF-8 R script, declared provenance/population, complete inputs/expected outputs and explicit ALLOW execution/trusted-code policies. Install only permitted prerequisites using the rstatix072 provider guide. SKIP3 skips dependent analysis for missing/forbidden runtime, installation, source, input, required export/package or execution. FAIL2 admits no complete result; preserve full inputs, original objects/errors/warnings, raw logs, conditions, output indexes and status, correct the named cause and retry NEW paths. Review every original warning. Do not shrink a failed population or call a partial result complete. Original R4.5.3 dependencies differ from historical author runtimes; no missing design, direction, comparison family, spelling repair or paper reproduction is inferred. Trusted R code runs with account permissions; this is not an OS sandbox, arbitrary-code validator or total CPU/RSS limit.'
class Skipped(OSError):pass
def pin(path):
 h=hashlib.sha256();size=0
 with path.open('rb') as f:
  for raw in iter(lambda:f.read(1048576),b''):h.update(raw);size+=len(raw)
 return dict(bytes=size,sha256=h.hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False,allow_nan=False)+'\n',encoding='utf8',newline='\n')
def unique_pairs(pairs):
 value={}
 for k,v in pairs:
  if k in value:raise ValueError('Duplicate JSON key: '+k)
  value[k]=v
 return value
def load(path):return json.loads(path.read_text(encoding='utf8'),object_pairs_hook=unique_pairs,parse_constant=lambda s:(_ for _ in ()).throw(ValueError('Nonfinite JSON token: '+s)))
def text(value):
 if not isinstance(value,str) or not value or len(value)>32768 or any(ord(c)<32 or ord(c)==127 for c in value):raise ValueError('Nonempty literal text without control characters required')
 value.encode('utf8',errors='strict');return value
def member(value):
 value=text(value)
 if len(value)>1024 or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*(/[A-Za-z0-9][A-Za-z0-9_.-]*)*',value):raise ValueError('Portable relative logical filename required; scientific identifiers inside files remain literal')
 return value
def integer(value,lower,upper):
 if type(value)!=int or not lower<=value<=upper:raise ValueError('Integer outside declared capacity')
 return value
def canonical(value,parent):
 p=Path(text(value));p=p if p.is_absolute() else parent/p;p=p.absolute()
 if p!=p.resolve():raise ValueError('Canonical paths without symbolic-link aliases required: '+str(p))
 return p
def regular(path):
 if not path.is_file() or path.is_symlink():raise Skipped('Regular permitted prerequisite unavailable: '+str(path))
def same(path,wanted):
 regular(path)
 if pin(path)!=wanted:raise ValueError('Complete protected/captured file changed: '+str(path))
def tree(root,maximum_files,maximum_bytes,maximum_file_bytes,hash_files=False,retain_link_metadata=False):
 result={};total=0;entries=0;pending=[root]
 while pending:
  folder=pending.pop()
  for entry in os.scandir(folder):
   try:info=entry.stat(follow_symlinks=False)
   except FileNotFoundError:continue
   entries+=1
   if entries>maximum_files:raise ValueError('Complete workspace entry capacity exceeded; no prefix admitted')
   p=Path(entry.path);name=p.relative_to(root).as_posix()
   if stat.S_ISLNK(info.st_mode):
    if not retain_link_metadata:raise ValueError('Symbolic links are forbidden in the owned analysis workspace: '+name)
    result[name]=dict(kind='symlink',target=os.readlink(p));continue
   if stat.S_ISDIR(info.st_mode):pending.append(p);continue
   if not stat.S_ISREG(info.st_mode):raise ValueError('Nonregular workspace member: '+name)
   total+=info.st_size
   if info.st_size>maximum_file_bytes or total>maximum_bytes:raise ValueError('Complete workspace byte capacity exceeded; no prefix admitted')
   result[name]=pin(p) if hash_files else dict(bytes=info.st_size)
 return result
def provider(directory):
 ready_file=directory/'READY.json';regular(ready_file);ready_pin=pin(ready_file);ready=load(ready_file);profile=load(SOURCE/'source-profile.json')
 if ready.get('status')!='READY_PRIVATE_ORIGINAL_RSTATIX072_NOT_ANALYSIS' or ready.get('version')!='0.7.2' or ready.get('source_profile')!=pin(SOURCE/'source-profile.json'):
  raise ValueError('Exact original provider/source identity differs')
 if sorted(ready['namespace_exports'])!=profile['namespace_exports'] or ready['source_functions_checked']!=339:raise ValueError('Complete original namespace identity differs')
 pins={ready_file:ready_pin}
 for name,wanted in ready['source_pins'].items():
  p=SOURCE/name;same(p,wanted);pins[p]=wanted
 for name,wanted in ready['dependency_pins'].items():
  p=Path(name);same(p,wanted);pins[p]=wanted
 library=Path(ready['library']);base=Path(ready['base_library']);source_root=Path(ready['source_root']);rscript=Path(ready['rscript']);prefix=Path(ready['r_prefix'])
 if library!=directory/'library' or source_root!=directory/'source/rstatix' or rscript!=prefix/'bin/Rscript' or base!=prefix/'lib/R/library':raise ValueError('Declared original provider paths differ')
 if not library.is_dir() or not source_root.is_dir() or not base.is_dir():raise Skipped('Complete original provider/runtime directories unavailable')
 for root,expected in [(library,ready['installed_library']),(directory/'source',profile['source_files'])]:
  actual={p.relative_to(root).as_posix():p for p in root.rglob('*') if p.is_file() or p.is_symlink()}
  if set(actual)!=set(expected):raise ValueError('Complete original library/source population differs')
  for n,wanted in expected.items():same(actual[n],wanted);pins[actual[n]]=wanted
 for p,wanted in [(directory/profile['archive_name'],profile['archive']),(directory/'readiness.json',ready['readiness'])]:same(p,wanted);pins[p]=wanted
 regular(rscript)
 if not os.access(rscript,os.X_OK):raise Skipped('Original Rscript is not executable')
 return ready,pins
def terminate_group(proc):
 # Only the new process group created by this controller is targeted.
 try:os.killpg(proc.pid,signal.SIGTERM)
 except ProcessLookupError:pass
 try:proc.wait(timeout=3)
 except subprocess.TimeoutExpired:
  try:os.killpg(proc.pid,signal.SIGKILL)
  except ProcessLookupError:pass
  proc.wait(timeout=5)
 # Also end same-group descendants after an exited leader. Separate sessions
 # created by arbitrary caller code are outside the stated supervision scope.
 try:os.killpg(proc.pid,signal.SIGKILL)
 except ProcessLookupError:pass
def execute(command,out,env,limits):
 start=time.monotonic();proc=None;problem=None
 with (out/'process.stdout').open('xb') as so,(out/'process.stderr').open('xb') as se:
  try:
   proc=subprocess.Popen(list(map(str,command)),cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True)
   save(out/'process-live.json',dict(command=list(map(str,command)),pid=proc.pid,process_group=proc.pid,started_unix=time.time()))
   while True:
    tree(out,limits['maximum_files'],limits['maximum_workspace_bytes'],limits['maximum_file_bytes'])
    if time.monotonic()-start>limits['timeout_seconds']:raise ValueError('Original worker exceeded declared wall-time capacity')
    code=proc.poll()
    if code is not None:break
    time.sleep(.05)
  except BaseException as error:problem=error
  finally:
   if proc is not None:
    terminate_group(proc);save(out/'process-exit.json',dict(exit_code=proc.returncode,elapsed_seconds=time.monotonic()-start,owned_process_group_terminated=True))
 if problem is not None:raise problem
 return code

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--request',required=True,type=Path);a=parser.parse_args();out=None;owned=False;admitted=False;code=2;warnings=0;messages=0;errors=0;cause='FAIL2_INVALID_REQUEST';protected={}
 try:
  request=a.request.absolute();regular(request)
  if request!=request.resolve():raise ValueError('Canonical request path required')
  if request.stat().st_size>1048576:raise ValueError('Complete request exceeds1MiB')
  raw=request.read_bytes();document=load(request);request_pin=pin(request)
  if request_pin!=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()):raise ValueError('Request changed during complete capture')
  fields={'schema_version','version','mode','script','provider_directory','inputs','expected_outputs','required_exports','required_packages','expected_return','input_provenance','analysis_population','execution_policy','trusted_code_policy','out','limits'}
  if not isinstance(document,dict) or set(document)!=fields:raise ValueError('Require exactly documented request fields')
  integer(document['schema_version'],1,1)
  if document['version']!='0.7.2' or document['mode']!='SCRIPT':raise ValueError('Require explicit original rstatix0.7.2 SCRIPT')
  parent=request.parent;out=canonical(document['out'],parent);script=canonical(document['script'],parent);directory=canonical(document['provider_directory'],parent)
  if not isinstance(document['inputs'],dict) or not isinstance(document['expected_outputs'],dict):raise ValueError('Declare complete logical input/output objects')
  inputs={member(n):canonical(v,parent) for n,v in document['inputs'].items()};outputs={member(n):integer(v,0,137438953472) for n,v in document['expected_outputs'].items()}
  for names in [inputs,outputs]:
   if len(names)>4096 or len({n.casefold() for n in names})!=len(names):raise ValueError('Duplicate portable filename or excessive population')
   for n in names:
    if any(other.casefold().startswith(n.casefold()+'/') for other in names):raise ValueError('Logical files overlap as directories')
  if not outputs:raise ValueError('Declare every expected complete result')
  exports=document['required_exports'];packages=document['required_packages']
  if not isinstance(exports,list) or len(exports)!=len(set(exports)) or any(not isinstance(n,str) for n in exports):raise ValueError('Unique original export names required')
  if not isinstance(packages,dict) or any(not re.fullmatch(r'[A-Za-z][A-Za-z0-9.]*',n) or not isinstance(v,str) or not re.fullmatch(r'[A-Za-z0-9.+_-]+',v) for n,v in packages.items()):raise ValueError('Explicit required dependency names/versions required')
  for k in ['input_provenance','analysis_population','expected_return']:text(document[k])
  limits=document['limits'];capacities={'timeout_seconds':(1,604800),'maximum_files':(32,1000000),'maximum_workspace_bytes':(1024,137438953472),'maximum_file_bytes':(1,137438953472)}
  if not isinstance(limits,dict) or set(limits)!=set(capacities):raise ValueError('Declare all resource capacities')
  for k,(lower,upper) in capacities.items():integer(limits[k],lower,upper)
  for p in [request,script,directory,*inputs.values(),HERE,SOURCE]:
   if out.is_relative_to(p) or p.is_relative_to(out):raise ValueError('Output overlaps protected request/source/provider/input')
  out.mkdir(exist_ok=False);owned=True;(out/'source-request.json').write_bytes(raw)
  for k in ['execution_policy','trusted_code_policy']:
   if document[k] not in ['ALLOW','FORBID']:raise ValueError('Declare ALLOW or FORBID: '+k)
   if document[k]=='FORBID':raise Skipped('Policy FORBID: '+k+'; no original analysis launched')
  if sys.platform!='linux':raise Skipped('Use the qualified Linux/WSL original R provider')
  ready,provider_pins=provider(directory);protected.update(provider_pins)
  unavailable=set(exports)-set(ready['namespace_exports'])
  if unavailable:raise Skipped('Required original exports unavailable (no spelling repair): '+','.join(sorted(unavailable)))
  regular(script)
  if script.stat().st_size>16777216:raise ValueError('Complete trusted script exceeds16MiB')
  script_raw=script.read_bytes();script_raw.decode('utf8',errors='strict');script_pin=pin(script);protected[script]=script_pin;protected[request]=request_pin
  own_sources={HERE/'run.py':pin(HERE/'run.py'),HERE/'OriginalWorker.R':pin(HERE/'OriginalWorker.R')};protected.update(own_sources)
  (out/'script.R').write_bytes(script_raw);same(out/'script.R',script_pin);(out/'OriginalWorker.R').write_bytes((HERE/'OriginalWorker.R').read_bytes())
  captured=out/'inputs';results=out/'results';diagnostics=out/'diagnostics'
  for p in [captured,results,diagnostics,out/'owned-home',out/'tmp']:p.mkdir()
  input_pins={}
  for n,p in inputs.items():
   regular(p);wanted=pin(p);target=captured/n;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,target);same(target,wanted);same(p,wanted);protected[p]=wanted;protected[target]=wanted;input_pins[n]=wanted
  protected[out/'script.R']=script_pin;protected[out/'OriginalWorker.R']=own_sources[HERE/'OriginalWorker.R']
  save(out/'input-pins.json',input_pins);save(out/'original-provider.json',ready)
  control=dict(library=ready['library'],base_library=ready['base_library'],source_root=ready['source_root'],all_exports=ready['namespace_exports'],required_exports=exports,required_packages=packages,
   script=str(out/'script.R'),inputs=str(captured),results=str(results),diagnostics=str(diagnostics),expected_return=document['expected_return'],input_provenance=document['input_provenance'],analysis_population=document['analysis_population'])
  save(out/'worker-control.json',control);protected[out/'worker-control.json']=pin(out/'worker-control.json')
  env={k:v for k,v in os.environ.items() if not k.startswith('R_') and k not in ['LD_PRELOAD','LD_LIBRARY_PATH','PYTHONPATH']}
  prefix=Path(ready['r_prefix']);env.update(PATH=str(prefix/'bin')+':/usr/bin:/bin',R_HOME=str(prefix/'lib/R'),LD_LIBRARY_PATH=str(prefix/'lib'),R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',R_LIBS=ready['library']+':'+ready['base_library'],R_LIBS_USER=ready['library'],R_LIBS_SITE='',HOME=str(out/'owned-home'),TMPDIR=str(out/'tmp'),LC_ALL='C.UTF-8',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
  tree(out,limits['maximum_files'],limits['maximum_workspace_bytes'],limits['maximum_file_bytes'])
  result=execute([ready['rscript'],'--vanilla',out/'OriginalWorker.R',out/'worker-control.json'],out,env,limits)
  for p,wanted in protected.items():same(p,wanted)
  if not (diagnostics/'worker-status.json').is_file():
   if not (diagnostics/'analysis-started.json').is_file():raise Skipped('Original R startup/readiness unavailable; inspect full worker logs')
   raise ValueError('Original script ended without complete worker diagnostics/return')
  status=load(diagnostics/'worker-status.json');warnings=status['warning_count'];messages=status['message_count'];errors=status['error_count']
  if result==3 and status['exit_code']==3 and status['phase']=='READINESS':raise Skipped('Required original dependency unavailable; see retained original error and full logs')
  if result!=0 or status['status']!='ORIGINAL_RSTATIX072_SCRIPT_COMPLETE' or status['exit_code']!=0 or errors!=0 or not status['returned_marker_matches']:raise ValueError('Original script failed or did not complete the entire declared population')
  if set(tree(captured,limits['maximum_files'],limits['maximum_workspace_bytes'],limits['maximum_file_bytes']))!=set(inputs):raise ValueError('Captured input population changed')
  allowed_roots={'source-request.json','script.R','OriginalWorker.R','inputs','results','diagnostics','owned-home','tmp','input-pins.json','original-provider.json','worker-control.json','process.stdout','process.stderr','process-live.json','process-exit.json'}
  if {p.name for p in out.iterdir()}-allowed_roots:raise ValueError('Unexpected output outside declared results/owned diagnostic directories')
  actual=tree(results,limits['maximum_files'],limits['maximum_workspace_bytes'],limits['maximum_file_bytes'],True)
  if set(actual)!=set(outputs):raise ValueError('Missing/extra complete result files; no prefix/subset admitted')
  for n,minimum in outputs.items():
   if actual[n]['bytes']<minimum:raise ValueError('Incomplete result file: '+n)
  save(out/'result-files.json',actual);admitted=True;code=0;cause='PASS_COMPLETE_DECLARED_ORIGINAL_RSTATIX072_SCRIPT_OUTPUTS'
 except Skipped as error:code=3;cause='SKIP3: '+str(error)
 except OSError as error:
  started=owned and (out/'diagnostics/analysis-started.json').is_file()
  code=2 if started or isinstance(error,FileExistsError) else 3
  cause=('FAIL2: ' if code==2 else 'SKIP3: ')+repr(error)
 except Exception as error:code=2;cause='FAIL2: '+repr(error)
 finally:
  if owned:
   try:
    # Read retained structured counts even if supervision rejected capacity,
    # output completeness or a post-run input/source contradiction.
    p=out/'diagnostics/worker-status.json'
    if p.is_file():
     s=load(p);warnings=s.get('warning_count',warnings);messages=s.get('message_count',messages);errors=s.get('error_count',errors)
    (out/'WARNINGS.md').write_text('# '+cause+'\n\nOriginal warnings: '+str(warnings)+'; messages: '+str(messages)+'; errors: '+str(errors)+'. Review diagnostics/conditions.json, full condition RDS objects and complete process.stdout/process.stderr.\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
    # Retain all regular partial outputs even after a declared-capacity failure.
    inventory=tree(out,1000000,137438953472,137438953472,True,retain_link_metadata=not admitted)
    save(out/'MANIFEST.json',dict(files=inventory,excluded_final_records=['MANIFEST.json','analysis-status.json']))
    save(out/'analysis-status.json',dict(status=cause,exit_code=code,result_admitted=admitted,original_warnings=warnings,original_messages=messages,original_errors=errors,warning_review_required=warnings>0,original_version='0.7.2',whole_tool_qualified=False,paper_reproduced=False,whole_goal_complete=False,required_action=ACTION))
   except Exception as recording:
    code=2;admitted=False;cause='FAIL2: Complete status/inventory recording failed: '+repr(recording)
    try:save(out/'analysis-status.json',dict(status=cause,exit_code=2,result_admitted=False,required_action=ACTION))
    except OSError:pass
 print(cause+'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
