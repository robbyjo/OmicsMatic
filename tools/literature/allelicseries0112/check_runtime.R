# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(trailingOnly=TRUE)
stopifnot(length(args)==4L)
lib <- normalizePath(args[1],mustWork=TRUE)
base <- normalizePath(args[2],mustWork=TRUE)
source <- normalizePath(args[3],mustWork=TRUE)
out <- normalizePath(args[4],mustWork=TRUE)
.libPaths(c(lib,base))
stopifnot(as.character(getRversion())=='4.5.3')
suppressPackageStartupMessages(library(AllelicSeries,lib.loc=lib))
stopifnot(as.character(packageVersion('AllelicSeries',lib.loc=lib))=='0.1.1.2')
ns <- asNamespace('AllelicSeries')
exports <- c('ASBT','ASBTSS','ASKAT','ASKATSS','Aggregator','BaselineSS','COAST','COASTSS','CalcSumstats','Comparator','Counts','DGP','GenomicControl','OLS','SumCountSS','isPD')
stopifnot(all(vapply(exports,function(n)is.function(getExportedValue('AllelicSeries',n)),logical(1))))
# S4 exports also appear in the namespace export set on this R version.
stopifnot(all(exports %in% getNamespaceExports('AllelicSeries')))
actual_functions <- Filter(function(name)is.function(getExportedValue('AllelicSeries',name)),getNamespaceExports('AllelicSeries'))
stopifnot(identical(sort(actual_functions),sort(exports)))
checked <- character()
for (file in list.files(file.path(source,'R'),pattern='\\.R$',full.names=TRUE)) {
  for (expr in parse(file,keep.source=FALSE)) {
    if (is.call(expr) && identical(expr[[1]],as.name('<-')) && length(expr)==3L && is.symbol(expr[[2]]) && is.call(expr[[3]]) && identical(expr[[3]][[1]],as.name('function'))) {
      name <- as.character(expr[[2]])
      original <- eval(expr[[3]],envir=ns)
      installed <- get(name,envir=ns,inherits=FALSE)
      stopifnot(identical(formals(original),formals(installed)),identical(body(original),body(installed)))
      checked <- c(checked,name)
    }
  }
}
stopifnot(all(exports %in% checked))
saveRDS(list(status='READY_ORIGINAL_NAMESPACE_NOT_ANALYSIS',version=as.character(packageVersion('AllelicSeries')),function_exports=exports,source_functions_checked=sort(checked),dll=getLoadedDLLs()[['AllelicSeries']][['path']],source_modified=FALSE,paper_analysis_executed=FALSE),file.path(out,'readiness.rds'),version=3)
writeLines(c('READY_ORIGINAL_NAMESPACE_NOT_ANALYSIS',as.character(packageVersion('AllelicSeries')),as.character(length(checked)),sort(exports)),file.path(out,'readiness.txt'))
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
cat('READY_ORIGINAL_NAMESPACE_NOT_ANALYSIS source_functions=',length(checked),' exports=',length(exports),'\n',sep='')
