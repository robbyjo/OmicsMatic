#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the exact trusted Python script in the unchanged original cell2location namespace."""
from pathlib import Path
import importlib,importlib.metadata as md,json,logging,os,re,runpy,sys,traceback,types,warnings
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
  profile=json.loads(Path(control['profile']).read_bytes());prefix=Path(control['prefix'])
  def records():return sorted([dict(name=d.metadata['Name'],version=d.version,location=Path(d.locate_file('')).relative_to(prefix).as_posix()) for d in md.distributions()],key=lambda d:(d['name'].lower(),d['version'],d['location']))
  if records()!=profile['installed_distribution_records']:raise OSError('Exact installed dependency records unavailable')
  import cell2location,torch,scvi,pyro,cv2,plotnine,adjustText
  if cell2location.__version__!='0.1.3' or torch.__version__!='2.0.1+cpu' or torch.cuda.is_available():raise OSError('Exact original CPU runtime unavailable')
  if records()!=profile['import_visible_distribution_records']:raise OSError('Exact original installed/vendored import behavior unavailable')
  for name,wanted in profile['imported_module_origins'].items():
   module=sys.modules.get(name)
   if module is None or not getattr(module,'__file__',None) or dict(file=Path(module.__file__).resolve().relative_to(prefix).as_posix(),declared_version=getattr(module,'__version__',None))!=wanted:raise OSError('Original module source origin differs: '+name)
  for symbol in control['required_symbols']:
   value=cell2location
   for part in symbol.split('.'):
    if not re.fullmatch('[A-Za-z_][A-Za-z0-9_]*',part):raise OSError('Invalid literal original symbol: '+symbol)
    if not hasattr(value,part) and isinstance(value,types.ModuleType):
     try:importlib.import_module(value.__name__+'.'+part)
     except ImportError as error:raise OSError('Literal original symbol/module unavailable; no spelling/API substitution: '+symbol) from error
    if not hasattr(value,part):raise OSError('Literal original symbol unavailable; no spelling/API substitution: '+symbol)
    value=getattr(value,part)
  for name,version in control['required_distributions'].items():
   if profile['installed_distributions'].get(name)!=version:raise OSError('Literal required installed package/version unavailable: '+name+'=='+version)
  phase='ANALYSIS';save(diagnostics/'analysis-started.json',dict(original_version='0.1.3',population=control['analysis_population']))
  result=runpy.run_path(control['script'],init_globals=dict(input_directory=Path(control['inputs']),result_directory=Path(control['results']),diagnostic_directory=diagnostics,analysis_population=control['analysis_population'],original_cell2location=cell2location),run_name='__omicsmatic_original_cell2location_analysis__')
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
  status=dict(status='ORIGINAL_CELL2LOCATION013_SCRIPT_COMPLETE' if code==0 else 'ORIGINAL_CELL2LOCATION013_SCRIPT_UNADMITTED',exit_code=code,phase=phase,returned_marker_matches=marker,warning_count=warning_count,log_count=log_count if log_capture_complete else None,error_count=error_count,warning_filters_modified_by_worker=False,warning_as_error_semantics_preserved=True,original_default_log_handlers_preserved=True)
  for name,value in [('conditions.json',conditions),('worker-status.json',status)]:
   try:save(diagnostics/name,value)
   except BaseException as recording:print('FAIL2: could not write',name,repr(recording),'retain complete console and original objects',file=sys.stderr,flush=True);code=2
 return code
if __name__=='__main__':raise SystemExit(main())
