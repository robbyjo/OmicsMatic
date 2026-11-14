# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Retain complete original R objects; no flattening of Seurat closures/environments.
local({
 args <- commandArgs(TRUE);stopifnot(length(args)==1L)
 control <- jsonlite::fromJSON(args[[1L]],simplifyVector=FALSE)
 .libPaths(c(control$library,control$base_library))
 out <- control$output;phase <- "READINESS";conditions <- list();failure <- NULL;value <- NULL
 # Keep the entire original condition. Error stacks are captured below before
 # unwinding. Injecting every live stack into every ordinary message duplicates
 # large Seurat call arguments and can make diagnostic serialization enormous.
 record <- function(c,kind) conditions[[length(conditions)+1L]] <<- list(kind=kind,condition=c,class=class(c),message=conditionMessage(c),call=conditionCall(c))
 audit <- function(label) {
  profile <- jsonlite::fromJSON(control$profile,simplifyVector=FALSE)
  stopifnot(as.character(getRversion())==profile$selected_R)
  for (package in names(profile$selected_dependency_versions)) {
   loadNamespace(package)
   stopifnot(as.character(packageVersion(package))==profile$selected_dependency_versions[[package]])
  }
  ns <- asNamespace("DoubletFinder");checked <- character()
  for (file in sort(list.files(file.path(control$source,"R"),full.names=TRUE,pattern="[.]R$")))for (expression in parse(file,keep.source=FALSE)) {
   if(is.call(expression) && identical(expression[[1L]],as.name("<-")) && is.symbol(expression[[2L]]) && is.call(expression[[3L]]) && identical(expression[[3L]][[1L]],as.name("function"))) {
    name <- as.character(expression[[2L]]);original <- eval(expression[[3L]],envir=ns);installed <- get(name,envir=ns,inherits=FALSE)
    stopifnot(identical(formals(original),formals(installed)),identical(body(original),body(installed)))
    checked <- c(checked,name)
   }
  }
  stopifnot(identical(sort(checked),sort(unlist(profile$namespace_functions,use.names=FALSE))))
  saveRDS(list(checked=checked,session=sessionInfo()),file.path(out,paste0(label,"-namespace-and-session.rds")),version=3)
 }
 tryCatch(withCallingHandlers({
  audit("before")
  phase <- "READ_REQUEST"
  request <- readRDS(control$request)
  stopifnot(is.list(request),is.character(request$operation),length(request$operation)==1L,request$operation %in% c("doubletFinder","paramSweep","summarizeSweep","find.pK","modelHomotypic"),is.list(request$arguments),is.list(request$rng),is.character(request$rng$kind),length(request$rng$kind)==3L,is.integer(request$rng$seed),length(request$rng$seed)>0L)
  grDevices::png(file.path(out,"original-plot-%03d.png"),width=800L,height=800L,type="cairo")
  grDevices::dev.control(displaylist="enable")
  do.call(RNGkind,as.list(request$rng$kind));assign(".Random.seed",request$rng$seed,envir=globalenv())
  rng.before <- list(kind=RNGkind(),seed=get(".Random.seed",envir=globalenv()))
  phase <- "ORIGINAL_ANALYSIS"
  value <- do.call(getExportedValue("DoubletFinder",request$operation),request$arguments)
  phase <- "COMPLETE_ORIGINAL_RESULT_RECORDING"
  saveRDS(value,file.path(out,"complete-result.rds"),version=3)
  saveRDS(list(before=rng.before,after=list(kind=RNGkind(),seed=get(".Random.seed",envir=globalenv()))),file.path(out,"complete-rng-before-after.rds"),version=3)
  saveRDS(grDevices::recordPlot(),file.path(out,"complete-original-recorded-plot.rds"),version=3)
  grDevices::dev.off()
  phase <- "POST_ANALYSIS_SOURCE_AUDIT"
  audit("after")
  phase <- "COMPLETE"
 },warning=function(c)record(c,"warning"),message=function(c)record(c,"message"),error=function(c){saveRDS(list(error=c,calls=sys.calls()),file.path(out,"complete-original-failure-and-calls.rds"),version=3)}),error=function(c){failure <<- c;record(c,"error")})
 saveRDS(list(phase=phase,conditions=conditions,error=failure),file.path(out,"complete-conditions.rds"),version=3)
 writeLines(capture.output(sessionInfo()),file.path(out,"original-session-info.txt"))
 counts <- vapply(c("warning","message","error"),function(kind)sum(vapply(conditions,function(c)identical(c$kind,kind),logical(1))),integer(1))
 groups <- list();lookup <- new.env(parent=emptyenv())
 for (entry in conditions) {
  key <- paste(entry$kind,paste(entry$class,collapse="|"),entry$message,sep="\r")
  if(exists(key,envir=lookup,inherits=FALSE)) {
   index<-get(key,envir=lookup,inherits=FALSE);groups[[index]]$count<-groups[[index]]$count+1L
  } else {
   index<-length(groups)+1L;assign(key,index,envir=lookup);groups[[index]]<-list(kind=entry$kind,class=entry$class,message=entry$message,count=1L)
  }
 }
 iteration.warnings <- sum(vapply(conditions,function(entry)identical(entry$kind,"warning") && grepl("iteration limit reached",entry$message,fixed=TRUE),logical(1)))
 jsonlite::write_json(list(groups=groups,all_original_conditions="complete-conditions.rds",R_handler_counts=as.list(counts),recognized_iteration_limit_warnings=iteration.warnings,convergence_qualified=FALSE,raw_console_diagnostic_counts_complete=FALSE),file.path(out,"condition-summary.json"),pretty=TRUE,auto_unbox=TRUE,null="null",na="string",digits=17)
 status <- list(status=if(is.null(failure))"PASS_ORIGINAL_DOUBLET_FINDER206_COMPLETE_R_OBJECT"else"FAIL2_ORIGINAL_DOUBLET_FINDER206_UNADMITTED",phase=phase,complete_original_RDS_result=is.null(failure),R_handler_counts=as.list(counts),recognized_iteration_limit_warnings=iteration.warnings,convergence_qualified=FALSE,raw_console_diagnostic_counts_complete=FALSE,portable_value_tree_claimed=FALSE,whole_tool_qualified=FALSE,author_environment_inferred=FALSE)
 if(!is.null(failure))status$error <- list(class=class(failure),message=conditionMessage(failure),call=deparse(conditionCall(failure)))
 jsonlite::write_json(status,file.path(out,"R-status.json"),pretty=TRUE,auto_unbox=TRUE,null="null",na="string",digits=17)
 cat(status$status,"\n")
 if(!is.null(failure))quit(save="no",status=2L,runLast=FALSE)
})
