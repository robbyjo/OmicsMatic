# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE)
stopifnot(length(args)==3L)
.libPaths(c(args[1],R.home("library")))
stopifnot(as.character(packageVersion("bigsnpr"))==args[2])
library(bigsnpr)
stopifnot(normalizePath(find.package("bigsnpr"))==normalizePath(file.path(args[1],"bigsnpr")))
expressions <- parse(file.path(args[3],"original-LDpred2.R"),keep.source=FALSE)
checked <- character()
for(expression in expressions) {
 if(is.call(expression)&&identical(expression[[1]],as.name("<-"))&&as.character(expression[[2]])%in%c("snp_ldpred2_inf","snp_ldpred2_grid","snp_ldpred2_auto")) {
  name <- as.character(expression[[2]])
  expected <- eval(expression[[3]],envir=asNamespace("bigsnpr"));actual <- get(name,asNamespace("bigsnpr"))
  stopifnot(identical(formals(expected),formals(actual)))
  stopifnot(identical(deparse(body(expected),width.cutoff=500L),deparse(body(actual),width.cutoff=500L)))
  checked <- c(checked,name)
 }
}
stopifnot(length(checked)==3L)
writeLines(c(capture.output(sessionInfo()),capture.output(.libPaths())),file.path(args[3],"sessionInfo.txt"))
writeLines(normalizePath(getLoadedDLLs()[["bigsnpr"]][["path"]]),file.path(args[3],"loaded-dll.txt"))
writeLines(paste0("original_R_function_bodies_checked=",length(checked)),file.path(args[3],"PROBE_PASSED.txt"))
