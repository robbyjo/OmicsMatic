# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Exercise the actual reference helper's numeric transport without refitting tests.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==1L,!dir.exists(args[[1]]));dir.create(args[[1]])
exprs<-parse('tools/literature/collect_seurat_differential_reference.R',keep.source=FALSE)
selected<-Filter(function(e)is.call(e)&&identical(e[[1]],as.name('<-'))&&identical(e[[2]],as.name('number')),as.list(exprs));stopifnot(length(selected)==1L)
env<-new.env(parent=baseenv());eval(selected[[1]],env)
values<-c(1/3,NaN,NA_real_,Inf,-Inf,0,-0,.Machine$double.xmin,.Machine$double.xmax)
encoded<-vapply(values,env$number,character(1));stopifnot(identical(encoded[2:5],c('NaN','NA','Inf','-Inf')))
stopifnot(identical(as.numeric(encoded[c(1,6:9)]),values[c(1,6:9)]))
saveRDS(values,file.path(args[[1]],'original-values.rds'));stopifnot(identical(readRDS(file.path(args[[1]],'original-values.rds')),values))
writeLines(encoded,file.path(args[[1]],'encoded-values.txt'),useBytes=TRUE)
writeLines('PASSED_EXACT_REFERENCE_FORMATTER_FINITE_AND_DISTINCT_NONFINITE_KINDS',file.path(args[[1]],'COMPLETED.txt'))
cat('Reference numeric transport passed:17-digit finite roundtrip and distinct NA/NaN/Inf kinds\n')
