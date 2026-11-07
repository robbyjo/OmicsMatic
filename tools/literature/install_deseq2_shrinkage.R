# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
p <- commandArgs(TRUE);stopifnot(length(p)==7L,!dir.exists(p[[1]]),dir.create(p[[1]]))
.libPaths(c(p[[1]],p[3:7],.Library),include.site=FALSE)
options(repos=c(BioCsoft="https://bioconductor.posit.co/packages/3.23/bioc",CRAN="https://cloud.r-project.org"),timeout=180)
dir.create(file.path(p[[2]],"downloads"));state <- "SKIP3_OPTIONAL_SHRINKAGE_UNAVAILABLE";detail <- "";warnings_seen <- character()
tryCatch(withCallingHandlers({
    available <- available.packages(type="win.binary")
    write.table(available[c("apeglm","ashr"),,drop=FALSE],file.path(p[[2]],"repository-records.tsv"),sep="\t",quote=TRUE)
    install.packages(c("apeglm","ashr"),lib=p[[1]],dependencies=NA,type="win.binary",destdir=file.path(p[[2]],"downloads"))
    stopifnot(requireNamespace("DESeq2",quietly=TRUE),identical(as.character(packageVersion("DESeq2")),"1.52.0"),requireNamespace("apeglm",quietly=TRUE),requireNamespace("ashr",quietly=TRUE))
    state <- "INSTALLED_OPTIONAL_ORIGINAL_SHRINKAGE_REFERENCE_ONLY"
},warning=function(w){warnings_seen <<- c(warnings_seen,conditionMessage(w))}),error=function(e){detail <<- conditionMessage(e)})
ip <- installed.packages(lib.loc=.libPaths())
write.table(ip[,c("Package","Version","LibPath","Built"),drop=FALSE],file.path(p[[2]],"installed-packages.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
writeLines(capture.output(sessionInfo()),file.path(p[[2]],"session-info.txt"))
jsonlite::write_json(list(status=state,detail=detail,warnings=unname(warnings_seen),analysis_admitted=FALSE),file.path(p[[2]],"R-RESULT.json"),auto_unbox=TRUE,pretty=TRUE,null="null")
quit(status=if(state=="INSTALLED_OPTIONAL_ORIGINAL_SHRINKAGE_REFERENCE_ONLY")0L else 3L)
