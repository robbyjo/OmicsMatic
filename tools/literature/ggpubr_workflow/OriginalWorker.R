# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Use unchanged original namespaces. Never replace their statistics or geoms.
local({
 args <- commandArgs(TRUE);stopifnot(length(args)==1L)
 control <- jsonlite::fromJSON(args[1],simplifyVector=FALSE)
 .libPaths(c(control$library,control$base_library));diagnostics <- control$diagnostics
 write_json <- function(x,name)jsonlite::write_json(x,file.path(diagnostics,name),pretty=TRUE,auto_unbox=TRUE,digits=17,na='string',null='null')
 conditions <- list();failure <- NULL;phase <- 'READINESS';returned <- NULL
 append_condition <- function(c,kind) {
  plain <- gsub('\033\\[[0-9;]*m','',conditionMessage(c),perl=TRUE)
  failed_layer <- kind=='warning' && grepl('^Computation failed in(?:[[:space:]]|$)',plain,perl=TRUE)
  conditions[[length(conditions)+1L]] <<- list(ordinal=length(conditions)+1L,kind=kind,
   failed_layer_warning=failed_layer,object=c,class=class(c),message=conditionMessage(c),call=conditionCall(c),calls=sys.calls())
 }
 is_ggproto_call <- function(expr) {
  if(!is.call(expr))return(FALSE)
  head <- expr[[1]]
  identical(head,as.name('ggproto')) || (is.call(head)&&identical(head[[1]],as.name('::'))&&identical(head[[2]],as.name('ggplot2'))&&identical(head[[3]],as.name('ggproto')))
 }
 checks <- list();scope <- new.env(parent=globalenv())
 tryCatch(withCallingHandlers({
  stopifnot(as.character(getRversion())=='4.3.3',control$version %in% c('0.4.0','0.6.0'))
  suppressPackageStartupMessages(library(ggpubr,lib.loc=control$library))
  profile <- jsonlite::fromJSON(control$source_profile,simplifyVector=FALSE)
  selected <- list(ggplot2='3.4.3',rstatix='0.7.2',ggpubr=control$version)
  for(package in names(selected)) {
   stopifnot(as.character(packageVersion(package,lib.loc=control$library))==selected[[package]])
   ns <- asNamespace(package);spec <- profile$profiles[[paste(package,selected[[package]],sep='-')]]
   stopifnot(identical(sort(getNamespaceExports(package)),sort(unlist(spec$namespace_exports,use.names=FALSE))))
   functions <- character();prototypes <- list()
   for(file in list.files(file.path(control$source_root,package,'R'),pattern='\\.[Rr]$',full.names=TRUE)) {
    for(expr in parse(file,keep.source=FALSE)) {
     if(!is.call(expr)||!identical(expr[[1]],as.name('<-'))||length(expr)!=3L||!is.symbol(expr[[2]]))next
     name <- as.character(expr[[2]]);rhs <- expr[[3]]
     if(is.call(rhs)&&identical(rhs[[1]],as.name('function'))) {
      wanted <- eval(rhs,envir=ns);actual <- get(name,envir=ns,inherits=FALSE)
      if(!identical(formals(wanted),formals(actual))||!identical(body(wanted),body(actual)))stop('Original source function differs: ',package,'::',name)
      functions <- c(functions,name)
     } else if(is_ggproto_call(rhs)) {
      wanted <- eval(rhs,envir=ns);actual <- get(name,envir=ns,inherits=FALSE)
      stopifnot(inherits(wanted,'ggproto'),inherits(actual,'ggproto'))
      fields <- sort(ls(wanted,all.names=TRUE));stopifnot(identical(fields,sort(ls(actual,all.names=TRUE))))
      for(field in fields) {
       a <- get(field,envir=wanted,inherits=FALSE);b <- get(field,envir=actual,inherits=FALSE)
       if(is.function(a)) {
        if(!is.function(b)||!identical(formals(a),formals(b))||!identical(body(a),body(b)))stop('Original prototype method differs: ',package,'::',name,'$',field)
       } else if(!isTRUE(all.equal(a,b,check.environment=FALSE,tolerance=0)))stop('Original prototype field differs: ',package,'::',name,'$',field)
      }
      prototypes[[name]] <- fields
     }
    }
   }
   expected <- control$expected_counts[[package]]
   stopifnot(length(unique(functions))==expected$functions,length(prototypes)==expected$prototypes)
   checks[[package]] <- list(version=selected[[package]],source_functions=sort(unique(functions)),source_prototypes=prototypes,exports=sort(getNamespaceExports(package)))
  }
  for(name in control$required_exports)stopifnot(is.function(getExportedValue('ggpubr',name)))
  for(name in names(control$required_packages)) {
   if(!requireNamespace(name,quietly=TRUE)||as.character(packageVersion(name))!=control$required_packages[[name]])
    stop(structure(list(message=paste('Required original dependency unavailable:',name,control$required_packages[[name]]),call=NULL),class=c('ggpubr_missing_dependency','error','condition')))
  }
  write_json(list(status='READY_UNCHANGED_ORIGINAL_GGPUBR_NAMESPACES_NOT_ANALYSIS',checks=checks),'namespace-readiness.json')
  scope$input_directory <- control$inputs;scope$result_directory <- control$results
  scope$input_provenance <- control$input_provenance;scope$analysis_population <- control$analysis_population
  phase <- 'ANALYSIS';write_json(list(status='ORIGINAL_GGPUBR_CALLER_SCRIPT_STARTED',version=control$version,analysis_population=control$analysis_population),'analysis-started.json')
  sys.source(control$script,envir=scope,keep.source=TRUE)
  returned <- if(exists('analysis_complete',envir=scope,inherits=FALSE))get('analysis_complete',envir=scope,inherits=FALSE) else NULL
  if(!is.character(returned)||length(returned)!=1L||is.na(returned)||!identical(returned,control$expected_return))stop('Trusted script did not supply its exact complete-population marker')
 },warning=function(w)append_condition(w,'warning'),message=function(m)append_condition(m,'message')),
 error=function(e){failure <<- e;append_condition(e,'error')})
 # Preserve original warning behavior (including warn=2), full objects and calls.
 saveRDS(conditions,file.path(diagnostics,'complete-conditions.rds'),version=3)
 saveRDS(list(phase=phase,analysis_complete=returned,error=failure,conditions=conditions),file.path(diagnostics,'complete-original-run.rds'),version=3)
 if(!is.null(failure))saveRDS(failure,file.path(diagnostics,'original-error.rds'),version=3)
 portable <- lapply(conditions,function(c)list(ordinal=c$ordinal,kind=c$kind,failed_layer_warning=c$failed_layer_warning,class=c$class,message=c$message,
  call=if(is.null(c$call))NULL else deparse(c$call),calls=lapply(c$calls,deparse)))
 write_json(portable,'conditions.json');writeLines(capture.output(sessionInfo()),file.path(diagnostics,'session-info.txt'))
 failed_layers <- sum(vapply(conditions,function(c)c$failed_layer_warning,logical(1)))
 code <- if(phase=='READINESS'&&inherits(failure,'ggpubr_missing_dependency'))3L else if(!is.null(failure)||failed_layers>0L)2L else 0L
 status <- if(code==0L)'ORIGINAL_GGPUBR_SCRIPT_COMPLETE' else if(code==3L)'SKIP3_REQUIRED_ORIGINAL_DEPENDENCY_UNAVAILABLE' else 'FAIL2_ORIGINAL_GGPUBR_SCRIPT_UNADMITTED'
 write_json(list(status=status,exit_code=code,phase=phase,version=control$version,R_version=as.character(getRversion()),
  warning_count=sum(vapply(conditions,function(c)c$kind=='warning',logical(1))),message_count=sum(vapply(conditions,function(c)c$kind=='message',logical(1))),
  error_count=sum(vapply(conditions,function(c)c$kind=='error',logical(1))),failed_layer_warning_count=failed_layers,
  script_completed_marker=is.null(failure),checks=checks,
  error=if(is.null(failure))NULL else list(class=class(failure),message=conditionMessage(failure),call=deparse(conditionCall(failure)))),'worker-status.json')
 if(!is.null(failure))cat(status,': ',conditionMessage(failure),'\n',file=stderr(),sep='')
 if(failed_layers>0L)cat('FAIL2: original ggplot statistical computation failure warning(s); no complete plot analysis admitted.\n',file=stderr())
 quit(save='no',status=code,runLast=FALSE)
})
