# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(trailingOnly=TRUE);stopifnot(length(args)==6L)
out <- args[1];.libPaths(args[2:3],include.site=FALSE);profile <- jsonlite::read_json(args[6],simplifyVector=FALSE)
stopifnot(as.character(getRversion())==profile$R,R.version$platform==profile$platform)
for(name in names(profile$packages))stopifnot(requireNamespace(name,quietly=TRUE),as.character(getNamespaceVersion(name))==profile$packages[[name]])
suppressPackageStartupMessages(library(monocle3))
guard <- function(package,source,names){
 ns <- asNamespace(package);definitions <- list()
 for(file in list.files(file.path(source,"R"),pattern="[.]R$",full.names=TRUE))for(expr in parse(file,keep.source=FALSE))if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]]) %in% c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))definitions[[as.character(expr[[2]])]] <- expr[[3]]
 for(name in names){stopifnot(!is.null(definitions[[name]]),exists(name,ns,inherits=FALSE));original <- eval(definitions[[name]],envir=ns);loaded <- get(name,ns,inherits=FALSE);stopifnot(identical(body(original),body(loaded)),identical(formals(original),formals(loaded)))}
 as.list(names)
}
g <- list(monocle3=guard("monocle3",args[4],unlist(profile$guarded_functions$monocle3,use.names=FALSE)),batchelor=guard("batchelor",args[5],unlist(profile$guarded_functions$batchelor,use.names=FALSE)))
stopifnot(normalizePath(find.package("monocle3"))==normalizePath(file.path(args[2],"monocle3")),normalizePath(find.package("batchelor"))==normalizePath(file.path(args[2],"batchelor")))
v <- list(status="READY_ORIGINAL_MONOCLE137_BATCHELOR120_NAMESPACE_NOT_ANALYSIS",packages=setNames(lapply(loadedNamespaces(),function(n)as.character(getNamespaceVersion(n))),loadedNamespaces()),guarded_functions=g,analysis_admitted=FALSE)
jsonlite::write_json(v,file.path(out,"READINESS.json"),pretty=TRUE,auto_unbox=TRUE,digits=NA)
writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"));cat(v$status,"\n")
