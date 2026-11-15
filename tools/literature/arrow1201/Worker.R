# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Execute unchanged original Arrow public I/O and lazy dplyr Dataset methods.
local({
  args <- commandArgs(TRUE);stopifnot(length(args)==1L)
  control <- jsonlite::fromJSON(args[[1L]],simplifyVector=FALSE)
  source(control$audit,local=TRUE)
  out <- control$output;phase <- "READINESS";failure <- NULL;conditions <- list()
  value <- NULL;started <- FALSE;rng.before <- NULL
  rng <- function() list(kind=RNGkind(),seed_exists=exists(".Random.seed",globalenv(),inherits=FALSE),
    seed=if(exists(".Random.seed",globalenv(),inherits=FALSE))get(".Random.seed",globalenv())else NULL)
  record <- function(c,kind) conditions[[length(conditions)+1L]] <<-
    list(kind=kind,phase=phase,condition=c,class=class(c),message=conditionMessage(c),call=conditionCall(c))
  named <- function(x) is.list(x) && (length(x)==0L ||
    (!is.null(names(x)) && !anyNA(names(x)) && all(nzchar(names(x))) && !anyDuplicated(names(x))))
  asset <- function(path) {
    stopifnot(is.character(path),length(path)>0L,!anyNA(path),all(startsWith(path,"/")))
    root <- normalizePath(control$assets,mustWork=TRUE)
    resolved <- normalizePath(path,mustWork=TRUE)
    stopifnot(all(resolved==root|startsWith(resolved,paste0(root,"/"))))
  }
  tryCatch(withCallingHandlers({
    audit.arrow1201(control$source,file.path(out,"before-original-source-audit.rds"))
    stopifnot(identical(as.character(utils::packageVersion("dplyr")),"1.1.4"))
    phase <- "READ_REQUEST";request <- readRDS(control$request)
    stopifnot(named(request),identical(request$schema_version,1L),
      is.character(request$operation),length(request$operation)==1L,!is.na(request$operation),
      request$operation %in% c("read-chunks","dataset-query"),
      is.integer(request$cpu_count),length(request$cpu_count)==1L,
      !is.na(request$cpu_count),request$cpu_count>=1L)
    if(identical(request$operation,"read-chunks")) {
      stopifnot(setequal(names(request),c("schema_version","operation","cpu_count","chunks")))
      stopifnot(is.list(request$chunks),length(request$chunks)>0L)
      for(chunk in request$chunks) {
        stopifnot(named(chunk),is.character(chunk$file),length(chunk$file)==1L,
          identical(chunk$as_data_frame,TRUE))
        asset(chunk$file)
      }
    } else {
      stopifnot(setequal(names(request),c("schema_version","operation","cpu_count","arguments","steps","collect_arguments")))
      stopifnot(named(request$arguments),identical(request$arguments$format,"parquet"),
        is.list(request$steps),named(request$collect_arguments),
        identical(request$collect_arguments$as_data_frame,TRUE))
      asset(request$arguments$sources)
      for(step in request$steps)stopifnot(named(step),setequal(names(step),c("verb","arguments")),
        is.character(step$verb),length(step$verb)==1L,!is.na(step$verb),step$verb %in% c("filter","select","arrange"),
        is.list(step$arguments),!any(names(step$arguments) %in% c(".data","x")))
    }
    phase <- "ORIGINAL_ANALYSIS";started <- TRUE
    arrow::set_cpu_count(request$cpu_count)
    rng.before <- rng()
    if(identical(request$operation,"read-chunks")) {
      value <- lapply(request$chunks,function(a)do.call(arrow::read_parquet,a))
      stopifnot(all(vapply(value,is.data.frame,logical(1))))
    } else {
      query <- do.call(arrow::open_dataset,request$arguments)
      for(step in request$steps) {
        query <- do.call(getExportedValue("dplyr",step$verb),c(list(.data=query),step$arguments))
        stopifnot(inherits(query,"Dataset")||inherits(query,"arrow_dplyr_query"))
      }
      value <- do.call(dplyr::collect,c(list(x=query),request$collect_arguments))
      stopifnot(is.data.frame(value))
    }
    phase <- "COMPLETE_RESULT_RECORDING"
    saveRDS(value,file.path(out,"complete-result.rds"),version=3)
    saveRDS(list(before=rng.before,after=rng()),file.path(out,"complete-rng-before-after.rds"),version=3)
    phase <- "POST_ANALYSIS_SOURCE_AUDIT"
    audit.arrow1201(control$source,file.path(out,"after-original-source-audit.rds"))
    phase <- "COMPLETE"
  },warning=function(c)record(c,"warning"),message=function(c)record(c,"message"),
    error=function(c)saveRDS(list(error=c,calls=sys.calls()),file.path(out,"complete-original-failure-and-calls.rds"),version=3)),
    error=function(c){failure <<- c;record(c,"error")})
  if(started && !is.null(failure))saveRDS(list(before=rng.before,after=rng()),
    file.path(out,"failed-analysis-rng-before-after.rds"),version=3)
  saveRDS(list(phase=phase,conditions=conditions,error=failure),file.path(out,"complete-conditions.rds"),version=3)
  writeLines(capture.output(sessionInfo()),file.path(out,"original-session-info.txt"))
  counts <- vapply(c("warning","message","error"),function(kind)
    sum(vapply(conditions,function(entry)identical(entry$kind,kind),logical(1))),integer(1))
  code <- if(is.null(failure))0L else if(identical(phase,"READINESS"))3L else 2L
  status <- list(status=if(code==0L)"PASS_ORIGINAL_ARROW1201_COMPLETE_COLLECTED_R_OBJECT"else
    if(code==3L)"SKIP3_ORIGINAL_ARROW1201_REQUIREMENT_UNAVAILABLE"else"FAIL2_ORIGINAL_ARROW1201_UNADMITTED",
    phase=phase,exit_code=code,analysis_started=started,complete_original_RDS_result=code==0L,
    R_handler_counts=as.list(counts),raw_console_diagnostic_counts_complete=FALSE,
    live_arrow_pointer_persisted=FALSE,whole_tool_qualified=FALSE,author_environment_inferred=FALSE)
  if(!is.null(failure))status$error <- list(class=class(failure),message=conditionMessage(failure),call=deparse(conditionCall(failure)))
  jsonlite::write_json(status,file.path(out,"R-status.json"),pretty=TRUE,auto_unbox=TRUE,null="null",na="string",digits=17)
  cat(status$status,"\n");if(code!=0L)quit(save="no",status=code,runLast=FALSE)
})
