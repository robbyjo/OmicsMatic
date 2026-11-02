# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Execute original mashr summary functions on explicit posterior tables.
args<-commandArgs(TRUE);stopifnot(length(args)==3)
.libPaths(c(normalizePath(args[1],winslash='/'),.Library),include.site=FALSE)
stopifnot(packageDescription('mashr')$Version=='0.2.79')
library(mashr);out<-args[2];stopifnot(!file.exists(out),dir.create(out));set.seed(70831)
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
for(name in c('get_significant_results','get_n_significant_conditions','get_pairwise_sharing'))writeLines(capture.output(dput(get(name,asNamespace('mashr')))),file.path(out,paste0('original-',name,'.R.txt')))
dump<-function(x,path){
 if(is.null(dim(x)))x<-matrix(x,nrow=1)
 text<-matrix(format(as.vector(x),digits=17,scientific=TRUE,trim=TRUE),nrow=nrow(x),ncol=ncol(x))
 write.table(text,path,sep='\t',quote=FALSE,row.names=FALSE,col.names=FALSE,na='NA',fileEncoding='UTF-8')
}
cases<-character()
one<-function(name,means,sig,factor=.5,threshold=.05,absolute=FALSE,selection=seq_len(ncol(sig)),nominal=NULL,minimum=2,scoreThreshold=.9){
 folder<-file.path(out,name);stopifnot(dir.create(folder));nr<-nrow(means);nc<-ncol(means);stopifnot(identical(dim(means),dim(sig)))
 if(is.null(nominal))nominal<-matrix(rep(c(.001,.05,.04999999999999999,.5),length.out=nr*nc),nr,nc)
 colnames(means)<-colnames(sig)<-paste0('condition-',seq_len(nc))
 m<-list(result=list(PosteriorMean=means,lfsr=sig));class(m)<-'mash'
 dump(means,file.path(folder,'means.tsv'));dump(sig,file.path(folder,'lfsr.tsv'));dump(nominal,file.path(folder,'nominal-p.tsv'));dump(selection,file.path(folder,'selection.tsv'))
 settings<-c(rows=nr,conditions=nc,factor=factor,threshold=threshold,absolute=as.integer(absolute),nominal_threshold=.05,minimum_nominal=minimum,score_threshold=scoreThreshold)
 write.table(data.frame(key=names(settings),value=format(settings,digits=17,trim=TRUE)),file.path(folder,'settings.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
 selected<-get_significant_results(m,thresh=threshold,conditions=selection)
 dump(selected,file.path(folder,'original-selected-rows.tsv'));dump(get_n_significant_conditions(m,thresh=threshold,conditions=selection),file.path(folder,'original-significant-counts.tsv'))
 dump(apply(sig[,selection,drop=FALSE],1,min),file.path(folder,'original-minimum.tsv'));dump(apply(sig[,selection,drop=FALSE]<=threshold,1,sum),file.path(folder,'inclusive-counts.tsv'))
 transform<-if(absolute)abs else identity
 sharing<-get_pairwise_sharing(m,factor=factor,lfsr_thresh=threshold,FUN=transform)
 dump(sharing,file.path(folder,'original-sharing.tsv'))
 state<-matrix(ifelse(is.nan(sharing),'EMPTY',ifelse(is.na(sharing),'UNDEFINED','DEFINED')),nc,nc)
 write.table(state,file.path(folder,'original-sharing-state.tsv'),sep='\t',quote=FALSE,row.names=FALSE,col.names=FALSE)
 groups<-list(seq_len(nc),seq_len(min(2,nc)))
 writeLines(vapply(groups,function(x)paste(x,collapse='\t'),character(1)),file.path(folder,'groups.tsv'))
 scores<-logs<-matrix(0,nr,length(groups));underflow<-counts<-eligible<-qualifies<-matrix(0L,nr,length(groups))
 for(r in seq_len(nr))for(g in seq_along(groups)){
  q<-1-sig[r,groups[[g]]];scores[r,g]<-prod(q);logs[r,g]<-sum(log(q));underflow[r,g]<-as.integer(scores[r,g]==0&&all(q>0));counts[r,g]<-sum(nominal[r,groups[[g]]]<.05);eligible[r,g]<-as.integer(counts[r,g]>=minimum);qualifies[r,g]<-as.integer(eligible[r,g]&&scores[r,g]>scoreThreshold)
 }
 for(item in c('scores','logs','underflow','counts','eligible','qualifies'))dump(get(item),file.path(folder,paste0('composite-',item,'.tsv')))
 cases<<-c(cases,name)
}
randomMean<-matrix(rnorm(57*4),57,4);randomSig<-matrix(runif(57*4,0,.15),57,4)
boundaryMean<-rbind(c(1,2,1),c(2,1,2),c(0,1,-1),c(1,0,2),c(0,0,0),c(-1,-2,3),c(-1,1,-2),c(.Machine$double.xmax,.Machine$double.xmin,1),c(.Machine$double.xmin,.Machine$double.xmax,1),c(-0,0,1),c(1,-0,1))
boundarySig<-matrix(rep(c(0,.05,.04999999999999999),length.out=length(boundaryMean)),nrow(boundaryMean),3)
for(absolute in c(FALSE,TRUE))for(factor in c(0,.25,.5,1)){
 suffix<-paste0(if(absolute)'absolute' else 'identity','-',factor)
 one(paste0('random-',suffix),randomMean,randomSig,factor,absolute=absolute,selection=c(4,2,2,1))
 one(paste0('boundary-',suffix),boundaryMean,boundarySig,factor,absolute=absolute)
}
for(threshold in c(0,1))one(paste0('threshold-',threshold),randomMean,randomSig,threshold=threshold)
for(absolute in c(FALSE,TRUE)){
 one(paste0('singleton-',absolute),matrix(c(1,0,-2),1,3),matrix(c(.01,.05,0),1,3),absolute=absolute)
 one(paste0('one-condition-',absolute),matrix(c(0,-1,2),3,1),matrix(c(0,.01,.05),3,1),absolute=absolute)
 one(paste0('empty-',absolute),matrix(numeric(),0,3),matrix(numeric(),0,3),absolute=absolute)
}
one('score-boundaries',matrix(1,7,4),rbind(c(.1,0,0,0),c(0,0,0,0),c(1,0,0,0),c(.05,.05,0,0),c(.1,1e-13,0,0),c(.10000000000000002,0,0,0),c(.09999999999999998,0,0,0)),nominal=matrix(.001,7,4))
one('product-underflow',matrix(1,2,200),rbind(rep(.9999,200),rep(1,200)),nominal=matrix(.001,2,200))
real<-args[3];mean<-as.matrix(read.table(file.path(real,'mean.tsv'),header=FALSE,sep='\t'));sig<-as.matrix(read.table(file.path(real,'lfsr.tsv'),header=FALSE,sep='\t'))
for(absolute in c(FALSE,TRUE))for(factor in c(0,.5,1))one(paste0('native-posterior-',absolute,'-',factor),mean,sig,factor,absolute=absolute)
writeLines(c('name',cases),file.path(out,'cases.tsv'))
writeLines('ORIGINAL_MASHR_SHARING_AND_EXPLICIT_MARGINAL_PRODUCT_CONTROLS_COMPLETE',file.path(out,'STATUS.txt'))
