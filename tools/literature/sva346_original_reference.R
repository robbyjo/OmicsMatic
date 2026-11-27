# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent, deterministic synthetic fixtures and direct original R results.
# Usage: Rscript --vanilla this-file.R NEW-DIRECTORY
args<-commandArgs(TRUE);stopifnot(length(args)==1L,!dir.exists(args[1]));dir.create(args[1]);setwd(args[1])
stopifnot(getRversion()=='4.2.3',as.character(packageVersion('sva'))=='3.46.0')
library(sva)
RNGkind('Mersenne-Twister','Inversion','Rejection');set.seed(61027)
g<-500L;n<-20L;group<-rep(0:1,each=n/2);age<-seq(-1,1,length.out=n);latent<-rnorm(n);latent2<-rnorm(n)
dat<-outer(rnorm(g,7,1),rep(1,n))+outer(c(rep(2,300),rep(0,200)),latent)+outer(rnorm(g,0,.6),latent2)+outer(rnorm(g,0,.3),group)+matrix(rnorm(g*n,sd=.35),g,n)
rownames(dat)<-sprintf('feature-%04d',seq_len(g));colnames(dat)<-sprintf('sample-%02d',seq_len(n))
counts<-exp(dat/3)+.137;mod<-cbind(intercept=1,group=group,age=age);rownames(mod)<-colnames(dat)
mod0<-mod[,c('intercept','age'),drop=FALSE];controls<-c(seq(.2,1,length.out=300),rep(0,200))
table.write<-function(x,path,first){write.table(cbind(setNames(data.frame(rownames(x),check.names=FALSE),first),as.data.frame(x,check.names=FALSE)),path,quote=FALSE,sep='\t',row.names=FALSE,col.names=TRUE,na='NA')}
cases<-list(
 list(id='sva-irw-default-null',operation='SVA',method='irw',n_sv=2L,iterations=5L),
 list(id='sva-irw-explicit-null',operation='SVA',method='irw',n_sv=1L,iterations=3L,null=TRUE),
 list(id='seq-irw-fractional',operation='SVASEQ',method='irw',n_sv=2L,iterations=5L),
 list(id='seq-irw-constant',operation='SVASEQ',method='irw',n_sv=1L,iterations=4L,constant=2.5,null=TRUE),
 list(id='sva-two-step',operation='SVA',method='two-step',n_sv=2L),
 list(id='seq-two-step',operation='SVASEQ',method='two-step',n_sv=2L),
 list(id='sva-supervised',operation='SVA',method='supervised',n_sv=2L,control=TRUE),
 list(id='seq-control-override',operation='SVASEQ',method='irw',n_sv=2L,control=TRUE),
 list(id='sva-zero',operation='SVA',method='irw',n_sv=0L),
 list(id='seq-zero',operation='SVASEQ',method='irw',n_sv=0L),
 list(id='sva-filter',operation='SVA',method='irw',n_sv=2L,vfilter=250L),
 list(id='seq-filter',operation='SVASEQ',method='irw',n_sv=2L,vfilter=250L),
 list(id='number-be',operation='NUM_SV',num_sv_method='be',permutations=20L),
 list(id='number-leek',operation='NUM_SV',num_sv_method='leek'),
 list(id='number-be-filter',operation='NUM_SV',num_sv_method='be',permutations=11L,vfilter=250L),
 list(id='sva-auto-be',operation='SVA',method='irw',n_sv='auto',num_sv_method='be'),
 list(id='seq-auto-leek',operation='SVASEQ',method='irw',n_sv='auto',num_sv_method='leek'),
 list(id='seq-filter-auto-original-error',operation='SVASEQ',method='irw',n_sv='auto',num_sv_method='be',vfilter=250L),
 list(id='controls-filter-original-error',operation='SVA',method='irw',n_sv=2L,control=TRUE,vfilter=250L)
)
number<-function(x)vapply(x,function(v)if(is.nan(v))'NaN'else if(is.na(v))'NA'else sprintf('%.17g',v),'')
ledger<-list()
for(case in cases){
 dir.create(case$id);input<-if(case$operation=='SVASEQ')counts else dat
 options(digits=17);table.write(input,file.path(case$id,'matrix.tsv'),'feature_id');table.write(mod,file.path(case$id,'design.tsv'),'sample_id')
 if(isTRUE(case$null))table.write(mod0,file.path(case$id,'null_design.tsv'),'sample_id')
 if(isTRUE(case$control))table.write(matrix(controls,ncol=1,dimnames=list(rownames(dat),'probability')),file.path(case$id,'controls.tsv'),'feature_id')
 # Fit the exact written fixture bytes, including their decimal precision.
 matrix.read<-function(path)as.matrix(read.delim(path,row.names=1,check.names=FALSE,quote='',comment.char='',encoding='UTF-8'))
 input<-matrix.read(file.path(case$id,'matrix.tsv'));design.input<-matrix.read(file.path(case$id,'design.tsv'))
 request<-case;request$id<-request$null<-request$control<-NULL;request$units<-if(case$operation=='SVASEQ')'normalized-counts'else 'continuous-values';request$seed<-1729L
 jsonlite::write_json(request,file.path(case$id,'request-core.json'),auto_unbox=TRUE,pretty=TRUE)
 call<-list(dat=input,mod=design.input)
 if(case$operation=='NUM_SV'){call$method<-case$num_sv_method;if(!is.null(case$permutations))call$B<-case$permutations;fun<-sva::num.sv}
 else {call$mod0<-if(isTRUE(case$null))matrix.read(file.path(case$id,'null_design.tsv'))else NULL;call$n.sv<-if(identical(case$n_sv,'auto'))NULL else case$n_sv;call$method<-case$method;if(isTRUE(case$control))call$controls<-as.vector(matrix.read(file.path(case$id,'controls.tsv')));if(!is.null(case$iterations))call$B<-case$iterations;if(!is.null(case$num_sv_method))call$numSVmethod<-case$num_sv_method;if(!is.null(case$constant))call$constant<-case$constant;fun<-if(case$operation=='SVA')sva::sva else sva::svaseq}
 if(!is.null(case$vfilter))call$vfilter<-case$vfilter
 set.seed(request$seed);before<-.Random.seed;observed<-character();error<-NULL
 console<-capture.output(result<-tryCatch(withCallingHandlers(do.call(fun,call),warning=function(w){observed<<-c(observed,conditionMessage(w));invokeRestart('muffleWarning')}),error=function(e){error<<-conditionMessage(e);NULL}))
 saveRDS(list(input=call,result=result,rng_before=before,rng_after=.Random.seed),file.path(case$id,'original.rds'),compress=FALSE)
 writeLines(c(console,observed,if(!is.null(error))error),file.path(case$id,'original-console.txt'))
 status<-if(is.null(error))'COMPLETE'else 'ORIGINAL_ERROR_NO_RESULT'
 values<-if(case$operation=='NUM_SV')list(n.sv=result)else result
 if(is.null(error)){
  meta<-lapply(values,function(x)list(class=as.list(class(x)),typeof=typeof(x),length=length(x),dim=if(is.null(dim(x)))NULL else as.list(dim(x))))
  jsonlite::write_json(meta,file.path(case$id,'original-shapes.json'),auto_unbox=TRUE,pretty=TRUE,null='null')
  rows<-'component\tindex\tvalue';for(name in names(values)){x<-values[[name]];if(length(x))rows<-c(rows,paste(name,seq_along(x),if(is.logical(x))as.character(as.vector(x))else number(as.vector(x)),sep='\t'))};writeLines(rows,file.path(case$id,'original-components.tsv'))
  if(any(vapply(values,function(x)anyNA(x)||is.numeric(x)&&any(!is.finite(x)),TRUE)))status<-'ORIGINAL_UNDEFINED_NO_RESULT'
 }
 ledger[[length(ledger)+1L]]<-list(id=case$id,status=status,error=error)
 cat(case$id,status,'\n')
}
jsonlite::write_json(ledger,'CASES.json',auto_unbox=TRUE,pretty=TRUE,null='null')
