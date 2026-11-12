# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(trailingOnly=TRUE);stopifnot(length(args)==5L)
out <- args[1];source_root <- args[2];profile_path <- args[3];.libPaths(args[4:5],include.site=FALSE)
stopifnot(as.character(getRversion())=="4.3.3",R.version$platform=="x86_64-conda-linux-gnu")
profile <- jsonlite::read_json(profile_path,simplifyVector=FALSE)
for(name in names(profile$packages))stopifnot(requireNamespace(name,quietly=TRUE),as.character(getNamespaceVersion(name))==profile$packages[[name]])
suppressPackageStartupMessages(library(monocle3));stopifnot(as.character(getNamespaceVersion("monocle3"))=="1.3.1")
ns <- asNamespace("monocle3");definitions <- list()
for(file in list.files(file.path(source_root,"R"),pattern="[.]R$",full.names=TRUE))for(expr in parse(file,keep.source=FALSE))if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]]) %in% c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))definitions[[as.character(expr[[2]])]] <- expr[[3]]
for(name in unlist(profile$guarded_functions,use.names=FALSE)){stopifnot(!is.null(definitions[[name]]),exists(name,ns,inherits=FALSE));original <- eval(definitions[[name]],envir=ns);loaded <- get(name,ns,inherits=FALSE);stopifnot(identical(body(original),body(loaded)),identical(formals(original),formals(loaded)))}
jsonlite::write_json(list(status="READY_ORIGINAL_MONOCLE131_MODEL_NAMESPACE_NOT_ANALYSIS",monocle3=as.character(getNamespaceVersion("monocle3")),R=as.character(getRversion()),namespace=find.package("monocle3"),guarded_functions=profile$guarded_functions,packages=profile$packages,original_analysis_run=FALSE),file.path(out,"READINESS.json"),pretty=TRUE,auto_unbox=TRUE,digits=NA)
writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"));cat("READY_ORIGINAL_MONOCLE131_MODEL_NAMESPACE_NOT_ANALYSIS\n")
