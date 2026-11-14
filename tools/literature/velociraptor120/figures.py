#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run original R-only figure calls; retain full graph RDS and complete SVG/PNG."""
from pathlib import Path
import argparse,hashlib,math,shutil,sys,tempfile,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,Unavailable,load,pin,regular,same,save
from PlotRuntime import PLOT_ACTION,provider,environment
from run import canonical,text,member,integer,tree,execute,capture_native,condition_counts
from FinalRecords import finalize

AUTOMATIC={'original-graph.rds','render-options.rds','figure.svg','figure.png','graph-after-render.rds','render-rng-state.rds'}

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=PLOT_ACTION);parser.add_argument('--request',type=Path,required=True);args=parser.parse_args()
 out=native=None;owned=False;admitted=False;code=2;cause='FAIL2_INVALID_FIGURE_REQUEST';counts=[None,None,None];counts_complete=False;error_trace=None
 try:
  request=args.request.absolute();regular(request)
  if request!=request.resolve() or request.stat().st_size>1048576:raise ValueError('Canonical complete figure request at most 1 MiB required')
  raw=request.read_bytes();document=load(request);request_pin=pin(request)
  if request_pin!=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()):raise ValueError('Figure request changed during capture')
  fields={'schema_version','version','mode','script','provider_directory','inputs','expected_outputs','required_exports','required_packages','expected_return','input_provenance','analysis_population','execution_policy','trusted_code_policy','out','limits','render'}
  if not isinstance(document,dict) or set(document)!=fields or document['schema_version']!=1 or type(document['schema_version'])!=int or document['version']!='1.20.0' or document['mode']!='FIGURES':raise ValueError('Exact original1.20.0 FIGURES request fields/version required')
  parent=request.parent;out=canonical(document['out'],parent);script=canonical(document['script'],parent);directory=canonical(document['provider_directory'],parent)
  if not isinstance(document['inputs'],dict) or not document['inputs'] or not isinstance(document['expected_outputs'],dict):raise ValueError('Declare complete original inputs and additional caller outputs')
  inputs={member(name):canonical(value,parent) for name,value in document['inputs'].items()};outputs={member(name):integer(value,0,137438953472) for name,value in document['expected_outputs'].items()}
  for names in [inputs,outputs]:
   if len(names)>4096 or len({name.casefold() for name in names})!=len(names):raise ValueError('Duplicate/excessive logical filename population')
   for name in names:
    if any(other.casefold().startswith(name.casefold()+'/') for other in names):raise ValueError('Overlapping file/directory logical names')
  for name in outputs:
   if any(name.casefold()==reserved or name.casefold().startswith(reserved+'/') or reserved.startswith(name.casefold()+'/') for reserved in AUTOMATIC):raise ValueError('Caller output overlaps an automatic complete graph artifact')
  exports=document['required_exports'];packages=document['required_packages']
  if not isinstance(exports,list) or not exports or any(not isinstance(name,str) for name in exports) or len(set(exports))!=len(exports):raise ValueError('Unique original figure exports required')
  if not isinstance(packages,dict) or any(not isinstance(name,str) or not isinstance(version,str) for name,version in packages.items()):raise ValueError('Literal dependency names/versions required')
  for name in ['expected_return','input_provenance','analysis_population']:text(document[name])
  limits=document['limits'];capacities={'timeout_seconds':(1,604800),'maximum_files':(32,1000000),'maximum_workspace_bytes':(1024,137438953472),'maximum_file_bytes':(1,137438953472)}
  if not isinstance(limits,dict) or set(limits)!=set(capacities):raise ValueError('Declare all four figure workspace/wall-time capacities')
  for name,bounds in capacities.items():integer(limits[name],*bounds)
  render=document['render']
  if not isinstance(render,dict) or set(render)!={'width_inches','height_inches','png_dpi','seed','background'}:raise ValueError('Declare exact complete figure dimensions, PNG dpi, renderer seed and background')
  for name in ['width_inches','height_inches']:
   value=render[name]
   if type(value) not in [int,float] or not math.isfinite(value) or not 0<value<=50:raise ValueError('Finite positive figure dimension at most50 inches required')
  integer(render['png_dpi'],36,600);integer(render['seed'],0,2147483647);text(render['background'])
  if render['width_inches']*render['height_inches']*render['png_dpi']**2>100000000:raise ValueError('Declared PNG exceeds100 million pixels')
  for path in [request,script,directory,HERE,*inputs.values()]:
   if out.is_relative_to(path) or path.is_relative_to(out):raise ValueError('NEW figure output overlaps protected source/provider/input')
  out.mkdir(exist_ok=False);owned=True;(out/'source-request.json').write_bytes(raw)
  for name in ['execution_policy','trusted_code_policy']:
   if document[name] not in ['ALLOW','FORBID']:raise ValueError('Explicit ALLOW or FORBID required: '+name)
   if document[name]=='FORBID':raise Unavailable('Policy FORBID: '+name+'; dependent figure analysis skipped')
  if sys.platform!='linux':raise Unavailable('Use the declared original Linux/WSL figure profile')
  if set(exports)-{'plotVelocity','plotVelocityStream'}:raise Unavailable('This R-only FIGURES profile provides original graph exports; use the numeric fitting/embedding workflow for other operations')
  ready=provider(directory);regular(script)
  if script.stat().st_size>16777216:raise ValueError('Trusted original figure script exceeds16 MiB')
  script_raw=script.read_bytes();script_raw.decode('utf-8',errors='strict')
  source_names=['figures.py','FinalRecords.py','FiguresWorker.R','AuditPlots.R','PlotRuntime.py','Runtime.py','run.py','RValues.py','source-profile.json'];sources={name:pin(HERE/name) for name in source_names}
  protected={request:request_pin,script:pin(script),directory/'READY.json':pin(directory/'READY.json'),**{HERE/name:wanted for name,wanted in sources.items()}}
  for name in ['inputs','results','diagnostics','owned-home','tmp','unused-external-cache','adapter-source']:(out/name).mkdir()
  native=Path(tempfile.mkdtemp(prefix='vfp-',dir='/tmp'));save(out/'native-temporary.json',dict(path=str(native),original_root_deleted=False,durable_copy='native-temporary',live_IPC_or_process_memory_reconstructed=False))
  for name in source_names:
   dest=out/'adapter-source'/name;shutil.copyfile(HERE/name,dest);same(dest,sources[name]);protected[dest]=sources[name]
  (out/'script.R').write_bytes(script_raw);same(out/'script.R',protected[script]);protected[out/'script.R']=protected[script]
  input_pins={}
  for name,path in inputs.items():
   regular(path);wanted=pin(path);dest=out/'inputs'/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,dest);same(dest,wanted);same(path,wanted);protected[path]=wanted;protected[dest]=wanted;input_pins[name]=wanted
  save(out/'input-pins.json',input_pins);shutil.copyfile(directory/'READY.json',out/'original-provider.json')
  control=dict(library=ready['library'],base_library=ready['base_library'],source_root=ready['source_root'],audit_script=str(out/'adapter-source/AuditPlots.R'),inputs=str(out/'inputs'),results=str(out/'results'),diagnostics=str(out/'diagnostics'),script=str(out/'script.R'),native_temporary_directory=str(native),required_exports=exports,required_packages=packages,expected_return=document['expected_return'],input_provenance=document['input_provenance'],analysis_population=document['analysis_population'],render=render)
  save(out/'worker-control.json',control);protected[out/'worker-control.json']=pin(out/'worker-control.json');tree(out,limits)
  result=execute([ready['rscript'],'--vanilla',out/'adapter-source/FiguresWorker.R',out/'worker-control.json'],out,environment(ready,out,temporary_directory=native),limits,native)
  for path,wanted in protected.items():same(path,wanted)
  provider(directory)
  status_path=out/'diagnostics/worker-status.json'
  if not status_path.is_file():raise ValueError('Figure worker ended without complete original diagnostics')
  regular(status_path);worker=load(status_path);counts=condition_counts(worker);counts_complete=True
  if result==3 and worker['exit_code']==3 and worker['phase']=='READINESS':raise Unavailable('Required original figure dependency unavailable; inspect retained conditions')
  if result!=0 or worker['status']!='COMPLETE_ORIGINAL_VELOCIRAPTOR120_R_FIGURES' or worker['exit_code']!=0 or counts[2] or not worker['complete_original_graph_serialized'] or not worker['complete_original_SVG_and_PNG_rendered'] or not worker['original_R_temporary_tree_retained'] or worker['python_initialized'] or worker['graph_environment_pointer_or_opaque_RDS_byte_parity_claimed']:raise ValueError('Original graph serialization/rendering did not complete the declared whole figure')
  actual=tree(out/'results',limits,True)
  if set(actual)!=AUTOMATIC|set(outputs):raise ValueError('Missing/extra complete graph/caller artifact population')
  for name,minimum in {**{name:1 for name in AUTOMATIC},**outputs}.items():
   if actual[name]['bytes']<minimum:raise ValueError('Incomplete original graph artifact: '+name)
  if not (out/'results/figure.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n') or b'<svg' not in (out/'results/figure.svg').read_bytes():raise ValueError('Complete original rendered artifact headers differ')
  save(out/'result-files.json',actual);admitted=True;code=0;cause='PASS_COMPLETE_DECLARED_ORIGINAL_VELOCIRAPTOR120_R_FIGURE_ARTIFACTS'
 except Unavailable as error:code=2 if owned and (out/'diagnostics/analysis-started.json').is_file() else 3;cause=('FAIL2: ' if code==2 else 'SKIP3: ')+str(error);error_trace=traceback.format_exc()
 except OSError as error:code=2 if isinstance(error,FileExistsError) or owned and (out/'process-live.json').is_file() else 3;cause=('FAIL2: ' if code==2 else 'SKIP3: ')+repr(error);error_trace=traceback.format_exc()
 except BaseException as error:code=2;cause='FAIL2: '+repr(error);error_trace=traceback.format_exc()
 finally:
  if owned:
   if native is not None:
    try:capture_native(native,out)
    except BaseException as error:code=2;admitted=False;cause='FAIL2_NATIVE_FIGURE_TEMPORARY_CAPTURE_INCOMPLETE: '+repr(error)+'; '+cause;print(cause,PLOT_ACTION,flush=True)
   try:
    if error_trace:(out/'SUPERVISOR_ERROR.txt').write_text(error_trace,encoding='utf-8',newline='\n')
    path=out/'diagnostics/worker-status.json'
    if path.is_file():regular(path);counts=condition_counts(load(path));counts_complete=True
   except BaseException as error:code=2;admitted=False;counts=[None,None,None];counts_complete=False;cause='FAIL2_FIGURE_DIAGNOSTIC_RECORDING_ERROR: '+repr(error)+'; '+cause
   code,cause=finalize(out,code=code,cause=cause,admitted=admitted,counts=counts,counts_complete=counts_complete,action=PLOT_ACTION,tree=tree,save=save,fields=dict(return_representation='complete original graph RDS and complete declared SVG/PNG artifacts',graph_environment_pointer_or_opaque_RDS_byte_parity_claimed=False,provides_Python_fitting_backend=False,whole_R_wrapper_qualified=False,new_family_credit=0,whole_goal_complete=False))
 print(cause+'\n'+PLOT_ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
