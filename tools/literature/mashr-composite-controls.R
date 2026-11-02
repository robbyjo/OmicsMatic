# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Execute the unmodified, pinned dreamlet composite functions, not a rewritten oracle.
args<-commandArgs(TRUE);stopifnot(length(args)==2)
source(args[1],local=TRUE,encoding='UTF-8')
out<-args[2];stopifnot(!file.exists(out),dir.create(out));set.seed(73089)
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
dump<-function(x,name){
 if(is.null(dim(x)))x<-matrix(x,nrow=1)
 text<-matrix(format(as.vector(x),digits=17,scientific=TRUE,trim=TRUE),nrow=nrow(x),ncol=ncol(x))
 write.table(text,name,sep='\t',quote=FALSE,row.names=FALSE,col.names=FALSE,na='NA',fileEncoding='UTF-8')
}
cases<-character()
log1mexp<-function(x){if(x == -Inf)return(0);if(x==0)return(-Inf);if(x < -log(2))log1p(-exp(x)) else log(-expm1(x))}
one<-function(name,input,include,exclude,mode='all',scale='probability'){
 path<-file.path(out,name);stopifnot(dir.create(path));nc<-ncol(input);nr<-nrow(input)
 colnames(input)<-if(nc==0)character() else if(nc==4)c('EN.*','EN_L3_IT','IN','Astro') else paste0('condition-',seq_len(nc))
 p<-if(scale=='lfsr')1-input else input
 inc<-colnames(p)[include];exc<-colnames(p)[exclude]
 # Match the study scripts' direct internal call and the public matrix path.
 original<-.compositePosteriorTest(p,include=inc,exclude=exc,test=mode)
 public<-compositePosteriorTest(p,include=inc,exclude=exc,test=mode)
 stopifnot(identical(original,public))
 dump(input,file.path(path,'input.tsv'));dump(include,file.path(path,'include.tsv'));dump(exclude,file.path(path,'exclude.tsv'))
 settings<-c(rows=nr,conditions=nc,scale=scale,mode=mode)
 write.table(data.frame(key=names(settings),value=settings),file.path(path,'settings.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
 dump(original,file.path(path,'original-score.tsv'))
 stable<-missing<-numeric(nr)
 for(r in seq_len(nr)){
  vals<-input[r,];missing[r]<-sum(is.na(vals[c(include,exclude)]))
  present<-if(scale=='lfsr')log1p(-vals) else log(vals)
  absent<-if(scale=='lfsr')log(vals) else log1p(-vals)
  present[is.na(vals)]<--Inf;absent[is.na(vals)]<-0
  first<-if(mode=='all')sum(present[include]) else log1mexp(sum(absent[include]))
  stable[r]<-first+sum(absent[exclude])
 }
 dump(stable,file.path(path,'stable-log.tsv'));dump(missing,file.path(path,'missing-occurrences.tsv'))
 dump(as.integer(original>=.05),file.path(path,'inclusive-005.tsv'));dump(as.integer(original>.5),file.path(path,'strict-05.tsv'));dump(as.integer(original>.8),file.path(path,'strict-08.tsv'))
 cases<<-c(cases,name)
}
random<-matrix(runif(48*4),48,4)
for(scale in c('probability','lfsr'))for(mode in c('all','at least 1')){
 suffix<-paste0(scale,'-',gsub(' ','-',mode))
 one(paste0('random-',suffix),random,c(1,2),c(3,4),mode,scale)
 one(paste0('empty-include-',suffix),random,integer(),c(3,4),mode,scale)
 one(paste0('empty-exclude-',suffix),random,c(1,2),integer(),mode,scale)
 one(paste0('duplicate-overlap-',suffix),random,c(1,1,2),c(2,3,3),mode,scale)
 one(paste0('zero-rows-',suffix),matrix(numeric(),0,4),c(1,2),c(3,4),mode,scale)
 one(paste0('zero-columns-',suffix),matrix(numeric(),3,0),integer(),integer(),mode,scale)
}
boundary<-rbind(c(.05,1,1,1),c(.5,1,1,1),c(.8,1,1,1),c(0,1,0,1),c(1,0,1,0),c(NA,.5,NaN,1))
one('thresholds-and-missing',boundary,1:4,integer())
one('missing-any',boundary,c(1,2),c(3,4),'at least 1')
tiny<-rbind(c(1e-100,1e-200,1e-300,0),c(1e-16,0,0,0),c(1-2^-53,1-2^-52,1,0))
one('complement-cancellation',tiny,c(1,2),integer(),'at least 1')
one('lfsr-complement-cancellation',tiny,c(3,4),c(1,2),'all','lfsr')
one('product-underflow',matrix(rep(c(1e-200,1e-200,.1,.2),2),2,4,byrow=TRUE),c(1,2),c(3,4))
one('eight-contrasts',matrix(c(rep(.95,8),rep(.9,8),rep(0,8),rep(1,8)),4,8,byrow=TRUE),1:8,integer(),'all','lfsr')
unknown<-tryCatch(.compositePosteriorTest(matrix(.5,2,4,dimnames=list(NULL,c('EN.*','EN_L3_IT','IN','Astro'))),'EN_L3.*'),error=function(e)e)
stopifnot(inherits(unknown,'error'));writeLines(conditionMessage(unknown),file.path(out,'unknown-regex-is-not-a-selector.txt'))
writeLines(c('name',cases),file.path(out,'cases.tsv'));writeLines('ORIGINAL_DREAMLET_COMPOSITE_CONTROLS_COMPLETE',file.path(out,'STATUS.txt'))
