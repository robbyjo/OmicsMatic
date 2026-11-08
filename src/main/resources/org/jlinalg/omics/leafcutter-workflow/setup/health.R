# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args<-commandArgs(TRUE)
library(jsonlite)
.libPaths(c(args[1],.libPaths()))
library(leafcutter)
ns<-asNamespace("leafcutter")
stopifnot(normalizePath(getNamespaceInfo(ns,"path"))==normalizePath(file.path(args[1],"leafcutter")))
checked<-character()
for(path in list.files(file.path(args[2],"R"),pattern="\\.R$",full.names=TRUE))for(expr in parse(path,keep.source=FALSE)) {
  if(is.call(expr)&&length(expr)>=3&&(identical(expr[[1]],as.name("<-"))||identical(expr[[1]],as.name("=")))&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function"))) {
    name<-as.character(expr[[2]]);expected<-eval(expr[[3]],envir=ns);actual<-get(name,envir=ns,inherits=FALSE)
    stopifnot(identical(paste(deparse(formals(expected)),collapse="\n"),paste(deparse(formals(actual)),collapse="\n")),identical(paste(deparse(body(expected)),collapse="\n"),paste(deparse(body(actual)),collapse="\n")))
    checked<-c(checked,name)
  }
}
stopifnot(length(checked)==24,length(get("stanmodels",ns))==5)
probe<-c(pi,1e-300,1e300,1+.Machine$double.eps)
stopifnot(identical(probe,fromJSON(toJSON(probe,digits=I(17)))))
write_json(list(status="PASS_ORIGINAL_LEAFCUTTER_NAMESPACE_AND_LOSSLESS_EXPORT_ONLY",R=R.version.string,version=as.character(packageVersion("leafcutter")),rstan=as.character(packageVersion("rstan")),jsonlite=as.character(packageVersion("jsonlite")),package_path=getNamespaceInfo(ns,"path"),namespace_functions_checked=checked,lossless_roundtrip=TRUE,namespace_patched=FALSE,analysis_executed=FALSE),args[3],auto_unbox=TRUE,pretty=TRUE,digits=I(17))
