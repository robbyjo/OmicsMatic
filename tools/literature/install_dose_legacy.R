# Copyright (C) 2026 JLinAlg contributors
# SPDX-License-Identifier: GPL-3.0-or-later
# Explicit original legacy DOSE generic pathway API, never a silent downgrade.
args <- commandArgs(trailingOnly=TRUE)
if(!length(args)%in%c(4L,5L)||!args[[4]]%in%c("allow","forbid"))stop("Usage: Rscript --vanilla install_dose_legacy.R LIBRARY DEPENDENCY_LIBRARY REPORT allow|forbid [PINNED_ARCHIVE]")
lib <- normalizePath(args[[1]],winslash="/",mustWork=FALSE);deps <- normalizePath(args[[2]],winslash="/",mustWork=FALSE)
report <- normalizePath(args[[3]],winslash="/",mustWork=FALSE);allowed <- args[[4]]=="allow"
dir.create(report,recursive=TRUE,showWarnings=FALSE)
attempt <- file.path(report,paste0(gsub("[.]","",format(Sys.time(),"%Y%m%dT%H%M%OS6")),"-",Sys.getpid()));if(dir.exists(attempt))stop("Attempt exists; preserve it");dir.create(attempt)
.Library.site <- character()
.libPaths(c(if(dir.exists(lib))lib,deps,.Library))
source <- "https://bioconductor.posit.co/packages/3.23/bioc/bin/windows/contrib/4.6/DOSE_4.5.0.zip"
expected <- "568d11f99c113605fd63ba8b3783191ee2f62ea585a7e41ac4d3022013d12ca0"
marker <- file.path(lib,".jlinalg-dose-legacy-runtime.json");ownership <- file.path(lib,".jlinalg-dose-legacy-library")
state <- "skipped-dependency-unavailable";detail <- "";warnings <- character()
sha <- function(p)digest::digest(file=p,algo="sha256",serialize=FALSE)
inventory <- function(home,saved,excluded) {
  names <- names(saved$files)
  if(!length(names)||anyNA(names)||any(!nzchar(names))||anyDuplicated(names)||any(grepl("(^/|:|\\\\|(^|/)\\.\\.(/|$))",names)))stop("Managed runtime seal contains invalid relative paths")
  observed <- setdiff(gsub("\\\\","/",list.files(home,recursive=TRUE,all.files=TRUE,no..=TRUE,include.dirs=FALSE)),excluded)
  if(!setequal(observed,names))stop("Managed runtime inventory has unsealed or missing files; preserve it and select a new LIBRARY")
  for(n in names)if(!grepl("^[0-9a-f]{64}$",saved$files[[n]])||!identical(sha(file.path(home,n)),saved$files[[n]]))stop("Managed runtime file changed: ",n,"; preserve it and select a new LIBRARY")
}
action <- paste("Skip legacy DOSE analysis until a permitted original4.5.0 profile is supplied. First install the main enrichment runtime,then where permitted run Rscript --vanilla tools/literature/install_dose_legacy.R",lib,deps,report,"allow . Preserve existing,changed and incomplete libraries and select a NEW LIBRARY after correcting the cause. The separate4.6 current profile does not provide these legacy generic interfaces; no historical3.24.2 equivalence or scientific validity follows from installation.")
sink(file.path(attempt,"installation.log"),split=TRUE)
tryCatch(withCallingHandlers({
  if(!dir.exists(lib)&&!allowed){state<-"skipped-install-not-permitted";detail<-"Legacy runtime is absent and installation is forbidden."}
  else {
    if(.Platform$OS.type!="windows"||R.version$arch!="x86_64"||!identical(as.character(getRversion()),"4.6.1"))stop("This legacy profile requires Windows x86_64 R4.6.1")
    if(!requireNamespace("jsonlite",quietly=TRUE)||!requireNamespace("digest",quietly=TRUE))stop("Main managed dependency library is unavailable; install it separately where permitted")
    depMarker <- file.path(deps,".jlinalg-enrichment-runtime.json");dependency <- jsonlite::fromJSON(depMarker,simplifyVector=TRUE)
    if(!identical(dependency$schema,"jlinalg-enrichment-runtime-v1"))stop("Main dependency marker has the wrong identity")
    inventory(deps,dependency,basename(depMarker))
    depHash <- sha(depMarker)
    if(dir.exists(lib)) {
      if(!file.exists(ownership)||!file.exists(marker))stop("Existing unmanaged or incomplete legacy library is preserved; choose a new LIBRARY")
      saved <- jsonlite::fromJSON(marker,simplifyVector=TRUE)
      if(!identical(saved$schema,"jlinalg-dose-legacy-v1")||!identical(saved$version,"4.5.0")||saved$dependency_marker_sha256!=depHash)stop("Legacy profile/dependencies changed; preserve it and choose a new LIBRARY")
      inventory(lib,saved,basename(marker))
      state <- "available-not-yet-analysis-validated"
    } else {
      if(.Platform$OS.type!="windows"||R.version$arch!="x86_64"||!startsWith(R.version$minor,"6."))stop("This legacy binary profile requires Windows x86_64 R4.6")
      archive <- if(length(args)==5L)normalizePath(args[[5]],winslash="/",mustWork=TRUE) else file.path(attempt,"DOSE_4.5.0.zip")
      if(length(args)==4L)download.file(source,archive,mode="wb",quiet=FALSE)
      if(!identical(sha(archive),expected))stop("Original DOSE4.5 archive SHA256 differs; no software was installed")
      dir.create(lib,recursive=TRUE);writeLines("JLinAlg explicit legacy DOSE4.5 profile; private only",ownership)
      install.packages(archive,lib=lib,repos=NULL,type="win.binary")
      .libPaths(c(lib,deps,.Library))
      if(!identical(as.character(packageVersion("DOSE",lib.loc=lib)),"4.5.0"))stop("Exact legacy DOSE version was not installed")
      if(!requireNamespace("DOSE",quietly=TRUE))stop("Original legacy DOSE cannot load; inspect retained package/DLL diagnostics")
      for(n in c("GSEA_internal","enricher_internal","build_Anno"))if(!is.function(getFromNamespace(n,"DOSE")))stop("Original legacy API is absent: ",n)
      files <- list.files(lib,recursive=TRUE,all.files=TRUE,no..=TRUE,include.dirs=FALSE)
      saved <- list(schema="jlinalg-dose-legacy-v1",version="4.5.0",archive_sha256=expected,source=source,dependency_marker_sha256=depHash,dependency_library=deps,files=setNames(lapply(files,function(n)sha(file.path(lib,n))),gsub("\\\\","/",files)),scope="Original unmodified legacy generic pathway APIs only; main DOSE4.6 profile unchanged. Installation/version/API probes are not scientific validation or historical3.24.2 equivalence.")
      jsonlite::write_json(saved,paste0(marker,".tmp"),auto_unbox=TRUE,pretty=TRUE)
      if(!file.rename(paste0(marker,".tmp"),marker))stop("Cannot finalize legacy runtime marker")
      state <- "installed-not-yet-analysis-validated"
    }
  }
},warning=function(w){warnings<<-c(warnings,conditionMessage(w))}),error=function(e){detail<<-conditionMessage(e)})
sink()
status<-c(paste("status:",state),paste("library:",encodeString(lib,quote='"')),paste("dependency_library:",encodeString(deps,quote='"')),paste("attempt:",encodeString(attempt,quote='"')),paste("detail:",encodeString(detail,quote='"')),paste("required_action:",encodeString(action,quote='"')))
writeLines(status,file.path(attempt,"dependency-status.yaml"));writeLines(status,file.path(report,"dependency-status.yaml"))
writeLines(c(if(startsWith(state,"skipped"))"# LEGACY DOSE ANALYSIS SKIPPED"else"# Legacy DOSE installed; science remains unvalidated","",detail,"",paste("Required action:",action),"",unique(warnings)),file.path(report,"WARNINGS.md"))
cat(paste(status,collapse="\n"),"\n");quit(status=if(startsWith(state,"skipped"))3L else 0L)
