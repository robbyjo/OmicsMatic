# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Execute original functions only; preserve every argument, returned object and condition.
args <- commandArgs(trailingOnly=TRUE)
stopifnot(length(args)==3L)
lib <- normalizePath(args[1],mustWork=TRUE)
base <- normalizePath(args[2],mustWork=TRUE)
out <- args[3]
stopifnot(!file.exists(out));dir.create(out,recursive=TRUE)
.libPaths(c(lib,base));suppressPackageStartupMessages(library(AllelicSeries,lib.loc=lib))
stopifnot(as.character(getRversion())=='4.5.3',as.character(packageVersion('AllelicSeries',lib.loc=lib))=='0.1.1.2')
options(digits=17)
capture <- function(f) {
  warnings <- list()
  value <- tryCatch(withCallingHandlers(f(),warning=function(w) {
    warnings[[length(warnings)+1L]] <<- w;invokeRestart('muffleWarning')
  }),error=function(e)e)
  list(value=value,warnings=warnings,error=inherits(value,'error'))
}
csv <- function(x) if(is.null(x)) 'NULL' else paste(sprintf('%.17g',as.numeric(x)),collapse=',')
row <- function(x) paste(x,collapse='\t')
set.seed(780321)
cases <- list()
for (i in seq_len(512)) {
  categories <- 1L + i %% 5L
  n <- categories + 2L + i %% 19L
  anno <- sample(rep(seq_len(categories),length.out=n))
  b <- rnorm(n,sd=if(i%%9L==0L)0 else 0.1)
  se <- exp(runif(n,-2,1))
  mode <- i%%8L
  rho <- c(-.7,-.2,0,.2,.7)[1L+i%%5L]
  ld <- outer(seq_len(n),seq_len(n),function(a,b)rho^abs(a-b))
  if(mode==1L)ld <- NULL
  if(mode==2L)ld <- matrix(1,n,n)
  if(mode==3L)ld <- diag(n)*1e-9
  if(mode==4L)ld <- diag(n)*0
  if(mode==5L)ld[1,2] <- ld[1,2]+0.01
  weights <- as.numeric(seq_len(categories))
  if(i%%11L==0L)weights <- rep(0,categories)
  if(i%%13L==0L)weights <- -weights
  if(i%%17L==0L && categories>1L)anno[anno==categories] <- 1L
  if(i%%19L==0L)anno <- anno-1L
  cases[[length(cases)+1L]] <- list(id=paste0('grid-',i),kind='wrapper',anno=anno,beta=b,se=se,ld=ld,weights=weights,method=if(i%%2L)'none' else 'sum',eps=if(mode==2L)0 else 1,lambda=c(.8,1,1.2,2,Inf)[1L+i%%5L])
}
for (kind in c('wrapper','baseline','sum')) for (edge in c('all-zero-se','mixed-zero-se','all-negative-se','mixed-negative-se','missing-beta','missing-se','infinite-beta','infinite-se','missing-category')) {
  c <- list(id=paste(kind,edge,sep='-'),kind=kind,anno=1:3,beta=c(.1,-.2,.3),se=rep(.2,3),ld=diag(3),weights=c(1,2,3),method=if(kind=='sum')'sum' else 'none',eps=1,lambda=1)
  if(edge=='all-zero-se')c$se[]<-0
  if(edge=='mixed-zero-se')c$se[1]<-0
  if(edge=='all-negative-se')c$se[]<--1
  if(edge=='mixed-negative-se')c$se[1]<--1
  if(edge=='missing-beta')c$beta[1]<-NA_real_
  if(edge=='missing-se')c$se[1]<-NA_real_
  if(edge=='infinite-beta')c$beta[1]<-Inf
  if(edge=='infinite-se')c$se[1]<-Inf
  if(edge=='missing-category')c$anno[]<-1L
  cases[[length(cases)+1L]]<-c
}
inputs <- character();results <- character();objects <- list()
for (c in cases) {
  inputs <- c(inputs,row(c(c$id,c$kind,csv(c$anno),csv(c$beta),csv(c$se),if(is.null(c$ld))'NULL' else csv(t(c$ld)),csv(c$weights),c$method,csv(c$eps),csv(c$lambda))))
  result <- capture(function() {
    if(c$kind=='wrapper') ASBTSS(anno=c$anno,beta=c$beta,se=c$se,ld=c$ld,weights=c$weights,method=c$method,eps=c$eps,lambda=c$lambda,return_beta=TRUE)
    else if(c$kind=='baseline') BaselineSS(anno=c$anno,beta=c$beta,se=c$se,ld=c$ld,n_anno=length(c$weights),return_beta=TRUE)
    else SumCountSS(anno=c$anno,beta=c$beta,se=c$se,ld=c$ld,weights=c$weights,return_beta=TRUE)
  })
  objects[[c$id]] <- list(input=c,result=result)
  v<-result$value
  if(result$error)r<-c(c$id,'error','NULL','NULL','NULL')
  else if(is.numeric(v)&&length(v)==1L)r<-c(c$id,'scalar',csv(v),'NULL','NULL')
  else if(c$kind=='wrapper')r<-c(c$id,'effects',csv(v$pval),csv(v$betas$beta),csv(v$betas$se))
  else r<-c(c$id,'effects',csv(v$pval),csv(v$beta),csv(v$se))
  results<-c(results,row(r))
}
writeLines(inputs,file.path(out,'native-inputs.tsv'));writeLines(results,file.path(out,'native-original-results.tsv'))
saveRDS(objects,file.path(out,'complete-native-cases-and-original-objects.rds'),version=3)

