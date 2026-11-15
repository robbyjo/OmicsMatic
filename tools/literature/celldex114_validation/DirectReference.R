# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent direct original call. Worker.R and its operation code are not used.
local({
 a<-commandArgs(TRUE);stopifnot(length(a)==1L);control<-jsonlite::read_json(a[[1L]],simplifyVector=FALSE)
 .libPaths(c(control$library,control$base_library));out<-control$output;stopifnot(!dir.exists(out));dir.create(out,recursive=TRUE)
 for(package in c('GenomeInfoDbData','celldex','gypsum','ExperimentHub','AnnotationHub','digest'))requireNamespace(package,quietly=TRUE)
 request<-readRDS(control$request)
 if(length(request$caller_packages))for(package in request$caller_packages)library(package,character.only=TRUE)
 if(length(request$options))options(request$options)
 gypsum::cacheDirectory(file.path(control$cache,'gypsum'))
 ExperimentHub::setExperimentHubOption('CACHE',file.path(control$cache,'ExperimentHub'))
 AnnotationHub::setAnnotationHubOption('CACHE',file.path(control$cache,'AnnotationHub'))
 if(length(request$hub_options))for(package in names(request$hub_options))for(option in names(request$hub_options[[package]])){
  setter<-getExportedValue(package,paste0('set',package,'Option'));setter(option,request$hub_options[[package]][[option]])
 }
 rng<-function()list(kind=RNGkind(),seed_exists=exists('.Random.seed',globalenv(),inherits=FALSE),
  seed=if(exists('.Random.seed',globalenv(),inherits=FALSE))get('.Random.seed',globalenv())else NULL)
 if(!is.null(request$rng)){
  do.call(RNGkind,as.list(request$rng$kind))
  if(isTRUE(request$rng$seed_exists)||!is.null(request$rng$seed))assign('.Random.seed',request$rng$seed,globalenv())
  else if(exists('.Random.seed',globalenv(),inherits=FALSE))rm('.Random.seed',envir=globalenv())
 }
 before<-rng();saveRDS(before,file.path(out,'complete-rng-before.rds'),version=3)
 returned<-FALSE;error<-value<-NULL;conditions<-list()
 record<-function(c,kind)conditions[[length(conditions)+1L]]<<-list(kind=kind,condition=c,class=class(c),message=conditionMessage(c),call=conditionCall(c))
 tryCatch(withCallingHandlers({value<-do.call(request$operation,request$arguments,envir=asNamespace('celldex'));returned<-TRUE},
  warning=function(c)record(c,'warning'),message=function(c)record(c,'message'),error=function(c){
   record(c,'error');saveRDS(list(error=c,calls=sys.calls()),file.path(out,'complete-error-and-calls.rds'),version=3)
  }),error=function(c){error<<-c})
 after<-rng()
 if(returned)saveRDS(value,file.path(out,'complete-return.rds'),version=3)
 saveRDS(list(conditions=conditions,error=error),file.path(out,'complete-conditions.rds'),version=3)
 saveRDS(list(before=before,after=after),file.path(out,'complete-rng.rds'),version=3)
 writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
 jsonlite::write_json(list(returned=returned,operation=request$operation,error=if(is.null(error))NULL else conditionMessage(error),conditions=length(conditions)),
  file.path(out,'STATUS.json'),pretty=TRUE,auto_unbox=TRUE,null='null')
 if(!returned)quit(save='no',status=2L,runLast=FALSE)
})
