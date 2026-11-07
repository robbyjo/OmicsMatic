# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Private original-package reference setup. No user analysis is performed.
args <- commandArgs(trailingOnly=TRUE)
if (length(args)!=4L || !args[[4]] %in% c("allow","forbid")) stop("Usage: install_signac_reference.R NEW_LIBRARY NEW_REPORT READ_ONLY_BASE_LIBRARY allow|forbid")
lib <- normalizePath(args[[1]],winslash="/",mustWork=FALSE)
report <- normalizePath(args[[2]],winslash="/",mustWork=FALSE)
base_lib <- normalizePath(args[[3]],winslash="/",mustWork=TRUE)
if (dir.exists(lib) || dir.exists(report)) stop("Preserve existing paths; choose NEW private library and report")
if (!dir.create(report,recursive=FALSE)) stop("Required report path unavailable; skip dependent analysis and use a permitted path")
writeLines("INCOMPLETE_SIGNAC_REFERENCE_SETUP",file.path(report,"INCOMPLETE.txt"))
state <- "SKIP3_SIGNAC_REFERENCE_RUNTIME_UNAVAILABLE";detail <- "";warnings_seen <- character()
action <- "Skip dependent Signac runtime analysis while requirements are unavailable. Read installation.log, warnings and package versions. Where permitted provide exact Signac1.17.1 and required packages in a NEW private library with working native DLLs; never modify the frozen base library or substitute editions silently. Setup alone does not admit a scientific/user workflow. Preserve all attempts and use NEW paths after correction."
options(repos=c(BioCsoft="https://bioconductor.org/packages/3.23/bioc",BioCann="https://bioconductor.org/packages/3.23/data/annotation",CRAN="https://cloud.r-project.org"),timeout=300)
sink(file.path(report,"installation.log"),split=TRUE)
tryCatch(withCallingHandlers({
    if (!identical(args[[4]],"allow")) stop("Installation/download not permitted; analysis skipped")
    if (!dir.create(lib,recursive=FALSE)) stop("Required private install directory is unavailable or forbidden")
    .libPaths(c(lib,base_lib,.Library))
    type <- if (.Platform$OS.type=="windows") "binary" else "source"
    available <- available.packages(type=type)
    write.table(available,file.path(report,"available-packages.tsv"),sep="\t",quote=TRUE,row.names=TRUE)
    if (!"Signac" %in% rownames(available) || !identical(unname(available["Signac","Version"]),"1.17.1")) stop("Exact Signac1.17.1 is unavailable from the selected repositories; no edition was substituted")
    downloads <- file.path(report,"downloads");dir.create(downloads)
    install.packages("Signac",lib=lib,dependencies=NA,type=type,destdir=downloads)
    if (!requireNamespace("Signac",quietly=TRUE) || !identical(as.character(packageVersion("Signac")),"1.17.1")) stop("Exact original Signac failed to load; inspect retained package/DLL dependency diagnostics")
    state <- "INSTALLED_ORIGINAL_SIGNAC_REFERENCE_ONLY_NOT_ANALYSIS_ADMISSION"
},warning=function(w){warnings_seen <<- c(warnings_seen,conditionMessage(w))}),error=function(e){detail <<- conditionMessage(e)})
ip <- installed.packages(lib.loc=.libPaths())
write.table(ip[,c("Package","Version","LibPath","Built"),drop=FALSE],file.path(report,"installed-packages.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
writeLines(capture.output(sessionInfo()),file.path(report,"session-info.txt"))
text <- c(if(startsWith(state,"SKIP3")) "# SKIP3: SIGNAC RUNTIME ANALYSIS SKIPPED" else "# Signac reference package installed; no user workflow admitted","",detail,"",paste("Required action:",action),"","## Retained installation warnings","",unique(warnings_seen))
writeLines(text,file.path(report,"WARNINGS.md"))
if (requireNamespace("jsonlite",quietly=TRUE)) {
    result <- list(status=state,detail=detail,r_version=R.version.string,private_library=lib,read_only_base_library=base_lib,repositories=getOption("repos"),installation_authorized=identical(args[[4]],"allow"),warnings=unname(warnings_seen),scientific_runtime_admitted=FALSE,user_analysis_admitted=FALSE,required_action=action)
    jsonlite::write_json(result,file.path(report,"RESULT.json"),auto_unbox=TRUE,pretty=TRUE,null="null")
}
if (!startsWith(state,"SKIP3")) {writeLines(state,file.path(report,"COMPLETED.txt"));unlink(file.path(report,"INCOMPLETE.txt"))}
cat("STATUS:",state,"\nDETAIL:",detail,"\n");sink();quit(status=if(startsWith(state,"SKIP3"))3L else 0L)
