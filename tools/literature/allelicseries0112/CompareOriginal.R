# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args<-commandArgs(trailingOnly=TRUE)
stopifnot(length(args)==4L)
.libPaths(c(args[1],args[2]));suppressPackageStartupMessages(library(AllelicSeries,lib.loc=args[1]))
reference<-readRDS(file.path(args[3],'complete-namespace-reference.rds'))
results<-list()
for(name in names(reference$requests)){
  original<-reference$results[[name]]
  stopifnot(!original$error)
  actual<-readRDS(file.path(args[4],name,'original-value.rds'))
  stopifnot(identical(actual,original$value))
  packet<-readRDS(file.path(args[4],name,'complete-original-return.rds'))
  stopifnot(identical(packet$arguments,reference$requests[[name]]),identical(packet$function_name,name),length(packet$warnings)==length(original$warnings))
  results[[name]]<-list(identical_full_original_value=TRUE,returned_class=class(actual),warnings=length(packet$warnings))
}
saveRDS(results,file.path(args[4],'complete-comparison.rds'),version=3)
writeLines(paste('PASS_FULL_ORIGINAL_RETURN_OBJECTS',length(results)),file.path(args[4],'COMPARISON.txt'))
cat('PASS_FULL_ORIGINAL_RETURN_OBJECTS',length(results),'\n')
