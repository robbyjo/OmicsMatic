#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare two complete adapter calls to separately retained original R references."""
from pathlib import Path
import argparse,os,subprocess,sys,time,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,environment,load,pin,save
from RValues import compare_trees

def main():
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('--references',required=True,type=Path);parser.add_argument('--provider',required=True,type=Path);parser.add_argument('--out',required=True,type=Path)
 parser.add_argument('--stochastic-reference',type=Path);parser.add_argument('--dynamical-reference',type=Path)
 args=parser.parse_args();out=args.out.absolute();owned=False;calls=[];results=[]
 try:
  if sys.platform!='linux':raise OSError('Use the already provisioned original Linux/WSL runtime')
  if out!=out.resolve():raise ValueError('Canonical NEW test output required')
  for p in [HERE,args.references.resolve(),args.provider.resolve()]:
   # References are a parent development evidence collection; a new child is
   # allowed, but the actual immutable reference directories cannot overlap.
   if p==HERE and (out.is_relative_to(p) or p.is_relative_to(out)):raise ValueError('Do not write over adapter sources')
  out.mkdir(exist_ok=False);owned=True
  ready=load(args.provider/'READY.json');source_pins={p.name:pin(p) for p in HERE.iterdir() if p.is_file()};save(out/'SOURCE-PINS.json',source_pins)
  rlib=Path(ready['base_library']);versions={}
  for name in ['SingleCellExperiment','BiocSingular']:
   lines=(rlib/name/'DESCRIPTION').read_text().splitlines();versions[name]=next(line[9:] for line in lines if line.startswith('Version: '))
  def run(label,command,env=None):
   command=list(map(str,command));start=time.monotonic()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se)
    save(out/'LIVE.json',dict(label=label,pid=child.pid,command=command,started_unix=time.time()))
    while True:
     try:code=child.wait(timeout=20);break
     except subprocess.TimeoutExpired:print('SAME_ROUNDTRIP_CHILD_RUNNING',label,child.pid,round(time.monotonic()-start),flush=True)
   calls.append(dict(label=label,command=command,exit_code=code,elapsed_seconds=time.monotonic()-start));save(out/'PROCESSES.json',calls)
   if code:raise ValueError('Complete original-versus-adapter stage failed: '+label)
  script='''values <- readRDS(file.path(input_directory,"inputs.rds"))
parameters <- readRDS(file.path(input_directory,"parameters.rds"))
set.seed(882341L)
ns <- asNamespace("velociraptor")
spec <- get("velo.env",envir=ns,inherits=FALSE)
basilisk::basiliskRun(env=spec,fun=function(seed) {np <- reticulate::import("numpy");np$random$seed(as.integer(seed));NULL},seed=882341L)
analysis_result <- do.call(velociraptor::scvelo,c(list(x=values$INPUT),parameters))
stopifnot(identical(dim(analysis_result),c(12L,160L)),identical(rownames(analysis_result),rownames(values$list_input$X)),identical(colnames(analysis_result),colnames(values$list_input$X)))
saveRDS(analysis_result,file.path(result_directory,"complete-original-result.rds"),version=3)
saveRDS(parameters,file.path(result_directory,"complete-caller-parameters.rds"),version=3)
analysis_complete <- "COMPLETE_ORIGINAL_160_CELLS_12_GENES"
'''
  for mode,number,member in [('stochastic',11,'list_input'),('dynamical',10,'SCE_input')]:
   supplied=args.stochastic_reference if mode=='stochastic' else args.dynamical_reference
   original=supplied if supplied is not None else args.references/('velociraptor-original-workflow-'+str(number))/'original-results';script_path=out/(mode+'.R');script_path.write_text(script.replace('$INPUT','$'+member),encoding='utf-8',newline='\n');actual=out/(mode+'-adapter')
   request=dict(schema_version=1,version='1.20.0',mode='R_SCRIPT',script=str(script_path),provider_directory=str(args.provider),inputs={'inputs.rds':str(original/'COMPLETE-INPUTS.rds'),'parameters.rds':str(original/'COMPLETE-CALLER-PARAMETERS.rds')},expected_outputs={'complete-original-result.rds':1,'complete-caller-parameters.rds':1},required_exports=['scvelo'],required_packages=versions,
    expected_return='COMPLETE_ORIGINAL_160_CELLS_12_GENES',input_provenance='Exact original scvelo 0.3.3 normal-noise simulation and separately saved original RDS caller parameters; literal NA/Greek identifiers retained.',analysis_population='All 160 synthetic cells and 12 genes; explicit seed 882341; '+mode+' '+member+' dispatch; the original 15-iteration caller budget is not convergence or paper reproduction.',execution_policy='ALLOW',trusted_code_policy='ALLOW',out=str(actual),limits=dict(timeout_seconds=1200,maximum_files=20000,maximum_workspace_bytes=2147483648,maximum_file_bytes=536870912,maximum_R_nodes=100000,maximum_R_value_bytes=536870912))
   path=out/(mode+'-request.json');save(path,request);run(mode+'-adapter',[sys.executable,'-B',HERE/'run.py','--request',path])
   status=load(actual/'analysis-status.json')
   if not status['result_admitted'] or status['exit_code']!=0:raise ValueError('Adapter did not admit a complete result: '+mode)
   comparison=compare_trees(original/'complete-value-tree',actual/'results/result-value-tree');save(out/(mode+'-complete-value-tree-comparison.json'),comparison)
   reader=out/(mode+'-strict-reader');reader.mkdir()
   for name in ['owned-home','tmp','matplotlib-cache','numba-cache','unused-external-cache']:(reader/name).mkdir()
   run(mode+'-strict-R-values',[ready['rscript'],'--vanilla',HERE/'CompareOriginal.R',ready['library'],original/'COMPLETE-ORIGINAL-RESULT.rds',actual/'results/result-value-tree/original.rds',out/(mode+'-strict-comparison.json')],environment(ready,reader))
   run(mode+'-strict-R-parameters',[ready['rscript'],'--vanilla',HERE/'CompareOriginal.R',ready['library'],original/'COMPLETE-CALLER-PARAMETERS.rds',actual/'results/complete-caller-parameters.rds',out/(mode+'-strict-parameter-comparison.json')],environment(ready,reader))
   results.append(dict(mode=mode,cells=160,genes=12,interface=member,complete_value_tree=comparison,strict_complete_R_values=load(out/(mode+'-strict-comparison.json')),strict_caller_parameters=load(out/(mode+'-strict-parameter-comparison.json')),adapter_status=status))
   save(out/'COMPLETE-CASES.json',results)
  for name,wanted in source_pins.items():
   if pin(HERE/name)!=wanted:raise ValueError('Executed source changed: '+name)
  result=dict(status='PASS_TWO_COMPLETE_ORIGINAL_VERSUS_ADAPTER_R_REFERENCES',cases=results,calls=calls,source_pins=source_pins,other_modes_qualified=False,plots_qualified=False,whole_R_wrapper_qualified=False,new_family_credit=0,whole_goal_complete=False,warning_review_required=True,raw_console_diagnostic_counts_complete=False)
  save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
 except BaseException as error:
  result=dict(status='FAIL2_ORIGINAL_ROUNDTRIP_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),calls=calls,completed_cases=results,required_action=ACTION)
  if owned:save(out/'FAILURE.json',result)
  print(result['status'],repr(error),ACTION,flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
