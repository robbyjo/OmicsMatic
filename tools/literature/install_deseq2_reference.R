# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE)
stopifnot(length(args) == 7L)
lib <- args[[1]]; report <- args[[2]]; binary <- args[[3]]
stopifnot(!dir.exists(lib), dir.create(lib))
.libPaths(c(lib,args[4:7],.Library),include.site=FALSE)
options(repos=c(CRAN="https://cloud.r-project.org"),timeout=120)
warnings_seen <- character(); detail <- ""; status <- "SKIP3_DESEQ2_REFERENCE_UNAVAILABLE"
tryCatch(withCallingHandlers({
    # Only the missing dependency is installed. Existing scientific libraries are read-only.
    if (!requireNamespace("locfit",quietly=TRUE)) {
        dir.create(file.path(report,"downloads"))
        available <- available.packages(type="win.binary")
        write.table(available["locfit",,drop=FALSE],file.path(report,"locfit-repository-record.tsv"),sep="\t",quote=TRUE)
        install.packages("locfit",lib=lib,dependencies=FALSE,type="win.binary",destdir=file.path(report,"downloads"))
    }
    install.packages(binary,lib=lib,repos=NULL,type="win.binary")
    stopifnot(requireNamespace("DESeq2",quietly=TRUE),identical(as.character(packageVersion("DESeq2")),"1.52.0"))
    # Exercise the loaded function and native negative-binomial code, not just DESCRIPTION lookup.
    sf <- DESeq2::estimateSizeFactorsForMatrix(matrix(c(1L,2L,3L,4L,2L,4L,6L,8L),ncol=2L))
    stopifnot(length(sf)==2L,all(is.finite(sf)),abs(sf[[2]]/sf[[1]]-2)<1e-12)
    set.seed(8024L)
    dds <- DESeq2::makeExampleDESeqDataSet(n=100L,m=6L)
    dds <- DESeq2::DESeq(dds,fitType="mean",quiet=TRUE,parallel=FALSE)
    stopifnot(nrow(DESeq2::results(dds))==100L)
    status <- "INSTALLED_ORIGINAL_DESEQ2_REFERENCE_ONLY"
},warning=function(w){warnings_seen <<- c(warnings_seen,conditionMessage(w))}),
error=function(e){detail <<- conditionMessage(e)})
ip <- installed.packages(lib.loc=.libPaths())
write.table(ip[,c("Package","Version","LibPath","Built"),drop=FALSE],file.path(report,"installed-packages.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
writeLines(capture.output(sessionInfo()),file.path(report,"session-info.txt"))
if (requireNamespace("DESeq2",quietly=TRUE))
    dput(DESeq2::estimateSizeFactorsForMatrix,file=file.path(report,"loaded-size-factor-function.R"))
jsonlite::write_json(list(status=status,detail=detail,warnings=unname(warnings_seen),
                         r_version=R.version.string,private_library=lib,
                         exact_DESeq2="1.52.0",analysis_admitted=FALSE),
                     file.path(report,"R-RESULT.json"),auto_unbox=TRUE,pretty=TRUE,null="null")
quit(status=if(status=="INSTALLED_ORIGINAL_DESEQ2_REFERENCE_ONLY")0L else 3L)
