# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent original scalar primitives: no high-level singleton wrapper or padding.
args<-commandArgs(TRUE);stopifnot(length(args)==3)
.libPaths(c(args[1],.Library),include.site=FALSE);library(mashr)
stopifnot(packageDescription('mashr')$Version=='0.2.79',packageDescription('ashr')$Version=='2.2-63')
root<-args[2];out<-args[3];stopifnot(!file.exists(out),dir.create(out))
readm<-function(name)as.matrix(read.table(file.path(root,name),header=FALSE,sep='\t',check.names=FALSE))
dump<-function(x,name){if(is.null(dim(x)))x<-matrix(x,nrow=1);stopifnot(all(is.finite(x)));write.table(matrix(format(as.vector(x),digits=17,scientific=TRUE,trim=TRUE),nrow=nrow(x)),file.path(out,name),sep='\t',quote=FALSE,row.names=FALSE,col.names=FALSE)}
settings<-read.delim(file.path(root,'model/settings.tsv'),colClasses='character');settings<-setNames(as.list(settings$value),settings$key)
alpha<-as.numeric(settings$alpha);threshold<-as.numeric(settings$posterior_threshold)
pi<-as.vector(readm('model/proportions.tsv'));K<-length(pi);V<-readm('model/residual.tsv');n<-nrow(V)
U<-lapply(seq_len(K),function(k)readm(paste0('model/component-',k,'.tsv')))
effects<-read.delim(file.path(root,'inputs/effects.tsv'),colClasses='character',check.names=FALSE,quote='',comment.char='',fileEncoding='UTF-8')
errors<-read.delim(file.path(root,'inputs/standard-errors.tsv'),colClasses='character',check.names=FALSE,quote='',comment.char='',fileEncoding='UTF-8')
stopifnot(identical(effects[[1]],errors[[1]]),identical(names(effects),names(errors)))
ids<-effects[[1]];bhat<-matrix(as.numeric(as.matrix(effects[,-1,drop=FALSE])),nrow=nrow(effects));shat<-matrix(as.numeric(as.matrix(errors[,-1,drop=FALSE])),nrow=nrow(errors))
lse<-function(values){m<-max(values);m+log(sum(exp(values-m)))}
likelihood<-function(row){
 s<-shat[row,]^(1-alpha);D<-diag(s,n,n);sigma<-D%*%V%*%D;x<-bhat[row,]/shat[row,]^alpha
 as.vector(mashr:::calc_lik_vector(x,sigma,U,log=TRUE))-alpha*sum(log(shat[row,]))
}
target<-readLines(file.path(root,'target-ids.tsv'));train<-readLines(file.path(root,'training-ids.tsv'))
targetRows<-match(target,ids);trainingRows<-match(train,ids);stopifnot(!anyNA(targetRows),!anyNA(trainingRows))
fields<-list(mean=matrix(0,length(target),n),sd=matrix(0,length(target),n),negative=matrix(0,length(target),n),zero=matrix(0,length(target),n),lfsr=matrix(0,length(target),n),covariance=matrix(0,length(target),n*n),weights=matrix(0,length(target),K),component=matrix(0,length(target),K),likelihood=matrix(0,length(target),3))
for(t in seq_along(targetRows)){
 row<-targetRows[t];ll<-likelihood(row);full<-lse(log(pi)+ll);keep<-pi>threshold;w<-numeric(K);w[keep]<-exp(log(pi[keep])+ll[keep]-lse(log(pi[keep])+ll[keep]))
 scale<-shat[row,]^alpha;s<-shat[row,]^(1-alpha);D<-diag(s,n,n);sigma<-D%*%V%*%D;inverse<-solve(sigma);x<-bhat[row,]/scale
 covariance<-lapply(U,function(u)mashr:::posterior_cov(inverse,u));means<-lapply(covariance,function(c)as.vector(mashr:::posterior_mean(x,inverse,c))*scale)
 covariance<-lapply(covariance,function(c)c*outer(scale,scale));mean<-Reduce('+',Map(function(p,m)p*m,w,means));central<-matrix(0,n,n);negative<-zero<-numeric(n)
 for(k in seq_len(K))if(w[k]>0){
  central<-central+w[k]*(covariance[[k]]+tcrossprod(means[[k]]-mean))
  for(j in seq_len(n))if(covariance[[k]][j,j]==0){stopifnot(means[[k]][j]==0);zero[j]<-zero[j]+w[k]}else{
   stopifnot(covariance[[k]][j,j]>0);negative[j]<-negative[j]+w[k]*pnorm(0,means[[k]][j],sqrt(covariance[[k]][j,j]))
  }
 }
 fields$mean[t,]<-mean;fields$sd[t,]<-sqrt(diag(central));fields$negative[t,]<-negative;fields$zero[t,]<-zero;fields$lfsr[t,]<-ashr::compute_lfsr(negative,zero)
 fields$covariance[t,]<-as.vector(central);fields$weights[t,]<-w;fields$component[t,]<-ll;fields$likelihood[t,]<-c(full,full+alpha*sum(log(shat[row,])),sum(pi[keep]))
}
for(name in names(fields))dump(fields[[name]],paste0('original-',name,'.tsv'))
if(length(trainingRows)>0){
 ll<-t(vapply(trainingRows,likelihood,numeric(K)));dump(ll,'original-training-component.tsv');prior<-as.vector(readm('fit/dirichlet.tsv'));pseudo<-prior-1
 lognorm<-apply(ll,1,function(row)lse(log(pi)+row));offset<-apply(ll,1,max)
 relative<-sum(lognorm-offset);penalty<-sum(pseudo[pseudo>0]*log(pi[pseudo>0]));gradient<-colSums(exp(ll-lognorm))
 gradient[pseudo>0]<-gradient[pseudo>0]+pseudo[pseudo>0]/pi[pseudo>0]
 lambda<-length(trainingRows)+sum(pseudo);gap<-max(0,max(gradient)/lambda-1)
 dump(c(sum(lognorm),relative+penalty,gap,lambda),'original-training-objective-gap.tsv')
}
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
