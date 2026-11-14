# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE)
stopifnot(length(args)==4L)
lib <- normalizePath(args[1],mustWork=TRUE)
base <- normalizePath(args[2],mustWork=TRUE)
source <- normalizePath(args[3],mustWork=TRUE)
out <- normalizePath(args[4],mustWork=TRUE)
.libPaths(c(lib,base))
stopifnot(as.character(getRversion())=='4.5.3')
suppressPackageStartupMessages(library(rstatix,lib.loc=lib))
stopifnot(as.character(packageVersion('rstatix',lib.loc=lib))=='0.7.2')
ns <- asNamespace('rstatix')
original_exports <- character()
for(expr in parse(file.path(source,'NAMESPACE'))) {
 if(is.call(expr) && identical(expr[[1]],as.name('export')))
  original_exports <- c(original_exports,vapply(as.list(expr)[-1],as.character,character(1)))
}
stopifnot(identical(sort(getNamespaceExports('rstatix')),sort(original_exports)),length(original_exports)==126L)
checked <- character()
for(file in list.files(file.path(source,'R'),pattern='\\.R$',full.names=TRUE)) {
 for(expr in parse(file,keep.source=FALSE)) {
  if(is.call(expr) && identical(expr[[1]],as.name('<-')) && length(expr)==3L && is.symbol(expr[[2]]) && is.call(expr[[3]]) && identical(expr[[3]][[1]],as.name('function'))) {
   name <- as.character(expr[[2]])
   original <- eval(expr[[3]],envir=ns)
   installed <- get(name,envir=ns,inherits=FALSE)
   stopifnot(identical(formals(original),formals(installed)),identical(body(original),body(installed)))
   checked <- c(checked,name)
  }
 }
}
stopifnot(all(c('levene_test','t_test','wilcox_test','shapiro_test','kruskal_test','cor_test','adjust_pvalue') %in% checked))
result <- list(status='READY_ORIGINAL_RSTATIX072_NAMESPACE_NOT_ANALYSIS',version='0.7.2',
 exports=sort(original_exports),source_functions_checked=sort(unique(checked)),
 all_exported_objects=lapply(sort(original_exports),function(n)list(name=n,class=class(getExportedValue('rstatix',n)))),
 packages=as.data.frame(installed.packages(lib.loc=c(lib,base))[,c('Package','Version','LibPath')],stringsAsFactors=FALSE),
 source_modified=FALSE,paper_analysis_executed=FALSE)
saveRDS(result,file.path(out,'readiness.rds'),version=3)
jsonlite::write_json(result,file.path(out,'readiness.json'),pretty=TRUE,auto_unbox=TRUE)
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
writeLines(capture.output(getS3method('leveneTest','default',envir=asNamespace('car'))),file.path(out,'original-car-levene-default.txt'))
cat('READY_ORIGINAL_RSTATIX072_NAMESPACE_NOT_ANALYSIS functions=',length(unique(checked)),' exports=',length(original_exports),'\n',sep='')
