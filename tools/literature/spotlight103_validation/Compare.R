# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Compare complete original returns, conditions/calls and RNG without numeric tolerances.
local({
 a<-commandArgs(TRUE);stopifnot(length(a)==5L);.libPaths(c(a[[1L]],.libPaths()))
 d<-a[[2L]];w<-a[[3L]];out<-a[[4L]];name<-a[[5L]]
 direct<-readRDS(file.path(d,'status.rds'));worker<-jsonlite::read_json(file.path(w,'R-status.json'),simplifyVector=TRUE)
 conditions<-readRDS(file.path(w,'complete-original-conditions.rds'))
 events<-Filter(function(x)x$phase=='ORIGINAL_ANALYSIS',conditions$conditions)
 # Rcpp records ASLR addresses and wrapper-dependent C stack frames. Preserve all
 # original traces; compare the complete other fields and original native origin.
 condition.contract<-function(x){
  if(is.null(x)||!inherits(x,'Rcpp::exception'))return(x)
  trace<-x$cppstack
  stopifnot(identical(names(trace),c('file','line','stack')),inherits(trace,'Rcpp_stack_trace'),is.character(trace$stack))
  origin<-trace$stack[grepl('/SPOTlight/libs/SPOTlight[.]so',trace$stack,fixed=FALSE)]
  stopifnot(length(origin)>0L)
  origin<-sub(' \\[0x[[:xdigit:]]+\\]$',' [PROCESS_ADDRESS]',origin)
  x$cppstack<-list(file=trace$file,line=trace$line,original_SPOTlight_native_frames=origin,
   full_process_specific_backtrace_retained_in_original_records=TRUE)
  x
 }
 event.contract<-function(x){x$condition<-condition.contract(x$condition);x}
 checks<-list(returned=identical(direct$returned,worker$original_function_returned),
  error_semantic_payload_and_native_origin=identical(condition.contract(direct$error),condition.contract(conditions$error)),
  all_condition_fields_except_process_specific_cpp_backtraces=identical(lapply(direct$events,event.contract),lapply(events,event.contract)),
  RNG_before=identical(readRDS(file.path(d,'rng-before.rds')),readRDS(file.path(w,'complete-original-rng-before.rds'))))
 if(direct$returned){
  checks$complete_return<-identical(readRDS(file.path(d,'return.rds')),readRDS(file.path(w,'complete-original-return.rds')))
  checks$complete_RNG<-identical(readRDS(file.path(d,'rng.rds')),readRDS(file.path(w,'complete-original-rng.rds')))
  if(name=='train.deconvolve')checks$complete_training_return<-identical(readRDS(file.path(d,'training-return.rds')),readRDS(file.path(w,'complete-original-training-return.rds')))
 }else checks$complete_failed_RNG<-identical(readRDS(file.path(d,'failed-rng.rds')),readRDS(file.path(w,'failed-original-rng.rds')))
 result<-list(status=if(all(unlist(checks)))'PASS_COMPLETE_ORIGINAL_SPOTLIGHT103_COMPARISON'else'FAIL2_ORIGINAL_SPOTLIGHT103_DIFFERENCE',case=name,returned=direct$returned,checks=checks,
  original_warnings=unname(vapply(Filter(function(x)x$kind=='warning',direct$events),function(x)x$message,character(1))),
  original_error=if(is.null(direct$error))NULL else conditionMessage(direct$error),
  complete_raw_condition_payloads_identical=identical(direct$events,events),
  full_process_specific_cpp_backtraces_retained=TRUE,original_condition_objects_modified=FALSE,
  process_specific_cpp_backtrace_bytes_compared_for_equality=FALSE,whole_tool_qualified=FALSE,biological_accuracy_established=FALSE)
 jsonlite::write_json(result,out,pretty=TRUE,auto_unbox=TRUE,null='null',digits=17)
 if(!all(unlist(checks)))quit(save='no',status=2L,runLast=FALSE)
})
