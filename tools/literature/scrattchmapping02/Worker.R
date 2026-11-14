# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Prepared-template public workflow; no algorithm edits or lossy output export.
local({
  args <- commandArgs(TRUE);stopifnot(length(args)==1L)
  control <- jsonlite::fromJSON(args[[1L]],simplifyVector=FALSE)
  .libPaths(c(control$library,control$base_library))
  source(control$audit,local=TRUE)
  out <- control$output;phase <- "READINESS";failure <- NULL;conditions <- list()
  value <- NULL;analysis.started <- FALSE;rng.before <- NULL
  record <- function(c,kind) conditions[[length(conditions)+1L]] <<-
    list(kind=kind,phase=phase,condition=c,class=class(c),message=conditionMessage(c),call=conditionCall(c))
  asset <- function(path) {
    stopifnot(is.character(path),length(path)==1L,!is.na(path),file.exists(path),!dir.exists(path))
    resolved <- normalizePath(path,mustWork=TRUE)
    stopifnot(startsWith(resolved,paste0(normalizePath(control$assets,mustWork=TRUE),"/")))
  }
  tryCatch(withCallingHandlers({
    audit.mapping02(control,"before")
    phase <- "READ_REQUEST"
    request <- readRDS(control$request)
    stopifnot(is.list(request),identical(request$operation,"run_mapping_on_taxonomy"),
              is.list(request$arguments),!is.null(names(request$arguments)),
              !anyDuplicated(names(request$arguments)),all(nzchar(names(request$arguments))),
              is.list(request$rng),is.character(request$rng$kind),length(request$rng$kind)==3L,
              is.integer(request$rng$seed),length(request$rng$seed)>0L)
    a <- request$arguments
    stopifnot(all(c("query.dat","Taxonomy","TaxFN","mapping.method","iter","mc.cores","blocksize","dist.method","flag.serial") %in% names(a)),
              is.character(a$Taxonomy),length(a$Taxonomy)==1L,is.na(a$Taxonomy),
              identical(a$mapping.method,"hierarchy"),identical(a$dist.method,"cor"),
              identical(a$flag.serial,TRUE),is.null(a$flag.fuzzy)||identical(a$flag.fuzzy,FALSE),
              is.matrix(a$query.dat)||inherits(a$query.dat,"dgCMatrix"))
    asset(a$TaxFN)
    # Validate the prepared asset boundary without changing the serialized
    # templates or their absolute Annoy index paths. Original errors during
    # execution remain errors and are never repaired here.
    templates <- new.env(parent=emptyenv());load(a$TaxFN,envir=templates)
    stopifnot(exists("train.list",templates,inherits=FALSE))
    train <- templates$train.list;asset(train$MI_FN)
    indexes <- new.env(parent=emptyenv());load(train$MI_FN,envir=indexes)
    stopifnot(exists("marker_index",indexes,inherits=FALSE))
    for(node in indexes$marker_index) {
      if(is.list(node$index_tree))for(iteration in node$index_tree) {
        stopifnot(inherits(iteration$index,"AnnoyIndex"));asset(slot(iteration$index,"path"))
      }
    }
    phase <- "ORIGINAL_ANALYSIS"
    do.call(RNGkind,as.list(request$rng$kind));assign(".Random.seed",request$rng$seed,envir=globalenv())
    rng.before <- list(kind=RNGkind(),seed=get(".Random.seed",envir=globalenv()))
    analysis.started <- TRUE
    value <- do.call(getExportedValue("scrattch.mapping",request$operation),request$arguments)
    phase <- "COMPLETE_ORIGINAL_RESULT_RECORDING"
    saveRDS(value,file.path(out,"complete-result.rds"),version=3)
    saveRDS(list(before=rng.before,after=list(kind=RNGkind(),seed=get(".Random.seed",envir=globalenv()))),
            file.path(out,"complete-rng-before-after.rds"),version=3)
    phase <- "POST_ANALYSIS_SOURCE_AUDIT"
    audit.mapping02(control,"after")
    phase <- "COMPLETE"
  },warning=function(c)record(c,"warning"),message=function(c)record(c,"message"),
    error=function(c)saveRDS(list(error=c,calls=sys.calls()),file.path(out,"complete-original-failure-and-calls.rds"),version=3)),
    error=function(c){failure <<- c;record(c,"error")})
  if(analysis.started && !is.null(failure)) {
    saveRDS(list(before=rng.before,after=list(kind=RNGkind(),seed=get(".Random.seed",envir=globalenv()))),
            file.path(out,"failed-analysis-rng-before-after.rds"),version=3)
  }
  saveRDS(list(phase=phase,conditions=conditions,error=failure),file.path(out,"complete-conditions.rds"),version=3)
  writeLines(capture.output(sessionInfo()),file.path(out,"original-session-info.txt"))
  counts <- vapply(c("warning","message","error"),function(kind)
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
    file.path(out,"condition-summary.json"),pretty=TRUE,auto_unbox=TRUE,null="null",na="string",digits=17)
  code <- if(is.null(failure))0L else if(identical(phase,"READINESS"))3L else 2L
  status <- list(status=if(code==0L)"PASS_ORIGINAL_MAPPING02_COMPLETE_R_OBJECT"else if(code==3L)"SKIP3_ORIGINAL_REQUIREMENT_UNAVAILABLE"else"FAIL2_ORIGINAL_MAPPING02_UNADMITTED",
    phase=phase,exit_code=code,analysis_started=analysis.started,complete_original_RDS_result=code==0L,
    R_handler_counts=as.list(counts),raw_console_diagnostic_counts_complete=FALSE,
    portable_value_tree_claimed=FALSE,whole_tool_qualified=FALSE,author_environment_inferred=FALSE)
  if(!is.null(failure))status$error <- list(class=class(failure),message=conditionMessage(failure),call=deparse(conditionCall(failure)))
  jsonlite::write_json(status,file.path(out,"R-status.json"),pretty=TRUE,auto_unbox=TRUE,null="null",na="string",digits=17)
  cat(status$status,"\n");if(code!=0L)quit(save="no",status=code,runLast=FALSE)
})
