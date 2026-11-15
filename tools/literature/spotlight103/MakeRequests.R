# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Project synthetic comparisons, not biological data or recovered author settings.
local({
 a<-commandArgs(TRUE);stopifnot(length(a)==1L,!dir.exists(a[[1L]]));dir.create(a[[1L]],recursive=TRUE)
 suppressPackageStartupMessages(library(Matrix));suppressPackageStartupMessages(library(SingleCellExperiment))
 RNGkind('Mersenne-Twister','Inversion','Rejection');set.seed(103)
 groups<-rep(c('neutrophil A','T/B','other'),each=6L)
 x<-matrix(2+((seq_len(30L*18L)*7L)%%11L),30L,18L,
  dimnames=list(paste0('gene ',seq_len(30L)),paste0('cell/',seq_len(18L))))
 for(i in seq_len(3L))x[((i-1L)*10L+1L):(i*10L),groups==unique(groups)[i]]<-x[((i-1L)*10L+1L):(i*10L),groups==unique(groups)[i]]+50
 y<-cbind(rowMeans(x[,1:6]),rowMeans(x[,7:12]),rowMeans(x[,13:18]),rowMeans(x[,c(1:3,7:9,13:15)]))
 dimnames(y)<-list(rownames(x),c('spot A','spot/B','spot|C','mixed spot'))
 mgs<-data.frame(gene=rownames(x),cluster=rep(unique(groups),each=10L),weight=rep(seq(1,2,length.out=10L),3L),check.names=FALSE)
 args<-list(x=x,y=y,groups=groups,mgs=mgs,verbose=FALSE,threads=1L)
 request<-function(name,arguments,operation='SPOTlight',options=list(),caller_packages=character()){
  set.seed(3103)
  saveRDS(list(schema=1L,edition='1.0.3',operation=operation,arguments=arguments,
   rng=list(kind=RNGkind(),seed=.Random.seed),options=options,caller_packages=caller_packages),file.path(a[[1L]],paste0(name,'.rds')),version=3)
 }
 change<-function(values){result<-args;result[names(values)]<-values;result}
 request('matrix.default',args)
 request('matrix.unscaled',change(list(scale=FALSE)))
 request('matrix.std',change(list(model='std')))
 request('matrix.minprop.high',change(list(min_prop=0.95)))
 request('matrix.unknown.argument',change(list(unknown_original_option=123L)))
 request('matrix.one.iteration',change(list(maxit=1L,verbose=TRUE)))
 request('matrix.penalized',change(list(L1_nmf=0.001,L2_nmf=0.01,L1_nnls_topics=0.001,L2_nnls_topics=0.01,L1_nnls_prop=0.001,L2_nnls_prop=0.01)))
 request('groups.factor',change(list(groups=factor(groups,levels=rev(unique(groups))))))
 request('markers.top',change(list(n_top=5L)))
 request('markers.S4',change(list(mgs=S4Vectors::DataFrame(mgs))))
 custom<-mgs;names(custom)<-c('gene symbol','cell type','marker score')
 request('markers.literal.names',change(list(mgs=custom,gene_id='gene symbol',group_id='cell type',weight_id='marker score')))
 request('genes.reordered',change(list(y=y[rev(seq_len(nrow(y))),])))
 request('matrix.sparse',change(list(x=as(x,'dgCMatrix'),y=as(y,'dgCMatrix'))))
 sce<-SingleCellExperiment(list(counts=x));colLabels(sce)<-groups
 spe<-SingleCellExperiment(list(counts=y))
 request('SCE.explicit',change(list(x=sce,y=spe)))
 request('SCE.labels.warning',change(list(x=sce,y=spe,groups=NULL)))
 request('SCE.warning.error',change(list(x=sce,y=spe,groups=NULL)),options=list(warn=2L))
 request('matrix.delayed',change(list(x=DelayedArray::DelayedArray(x),y=DelayedArray::DelayedArray(y))))
 training<-args;training$y<-rownames(y)
 request('train.full',training,'trainNMF')
 request('train.deconvolve',list(training=training,deconvolution=list(x=y,verbose=FALSE,threads=1L)),'train-deconvolve')
 w<-matrix(1e-12,nrow(x),3L,dimnames=list(rownames(x),paste0('topic_',1:3)))
 for(i in 1:3)w[((i-1L)*10L+1L):(i*10L),i]<-0.1
 h<-matrix(0,3L,ncol(x),dimnames=list(colnames(w),colnames(x)))
 for(i in 1:3)h[i,groups==unique(groups)[i]]<-1
 ref<-diag(3L);dimnames(ref)<-list(unique(groups),colnames(w))
 decon<-list(x=y,mod=list(w=w,d=rep(1,3L),h=h),ref=ref,verbose=FALSE,threads=1L)
 request('deconvolve.fixed.model',decon,'runDeconvolution')
 zero<-decon;zero$x[,4L]<-0
 request('deconvolve.zero.spot',zero,'runDeconvolution')
 request('error.few.features',change(list(x=x[1:9,],y=y[1:9,])))
 request('error.missing.markers',change(list(mgs=mgs[mgs$cluster!='other',])))
 request('error.model',change(list(model='invalid')))
 request('error.minprop',change(list(min_prop=1.01)))
 request('error.penalty',change(list(L1_nmf=1)))
 request('error.groups',change(list(groups=NULL)))
 request('error.missing.assay',change(list(x=sce,slot_sc='missing assay')))
 request('error.mixture.features',change(list(y=y[1:9,])))
 saveRDS(list(x=x,y=y,groups=groups,markers=mgs),file.path(a[[1L]],'fixture-original-objects.rds'),version=3)
 writeLines('Synthetic project-selected cells, spots and markers; not biological validation or author reproduction.',file.path(a[[1L]],'WARNINGS.md'))
})
