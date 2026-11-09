# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Build the unmodified reported-version source in a NEW private library.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==4L,getRversion()=="4.6.1")
lib<-args[[1L]];out<-args[[2L]];archive<-args[[3L]];source<-args[[4L]]
stopifnot(!dir.exists(lib),file.exists(archive),file.exists(source));dir.create(lib);.libPaths(c(lib,.Library))
writeLines(capture.output(Sys.which(c("make","gcc","g++","sh"))),file.path(out,"R-build-tools.txt"))
stopifnot(nzchar(Sys.which("make")),nzchar(Sys.which("gcc")),nzchar(Sys.which("sh")))
utils::install.packages(archive,lib=lib,repos=NULL,type="source",INSTALL_opts="--no-multiarch")
ns<-loadNamespace("Rsubread",lib.loc=lib);stopifnot(as.character(utils::packageVersion("Rsubread",lib.loc=lib))=="2.23.2")
reference<-new.env(parent=baseenv());sys.source(source,envir=reference)
actual<-get("featureCounts",envir=ns,inherits=FALSE);expected<-get("featureCounts",envir=reference,inherits=FALSE)
stopifnot(identical(formals(actual),formals(expected)),identical(body(actual),body(expected)))
saveRDS(formals(actual),file.path(out,"featureCounts-formals.rds"),version=3L);saveRDS(body(actual),file.path(out,"featureCounts-body.rds"),version=3L)
writeLines(capture.output(sessionInfo()),file.path(out,"sessionInfo.txt"));dlls<-getLoadedDLLs();write.table(data.frame(name=names(dlls),path=vapply(dlls,function(x)x[["path"]],"")),file=file.path(out,"loaded-dlls.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
writeLines("BUILT_ORIGINAL_RSUBREAD2232_REFERENCE_ONLY_NOT_PAPER_R441",file.path(out,"R-status.txt"))