# Every original function export is exercised on an explicit retained fixture.
set.seed(994223)
g<-matrix(rbinom(600L*12L,2,.08),600L,12L);a<-rep(1:3,4)
x<-cbind(1,rnorm(600));y<-as.numeric(x%*%c(1,.2)+g%*%rep(c(.1,.2,.3),4)+rnorm(600))
data<-list(anno=a,covar=x,geno=g,pheno=y,type='quantitative')
ss<-CalcSumstats(data=data)
individual<-list(anno=a,geno=g,pheno=y,covar=x)
summary<-list(anno=ss$sumstats$anno,beta=ss$sumstats$beta,se=ss$sumstats$se,ld=ss$ld)
requests<-list(
  ASBT=c(individual,list(return_beta=TRUE)),
  ASBTSS=c(summary,list(return_beta=TRUE)),
  ASKAT=individual,
  ASKATSS=c(summary,list(maf=ss$sumstats$maf)),
  Aggregator=list(anno=a,geno=g),
  BaselineSS=c(summary,list(n_anno=3L,return_beta=TRUE)),
  COAST=individual,
  COASTSS=c(summary,list(maf=ss$sumstats$maf)),
  CalcSumstats=list(data=data),
  Comparator=list(covar=x,geno=g,pheno=y),
  Counts=list(anno=a,geno=g,n_anno=3L),
  DGP=list(n=200L,snps=12L,maf_range=c(.05,.1)),
  GenomicControl=list(lambda=1.3,pval=.0001),
  OLS=list(y=y,X=cbind(g,x)),
  SumCountSS=c(summary,list(weights=c(1,2,3),return_beta=TRUE)),
  isPD=list(x=ss$ld)
)
namespace_results<-list()
dir.create(file.path(out,'namespace-requests'))
for(name in names(requests)) {
  saveRDS(requests[[name]],file.path(out,'namespace-requests',paste0(name,'.rds')),version=3)
  set.seed(88201L)
  namespace_results[[name]]<-capture(function()do.call(getExportedValue('AllelicSeries',name),requests[[name]]))
  saveRDS(namespace_results[[name]],file.path(out,paste0('original-',name,'.rds')),version=3)
}
saveRDS(list(data=data,sumstats=ss,requests=requests,results=namespace_results,seed=88201L),file.path(out,'complete-namespace-reference.rds'),version=3)
writeLines(vapply(names(namespace_results),function(name)paste(name,if(namespace_results[[name]]$error)'error' else 'returned',length(namespace_results[[name]]$warnings),sep='\t'),character(1)),file.path(out,'namespace-results.tsv'))
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
writeLines(paste('native_cases',length(cases),'namespace_exports',length(requests),'namespace_errors',sum(vapply(namespace_results,function(r)r$error,logical(1)))),file.path(out,'COMPLETED.txt'))
cat(readLines(file.path(out,'COMPLETED.txt')),sep='\n')
