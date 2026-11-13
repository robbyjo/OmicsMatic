# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent reference: call the original namespace directly, never the project protocol.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==3L)
.libPaths(c(args[1],args[2]));suppressPackageStartupMessages(library(AllelicSeries,lib.loc=args[1]))
stopifnot(packageDescription('AllelicSeries',lib.loc=args[1])$Version=='0.1.1.2',!file.exists(args[3]))
out<-args[3];dir.create(out,recursive=TRUE);options(digits=17)
capture_ref<-function(fun,request){
  before<-get('.Random.seed',envir=.GlobalEnv);warnings<-list();messages<-list()
  value<-tryCatch(withCallingHandlers(do.call(getExportedValue('AllelicSeries',fun),request),warning=function(w){warnings[[length(warnings)+1L]]<<-w;invokeRestart('muffleWarning')},message=function(m){messages[[length(messages)+1L]]<<-m;invokeRestart('muffleMessage')}),error=function(e)e)
  list(result=value,warnings=warnings,messages=messages,RNG_before=before,RNG_after=get('.Random.seed',envir=.GlobalEnv))
}
save_group<-function(name,fun,requests){
  saveRDS(requests,file.path(out,paste0(name,'.rds')),version=3)
  set.seed(97744L);reference<-lapply(requests,function(r)capture_ref(fun,r))
  saveRDS(list(function_name=fun,identifiers=names(requests),requests=requests,reference=reference),file.path(out,paste0(name,'-reference.rds')),version=3)
  writeLines(vapply(reference,function(x)if(inherits(x$result,'error'))'original-error' else 'returned',character(1)),file.path(out,paste0(name,'-return-types.txt')))
  cat(name,'complete_original_calls=',length(reference),'\n');flush.console()
}
set.seed(330178L);genes<-vector('list',1000L)
for(i in seq_along(genes)){
  categories<-1L+i%%4L;n<-max(3L,categories+2L)+i%%13L;rho<-c(-.4,0,.2,.6)[1L+i%%4L]
  ld<-outer(seq_len(n),seq_len(n),function(a,b)rho^abs(a-b));if(i%%7L==0L)ld<-NULL
  if(i%%11L==0L)ld<-matrix(1,n,n)
  genes[[i]]<-list(anno=rep(seq_len(categories),length.out=n),beta=rnorm(n,sd=.08),se=runif(n,.05,.5),maf=runif(n,.0001,.01),ld=ld,weights=as.numeric(seq_len(categories)),lambda=if(i%%13L==0L)c(.8,1.2,1) else c(1,1,1))
}
names(genes)<-sprintf('gene-%06d',seq_along(genes))
names(genes)[seq_len(14L)]<-c('001','1','gene.v1','gene.v01','A','a','../escape','nested/gene','C:\\gene','gène\t"quoted"','line\nbreak','-',' NA ','ENSG000001.7')
save_group('coast1000','COASTSS',genes)
mixed<-genes[seq_len(7L)];names(mixed)<-paste0('mixed-',seq_along(mixed));mixed[[2]]$se[]<-0;mixed[[4]]$beta[1]<-NA_real_;mixed[[6]]$weights[]<-0
save_group('mixed','COASTSS',mixed)
dgp<-list(first=list(n=60L,snps=9L,maf_range=c(.05,.1)),second=list(n=65L,snps=12L,maf_range=c(.02,.1)),third=list(n=70L,snps=15L,maf_range=c(.03,.2)))
save_group('stochastic','DGP',dgp)
base<-list(anno=1:3,beta=c(.1,-.2,.3),se=rep(.2,3),ld=diag(3),n_anno=3L)
low<-list()
for(label in c('zero','negative','missing','partial-name','effects-zero','positive-underflow','positive-underflow-effects','mixed-zero')) {
  x<-base
  if(label %in% c('zero','partial-name','effects-zero'))x$se[]<-0
  if(label=='negative')x$se[]<--1
  if(label=='missing')x$se[]<-NA_real_
  if(label=='partial-name')names(x)[names(x)=='se']<-'s'
  if(label=='effects-zero')x$return_beta<-TRUE
  if(label %in% c('positive-underflow','positive-underflow-effects'))x$beta[]<-1000
  if(label=='positive-underflow-effects')x$return_beta<-TRUE
  if(label=='mixed-zero')x$se[1]<-0
  low[[label]]<-x
}
save_group('baseline-zero','BaselineSS',low)
sumlow<-lapply(low,function(x){x$n_anno<-NULL;x$weights<-c(1,2,3);x})
save_group('sum-zero','SumCountSS',sumlow)
for(fun in c('baseline','sum'))for(name in names(low))saveRDS(if(fun=='baseline')low[[name]] else sumlow[[name]],file.path(out,paste0('single-',fun,'-',name,'.rds')),version=3)
saveRDS(list(se=quote(rep(0,3)),anno=1:3,beta=c(.1,-.2,.3),ld=diag(3)),file.path(out,'ambiguous-zero.rds'),version=3)
invalid<-list(empty=list(),duplicate=structure(genes[1:2],names=c('same','same')),missing=structure(genes[1:2],names=c('good',NA_character_)),unnamed=unname(genes[1:2]),bad_arguments=list(good=genes[[1]],bad=1))
for(name in names(invalid))saveRDS(invalid[[name]],file.path(out,paste0('invalid-',name,'.rds')),version=3)
saveRDS(genes[1:3],file.path(out,'capacity.rds'),version=3)
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
writeLines('COMPLETE_INDEPENDENT_ORIGINAL_BATCH_FIXTURES',file.path(out,'COMPLETED.txt'))
