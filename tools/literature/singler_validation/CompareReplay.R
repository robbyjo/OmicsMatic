# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Fresh independent original calls versus both complete frozen scientific channels.
local({
  a<-commandArgs(TRUE);stopifnot(length(a)==5L);.libPaths(c(a[[1L]],.libPaths()))
  requireNamespace("SingleR");requireNamespace("SingleCellExperiment")
  captured<-a[[2L]];fresh<-a[[3L]];out<-a[[4L]]
  cases<-jsonlite::fromJSON(a[[5L]]);results<-list();failed<-FALSE
  eq<-function(x,y)identical(serialize(x,NULL,version=3),serialize(y,NULL,version=3))
  for(name in cases) {
    d<-file.path(captured,"cases",name,"direct");w<-file.path(captured,"cases",name,"worker/original");f<-file.path(fresh,name,"direct")
    ds<-jsonlite::read_json(file.path(d,"STATUS.json"),simplifyVector=TRUE)
    ws<-jsonlite::read_json(file.path(w,"R-status.json"),simplifyVector=TRUE)
    fs<-jsonlite::read_json(file.path(f,"STATUS.json"),simplifyVector=TRUE)
    checks<-list(completion_direct=identical(ds$returned,fs$returned),completion_worker=identical(ws$original_function_returned,fs$returned),worker_analysis_started=isTRUE(ws$analysis_started))
    dcond<-readRDS(file.path(d,"complete-conditions.rds"));wcond<-readRDS(file.path(w,"complete-original-conditions.rds"));fcond<-readRDS(file.path(f,"complete-conditions.rds"))
    checks$whole_original_conditions_direct<-eq(dcond$conditions,fcond$conditions)
    checks$whole_original_conditions_worker<-eq(lapply(wcond$conditions,function(x)x[c("kind","condition","class","message","call")]),fcond$conditions)
    checks$whole_original_error_direct<-eq(dcond$error,fcond$error)
    checks$whole_original_error_worker<-eq(wcond$error,fcond$error)
    wrng<-if(isTRUE(fs$returned))"complete-original-rng.rds"else"failed-original-rng.rds"
    frng<-readRDS(file.path(f,"complete-rng.rds"))
    checks$whole_rng_direct<-eq(readRDS(file.path(d,"complete-rng.rds")),frng)
    checks$whole_rng_worker<-eq(readRDS(file.path(w,wrng)),frng)
    checks$whole_pre_call_rng_direct<-eq(readRDS(file.path(d,"complete-rng-before.rds")),frng$before)
    checks$whole_pre_call_rng_worker<-eq(readRDS(file.path(w,"complete-original-rng-before.rds")),frng$before)
    if(isTRUE(fs$returned)) {
      value<-readRDS(file.path(f,"complete-return.rds"))
      checks$whole_return_direct<-eq(readRDS(file.path(d,"complete-return.rds")),value)
      checks$whole_return_worker<-eq(readRDS(file.path(w,"complete-original-return.rds")),value)
    }
    training<-file.exists(file.path(f,"complete-training-return.rds"))
    checks$training_record_presence<-identical(training,file.exists(file.path(d,"complete-training-return.rds")))&&identical(training,file.exists(file.path(w,"complete-original-training-return.rds")))
    if(training&&checks$training_record_presence) {
      trained<-readRDS(file.path(f,"complete-training-return.rds"))
      checks$whole_serialized_training_direct<-eq(readRDS(file.path(d,"complete-training-return.rds")),trained)
      checks$whole_serialized_training_worker<-eq(readRDS(file.path(w,"complete-original-training-return.rds")),trained)
    }
    passed<-all(unlist(checks));failed<-failed||!passed
    results[[name]]<-list(passed=passed,checks=checks,original_returned=fs$returned)
  }
  jsonlite::write_json(list(status=if(failed)"FAIL2_FROZEN_SINGLER_SCIENCE_DIFFERS"else"PASS_BOTH_FROZEN_CHANNELS_VS_COMPLETE_FRESH_ORIGINALS",
    cases=results,original_fields_or_environments_removed=FALSE,live_external_pointer_internal_equivalence_claimed=FALSE),
    file.path(out,"FROZEN-SCIENCE-COMPARISONS.json"),pretty=TRUE,auto_unbox=TRUE,null="null")
  if(failed)quit(save="no",status=2L,runLast=FALSE)
})
