# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent STOCK exported-API comparison, never sources the adapter worker.
# Rscript --vanilla edger_workflow_reference.R REQUEST_JSON CAPTURE NEW_OUT
args<-commandArgs(TRUE);stopifnot(length(args)==3L,!file.exists(args[3]))
dir.create(args[3],recursive=TRUE);out<-normalizePath(args[3],winslash='/',mustWork=TRUE)
request.path<-normalizePath(args[1],winslash='/',mustWork=TRUE)
capture<-normalizePath(args[2],winslash='/',mustWork=TRUE)
# A bootstrap library list is supplied by the qualification harness; no install.
bootstrap<-Sys.getenv('OMICSMATIC_EDGER_REFERENCE_LIBRARIES')
stopifnot(nzchar(bootstrap));.libPaths(c(readLines(bootstrap),.Library),include.site=FALSE)
stopifnot(requireNamespace('jsonlite',quietly=TRUE))
r<-jsonlite::fromJSON(request.path,simplifyVector=FALSE)
resolve<-function(x)if(grepl('^([A-Za-z]:|/)',x))x else file.path(dirname(request.path),x)
.libPaths(c(vapply(r$r_libraries,resolve,''),.Library),include.site=FALSE)
stopifnot(as.character(getRversion())=='4.6.1',requireNamespace('edgeR',quietly=TRUE),
 as.character(packageVersion('edgeR'))==sub('^edgeR-(.*)-R4[.]6[.]1$','\\1',r$profile))
