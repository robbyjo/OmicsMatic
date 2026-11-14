# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Execute the caller's exact trusted R program against the unchanged original namespace.
local({
 args <- commandArgs(TRUE);stopifnot(length(args)==1L)
 control <- jsonlite::fromJSON(args[[1L]],simplifyVector=FALSE)
 .libPaths(c(control$library,control$base_library))
 diagnostic <- control$diagnostics;phase <- 'READINESS';conditions <- list();failure <- NULL
 scope <- new.env(parent=globalenv());returned <- NULL;complete.value <- FALSE
 write.json <- function(value,name) jsonlite::write_json(value,file.path(diagnostic,name),pretty=TRUE,auto_unbox=TRUE,null='null',na='string',digits=17)
 record.condition <- function(condition,kind) {
  conditions[[length(conditions)+1L]] <<- list(ordinal=length(conditions)+1L,kind=kind,object=condition,class=class(condition),message=conditionMessage(condition),call=conditionCall(condition),calls=sys.calls())
 }
 original.error <- function(error) {
  # This calling handler runs before the original call stack unwinds.
  saveRDS(list(error=error,calls=sys.calls()),file.path(diagnostic,'complete-original-error-and-calls.rds'),version=3)
  if (isNamespaceLoaded('reticulate')) {
   py <- tryCatch(reticulate::py_last_error(),error=function(e)e)
   saveRDS(py,file.path(diagnostic,'complete-Python-last-error.rds'),version=3)
   writeLines(capture.output(print(py)),file.path(diagnostic,'complete-Python-last-error.txt'))
  }
 }
 tryCatch(withCallingHandlers({
  source(control$audit_script,local=TRUE)
  profile <- jsonlite::fromJSON(control$source_profile,simplifyVector=FALSE)
  auditVelociraptorRuntime(control$library,control$source_root,control$backend,file.path(diagnostic,'namespace'),unlist(profile$namespace_exports,use.names=FALSE))
  for (name in control$required_exports) {
   if (!is.function(getExportedValue('velociraptor',name))) stop('Required original export is not a function: ',name)
  }
  for (name in names(control$required_packages)) {
   if (!requireNamespace(name,quietly=TRUE) || as.character(packageVersion(name))!=control$required_packages[[name]])
    stop(structure(list(message=paste('Required original dependency unavailable:',name,control$required_packages[[name]]),call=NULL),class=c('velociraptor_missing_dependency','error','condition')))
  }
  scope$input_directory <- control$inputs;scope$result_directory <- control$results
  scope$input_provenance <- control$input_provenance;scope$analysis_population <- control$analysis_population
  phase <- 'ANALYSIS'
  write.json(list(status='EXACT_TRUSTED_ORIGINAL_R_SCRIPT_STARTED',analysis_population=control$analysis_population),'analysis-started.json')
  sys.source(control$script,envir=scope,keep.source=TRUE)
  returned <- if (exists('analysis_complete',envir=scope,inherits=FALSE)) get('analysis_complete',envir=scope,inherits=FALSE) else NULL
  if (!is.character(returned) || length(returned)!=1L || is.na(returned) || !identical(returned,control$expected_return)) stop('Exact complete-population analysis_complete marker is missing or differs')
  if (!exists('analysis_result',envir=scope,inherits=FALSE)) stop('Complete original analysis_result is missing')
  phase <- 'COMPLETE_VALUE_CAPTURE'
  source(control$value_capture_script,local=TRUE)
  captureRValues(get('analysis_result',envir=scope,inherits=FALSE),control$value_directory,max.nodes=control$maximum_R_nodes,max.bytes=control$maximum_R_value_bytes)
  complete.value <- TRUE
  # Check original plain-function bodies again after trusted code. This is an
  # integrity check, not a sandbox or a validator of arbitrary caller R code.
  ns <- asNamespace('velociraptor');checked <- character()
  for (file in list.files(file.path(control$source_root,'R'),pattern='\\.R$',full.names=TRUE)) {
   for (expr in parse(file,keep.source=FALSE)) {
    if (is.call(expr) && identical(expr[[1L]],as.name('<-')) && length(expr)==3L && is.symbol(expr[[2L]]) && is.call(expr[[3L]]) && identical(expr[[3L]][[1L]],as.name('function'))) {
     name <- as.character(expr[[2L]]);installed <- get(name,envir=ns,inherits=FALSE)
     stopifnot(identical(formals(installed),expr[[3L]][[2L]]),identical(body(installed),expr[[3L]][[3L]]));checked <- c(checked,name)
    }
   }
  }
  stopifnot(length(checked)==11L)
  phase <- 'COMPLETE'
 },warning=function(w)record.condition(w,'warning'),message=function(m)record.condition(m,'message'),error=original.error),
 error=function(e) {failure <<- e;record.condition(e,'error')})
 # Preserve available caller values even after original failure. Environment
 # serialization is retained as RDS; cross-process environment identity is not inferred.
 saveRDS(scope,file.path(diagnostic,'complete-available-caller-scope.rds'),version=3)
 saveRDS(conditions,file.path(diagnostic,'complete-conditions.rds'),version=3)
 saveRDS(list(phase=phase,error=failure,analysis_complete=returned,conditions=conditions),file.path(diagnostic,'complete-original-run.rds'),version=3)
 writeLines(capture.output(sessionInfo()),file.path(diagnostic,'original-R-session-info.txt'))
 if (file.exists('/proc/self/maps')) file.copy('/proc/self/maps',file.path(diagnostic,'original-R-native-maps.txt'))
 portable <- lapply(conditions,function(c)list(ordinal=c$ordinal,kind=c$kind,class=c$class,message=c$message,call=if(is.null(c$call))NULL else deparse(c$call),calls=lapply(c$calls,deparse)))
 write.json(portable,'conditions.json')
 # Preserve the R session tree on the same native Linux filesystem before R's
 # normal exit cleanup. The supervisor then copies the entire owned native root
 # into the durable output. Short native paths avoid AF_UNIX socket limits.
 # Literal paths within original files are never repaired or rewritten.
 temporary <- tempdir();retained <- file.path(control$native_temporary_directory,'retained-R-session-tempdir')
 temp.retained <- !file.exists(retained) && isTRUE(file.rename(temporary,retained))
 if (!temp.retained && is.null(failure)) failure <- simpleError('Could not retain the original R session temporary tree; inspect all partial evidence')
 code <- if(is.null(failure))0L else if(phase=='READINESS' && inherits(failure,'velociraptor_missing_dependency'))3L else 2L
 write.json(list(status=if(code==0L)'COMPLETE_ORIGINAL_VELOCIRAPTOR120_R_SCRIPT' else if(code==3L)'SKIP3_REQUIRED_ORIGINAL_DEPENDENCY_UNAVAILABLE' else 'FAIL2_ORIGINAL_VELOCIRAPTOR_RESULT_UNADMITTED',exit_code=code,phase=phase,
  warning_count=sum(vapply(conditions,function(c)c$kind=='warning',logical(1))),message_count=sum(vapply(conditions,function(c)c$kind=='message',logical(1))),error_count=sum(vapply(conditions,function(c)c$kind=='error',logical(1))),
  R_condition_counts_scope='Handlers around readiness, caller script and complete-value capture; raw/native/Python console totals are not included',raw_console_diagnostic_counts_complete=FALSE,
  returned_marker_matches=is.null(failure),complete_value_capture=complete.value,original_R_temporary_tree_retained=temp.retained,original_R_temp_path=temporary,retained_R_temp_path=retained,
  error=if(is.null(failure))NULL else list(class=class(failure),message=conditionMessage(failure),call=deparse(conditionCall(failure)))),'worker-status.json')
 if (!is.null(failure)) cat('ORIGINAL_WORKER_UNADMITTED: ',conditionMessage(failure),'\n',file=stderr(),sep='')
 quit(save='no',status=code,runLast=FALSE)
})
