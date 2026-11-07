# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Retain original-package numerical controls. No user dataset is analyzed.
args <- commandArgs(TRUE)
stopifnot(length(args)==6L)
out <- args[[1]]
stopifnot(!dir.exists(out),dir.create(out))
.libPaths(c(args[2:6],.Library),include.site=FALSE)
stopifnot(identical(as.character(packageVersion("DESeq2")),"1.52.0"))
source_file <- file.path(dirname(args[[2]]),"../reference-intake-2/source/R/core.R")
expressions <- parse(source_file)
definition <- Filter(function(x)is.call(x)&&identical(x[[1]],as.name("<-"))&&
                       identical(x[[2]],as.name("estimateSizeFactorsForMatrix")),expressions)
stopifnot(length(definition)==1L)
source_function <- eval(definition[[1]][[3]],envir=asNamespace("DESeq2"))
stopifnot(identical(body(source_function),body(DESeq2::estimateSizeFactorsForMatrix)),
          identical(formals(source_function),formals(DESeq2::estimateSizeFactorsForMatrix)))
number <- function(x)vapply(x,function(z){
    if(is.nan(z)) "NaN" else if(is.na(z)) "NA" else if(is.infinite(z))
        if(z>0)"Inf" else "-Inf" else formatC(z,digits=17L,format="g",decimal.mark=".")
},character(1))
matrix_numbers <- function(m)lapply(seq_len(nrow(m)),function(i)number(m[i,]))
cases <- list()
add <- function(name,counts,type="ratio",means=NULL,controls=NULL) {
    params <- list(counts=counts,type=type)
    if(!is.null(means)) params$geoMeans <- means
    if(!is.null(controls)) params$controlGenes <- controls+1L
    warnings_seen <- character();error <- NULL
    sf <- tryCatch(withCallingHandlers(do.call(DESeq2::estimateSizeFactorsForMatrix,params),
                   warning=function(w){warnings_seen <<- c(warnings_seen,conditionMessage(w))}),
                   error=function(e){error <<- conditionMessage(e);NULL})
    cases[[length(cases)+1L]] <<- list(name=name,counts=counts,type=type,
         geo_means=if(is.null(means))NULL else number(means),control_genes=controls,
         expected=if(!is.null(error))list(status="error",error=error,warnings=unname(warnings_seen)) else
             list(status="success",factors=number(sf),normalized=matrix_numbers(sweep(counts,2L,sf,"/")),
                  warnings=unname(warnings_seen)))
}
base <- rbind(c(1L,4L,9L),c(9L,4L,1L),c(16L,1L,4L),c(2L,8L,18L))
add("even-median-is-on-log-scale",base[1:2,1:2])
add("ordinary-ratio",base)
add("zero-gene-discarded",rbind(base,c(0L,1L,4L)))
add("poscounts-root-uses-all-samples",rbind(c(0L,9L,27L),c(1L,0L,4L)),"poscounts")
add("all-zero-ratio-error",matrix(0L,3L,2L))
add("all-zero-poscounts-error",matrix(0L,3L,2L),"poscounts")
add("every-gene-has-zero-ratio-error",rbind(c(0L,1L),c(2L,0L)))
add("empty-sample-poscounts-NA",rbind(c(0L,9L,27L),c(0L,1L,4L)),"poscounts")
add("all-zero-gene-poscounts",rbind(base,c(0L,0L,0L)),"poscounts")
add("one-sample",matrix(c(0L,1L,99L),ncol=1L))
add("one-gene",matrix(c(3L,5L,7L),nrow=1L))
add("controls-duplicates-retained",base,controls=c(2L,0L,2L,2L))
add("controls-empty-NA",base,controls=integer())
add("controls-only-ineligible-NA",rbind(base,c(0L,1L,4L)),controls=4L)
add("provided-means-center-factors",base,means=c(1,2,3,4))
add("provided-means-ignore-type",base,"poscounts",means=c(1,2,3,4))
add("provided-means-exclude-zero-infinity",base,means=c(0,Inf,3,4))
add("provided-means-partial-NaN",base,means=c(NaN,2,3,4))
add("provided-all-infinite-error",base,means=c(0,Inf,0,Inf))
add("provided-all-NaN-NA",base,means=rep(NaN,4L))
add("provided-infinite-NaN-NA",base,means=c(NaN,0,Inf,0))
add("provided-means-empty-controls-propagate-NA",base,means=c(1,2,3,4),controls=integer())
add("provided-means-one-empty-sample-propagates-NA",rbind(c(0L,9L,27L),c(0L,1L,4L)),means=c(1,2))
add("provided-means-all-zero-counts-NA",matrix(0L,3L,2L),means=c(1,2,3))
add("provided-means-length-error",base,means=c(1,2))
add("maximum-integer-count",rbind(c(2147483647L,1L),c(1L,2147483647L)))
add("provided-means-overflow-centering-NaN",matrix(2147483647L,2L,2L),means=rep(.Machine$double.xmin*.Machine$double.eps,2L))
add("provided-means-extreme-dynamic-range",rbind(c(1L,2147483647L),c(2147483647L,1L)),means=c(.Machine$double.xmin,1e300))
set.seed(9142026L)
for(i in seq_len(48L)) {
    genes <- sample(2:80,1L); samples <- sample(2:17,1L)
    m <- matrix(as.integer(rnbinom(genes*samples,mu=sample(c(1,10,100,1000),1L),size=2)),nrow=genes)
    means <- if(i%%3L==0L)exp(runif(genes,-3,10)) else NULL
    controls <- if(i%%4L==0L)sample.int(genes,sample.int(genes,1L),replace=TRUE)-1L else NULL
    add(paste0("seeded-random-",i),m,if(i%%2L==0L)"ratio" else "poscounts",means,controls)
}
jsonlite::write_json(list(schema_version=1L,reference_package="DESeq2",reference_version="1.52.0",
                         source_function_body_and_formals_match=TRUE,cases=cases),
                     file.path(out,"original.json"),auto_unbox=FALSE,pretty=TRUE,null="null",matrix="rowmajor")
writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"))
writeLines(capture.output(DESeq2::estimateSizeFactorsForMatrix),file.path(out,"loaded-function.txt"))
cat("Original DESeq2 function cases:",length(cases),"\n")
