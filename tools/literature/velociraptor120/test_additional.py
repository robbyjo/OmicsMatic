#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare additional complete original modes, preprocessing and embedding calls."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor,as_completed
import argparse,subprocess,sys,tempfile,time,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,environment,load,pin,provider,save
from RValues import compare_trees
from run import capture_native

SCENARIOS=['default-mode-ANY','deterministic-SummarizedExperiment','provided-embedding-subset-SCE','explicit-size-factors-ANY','Python-preprocessing-dense-provided','Python-preprocessing-sparse-no-embedding','embedVelocity-ANY','embedVelocity-SCE','gridVectors-ANY-intermediates','gridVectors-SCE-default','embedVelocity-default-PCA2-ANY']
SCRIPT='''call <- readRDS(file.path(input_directory,"call.rds"))
set.seed(call$seed)
spec <- get("velo.env",envir=asNamespace("velociraptor"),inherits=FALSE)
basilisk::basiliskRun(env=spec,fun=function(seed) {reticulate::import("numpy")$random$seed(as.integer(seed));NULL},seed=call$seed)
analysis_result <- do.call(getExportedValue("velociraptor",call$export),call$arguments)
saveRDS(analysis_result,file.path(result_directory,"COMPLETE-ORIGINAL-RESULT.rds"),version=3)
saveRDS(call$arguments,file.path(result_directory,"COMPLETE-ARGUMENTS-AFTER-CALL.rds"),version=3)
if (call$scenario=="gridVectors-ANY-intermediates") {
 stopifnot(identical(names(analysis_result),c("start","end","limits","delta","categories","grp","vec")),length(analysis_result$grp)==160L,identical(dim(analysis_result$categories),c(160L,2L)),identical(dim(analysis_result$limits),c(2L,2L)),length(analysis_result$delta)==2L,nrow(analysis_result$start)==length(unique(analysis_result$grp)))
} else if (call$scenario=="gridVectors-SCE-default") {
 stopifnot(is.data.frame(analysis_result),ncol(analysis_result)==4L,nrow(analysis_result)>0L)
} else stopifnot(identical(dim(analysis_result),c(call$expected.rows,call$expected.columns)))
if (call$export=="scvelo") stopifnot(identical(rownames(analysis_result),call$expected.rownames),identical(colnames(analysis_result),call$expected.colnames))
analysis_complete <- paste0("COMPLETE_ADDITIONAL_",call$scenario)
'''

