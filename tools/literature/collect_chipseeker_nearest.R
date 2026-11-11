# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original component references only. Source files are sourced unchanged; no package is installed.
args <- commandArgs(trailingOnly=TRUE)
if (length(args)!=3L) stop("Usage: collect_chipseeker_nearest.R PLAN_JSON NEW_OUTPUT_JSON LIBRARY_PATHS")
.libPaths(c(strsplit(args[[3]],.Platform$path.sep,fixed=TRUE)[[1]],.Library))
plan <- jsonlite::fromJSON(args[[1]],simplifyVector=FALSE)
.libPaths(c(unlist(plan$libraries),.Library))
suppressPackageStartupMessages(library(GenomicRanges))
versions <- vapply(c("GenomicRanges","IRanges","S4Vectors","GenomeInfoDb","BiocGenerics","jsonlite"),function(p) as.character(packageVersion(p)),character(1))
make_ranges <- function(rows) {
    if (length(rows)==0L) return(GRanges())
    value <- GRanges(seqnames=vapply(rows,`[[`,character(1),"contig"),
        ranges=IRanges(start=vapply(rows,`[[`,integer(1),"start"),end=vapply(rows,`[[`,integer(1),"end")),
        strand=vapply(rows,`[[`,character(1),"strand"))
    mcols(value)$input_ordinal <- seq_along(rows)
    value
}
tokens <- function(value) vapply(value,function(x) {
    if (is.na(x)) {if (is.nan(x)) "NaN" else "NA"} else if (is.infinite(x)) {if (x>0) "Inf" else "-Inf"} else format(x,scientific=FALSE,trim=TRUE,digits=17)
},character(1))
rows <- list()
for (edition in plan$editions) {
    original <- new.env(parent=globalenv())
    sys.source(edition$utilities,envir=original)
    sys.source(edition$nearest,envir=original)
    for (case in plan$cases) {
        warnings <- character();failure <- NULL
        result <- tryCatch(withCallingHandlers({
            p <- make_ranges(case$peaks); f <- make_ranges(case$features)
            original$getNearestFeatureIndicesAndDistances(p,f,
                sameStrand=case$options$same_strand,ignoreOverlap=case$options$ignore_overlap,
                ignoreUpstream=case$options$ignore_upstream,ignoreDownstream=case$options$ignore_downstream,
                overlap=case$options$overlap)
        },warning=function(w) {warnings <<- c(warnings,conditionMessage(w));invokeRestart("muffleWarning")}),
        error=function(e) {failure <<- list(class=class(e),message=conditionMessage(e));NULL})
        output <- if (is.null(failure)) list(index_tokens=tokens(result$index),distance_tokens=tokens(result$distance),
            retained_input_ordinals=as.list(mcols(result$peak)$input_ordinal)) else NULL
        rows[[length(rows)+1L]] <- list(version=edition$version,case_id=case$id,output=output,source_error=failure,warnings=as.list(warnings))
    }
}
jsonlite::write_json(list(status="original-source-component-controls-not-user-analyses",R=R.version.string,
    dependency_versions=as.list(versions),cases=rows,full_ChIPseeker_package_loaded=FALSE,
    scope="Original getNearestFeatureIndicesAndDistances plus original utilities executed unchanged with the named present-day GenomicRanges dependencies. No whole package, historical dependency environment, native Java or biological equivalence is claimed."),
    path=args[[2]],pretty=TRUE,auto_unbox=TRUE,null="null",na="string",digits=NA)
