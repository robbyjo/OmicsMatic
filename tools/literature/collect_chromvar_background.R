# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE);stopifnot(length(args)==1L)
paths <- readLines(args[[1]],encoding="UTF-8",warn=FALSE);stopifnot(length(paths)==5L)
.libPaths(c(paths[1:3],.Library),include.site=FALSE)
stopifnot(as.character(getRversion())=="4.6.1")
versions <- c(chromVAR="1.34.1",Matrix="1.7.5",nabor="0.5.0",Rcpp="1.1.2",RcppArmadillo="15.6.2.1",jsonlite="2.0.0")
for(pkg in names(versions))stopifnot(as.character(packageVersion(pkg))==versions[[pkg]])
suppressPackageStartupMessages(library(chromVAR));suppressPackageStartupMessages(library(Matrix))
input <- jsonlite::fromJSON(paths[[4]],simplifyVector=FALSE)
functions <- list()
for(profile in c("1.16.0-source","1.34.1-package")) {
  edition <- sub("-(source|package)$","",profile)
  e <- new.env(parent=asNamespace("chromVAR"));sys.source(file.path(paths[[5]],edition,"get_background_peaks_core.R"),envir=e)
  functions[[profile]] <- e$get_background_peaks_core
}
current <- getFromNamespace("get_background_peaks_core","chromVAR")
stopifnot(identical(body(current),body(functions[["1.34.1-package"]])),identical(formals(current),formals(functions[["1.34.1-package"]])))
functions[["1.34.1-public-Matrix-interface"]] <- function(object,bias,niterations,w,bs)getBackgroundPeaks(object,bias=bias,niterations=niterations,w=w,bs=bs)
RNGkind("Mersenne-Twister","Inversion","Rejection")
number <- function(x)if(is.character(x)&&x=="NaN")NaN else as.numeric(x)
encode <- function(x)if(is.nan(x))"NaN" else if(is.na(x))"NA" else if(is.infinite(x))if(x>0)"Inf" else "-Inf" else format(x,digits=17,scientific=FALSE,trim=TRUE)
results <- list()
for(case in input$cases){
  dataset <- input$datasets[[case$dataset]]
  counts <- do.call(rbind,lapply(dataset$counts,function(row)vapply(row,number,numeric(1))))
  object <- as(counts,"dgCMatrix");bias <- vapply(dataset$bias,number,numeric(1))
  for(profile in names(functions)){
    set.seed(as.integer(case$seed));before <- as.integer(.Random.seed);warnings <- character()
    result <- tryCatch(withCallingHandlers({
      background <- functions[[profile]](object,bias,as.integer(case$niterations),case$w,as.integer(case$bs))
      list(ok=TRUE,rows=nrow(background),columns=ncol(background),background=lapply(seq_len(nrow(background)),function(i)unname(as.list(vapply(background[i,],encode,character(1))))))
    },warning=function(w){warnings <<- c(warnings,conditionMessage(w));invokeRestart("muffleWarning")}),error=function(e)list(ok=FALSE,error=conditionMessage(e)))
    result$id <- case$id;result$profile <- profile;result$warnings <- unname(as.list(warnings));result$rng_before <- unname(as.list(before));result$rng_after <- unname(as.list(as.integer(.Random.seed)))
    results[[length(results)+1L]] <- result
  }
}
jsonlite::write_json(list(R_version=as.character(getRversion()),versions=as.list(versions),RNGkind=as.list(RNGkind()),results=results),"original.json",auto_unbox=TRUE,pretty=TRUE,na="string")
cat("COMPLETE_ORIGINAL_CHROMVAR_BACKGROUNDS\n")
