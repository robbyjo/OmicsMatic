# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Supervise the unchanged original namespace and caller's exact trusted script.
local({
 args <- commandArgs(TRUE)
 stopifnot(length(args)==1L)
 control <- jsonlite::fromJSON(args[1],simplifyVector=FALSE)
 .libPaths(c(control$library,control$base_library))
 diagnostic <- control$diagnostics
 write_json <- function(value,name) jsonlite::write_json(value,file.path(diagnostic,name),pretty=TRUE,auto_unbox=TRUE,digits=17,na='string',null='null')
 phase <- 'READINESS'
 conditions <- list();failure <- NULL;scope <- new.env(parent=globalenv())
 returned <- NULL;checked <- character()
 append_condition <- function(c,kind) {
  conditions[[length(conditions)+1L]] <<- list(ordinal=length(conditions)+1L,kind=kind,
   object=c,class=class(c),message=conditionMessage(c),call=conditionCall(c),calls=sys.calls())
 }
 tryCatch(withCallingHandlers({
  stopifnot(as.character(getRversion())=='4.5.3')
  suppressPackageStartupMessages(library(rstatix,lib.loc=control$library,character.only=FALSE))
  stopifnot(as.character(packageVersion('rstatix',lib.loc=control$library))=='0.7.2')
  ns <- asNamespace('rstatix')
  stopifnot(identical(sort(getNamespaceExports('rstatix')),sort(unlist(control$all_exports,use.names=FALSE))))
  for(file in list.files(file.path(control$source_root,'R'),pattern='\\.R$',full.names=TRUE)) {
   for(expr in parse(file,keep.source=FALSE)) {
    if(is.call(expr) && identical(expr[[1]],as.name('<-')) && length(expr)==3L && is.symbol(expr[[2]]) && is.call(expr[[3]]) && identical(expr[[3]][[1]],as.name('function'))) {
     name <- as.character(expr[[2]])
     original <- eval(expr[[3]],envir=ns)
     installed <- get(name,envir=ns,inherits=FALSE)
     stopifnot(identical(formals(original),formals(installed)),identical(body(original),body(installed)))
     checked <- c(checked,name)
    }
   }
  }
  stopifnot(length(unique(checked))==339L)
  for(name in control$required_exports) {
   if(!is.function(getExportedValue('rstatix',name))) stop('Required original export is not a function: ',name)
  }
  for(name in names(control$required_packages)) {
   if(!requireNamespace(name,quietly=TRUE) || as.character(packageVersion(name))!=control$required_packages[[name]])
    stop(structure(list(message=paste('Required original dependency unavailable:',name,control$required_packages[[name]]),call=NULL),class=c('rstatix_missing_dependency','error','condition')))
  }
  write_json(list(status='READY_ORIGINAL_RSTATIX072_NAMESPACE_NOT_ANALYSIS',source_functions=sort(unique(checked)),exports=sort(getNamespaceExports('rstatix'))),'namespace-readiness.json')
  scope$input_directory <- control$inputs
  scope$result_directory <- control$results
  scope$input_provenance <- control$input_provenance
  scope$analysis_population <- control$analysis_population
  phase <- 'ANALYSIS'
  write_json(list(status='ORIGINAL_CALLER_SCRIPT_STARTED',input_provenance=control$input_provenance,analysis_population=control$analysis_population),'analysis-started.json')
  sys.source(control$script,envir=scope,keep.source=TRUE)
  returned <- if(exists('analysis_complete',envir=scope,inherits=FALSE))get('analysis_complete',envir=scope,inherits=FALSE) else NULL
  if(!is.character(returned) || length(returned)!=1L || is.na(returned) || !identical(returned,control$expected_return))
   stop('Trusted script did not supply the exact complete-population analysis_complete marker')
 },warning=function(w) {append_condition(w,'warning')},message=function(m) {append_condition(m,'message')}),
 error=function(e) {failure <<- e;append_condition(e,'error')})
 # Warning handlers record without muffling: original warn=2 and message/error
 # semantics remain in effect. Full condition objects/call stacks remain RDS.
 saveRDS(conditions,file.path(diagnostic,'complete-conditions.rds'),version=3)
 saveRDS(list(phase=phase,analysis_complete=returned,error=failure,conditions=conditions),file.path(diagnostic,'complete-original-run.rds'),version=3)
 if(!is.null(failure)) saveRDS(failure,file.path(diagnostic,'original-error.rds'),version=3)
 portable <- lapply(conditions,function(c)list(ordinal=c$ordinal,kind=c$kind,class=c$class,message=c$message,
  call=if(is.null(c$call))NULL else deparse(c$call),calls=lapply(c$calls,deparse)))
 write_json(portable,'conditions.json')
 writeLines(capture.output(sessionInfo()),file.path(diagnostic,'session-info.txt'))
 code <- if(is.null(failure))0L else if(phase=='READINESS' && inherits(failure,'rstatix_missing_dependency'))3L else 2L
 status <- if(code==0L)'ORIGINAL_RSTATIX072_SCRIPT_COMPLETE' else if(code==3L)'SKIP3_REQUIRED_ORIGINAL_DEPENDENCY_UNAVAILABLE' else 'FAIL2_ORIGINAL_RSTATIX_SCRIPT_UNADMITTED'
 write_json(list(status=status,exit_code=code,phase=phase,source_functions=length(unique(checked)),
  warning_count=sum(vapply(conditions,function(c)c$kind=='warning',logical(1))),
  message_count=sum(vapply(conditions,function(c)c$kind=='message',logical(1))),
  error_count=sum(vapply(conditions,function(c)c$kind=='error',logical(1))),
  returned_marker_matches=is.null(failure),original_version='0.7.2',R_version=as.character(getRversion()),
  error=if(is.null(failure))NULL else list(class=class(failure),message=conditionMessage(failure),call=deparse(conditionCall(failure)))),'worker-status.json')
 if(!is.null(failure))cat(status,': ',conditionMessage(failure),'\n',file=stderr(),sep='')
 quit(save='no',status=code,runLast=FALSE)
})
