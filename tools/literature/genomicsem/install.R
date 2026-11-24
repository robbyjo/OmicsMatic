# Copyright (C) 2026 JLinAlg contributors; GPL-3.0-or-later.
# Called only by the explicit installer, never by scientific RUN/VERIFY.
options(warn=1)
a<-commandArgs(trailingOnly=TRUE);stopifnot(length(a)==2)
# No non-base R package is needed before the private dependency installation.
root<-normalizePath(a[1],winslash='/',mustWork=TRUE)
cache<-readLines(a[2],warn=FALSE)
lib<-file.path(root,'library');stopifnot(!dir.exists(lib));dir.create(lib)
.libPaths(c(lib,.Library),include.site=FALSE)
install.packages(cache,lib=lib,repos=NULL,type='win.binary')
Sys.setenv(R_LIBS=lib,R_LIBS_USER=lib,R_LIBS_SITE='')
r<-file.path(R.home('bin'),'R.exe')
for(source in c(file.path(root,'lavaan.tar.gz'),file.path(root,'source'))){
 result<-system2(r,c('CMD','INSTALL',shQuote(paste0('--library=',lib)),shQuote(source)))
 if(result!=0)stop(paste('Private original-source installation failed:',source))
}
library(GenomicSEM,lib.loc=lib)
write.table(installed.packages(lib.loc=c(lib,.Library))[,c('Package','Version','LibPath','Built')],file.path(root,'packages.tsv'),sep='\t',quote=TRUE,row.names=FALSE)
writeLines(capture.output(sessionInfo()),file.path(root,'install-session-info.txt'))
