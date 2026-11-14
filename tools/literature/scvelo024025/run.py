#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run an exact trusted script with original scVelo0.2.4/0.2.5; retain all diagnostics and refuse partial populations."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,os,re,shutil,signal,stat,subprocess,sys,tempfile,time,traceback
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE))
from Provider import verify_provider,Unavailable
ACTION='Use only the exact selected original0.2.4 or0.2.5 Linux/WSL CPU provider, a trusted UTF-8 Python script, complete input/output declarations and explicit ALLOW execution/trusted-code policies. SKIP3 skips dependent analysis for missing/forbidden execution, runtime, input or literal original symbol/dependency. Supply only permitted exact prerequisites and retry NEW paths. The printed0.24 edition remains unresolved; no version substitution is admitted. The R velociraptor wrapper remains separate. FAIL2 admits no complete result: preserve every original object, NaN/Inf, warning/error, raw malformed report, partial output, manifest and full console. Unknown condition totals are unavailable, never zero. Inspect WARNINGS.md, analysis-status.json, diagnostics/conditions.json, raw worker-status.json and all supervisor/recording errors. Delivered conditions include the trusted caller script and original dependencies; do not mistake zero delivered warnings for numerical validity. Original loss-history padding and all convergence limits remain explicit. A dedicated short native-Linux temporary root is monitored with the analysis root; available temporary bytes and native node identities are retained in the analysis bundle, and the original native root remains retained. Capacity checks cover both roots, including retained copies. Trusted scripts run with account permissions; supervision is not an OS sandbox or total CPU/RSS enclosure. Structural completion is not statistical validity, convergence, author-environment identity or paper reproduction.'
class Skipped(OSError):pass

def save(path,value):path.write_bytes((json.dumps(value,indent=2,ensure_ascii=False,allow_nan=False)+'\n').encode('utf8'))

def member(value):
 value=text(value)
 if len(value.encode('utf8'))>4096 or '\\' in value or re.match(r'^[A-Za-z]:',value):raise ValueError('Exact safe relative logical filename required')
 p=PurePosixPath(value)
 if p.is_absolute() or p.as_posix()!=value or any(n in ['', '.', '..'] for n in value.split('/')):raise ValueError('Exact relative filename without traversal required')
 return value

def pin(path):
 h=hashlib.sha256();size=0
 with path.open('rb') as f:
  for raw in iter(lambda:f.read(1048576),b''):h.update(raw);size+=len(raw)
 return dict(bytes=size,sha256=h.hexdigest())

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

def tree(root,maximum_files,maximum_bytes,maximum_file_bytes,hash_files=False,retain_link_metadata=False,allow_owned_ipc=False,ipc_prefix='tmp/',statistics=None):
 result={};total=0;entries=0;pending=[root]
 while pending:
  folder=pending.pop()
  # Original R routinely removes its temporary child directories on exit.
  # Ignore only a child that disappeared before its queued monitoring scan;
  # a missing owned root remains an error. Complete results are checked later.
  try:directory_entries=os.scandir(folder)
  except FileNotFoundError:
   if folder==root:raise
   continue
  for entry in directory_entries:
   try:info=entry.stat(follow_symlinks=False)
   except FileNotFoundError:continue
   entries+=1
   if entries>maximum_files:raise ValueError('Complete workspace entry capacity exceeded; no prefix admitted')
   p=Path(entry.path);name=p.relative_to(root).as_posix()
   if stat.S_ISLNK(info.st_mode):
    if not retain_link_metadata:raise ValueError('Symbolic links are forbidden in the owned analysis workspace: '+name)
    result[name]=dict(kind='symlink',target=os.readlink(p));continue
   if stat.S_ISDIR(info.st_mode):pending.append(p);continue
   if stat.S_ISSOCK(info.st_mode) and allow_owned_ipc and re.fullmatch(re.escape(ipc_prefix)+r'pymp-[a-z0-9_]{8}/listener-[a-z0-9_]{8}',name):
    result[name]=dict(kind='owned-temporary-manager-socket',mode=stat.S_IMODE(info.st_mode),device=info.st_dev,inode=info.st_ino);continue
   if not stat.S_ISREG(info.st_mode):raise ValueError('Nonregular workspace member: '+name)
   total+=info.st_size
   if info.st_size>maximum_file_bytes or total>maximum_bytes:raise ValueError('Complete workspace byte capacity exceeded; no prefix admitted')
   result[name]=pin(p) if hash_files else dict(bytes=info.st_size)
 if statistics is not None:statistics.update(entries=entries,bytes=total)
 return result

