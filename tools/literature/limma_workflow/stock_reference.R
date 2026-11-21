# SPDX-License-Identifier: GPL-3.0-or-later
# Independent direct original namespace controls. Does not source the adapter worker.
args<-commandArgs(TRUE);stopifnot(length(args)==2L);root<-normalizePath(args[1],winslash='/');out<-args[2]
.libPaths(c(readLines(file.path(root,'worker/runtime-paths.txt'),warn=FALSE,encoding='UTF-8'),.Library),include.site=FALSE)
stopifnot(as.character(getRversion())=='4.6.1',as.character(packageVersion('limma'))=='3.68.5',as.character(packageVersion('edgeR'))=='4.10.7',as.character(packageVersion('statmod'))=='1.5.2')
r<-jsonlite::fromJSON(file.path(root,'effective-request.json'),simplifyVector=FALSE)
input<-function(key)file.path(root,r[[key]])
read.matrix<-function(key) {
 header<-readLines(input(key),n=1L,warn=FALSE,encoding='UTF-8');n<-length(strsplit(header,'\t',fixed=TRUE)[[1]])-1L
 as.matrix(read.delim(input(key),row.names=1L,colClasses=c('character',rep('numeric',n)),na.strings=character(),quote='',comment.char='',check.names=FALSE,encoding='UTF-8'))
}
read.text<-function(key)read.delim(input(key),colClasses='character',na.strings=character(),quote='',comment.char='',check.names=FALSE,encoding='UTF-8')
factor.input<-function(key)if(is.null(r[[key]]))NULL else factor(read.text(key)[[key]],levels=as.character(unlist(r[[paste0(key,'_levels')]])))
seed<-as.integer(r$seed);RNGkind('Mersenne-Twister','Inversion','Rejection');set.seed(seed)
original<-list();warnings<-messages<-character()
compute<-function() {
 op<-r$operation
 if(op=='CAMERAPR') {
  s<-read.text('statistics');statistic<-setNames(as.numeric(s$statistic),s$feature_id);g<-read.text('gene_sets');index<-setNames(lapply(unique(g$set_id),function(id)match(g$feature_id[g$set_id==id],s$feature_id)),unique(g$set_id))
  c<-r$camera;original$camera<<-limma::cameraPR(statistic,index=index,use.ranks=c$use_ranks,inter.gene.cor=as.numeric(unlist(c$inter_gene_cor)),sort=c$sort,directional=c$directional);return(invisible(NULL))
 }
 x<-read.matrix('matrix');design<-if(!is.null(r$design))read.matrix('design') else NULL;w<-if(!is.null(r$weights))read.matrix('weights') else NULL;block<-factor.input('block')
 if(op=='NORMALIZE'){original$normalized<<-limma::normalizeBetweenArrays(x,method=r$normalize_method);return(invisible(NULL))}
 if(op=='BATCH'){original$batch<<-limma::removeBatchEffect(x,batch=factor.input('batch'),batch2=factor.input('batch2'),covariates=if(!is.null(r$covariates))read.matrix('covariates') else NULL,design=design,weights=w);return(invisible(NULL))}
 rho<-NULL;v<-NULL;fit<-NULL
 if(op%in%c('VOOM','EDGER_VOOM_LMFIT')) {
  lib<-if(!is.null(r$libraries))as.numeric(read.text('libraries')$library_size) else colSums(x);y<-edgeR::DGEList(counts=x,lib.size=lib);n<-r$normalization
  if(n$method=='provided')y$samples$norm.factors<-as.numeric(read.text('norm_factors')$norm_factor)
  else {
   a<-list(object=y,method=n$method)
   if(n$method%in%c('TMM','TMMwsp')){a$logratioTrim<-n$logratio_trim;a$sumTrim<-n$sum_trim;a$doWeighting<-n$do_weighting;a$Acutoff<-n$a_cutoff;if(!is.null(n$reference_column))a$refColumn<-n$reference_column}
   if(n$method=='upperquartile')a$p<-n$p;y<-do.call(edgeR::normLibSizes,a)
  }
  original$normalized_counts<<-y;z<-r$voom
  if(op=='EDGER_VOOM_LMFIT') {
   fit<-edgeR::voomLmFit(y,design=design,block=block,prior.weights=w,sample.weights=z$sample_weights,normalize.method=z$normalize_method,span=z$span,adaptive.span=z$adaptive_span,plot=FALSE,save.plot=TRUE,keep.EList=TRUE)
  }else {
   c<-r$correlation;if(c$mode=='fixed')rho<-c$value
   v<-limma::voom(y,design=design,block=if(is.null(rho))NULL else block,correlation=rho,weights=w,normalize.method=z$normalize_method,span=z$span,adaptive.span=z$adaptive_span,plot=FALSE,save.plot=TRUE)
   if(c$mode=='estimate') {
    dc<-limma::duplicateCorrelation(v,design=design,block=block,trim=c$trim);original$correlation<<-dc;rho<-dc$consensus.correlation
    if(c$voom_passes==2L)v<-limma::voom(y,design=design,block=block,correlation=rho,weights=w,normalize.method=z$normalize_method,span=z$span,adaptive.span=z$adaptive_span,plot=FALSE,save.plot=TRUE)
   }
   original$voom<<-v;fit<-limma::lmFit(v,design=design,block=block,correlation=rho)
  }
 }else {
  c<-r$correlation;if(c$mode=='fixed')rho<-c$value
  if(c$mode=='estimate'){dc<-limma::duplicateCorrelation(x,design=design,block=block,trim=c$trim,weights=w);original$correlation<<-dc;rho<-dc$consensus.correlation}
  fit<-limma::lmFit(x,design=design,weights=w,block=block,correlation=rho)
 }
 original$fit<<-fit
 contrast<-do.call(cbind,lapply(r$contrasts,function(x)as.numeric(unlist(x$values))));rownames(contrast)<-colnames(design);colnames(contrast)<-vapply(r$contrasts,function(x)x$id,'')
 fit<-limma::contrasts.fit(fit,contrasts=contrast);original$contrasted<<-fit;m<-r$moderation
 trend<-switch(m$trend,none=FALSE,Amean=TRUE,provided=as.numeric(read.text('trend_covariate')$covariate))
 legacy<-switch(m$legacy,auto=NULL,'true'=TRUE,'false'=FALSE)
 a<-list(fit=fit,trend=trend,robust=m$robust,legacy=legacy);if(m$robust)a$winsor.tail.p<-as.numeric(unlist(m$winsor_tail_p))
 if(m$method=='TREAT'){a$lfc<-m$lfc;a$upshot<-FALSE;fit<-do.call(limma::treat,a)}
 else {a$proportion<-m$proportion;a$stdev.coef.lim<-as.numeric(unlist(m$stdev_coef_lim));if(!is.null(m$span))a$span<-m$span;fit<-do.call(limma::eBayes,a)}
 original$moderated<<-fit;adjusted<-fit$p.value
 if(r$adjustment_scope=='all-contrasts')adjusted[]<-p.adjust(as.numeric(fit$p.value),method=r$adjust_method)
 else for(j in seq_len(ncol(adjusted)))adjusted[,j]<-p.adjust(fit$p.value[,j],method=r$adjust_method)
 original$adjusted<<-adjusted
}
withCallingHandlers(compute(),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')},message=function(m){messages<<-c(messages,conditionMessage(m));invokeRestart('muffleMessage')})
# Read adapter returns only after all independent original computations complete.
saveRDS(original,file.path(out,'original-objects.rds'),version=3,compress=FALSE)
actual<-readRDS(file.path(root,'worker/objects.rds'))
get.actual<-function(label){i<-which(endsWith(names(actual),paste0('-',label)));stopifnot(length(i)==1L);actual[[i]]}
comparisons<-list()
compare<-function(name,wanted,observed) {
 check<-all.equal(observed,wanted,tolerance=0,check.attributes=TRUE)
 comparisons[[name]]<<-list(identical=identical(observed,wanted),all_equal=identical(check,TRUE),detail=if(identical(check,TRUE))NULL else check)
}
if(r$operation=='CAMERAPR'){compare('cameraPR-complete-return',original$camera,get.actual('camera-original'))
}else if(r$operation=='BATCH'){compare('removeBatchEffect-complete-return',original$batch,get.actual('batch-corrected-original'))
}else if(r$operation=='NORMALIZE'){compare('normalizeBetweenArrays-complete-return',original$normalized,get.actual('normalized-original'))
}else {
 compare('lmFit-complete-return',original$fit,get.actual(if(r$operation=='LM')'lmFit-original' else if(r$operation=='VOOM')'lmFit-voom-original' else 'edgeR-voomLmFit-original'))
 compare('contrasts-fit-complete-return',original$contrasted,get.actual('contrasts-fit-original'))
 compare('moderation-complete-return',original$moderated,get.actual('moderated-original'))
 compare('adjustment-complete-matrix',original$adjusted,get.actual('adjusted-p-values'))
 if(!is.null(original$normalized_counts))compare('DGEList-normalized-complete-return',original$normalized_counts,get.actual('DGEList-normalized-original'))
 if(!is.null(original$voom))compare('voom-complete-return',original$voom,get.actual(if(r$correlation$mode=='estimate'&&r$correlation$voom_passes==2L)'voom-original-pass2' else 'voom-original-pass1'))
}
jsonlite::write_json(list(status=if(all(vapply(comparisons,function(v)v$all_equal,TRUE)))'PASS_INDEPENDENT_DIRECT_STOCK_COMPLETE_OBJECTS' else 'FAILED_ORIGINAL_COMPARISON',operation=r$operation,comparisons=comparisons,warnings=as.list(warnings),messages=as.list(messages),adapter_worker_sourced=FALSE,tolerance=0),file.path(out,'COMPLETED.json'),pretty=TRUE,auto_unbox=TRUE,null='null')
stopifnot(all(vapply(comparisons,function(v)v$all_equal,TRUE)))
