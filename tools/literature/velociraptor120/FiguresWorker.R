# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original R graph objects are retained as complete RDS, with complete SVG/PNG.
# This graph protocol makes no environment/pointer or opaque RDS byte identity claim.
local({
 args <- commandArgs(TRUE);stopifnot(length(args)==1L)
 control <- jsonlite::fromJSON(args[[1L]],simplifyVector=FALSE);.libPaths(c(control$library,control$base_library))
 diagnostics <- control$diagnostics;phase <- 'READINESS';conditions <- list();failure <- NULL;scope <- new.env(parent=globalenv());graph <- NULL;serialized <- rendered <- FALSE
 write.json <- function(value,name)jsonlite::write_json(value,file.path(diagnostics,name),pretty=TRUE,auto_unbox=TRUE,null='null',na='string',digits=17)
 record <- function(c,kind)conditions[[length(conditions)+1L]] <<- list(kind=kind,phase=phase,object=c,class=class(c),message=conditionMessage(c),call=conditionCall(c),calls=sys.calls())
 tryCatch(withCallingHandlers({
  source(control$audit_script,local=TRUE)
  auditVelociraptorPlots(control$library,control$source_root,file.path(diagnostics,'namespace'))
  for (name in control$required_exports)stopifnot(is.function(getExportedValue('velociraptor',name)))
  for (name in names(control$required_packages)) {
   if (!requireNamespace(name,quietly=TRUE) || as.character(packageVersion(name))!=control$required_packages[[name]])stop(structure(list(message=paste('Required original figure dependency unavailable:',name,control$required_packages[[name]]),call=NULL),class=c('velociraptor_missing_dependency','error','condition')))
  }
  scope$input_directory <- control$inputs;scope$result_directory <- control$results;scope$input_provenance <- control$input_provenance;scope$analysis_population <- control$analysis_population
  phase <- 'ANALYSIS';write.json(list(status='EXACT_TRUSTED_ORIGINAL_R_FIGURE_SCRIPT_STARTED',analysis_population=control$analysis_population),'analysis-started.json')
  sys.source(control$script,envir=scope,keep.source=TRUE)
  if (!exists('analysis_complete',envir=scope,inherits=FALSE) || !identical(get('analysis_complete',envir=scope,inherits=FALSE),control$expected_return))stop('Exact complete-figure marker missing or different')
  if (!exists('analysis_result',envir=scope,inherits=FALSE))stop('Complete original graph analysis_result missing')
  graph <- get('analysis_result',envir=scope,inherits=FALSE)
  if (!inherits(graph,c('ggplot','patchwork','ggplot2::ggplot')))stop('FIGURES requires the complete original ggplot/patchwork graph; use numeric R_SCRIPT for computational values')
  phase <- 'COMPLETE_ORIGINAL_GRAPH_SERIALIZATION'
  saveRDS(graph,file.path(control$results,'original-graph.rds'),version=3);serialized <- TRUE
  saveRDS(control$render,file.path(control$results,'render-options.rds'),version=3)
  rng <- list(kind=RNGkind(),before.render=if(exists('.Random.seed',envir=.GlobalEnv))get('.Random.seed',envir=.GlobalEnv)else NULL)
  phase <- 'ORIGINAL_SVG_RENDER';set.seed(as.integer(control$render$seed));rng$before.svg <- get('.Random.seed',envir=.GlobalEnv)
  grDevices::svg(file.path(control$results,'figure.svg'),width=control$render$width_inches,height=control$render$height_inches,bg=control$render$background)
  tryCatch(print(graph),finally=grDevices::dev.off());rng$after.svg <- get('.Random.seed',envir=.GlobalEnv)
  phase <- 'ORIGINAL_PNG_RENDER';set.seed(as.integer(control$render$seed));rng$before.png <- get('.Random.seed',envir=.GlobalEnv)
  grDevices::png(file.path(control$results,'figure.png'),width=control$render$width_inches,height=control$render$height_inches,units='in',res=control$render$png_dpi,type='cairo',bg=control$render$background)
  tryCatch(print(graph),finally=grDevices::dev.off());rng$after.png <- get('.Random.seed',envir=.GlobalEnv)
  saveRDS(graph,file.path(control$results,'graph-after-render.rds'),version=3);saveRDS(rng,file.path(control$results,'render-rng-state.rds'),version=3);rendered <- TRUE
  if (reticulate::py_available(initialize=FALSE))stop('R-only FIGURES initialized Python unexpectedly; use a declared fitting/embedding provider')
  ns <- asNamespace('velociraptor');checked <- character()
  for (file in list.files(file.path(control$source_root,'R'),pattern='\\.R$',full.names=TRUE)) {
   for (expr in parse(file,keep.source=FALSE)) {
    if (is.call(expr) && identical(expr[[1L]],as.name('<-')) && length(expr)==3L && is.symbol(expr[[2L]]) && is.call(expr[[3L]]) && identical(expr[[3L]][[1L]],as.name('function'))) {
     name <- as.character(expr[[2L]]);f <- get(name,envir=ns,inherits=FALSE);stopifnot(identical(formals(f),expr[[3L]][[2L]]),identical(body(f),expr[[3L]][[3L]]));checked <- c(checked,name)
    }
   }
  }
  stopifnot(length(checked)==11L);phase <- 'COMPLETE'
 },warning=function(w)record(w,'warning'),message=function(m)record(m,'message'),error=function(e)saveRDS(list(error=e,calls=sys.calls()),file.path(diagnostics,'complete-original-error-and-calls.rds'),version=3)),error=function(e){failure <<- e;record(e,'error')})
 saveRDS(scope,file.path(diagnostics,'complete-available-caller-scope.rds'),version=3)
 saveRDS(list(error=failure,conditions=conditions,phase=phase),file.path(diagnostics,'complete-original-run.rds'),version=3)
 saveRDS(conditions,file.path(diagnostics,'complete-conditions.rds'),version=3)
 write.json(lapply(conditions,function(c)list(kind=c$kind,phase=c$phase,class=c$class,message=c$message,call=if(is.null(c$call))NULL else deparse(c$call),calls=lapply(c$calls,deparse))),'conditions.json')
 writeLines(capture.output(sessionInfo()),file.path(diagnostics,'original-R-session-info.txt'))
 temporary <- tempdir();retained <- file.path(control$native_temporary_directory,'retained-R-session-tempdir');temp.retained <- !file.exists(retained) && isTRUE(file.rename(temporary,retained))
 if (!temp.retained && is.null(failure))failure <- simpleError('Could not preserve the original R temporary session tree')
 code <- if(is.null(failure))0L else if(phase=='READINESS' && inherits(failure,'velociraptor_missing_dependency'))3L else 2L
 write.json(list(status=if(code==0L)'COMPLETE_ORIGINAL_VELOCIRAPTOR120_R_FIGURES'else if(code==3L)'SKIP3_REQUIRED_ORIGINAL_FIGURE_DEPENDENCY_UNAVAILABLE'else'FAIL2_ORIGINAL_FIGURES_UNADMITTED',exit_code=code,phase=phase,
  warning_count=sum(vapply(conditions,function(c)c$kind=='warning',logical(1))),message_count=sum(vapply(conditions,function(c)c$kind=='message',logical(1))),error_count=sum(vapply(conditions,function(c)c$kind=='error',logical(1))),raw_console_diagnostic_counts_complete=FALSE,
  complete_original_graph_serialized=serialized,complete_original_SVG_and_PNG_rendered=rendered,original_R_temporary_tree_retained=temp.retained,original_R_temp_path=temporary,retained_R_temp_path=retained,
  graph_class=if(is.null(graph))NULL else class(graph),graph_environment_pointer_or_opaque_RDS_byte_parity_claimed=FALSE,python_initialized=reticulate::py_available(initialize=FALSE),error=if(is.null(failure))NULL else list(class=class(failure),message=conditionMessage(failure),call=deparse(conditionCall(failure)))),'worker-status.json')
 if (!is.null(failure))cat('ORIGINAL_FIGURE_UNADMITTED: ',conditionMessage(failure),'\n',file=stderr(),sep='')
 quit(save='no',status=code,runLast=FALSE)
})
