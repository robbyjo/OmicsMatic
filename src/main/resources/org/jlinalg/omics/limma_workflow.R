# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original limma/edgeR/statmod calls from closed prepared numeric data.
.libPaths(c(readLines('runtime-paths.txt',encoding='UTF-8',warn=FALSE),.Library),include.site=FALSE)
observed.warnings<-observed.messages<-adapter.warnings<-character();objects<-list();object.count<-0L
runtime.ready<-FALSE;guards<-list();report<-list(status='RUNNING_NO_RESULT_ADMITTED',results_admitted=FALSE)
internal.failures<-character();internal.count<-0L;journal<-NULL;internal.context<-list();gamma.count<-0L
action<-'FAIL2: NO COMPLETE RESULT. Preserve captured inputs, immutable object snapshots, partials, warnings and logs; correct the named input, design, numerical, convergence, integrity, process or capacity cause and retry NEW output. SKIP3: dependent analysis skipped; restore the exact permitted named runtime/package or retain the skip. No version, population, threshold, identity or estimator substitution.'
unavailable<-function(message)stop(structure(list(message=message,call=NULL),class=c('omics_unavailable','error','condition')))
finite<-function(x,label){if(any(!is.finite(x)))stop(paste('Original nonfinite',label,'retained; NO COMPLETE RESULT'))}
number<-function(x)vapply(x,function(v)if(is.nan(v))'NaN' else if(is.na(v))'NA' else if(is.infinite(v))if(v>0)'Inf' else '-Inf' else sprintf('%.17g',v),'')
write.utf8<-function(lines,file) {connection<-file(file,open='wb');on.exit(close(connection));if(length(lines))writeBin(charToRaw(enc2utf8(paste0(paste(lines,collapse='\n'),'\n'))),connection)}
table.out<-function(x,path,ids=NULL,id.name='feature_id') {
 x<-as.data.frame(x,check.names=FALSE,stringsAsFactors=FALSE)
 values<-lapply(x,function(v)if(is.numeric(v))number(v) else as.character(v))
 if(!is.null(ids))values<-c(setNames(list(as.character(ids)),id.name),values)
 connection<-file(path,open='wb');on.exit(close(connection));emit<-function(lines)writeBin(charToRaw(enc2utf8(paste0(paste(lines,collapse='\n'),'\n'))),connection)
 emit(paste(names(values),collapse='\t'));n<-if(length(values))length(values[[1]]) else 0L
 if(any(lengths(values)!=n))stop('Output column lengths differ; no recycling')
 chunk<-max(1L,min(1024L,as.integer(1e6/max(1L,length(values)))))
 if(n)for(start in seq.int(1L,n,by=chunk)){at<-start:min(n,start+chunk-1L);emit(do.call(paste,c(lapply(values,function(v)v[at]),sep='\t')))}
}
retain<-function(label,value) {
 object.count<<-object.count+1L;key<-sprintf('%05d-%s',object.count,label)
 objects[[key]]<<-value;saveRDS(value,file.path('objects',paste0(key,'.rds')),version=3,compress=FALSE)
 cat(paste(key,paste(class(value),collapse=','),sep='\t'),'\n',file='objects-index.tsv',append=TRUE,sep='')
 value
}
matrix.in<-function(file,ncol=NULL) {
 if(is.null(ncol))ncol<-length(strsplit(readLines(file,n=1L,warn=FALSE,encoding='UTF-8'),'\t',fixed=TRUE)[[1]])-1L
 x<-read.delim(file,row.names=1L,check.names=FALSE,quote='',comment.char='',na.strings=character(),encoding='UTF-8',colClasses=c('character',rep('numeric',ncol)))
 x<-as.matrix(x);storage.mode(x)<-'double';x
}
table.in<-function(file)read.delim(file,check.names=FALSE,quote='',comment.char='',na.strings=character(),colClasses='character',encoding='UTF-8')
legacy<-function(x)switch(x,auto=NULL,'true'=TRUE,'false'=FALSE,stop('Unknown legacy policy'))
definitions<-list()
guard.package<-function(pkg,names) {
 ns<-asNamespace(pkg);expressions<-list()
 collect<-function(e) {
  assignment<-function(z)is.call(z)&&length(z)==3L&&(identical(z[[1]],as.name('<-'))||identical(z[[1]],as.name('=')))&&is.symbol(z[[2]])
  if(!assignment(e))return(invisible(NULL))
  if(assignment(e[[3]])){collect(e[[3]]);expressions[[as.character(e[[2]])]]<<-e[[3]][[2]]}
  else expressions[[as.character(e[[2]])]]<<-e[[3]]
 }
 for(file in zip.names[startsWith(zip.names,paste0(pkg,'/R/'))&grepl('[.]R$',zip.names)]) {
  connection<-unz(source.zip,file,open='r');code<-tryCatch(parse(connection,keep.source=FALSE),finally=close(connection))
  for(e in code)collect(e)
 }
 resolve<-function(name,seen=character()) {
  if(name%in%seen)stop('Source alias cycle')
  e<-expressions[[name]]
  if(is.symbol(e))return(resolve(as.character(e),c(seen,name)))
  if(is.null(e)||!is.call(e)||!identical(e[[1]],as.name('function')))stop(paste('Original function definition absent:',pkg,name))
  eval(e,envir=ns)
 }
 for(name in names) {
  if(!exists(name,ns,inherits=FALSE))stop(paste('Original namespace function absent:',pkg,name))
  original<-get(name,ns,inherits=FALSE);ref<-resolve(name)
  if(!identical(body(original),body(ref))||!identical(formals(original),formals(ref)))stop(paste('Original source body/formals differ:',pkg,name))
  guards[[paste(pkg,name,sep='::')]]<<-list(body=body(original),formals=formals(original))
 }
 definitions[[pkg]]<<-expressions
}
guard.after<-function() {
 for(key in names(guards)) {
  parts<-strsplit(key,'::',fixed=TRUE)[[1]];fn<-get(parts[2],asNamespace(parts[1]),inherits=FALSE)
  if(!identical(body(fn),guards[[key]]$body)||!identical(formals(fn),guards[[key]]$formals))stop(paste('Original namespace changed during execution:',key))
 }
}
record.internal<-function(label,value) {
 internal.count<<-internal.count+1L;start<-seek(journal,where=NA,rw='write')
 serialize(list(index=internal.count,label=label,value=value),journal,ascii=FALSE,version=3);flush(journal)
 end<-seek(journal,where=NA,rw='write');cat(paste(internal.count,label,start,end,sep='\t'),'\n',file='internal-model-index.tsv',append=TRUE,sep='')
}
gamma.observer<-function(frame) {
 fields<-intersect(c('iter','maxit','tol','dl','dbeta','dev','mu','lambda','maxinfo','beta','betaold','maxy'),ls(frame,all.names=TRUE))
 state<-mget(fields,envir=frame,inherits=FALSE)
 state$context<-internal.context
 if('iter'%in%fields) {
  needed<-c('dl','dbeta','dev','mu','tol','lambda','maxinfo')
  converged<-all(needed%in%fields)&&isTRUE(as.numeric(crossprod(state$dl,state$dbeta))<state$tol||state$dev/max(state$mu)<1e-15)
  damped<-isTRUE(state$lambda/state$maxinfo>1e15)
  state$observed_converged<-converged&&!damped
  gamma.count<<-gamma.count+1L
  metrics<-c(gamma.count,internal.context$feature_index,state$iter,state$maxit,state$tol,as.numeric(crossprod(state$dl,state$dbeta)),state$dev/max(state$mu),state$lambda/state$maxinfo,as.numeric(state$observed_converged),as.numeric(state$dl),as.numeric(state$dbeta))
  if(length(metrics)!=13L)stop('Unexpected original two-variance-component convergence metric shape')
  cat(paste(number(metrics),collapse='\t'),'\n',file='convergence.tsv',append=TRUE,sep='')
  if(!state$observed_converged)internal.failures<<-c(internal.failures,paste('Original statmod glmgam.fit did not satisfy its source convergence criterion at feature',internal.context$feature_index,internal.context$feature_id))
 }else state$source_early_return<-TRUE
 record.internal('statmod-glmgam-final-locals',state)
}
observed.mixed<-function(y,X,Z,w=NULL,...) {
 caller<-parent.frame();before<-internal.context
 if(!exists('i',caller,inherits=FALSE)||!exists('M',caller,inherits=FALSE))stop('Original duplicateCorrelation observer lacks exact gene context')
 at<-get('i',caller,inherits=FALSE);M<-get('M',caller,inherits=FALSE)
 internal.context<<-list(feature_index=at,feature_id=rownames(M)[at]);on.exit(internal.context<<-before,add=TRUE)
 ns<-asNamespace('statmod');env<-new.env(parent=ns)
 env$glmgam.fit<-function(...) {
  original<-get('glmgam.fit',ns,inherits=FALSE);copy<-original;observer.env<-new.env(parent=ns);observer.env$.omics_observe<-gamma.observer
  prefix<-quote(on.exit(.omics_observe(environment()),add=TRUE))
  body(copy)<-as.call(list(as.name('{'),prefix,body(original)));environment(copy)<-observer.env
  stopifnot(identical(body(copy)[[2]],prefix),identical(body(copy)[[3]],body(original)),identical(formals(copy),formals(original)))
  answer<-withCallingHandlers(do.call(copy,list(...)),warning=function(w){observed.warnings<<-c(observed.warnings,paste('Original internal statmod glmgam.fit:',conditionMessage(w)));invokeRestart('muffleWarning')})
  record.internal('statmod-glmgam-fit',list(context=internal.context,result=answer));answer
 }
 original<-get('mixedModel2Fit',ns,inherits=FALSE);copy<-original;environment(copy)<-env
 answer<-tryCatch(do.call(copy,c(list(y=y,X=X,Z=Z,w=w),list(...))),error=function(e){record.internal('statmod-mixedModel-error',list(error=conditionMessage(e),y=y,X=X,Z=Z,w=w));internal.failures<<-c(internal.failures,conditionMessage(e));stop(e)})
 record.internal('statmod-mixedModel-fit',list(context=internal.context,result=answer));answer
}
observed.environment<-function(pkg) {
 env<-new.env(parent=asNamespace(pkg))
 env$lmFit<-function(object,design=NULL,...) {
  fit<-limma::lmFit(object,design=design,...);retain('internal-lmFit',list(fit=fit,design=design));fit
 }
 env$duplicateCorrelation<-function(object,design=NULL,...) {
  fit<-observed.call('limma','duplicateCorrelation',c(list(object=object,design=design),list(...)));retain('internal-duplicateCorrelation',fit);fit
 }
 env$mixedModel2Fit<-observed.mixed
 env$glmFit<-function(y,...) {
  fit<-edgeR::glmFit(y,...);retain('internal-glmFit',fit)
  if(!is.null(fit$failed)&&any(is.na(fit$failed)|fit$failed))stop('Original internal GLM reported failed fits; object retained')
  if(identical(fit$method,'levenberg')&&(any(!is.finite(fit$iter))||any(fit$iter>250)))stop('Original internal GLM exhausted its iteration budget; object retained')
  fit
 }
 env
}
observed.call<-function(pkg,name,args) {
 original<-get(name,asNamespace(pkg),inherits=FALSE);copy<-original;environment(copy)<-observed.environment(pkg)
 stopifnot(identical(body(copy),body(original)),identical(formals(copy),formals(original)))
 do.call(copy,args)
}
check.fit<-function(fit,label) {
 if(is.null(fit$coefficients)||is.null(fit$stdev.unscaled)||is.null(fit$sigma)||is.null(fit$df.residual))stop(paste('Incomplete original',label))
 finite(fit$coefficients,paste(label,'coefficients'));finite(fit$stdev.unscaled,paste(label,'unscaled standard errors'));finite(fit$df.residual,paste(label,'residual df'))
 boundary<-fit$df.residual==0 & is.na(fit$sigma) & !is.nan(fit$sigma)
 if(any(!is.finite(fit$sigma)&!boundary)||any(fit$stdev.unscaled<=0)||any(fit$sigma[!boundary]<0)||any(fit$df.residual<0))stop(paste('Invalid original',label,'standard error/variance/residual df; no result'))
 if(any(boundary))adapter.warnings<<-c(adapter.warnings,paste('Original',label,'retains',sum(boundary),'estimable zero-residual-df rows with NA sigma. No sigma is imputed; any inference must still pass original posterior and finite-output checks.'))
}
main<-function() {
 if(!requireNamespace('jsonlite',quietly=TRUE))unavailable('jsonlite is unavailable in declared libraries')
 p<<-jsonlite::fromJSON('parameters.json',simplifyVector=FALSE)
 if(as.character(getRversion())!='4.6.1')unavailable('Require exact R4.6.1')
 for(pkg in c('limma','edgeR','statmod')) {
  version<-switch(pkg,limma='3.68.5',edgeR='4.10.7',statmod='1.5.2')
  if(!requireNamespace(pkg,quietly=TRUE)||as.character(packageVersion(pkg))!=version)unavailable(paste('Require exact',pkg,version,'in declared permitted libraries'))
 }
 source.zip<<-'../source/reference/source.zip';zip.names<<-unzip(source.zip,list=TRUE)$Name
 guard.package('limma',c('lmFit','lm.series','gls.series','getEAWP','asMatrixWeights','duplicateCorrelation','contrasts.fit','eBayes','.ebayes','squeezeVar','.squeezeVar','fitFDist','fitFDistUnequalDF1','fitFDistRobustly','trigammaInverse','tmixture.matrix','tmixture.vector','treat','classifyTestsF','voom','normalizeBetweenArrays','normalizeQuantiles','normalizeCyclicLoess','normalizeMedianValues','cameraPR','cameraPR.default','rankSumTestWithCorrelation','chooseLowessSpan','arrayWeights'))
 guard.package('edgeR',c('DGEList','validDGEList','normLibSizes','normLibSizes.DGEList','normLibSizes.default','getNormLibSizes','voomLmFit','glmFit','glmFit.DGEList','glmFit.default','mglmLevenberg','mglmOneWay','mglmOneGroup'))
 guard.package('statmod',c('logmdigamma','mixedModel2','mixedModel2Fit','glmgam.fit','gauss.quad','gauss.quad.prob'))
 runtime.ready<<-TRUE;dir.create('objects');writeLines('object\tclass','objects-index.tsv')
 journal<<-file('internal-model-journal.bin',open='wb');writeLines('index\tlabel\tstart\tend','internal-model-index.tsv')
 writeLines('fit_index\tfeature_index\titerations\tmaximum_iterations\ttolerance\tscore_step\trelative_deviance\tdamping_ratio\tconverged\tscore1\tscore2\tstep1\tstep2','convergence.tsv')
 RNGkind('Mersenne-Twister','Inversion','Rejection');set.seed(p$seed);saveRDS(.Random.seed,'rng-before.rds',version=3)
 jsonlite::write_json(list(R=as.character(getRversion()),packages=lapply(sort(loadedNamespaces()),function(pkg)list(package=pkg,version=as.character(packageVersion(pkg)),path=find.package(pkg))),libraries=as.list(.libPaths()),function_guards=as.list(names(guards)),native_DLLs=lapply(getLoadedDLLs(),function(d)list(name=d[['name']],path=d[['path']])),scope='Original R bodies/formals and recorded package/DLL identities; not full host/native-DLL authentication'),'runtime.json',pretty=TRUE,auto_unbox=TRUE,null='null')
 writeLines(capture.output(sessionInfo()),'SESSION.txt');out<-'../results';op<-p$operation
 if(op=='CAMERAPR') {
  s<-table.in('statistics.tsv');statistic<-setNames(as.numeric(s$statistic),s$feature_id);finite(statistic,'ranked statistics')
  gs<-table.in('gene_sets.tsv');set.ids<-unique(gs$set_id);index<-setNames(lapply(set.ids,function(id)match(gs$feature_id[gs$set_id==id],names(statistic))),set.ids)
  retain('camera-input',list(statistic=statistic,index=index));c<-p$camera
  fit<-limma::cameraPR(statistic,index=index,use.ranks=c$use_ranks,inter.gene.cor=as.numeric(unlist(c$inter_gene_cor)),sort=c$sort,directional=c$directional);retain('camera-original',fit)
  for(column in names(fit)[vapply(fit,is.numeric,TRUE)])finite(fit[[column]],paste('cameraPR',column))
  table.out(fit,file.path(out,'camera.tsv'),rownames(fit),'set_id')
 }else {
  x<-matrix.in('matrix.tsv',p$samples);stopifnot(identical(dim(x),c(as.integer(p$features),as.integer(p$samples))));finite(x,'input matrix');retain('input-matrix',x)
  design<-if(isTRUE(p$has_design))matrix.in('design.tsv',p$design_columns) else NULL
  weights<-if(isTRUE(p$has_weights))matrix.in('weights.tsv',p$samples) else NULL
  if(!is.null(design)) {
   stopifnot(identical(rownames(design),colnames(x)));if(qr(design)$rank!=ncol(design))stop('Original R QR reports rank-deficient design; no repair')
   retain('design',design)
  }
  if(!is.null(weights))stopifnot(identical(dimnames(weights),dimnames(x)))
  factor.in<-function(key) {z<-table.in(paste0(key,'.tsv'));stopifnot(identical(z$sample_id,colnames(x)));factor(z[[key]],levels=as.character(unlist(p[[paste0(key,'_levels')]])))}
  block<-if(isTRUE(p$has_block))factor.in('block') else NULL
  if(op=='NORMALIZE') {
   corrected<-limma::normalizeBetweenArrays(x,method=p$normalize_method);retain('normalized-original',corrected);finite(corrected,'normalized matrix');table.out(corrected,file.path(out,'expression.tsv'),rownames(corrected))
  }else if(op=='BATCH') {
   batch<-if(isTRUE(p$has_batch))factor.in('batch') else NULL;batch2<-if(isTRUE(p$has_batch2))factor.in('batch2') else NULL
   covariates<-if(isTRUE(p$has_covariates))matrix.in('covariates.tsv') else NULL
   corrected<-observed.call('limma','removeBatchEffect',list(x=x,batch=batch,batch2=batch2,covariates=covariates,design=design,weights=weights));retain('batch-corrected-original',corrected)
   internal<-objects[[tail(grep('internal-lmFit',names(objects),value=TRUE),1)]];check.fit(internal$fit,'batch combined fit')
   keep<-ncol(design);nuisance<-seq.int(keep+1L,ncol(internal$design));beta<-internal$fit$coefficients[,nuisance,drop=FALSE];z<-internal$design[,nuisance,drop=FALSE]
   colnames(beta)<-colnames(z)<-paste0('n',seq_along(nuisance));finite(corrected,'batch-corrected matrix')
   table.out(corrected,file.path(out,'expression.tsv'),rownames(corrected));table.out(beta,file.path(out,'batch-coefficients.tsv'),rownames(beta));table.out(z,file.path(out,'batch-design.tsv'),rownames(z),'sample_id')
  }else {
   fit<-NULL;expr<-x;correlation<-NULL;v<-NULL
   if(op%in%c('VOOM','EDGER_VOOM_LMFIT')) {
    n<-p$normalization;lib<-if(isTRUE(p$has_libraries))as.numeric(table.in('libraries.tsv')$library_size) else colSums(x)
    finite(lib,'library sizes');if(any(lib<=0))stop('Positive library sizes required; no inferred substitute')
    y<-edgeR::DGEList(counts=x,lib.size=lib);retain('DGEList-original',y)
    if(n$method=='provided') {
     y$samples$norm.factors<-as.numeric(table.in('norm_factors.tsv')$norm_factor)
     adapter.warnings<<-c(adapter.warnings,'Caller-provided normalization factors used literally without recentering or inferred provenance.')
    }else {
     a<-list(object=y,method=n$method)
     if(n$method%in%c('TMM','TMMwsp')) {a$logratioTrim<-n$logratio_trim;a$sumTrim<-n$sum_trim;a$doWeighting<-n$do_weighting;a$Acutoff<-n$a_cutoff;if(!is.null(n$reference_column))a$refColumn<-n$reference_column}
     if(n$method=='upperquartile')a$p<-n$p
     y<-do.call(edgeR::normLibSizes,a)
     if(n$method=='TMMwsp'&&n$a_cutoff!= -1e10)adapter.warnings<<-c(adapter.warnings,'Original TMMwsp accepts but does not apply Acutoff; no extra filtering is introduced.')
    }
    retain('DGEList-normalized-original',y);finite(y$samples$norm.factors,'normalization factors');if(any(y$samples$norm.factors<=0))stop('Nonpositive original normalization factors')
    table.out(data.frame(library_size=y$samples$lib.size,norm_factor=y$samples$norm.factors,effective_library_size=y$samples$lib.size*y$samples$norm.factors),file.path(out,'normalization.tsv'),colnames(x),'sample_id')
    options<-p$voom
    if(op=='EDGER_VOOM_LMFIT') {
     fit<-observed.call('edgeR','voomLmFit',list(counts=y,design=design,block=block,prior.weights=weights,sample.weights=options$sample_weights,normalize.method=options$normalize_method,span=options$span,adaptive.span=options$adaptive_span,plot=FALSE,save.plot=TRUE,keep.EList=TRUE));retain('edgeR-voomLmFit-original',fit)
     v<-fit$EList;if(is.null(v))stop('Original edgeR voomLmFit did not retain EList');expr<-v$E
    }else {
     c<-p$correlation
     make.voom<-function(correlation=NULL)observed.call('limma','voom',list(counts=y,design=design,normalize.method=options$normalize_method,block=if(is.null(correlation))NULL else block,correlation=correlation,weights=weights,span=options$span,adaptive.span=options$adaptive_span,plot=FALSE,save.plot=TRUE))
     if(c$mode=='fixed')correlation<-c$value
     v<-make.voom(correlation);retain('voom-original-pass1',v)
     if(c$mode=='estimate') {
      dc<-observed.call('limma','duplicateCorrelation',list(object=v,design=design,block=block,trim=c$trim));retain('voom-duplicateCorrelation-original',dc);correlation<-dc$consensus.correlation;finite(correlation,'consensus correlation')
      if(p$correlation$voom_passes==2L){v<-make.voom(correlation);retain('voom-original-pass2',v)}
     }
     expr<-v$E;fit<-limma::lmFit(v,design=design,block=block,correlation=correlation);retain('lmFit-voom-original',fit)
    }
   }else if(op=='LM') {
    c<-p$correlation;if(c$mode=='fixed')correlation<-c$value
    if(c$mode=='estimate') {dc<-observed.call('limma','duplicateCorrelation',list(object=x,design=design,block=block,trim=c$trim,weights=weights));retain('duplicateCorrelation-original',dc);correlation<-dc$consensus.correlation;finite(correlation,'consensus correlation')}
    fit<-limma::lmFit(x,design=design,block=block,correlation=correlation,weights=weights);retain('lmFit-original',fit)
   }else stop('Unsupported fitted operation')
   check.fit(fit,'final linear fit');finite(expr,'model expression');table.out(expr,file.path(out,'expression.tsv'),rownames(expr))
   if(!is.null(v$weights)){finite(v$weights,'voom weights');if(any(v$weights<=0))stop('Nonpositive original voom weights');published.weights<-v$weights;dimnames(published.weights)<-dimnames(expr);table.out(published.weights,file.path(out,'weights.tsv'),rownames(expr))}
   if(!is.null(correlation))table.out(data.frame(consensus_correlation=correlation),file.path(out,'correlation.tsv'))
   table.out(fit$coefficients,file.path(out,'coefficients-original.tsv'),rownames(x));table.out(fit$stdev.unscaled,file.path(out,'stdev-original.tsv'),rownames(x))
   contrast<-do.call(cbind,lapply(p$contrasts,function(z)as.numeric(unlist(z$values))));rownames(contrast)<-colnames(design);colnames(contrast)<-vapply(p$contrasts,function(z)z$id,'')
   retain('contrasts',contrast);table.out(contrast,file.path(out,'contrasts.tsv'),rownames(contrast),'coefficient_id')
   fit<-limma::contrasts.fit(fit,contrasts=contrast);retain('contrasts-fit-original',fit);check.fit(fit,'contrasted fit')
   m<-p$moderation;trend<-switch(m$trend,none=FALSE,Amean=TRUE,provided=as.numeric(table.in('trend_covariate.tsv')$covariate))
   a<-list(fit=fit,trend=trend,robust=m$robust,legacy=legacy(m$legacy));if(isTRUE(m$robust))a$winsor.tail.p<-as.numeric(unlist(m$winsor_tail_p))
   if(m$method=='EBAYES') {a$proportion<-m$proportion;a$stdev.coef.lim<-as.numeric(unlist(m$stdev_coef_lim));if(!is.null(m$span))a$span<-m$span;fit<-do.call(limma::eBayes,a)}
   else {a$lfc<-m$lfc;a$upshot<-FALSE;fit<-do.call(limma::treat,a)}
   retain('moderated-original',fit)
   check.fit(fit,'moderated fit')
   for(name in c('coefficients','stdev.unscaled','df.residual','s2.prior','s2.post','df.total','t','p.value'))finite(fit[[name]],paste('moderated',name))
   if(anyNA(fit$df.prior)||any(fit$df.prior<0))stop('Invalid original prior df; positive infinity is valid and is not clamped')
   if(any(fit$p.value<0|fit$p.value>1)||any(fit$s2.post<0)||any(fit$df.total<=0))stop('Invalid original probability/variance/total df')
   adjusted<-fit$p.value
   if(p$adjustment_scope=='all-contrasts')adjusted[]<-p.adjust(as.numeric(fit$p.value),method=p$adjust_method)
   else for(j in seq_len(ncol(fit$p.value)))adjusted[,j]<-p.adjust(fit$p.value[,j],method=p$adjust_method)
   retain('adjusted-p-values',adjusted)
   for(pair in list(c('coefficients','coefficients.tsv'),c('stdev.unscaled','stdev-unscaled.tsv'),c('t','t.tsv'),c('p.value','pvalue.tsv')))table.out(fit[[pair[1]]],file.path(out,pair[2]),rownames(x))
   table.out(adjusted,file.path(out,'padjust.tsv'),rownames(x))
   if(!is.null(fit$lods)){finite(fit$lods,'B statistics');table.out(fit$lods,file.path(out,'lods.tsv'),rownames(x))}
   if(!is.null(fit$F)){finite(fit$F,'F statistics');finite(fit$F.p.value,'F probabilities');table.out(data.frame(F=fit$F,PValue=fit$F.p.value),file.path(out,'F.tsv'),rownames(x))}
   table.out(data.frame(sigma=fit$sigma,residual_df=fit$df.residual,prior_df=rep_len(fit$df.prior,nrow(x)),prior_variance=rep_len(fit$s2.prior,nrow(x)),posterior_variance=fit$s2.post,total_df=fit$df.total,Amean=fit$Amean),file.path(out,'variance.tsv'),rownames(x))
   report$prior_df_length<<-length(fit$df.prior);report$prior_variance_length<<-length(fit$s2.prior)
   report$source_zero_df_NA_sigma_rows<<-sum(fit$df.residual==0&is.na(fit$sigma)&!is.nan(fit$sigma))
  }
 }
 if(length(internal.failures))stop(paste('Original internal numerical/convergence failure retained:',paste(unique(internal.failures),collapse='; ')))
 if(any(grepl('iteration limit|failed to converge|not converg|convergence fail|convergence tolerance not achievable|estimation.*failed',observed.warnings,ignore.case=TRUE)))stop('Original warning reports failed estimation/convergence; original objects and source defaults retained but no result admitted')
 guard.after();report$source_guards_after<<-TRUE
 report$status<<-'completed-with-warnings';report$results_admitted<<-TRUE
}
error<-NULL;code<-tryCatch(withCallingHandlers({main();0L},warning=function(w){observed.warnings<<-c(observed.warnings,conditionMessage(w));invokeRestart('muffleWarning')},message=function(m){observed.messages<<-c(observed.messages,conditionMessage(m));invokeRestart('muffleMessage')}),omics_unavailable=function(e){error<<-conditionMessage(e);3L},error=function(e){error<<-conditionMessage(e);2L})
if(code!=0L){report$status<-if(code==3L)'SKIP3_DEPENDENCY_UNAVAILABLE' else 'FAIL2_NO_COMPLETE_RESULT';report$results_admitted<-FALSE}
report$runtime_ready<-runtime.ready;report$error<-error;report$required_action<-action;report$object_snapshots<-object.count;report$source_guards_before<-length(guards);report$internal_model_frames<-internal.count;report$iterative_gamma_fits<-gamma.count;report$internal_failures<-as.list(internal.failures);report$execution_scope<-'Original statistical bodies retained; observational call bindings and one read-only glmgam.fit on.exit prefix with exact original-body inverse; unchanged original namespaces'
if(exists('p')) {report$profile<-p$profile;report$operation<-p$operation;report$features_input<-p$features;report$samples<-p$samples;report$contrasts<-length(p$contrasts);report$sets<-p$sets}
write.utf8(c(observed.warnings,adapter.warnings),'warnings.txt');write.utf8(observed.messages,'messages.txt')
if(runtime.ready) {
 if(!is.null(journal))close(journal)
 tryCatch({saveRDS(objects,'objects-final.rds.part',version=3,compress=FALSE);if(!file.rename('objects-final.rds.part','objects.rds'))stop('Cannot finalize original object bundle')},error=function(e){if(code==0L){code<<-2L;report$status<<-'FAIL2_NO_COMPLETE_RESULT';report$results_admitted<<-FALSE;report$error<<-conditionMessage(e)}})
 if(exists('.Random.seed',envir=.GlobalEnv))saveRDS(.Random.seed,'rng-after.rds',version=3)
}
if(requireNamespace('jsonlite',quietly=TRUE))jsonlite::write_json(report,'report.json',pretty=TRUE,auto_unbox=TRUE,null='null')
clean<-function(x)gsub('[\t\r\n]',' ',if(is.null(x))'' else x)
write.utf8(c('status\tdetail\trequired_action',paste(report$status,clean(report$error),clean(action),sep='\t')),'worker-status.tsv')
quit(save='no',status=code,runLast=FALSE)
