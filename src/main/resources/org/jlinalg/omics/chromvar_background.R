# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Run unchanged chromVAR source functions on explicitly recorded current dependencies.
args <- commandArgs(TRUE)
if(length(args)!=2L || args[[1]]!="--request-file")stop("Require --request-file UTF8_PROTOCOL")
dependency <- function(message)stop(structure(list(message=message,call=NULL),class=c("dependency_unavailable","error","condition")))
clean <- function(x)gsub("[\t\r\n]"," ",enc2utf8(x))
table <- function(x,path)write.table(x,path,sep="\t",quote=FALSE,row.names=FALSE,col.names=TRUE,na="NA",fileEncoding="UTF-8")
status <- function(state,detail,action)table(data.frame(status=state,detail=clean(detail),required_action=clean(action)),"worker-status.tsv")
warnings <- character()
exit <- tryCatch(withCallingHandlers({
  paths <- readLines(args[[2]],encoding="UTF-8",warn=FALSE)
  if(length(paths)!=4L)stop("Invalid worker protocol")
  if(any(!dir.exists(paths[1:3])))dependency("A declared private R library is unavailable")
  .libPaths(c(paths[1:3],.Library),include.site=FALSE)
  if(as.character(getRversion())!="4.6.1")dependency("Require the validated R 4.6.1 runtime; this worker did not run an analysis")
  versions <- c(chromVAR="1.34.1",Matrix="1.7.5",nabor="0.5.0",Rcpp="1.1.2",RcppArmadillo="15.6.2.1",jsonlite="2.0.0")
  for(pkg in names(versions))if(!requireNamespace(pkg,quietly=TRUE)||as.character(packageVersion(pkg))!=versions[[pkg]])dependency(paste("Require",pkg,versions[[pkg]],"in the declared private library paths"))
  suppressPackageStartupMessages(library(chromVAR));suppressPackageStartupMessages(library(Matrix))
  p <- jsonlite::fromJSON(paths[[4]],simplifyVector=TRUE)
  stopifnot(p$profile %in% c("1.16.0-source","1.34.1-package"),length(p$seed)==1L,is.finite(p$seed),p$seed>=0,p$seed<=.Machine$integer.max,p$seed==floor(p$seed),
            length(p$niterations)==1L,is.finite(p$niterations),p$niterations>=1,p$niterations<=10000,p$niterations==floor(p$niterations),
            length(p$bs)==1L,is.finite(p$bs),p$bs>=2,p$bs<=50,p$bs==floor(p$bs),length(p$w)==1L,is.finite(p$w),p$w>0,p$w<=1e6)
  functions <- list()
  for(edition in c("1.16.0","1.34.1")){
    e <- new.env(parent=asNamespace("chromVAR"));sys.source(file.path("source/chromvar-background",edition,"get_background_peaks_core.R"),envir=e)
    functions[[edition]] <- e$get_background_peaks_core
  }
  current <- getFromNamespace("get_background_peaks_core","chromVAR")
  if(!identical(body(current),body(functions[["1.34.1"]]))||!identical(formals(current),formals(functions[["1.34.1"]])))dependency("Installed chromVAR function differs from the retained validated source")
  stopifnot(identical(body(functions[["1.16.0"]]),body(functions[["1.34.1"]])),identical(formals(functions[["1.16.0"]]),formals(functions[["1.34.1"]])))
  cpp <- function(edition){path <- file.path("source/chromvar-background",edition,"utils.cpp");readBin(path,"raw",n=file.info(path)$size)}
  stopifnot(identical(cpp("1.16.0"),cpp("1.34.1")))
  object <- as(Matrix::readMM("counts.mtx"),"dgCMatrix")
  stopifnot(nrow(object)>=1L,ncol(object)>=1L,nrow(object)*p$niterations<=20000000,length(object@x)<=2000000)
  bias_table <- read.delim("bias.tsv",check.names=FALSE,colClasses="character",quote="",comment.char="",fileEncoding="UTF-8",na.strings=NULL)
  stopifnot(identical(names(bias_table),c("peak_index","bias")),nrow(bias_table)==nrow(object),identical(bias_table$peak_index,as.character(seq_len(nrow(object)))))
  decode <- function(x)if(x=="NA")NA_real_ else if(x=="NaN")NaN else if(x=="Inf")Inf else if(x=="-Inf")-Inf else as.numeric(x)
  bias <- vapply(bias_table$bias,decode,numeric(1))
  RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(as.integer(p$seed));before <- as.integer(.Random.seed)
  edition <- sub("-(source|package)$","",p$profile)
  background <- functions[[edition]](object,bias,as.integer(p$niterations),p$w,as.integer(p$bs));after <- as.integer(.Random.seed)
  set.seed(as.integer(p$seed));public_before <- as.integer(.Random.seed)
  replay <- chromVAR::getBackgroundPeaks(object,bias=bias,niterations=as.integer(p$niterations),w=p$w,bs=as.integer(p$bs));public_after <- as.integer(.Random.seed)
  stopifnot(identical(background,replay),identical(before,public_before),identical(after,public_after),identical(dim(background),c(nrow(object),as.integer(p$niterations))),all(is.finite(background)),all(background==floor(background)),all(background>=1L),all(background<=nrow(object)))
  table(cbind(data.frame(peak_index=seq_len(nrow(background))),setNames(as.data.frame(background),paste0("iteration_",seq_len(ncol(background))))),"background-original.tsv")
  for(name in c("before","after","public_after"))table(data.frame(index=seq_len(length(get(name))),value=get(name)),paste0("rng-",gsub("_","-",name),".tsv"))
  settings <- c(profile=p$profile,R_version=as.character(getRversion()),versions,seed=as.character(p$seed),niterations=as.character(p$niterations),w=sprintf("%.17g",p$w),bs=as.character(p$bs),peaks=as.character(nrow(object)),cells=as.character(ncol(object)),rng_kind=paste(RNGkind(),collapse=","),source_current_body_formals_identical="true",primary_public_background_and_RNG_identical="true",historical_whole_packages_executed="false",independent_native_RNG_replay="false",installed_DLL_source_authentication="false")
  table(data.frame(key=names(settings),value=unname(settings)),"effective-settings.tsv")
  capture.output(sessionInfo(),file="sessionInfo.txt")
  status("completed-with-warnings","Original background and public interface replay matched exactly","Review source profiles, GC bias, input populations, seed, warnings and downstream deviation settings. This is source replay on recorded current dependencies.")
  0L
},warning=function(w){warnings <<- c(warnings,clean(conditionMessage(w)));invokeRestart("muffleWarning")}),dependency_unavailable=function(e){
  status("skipped-dependency-unavailable",conditionMessage(e),"Supply R 4.6.1 and exact validated packages in separate private library paths only where permitted. If unavailable or installation is forbidden, keep this analysis skipped; no result was admitted.");3L
},error=function(e){
  status("failed",conditionMessage(e),"No result was admitted. Preserve this attempt; inspect reference/bias, positive pooled counts, covariance rank, source settings and capacities. Correct the named cause and retry into a NEW output path.");2L
})
table(data.frame(warning=warnings),"worker-warnings.tsv")
quit(save="no",status=exit,runLast=FALSE)
