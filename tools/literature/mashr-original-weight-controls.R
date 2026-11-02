# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args<-commandArgs(TRUE)
stopifnot(length(args)==2)
.libPaths(c(args[1],.Library),include.site=FALSE)
library(mashr)
stopifnot(packageDescription('mashr')$Version=='0.2.79',packageDescription('ashr')$Version=='2.2-63')
root<-args[2];options(digits=17,warn=1)
writeLines(capture.output(sessionInfo()),file.path(root,'session-info.txt'))
for(fn in c('mixEM','mixSQP','set_control_squarem','fixpoint','negpenloglik'))
  writeLines(deparse(get(fn,envir=asNamespace('ashr'))),file.path(root,paste0('original-ashr-',fn,'.R.txt')))
writeLines(deparse(mashr:::optimize_pi),file.path(root,'original-mashr-optimize_pi.R.txt'))
dump<-function(value,path){write.table(matrix(format(as.vector(value),digits=17,scientific=TRUE,trim=TRUE),nrow=1),path,sep='\t',quote=FALSE,row.names=FALSE,col.names=FALSE)}
cases<-read.table(file.path(root,'cases.tsv'),header=TRUE,sep='\t',stringsAsFactors=FALSE)
for(i in seq_len(nrow(cases))){
  spec<-cases[i,];folder<-file.path(root,spec$case)
  ll<-as.matrix(read.table(file.path(folder,'component-loglik.tsv'),sep='\t',header=FALSE))
  centered<-ll-apply(ll,1,max)
  likelihood<-exp(centered);k<-ncol(ll)
  prior<-rep(1,k);if(spec$prior=='nullbiased')prior[1]<-10
  initial<-rep(1/k,k)
  dump(prior,file.path(folder,'prior.tsv'));dump(initial,file.path(folder,'initial.tsv'))
  for(method in c('mixEM','mixSQP')){
    destination<-file.path(folder,method);dir.create(destination);warnings<-character()
    status<-tryCatch(withCallingHandlers({
      control<-if(method=='mixEM')list(tol=1e-12,maxiter=100000,trace=FALSE) else list(verbose=FALSE,eps=1e-12)
      fit<-get(method,envir=asNamespace('ashr'))(matrix_lik=likelihood,prior=prior,pi_init=initial,control=control)
      fromMash<-mashr:::optimize_pi(likelihood,pi_init=initial,prior=prior,optmethod=method,control=control)
      stopifnot(isTRUE(all.equal(as.vector(fromMash),as.vector(fit$pihat),tolerance=1e-10)))
      p<-as.vector(fit$pihat);denominator<-as.vector(likelihood%*%p)
      relativeLoglik<-sum(log(denominator));penalty<-sum((prior[prior>1]-1)*log(p[prior>1]))
      gradient<-colSums(likelihood/denominator);gradient[prior>1]<-gradient[prior>1]+(prior[prior>1]-1)/p[prior>1]
      lambda<-nrow(ll)+sum(prior-1);gap<-max(0,max(gradient)/lambda-1)
      dump(p,file.path(destination,'proportions.tsv'))
      dump(c(relativeLoglik+penalty,gap,lambda),file.path(destination,'objective-gap-lambda.tsv'))
      writeLines(capture.output(dput(fit)),file.path(destination,'original-fit.txt'))
      saveRDS(fit,file.path(destination,'original-fit.rds'),compress='xz')
      'COMPLETED'
    },warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')}),
    error=function(e){writeLines(conditionMessage(e),file.path(destination,'error.txt'));'FAILED'})
    writeLines(warnings,file.path(destination,'warnings.txt'));writeLines(status,file.path(destination,'status.txt'))
  }
}
