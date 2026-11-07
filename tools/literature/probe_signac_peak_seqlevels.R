# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args<-commandArgs(TRUE);stopifnot(length(args)==1L,!dir.exists(args[[1]]));out<-args[[1]];dir.create(out)
.libPaths(c(normalizePath('build/literature-review/signac-runtime/reference-setup-1/R-lib'),normalizePath('build/omics-sctransform-r'),.Library),include.site=FALSE)
ns<-asNamespace('GenomeInfoDb');stopifnot(requireNamespace('GenomicRanges',quietly=TRUE))
found<-character()
for(name in c('rankSeqlevels','sortSeqlevels','.rankSeqlevels','.extractChromNamePrefix','.orderSeqlevels','orderSeqlevels'))if(exists(name,envir=ns,inherits=TRUE)){
 f<-get(name,envir=ns,inherits=TRUE);writeLines(deparse(f,width.cutoff=500),file.path(out,paste0(name,'.R')));found<-c(found,name)
 if(is.function(f)&&isNamespace(environment(f))) {
  owner<-environment(f);writeLines(environmentName(owner),file.path(out,paste0(name,'-owner.txt')))
  for(helper in unique(all.names(body(f))))if(exists(helper,envir=owner,inherits=FALSE)&&is.function(get(helper,envir=owner,inherits=FALSE)))writeLines(deparse(get(helper,envir=owner,inherits=FALSE),width.cutoff=500),file.path(out,paste0('helper-',helper,'.R')))
 }
}
stopifnot(all(c('rankSeqlevels','sortSeqlevels')%in%found))
writeLines(as.character(packageVersion('GenomeInfoDb')),file.path(out,'GenomeInfoDb-version.txt'))
cat('SOURCE_SEQLEVEL_ORDER_PROBE_COMPLETE\n')
