# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==3L,getRversion()=="4.4.1")
lib<-args[[1L]];out<-args[[2L]];source<-args[[3L]];.libPaths(c(lib,.Library))
ns<-loadNamespace("Rsubread",lib.loc=lib);stopifnot(as.character(utils::packageVersion("Rsubread",lib.loc=lib))=="2.23.2")
reference<-new.env(parent=baseenv());sys.source(source,envir=reference);actual<-get("featureCounts",envir=ns,inherits=FALSE);expected<-get("featureCounts",envir=reference,inherits=FALSE)
stopifnot(identical(formals(actual),formals(expected)),identical(body(actual),body(expected)))
saveRDS(formals(actual),file.path(out,"featureCounts-formals.rds"),version=3L);saveRDS(body(actual),file.path(out,"featureCounts-body.rds"),version=3L)
writeLines(capture.output(sessionInfo()),file.path(out,"sessionInfo.txt"));dlls<-getLoadedDLLs();write.table(data.frame(name=names(dlls),path=vapply(dlls,function(x)x[["path"]],"")),file=file.path(out,"loaded-dlls.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
writeLines("LOADED_ORIGINAL_RSUBREAD2232_ON_R441_ONLY_NOT_PAPER_BINARY",file.path(out,"R-status.txt"))
