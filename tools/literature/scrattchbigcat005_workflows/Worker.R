# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# One original operation per fresh process; no upstream function is replaced.
local({
  args <- commandArgs(TRUE);stopifnot(length(args)==1L)
  control <- jsonlite::fromJSON(args[[1L]],simplifyVector=FALSE)
  source(control$audit,local=TRUE);source(control$request_helpers,local=TRUE)
  source(control$original_call_helpers,local=TRUE);source(control$cross_validation_helpers,local=TRUE)
  source(control$constellation_helpers,local=TRUE)
  source(control$original_asset_helpers,local=TRUE)
  out <- control$output;phase <- "READINESS";failure <- NULL;conditions <- list()
  value <- NULL;started <- FALSE;returned <- FALSE;post <- NULL;request <- NULL
  rng.before <- rng.after <- NULL
  rng <- function()list(kind=RNGkind(),seed_exists=exists(".Random.seed",globalenv(),inherits=FALSE),
    seed=if(exists(".Random.seed",globalenv(),inherits=FALSE))get(".Random.seed",globalenv())else NULL)
  record <- function(c,kind)conditions[[length(conditions)+1L]] <<-
    list(kind=kind,phase=phase,condition=c,class=class(c),message=conditionMessage(c),call=conditionCall(c))
  tryCatch(withCallingHandlers({
    audit.bigcat(control,file.path(out,"before-original-source-audit.rds"))
    # This is the declared project caller search path, not a recovered author session.
    for(package in c("scrattch.bigcat","Matrix","matrixStats","data.table","arrow",
      "ggplot2","foreach","doMC","dplyr"))library(package,character.only=TRUE)
    phase <- "READ_REQUEST";request <- bigcat.request(control)
    phase <- "ORIGINAL_PREPARATION";prepared <- bigcat.prepare(request,control)
    saveRDS(list(constructors=prepared$constructors,objects=prepared$prepared,
      resolved_arguments=prepared$arguments,explicit_globals=request$globals,search_path=search()),
      file.path(out,"complete-original-preparation.rds"),version=3)
    do.call(RNGkind,as.list(request$rng$kind));assign(".Random.seed",request$rng$seed,envir=globalenv())
    arrow::set_cpu_count(request$arrow_cpu_count)
    rng.before <- rng();phase <- "ORIGINAL_ANALYSIS";started <- TRUE
    value <- if(request$operation=="centroid_cross_validation")bigcat.cross.validate(prepared$arguments,control)
      else if(request$operation=="constellation_from_embedding")bigcat.constellation(prepared$arguments,control)
      else do.call(getExportedValue("scrattch.bigcat",request$operation),prepared$arguments)
    returned <- TRUE;rng.after <- rng();phase <- "COMPLETE_ORIGINAL_RETURN_RECORDING"
    saveRDS(value,file.path(out,"complete-result.rds"),version=3)
    saveRDS(list(before=rng.before,after=rng.after),file.path(out,"complete-rng-before-after.rds"),version=3)
    phase <- "ORIGINAL_RETURN_ASSET_RECORDING"
    assets <- bigcat.archive.original.assets(value,control)
    phase <- "CALLER_POST_SELECTION"
    if(!is.null(request$post_selection)) {
      post <- bigcat.post.selection(value,prepared$arguments,request$post_selection)
      saveRDS(post,file.path(out,"complete-post-selection.rds"),version=3)
    }
    phase <- "POST_ANALYSIS_SOURCE_AUDIT"
    audit.bigcat(control,file.path(out,"after-original-source-audit.rds"))
    stopifnot(identical(rng.after,rng()))
    phase <- "COMPLETE"
  },warning=function(c)record(c,"warning"),message=function(c)record(c,"message"),
    error=function(c) {
      record(c,"error")
      tryCatch(saveRDS(list(error=c,calls=sys.calls()),file.path(out,"complete-original-failure-and-calls.rds"),version=3),
        error=function(recording)record(recording,"recording_error"))
    }),error=function(c){failure <<- c})
  if(started&&!is.null(failure))saveRDS(list(before=rng.before,after=rng()),
    file.path(out,"failed-analysis-rng-before-after.rds"),version=3)
  saveRDS(list(phase=phase,conditions=conditions,error=failure),file.path(out,"complete-conditions.rds"),version=3)
  writeLines(capture.output(sessionInfo()),file.path(out,"original-session-info.txt"))
  counts <- vapply(c("warning","message","error","recording_error"),function(kind)
    sum(vapply(conditions,function(entry)identical(entry$kind,kind),logical(1))),integer(1))
  groups <- list();lookup <- new.env(parent=emptyenv())
  for(entry in conditions) {
    key <- paste(entry$kind,paste(entry$class,collapse="|"),entry$message,sep="\r")
    if(exists(key,lookup,inherits=FALSE)) {
      index <- get(key,lookup,inherits=FALSE);groups[[index]]$count <- groups[[index]]$count+1L
    } else {
      index <- length(groups)+1L;assign(key,index,lookup)
      groups[[index]] <- list(kind=entry$kind,class=entry$class,message=entry$message,count=1L)
    }
  }
  jsonlite::write_json(list(groups=groups,R_handler_counts=as.list(counts),raw_console_diagnostic_counts_complete=FALSE),
    file.path(out,"condition-summary.json"),pretty=TRUE,auto_unbox=TRUE,null="null")
  code <- if(is.null(failure))0L else if(identical(phase,"READINESS")||inherits(failure,"bigcat_requirement_unavailable"))3L else 2L
  status <- list(status=if(code==0L)"PASS_ORIGINAL_BIGCAT_COMPLETE_RETURN_RECORDED"else
    if(code==3L)"SKIP3_ORIGINAL_BIGCAT_REQUIREMENT_UNAVAILABLE"else"FAIL2_ORIGINAL_BIGCAT_UNADMITTED",
    profile=control$profile,phase=phase,exit_code=code,analysis_started=started,original_function_returned=returned,
    complete_original_RDS_result=code==0L,original_return_is_null=returned&&is.null(value),
    no_clustering_result_claimed=returned&&is.null(value),post_selection_recorded=!is.null(post),
    R_handler_counts=as.list(counts),raw_console_diagnostic_counts_complete=FALSE,
    final_adapter_admission_established=FALSE,live_pointer_persistence_qualified=FALSE,
    whole_tool_qualified=FALSE,author_environment_inferred=FALSE)
  if(!is.null(failure))status$error <- list(class=class(failure),message=conditionMessage(failure),call=deparse(conditionCall(failure)))
  jsonlite::write_json(status,file.path(out,"R-status.json"),pretty=TRUE,auto_unbox=TRUE,null="null",na="string",digits=17)
  cat(status$status,"\n");if(code!=0L)quit(save="no",status=code,runLast=FALSE)
})
