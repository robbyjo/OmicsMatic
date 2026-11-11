# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent calls to installed original annotatePeak; no project worker is sourced.
args <- commandArgs(TRUE);stopifnot(length(args)==3L)
.libPaths(c(strsplit(args[[3]],.Platform$path.sep,fixed=TRUE)[[1]],.Library),include.site=FALSE)
plan <- jsonlite::fromJSON(args[[1]],simplifyVector=FALSE);out <- args[[2]]
stopifnot(requireNamespace("ChIPseeker",quietly=TRUE),as.character(getNamespaceVersion("ChIPseeker"))==plan$version)
ns <- asNamespace("ChIPseeker");definitions <- list()
for(file in list.files(plan$source_R,pattern="[.]R$",full.names=TRUE))for(expression in parse(file,keep.source=FALSE)){
    if(is.call(expression)&&length(expression)==3L&&identical(expression[[1]],as.name("<-"))&&
       is.symbol(expression[[2]])&&is.call(expression[[3]])&&identical(expression[[3]][[1]],as.name("function")))
        definitions[[as.character(expression[[2]])]] <- expression[[3]]
}
guards <- c("annotatePeak","getNearestFeatureIndicesAndDistances","getGenomicAnnotation","getGenomicAnnotation.internal",
    "updateGenomicAnnotation","getGene",".ChIPseekerEnv","get_exonList","get_intronList","TXID2EG","TXID2EGID",
    "getFirstHitIndex","getAllFlankingGene","getGenomicAnnoStat","loadTxDb","as.GRanges","as.data.frame.csAnno")
for(name in guards){original <- eval(definitions[[name]],ns);loaded <- get(name,ns,inherits=FALSE)
    stopifnot(identical(body(original),body(loaded)),identical(formals(original),formals(loaded)))}
db <- AnnotationDbi::loadDb(plan$txdb);results <- list()
for(case in plan$cases){
    folder <- file.path(out,case$id);dir.create(folder)
    peaks <- GenomicRanges::GRanges(seqnames=vapply(case$peaks,`[[`,character(1),"contig"),
        ranges=IRanges::IRanges(start=vapply(case$peaks,`[[`,integer(1),"start"),end=vapply(case$peaks,`[[`,integer(1),"end")),
        strand=vapply(case$peaks,`[[`,character(1),"strand"))
    S4Vectors::mcols(peaks)$input_ordinal <- seq_along(case$peaks)
    S4Vectors::mcols(peaks)$peak_id <- vapply(case$peaks,`[[`,character(1),"peak_id")
    warnings <- character();failure <- NULL
    value <- tryCatch(withCallingHandlers(ChIPseeker::annotatePeak(peaks,TxDb=db,level="transcript",
        sameStrand=case$same_strand,genomicAnnotationPriority=as.character(unlist(case$priority)),verbose=FALSE),
        warning=function(w){warnings <<- c(warnings,conditionMessage(w));invokeRestart("muffleWarning")}),
        error=function(e){failure <<- list(class=class(e),message=conditionMessage(e));NULL})
    if(!is.null(value)){
        saveRDS(value,file.path(folder,"original-csAnno.rds"))
        jsonlite::write_json(list(annotations=as.data.frame(value),detail=value@detailGenomicAnnotation,distribution=value@annoStat,
            tss_region=value@tssRegion,level=value@level,peak_num=value@peakNum),file.path(folder,"original-output.json"),
            auto_unbox=FALSE,dataframe="rows",pretty=TRUE,na="string",null="null",digits=NA)
    }
    results[[length(results)+1L]] <- list(id=case$id,source_error=failure,warnings=as.list(warnings))
}
jsonlite::write_json(list(version=plan$version,source_guards=as.list(guards),cases=results),file.path(out,"ORIGINAL.json"),auto_unbox=TRUE,pretty=TRUE,null="null")
writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"))