def workspace(out,limits,native_temporary=None,hash_files=False):
 statistics={};files=tree(out,limits['maximum_files'],limits['maximum_workspace_bytes'],limits['maximum_file_bytes'],hash_files,statistics=statistics);external={}
 if native_temporary is not None:
  other={};external=tree(native_temporary,limits['maximum_files']-statistics['entries'],limits['maximum_workspace_bytes']-statistics['bytes'],limits['maximum_file_bytes'],hash_files,allow_owned_ipc=True,ipc_prefix='',statistics=other)
  statistics={k:statistics[k]+other[k] for k in ['entries','bytes']}
 return dict(files=files,native_temporary=external,statistics=statistics)

def retain_native_temporary(root,out):
 """Copy available regular temporary bytes; retain IPC/link identity without following special nodes."""
 target=out/'native-temporary';target.mkdir(exist_ok=False);records={}
 for folder,directories,files in os.walk(root,followlinks=False):
  for name in sorted(directories+files):
   path=Path(folder)/name;relative=path.relative_to(root).as_posix();info=path.lstat();destination=target/relative;mode=stat.S_IMODE(info.st_mode)
   if stat.S_ISLNK(info.st_mode):records[relative]=dict(kind='symlink',target=os.readlink(path),mode=mode)
   elif stat.S_ISDIR(info.st_mode):destination.mkdir(parents=True,exist_ok=True);records[relative]=dict(kind='directory',mode=mode)
   elif stat.S_ISREG(info.st_mode):
    destination.parent.mkdir(parents=True,exist_ok=True);wanted=pin(path);shutil.copyfile(path,destination)
    if pin(destination)!=wanted or pin(path)!=wanted:raise ValueError('Owned native temporary file changed during complete retention: '+relative)
    records[relative]=dict(kind='file',mode=mode,**wanted)
   elif stat.S_ISSOCK(info.st_mode):records[relative]=dict(kind='socket-metadata-only',mode=mode,device=info.st_dev,inode=info.st_ino)
   else:raise ValueError('Unknown native temporary node; preserve native root and partial copy: '+relative)
 value=dict(original_root=str(root),retained_copy='native-temporary',original_root_retained=True,files=records,scope='All available regular file bytes copied without reencoding; native modes, directories and remaining socket/link identities recorded. No IPC-memory or cross-process state equivalence is claimed. Original native root remains retained.')
 save(out/'native-temporary-inventory.json',value);return value

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

def execute(command,out,env,limits,native_temporary=None):
 start=time.monotonic();proc=None;problem=None
 with (out/'process.stdout').open('xb') as so,(out/'process.stderr').open('xb') as se:
  try:
   proc=subprocess.Popen(list(map(str,command)),cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True)
   save(out/'process-live.json',dict(command=list(map(str,command)),pid=proc.pid,process_group=proc.pid,started_unix=time.time()))
   while True:
    workspace(out,limits,native_temporary)
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