warnings.seen<-messages.seen<-character();original<-list()
main<-function() {
 counts<-as.matrix(read.delim(resolve(r$counts),row.names=1,check.names=FALSE,quote='',comment.char='',na.strings=character(),encoding='UTF-8'))
 # The public Java contract explicitly transports float64 counts, even when
 # their declared units are raw integers. Match that type before original calls.
 storage.mode(counts)<-'double'
 groups<-read.delim(resolve(r$groups),colClasses='character',quote='',comment.char='',na.strings=character(),encoding='UTF-8')
 source.ids<-list(features=rownames(counts),samples=colnames(counts),groups=groups$group)
 levels<-as.character(unlist(r$group_levels));group<-factor(paste0('g',match(groups$group,levels)),levels=paste0('g',seq_along(levels)))
 dimnames(counts)<-list(paste0('f',seq_len(nrow(counts))),paste0('s',seq_len(ncol(counts))))
 design<-if(r$model=='EXACT')NULL else as.matrix(read.delim(resolve(r$design),row.names=1,check.names=FALSE,quote='',comment.char='',na.strings=character(),encoding='UTF-8'))
 if(!is.null(design))dimnames(design)<-list(colnames(counts),paste0('d',seq_len(ncol(design))))
 lib<-if(is.null(r$libraries))colSums(counts) else read.delim(resolve(r$libraries),quote='',comment.char='',na.strings=character(),encoding='UTF-8')$library_size
 y<-edgeR::DGEList(counts=counts,group=group,lib.size=lib)
 norm<-function(z) {
  n<-r$normalization
  if(n$method=='provided'){z$samples$norm.factors<-read.delim(resolve(r$norm_factors),quote='',comment.char='',na.strings=character(),encoding='UTF-8')$norm_factor;return(z)}
  a<-list(object=z,method=n$method)
  if(n$method%in%c('TMM','TMMwsp')) {
   a$refColumn<-n$reference_column;a$logratioTrim<-if(is.null(n$logratio_trim)).3 else n$logratio_trim
   a$sumTrim<-if(is.null(n$sum_trim)).05 else n$sum_trim;a$doWeighting<-if(is.null(n$do_weighting))TRUE else n$do_weighting
   a$Acutoff<-if(is.null(n$a_cutoff))-1e10 else n$a_cutoff
  }
  if(n$method=='upperquartile')a$p<-if(is.null(n$p)).75 else n$p
  do.call(edgeR::calcNormFactors,a)
 }
 keep<-rep(TRUE,nrow(y));names(keep)<-rownames(y)
 filter<-function(z) {
  f<-r$filter;a<-list(y=z,min.count=if(is.null(f$min_count))10 else f$min_count,min.total.count=if(is.null(f$min_total_count))15 else f$min_total_count,
   large.n=if(is.null(f$large_n))10 else f$large_n,min.prop=if(is.null(f$min_prop)).7 else f$min_prop)
  if(f$basis=='group')a$group<-group else a$design<-design
  keep<<-do.call(edgeR::filterByExpr,a)
  z[keep,,keep.lib.sizes=isTRUE(f$keep_library_sizes)]
 }
 if(r$filter$method=='filterByExpr'&&r$filter$order=='before-normalization')y<-filter(y)
 y<-norm(y)
 if(r$filter$method=='filterByExpr'&&r$filter$order=='after-normalization'){y<-filter(y);if(isTRUE(r$filter$renormalize_after_filter))y<-norm(y)}
 original$source_ids<<-source.ids;original$prepared<<-y;original$keep<<-keep
 original$cpm<<-edgeR::cpm(y,normalized.lib.sizes=TRUE,log=FALSE)
 original$log_cpm<<-edgeR::cpm(y,normalized.lib.sizes=TRUE,log=TRUE,prior.count=2)
 d<-r$dispersion;f<-r$fit;dispersion<-NULL
 if(d$method=='estimateDisp') {
  a<-list(y=y,design=design)
  mapping<-c(trend_method='trend.method',tagwise='tagwise',min_row_sum='min.row.sum',grid_length='grid.length',robust='robust',tolerance='tol',prior_df='prior.df',span='span')
  for(field in names(mapping))if(!is.null(d[[field]]))a[[mapping[[field]]]]<-d[[field]]
  if(!is.null(d$grid_range))a$grid.range<-as.numeric(unlist(d$grid_range))
  if(!is.null(d$winsor_tail_p))a$winsor.tail.p<-as.numeric(unlist(d$winsor_tail_p))
  y<-do.call(edgeR::estimateDisp,a);original$estimated<<-y
 }else if(d$method=='provided')dispersion<-d$value
 if(r$model=='GLM_QLF'&&d$method=='estimateDisp')dispersion<-if(f$dispersion_selector=='trended')y$trended.dispersion else y$common.dispersion
 prior<-if(is.null(f$prior_count)).125 else f$prior_count
 if(r$model=='GLM_LRT')fit<-edgeR::glmFit(y,design=design,dispersion=dispersion,prior.count=prior)
 else if(r$model=='GLM_QLF') {
  a<-list(y=y,design=design,dispersion=dispersion,prior.count=prior)
  if(!is.null(f$abundance_trend))a$abundance.trend<-f$abundance_trend
  if(!is.null(f$robust))a$robust<-f$robust
  if(!is.null(f$winsor_tail_p))a$winsor.tail.p<-as.numeric(unlist(f$winsor_tail_p))
  if(as.character(packageVersion('edgeR'))=='4.10.7')a$legacy<-f$ql_mode=='legacy'
  if(!is.null(f$top_proportion))a$top.proportion<-f$top_proportion
  fit<-do.call(edgeR::glmQLFit,a)
 }else fit<-NULL
 original$fit<<-fit;original$tests<<-list();original$top_tables<<-list()
 for(i in seq_along(r$results)) {
  selector<-r$results[[i]]
  if(r$model=='EXACT') {
   if(d$method=='estimateDisp')dispersion<-if(is.null(f$dispersion_selector))'auto' else f$dispersion_selector
   a<-list(object=y,pair=paste0('g',match(unlist(selector$pair),levels)),dispersion=dispersion,prior.count=prior)
   if(!is.null(f$rejection_region))a$rejection.region<-f$rejection_region
   if(!is.null(f$big_count))a$big.count<-f$big_count
   test<-do.call(edgeR::exactTest,a)
  }else {
   a<-list(glmfit=fit)
   if(!is.null(selector$coefficients))a$coef<-as.integer(unlist(selector$coefficients)) else a$contrast<-as.numeric(unlist(selector$contrast))
   if(r$model=='GLM_QLF'){if(!is.null(f$poisson_bound))a$poisson.bound<-f$poisson_bound;test<-do.call(edgeR::glmQLFTest,a)} else test<-do.call(edgeR::glmLRT,a)
  }
  original$tests[[i]]<<-test
  original$top_tables[[i]]<<-edgeR::topTags(test,n=Inf,adjust.method=if(is.null(r$adjust_method))'BH' else r$adjust_method,sort.by='none',p.value=1)$table
 }
 invisible(TRUE)
}
RNGkind('Mersenne-Twister','Inversion','Rejection');set.seed(if(is.null(r$seed))1 else r$seed);rng.before<-.Random.seed
withCallingHandlers(main(),warning=function(w){warnings.seen<<-c(warnings.seen,conditionMessage(w));invokeRestart('muffleWarning')},message=function(m){messages.seen<<-c(messages.seen,conditionMessage(m));invokeRestart('muffleMessage')})
rng.after<-.Random.seed
saveRDS(list(original=original,warnings=warnings.seen,messages=messages.seen,rng_before=rng.before,rng_after=rng.after),file.path(out,'original-reference.rds'),version=3)
writeLines(capture.output(sessionInfo()),file.path(out,'SESSION.txt'))
actual<-readRDS(file.path(capture,'worker/objects.rds'));comparisons<-list()
check<-function(label,a,b){same<-identical(a,b);comparisons[[length(comparisons)+1L]]<<-list(label=label,identical=same);if(!same)stop(paste('Independent STOCK original object differs:',label,paste(capture.output(all.equal(a,b,tolerance=0)),collapse=' ')))}
check('complete prepared DGEList',actual$prepared,original$prepared)
check('filter mask',if(is.null(actual$keep))setNames(rep(TRUE,nrow(original$prepared)),rownames(original$prepared)) else actual$keep,original$keep)
if(!is.null(original$estimated))check('complete estimateDisp DGEList',actual$estimated,original$estimated)
if(!is.null(original$fit))check('complete original full fit',actual$fit,original$fit)
for(i in seq_along(original$tests)) {
 check(paste('complete STOCK original test',i),actual$tests[[i]],original$tests[[i]])
 check(paste('complete original topTags table',i),actual$top_tables[[i]],original$top_tables[[i]])
}
check('RNG before',readRDS(file.path(capture,'worker/rng-before.rds')),rng.before)
check('RNG after',readRDS(file.path(capture,'worker/rng-after.rds')),rng.after)
result<-list(status='PASS_INDEPENDENT_STOCK_EDGER_COMPLETE_MODELS_TESTS_TABLES_AND_RNG_IDENTICAL',profile=r$profile,model=r$model,numeric_transport='Explicit declared float64 before original calls',
 comparisons=comparisons,maximum_numeric_error=0,source_worker_loaded=FALSE,stock_namespace_modified=FALSE,
 observational_test_environment_numeric_equivalence=TRUE,original_reference='original-reference.rds',warnings=as.list(warnings.seen),messages=as.list(messages.seen))
jsonlite::write_json(result,file.path(out,'COMPLETED.json'),auto_unbox=TRUE,pretty=TRUE,null='null',digits=NA)
cat('PASS independent STOCK edgeR full objects:',r$profile,r$model,length(comparisons),'exact comparisons\n')
