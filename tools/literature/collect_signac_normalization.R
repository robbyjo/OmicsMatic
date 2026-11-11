# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original package + explicitly labelled extracted historical source controls.
args <- commandArgs(trailingOnly=TRUE)
stopifnot(length(args)==4L)
.libPaths(c(normalizePath(args[[3]]), normalizePath("build/omics-sctransform-r"), .Library))
suppressPackageStartupMessages(library(Signac))
suppressPackageStartupMessages(library(Matrix))
stopifnot(identical(as.character(packageVersion("Signac")), "1.17.1"))
input <- jsonlite::read_json(args[[1]], simplifyVector=FALSE)
versions <- c("1.6.0","1.7.0","1.9.0","1.14.0","1.17.1")
functions <- c("RunTFIDF.default","FindTopFeatures.default","FindTopFeatures.Assay")
editions <- list()
for (version in versions) {
  path <- file.path(args[[4]],version,"source/Signac/R/preprocessing.R")
  expressions <- parse(file=path,keep.source=FALSE)
  env <- new.env(parent=asNamespace("Signac"))
  selected <- vapply(expressions,function(e) is.call(e) && identical(e[[1]],as.name("<-")) && as.character(e[[2]]) %in% functions,logical(1))
  stopifnot(sum(selected)==3L)
  for (expr in expressions[selected]) eval(expr,envir=env)
  if (version=="1.17.1") for (name in functions) stopifnot(identical(body(get(name,env)),body(get(name,asNamespace("Signac")))))
  editions[[version]] <- env
}
capture <- function(f) {
  warnings <- character()
  result <- withCallingHandlers(f(),warning=function(w){warnings <<- c(warnings,conditionMessage(w));invokeRestart("muffleWarning")})
  list(result=result,warnings=warnings)
}
vector <- function(x) unname(as.vector(x))
matrix_result <- function(x) list(p=vector(x@p),i=vector(x@i),x=vector(x@x),features=rownames(x),cells=colnames(x))
metadata_result <- function(x) list(features=rownames(x),count=vector(x$count),percentile=vector(x$percentile))
results <- list(); ordinal <- 0L
for (case in input$matrices) {
  x <- new("dgCMatrix", i=as.integer(unlist(case$i)), p=as.integer(unlist(case$p)), x=as.numeric(unlist(case$x)),
           Dim=as.integer(c(length(case$features),length(case$cells))),Dimnames=list(unlist(case$features),unlist(case$cells)))
  validObject(x)
  # Matrix metadata: even a supplied numeric cutoff must not remove rows.
  meta <- Signac::FindTopFeatures(x,min.cutoff=5,verbose=FALSE)
  historical_metadata <- lapply(editions,function(env)metadata_result(get("FindTopFeatures.default",env)(x,min.cutoff=5,verbose=FALSE)))
  stopifnot(all(vapply(historical_metadata,identical,logical(1),metadata_result(meta))))
  selections <- list()
  for (cutoff in input$cutoffs) {
    value <- switch(cutoff$kind, ALL=NULL,COUNT=cutoff$value,PERCENTILE=paste0("q",cutoff$value),KEEP_EXISTING=NA)
    assay <- SeuratObject::CreateAssayObject(counts=x,min.cells=0,min.features=0)
    old <- rev(rownames(x))[seq_len(min(2L,nrow(x)))]
    SeuratObject::VariableFeatures(assay) <- old
    current <- Signac::FindTopFeatures(assay,min.cutoff=value,verbose=FALSE)
    selected <- SeuratObject::VariableFeatures(current)
    # Historical wrappers execute against the current SeuratObject API. This is
    # source-wrapper evidence, NOT a historical Signac/Seurat installation.
    historical <- lapply(names(editions),function(version) {
      outcome <- tryCatch({
        result <- suppressWarnings(get("FindTopFeatures.Assay",editions[[version]])(assay,min.cutoff=value,verbose=FALSE))
        list(status="passed-extracted-wrapper-current-SeuratObject-not-historical-package",selected=vector(SeuratObject::VariableFeatures(result)))
      },error=function(e)list(status="SKIP3_HISTORICAL_ASSAY_WRAPPER_API_INCOMPATIBLE",error=conditionMessage(e),
                             required_action="Skip this historical wrapper on SeuratObject5.4. Use an isolated compatible historical SeuratObject runtime if historical whole-assay reproduction is required. Do not change original source or suppress a defunct-argument error."))
      if(version %in% c("1.6.0","1.7.0","1.9.0")) {
        stopifnot(outcome$status=="SKIP3_HISTORICAL_ASSAY_WRAPPER_API_INCOMPATIBLE",grepl("defunct",outcome$error,fixed=TRUE))
      } else stopifnot(outcome$status=="passed-extracted-wrapper-current-SeuratObject-not-historical-package",identical(outcome$selected,vector(selected)))
      outcome
    })
    names(historical) <- names(editions)
    selections[[length(selections)+1L]] <- list(kind=cutoff$kind,value=cutoff$value,existing=vector(old),selected=vector(selected),historical_wrappers=historical)
  }
  normalized <- list()
  for (config in input$configurations) {
    idf <- if(config$supplied)as.numeric(unlist(case$idf)) else NULL
    current <- capture(function()Signac::RunTFIDF(x,method=config$method,scale.factor=config$scale,idf=idf,verbose=FALSE))
    historical <- lapply(editions,function(env)capture(function()get("RunTFIDF.default",env)(x,method=config$method,scale.factor=config$scale,idf=idf,verbose=FALSE)))
    stopifnot(all(vapply(historical,function(v)identical(matrix_result(v$result),matrix_result(current$result)) && identical(v$warnings,current$warnings),logical(1))))
    normalized[[length(normalized)+1L]] <- list(method=config$method,scale=config$scale,supplied=config$supplied,
                                              matrix=matrix_result(current$result),warnings=current$warnings)
  }
  ordinal <- ordinal+1L
  results[[ordinal]] <- list(id=case$id,metadata=metadata_result(meta),selections=selections,normalized=normalized)
}
result <- list(schema_version=1,status="passed-full-original-Signac-1.17.1-and-extracted-historical-functions",
               original_package=as.character(packageVersion("Signac")),Matrix=as.character(packageVersion("Matrix")),
               SeuratObject=as.character(packageVersion("SeuratObject")),historical_versions=versions,
               historical_packages_installed=FALSE,historical_source_functions_only=TRUE,
               historical_assay_wrapper_skipped_versions=c("1.6.0","1.7.0","1.9.0"),
               original_package_function_bodies_equal_retained_CRAN_source=TRUE,
               matrices=results)
jsonlite::write_json(result,args[[2]],pretty=TRUE,auto_unbox=TRUE,digits=17,na="string",null="null")
cat("COMPLETE_ORIGINAL_SIGNAC_CONTROLS",length(results),length(input$configurations),"FIVE_EXTRACTED_EDITIONS\n")
