# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent direct calls to unchanged original exports; no production worker/audit helper.
local({
 a<-commandArgs(TRUE);stopifnot(length(a)==1L)
 c<-jsonlite::read_json(a[[1L]],simplifyVector=FALSE);out<-c$output
 stopifnot(!dir.exists(out));dir.create(out)
 .libPaths(c(c$library,c$base_library));suppressPackageStartupMessages(requireNamespace('SPOTlight',quietly=TRUE))
 requireNamespace('Matrix',quietly=TRUE);requireNamespace('SingleCellExperiment',quietly=TRUE)
 req<-readRDS(c$request);events<-list();error<-NULL;returned<-FALSE;value<-NULL;phase<-'PREPARE'
 rng<-function()list(kind=RNGkind(),seed_exists=exists('.Random.seed',globalenv(),inherits=FALSE),seed=if(exists('.Random.seed',globalenv(),inherits=FALSE))get('.Random.seed',globalenv())else NULL)
 rec<-function(e,kind)events[[length(events)+1L]]<<-list(kind=kind,phase=phase,condition=e,class=class(e),message=conditionMessage(e),call=conditionCall(e))
 tryCatch(withCallingHandlers({
  for(p in req$caller_packages)library(p,character.only=TRUE)
  if(length(req$options))options(req$options)
  do.call(RNGkind,as.list(req$rng$kind));assign('.Random.seed',req$rng$seed,envir=globalenv());before<-rng()
  saveRDS(before,file.path(out,'rng-before.rds'),version=3)
  phase<-'ORIGINAL_ANALYSIS'
  if(req$operation=='train-deconvolve'){
   fit<-do.call('trainNMF',req$arguments$training,envir=asNamespace('SPOTlight'))
   saveRDS(fit,file.path(out,'training-return.rds'),version=3)
   value<-do.call('runDeconvolution',c(req$arguments$deconvolution,list(mod=fit$mod,ref=fit$topic)),envir=asNamespace('SPOTlight'))
  }else value<-do.call(req$operation,req$arguments,envir=asNamespace('SPOTlight'))
  returned<-TRUE;after<-rng();saveRDS(value,file.path(out,'return.rds'),version=3)
  saveRDS(list(before=before,after=after),file.path(out,'rng.rds'),version=3)
 },warning=function(e)rec(e,'warning'),message=function(e)rec(e,'message'),error=function(e)rec(e,'error')),error=function(e)error<<-e)
 if(!is.null(error))saveRDS(list(before=before,after=rng()),file.path(out,'failed-rng.rds'),version=3)
 saveRDS(list(returned=returned,events=events,error=error),file.path(out,'status.rds'),version=3)
 writeLines(capture.output(sessionInfo()),file.path(out,'session.txt'))
 quit(save='no',status=if(is.null(error))0L else 2L,runLast=FALSE)
})
