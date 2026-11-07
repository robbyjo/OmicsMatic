# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Private original package preparation; no user selection analysis is admitted.
a<-commandArgs(TRUE);out<-a[1];source<-a[2];private<-a[3];.libPaths(c(private,a[-(1:3)],.Library),include.site=FALSE)
stopifnot(as.character(getRversion())=="4.6.1",R.version$platform=="x86_64-w64-mingw32")
zips<-list.files(file.path(out,"downloads"),pattern="[.]zip$",full.names=TRUE)
if(length(zips))for(path in zips)install.packages(path,repos=NULL,type="win.binary",lib=private)
install.packages(source,repos=NULL,type="source",lib=private,dependencies=FALSE)
suppressPackageStartupMessages(library(dndscv))
original<-new.env(parent=asNamespace("dndscv"));for(p in list.files(file.path(source,"R"),pattern="[.]R$",full.names=TRUE))sys.source(p,original,keep.source=FALSE)
guards<-Filter(function(n)is.function(get(n,original,inherits=FALSE)),ls(original,all.names=TRUE))
for(n in guards){x<-get(n,original,inherits=FALSE);y<-get(n,asNamespace("dndscv"),inherits=FALSE);stopifnot(identical(formals(x),formals(y)),identical(body(x),body(y)))}
packages<-c("dndscv","seqinr","MASS","GenomicRanges","Biostrings","IRanges","Rsamtools","poilog","plyr","rtracklayer","jsonlite")
versions<-setNames(lapply(packages,function(p){stopifnot(requireNamespace(p,quietly=TRUE));as.character(packageVersion(p))}),packages)
jsonlite::write_json(list(status="loaded-exact-source-dndscv-reference-only",R_version=as.character(getRversion()),platform=R.version$platform,versions=versions,guarded_functions=as.list(guards),source_bodies_and_formals_checked=TRUE,analysis_admitted=FALSE),file.path(out,"R-RESULT.json"),pretty=TRUE,auto_unbox=TRUE)
cat("Original source installed privately; all source function bodies/formals match. No user analysis admitted.\n")