def main():
 parser=argparse.ArgumentParser(description=__doc__)
 for name in ['provider','inputs','fit','out']:parser.add_argument('--'+name,type=Path,required=True)
 parser.add_argument('--jobs',type=int,choices=[1,2],default=2);parser.add_argument('--cases',nargs='+',choices=SCENARIOS,default=SCENARIOS)
 parser.add_argument('--embedding',type=Path)
 args=parser.parse_args();out=args.out.absolute();owned=False;completed=[]
 try:
  if sys.platform!='linux':raise OSError('Use the declared original Linux/WSL runtime')
  if out!=out.resolve():raise ValueError('Canonical NEW additional-control output required')
  for path in [HERE,args.provider.resolve(),args.inputs.resolve(),args.fit.resolve()]:
   if out.is_relative_to(path) or path.is_relative_to(out):raise ValueError('Control output overlaps protected source/provider/input')
  if len(set(args.cases))!=len(args.cases):raise ValueError('Duplicate case selection')
  if any(name.startswith('gridVectors-') for name in args.cases) and args.embedding is None:raise ValueError('Declare the complete original --embedding RDS for grid controls')
  out.mkdir(exist_ok=False);owned=True;ready=provider(args.provider)
  names=['Runtime.py','run.py','OriginalWorker.R','AuditRuntime.R','RValues.R','RValues.py','CompareOriginal.R','source-profile.json','DirectAdditional.R','test_additional.py']
  sources={name:pin(HERE/name) for name in names};save(out/'SOURCE-PINS.json',sources);input_pins={str(path):pin(path) for path in [args.inputs,args.fit,*([args.embedding] if args.embedding is not None else [])]}
  def one_case(scenario):
   case=out/scenario;case.mkdir();original=case/'original';original.mkdir();calls=[]
   def execute(label,command,*,env=None):
    command=list(map(str,command));started=time.monotonic()
    with (case/(label+'.stdout')).open('xb') as so,(case/(label+'.stderr')).open('xb') as se:
     child=subprocess.Popen(command,cwd=case,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(case/'LIVE.json',dict(case=scenario,label=label,pid=child.pid,command=command,started_unix=time.time()))
     while True:
      try:code=child.wait(timeout=20);break
      except subprocess.TimeoutExpired:print('SAME_ADDITIONAL_CASE_CHILD_RUNNING',scenario,label,child.pid,round(time.monotonic()-started),flush=True)
    calls.append(dict(label=label,command=command,exit_code=code,elapsed_seconds=time.monotonic()-started));save(case/'PROCESSES.json',calls);return code
   try:
    for name in ['owned-home','tmp','matplotlib-cache','numba-cache','unused-external-cache']:(original/name).mkdir()
    native=Path(tempfile.mkdtemp(prefix='vxr-',dir='/tmp'));save(original/'native-temporary.json',dict(path=str(native),original_root_deleted=False))
    reference_code=execute('direct-original',[ready['rscript'],'--vanilla',HERE/'DirectAdditional.R',ready['library'],args.inputs,args.fit,scenario,original,HERE/'RValues.R',*([args.embedding] if args.embedding is not None else [])],env=environment(ready,original,temporary_directory=native))
    capture_native(native,original)
    reference=load(original/'REFERENCE-STATUS.json')
    if not reference['original.R.temporary.tree.retained']:raise ValueError('Direct original R temporary retention incomplete')
    if reference_code not in [0,2] or (reference_code==2 and reference['phase']!='ORIGINAL_ANALYSIS'):raise ValueError('Original control/readiness/capture failed outside the original analysis; preserve and investigate')
    script=case/'Analysis.R';script.write_text(SCRIPT,encoding='utf-8',newline='\n');actual=case/'adapter'
    export='embedVelocity' if scenario.startswith('embedVelocity-') else 'gridVectors' if scenario.startswith('gridVectors-') else 'scvelo'
    request=dict(schema_version=1,version='1.20.0',mode='R_SCRIPT',script=str(script),provider_directory=str(args.provider),inputs={'call.rds':str(original/'COMPLETE-ORIGINAL-CALL.rds')},expected_outputs={'COMPLETE-ORIGINAL-RESULT.rds':1,'COMPLETE-ARGUMENTS-AFTER-CALL.rds':1},required_exports=[export],required_packages={},expected_return='COMPLETE_ADDITIONAL_'+scenario,
     input_provenance='Unchanged original scvelo 0.3.3 normal-noise simulation and complete original caller argument RDS; literal NA/Greek identifiers retained.',analysis_population='Explicit synthetic control '+scenario+'; full original arguments record all cell/gene orders, any caller-selected subset, embedding and preprocessing choices. No author settings inferred.',execution_policy='ALLOW',trusted_code_policy='ALLOW',out=str(actual),limits=dict(timeout_seconds=1200,maximum_files=20000,maximum_workspace_bytes=2147483648,maximum_file_bytes=536870912,maximum_R_nodes=100000,maximum_R_value_bytes=536870912))
    request_path=case/'request.json';save(request_path,request);adapter_code=execute('adapter',[sys.executable,'-B',HERE/'run.py','--request',request_path]);status=load(actual/'analysis-status.json')
    if adapter_code!=reference_code or status['exit_code']!=reference_code:raise ValueError('Original and adapter exit/status differ')
    if reference_code==2:
     worker=load(actual/'diagnostics/worker-status.json')
     if status['result_admitted'] or worker['phase']!='ANALYSIS' or worker['error']['class']!=reference['error']['class'] or worker['error']['message']!=reference['error']['message']:raise ValueError('Original upstream error type/message or refusal semantics differ')
     result=dict(status='PASS_MATCHING_ORIGINAL_UPSTREAM_ERROR_NOT_SUPPORTED_ANALYSIS',scenario=scenario,reference=reference,adapter=status,error_class_and_message_equal=True,full_error_call_stacks_retained_separately=True,calls=calls)
    else:
     if not status['result_admitted']:raise ValueError('Complete original return was not admitted by adapter')
     comparison=compare_trees(original/'complete-value-tree',actual/'results/result-value-tree');save(case/'COMPLETE-VALUE-COMPARISON.json',comparison)
     reader=case/'strict-reader';reader.mkdir()
     for name in ['owned-home','tmp','matplotlib-cache','numba-cache','unused-external-cache']:(reader/name).mkdir()
     for label,left,right in [('return',original/'COMPLETE-ORIGINAL-RESULT.rds',actual/'results/result-value-tree/original.rds'),('arguments-after',original/'COMPLETE-ARGUMENTS-AFTER-CALL.rds',actual/'results/COMPLETE-ARGUMENTS-AFTER-CALL.rds')]:
      code=execute('strict-'+label,[ready['rscript'],'--vanilla',HERE/'CompareOriginal.R',ready['library'],left,right,case/('STRICT-'+label+'.json')],env=environment(ready,reader))
      if code:raise ValueError('Strict complete original R '+label+' differs')
     result=dict(status='PASS_COMPLETE_ADDITIONAL_ORIGINAL_VERSUS_ADAPTER_CASE',scenario=scenario,reference=reference,adapter=status,complete_value_tree=comparison,strict_complete_return=load(case/'STRICT-return.json'),strict_arguments_after=load(case/'STRICT-arguments-after.json'),calls=calls)
    save(case/'COMPLETED.json',result);print(result['status'],scenario,flush=True);return result
   except BaseException as error:
    result=dict(status='FAIL2_ADDITIONAL_CASE_UNADMITTED',scenario=scenario,cause=repr(error),traceback=traceback.format_exc(),calls=calls,required_action=ACTION);save(case/'FAILURE.json',result);print(result['status'],scenario,repr(error),flush=True);return result
  with ThreadPoolExecutor(max_workers=args.jobs) as pool:
   futures=[pool.submit(one_case,scenario) for scenario in args.cases]
   for future in as_completed(futures):
    completed.append(future.result());save(out/'COMPLETE-CASES.json',completed)
  for name,wanted in sources.items():
   if pin(HERE/name)!=wanted:raise ValueError('Executed source changed: '+name)
  for name,wanted in input_pins.items():
   if pin(Path(name))!=wanted:raise ValueError('Original physical input changed: '+name)
  print('VERIFYING_COMPLETE_RUNTIME_AFTER_ADDITIONAL_CASES',flush=True);provider(args.provider)
  failures=[row for row in completed if row['status'].startswith('FAIL2')]
  report=dict(status='FAIL2_ADDITIONAL_CONTROLS_INCOMPLETE' if failures else 'PASS_COMPLETE_ADDITIONAL_ORIGINAL_AND_ADAPTER_CASE_POPULATION',cases=completed,selected_cases=args.cases,source_pins=sources,input_pins=input_pins,complete_successful_cases=sum(row['status']=='PASS_COMPLETE_ADDITIONAL_ORIGINAL_VERSUS_ADAPTER_CASE' for row in completed),matching_original_error_cases=sum(row['status']=='PASS_MATCHING_ORIGINAL_UPSTREAM_ERROR_NOT_SUPPORTED_ANALYSIS' for row in completed),original_inputs_and_runtime_unchanged=True,plots_qualified=False,whole_R_wrapper_qualified=False,new_family_credit=0,whole_goal_complete=False)
  save(out/('FAILURE.json' if failures else 'COMPLETED.json'),report);print(report['status'],flush=True);return 2 if failures else 0
 except BaseException as error:
  if owned:save(out/'FAILURE.json',dict(status='FAIL2_ADDITIONAL_CONTROLS_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),completed_cases=completed,required_action=ACTION))
  print('FAIL2_ADDITIONAL_CONTROLS_UNADMITTED',repr(error),ACTION,flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
