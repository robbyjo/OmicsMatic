# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
options(warn=2)
args<-commandArgs(TRUE)
stopifnot(length(args)==3,R.version$major=='4',R.version$minor=='6.1')
.libPaths(c(args[1],.Library),include.site=FALSE)
expected<-read.delim(args[2],colClasses='character',check.names=FALSE)
for(i in seq_len(nrow(expected))){
 package<-expected$package[i]
 if(!requireNamespace(package,quietly=TRUE)){cat(paste0('SKIP3: required package/DLL unavailable: ',package,'\n'),file=stderr());quit(status=3)}
 stopifnot(packageDescription(package)$Version==expected$version[i])
}
library(mashr)
B<-matrix(c(1,-1,2,0),nrow=2)
data<-mash_set_data(B,1)
g<-list(pi=c(.2,.8),Ulist=list(shared=matrix(1,2,2)),grid=1,usepointmass=TRUE)
fit<-mash(data,g=g,fixg=TRUE,verbose=FALSE,outputlevel=3)
mu<-rowSums(B)/3;bf<-exp(rowSums(B)^2/6)/sqrt(3);w<-.8*bf/(.2+.8*bf)
expected_mean<-cbind(w*mu,w*mu)
expected_sd<-sqrt(w*(1/3+mu^2)-(w*mu)^2)
expected_lfsr<-1-w+w*pnorm(-abs(mu)/sqrt(1/3))
stopifnot(max(abs(get_pm(fit)-expected_mean))<1e-10,
 max(abs(get_psd(fit)-cbind(expected_sd,expected_sd)))<1e-10,
 max(abs(get_lfsr(fit)-cbind(expected_lfsr,expected_lfsr)))<1e-10)
writeLines(capture.output(sessionInfo()),file.path(args[3],'session-info.txt'))
write.table(cbind(get_pm(fit),get_psd(fit),get_lfsr(fit)),file.path(args[3],'health.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
writeLines(as.character(Sys.getpid()),file.path(args[3],'R-process-pid.txt'))
