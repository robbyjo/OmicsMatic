# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# One unchanged original scoring call, with complete returns/conditions/RNG.
local({
  paths <- commandArgs(TRUE);stopifnot(length(paths)==1L)
  control <- jsonlite::fromJSON(paths[[1L]],simplifyVector=FALSE)
  out <- control$output;phase <- "READINESS";started <- returned <- FALSE
  failure <- value <- request <- rng.before <- rng.after <- NULL;conditions <- list()
  rng <- function()list(kind=RNGkind(),seed_exists=exists(".Random.seed",globalenv(),inherits=FALSE),
    seed=if(exists(".Random.seed",globalenv(),inherits=FALSE))get(".Random.seed",globalenv())else NULL)
  record <- function(c,kind)conditions[[length(conditions)+1L]] <<- list(kind=kind,phase=phase,
    condition=c,class=class(c),message=conditionMessage(c),call=conditionCall(c))
  tryCatch(withCallingHandlers({
    .libPaths(c(control$library,control$base_library))
    source(control$audit,local=TRUE)
    hicat.audit(control,file.path(out,"before-original-source-audit.rds"),control$namespace_functions)
    # The Matrix namespace supplies the serialized input's original S4 methods.
    requireNamespace("Matrix",quietly=TRUE)
    phase <- "READ_REQUEST";request <- readRDS(control$request)
    required <- c("schema","operation","arguments","rng")
    optional <- c("caller_packages","options")
    stopifnot(is.list(request),!is.null(names(request)),!anyDuplicated(names(request)),
      all(required %in% names(request)),all(names(request) %in% c(required,optional)),
      identical(request$schema,1L),identical(request$operation,"doubletFinder"),is.list(request$arguments),
      is.list(request$rng),identical(sort(names(request$rng)),c("kind","seed")),
      is.character(request$rng$kind),length(request$rng$kind)==3L,!anyNA(request$rng$kind),
      is.integer(request$rng$seed),length(request$rng$seed)>0L)
    attached <- if(is.null(request$caller_packages))character()else request$caller_packages
    stopifnot(is.character(attached),!anyNA(attached),!anyDuplicated(attached),all(attached %in% c("Matrix","ggplot2")))
    for(package in attached)library(package,character.only=TRUE)
    options.request <- if(is.null(request$options))list()else request$options
    stopifnot(is.list(options.request),!anyDuplicated(names(options.request)),
      all(names(options.request) %in% c("warn","warnPartialMatchDollar")))
    if(length(options.request))options(options.request)
    saveRDS(list(request=request,search_path=search(),loaded_namespaces=loadedNamespaces(),
      selected_options=options()[c("warn","warnPartialMatchDollar")]),
      file.path(out,"complete-original-caller-context.rds"),version=3)
    do.call(RNGkind,as.list(request$rng$kind));assign(".Random.seed",request$rng$seed,envir=globalenv())
    rng.before <- rng();phase <- "ORIGINAL_ANALYSIS";started <- TRUE
    # A named call retains the original call expression rather than embedding
    # a copied upstream function body in the condition's call field.
    value <- do.call("doubletFinder",request$arguments,envir=asNamespace("scrattch.hicat"))
    returned <- TRUE;rng.after <- rng();phase <- "ORIGINAL_COMPLETE_RETURN_RECORDING"
    saveRDS(value,file.path(out,"complete-original-return.rds"),version=3)
    saveRDS(list(before=rng.before,after=rng.after),file.path(out,"complete-original-rng.rds"),version=3)
    phase <- "POST_ANALYSIS_SOURCE_AUDIT"
    hicat.audit(control,file.path(out,"after-original-source-audit.rds"),control$namespace_functions)
    stopifnot(identical(rng.after,rng()))
    phase <- "COMPLETE"
  },warning=function(c)record(c,"warning"),message=function(c)record(c,"message"),error=function(c) {
    record(c,"error")
    tryCatch(saveRDS(list(error=c,calls=sys.calls()),file.path(out,"complete-original-error-and-calls.rds"),version=3),
      error=function(recording)record(recording,"recording_error"))
  }),error=function(c){failure <<- c})
  if(started&&!is.null(failure))saveRDS(list(before=rng.before,after=rng()),file.path(out,"failed-original-rng.rds"),version=3)
  saveRDS(list(phase=phase,conditions=conditions,error=failure),file.path(out,"complete-original-conditions.rds"),version=3)
  writeLines(capture.output(sessionInfo()),file.path(out,"original-session-info.txt"))
  counts <- vapply(c("warning","message","error","recording_error"),function(kind)
    sum(vapply(conditions,function(x)identical(x$kind,kind),logical(1))),integer(1))
  code <- if(is.null(failure))0L else 2L
  status <- list(status=if(code==0L)"PASS_ORIGINAL_HICAT109_COMPLETE_RETURN_RECORDED"else"FAIL2_ORIGINAL_HICAT109_UNADMITTED",
    phase=phase,exit_code=code,analysis_started=started,original_function_returned=returned,
    original_return_is_null=returned&&is.null(value),complete_original_return_recorded=code==0L,
    R_handler_counts=as.list(counts),raw_native_diagnostic_counts_complete=FALSE,
    selected_source_profile=control$profile,author_environment_inferred=FALSE,
    source_derived_native_scoring_port=FALSE,final_adapter_admission_established=FALSE,whole_tool_qualified=FALSE)
  if(!is.null(failure))status$error <- list(class=class(failure),message=conditionMessage(failure))
  jsonlite::write_json(status,file.path(out,"R-status.json"),pretty=TRUE,auto_unbox=TRUE,null="null",na="string",digits=17)
  cat(status$status,"\n")
  if(code!=0L)quit(save="no",status=code,runLast=FALSE)
})
