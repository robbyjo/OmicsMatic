# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original edgeR calls from closed numeric inputs. No user expressions, installs or input RDS.
libraries<-readLines('runtime-paths.txt',encoding='UTF-8',warn=FALSE)
.libPaths(c(libraries,.Library),include.site=FALSE)
observed.warnings<-observed.messages<-adapter.warnings<-character()
objects<-list();guard.names<-character();runtime.ready<-FALSE
report<-list(status='RUNNING_NO_RESULT_ADMITTED',results_admitted=FALSE)
required.action<-'Preserve source inputs, objects.rds, partials, warnings and logs. FAIL2 admits no complete result; correct the named data, design, numeric, convergence or capacity cause and retry NEW output. SKIP3 skips dependent analysis: supply exact permitted R4.6.1 and the declared original edgeR edition/dependencies in private libraries, or retain the skip. No edition, identifiers, rounding, design, threshold or statistical method is substituted.'
unavailable<-function(message)stop(structure(list(message=message,call=NULL),class=c('omics_unavailable','error','condition')))
number<-function(x)vapply(x,function(z)if(is.nan(z))'NaN' else if(is.na(z))'NA' else if(is.infinite(z))if(z>0)'Inf' else '-Inf' else sprintf('%.17g',z),'')
table.out<-function(x,path,ids=NULL,id.name='feature_index') {
 x<-as.data.frame(x,check.names=FALSE)
 values<-lapply(x,function(v)if(is.numeric(v))number(v) else if(is.logical(v))ifelse(is.na(v),'NA',ifelse(v,'TRUE','FALSE')) else as.character(v))
 if(!is.null(ids))values<-c(setNames(list(as.character(ids)),id.name),values)
 write.table(as.data.frame(values,check.names=FALSE,stringsAsFactors=FALSE),path,sep='\t',quote=FALSE,row.names=FALSE,col.names=TRUE,na='NA',fileEncoding='UTF-8')
}
finite<-function(x,label){if(any(!is.finite(x)))stop(paste('Original nonfinite',label,'retained; no complete result'))}
save.objects<-function(){saveRDS(objects,'objects.rds',version=3)}
read.matrix<-function(path)as.matrix(read.delim(path,row.names=1L,check.names=FALSE,colClasses=c('character',rep('numeric',p$samples)),quote='',comment.char=''))
check.fit<-function(fit,label,allow.zero.boundary=FALSE,maximum.iterations=250L) {
 if(!is.null(fit$failed)&&any(is.na(fit$failed)|fit$failed))stop(paste('Original',label,'reported failed GLM genes; object retained'))
 if(identical(fit$method,'levenberg')&&(is.null(fit$iter)||any(!is.finite(fit$iter))||any(fit$iter>maximum.iterations)))stop(paste('Original',label,'exhausted the declared source GLM iteration budget; object retained'))
 bad<-which(rowSums(!is.finite(fit$coefficients))>0)
 if(length(bad)) {
  source.zero<-allow.zero.boundary&&identical(fit$method,'levenberg')&&all(fit$counts[bad,,drop=FALSE]==0)&&
   all(fit$fitted.values[bad,,drop=FALSE]==0)&&all(fit$deviance[bad]==0)&&all(fit$iter[bad]==0)&&
   all(is.na(fit$coefficients[bad,,drop=FALSE]))&&!any(is.nan(fit$coefficients[bad,,drop=FALSE]))
  if(!source.zero)stop(paste('Original nonfinite',label,'coefficients retained; no complete result'))
  # Original glm_levenberg.cpp/c returns NA coefficients, zero means/deviance/
  # iterations and failed=FALSE for all-zero rows. These internal nuisance
  # coefficients are not identifiable. Preserve source NA; never fill with zero.
  objects$internal_boundaries[[length(objects$internal_boundaries)+1L]]<<-list(fit_index=length(objects$internal_fits),feature_indices=rownames(fit$coefficients)[bad],kind='original-all-zero-Levenberg-NA-coefficients')
  save.objects()
  adapter.warnings<<-c(adapter.warnings,paste('Original internal all-zero Levenberg boundary retained:',length(bad),'NA coefficient rows, exact zero counts/means/deviance/iterations, failed=FALSE; no coefficient is imputed.'))
 }
 finite(fit$fitted.values,paste(label,'fitted means'));finite(fit$deviance,paste(label,'deviance'))
 if(any(fit$fitted.values<0))stop('Original negative fitted mean; no complete result')
}
main<-function() {
 if(!requireNamespace('jsonlite',quietly=TRUE))unavailable('Required jsonlite is unavailable in declared libraries')
 p<<-jsonlite::fromJSON('parameters.json',simplifyVector=FALSE)
 version<-sub('^edgeR-(.*)-R4[.]6[.]1$','\\1',p$profile)
 if(as.character(getRversion())!='4.6.1')unavailable('Require exact R4.6.1; no adjacent runtime is substituted')
 if(!requireNamespace('edgeR',quietly=TRUE)||as.character(packageVersion('edgeR'))!=version)unavailable(paste('Require original edgeR',version,'in declared private libraries'))
 if((isTRUE(p$dispersion$robust)||isTRUE(p$fit$robust))&&!requireNamespace('statmod',quietly=TRUE))unavailable('Requested original robust inference requires statmod')
 source.zip<-'../source/reference/source.zip'
 source.files<-unzip(source.zip,list=TRUE)$Name
 source.files<-source.files[startsWith(source.files,paste0(version,'/R/'))&grepl('[.]R$',source.files)]
 definitions<-list()
 for(file in source.files) {
  connection<-unz(source.zip,file,open='r')
  expressions<-tryCatch(parse(connection,keep.source=FALSE),finally=close(connection))
  for(expression in expressions)if(is.call(expression)&&length(expression)==3L&&(identical(expression[[1]],as.name('<-'))||identical(expression[[1]],as.name('=')))&&is.symbol(expression[[2]])&&is.call(expression[[3]])&&identical(expression[[3]][[1]],as.name('function')))
   definitions[[as.character(expression[[2]])]]<-expression[[3]]
 }
 guard.names<<-c('DGEList','validDGEList','calcNormFactors.DGEList','calcNormFactors.default','filterByExpr.DGEList','filterByExpr.default',
  'estimateDisp.DGEList','estimateDisp.default','glmFit.DGEList','glmFit.default','glmQLFit.DGEList','glmQLFit.default','glmQLFTest','glmLRT',
  'mglmLevenberg','mglmOneWay','mglmOneGroup','predFC.default','exactTest','topTags','getOffset','getDispersion','cpm.DGEList','cpm.default')
 ns<-asNamespace('edgeR')
 for(name in guard.names) {
  if(is.null(definitions[[name]])||!exists(name,ns,inherits=FALSE))stop(paste('Original source/function integrity failure; definition unavailable:',name))
  ref<-eval(definitions[[name]],envir=ns);actual<-get(name,ns,inherits=FALSE)
  if(!identical(body(ref),body(actual))||!identical(formals(ref),formals(actual)))stop(paste('Installed original source integrity failure; body/formals differ:',name))
 }
 runtime.ready<<-TRUE
 RNGkind('Mersenne-Twister','Inversion','Rejection');set.seed(p$seed);saveRDS(.Random.seed,'rng-before.rds',version=3)
 packages<-lapply(sort(loadedNamespaces()),function(pkg)list(package=pkg,version=as.character(packageVersion(pkg)),path=find.package(pkg)))
 jsonlite::write_json(list(R=as.character(getRversion()),edgeR=version,libraries=as.list(.libPaths()),packages=packages,
  function_guards=as.list(guard.names),native_DLLs=lapply(getLoadedDLLs(),function(d)list(name=d[['name']],path=d[['path']])),
  scope='Original R function identities and recorded package/DLL paths; not full host or native-DLL source authentication'),
  'runtime.json',auto_unbox=TRUE,pretty=TRUE,null='null')
 writeLines(capture.output(sessionInfo()),'SESSION.txt')
 x<-read.matrix('counts.tsv');stopifnot(identical(dim(x),c(as.integer(p$features),as.integer(p$samples))))
 stopifnot(identical(rownames(x),paste0('f',seq_len(p$features))),identical(colnames(x),paste0('s',seq_len(p$samples))))
 groups<-read.delim('groups.tsv',colClasses='character',check.names=FALSE,quote='',comment.char='')
 stopifnot(identical(groups$sample_index,colnames(x)))
 group<-factor(groups$group,levels=as.character(unlist(p$group_levels)))
 design<-if(p$model=='EXACT')NULL else as.matrix(read.delim('design.tsv',row.names=1L,check.names=FALSE,colClasses=c('character',rep('numeric',p$design_columns)),quote='',comment.char=''))
 if(!is.null(design)) {
  stopifnot(identical(rownames(design),colnames(x)),identical(colnames(design),paste0('d',seq_len(p$design_columns))))
  if(qr(design)$rank!=ncol(design))stop('Original R QR reports rank-deficient design; no repair is performed')
 }
 lib.size<-if(isTRUE(p$has_libraries))read.delim('libraries.tsv')$library_size else colSums(x)
 y<-edgeR::DGEList(counts=x,group=group,lib.size=lib.size)
 objects$input<<-y;objects$design<<-design;save.objects()
 normalize<-function(y) {
  n<-p$normalization
  if(n$method=='provided') {
   y$samples$norm.factors<-read.delim('norm_factors.tsv')$norm_factor
   adapter.warnings<<-c(adapter.warnings,'Caller-provided positive normalization factors are used literally, without geometric-mean recentering or inferred provenance.')
   return(y)
  }
  a<-list(object=y,method=n$method)
  if(n$method%in%c('TMM','TMMwsp')) {
   if(n$method=='TMMwsp'&&n$a_cutoff!= -1e10)adapter.warnings<<-c(adapter.warnings,'Original TMMwsp accepts but does not apply Acutoff; the declared value is captured and the original algorithm is unchanged.')
   a$logratioTrim<-n$logratio_trim;a$sumTrim<-n$sum_trim;a$doWeighting<-n$do_weighting;a$Acutoff<-n$a_cutoff
   if(!is.null(n$reference_column))a$refColumn<-n$reference_column
  }
  if(n$method=='upperquartile')a$p<-n$p
  do.call(edgeR::calcNormFactors,a)
 }
 keep<-rep(TRUE,nrow(y));names(keep)<-rownames(y)
 filter.y<-function(y) {
  f<-p$filter
  a<-list(y=y,min.count=f$min_count,min.total.count=f$min_total_count,large.n=f$large_n,min.prop=f$min_prop)
  if(f$basis=='group')a$group<-group else a$design<-design
  keep<<-do.call(edgeR::filterByExpr,a)
  objects$filter_input<<-y;objects$keep<<-keep;save.objects()
  table.out(data.frame(keep=keep),'keep.tsv',rownames(y))
  if(!any(keep))stop('Original filterByExpr retained no features; original mask retained and no differential fit run')
  y[keep,,keep.lib.sizes=f$keep_library_sizes]
 }
 if(p$filter$method=='filterByExpr'&&p$filter$order=='before-normalization')y<-filter.y(y)
 y<-normalize(y);objects$normalized_before_after_filter<<-y;save.objects()
 if(p$filter$method=='filterByExpr'&&p$filter$order=='after-normalization') {
  y<-filter.y(y)
  if(isTRUE(p$filter$renormalize_after_filter))y<-normalize(y)
 }
 if(p$filter$method=='none')table.out(data.frame(keep=keep),'keep.tsv',names(keep))
 finite(y$samples$lib.size,'library sizes');finite(y$samples$norm.factors,'normalization factors')
 if(any(y$samples$lib.size<=0)||any(y$samples$norm.factors<=0))stop('Original library sizes/factors must be positive')
 objects$prepared<<-y;save.objects()
 table.out(y$counts,'counts-prepared.tsv',rownames(y))
 table.out(data.frame(library_size=y$samples$lib.size,norm_factor=y$samples$norm.factors,effective_library_size=y$samples$lib.size*y$samples$norm.factors),'sample-normalization.tsv',colnames(y),'sample_index')
 table.out(edgeR::cpm(y,normalized.lib.sizes=TRUE,log=FALSE),'cpm.tsv',rownames(y))
 table.out(edgeR::cpm(y,normalized.lib.sizes=TRUE,log=TRUE,prior.count=2),'log-cpm.tsv',rownames(y))
 d<-p$dispersion;dispersion<-NULL
 if(d$method=='estimateDisp') {
  a<-list(y=y,design=design,trend.method=d$trend_method,tagwise=d$tagwise,min.row.sum=d$min_row_sum,grid.length=d$grid_length,
   grid.range=as.numeric(unlist(d$grid_range)),robust=d$robust,winsor.tail.p=as.numeric(unlist(d$winsor_tail_p)),tol=d$tolerance)
  if(!is.null(d$prior_df))a$prior.df<-d$prior_df
  if(!is.null(d$span))a$span<-d$span
  y<-do.call(edgeR::estimateDisp,a);objects$estimated<<-y;save.objects()
 }else if(d$method=='provided')dispersion<-d$value
 if(p$model=='GLM_QLF'&&d$method=='estimateDisp') {
  dispersion<-if(p$fit$dispersion_selector=='trended')y$trended.dispersion else y$common.dispersion
  if(is.null(dispersion))stop('Requested QL dispersion selector is absent from the original estimateDisp object')
 }
 # Retain original full, prior-count, null and optional Poisson glmFit returns.
 # Source bodies/formals are unchanged. Explicit dispatch is restricted to the
 # DGEList and dense-matrix inputs used here; the original namespace is untouched.
 test.environment<-new.env(parent=ns)
 objects$internal_fits<<-list();objects$internal_boundaries<<-list();objects$observed_fit_methods<<-character()
 for(name in c('glmFit.DGEList','glmFit.default','predFC.default','glmQLFit.DGEList','glmQLFit.default','glmLRT','glmQLFTest'))
  assign(name,eval(definitions[[name]],envir=test.environment),envir=test.environment)
 test.environment$glmFit<-function(y,...) {
  method<-if(is(y,'DGEList'))'glmFit.DGEList' else if(is.matrix(y))'glmFit.default' else stop('Unsupported observational glmFit dispatch input')
  answer<-do.call(get(method,test.environment),c(list(y=y),list(...)))
  objects$internal_fits[[length(objects$internal_fits)+1L]]<<-answer
  objects$observed_fit_methods<<-c(objects$observed_fit_methods,method);save.objects()
  check.fit(answer,'observed full/prior/null/Poisson fit',allow.zero.boundary=TRUE);answer
 }
 test.environment$predFC<-function(y,...) {
  if(!is.matrix(y))stop('Unsupported observational predFC dispatch input')
  do.call(test.environment$predFC.default,c(list(y=y),list(...)))
 }
 test.environment$glmQLFit<-function(y,...) {
  method<-if(is(y,'DGEList'))'glmQLFit.DGEList' else if(is.matrix(y))'glmQLFit.default' else stop('Unsupported observational glmQLFit dispatch input')
  do.call(get(method,test.environment),c(list(y=y),list(...)))
 }
 for(name in c('glmFit.DGEList','glmFit.default','predFC.default','glmQLFit.DGEList','glmQLFit.default','glmLRT','glmQLFTest'))
  stopifnot(identical(body(get(name,test.environment)),body(get(name,ns))),identical(formals(get(name,test.environment)),formals(get(name,ns))))
 if(p$model=='EXACT') {
  if(d$method=='estimateDisp')dispersion<-p$fit$dispersion_selector
  fit<-NULL
 }else {
  if(p$model=='GLM_LRT')fit<-test.environment$glmFit(y,design=design,dispersion=dispersion,prior.count=p$fit$prior_count)
  else {
   a<-list(y=y,design=design,dispersion=dispersion,prior.count=p$fit$prior_count,abundance.trend=p$fit$abundance_trend,
    robust=p$fit$robust,winsor.tail.p=as.numeric(unlist(p$fit$winsor_tail_p)))
   if(version=='4.10.7')a$legacy<-p$fit$ql_mode=='legacy'
   if(!is.null(p$fit$top_proportion))a$top.proportion<-p$fit$top_proportion
   fit<-do.call(test.environment$glmQLFit,a)
  }
  objects$fit<<-fit;save.objects();check.fit(fit,'full fit')
  table.out(fit$coefficients,'coefficients.tsv',rownames(y))
  field<-function(name)if(is.null(fit[[name]]))rep(NA_real_,nrow(y)) else rep_len(fit[[name]],nrow(y))
  table.out(data.frame(deviance=field('deviance'),residual_df=field('df.residual'),iterations=field('iter'),failed=if(is.null(fit$failed))rep(NA,nrow(y)) else fit$failed,
   prior_df=field('df.prior'),posterior_variance=if(is.null(fit$s2.post))field('var.post') else field('s2.post'),
   residual_df_zeros=if(is.null(fit$df.residual.adj))field('df.residual.zeros') else field('df.residual.adj')),'fit-diagnostics.tsv',rownames(y))
 }
 field<-function(name)if(is.null(y[[name]]))rep(NA_real_,nrow(y)) else rep_len(y[[name]],nrow(y))
 table.out(data.frame(common=field('common.dispersion'),trended=field('trended.dispersion'),tagwise=field('tagwise.dispersion')),'dispersion.tsv',rownames(y))
 objects$tests<<-list();objects$top_tables<<-list();comparisons<-list()
 for(i in seq_along(p$results)) {
  selector<-p$results[[i]]
  if(p$model=='EXACT')test<-edgeR::exactTest(y,pair=as.character(unlist(selector$pair)),dispersion=dispersion,
   rejection.region=p$fit$rejection_region,big.count=p$fit$big_count,prior.count=p$fit$prior_count)
  else {
   a<-list(glmfit=fit)
   if(!is.null(selector$coefficients))a$coef<-as.integer(unlist(selector$coefficients)) else a$contrast<-as.numeric(unlist(selector$contrast))
   if(p$model=='GLM_QLF') {a$poisson.bound<-p$fit$poisson_bound;test<-do.call(test.environment$glmQLFTest,a)} else test<-do.call(test.environment$glmLRT,a)
  }
  objects$tests[[i]]<<-test;save.objects()
  top<-edgeR::topTags(test,n=Inf,adjust.method=p$adjust_method,sort.by='none',p.value=1)$table
  objects$top_tables[[i]]<<-top;save.objects()
  table.out(top,paste0('result-',i,'.tsv'),rownames(top))
  finite(as.matrix(top),paste('result',i));stopifnot(identical(rownames(top),rownames(y)),all(top$PValue>=0&top$PValue<=1))
  comparisons[[i]]<-list(result_index=i,comparison=as.list(as.character(test$comparison)),columns=as.list(colnames(top)),df_test_unique=if(is.null(test$df.test))NULL else as.list(unique(test$df.test)))
 }
 jsonlite::write_json(comparisons,'comparisons.json',auto_unbox=TRUE,pretty=TRUE,null='null',digits=NA)
 for(name in guard.names)stopifnot(identical(body(get(name,ns)),body(eval(definitions[[name]],envir=ns))),identical(formals(get(name,ns)),formals(eval(definitions[[name]],envir=ns))))
 adapter.warnings<<-c(adapter.warnings,'The caller declares raw/estimated count units, biological replication and design adequacy. Numeric transport preserves literal external identities through explicit ASCII index maps.',
  'Unchanged original glmFit/predFC/glmQLFit/glmLRT/glmQLFTest bodies run with declared observational DGEList/matrix dispatch: original full/prior-count/null/Poisson glmFit returns are retained and checked for exposed failed flags and the source hardcoded 250-iteration limit. The original namespace is not modified. This does not establish convergence of every lower-level quantity for which stock edgeR exposes no flag.',
  'No whole paper, historical author host, generic empirical-Bayes replacement or unreported author operation is qualified by this prepared-matrix analysis.')
 report$profile<<-p$profile;report$model<<-p$model;report$features_input<<-p$features;report$features_retained<<-nrow(y);report$samples<<-p$samples
 report$results<<-length(p$results);report$source_function_guards<<-length(guard.names);report$observed_internal_fits<<-length(objects$internal_fits);report$internal_source_zero_boundaries<<-length(objects$internal_boundaries)
 report$test_environment<<-'Original unchanged bodies; observational DGEList/matrix glmFit/predFC/glmQLFit dispatch; unchanged namespace';report$status<<-'completed-with-warnings';report$results_admitted<<-TRUE
}
code<-tryCatch(withCallingHandlers({main();0L},warning=function(w){observed.warnings<<-c(observed.warnings,conditionMessage(w));invokeRestart('muffleWarning')},
 message=function(m){observed.messages<<-c(observed.messages,conditionMessage(m));invokeRestart('muffleMessage')}),
 error=function(error){report$error<<-conditionMessage(error);if(inherits(error,'omics_unavailable'))3L else 2L})
if(code!=0L){report$status<-if(code==3L)'SKIP3_DEPENDENCY_UNAVAILABLE' else 'FAIL2_NO_COMPLETE_RESULT';report$results_admitted<-FALSE}
report$required_action<-required.action;report$runtime_ready<-runtime.ready
writeLines(c(adapter.warnings,observed.warnings),'warnings.txt',useBytes=TRUE);writeLines(observed.messages,'messages.txt',useBytes=TRUE)
if(exists('.Random.seed',envir=.GlobalEnv,inherits=FALSE))saveRDS(.Random.seed,'rng-after.rds',version=3)
if(requireNamespace('jsonlite',quietly=TRUE))jsonlite::write_json(report,'report.json',auto_unbox=TRUE,pretty=TRUE,null='null',digits=NA)
detail<-if(is.null(report$error))'Original prepared-matrix analysis completed; inspect warnings and scope.' else report$error
clean<-function(x)gsub('[\t\r\n]',' ',x)
writeLines(c('status\tdetail\trequired_action',paste(report$status,clean(detail),clean(required.action),sep='\t')),'worker-status.tsv',useBytes=TRUE)
if(code!=0L)message(detail)
quit(save='no',status=code)
