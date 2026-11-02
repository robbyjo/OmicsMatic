# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Calls original mashr on previously captured synthetic source fits. No namespace patching.
args<-commandArgs(TRUE);stopifnot(length(args)==3)
.libPaths(c(args[1],.Library),include.site=FALSE);library(mashr)
stopifnot(packageDescription('mashr')$Version=='0.2.79',packageDescription('ashr')$Version=='2.2-63')
parent<-args[2];root<-args[3];options(digits=17,warn=1)
dump<-function(x,path){if(is.null(dim(x)))x<-matrix(x,nrow=1);write.table(matrix(format(as.vector(x),digits=17,scientific=TRUE,trim=TRUE),nrow=nrow(x)),path,sep='\t',quote=FALSE,row.names=FALSE,col.names=FALSE)}
readm<-function(path)as.matrix(read.table(path,sep='\t',header=FALSE))
names<-readLines(file.path(root,'cases.txt'))
for(name in names){
 source<-file.path(parent,name,'outputs');destination<-file.path(root,name);dir.create(destination)
 bhat<-readm(file.path(source,'training-bhat.tsv'));se<-readm(file.path(source,'training-shat.tsv'))
 settings<-read.delim(file.path(source,'settings.tsv'),colClasses='character');alpha<-as.numeric(settings$value[settings$key=='alpha'])
 fit<-readRDS(file.path(source,'original-training-fit.rds'))
 data<-mash_set_data(bhat,se,alpha=alpha,V=readm(file.path(source,'model/residual.tsv')))
 # The original E_V uses its own posterior means/covariances and cross-products.
 dump(mashr:::E_V(data,fit)/nrow(bhat),file.path(destination,'original-expected-noise.tsv'))
 dump((bhat-fit$result$PosteriorMean)/se,file.path(destination,'original-standardized-residual-mean.tsv'))
}
# Singleton reference: original vector likelihoods and original scalar conditional
# functions avoid the known high-level wrapper dimension bugs without padding data.
destination<-file.path(root,'singleton');dir.create(destination)
b<-c(10,10);se<-c(1,1);v<-diag(2);u<-list(null=matrix(0,2,2),shared=matrix(100,2,2))
ll<-as.vector(mashr:::calc_lik_vector(b,v,u,log=TRUE));relative<-matrix(exp(ll-max(ll)),nrow=1)
fit<-ashr::mixSQP(relative,prior=c(10,1),pi_init=c(.5,.5),control=list(verbose=FALSE,eps=1e-12,convtol.sqp=1e-12))
pi<-as.vector(fit$pihat);w<-as.vector(pi*exp(ll-max(ll)));w<-w/sum(w)
cv<-lapply(u,function(x)mashr:::posterior_cov(solve(v),x));mu<-lapply(cv,function(x)as.vector(mashr:::posterior_mean(b,solve(v),x)))
mean<-Reduce('+',Map(function(p,m)p*m,w,mu));cov<-Reduce('+',Map(function(p,c,m)p*(c+tcrossprod(m-mean)),w,cv,mu))
negative<-zero<-numeric(2)
for(k in seq_along(u))for(j in 1:2)if(cv[[k]][j,j]==0)zero[j]<-zero[j]+w[k] else negative[j]<-negative[j]+w[k]*pnorm(0,mu[[k]][j],sqrt(cv[[k]][j,j]))
dump(pi,file.path(destination,'original-proportions.tsv'));dump(ll,file.path(destination,'original-component-loglik.tsv'))
dump(mean,file.path(destination,'original-mean.tsv'));dump(sqrt(diag(cov)),file.path(destination,'original-sd.tsv'));dump(cov,file.path(destination,'original-covariance.tsv'))
dump(negative,file.path(destination,'original-negative.tsv'));dump(zero,file.path(destination,'original-zero.tsv'));dump(ashr::compute_lfsr(negative,zero),file.path(destination,'original-lfsr.tsv'))
# Retain the original second-moment cancellation at a representable large mean.
destination<-file.path(root,'large-mean-boundary');dir.create(destination)
bhat<-matrix(c(1e100,0,0,1e100,1e100,1e100),ncol=2,byrow=TRUE)
u<-diag(1e200,2);noise<-diag(2);conditional<-mashr:::posterior_cov(solve(noise),u)
mu<-mashr:::posterior_mean_matrix(bhat,solve(noise),conditional)
model<-list(result=list(PosteriorMean=mu,PosteriorCov=array(rep(conditional,nrow(bhat)),dim=c(2,2,nrow(bhat)))))
data<-mash_set_data(bhat,matrix(1,nrow(bhat),2))
dump(mashr:::E_V(data,model)/nrow(bhat),file.path(destination,'original-expected-noise.tsv'))
dump(conditional,file.path(destination,'original-conditional-covariance.tsv'))
dump(bhat-mu,file.path(destination,'subtracted-residual-mean.tsv'))
writeLines(capture.output(sessionInfo()),file.path(root,'session-info.txt'))
