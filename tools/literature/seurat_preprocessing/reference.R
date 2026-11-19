# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original-runtime reference generation, not a Java implementation or an analysis adapter.
args <- commandArgs(trailingOnly=TRUE)
stopifnot(length(args)==3L)
out <- args[[1L]]; source_root <- args[[2L]]
libs <- readLines(args[[3L]],warn=FALSE,encoding='UTF-8')
.libPaths(c(libs,.Library))
suppressPackageStartupMessages(library(Seurat));suppressPackageStartupMessages(library(Matrix))
stopifnot(as.character(getRversion())=='4.6.1',as.character(packageVersion('Seurat'))=='5.6.0',as.character(packageVersion('Matrix'))=='1.7.5')
options(Seurat.nthreads=1L);future::plan('sequential');Sys.setenv(OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
definitions <- new.env(parent=asNamespace('Seurat'))
for(file in c('R/preprocessing.R','R/utilities.R'))for(expr in parse(file.path(source_root,file))) {
 if(is.call(expr)&&identical(expr[[1L]],as.name('<-'))&&is.symbol(expr[[2L]])&&is.call(expr[[3L]])&&identical(expr[[3L]][[1L]],as.name('function')))eval(expr,envir=definitions)
}
guards <- lapply(c('LogNormalize.V3Matrix','NormalizeData.V3Matrix','RelativeCounts','CustomNormalize','ScaleData.default','FastRowScale'),function(name) {
 installed <- get(name,envir=asNamespace('Seurat'));reference <- get(name,envir=definitions)
 stopifnot(identical(body(installed),body(reference)),identical(formals(installed),formals(reference)))
 list(name=name,body_formals_identical=TRUE)
})
number <- function(x) vapply(x,function(v)if(is.nan(v))'NaN' else if(is.na(v))'NA' else if(is.infinite(v))if(v>0)'Inf' else '-Inf' else sprintf('%.17g',v),character(1))
sparse <- function(x) {
 x <- as(x,'dgCMatrix');dimnames(x) <- list(paste0('feature_',seq_len(nrow(x))),paste0('cell_',seq_len(ncol(x))));x
}
set.seed(193107L)
a <- matrix(rpois(17L*23L,3),17L,23L);a[matrix(runif(length(a))<.64,17L,23L)]<-0;a[1L,]<-0;a[2L,]<-2
fractional <- matrix(round(runif(5L*7L,0,20),8),5L,7L);fractional[fractional<7]<-0
signed <- matrix(c(-2,-2,-2,-2,0,0,0,0,-7,.25,3,-1,2,5,4,-8),4L,4L,byrow=TRUE)
explicit <- new('dgCMatrix',Dim=as.integer(c(4,5)),i=as.integer(c(0,1,3,0,2,3,0,1,2,3,1)),p=as.integer(c(0,3,6,10,11,11)),x=c(1,0,4,3,0,1,2,5,4,3,0),Dimnames=list(paste0('feature_',1:4),paste0('cell_',1:5)))
cancellation <- matrix(c(rep(1e8+.1,7),1e8+.2,rep(.1,8),rep(0,8)),3,8,byrow=TRUE)
overflow <- matrix(c(1e308,1e308,2,1e308,1e308,3,1,2,3),3,3)
tiny <- matrix(c(1e-310,2e-310,0,3e-310,0,4e-310),3,2)
datasets <- list(counts=sparse(a),fractional=sparse(fractional),signed=sparse(signed),explicit_zeros=explicit,single_cell=sparse(matrix(c(3,0,5),3,1)),cancellation=sparse(cancellation),overflow=sparse(overflow),tiny=sparse(tiny),single_feature=sparse(matrix(c(1,0,3,2),1,4)),one_by_one=sparse(matrix(7,1,1)),all_zero=sparse(matrix(0,3,4)))
datasets$prepared_log <- as(NormalizeData(datasets$counts,normalization.method='LogNormalize',scale.factor=10000,verbose=FALSE),'dgCMatrix')
datasets$prepared_clr <- as(NormalizeData(datasets$counts,normalization.method='CLR',margin=2,verbose=FALSE),'dgCMatrix')
records <- lapply(names(datasets),function(id) {
 x<-datasets[[id]];list(id=id,features=rownames(x),cells=colnames(x),pointers=x@p,rows=x@i,values=number(x@x))
})
normalizations <- list();scalings<-list();source_objects<-list()
capture <- function(fun) {
 warnings<-character();value<-tryCatch(withCallingHandlers(fun(),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')}),error=function(e)e)
 if(inherits(value,'error'))return(list(error=conditionMessage(value),warnings=warnings))
 list(value=value,warnings=warnings)
}
for(id in names(datasets)) {
 x<-datasets[[id]]
 if(id!='signed'&&!startsWith(id,'prepared_'))for(method in c('LogNormalize','RC','CLR1','CLR2')) {
  scales<-if(method=='LogNormalize')c(10000,2.75,.5,0) else if(method=='RC')c(10000,2.75,0) else NA_real_
  for(scale in scales) {
   name<-paste(id,method,if(is.na(scale))'none' else number(scale),sep=':');margin<-if(method=='CLR2')2L else 1L
   result<-capture(function()NormalizeData(x,normalization.method=if(startsWith(method,'CLR'))'CLR' else method,scale.factor=if(is.na(scale))10000 else scale,margin=margin,verbose=FALSE))
   record<-list(id=name,dataset=id,method=method,scale=if(is.na(scale))NULL else number(scale),margin=margin,warnings=result$warnings)
   if(!is.null(result$error))record$error<-result$error else {
    z<-result$value;record$class<-class(z);record$values<-number(as.vector(as.matrix(z)));record$stored_values<-if(inherits(z,'dgCMatrix'))number(z@x) else NULL;record$pointers<-if(inherits(z,'dgCMatrix'))z@p else NULL;record$rows<-if(inherits(z,'dgCMatrix'))z@i else NULL;source_objects[[name]]<-z
   }
   normalizations[[length(normalizations)+1L]]<-record
  }
 }
 for(center in c(TRUE,FALSE))for(scale in c(TRUE,FALSE))for(cap in c(10,.3,Inf,-.2)) {
  requested<-if(startsWith(id,'prepared_'))rownames(x)[c(17L,3L,7L)] else rev(rownames(x));name<-paste(id,'ScaleData',center,scale,number(cap),sep=':')
  result<-capture(function()ScaleData(x,features=requested,do.center=center,do.scale=scale,scale.max=cap,verbose=FALSE))
  record<-list(id=name,dataset=id,center=center,scale=scale,cap=number(cap),features=requested,warnings=result$warnings)
  if(!is.null(result$error))record$error<-result$error else {z<-result$value;record$values<-number(as.vector(z));record$cells<-colnames(z);source_objects[[name]]<-z}
  scalings[[length(scalings)+1L]]<-record
 }
}
versions <- lapply(c('Seurat','SeuratObject','Matrix','jsonlite','future'),function(package)list(package=package,version=as.character(packageVersion(package)),path=find.package(package)))
result<-list(profile='Seurat-5.6.0-matrix-preprocessing',source_commit='75dfa8603befe31505e6287d32dc86c607732bd6',guards=guards,versions=versions,datasets=records,normalizations=normalizations,scalings=scalings,numeric_text='17 significant digits; explicit NA/NaN/Inf/-Inf kinds',raw_count_identity_repair=FALSE)
jsonlite::write_json(result,file.path(out,'reference.json'),pretty=TRUE,auto_unbox=TRUE,digits=17,na='string')
saveRDS(source_objects,file.path(out,'original-results.rds'),version=3)
writeLines(capture.output(sessionInfo()),file.path(out,'session.txt'))
cat('COMPLETE',length(records),'datasets',length(normalizations),'normalizations',length(scalings),'scalings\n')
