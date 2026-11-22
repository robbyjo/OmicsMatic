# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Exact whole-object comparison must accept distinct captured environment
# identities while rejecting changes to their contents and every scientific field.
local({
  a<-commandArgs(TRUE);stopifnot(length(a)==3L);.libPaths(c(a[[1L]],.libPaths()))
  requireNamespace("SingleR");requireNamespace("BiocParallel")
  parent<-a[[2L]];out<-a[[3L]];stopifnot(!dir.exists(out));dir.create(out,recursive=TRUE)
  eq<-function(x,y)identical(serialize(x,NULL,version=3),serialize(y,NULL,version=3))
  clone<-function(x)unserialize(serialize(x,NULL,version=3))
  left<-readRDS(file.path(parent,"label-count-mismatch/direct/complete-conditions.rds"))
  right<-readRDS(file.path(parent,"label-count-mismatch/worker/original/complete-original-conditions.rds"))
  original<-readRDS(file.path(parent,"dense-default/direct/complete-return.rds"))
  rng<-readRDS(file.path(parent,"dense-default/direct/complete-rng.rds"))
  checks<-list(unchanged_whole_error_accepted=eq(left$error,right$error),
    unchanged_whole_condition_accepted=eq(left$conditions,lapply(right$conditions,function(x)x[c("kind","condition","class","message","call")])),
    different_process_environment_identity_observed=!identical(left$error,right$error))
  tampered<-list()
  x<-clone(left$error);parameter<-x$call$BPPARAM
  stopifnot(methods::is(parameter,"SerialParam"))
  x$call$BPPARAM<-BiocParallel::`bpRNGseed<-`(parameter,value=317L)
  checks$captured_environment_content_change_rejected<-!eq(left$error,x);tampered$error_environment<-x
  x<-clone(left$error);f<-x$call[[1L]];stopifnot(is.function(f));body(f)<-quote(stop("OWNED_UNADMITTED_SOURCE_BODY_TAMPER"));x$call[[1L]]<-f
  checks$captured_original_function_body_change_rejected<-!eq(left$error,x);tampered$error_function<-x
  x<-clone(original);x$scores[1L,1L]<-x$scores[1L,1L]+.01
  checks$single_score_change_rejected<-!eq(original,x);tampered$score<-x
  x<-clone(original);rownames(x)[1L]<-trimws(rownames(x)[1L])
  checks$literal_identifier_trim_rejected<-!eq(original,x);tampered$identifier<-x
  x<-clone(original);x$labels[1L]<-"OWNED_UNADMITTED_LABEL"
  checks$single_label_change_rejected<-!eq(original,x);tampered$label<-x
  x<-clone(rng);x$after$seed[10L]<-bitwXor(x$after$seed[10L],1L)
  checks$single_rng_integer_change_rejected<-!eq(rng,x);tampered$rng<-x
  saveRDS(list(checks=checks,complete_owned_tampered_objects=tampered),file.path(out,"complete-owned-object-guards.rds"),version=3)
  jsonlite::write_json(list(status=if(all(unlist(checks)))"PASS_WHOLE_CAPTURED_OBJECT_STATE_AND_SIX_TAMPER_REJECTIONS"else"FAIL2_WHOLE_OBJECT_COMPARISON_GUARDS",checks=checks,
    original_fields_or_environments_removed=FALSE,original_input_files_changed=FALSE,live_external_pointer_internals_compared=FALSE),
    file.path(out,if(all(unlist(checks)))"COMPLETED.json"else"FAILURE.json"),pretty=TRUE,auto_unbox=TRUE)
  stopifnot(all(unlist(checks)))
})
