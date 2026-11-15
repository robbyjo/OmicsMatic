# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
local({
 a<-commandArgs(TRUE);stopifnot(length(a)==1L);control<-jsonlite::read_json(a[[1L]],simplifyVector=FALSE)
 out<-control$output;stopifnot(!dir.exists(out));dir.create(out,recursive=TRUE)
 source(control$audit);celldex.audit(control,file.path(out,'complete-precall-source-audit.rds'))
 request<-readRDS(control$request)
 stopifnot(identical(request$edition,'1.14.0'),isTRUE(request$schema==1L),is.list(request$arguments))
 operations<-c('BlueprintEncodeData','DatabaseImmuneCellExpressionData','HumanPrimaryCellAtlasData','ImmGenData','MonacoImmuneData',
  'MouseRNAseqData','NovershternHematopoieticData','fetchReference','fetchMetadata','listReferences','listVersions','fetchLatestVersion',
  'searchReferences','surveyReferences','defineTextQuery')
 stopifnot(length(request$operation)==1L,request$operation%in%operations)
 if('cache'%in%names(request$arguments))stop('Cache paths are managed explicitly by the adapter; seed --reference-cache and omit the original cache argument')
 if(!isTRUE(control$allow_download)&&request$operation%in%c('listReferences','listVersions','fetchLatestVersion')){
  jsonlite::write_json(list(status='SKIP3_ORIGINAL_REST_LISTING_REQUIRES_NETWORK_PERMISSION',operation=request$operation,analysis_started=FALSE,
   required_action='This original listing/latest operation contacts the remote service. Supply explicit permitted --allow-download or use an explicitly versioned cached fetchReference operation.'),
   file.path(out,'PREREQUISITE-SKIPPED.json'),pretty=TRUE,auto_unbox=TRUE)
  quit(save='no',status=3L,runLast=FALSE)
 }
 if(length(request$caller_packages))for(package in request$caller_packages)library(package,character.only=TRUE)
 if(length(request$options))options(request$options)
 cache<-control$cache
 gypsum::cacheDirectory(file.path(cache,'gypsum'))
 ExperimentHub::setExperimentHubOption('CACHE',file.path(cache,'ExperimentHub'))
 AnnotationHub::setAnnotationHubOption('CACHE',file.path(cache,'AnnotationHub'))
 if(length(request$hub_options))for(package in names(request$hub_options)){
  stopifnot(package%in%c('ExperimentHub','AnnotationHub'))
  for(option in names(request$hub_options[[package]])){
   if(identical(option,'CACHE'))stop('Use the declared managed reference cache')
   setter<-getExportedValue(package,paste0('set',package,'Option'));setter(option,request$hub_options[[package]][[option]])
  }
 }
 rng<-function()list(kind=RNGkind(),seed_exists=exists('.Random.seed',globalenv(),inherits=FALSE),
  seed=if(exists('.Random.seed',globalenv(),inherits=FALSE))get('.Random.seed',globalenv())else NULL)
 saveRDS(list(request=request,managed_cache=cache,search_path=search(),loaded_namespaces=loadedNamespaces(),library_paths=.libPaths(),
  selected_options=options()[c('warn','warnPartialMatchDollar')],gypsum_rest_url=gypsum::restUrl()),file.path(out,'complete-caller-context.rds'),version=3)
 if(!is.null(request$rng)){
  do.call(RNGkind,as.list(request$rng$kind))
  if(isTRUE(request$rng$seed_exists)||!is.null(request$rng$seed))assign('.Random.seed',request$rng$seed,globalenv())
  else if(exists('.Random.seed',globalenv(),inherits=FALSE))rm('.Random.seed',envir=globalenv())
 }
 before<-rng();saveRDS(before,file.path(out,'complete-original-rng-before.rds'),version=3)
 conditions<-list();error<-value<-NULL;returned<-FALSE
 record<-function(c,kind)conditions[[length(conditions)+1L]]<<-list(kind=kind,condition=c,class=class(c),message=conditionMessage(c),call=conditionCall(c))
 tryCatch(withCallingHandlers({
  value<-do.call(request$operation,request$arguments,envir=asNamespace('celldex'));returned<-TRUE
 },warning=function(c)record(c,'warning'),message=function(c)record(c,'message'),error=function(c){
  record(c,'error');saveRDS(list(error=c,calls=sys.calls()),file.path(out,'complete-original-error-and-calls.rds'),version=3)
 }),error=function(c){error<<-c})
 after<-rng()
 transport.error<-function(c){
  if(is.null(c))return(FALSE)
  if(inherits(c,c('curl_error','httr2_failure')))return(TRUE)
  if(is.list(c)&&!is.null(c$parent))return(transport.error(c$parent))
  FALSE
 }
 if(returned)saveRDS(value,file.path(out,'complete-original-return.rds'),version=3)
 saveRDS(list(conditions=conditions,error=error),file.path(out,'complete-original-conditions.rds'),version=3)
 saveRDS(list(before=before,after=after),file.path(out,'complete-original-rng.rds'),version=3)
 writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
 celldex.audit(control,file.path(out,'complete-postcall-source-audit.rds'))
 jsonlite::write_json(list(status=if(returned)'ORIGINAL_CELLDEX_RETURNED'else'ORIGINAL_CELLDEX_ERROR_NO_RESULT',analysis_started=TRUE,
  original_function_returned=returned,operation=request$operation,edition='1.14.0',
  error=if(is.null(error))NULL else list(class=class(error),message=conditionMessage(error)),
  unavailable_reference_transport=transport.error(error),conditions=length(conditions),
  warning_messages=lapply(Filter(function(x)identical(x$kind,'warning'),conditions),function(x)x$message)),
  file.path(out,'R-status.json'),pretty=TRUE,auto_unbox=TRUE,null='null')
 if(!returned)quit(save='no',status=2L,runLast=FALSE)
})
