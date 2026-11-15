# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Deliberate corruption of an owned R process's captured method delegate.
# No upstream source file, installed runtime file or original analysis is changed.
local({
  a<-commandArgs(TRUE);stopifnot(length(a)==3L)
  control<-jsonlite::fromJSON(a[[1L]],simplifyVector=FALSE);out<-a[[3L]]
  stopifnot(!dir.exists(out));dir.create(out,recursive=TRUE)
  .libPaths(c(control$library,control$base_library));source(a[[2L]],local=TRUE)
  before<-singler.audit(control,file.path(out,"complete-original-before-audit.rds"))
  ns<-asNamespace("BiocNeighbors");method<-methods::getMethod("buildIndex","AnnoyParam",where=ns)
  frame<-environment(method);stopifnot(exists("FUN",frame,inherits=FALSE))
  original<-get("FUN",frame,inherits=FALSE);code.before<-deparse(body(method));formals.before<-formals(method)
  assign("FUN",get("buildKmknn",ns,inherits=FALSE),envir=frame)
  error<-tryCatch({singler.audit(control,file.path(out,"SHOULD-NOT-ADMIT-AUDIT.rds"));NULL},error=function(e)e)
  checks<-list(complete_original_before_accepted=identical(before$status,"PASS_COMPLETE_ORIGINAL_SINGLER_PROFILE_NAMESPACE_ASTS"),
    method_body_text_unchanged=identical(code.before,deparse(body(method))),method_formals_unchanged=identical(formals.before,formals(method)),
    substituted_captured_neighbour_algorithm_rejected=!is.null(error)&&grepl("buildIndex AnnoyParam",conditionMessage(error),fixed=TRUE),
    substituted_audit_not_admitted=!file.exists(file.path(out,"SHOULD-NOT-ADMIT-AUDIT.rds")))
  saveRDS(list(error=error,checks=checks,original_delegate=original,substituted_delegate=get("FUN",frame,inherits=FALSE)),
    file.path(out,"complete-owned-delegate-tamper-record.rds"),version=3)
  assign("FUN",original,envir=frame)
  restored<-singler.audit(control,file.path(out,"complete-restored-original-audit.rds"))
  checks$restored_original_accepted<-identical(restored$status,"PASS_COMPLETE_ORIGINAL_SINGLER_PROFILE_NAMESPACE_ASTS")
  jsonlite::write_json(list(status=if(all(unlist(checks)))"PASS_ORIGINAL_S4_CAPTURED_DELEGATE_TAMPER_GUARD"else"FAIL2_S4_CAPTURED_DELEGATE_GUARD",checks=checks,
    original_source_or_runtime_files_changed=FALSE,original_annotation_executed=FALSE),
    file.path(out,if(all(unlist(checks)))"COMPLETED.json"else"FAILURE.json"),pretty=TRUE,auto_unbox=TRUE)
  stopifnot(all(unlist(checks)))
})