def counts(status):
 if not isinstance(status,dict):raise ValueError('Complete original worker status object required')
 values=[status[k] for k in ['warning_count','log_count','error_count']]
 if any(type(x)!=int or x<0 for x in values):raise ValueError('Complete nonnegative integer condition counts required')
 return values

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--request',type=Path,required=True);a=parser.parse_args();out=None;owned=False;admitted=False;code=2;cause='FAIL2_INVALID_REQUEST';protected={};failure=None;warnings_count=logs_count=errors_count=0;native_temporary=None;native_inventory=None
 try:
  request=a.request.absolute();regular(request)
  if request!=request.resolve() or request.stat().st_size>16777216:raise ValueError('Canonical complete request of at most16MiB required')
  raw=request.read_bytes();doc=load(request);request_pin=pin(request)
  if request_pin!=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()):raise ValueError('Request changed during capture')
  fields={'schema_version','version','mode','script','provider_directory','inputs','expected_outputs','required_symbols','required_distributions','expected_return','input_provenance','analysis_population','execution_policy','trusted_code_policy','out','limits'}
  if not isinstance(doc,dict) or set(doc)!=fields:raise ValueError('Exactly documented request fields required')
  integer(doc['schema_version'],1,1)
  if doc['mode']!='SCRIPT':raise ValueError('Select exact original scVelo SCRIPT mode')
  text(doc['version'])
  parent=request.parent;out=canonical(doc['out'],parent);script=canonical(doc['script'],parent);directory=canonical(doc['provider_directory'],parent)
  if not isinstance(doc['inputs'],dict) or not isinstance(doc['expected_outputs'],dict):raise ValueError('Declare complete logical input/output objects')
  inputs={member(n):canonical(v,parent) for n,v in doc['inputs'].items()};outputs={member(n):integer(v,0,137438953472) for n,v in doc['expected_outputs'].items()}
  if not outputs:raise ValueError('Declare every complete expected result file')
  for names in [inputs,outputs]:
   if len(names)>1000000:raise ValueError('Excessive declared file population')
   for n in names:
    if any(x.startswith(n+'/') for x in names):raise ValueError('Logical file/directory names overlap')
  symbols=doc['required_symbols'];packages=doc['required_distributions']
  if not isinstance(symbols,list) or any(not isinstance(s,str) or not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*',s) for s in symbols) or len(symbols)!=len(set(symbols)):raise ValueError('Unique literal original dotted symbol names required')
  if not isinstance(packages,dict) or any(not re.fullmatch('[A-Za-z0-9_.-]+',n) or not isinstance(v,str) or not re.fullmatch('[A-Za-z0-9.+_-]+',v) for n,v in packages.items()):raise ValueError('Literal required installed dependency names/versions required')
  for n in ['input_provenance','analysis_population','expected_return']:text(doc[n])
  limits=doc['limits'];capacities={'timeout_seconds':(1,604800),'maximum_files':(32,1000000),'maximum_workspace_bytes':(1024,137438953472),'maximum_file_bytes':(1,137438953472)}
  if not isinstance(limits,dict) or set(limits)!=set(capacities):raise ValueError('Declare all resource capacities')
  for n,(lower,upper) in capacities.items():integer(limits[n],lower,upper)
  for p in [request,script,directory,*inputs.values(),HERE]:
   if out.is_relative_to(p) or p.is_relative_to(out):raise ValueError('Output overlaps protected source/provider/request/input')
  out.mkdir(parents=True,exist_ok=False);owned=True;(out/'source-request.json').write_bytes(raw)
  if doc['version'] not in ['0.2.4','0.2.5']:raise Skipped('Exact requested edition is not qualified: '+doc['version']+'; no silent version substitution')
  for n in ['execution_policy','trusted_code_policy']:
   if doc[n] not in ['ALLOW','FORBID']:raise ValueError('Declare ALLOW or FORBID: '+n)
   if doc[n]=='FORBID':raise Skipped('Policy FORBID: '+n+'; original analysis skipped')
  if sys.platform!='linux':raise Skipped('Original execution requires the captured Linux/WSL CPU runtime')
  ready,provider_pins=verify_provider(directory);protected.update(provider_pins)
  if ready['version']!=doc['version']:raise Skipped('Selected original provider edition differs from the literal request; supply the exact provider')
  prefix=Path(ready['prefix'])
  if out.is_relative_to(prefix) or prefix.is_relative_to(out):raise ValueError('Output overlaps original runtime')
  regular(script)
  if script.stat().st_size>16777216:raise ValueError('Complete trusted UTF-8 script exceeds16MiB')
  script_raw=script.read_bytes();script_raw.decode('utf8',errors='strict');protected[script]=pin(script);protected[request]=request_pin
  for n in ['run.py','Provider.py','OriginalWorker.py','runtime_audit.py','source-profile.json','runtime-profile.json']:
   p=HERE/n;regular(p);protected[p]=pin(p)
  (out/'script.py').write_bytes(script_raw);protected[out/'script.py']=protected[script]
  for n in ['OriginalWorker.py','runtime_audit.py','source-profile.json','runtime-profile.json']:
   shutil.copyfile(HERE/n,out/n);protected[out/n]=protected[HERE/n]
  captured=out/'inputs';results=out/'results';diagnostics=out/'diagnostics'
  for p in [captured,results,diagnostics,out/'work',out/'tmp',out/'cache']:p.mkdir()
  input_pins={}
  for n,p in inputs.items():
   regular(p);wanted=pin(p);target=captured/n;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,target);same(target,wanted);same(p,wanted);protected[p]=wanted;protected[target]=wanted;input_pins[n]=wanted
  save(out/'input-pins.json',input_pins);save(out/'original-provider.json',ready)
  native_temporary=Path(tempfile.mkdtemp(prefix='omicsmatic-scvelo-',dir='/tmp')).resolve();temporary_alias=str(native_temporary)
  if len(temporary_alias.encode())>60:raise Skipped('Short native Linux temporary prerequisite unavailable; dependent analysis skipped')
  temporary_identity=dict(directory=str(native_temporary),alias=temporary_alias,controller_pid=os.getpid(),retained_copy='native-temporary',scope='Dedicated short native-Linux temporary root owned by this analysis. Analysis and native temporary roots share admission capacities; available temporary bytes are copied into the final analysis bundle. Original native root remains retained.')
  save(out/'temporary-directory.json',temporary_identity);protected[out/'temporary-directory.json']=pin(out/'temporary-directory.json')
  control=dict(version=doc['version'],prefix=ready['prefix'],profile=str(out/'source-profile.json'),runtime_profile=str(out/'runtime-profile.json'),temporary_directory=str(native_temporary),temporary_alias=temporary_alias,required_symbols=symbols,required_distributions=packages,script=str(out/'script.py'),inputs=str(captured),results=str(results),diagnostics=str(diagnostics),expected_return=doc['expected_return'],input_provenance=doc['input_provenance'],analysis_population=doc['analysis_population'])
  save(out/'worker-control.json',control);protected[out/'worker-control.json']=pin(out/'worker-control.json')
  env={k:v for k,v in os.environ.items() if k not in ['PYTHONPATH','PYTHONHOME','LD_PRELOAD','LD_LIBRARY_PATH']}
  env.update(PATH=str(prefix/ready['venv']/'bin')+':/usr/bin:/bin',PYTHONNOUSERSITE='1',PYTHONDONTWRITEBYTECODE='1',TMPDIR=temporary_alias,MPLBACKEND='Agg',MPLCONFIGDIR=str(out/'cache/matplotlib'),NUMBA_CACHE_DIR=str(out/'cache/numba'),XDG_CACHE_HOME=str(out/'cache'),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
  workspace(out,limits,native_temporary)
  # Worker cwd stays in the owned root; every package-created file remains inventoried.
  result=execute([ready['python'],'-B',out/'OriginalWorker.py',out/'worker-control.json'],out,env,limits,native_temporary)
  try:
   for p,wanted in protected.items():same(p,wanted)
   # Recheck complete link/directory/member populations as well as every file byte.
   verify_provider(directory)
  except OSError as error:raise ValueError('Protected prerequisite disappeared or became unavailable after the original worker: '+repr(error)) from error
  status_file=diagnostics/'worker-status.json'
  if not status_file.is_file():
   if not (diagnostics/'analysis-started.json').is_file():raise Skipped('Original Python startup/readiness unavailable; inspect full console')
   raise ValueError('Original script did not leave complete worker diagnostics')
  status=load(status_file);warnings_count,logs_count,errors_count=counts(status)
  if result==3 and status['exit_code']==3 and status['phase']=='READINESS':raise Skipped('Required original dependency/symbol unavailable; retain original error and full logs')
  if result!=0 or status['status']!='ORIGINAL_SCVELO_SCRIPT_COMPLETE' or status['exit_code']!=0 or errors_count!=0 or not status['returned_marker_matches']:raise ValueError('Original script failed or did not return the complete declared population')
  if set(tree(captured,limits['maximum_files'],limits['maximum_workspace_bytes'],limits['maximum_file_bytes']))!=set(inputs):raise ValueError('Captured input population changed')
  allowed={'source-request.json','script.py','OriginalWorker.py','runtime_audit.py','source-profile.json','runtime-profile.json','temporary-directory.json','inputs','results','diagnostics','work','tmp','cache','input-pins.json','original-provider.json','worker-control.json','process.stdout','process.stderr','process-live.json','process-exit.json'}
  if {p.name for p in out.iterdir()}-allowed:raise ValueError('Unexpected file outside declared result/owned diagnostic/cache directories')
  actual=tree(results,limits['maximum_files'],limits['maximum_workspace_bytes'],limits['maximum_file_bytes'],True)
  if set(actual)!=set(outputs):raise ValueError('Missing/extra complete result files; no partial population admitted')
  for n,minimum in outputs.items():
   if actual[n]['bytes']<minimum:raise ValueError('Incomplete original result file: '+n)
  save(out/'result-files.json',actual);admitted=True;code=0;cause='PASS_COMPLETE_DECLARED_ORIGINAL_SCVELO_SCRIPT_OUTPUTS'
 except (Skipped,Unavailable) as error:code=3;cause='SKIP3: '+str(error);failure=traceback.format_exc()
 except OSError as error:
  code=2 if isinstance(error,FileExistsError) or owned and (out/'diagnostics/analysis-started.json').is_file() else 3;cause=('FAIL2: ' if code==2 else 'SKIP3: ')+repr(error);failure=traceback.format_exc()
 except BaseException as error:code=2;cause='FAIL2: '+repr(error);failure=traceback.format_exc()
 finally:
  if owned:
   totals_complete=not (out/'process-live.json').exists();recording_failed=False
   if native_temporary is not None:
    try:
     native_inventory=retain_native_temporary(native_temporary,out)
     if admitted:workspace(out,limits,native_temporary)
    except BaseException as error:
     recording_failed=True;code=2;admitted=False;cause='FAIL2: native temporary retention/capacity incomplete; '+cause;print(cause,repr(error),'\n'+ACTION,flush=True)
     try:(out/'NATIVE_TEMPORARY_ERROR.txt').write_text(traceback.format_exc(),encoding='utf8')
     except BaseException as recording:print('FAIL2: native temporary error record unavailable',repr(recording),flush=True)
   if failure:
    try:(out/'SUPERVISOR_ERROR.txt').write_bytes(failure.encode('utf8'))
    except BaseException as e:print('FAIL2: supervisor traceback file unavailable; retain console',repr(e),failure,flush=True);recording_failed=True
   status_file=out/'diagnostics/worker-status.json'
   try:
    if status_file.exists() or status_file.is_symlink():regular(status_file);warnings_count,logs_count,errors_count=counts(load(status_file));totals_complete=True
    elif not totals_complete:raise ValueError('Worker launched without a complete worker-status.json')
   except BaseException as e:
    totals_complete=False;code=2;admitted=False;cause='FAIL2: '+cause+'; original condition totals unavailable: '+repr(e)
    try:(out/'WORKER_DIAGNOSTIC_ERROR.txt').write_bytes((repr(e)+'\nRetain the raw worker report and full console; unknown condition totals are not zero.\n').encode('utf8'))
    except BaseException as recording:print('FAIL2: worker diagnostic file unavailable',repr(recording),flush=True);recording_failed=True
   if not totals_complete:warnings_count=logs_count=errors_count=None
   def warning_record():
    show=lambda value:'unavailable (worker diagnostics incomplete)' if value is None else str(value)
    return ('# '+cause+'\n\nDelivered Python warnings in original runtime and caller script: '+show(warnings_count)+'; delivered warning/error log records: '+show(logs_count)+'; caught original errors: '+show(errors_count)+'. Unknown totals are unavailable, never zero. Review every condition and full console.\n\n'+ACTION+'\n').encode('utf8')
   try:(out/'WARNINGS.md').write_bytes(warning_record())
   except BaseException as e:print('FAIL2: could not write WARNINGS.md',repr(e),'\n'+ACTION,flush=True);recording_failed=True
   inventory_complete=False
   try:
    inventory=tree(out,1000000,137438953472,137438953472,True,retain_link_metadata=True)
    for n in ['MANIFEST.json','analysis-status.json']:inventory.pop(n,None)
    save(out/'MANIFEST.json',dict(files=inventory,native_temporary=native_inventory,excluded_final_records=['MANIFEST.json','analysis-status.json']));inventory_complete=True
   except BaseException as e:print('FAIL2: could not complete MANIFEST.json',repr(e),'\n'+ACTION,flush=True);recording_failed=True
   if recording_failed:
    code=2;admitted=False;cause='FAIL2: diagnostic recording incomplete; '+cause
    try:(out/'WARNINGS.md').write_bytes(warning_record())
    except BaseException as e:print('FAIL2: could not write final WARNINGS.md',repr(e),'\n'+ACTION,flush=True)
    try:
     inventory=tree(out,1000000,137438953472,137438953472,True,retain_link_metadata=True)
     for n in ['MANIFEST.json','analysis-status.json']:inventory.pop(n,None)
     save(out/'MANIFEST.json',dict(files=inventory,native_temporary=native_inventory,excluded_final_records=['MANIFEST.json','analysis-status.json']));inventory_complete=True
    except BaseException as e:inventory_complete=False;print('FAIL2: could not refresh final MANIFEST.json',repr(e),'\n'+ACTION,flush=True)
   status=dict(status=cause,exit_code=code,result_admitted=admitted,original_warnings=warnings_count,original_log_conditions=logs_count,original_errors=errors_count,condition_counts_complete=totals_complete,warning_review_required=code!=0 or recording_failed or not totals_complete or warnings_count>0 or logs_count>0,manifest_complete=inventory_complete,original_version=doc['version'],condition_scope='trusted_script_and_original_runtime_delivered_conditions',native_temporary_root=str(native_temporary) if native_temporary is not None else None,native_temporary_copy_complete=native_inventory is not None,paper_reproduced=False,whole_tool_qualified=False,whole_goal_complete=False,required_action=ACTION)
   try:save(out/'analysis-status.json',status)
   except BaseException as e:
    code=2;admitted=False;cause='FAIL2: could not write analysis-status.json: '+repr(e);print(cause+'\n'+ACTION,flush=True)
    try:(out/'WARNINGS.md').write_bytes(warning_record())
    except BaseException as recording:print('FAIL2: final warnings record unavailable',repr(recording),'\n'+ACTION,flush=True)
    try:
     inventory=tree(out,1000000,137438953472,137438953472,True,retain_link_metadata=True)
     for n in ['MANIFEST.json','analysis-status.json']:inventory.pop(n,None)
     save(out/'MANIFEST.json',dict(files=inventory,native_temporary=native_inventory,excluded_final_records=['MANIFEST.json','analysis-status.json']))
    except BaseException as recording:print('FAIL2: final inventory record unavailable',repr(recording),'\n'+ACTION,flush=True)
 print(cause+'\n'+ACTION,flush=True);return code

if __name__=='__main__':raise SystemExit(main())
