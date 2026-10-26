# Rebuild independent fixtures with CRAN logistf 1.26.1 (not JLinAlg).
# Usage: Rscript --vanilla src/test/R/firth-logistic-reference.R OUTPUT [REFERENCE_ROOT]
# REFERENCE_ROOT contains binary/logistf and matching source/logistf extracted
# from the official Windows binary ZIP and source tarball. This exercises the
# upstream numerical fitter directly without installing its optional workflows.
args <- commandArgs(TRUE); stopifnot(length(args)>=1)
out <- args[1]; dir.create(out,recursive=TRUE,showWarnings=FALSE)
if(length(args)>=2) {
  reference <- args[2]
  dyn.load(file.path(reference,'binary/logistf/libs/x64/logistf.dll'))
  for(name in c('logistf.control.R','logistf.mod.control.R','logistf.fit.R'))
    source(file.path(reference,'source/logistf/R',name))
  version <- read.dcf(file.path(reference,'source/logistf/DESCRIPTION'))[1,'Version']
} else {
  stopifnot(requireNamespace('logistf',quietly=TRUE))
  logistf.fit <- getFromNamespace('logistf.fit','logistf')
  logistf.control <- logistf::logistf.control
  logistf.mod.control <- logistf::logistf.mod.control
  version <- as.character(packageVersion('logistf'))
}
stopifnot(version=='1.26.1')
set.seed(81743)
fixtures <- list()
add <- function(name,y,x,offset=rep(0,length(y)),null=0) {
  fixtures[[name]] <<- list(y=y,x=x,offset=offset,null=null)
}
n<-120;z<-rnorm(n);g<-rbinom(n,2,.2);y<-rbinom(n,1,plogis(-.3+.25*z+.4*g));add('balanced',y,cbind(1,z,g))
g<-c(rep(1,24),rep(0,96));add('complete-separation',g,cbind(1,g))
z<-rep(c(-1,1),60);g<-rbinom(n,2,.2);add('nuisance-separation',as.integer(z>0),cbind(1,z,g))
n<-1000;g<-c(rep(1,40),rep(0,960));z<-rnorm(n);y<-rep(0,n);y[c(1,7,99,333,701)]<-1;add('imbalanced',y,cbind(1,z,g))
n<-120;g<-c(rep(1,30),rep(0,90));y<-g;y[2]<-0;add('quasi-separation',y,cbind(1,g))
z<-rnorm(n);g<-rbinom(n,2,.15);offset<-.4+.7*sin(seq_len(n));y<-rbinom(n,1,plogis(-.8+.3*z+.6*g+offset));add('offset',y,cbind(1,z,g),offset)
n<-160;z<-rnorm(n);g<-runif(n,0,2);y<-rbinom(n,1,plogis(-1+.4*z+.5*g));add('dosage',y,cbind(1,z,g))
add('scaled',y,cbind(1,z*1e6,g*1e-6))
add('nonzero-null',y,cbind(1,z,g),null=.5)
results<-list()
control<-logistf.control(maxit=1000,maxhs=0,maxstep=5,lconv=1e-10,gconv=1e-8,xconv=1e-8,collapse=FALSE,fit='IRLS')
restricted.control<-logistf.control(maxit=1000,maxhs=0,maxstep=5,lconv=1e-10,gconv=1e-8,xconv=1e-8,collapse=FALSE,fit='NR')
for(name in names(fixtures)) {
  f<-fixtures[[name]];p<-ncol(f$x)
  # Exact reparameterization of dosage: scale products are one, so log|I|
  # and both penalized likelihoods are unchanged. Avoid changing upstream
  # optimizer tolerances merely because the parameter units differ by 1e12.
  reference.x<-if(name=='scaled')fixtures[['dosage']]$x else f$x
  fit<-logistf.fit(reference.x,f$y,offset=f$offset,control=control,modcontrol=logistf.mod.control())
  init<-fit$beta;init[p]<-f$null
  restricted<-logistf.fit(reference.x,f$y,offset=f$offset,init=init,control=restricted.control,modcontrol=logistf.mod.control(terms.fit=seq_len(p-1)))
  if(fit$iter>=1000||restricted$iter>=1000)stop(paste('Reference did not converge:',name))
  lr<-2*(fit$loglik-restricted$loglik);stopifnot(lr>=-1e-8)
  input<-data.frame(y=f$y,offset=f$offset,f$x);names(input)[-(1:2)]<-paste0('x',seq_len(p))
  options(digits=17)
  write.table(input,file.path(out,paste0(name,'.tsv')),sep='\t',row.names=FALSE,quote=FALSE)
  units<-if(name=='scaled')1e6 else 1
  mu<-plogis(drop(reference.x%*%fit$beta)+f$offset)
  ordinary.covariance<-solve(crossprod(reference.x*sqrt(mu*(1-mu))))
  results[[name]]<-data.frame(case=name,columns=p,beta=fit$beta[p]*units,se=sqrt(ordinary.covariance[p,p])*units,
    full_pll=fit$loglik,null_pll=restricted$loglik,lr=max(0,lr),p_value=pchisq(max(0,lr),1,lower.tail=FALSE),
    log_p=pchisq(max(0,lr),1,lower.tail=FALSE,log.p=TRUE),null_value=f$null,
    full_iterations=fit$iter,null_iterations=restricted$iter,upstream_irls_augmented_se=sqrt(fit$var[p,p])*units)
}
write.table(do.call(rbind,results),file.path(out,'reference.tsv'),sep='\t',row.names=FALSE,quote=FALSE)
writeLines(c(paste('Reference: CRAN logistf',version,'upstream numerical fitter'),R.version.string,
             'Full-design Jeffreys penalty retained during constrained optimization; individual Bernoulli outcomes; unit weights.',
             'SE is independently inverted ordinary Fisher information at the upstream penalized estimate; the upstream IRLS augmented covariance is retained separately.',
             'Upstream IRLS full / NR constrained, collapse=FALSE, maxhs=0 (upstream default). Scaled case is the exact diagonal reparameterization of dosage; no change in penalty determinant.'),file.path(out,'PROVENANCE.txt'))
cat(length(results),'independent reference cases written\n')
