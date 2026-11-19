# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args<-commandArgs(trailingOnly=TRUE)
if(length(args)!=1L)stop('Require one literal library-list path')
libs<-readLines(args[[1L]],warn=FALSE,encoding='UTF-8');.libPaths(c(libs,.Library))
skip<-function(message){cat('SKIP3: ',message,' ACTION: supply the named permitted exact runtime/library, or leave reference capture skipped; never substitute an edition.\n',sep='',file=stderr());quit(status=3L)}
if(as.character(getRversion())!='4.6.1')skip('Original-reference profile requires R 4.6.1.')
required<-c(Seurat='5.6.0',SeuratObject='5.4.0',Matrix='1.7.5',jsonlite='2.0.0',future='1.80.0')
for(package in names(required)) {
 error<-tryCatch({loadNamespace(package);NULL},error=function(e)conditionMessage(e))
 if(!is.null(error))skip(paste('Cannot load',package,required[[package]],':',error))
 if(as.character(packageVersion(package))!=required[[package]])skip(paste('Require',package,required[[package]],'; found',as.character(packageVersion(package))))
}
cat('PASS exact reference prerequisites; no installation performed\n')
