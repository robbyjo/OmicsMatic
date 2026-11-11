# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# All imported original code is immutable, trusted source; no user R code is evaluated.
rsubread_decode_path <- function(x) {
    stopifnot(grepl("^(?:[0-9a-f]{2})*$",x,perl=TRUE))
    if(!nchar(x))return("")
    start<-seq.int(1L,nchar(x),by=2L);value<-rawToChar(as.raw(strtoi(substring(x,start,start+1L),16L)));Encoding(value)<-"UTF-8";value
}
rsubread_runtime_guard <- function(config,reference,directory) {
    rows<-strsplit(readLines(config,warn=TRUE),"\t",fixed=TRUE)
    stopifnot(length(rows)==4L,all(lengths(rows)==2L))
    settings<-setNames(vapply(rows,function(x)rsubread_decode_path(x[2L]),""),vapply(rows,`[[`,"",1L))
    stopifnot(identical(names(settings),c("r_library","r_home","package_version","R_version")),
              .Platform$OS.type=="windows",R.version$arch=="x86_64",as.character(getRversion())==settings[["R_version"]],
              identical(normalizePath(R.home()),normalizePath(settings[["r_home"]])))
    .libPaths(c(settings[["r_library"]],file.path(settings[["r_home"]],"library")),include.site=FALSE)
    stopifnot(identical(.libPaths(),normalizePath(c(settings[["r_library"]],file.path(settings[["r_home"]],"library")),winslash="/")),
              as.character(utils::packageVersion("Rsubread",lib.loc=settings[["r_library"]]))==settings[["package_version"]])
    namespace<-loadNamespace("Rsubread",lib.loc=settings[["r_library"]]);source<-new.env(parent=baseenv())
    sys.source(file.path(reference,"R/cellCounts.R"),envir=source)
    sys.source(file.path(reference,"R/featureCounts.R"),envir=source)
    guarded<-ls(source,all.names=TRUE)
    for(name in guarded) {
        original<-get(name,envir=namespace,inherits=FALSE);expected<-get(name,envir=source,inherits=FALSE)
        if(is.function(expected))stopifnot(is.function(original),identical(body(original),body(expected)),identical(formals(original),formals(expected)))
        else stopifnot(identical(original,expected))
    }
    stopifnot(identical(get(".retrieve.tmp.parameters",envir=namespace,inherits=FALSE)(),NA))
    writeLines(guarded,file.path(directory,"guarded-original-symbols.txt"),useBytes=TRUE)
    dlls<-getLoadedDLLs()
    lines<-vapply(names(dlls),function(name)paste(name,paste(sprintf("%02x",as.integer(charToRaw(enc2utf8(dlls[[name]][["path"]])))),collapse=""),sep="\t"),"")
    writeLines(lines,file.path(directory,"loaded-dlls.hex.tsv"),useBytes=TRUE)
    writeLines(capture.output(sessionInfo()),file.path(directory,"sessionInfo.txt"))
    get("featureCounts",envir=namespace,inherits=FALSE)
}

if(sys.nframe()==0L) {
    args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==2L)
    tryCatch({rsubread_runtime_guard(args[1L],args[2L],getwd());writeLines("PASS_ORIGINAL_RSUBREAD_RUNTIME_GUARDS","worker-status.txt")},
        error=function(e){writeLines(c(class(e),conditionMessage(e)),"original-error.txt");writeLines("SKIP3_ORIGINAL_RSUBREAD_RUNTIME_UNAVAILABLE","worker-status.txt");quit(save="no",status=3L)})
}
