# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args<-commandArgs(TRUE);out<-args[[1]];profile_path<-args[[2]];.libPaths(args[-c(1,2)])
stopifnot(requireNamespace("jsonlite",quietly=TRUE))
p<-jsonlite::fromJSON(profile_path,simplifyVector=FALSE)
stopifnot(as.character(getRversion())==p$r_version)
for(name in names(p$package_versions))stopifnot(requireNamespace(name,quietly=TRUE),as.character(packageVersion(name))==p$package_versions[[name]])
dir.create(file.path(out,"readiness-functions"));ns<-asNamespace("batchelor")
canonical<-function(f)paste(c(deparse(formals(f),width.cutoff=500L),deparse(body(f),width.cutoff=500L)),collapse="\n")
for(i in seq_along(p$functions)) {
 name<-names(p$functions)[[i]];f<-get(name,ns,inherits=FALSE);stopifnot(is.function(f))
 path<-file.path(out,"readiness-functions",paste0(i,".txt"));con<-file(path,"wb");writeChar(enc2utf8(canonical(f)),con,eos=NULL,useBytes=TRUE);close(con)
 result<-system2("/usr/bin/sha256sum",shQuote(path),stdout=TRUE);stopifnot(is.null(attr(result,"status")),strsplit(result," ",fixed=TRUE)[[1]][1]==p$functions[[name]])
}
value<-list(status="READY_PRIVATE_ORIGINAL_FASTMNN129_NOT_ANALYSIS",R_version=as.character(getRversion()),batchelor_version=as.character(packageVersion("batchelor")),source_functions_checked=length(p$functions),namespaces_checked=length(p$package_versions),libraries=as.list(.libPaths()),analysis_admitted=FALSE,paper_patch_recovered=FALSE,whole_goal_complete=FALSE)
jsonlite::write_json(value,file.path(out,"readiness.json"),auto_unbox=TRUE,pretty=TRUE,null="null",digits=NA)
capture.output(sessionInfo(),file=file.path(out,"readiness-sessionInfo.txt"));cat(value$status,"\n")
