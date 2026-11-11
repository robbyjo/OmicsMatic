# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Direct original calls, independent of the Java worker's observation binding.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==1L,!dir.exists(args[[1]]));out<-args[[1]];dir.create(out)
.libPaths(c(normalizePath('build/literature-review/signac-runtime/reference-setup-1/R-lib'),normalizePath('build/omics-sctransform-r'),.Library),include.site=FALSE)
stopifnot(as.character(getRversion())=='4.6.1',as.character(packageVersion('Seurat'))=='5.6.0',!requireNamespace('presto',quietly=TRUE),!requireNamespace('limma',quietly=TRUE));future::plan(future::sequential)
exprs<-parse('tools/literature/collect_seurat_differential_reference.R',keep.source=FALSE);numeric.env<-new.env(parent=baseenv())
for(e in exprs)if(is.call(e)&&identical(e[[1]],as.name('<-'))&&identical(e[[2]],as.name('number')))eval(e,numeric.env)
number<-numeric.env$number
matrices<-readRDS('build/literature-review/signac-runtime/differential-original-reference-3/original/original-matrices.rds');counts<-matrices$counts;data<-matrices$data
scale<-as.matrix(data);scale<-scale-Matrix::rowMeans(data);Matrix::writeMM(as(Matrix::Matrix(scale,sparse=TRUE),'CsparseMatrix'),file.path(out,'scale-data.mtx'));saveRDS(scale,file.path(out,'original-scale-data.rds'))
base.metadata<-data.frame(group=factor(rep(c('a','b','c'),each=12),levels=c('a','b','c')),depth=as.numeric(Matrix::colSums(counts)),batch=factor(rep(c('batch1','batch2'),18)),row.names=colnames(counts))
partial<-base.metadata;partial$group<-factor(c(rep('a',17),rep('b',17),rep('c',2)),levels=c('a','b','c'))
constant<-base.metadata;constant$batch<-factor(rep('batch1',36),levels=c('batch1','batch2'))
for(name in c('base','partial','constant'))write.table(switch(name,base=base.metadata,partial=partial,constant=constant),file.path(out,paste0(name,'-metadata.tsv')),sep='\t',quote=FALSE,col.names=NA)
cases<-list(
 pair_sampled_wilcox=list(mode='PAIR',test='wilcox',slot='data',fc.slot='data',max.cells=6),
 all_sampled_LR=list(mode='ALL',test='LR',slot='data',fc.slot='data',max.cells=6,latent='depth',only.pos=TRUE),
 pair_count_means=list(mode='PAIR',test='wilcox',slot='counts',fc.slot='counts',min.pct=0,logfc.threshold=0),
 pair_scale_means=list(mode='PAIR',test='wilcox',slot='data',fc.slot='scale.data'),
 pair_selected=list(mode='PAIR',test='wilcox',slot='data',fc.slot='data',features=c('f20','f3','f1','f5')),
 all_raw_p_return=list(mode='ALL',test='wilcox',slot='data',fc.slot='data',return.thresh=.05,only.pos=TRUE),
 all_partial=list(mode='ALL',test='wilcox',slot='data',fc.slot='data',metadata='partial',only.pos=TRUE),
 pair_failure=list(mode='PAIR',test='LR',slot='data',fc.slot='data',metadata='constant',latent='batch')
)
results<-list()
for(name in names(cases)) {
 c<-cases[[name]];warnings<-character();metadata<-if(identical(c$metadata,'partial'))partial else if(identical(c$metadata,'constant'))constant else base.metadata
 assay<-SeuratObject::CreateAssayObject(counts=counts,min.cells=0,min.features=0);assay<-SeuratObject::SetAssayData(assay,layer='data',new.data=data);assay<-SeuratObject::SetAssayData(assay,layer='scale.data',new.data=scale)
 object<-SeuratObject::CreateSeuratObject(counts=assay,assay='peaks',meta.data=metadata);SeuratObject::Idents(object)<-metadata$group
 common<-list(test.use=c$test,slot=c$slot,fc.slot=c$fc.slot,min.pct=if(is.null(c$min.pct)).1 else c$min.pct,logfc.threshold=if(is.null(c$logfc.threshold)).1 else c$logfc.threshold,only.pos=isTRUE(c$only.pos),max.cells.per.ident=if(is.null(c$max.cells))Inf else c$max.cells,random.seed=17,verbose=FALSE)
 if(!is.null(c$features))common$features<-c$features
 if(c$mode=='PAIR'){common$object<-assay;common$cells.1<-colnames(counts)[1:12];common$cells.2<-colnames(counts)[13:24];if(!is.null(c$latent))common$latent.vars<-metadata[1:24,c$latent,drop=FALSE]}
 else {common$object<-object;common$return.thresh<-if(is.null(c$return.thresh))1 else c$return.thresh;if(!is.null(c$latent))common$latent.vars<-c$latent}
 set.seed(17)
 result<-tryCatch(withCallingHandlers(do.call(if(c$mode=='PAIR')Seurat::FindMarkers else Seurat::FindAllMarkers,common),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')}),error=function(e)list(error=conditionMessage(e)))
 if(is.data.frame(result)) {
  saveRDS(result,file.path(out,paste0(name,'.rds')));text<-result;for(column in names(text))if(is.numeric(text[[column]]))text[[column]]<-vapply(text[[column]],number,character(1));write.table(text,file.path(out,paste0(name,'.tsv')),sep='\t',quote=FALSE,col.names=NA,na='NA')
  results[[name]]<-list(status='original-completed',rows=nrow(result),warnings=as.list(warnings),settings=c)
 } else results[[name]]<-list(status='original-failed',error=result$error,warnings=as.list(warnings),settings=c)
}
stopifnot(results$pair_failure$status=='original-failed',all(vapply(results[setdiff(names(results),'pair_failure')],function(c)c$status=='original-completed',logical(1))))
jsonlite::write_json(list(status='direct-original-eight-extended-marker-controls-not-Java-adapter-admission',cases=results),file.path(out,'RESULT.json'),auto_unbox=TRUE,pretty=TRUE,digits=17);writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'));cat('DIRECT_ORIGINAL_EXTENDED_SEURAT_CONTROLS_COMPLETE\n')
