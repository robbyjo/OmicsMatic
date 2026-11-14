#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the exact trusted Python script in the unchanged original scvelo namespace."""
from pathlib import Path
import importlib,importlib.metadata as md,json,logging,os,re,runpy,socket,sys,tempfile,traceback,types,warnings
sys.dont_write_bytecode=True

def save(p,v):p.write_bytes((json.dumps(v,indent=2,ensure_ascii=False,allow_nan=False)+'\n').encode('utf8'))

def main():
 control=json.loads(Path(sys.argv[1]).read_bytes());diagnostics=Path(control['diagnostics']);conditions=[];phase='READINESS';marker=False;code=2;original_warning=warnings.showwarning;original_log_dispatch=logging.Logger.callHandlers;log_capture_complete=True
 def record_warning(message,category,filename,lineno,file=None,line=None):
  conditions.append(dict(kind='warning',category=category.__module__+'.'+category.__qualname__,message=str(message),filename=filename,lineno=lineno))
  return original_warning(message,category,filename,lineno,file=file,line=line)
 def record_log(logger,record):
  nonlocal log_capture_complete
  if record.levelno>=logging.WARNING:
   try:conditions.append(dict(kind='log',logger=record.name,level=record.levelname,message=record.getMessage(),filename=record.pathname,lineno=record.lineno))
   except BaseException as recording:log_capture_complete=False;conditions.append(dict(kind='log-capture-error',cause=repr(recording)))
  return original_log_dispatch(logger,record)
 # Observe dispatch without adding a root handler: doing so would prevent
 # logging.warning() from configuring its original default console handler.
 logging.Logger.callHandlers=record_log;warnings.showwarning=record_warning
 try:
  from runtime_audit import import_original
  version=control['version'];profile=json.loads(Path(control['runtime_profile']).read_bytes())['profiles'][version];prefix=Path(control['prefix'])
  import_original(prefix,version,profile)
  import scvelo
  if os.environ.get('TMPDIR')!=control['temporary_alias'] or not os.path.samefile(control['temporary_alias'],control['temporary_directory']):raise OSError('Owned short temporary-directory reference unavailable; dependent analysis skipped')
  probe_directory=Path(tempfile.mkdtemp(prefix='pymp-',dir=control['temporary_directory']));probe_path=probe_directory/'listener-ready000';probe=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM)
  try:
   probe.bind(str(probe_path));probe.listen(1);info=probe_path.stat();save(diagnostics/'ipc-readiness.json',dict(status='READY_OWNED_NATIVE_LINUX_UNIX_SOCKET',temporary_directory=control['temporary_directory'],socket_device=info.st_dev,socket_inode=info.st_ino,analysis_executed=False))
  finally:
   probe.close()
   if probe_path.exists():probe_path.unlink()
   probe_directory.rmdir()
  for symbol in control['required_symbols']:
   value=scvelo
   for part in symbol.split('.'):
    if not re.fullmatch('[A-Za-z_][A-Za-z0-9_]*',part):raise OSError('Invalid literal original symbol: '+symbol)
    if not hasattr(value,part) and isinstance(value,types.ModuleType):
     try:importlib.import_module(value.__name__+'.'+part)
     except ImportError as error:raise OSError('Literal original symbol/module unavailable; no spelling/API substitution: '+symbol) from error
    if not hasattr(value,part):raise OSError('Literal original symbol unavailable; no spelling/API substitution: '+symbol)
    value=getattr(value,part)
  for name,version in control['required_distributions'].items():
   if profile['installed_distributions'].get(name)!=version:raise OSError('Literal required installed package/version unavailable: '+name+'=='+version)
  phase='ANALYSIS';save(diagnostics/'analysis-started.json',dict(original_version=control['version'],population=control['analysis_population']))
  result=runpy.run_path(control['script'],init_globals=dict(input_directory=Path(control['inputs']),result_directory=Path(control['results']),diagnostic_directory=diagnostics,analysis_population=control['analysis_population'],original_scvelo=scvelo,selected_original_version=control['version']),run_name='__omicsmatic_original_scvelo_analysis__')
  marker=isinstance(result.get('analysis_complete'),str) and result['analysis_complete']==control['expected_return']
  if not marker:raise ValueError('Exact complete-population return marker missing or different')
  code=0
 except BaseException as error:
  code=3 if phase=='READINESS' and isinstance(error,(OSError,ImportError)) else 2
  conditions.append(dict(kind='error',category=type(error).__module__+'.'+type(error).__qualname__,message=str(error),phase=phase));traceback.print_exc()
  try:(diagnostics/'original-error.txt').write_bytes(traceback.format_exc().encode('utf8'))
  except BaseException as recording:print('FAIL2: could not retain original-error.txt; retain console',repr(recording),file=sys.stderr,flush=True);code=2
 finally:
  warnings.showwarning=original_warning;logging.Logger.callHandlers=original_log_dispatch
  warning_count=sum(c['kind']=='warning' for c in conditions);log_count=sum(c['kind']=='log' for c in conditions);error_count=sum(c['kind']=='error' for c in conditions)
  status=dict(status='ORIGINAL_SCVELO_SCRIPT_COMPLETE' if code==0 else 'ORIGINAL_SCVELO_SCRIPT_UNADMITTED',exit_code=code,phase=phase,returned_marker_matches=marker,warning_count=warning_count,log_count=log_count if log_capture_complete else None,error_count=error_count,warning_filters_modified_by_worker=False,warning_as_error_semantics_preserved=True,original_default_log_handlers_preserved=True)
  for name,value in [('conditions.json',conditions),('worker-status.json',status)]:
   try:save(diagnostics/name,value)
   except BaseException as recording:print('FAIL2: could not write',name,repr(recording),'retain complete console and original objects',file=sys.stderr,flush=True);code=2
 return code
if __name__=='__main__':raise SystemExit(main())
