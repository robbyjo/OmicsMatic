# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent original test outputs for the forthcoming typed Signac/Seurat adapter.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==1L)
.libPaths(c(normalizePath('build/literature-review/signac-runtime/reference-setup-1/R-lib'),normalizePath('build/omics-sctransform-r'),.Library),include.site=FALSE)
stopifnot(as.character(getRversion())=='4.6.1',as.character(packageVersion('Seurat'))=='5.6.0',as.character(packageVersion('SeuratObject'))=='5.4.0',as.character(packageVersion('Signac'))=='1.17.1',!requireNamespace('presto',quietly=TRUE),!requireNamespace('limma',quietly=TRUE))
future::plan(future::sequential)
out<-args[[1]];stopifnot(!dir.exists(out));dir.create(out)
set.seed(39471);counts<-matrix(rpois(120*36,2),120,36,dimnames=list(paste0('f',1:120),paste0('c',1:36)))
counts[1,]<-0;counts[2,]<-1;counts[3:12,1:12]<-counts[3:12,1:12]+4L;counts[13:20,13:24]<-counts[13:20,13:24]+3L
counts<-as(Matrix::Matrix(counts,sparse=TRUE),'dgCMatrix');data<-Signac::RunTFIDF(counts,method=1,scale.factor=10000,verbose=FALSE)
Matrix::writeMM(counts,file.path(out,'counts.mtx'));Matrix::writeMM(data,file.path(out,'tfidf.mtx'))
saveRDS(list(counts=counts,data=data),file.path(out,'original-matrices.rds'))
assay<-SeuratObject::CreateAssayObject(counts=counts,min.cells=0,min.features=0);assay<-SeuratObject::SetAssayData(assay,layer='data',new.data=data)
metadata<-data.frame(group=factor(rep(c('a','b','c'),each=12),levels=c('a','b','c')),depth=as.numeric(Matrix::colSums(counts)),batch=factor(rep(c('batch1','batch2'),18)),row.names=colnames(counts))
write.table(metadata,file.path(out,'metadata.tsv'),sep='\t',quote=FALSE,col.names=NA)
object<-SeuratObject::CreateSeuratObject(counts=assay,assay='peaks',meta.data=metadata);SeuratObject::Idents(object)<-metadata$group
cases<-list(
 pair_wilcox=list(mode='PAIR',test='wilcox',slot='data',min.pct=.1,logfc.threshold=.1),
 pair_LR=list(mode='PAIR',test='LR',slot='data',min.pct=.1,logfc.threshold=.1),
 pair_LR_latent=list(mode='PAIR',test='LR',slot='data',min.pct=.1,logfc.threshold=.1,latent=c('depth','batch')),
 pair_wilcox_counts=list(mode='PAIR',test='wilcox',slot='counts',min.pct=0,logfc.threshold=0),
 all_wilcox=list(mode='ALL',test='wilcox',slot='data',min.pct=.1,logfc.threshold=.1,only.pos=TRUE),
 all_LR_latent=list(mode='ALL',test='LR',slot='data',min.pct=.1,logfc.threshold=.1,latent=c('depth'),only.pos=TRUE)
)
results<-list()
number<-function(x)if(is.nan(x))'NaN' else if(is.na(x))'NA' else if(is.infinite(x))if(x>0)'Inf' else '-Inf' else sprintf('%.17g',x)
for(name in names(cases)) {
 c<-cases[[name]];warnings<-character()
 result<-tryCatch(withCallingHandlers({
  common<-list(test.use=c$test,slot=c$slot,min.pct=c$min.pct,logfc.threshold=c$logfc.threshold,only.pos=isTRUE(c$only.pos),verbose=FALSE,random.seed=17)
  if(c$mode=='PAIR') {
   common$object<-assay;common$cells.1<-colnames(counts)[1:12];common$cells.2<-colnames(counts)[13:24]
   if(!is.null(c$latent))common$latent.vars<-metadata[c(1:24),c$latent,drop=FALSE]
   do.call(Seurat::FindMarkers,common)
  } else {
   common$object<-object;common$return.thresh<-1
   if(!is.null(c$latent))common$latent.vars<-c$latent
   do.call(Seurat::FindAllMarkers,common)
  }
 },warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')}),error=function(e)list(error=conditionMessage(e)))
 if(is.data.frame(result)) {
  saveRDS(result,file.path(out,paste0(name,'.rds')))
  text<-result
  for(column in names(text))if(is.numeric(text[[column]]))text[[column]]<-vapply(text[[column]],number,character(1))
  write.table(text,file.path(out,paste0(name,'.tsv')),sep='\t',quote=FALSE,col.names=NA,na='NA')
  results[[name]]<-list(status='original-completed',rows=nrow(result),columns=colnames(result),warnings=warnings,settings=c)
 } else results[[name]]<-list(status='original-failed',error=result$error,warnings=warnings,settings=c)
}
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
jsonlite::write_json(list(status='original-Seurat-test-reference-not-Java-adapter-admission',cases=results,backend='base-R-wilcox-no-presto-no-limma;lmtest-LR',R='4.6.1',Seurat='5.6.0',historical_whole_packages_executed=FALSE),file.path(out,'RESULT.json'),auto_unbox=TRUE,pretty=TRUE,digits=17)
cat('SEURAT_ORIGINAL_DIFFERENTIAL_REFERENCE_COMPLETE\n')
