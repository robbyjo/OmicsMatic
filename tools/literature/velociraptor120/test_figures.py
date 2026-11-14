#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare complete original figure artifacts and serialized-graph replays."""
from pathlib import Path
import argparse,subprocess,sys,time,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,load,pin,save
from PlotRuntime import PLOT_ACTION,provider,environment

CASES=['plotVelocity-all-panels','plotVelocityStream-plain','plotVelocityStream-colored']
SCRIPT='''call <- readRDS(file.path(input_directory,"call.rds"))
set.seed(as.integer(call$render$seed))
analysis_result <- do.call(getExportedValue("velociraptor",call$export),call$arguments)
analysis_complete <- paste0("COMPLETE_ORIGINAL_FIGURE_",call$scenario)
'''

def main():
 parser=argparse.ArgumentParser(description=__doc__)
 for name in ['provider','originals','out']:parser.add_argument('--'+name,type=Path,required=True)
 parser.add_argument('--cases',choices=CASES,nargs='+',default=CASES);args=parser.parse_args();out=args.out.absolute();owned=False;rows=[];calls=[]
 try:
  if sys.platform!='linux':raise OSError('Use the original Linux/WSL R-only figure profile')
  if out!=out.resolve():raise ValueError('Canonical NEW full-figure comparison output required')
  for protected in [HERE,args.provider.resolve(),args.originals.resolve()]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Test output overlaps protected source/provider/original figures')
  out.mkdir(exist_ok=False);owned=True;ready=provider(args.provider)
  names=['figures.py','FiguresWorker.R','PlotRuntime.py','AuditPlots.R','Runtime.py','run.py','RValues.py','source-profile.json','ReplayGraph.R','test_figures.py'];sources={name:pin(HERE/name) for name in names};save(out/'SOURCE-PINS.json',sources)
  def execute(label,command,env=None):
   command=list(map(str,command));start=time.monotonic()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/'LIVE.json',dict(label=label,pid=child.pid,command=command,started_unix=time.time()))
    while True:
     try:code=child.wait(timeout=20);break
     except subprocess.TimeoutExpired:print('SAME_FULL_FIGURE_COMPARISON_CHILD_RUNNING',label,child.pid,round(time.monotonic()-start),flush=True)
   calls.append(dict(label=label,command=command,exit_code=code,elapsed_seconds=time.monotonic()-start));save(out/'PROCESSES.json',calls);return code
  render=dict(width_inches=6,height_inches=4,png_dpi=120,seed=882341,background='white');save(out/'render.json',render)
  for scenario in args.cases:
   original=args.originals/scenario;reference=load(original/'FIGURE-STATUS.json');case=out/scenario;case.mkdir();script=case/'Analysis.R';script.write_text(SCRIPT,encoding='utf-8',newline='\n');actual=case/'adapter'
   export='plotVelocityStream' if scenario.startswith('plotVelocityStream') else 'plotVelocity'
   request=dict(schema_version=1,version='1.20.0',mode='FIGURES',script=str(script),provider_directory=str(args.provider),inputs={'call.rds':str(original/'COMPLETE-ORIGINAL-CALL.rds')},expected_outputs={},required_exports=[export],required_packages={},expected_return='COMPLETE_ORIGINAL_FIGURE_'+scenario,input_provenance='Complete separately retained original plot call over all160 synthetic cells and12 fitted genes; supplied axis labels/coordinates are passed literally.',analysis_population='Explicit original '+scenario+' control; caller-selected genes and complete arguments retained in original RDS. Whole SVG/PNG compared without identifier or numeric normalization.',execution_policy='ALLOW',trusted_code_policy='ALLOW',out=str(actual),limits=dict(timeout_seconds=600,maximum_files=20000,maximum_workspace_bytes=2147483648,maximum_file_bytes=536870912),render=render)
   request_path=case/'request.json';save(request_path,request);code=execute(scenario+'-adapter',[sys.executable,'-B',HERE/'figures.py','--request',request_path]);status=load(actual/'analysis-status.json')
   if reference['status']!='COMPLETE_DIRECT_ORIGINAL_FIGURE':
    worker=load(actual/'diagnostics/worker-status.json')
    if code!=2 or status['result_admitted'] or worker['error']['class']!=reference['error']['class'] or worker['error']['message']!=reference['error']['message']:raise ValueError('Original figure failure class/message or refusal behavior differs')
    row=dict(scenario=scenario,status='PASS_MATCHING_ORIGINAL_FIGURE_ERROR_NOT_SUPPORTED_INPUT',original_error=reference['error'],complete_original_and_adapter_diagnostics_retained=True,adapter=status)
   else:
    if code or not status['result_admitted']:raise ValueError('Adapter did not admit the complete original figure')
    files={}
    for suffix in ['svg','png']:
     left=original/('COMPLETE-ORIGINAL-FIGURE.'+suffix);right=actual/'results'/('figure.'+suffix)
     if left.read_bytes()!=right.read_bytes():raise ValueError('Complete original '+suffix.upper()+' bytes differ; do not normalize IDs, numbers, paths or pixels')
     files[suffix]=pin(right)
    replays=[]
    for label,graph in [('original',original/'COMPLETE-ORIGINAL-GRAPH-BEFORE-RENDER.rds'),('adapter',actual/'results/original-graph.rds')]:
     replay=case/(label+'-RDS-replay');replay.mkdir()
     for name in ['owned-home','tmp','unused-external-cache']:(replay/name).mkdir()
     code=execute(scenario+'-'+label+'-RDS-replay',[ready['rscript'],'--vanilla',HERE/'ReplayGraph.R',ready['library'],graph,out/'render.json',replay],environment(ready,replay))
     if code:raise ValueError('Complete original graph RDS could not be re-rendered: '+label)
     for suffix in ['svg','png']:
      if (replay/('figure.'+suffix)).read_bytes()!=(original/('COMPLETE-ORIGINAL-FIGURE.'+suffix)).read_bytes():raise ValueError('Complete serialized-graph replay differs from original live figure: '+label+'/'+suffix)
     replays.append(dict(source=label,graph=pin(graph),rendered_artifacts=files,status=load(replay/'REPLAY-STATUS.json')))
    row=dict(scenario=scenario,status='PASS_COMPLETE_ORIGINAL_FIGURE_BYTES_AND_BOTH_GRAPH_RDS_REPLAYS',complete_SVG_and_PNG=files,graph_RDS_replays=replays,graph_environment_pointer_or_opaque_RDS_byte_parity_claimed=False,adapter=status)
   rows.append(row);save(out/'COMPLETE-CASES.json',rows);print(row['status'],scenario,flush=True)
  for name,wanted in sources.items():
   if pin(HERE/name)!=wanted:raise ValueError('Executed figure source changed: '+name)
  provider(args.provider)
  result=dict(status='PASS_COMPLETE_ORIGINAL_FIGURE_AND_ERROR_CASE_POPULATION',cases=rows,calls=calls,source_pins=sources,complete_figure_cases=sum(row['status']=='PASS_COMPLETE_ORIGINAL_FIGURE_BYTES_AND_BOTH_GRAPH_RDS_REPLAYS' for row in rows),matching_original_error_cases=sum(row['status']=='PASS_MATCHING_ORIGINAL_FIGURE_ERROR_NOT_SUPPORTED_INPUT' for row in rows),Python_analysis_backend_initialized=False,new_family_credit=0,whole_R_wrapper_qualified=False,whole_goal_complete=False)
  save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
 except BaseException as error:
  if owned:save(out/'FAILURE.json',dict(status='FAIL2_COMPLETE_FIGURE_CASES_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),completed_cases=rows,calls=calls,required_action=PLOT_ACTION))
  print('FAIL2_COMPLETE_FIGURE_CASES_UNADMITTED',repr(error),PLOT_ACTION,flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
