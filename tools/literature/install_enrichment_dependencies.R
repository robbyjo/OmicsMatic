# Copyright (C) 2026 JLinAlg contributors
# SPDX-License-Identifier: GPL-3.0-or-later
# Private original enrichment engines; installation is not scientific validation.
args <- commandArgs(trailingOnly=TRUE)
if(length(args)!=3L || !args[[3]] %in% c("allow","forbid")) stop("Usage: Rscript --vanilla install_enrichment_dependencies.R LIBRARY REPORT allow|forbid")
lib <- normalizePath(args[[1]],winslash="/",mustWork=FALSE)
report <- normalizePath(args[[2]],winslash="/",mustWork=FALSE)
allowed <- identical(args[[3]],"allow")
pins <- c(DOSE="4.6.0",clusterProfiler="4.20.3",fgsea="1.38.0",enrichit="0.2.6",jsonlite="2.0.0")
repos <- c(BioCsoft="https://bioconductor.posit.co/packages/3.23/bioc",BioCann="https://bioconductor.posit.co/packages/3.23/data/annotation",BioCexp="https://bioconductor.posit.co/packages/3.23/data/experiment",CRAN="https://cloud.r-project.org")
options(repos=repos,timeout=180)
.Library.site <- character()
.libPaths(c(if(dir.exists(lib))lib,.Library))
dir.create(report,recursive=TRUE,showWarnings=FALSE)
attempt <- file.path(report,paste0(gsub("[.]","",format(Sys.time(),"%Y%m%dT%H%M%OS6")),"-",Sys.getpid()))
if(dir.exists(attempt))stop("Attempt exists; preserve it")
dir.create(attempt)
marker <- file.path(lib,".jlinalg-enrichment-library")
seal <- file.path(lib,".jlinalg-enrichment-runtime.json")
state <- "skipped-dependency-unavailable";detail <- "";warnings <- character();downloads <- data.frame();plan <- NULL
sha <- function(path)digest::digest(file=path,algo="sha256",serialize=FALSE)
inventory <- function(home,saved,excluded) {
  names <- names(saved$files)
  if(!length(names)||anyNA(names)||any(!nzchar(names))||anyDuplicated(names)||any(grepl("(^/|:|\\\\|(^|/)\\.\\.(/|$))",names)))stop("Managed runtime seal contains invalid relative paths")
  observed <- list.files(home,recursive=TRUE,all.files=TRUE,no..=TRUE,include.dirs=FALSE)
  observed <- setdiff(gsub("\\\\","/",observed),excluded)
  if(!setequal(observed,names))stop("Managed runtime inventory has unsealed or missing files; preserve it and select a new LIBRARY")
  for(name in names)if(!grepl("^[0-9a-f]{64}$",saved$files[[name]])||!identical(sha(file.path(home,name)),saved$files[[name]]))stop("Managed runtime file changed: ",name,"; preserve it and select a new LIBRARY")
}
matching <- function()all(vapply(names(pins),function(p)identical(as.character(utils::packageVersion(p,lib.loc=lib)),unname(pins[p])),logical(1)))
action <- paste("Skip dependent enrichment analysis until the exact private runtime is available. Where permitted run Rscript --vanilla tools/literature/install_enrichment_dependencies.R",lib,report,"allow . Supply the resulting library and Rscript to the typed adapter. Preserve unmanaged,changed and incomplete libraries; select a NEW LIBRARY after correcting the named cause. Never substitute DOSE4.5.0 for the4.6.0 source pin. No installation/version probe establishes analysis validity.")
sink(file.path(attempt,"installation.log"),split=TRUE)
tryCatch(withCallingHandlers({
  if(dir.exists(lib)) {
    if(!file.exists(marker))stop("Existing unmanaged library is preserved; choose a new LIBRARY")
    if(!file.exists(seal))stop("Existing incomplete library is preserved; inspect its attempt and select a new LIBRARY")
    if(!requireNamespace("jsonlite",quietly=TRUE)||!requireNamespace("digest",quietly=TRUE))stop("Managed library lacks seal readers; preserve it and select a new LIBRARY")
    saved <- jsonlite::fromJSON(seal,simplifyVector=TRUE)
    if(!identical(saved$schema,"jlinalg-enrichment-runtime-v1")||!matching())stop("Managed library version/seal differs; preserve it and select a new LIBRARY")
    if(!identical(as.character(getRversion()),"4.6.1")||.Platform$OS.type!="windows"||R.version$arch!="x86_64")stop("This managed profile requires Windows x86_64 R4.6.1")
    inventory(lib,saved,basename(seal))
    if(!all(vapply(names(pins),requireNamespace,logical(1),quietly=TRUE)))stop("Managed pinned engines cannot load; inspect original DLL/package errors")
    state <- "available-not-yet-analysis-validated"
  } else if(!allowed) {
    state <- "skipped-install-not-permitted";detail <- "Required private enrichment runtime is absent and installation is forbidden."
  } else {
    if(.Platform$OS.type!="windows"||R.version$arch!="x86_64"||!identical(as.character(getRversion()),"4.6.1"))stop("This managed profile requires Windows x86_64 R4.6.1; use a permitted supported profile instead")
    binary <- available.packages(type="binary");source <- available.packages(type="source")
    write.table(binary,file.path(attempt,"available-binary.tsv"),sep="\t",quote=TRUE,row.names=TRUE)
    write.table(source,file.path(attempt,"available-source.tsv"),sep="\t",quote=TRUE,row.names=TRUE)
    for(p in names(pins))if(!p %in% rownames(source)||!identical(unname(source[p,"Version"]),unname(pins[p])))stop("Pinned source ",p," ",pins[p]," is unavailable; no substitution was made")
    base <- rownames(installed.packages(lib.loc=.Library))
    direct <- tools::package_dependencies(rownames(source),db=source,which=c("Depends","Imports","LinkingTo"),recursive=FALSE)
    visited <- character();ordered <- character();visiting <- character()
    visit <- function(p) {
      if(p %in% c(base,visited))return(invisible(NULL))
      if(p %in% visiting)stop("Required package dependency cycle: ",p)
      if(!p %in% rownames(source))stop("Required dependency lacks repository metadata: ",p)
      visiting <<- c(visiting,p)
      for(q in direct[[p]])if(q!="R")visit(q)
      visiting <<- setdiff(visiting,p);visited <<- c(visited,p);ordered <<- c(ordered,p)
    }
    for(p in names(pins))visit(p)
    types <- vapply(ordered,function(p) {
      if(p %in% rownames(binary)&&identical(unname(binary[p,"Version"]),unname(source[p,"Version"])))"binary"
      else if(is.na(source[p,"NeedsCompilation"])||source[p,"NeedsCompilation"]!="yes")"source"
      else if(p %in% rownames(binary)&&!p %in% names(pins))"binary"
      else stop("Required compiled source has no matching Windows binary: ",p,"; supply a separately permitted build/profile and inspect its compiler requirements")
    },character(1))
    plan <- data.frame(package=ordered,type=types,version=vapply(ordered,function(p)unname(if(types[[p]]=="binary")binary[p,"Version"] else source[p,"Version"]),character(1)),stringsAsFactors=FALSE)
    if(any(plan$package %in% names(pins)&plan$version!=pins[plan$package]))stop("Pinned package plan does not match exact version")
    write.table(plan,file.path(attempt,"package-plan.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
    dir.create(lib,recursive=TRUE);writeLines("JLinAlg managed enrichment runtime v1; private only",marker);.libPaths(c(lib,.Library))
    dest <- file.path(attempt,"downloads");dir.create(dest)
    for(type in c("binary","source")) {
      pkgs <- ordered[types==type]
      if(length(pkgs)) {
        db <- if(type=="binary")binary else source
        got <- download.packages(pkgs,destdir=dest,available=db,type=type)
        if(!setequal(got[,1],pkgs))stop("Some required package archives were not downloaded; preserve this attempt")
        for(i in seq_len(nrow(got))) {
          expected <- unname(db[got[i,1],"MD5sum"])
          if(!is.na(expected)&&nzchar(expected)&&!identical(unname(tools::md5sum(got[i,2])),expected))stop("Repository archive MD5 differs for ",got[i,1])
        }
        if(type=="binary")install.packages(got[,2],repos=NULL,lib=lib,type="win.binary")
        else for(p in pkgs)install.packages(got[match(p,got[,1]),2],repos=NULL,lib=lib,type="source")
        downloads <- rbind(downloads,data.frame(package=got[,1],archive=got[,2],type=type,stringsAsFactors=FALSE))
      }
    }
    if(!matching())stop("Exact pinned versions were not installed; inspect retained package logs")
    if(!all(vapply(names(pins),requireNamespace,logical(1),quietly=TRUE)))stop("Pinned engines cannot load; inspect retained original package/DLL diagnostics")
    if(!requireNamespace("digest",quietly=TRUE))stop("Runtime integrity reader digest is unavailable")
    files <- list.files(lib,recursive=TRUE,all.files=TRUE,no..=TRUE,include.dirs=FALSE)
    hashes <- setNames(lapply(files,function(p)sha(file.path(lib,p))),gsub("\\\\","/",files))
    installed <- installed.packages(lib.loc=lib)
    downloads$sha256 <- vapply(downloads$archive,sha,character(1))
    write.table(downloads,file.path(attempt,"download-sha256.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
    saved <- list(schema="jlinalg-enrichment-runtime-v1",pins=as.list(pins),r_version=R.version.string,r_platform=R.version$platform,package_versions=as.list(setNames(installed[,"Version"],installed[,"Package"])),files=hashes,archives=downloads,installation_attempt=attempt,scope="Original private engines only; no analysis validated,annotation download,LLM call or global installation. DOSE4.6 source deliberately differs from4.5 Windows binary;clusterProfiler current GSEA uses enrichit,not fgsea.")
    jsonlite::write_json(saved,paste0(seal,".tmp"),auto_unbox=TRUE,pretty=TRUE)
    if(!file.rename(paste0(seal,".tmp"),seal))stop("Cannot finalize runtime seal; preserve incomplete library")
    state <- "installed-not-yet-analysis-validated"
  }
},warning=function(w){warnings <<- c(warnings,conditionMessage(w))}),error=function(e){detail <<- conditionMessage(e)})
sink()
status <- c(paste("status:",state),paste("library:",encodeString(lib,quote='"')),paste("attempt:",encodeString(attempt,quote='"')),paste("install_authorized:",tolower(allowed)),paste("detail:",encodeString(detail,quote='"')),paste("r_version:",encodeString(R.version.string,quote='"')),paste("required_action:",encodeString(action,quote='"')))
writeLines(status,file.path(attempt,"dependency-status.yaml"));writeLines(status,file.path(report,"dependency-status.yaml"))
writeLines(c(if(startsWith(state,"skipped"))"# ANALYSIS SKIPPED - ENRICHMENT RUNTIME UNAVAILABLE" else "# Enrichment runtime installed; scientific validation is separate","",detail,"",paste("Required action:",action),"","## Retained installation warnings",unique(warnings)),file.path(report,"WARNINGS.md"))
cat(paste(status,collapse="\n"),"\n")
quit(status=if(startsWith(state,"skipped"))3L else 0L)
