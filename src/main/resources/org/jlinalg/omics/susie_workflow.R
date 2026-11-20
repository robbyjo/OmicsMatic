# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Closed transport. Unmodified installed susieR functions perform every model fit.
options(stringsAsFactors=FALSE,warn=1)
required.action<-paste('Read WARNINGS.md, analysis-status.json, worker/report.json and logs.',
 'Restore exact permitted R/packages/source or skip dependent analysis.',
 'Preserve raw inputs, model objects, partials and diagnostics; correct the cause and retry NEW output.',
 'No automatic installation, allele/ID harmonization, filtering, model fallback or nonfinite repair occurs.')
observed.warnings<-character();adapter.warnings<-character();fit.started<-FALSE;fit.returned<-FALSE
report<-list(status='FAIL2_NO_COMPLETE_RESULT',results_admitted=FALSE,
 statistical_engine='original-susieR',scientific_source_overlay=FALSE,
 full_host_or_DLL_source_authentication=FALSE,independent_statistical_refit_by_verifier=FALSE)
status<-function(code,detail){clean<-function(x)gsub('[\t\r\n]+',' ',x);writeLines(c('status\tdetail\trequired_action',paste(clean(code),clean(detail),clean(required.action),sep='\t')),'worker-status.tsv',useBytes=TRUE)}
unavailable<-function(message)stop(structure(list(message=message,call=NULL),class=c('omics_unavailable','error','condition')))
number<-function(x)if(is.nan(x))'NaN' else if(is.na(x))'NA' else if(is.infinite(x))if(x>0)'Inf' else '-Inf' else sprintf('%.17g',x)
table.out<-function(rows,path,header){
 con<-file(path,'wt',encoding='UTF-8');on.exit(close(con));writeLines(paste(header,collapse='\t'),con,useBytes=TRUE)
 if(length(rows))for(row in rows)writeLines(paste(row,collapse='\t'),con,useBytes=TRUE)
}
read.table.exact<-function(path){
 # read.table trims unquoted header fields even with check.names/strip.white
 # disabled. Closed transport has no quoting, embedded controls or empty cells.
 lines<-readLines(path,encoding='UTF-8',warn=FALSE);if(!length(lines))stop('Empty prepared TSV')
 fields<-strsplit(lines,'\t',fixed=TRUE);width<-length(fields[[1]])
 if(any(lengths(fields)!=width))stop('Prepared TSV widths differ')
 values<-if(length(fields)>1L)matrix(unlist(fields[-1],use.names=FALSE),nrow=length(fields)-1L,ncol=width,byrow=TRUE) else matrix(character(),0L,width)
 frame<-as.data.frame(values,stringsAsFactors=FALSE,optional=TRUE);names(frame)<-fields[[1]];frame
}
finite<-function(x,label){if(!is.numeric(x)||any(!is.finite(x)))stop(paste('Nonfinite/missing source model field:',label));invisible(TRUE)}
log.field<-function(x,label){if(!is.numeric(x)||any(is.na(x)|x==Inf))stop(paste('Invalid source log Bayes factor:',label));invisible(TRUE)}
main<-function(){
 paths<-readLines('runtime-paths.txt',encoding='UTF-8',warn=FALSE)
 .libPaths(c(normalizePath(paths,mustWork=TRUE),.Library),include.site=FALSE)
 if(!requireNamespace('jsonlite',quietly=TRUE))unavailable('Exact private jsonlite dependency is unavailable')
 p<-jsonlite::fromJSON('parameters.json',simplifyVector=FALSE)
 profiles<-jsonlite::fromJSON('../source/reference/SOURCE-PROFILE.json',simplifyVector=FALSE)
 profile<-profiles$profiles[[p$profile]]
 if(is.null(profile))stop('Unknown captured source profile')
 if(as.character(getRversion())!=profile$R_version)unavailable(paste('Need exact R',profile$R_version,'for',p$profile))
 for(name in names(profile$required_packages)){
  if(!requireNamespace(name,quietly=TRUE))unavailable(paste('Required private package unavailable:',name))
  if(as.character(packageVersion(name))!=profile$required_packages[[name]])unavailable(paste('Need exact',name,profile$required_packages[[name]],'for',p$profile))
 }
 for(name in unlist(profile$absent_optional_packages))if(requireNamespace(name,quietly=TRUE))unavailable(paste('Optional backend changes this qualified base profile:',name,'must be absent in the declared isolated runtime'))
 if(!p$model %in% unlist(profile$supported_models))stop('Model is unsupported by this exact edition')
 if(!nzchar(Sys.setlocale('LC_CTYPE',if(.Platform$OS.type=='windows')'English_United States.utf8' else 'C.UTF-8')))stop('Required UTF-8 identity locale unavailable')
 Sys.setlocale('LC_COLLATE','C');Sys.setlocale('LC_NUMERIC','C')
 ns<-asNamespace('susieR');original<-new.env(parent=ns)
 for(file in sort(list.files(file.path('../source/reference',profile$source_directory,'R'),full.names=TRUE,pattern='\\.[Rr]$'))){
  for(expr in parse(file,keep.source=FALSE))if(is.call(expr)&&as.character(expr[[1]]) %in% c('<-','=')&&
    is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name('function')))eval(expr,original)
 }
 guards<-unlist(profile$guarded_functions);if(!identical(sort(ls(original,all.names=TRUE)),guards))stop('Original function inventory differs')
 guard<-function(){for(name in guards){a<-get(name,original,inherits=FALSE);b<-get(name,ns,inherits=FALSE)
   if(!identical(body(a),body(b))||!identical(formals(a),formals(b)))stop(paste('Installed original function body/formals changed:',name))};TRUE}
 guard()
 dump<-unlist(lapply(guards,function(name)c(paste0('FUNCTION ',name),capture.output(dput(get(name,ns,inherits=FALSE))))))
 writeLines(dump,'original-function-bodies.txt',useBytes=TRUE)
 versions<-setNames(lapply(names(profile$required_packages),function(name)as.character(packageVersion(name))),names(profile$required_packages))
 jsonlite::write_json(list(profile=p$profile,R_version=as.character(getRversion()),packages=versions,
   guarded_functions=as.list(guards),source_bodies_formals_equal=TRUE,installed_package_paths=lapply(names(versions),find.package)),
   'runtime.json',pretty=TRUE,auto_unbox=TRUE)
 variants<-read.table.exact('variants.tsv');ids<-enc2utf8(variants[[1]])
 stopifnot(length(ids)==p$variables,!anyDuplicated(ids),all(nzchar(ids)))
 prior<-if('prior_weight'%in%names(variants))as.numeric(variants$prior_weight) else NULL
 common<-list(L=p$effects,max_iter=p$maximum_iterations,tol=p$tolerance,coverage=p$coverage,
   min_abs_corr=p$min_abs_corr,estimate_prior_variance=p$estimate_prior_variance,
   estimate_prior_method=p$estimate_prior_method,prior_tol=p$prior_tol,refine=p$refine,n_purity=p$n_purity,
   verbose=FALSE,track_fit=FALSE)
 if(!is.null(prior))common$prior_weights<-prior
 for(name in c('scaled_prior_variance','prior_variance','residual_variance','estimate_residual_variance','standardize','intercept','check_prior'))
   if(!is.null(p[[name]]))common[[name]]<-p[[name]]
 RNGkind('Mersenne-Twister','Inversion','Rejection');set.seed(p$seed)
 before.rng<-get('.Random.seed',envir=.GlobalEnv);actual.method<-NULL
 if(p$model=='INDIVIDUAL'){
  design<-read.table.exact('design.tsv');phenotype<-read.table.exact('phenotype.tsv')
  stopifnot(identical(enc2utf8(names(design)[-1]),ids),identical(design[[1]],phenotype[[1]]),nrow(design)==p$samples)
  X<-as.matrix(design[,-1,drop=FALSE]);storage.mode(X)<-'double';rownames(X)<-design[[1]]
  common$X<-X;common$y<-as.numeric(phenotype[[2]]);actual.method<-'susie'
 }else{
  input<-read.table.exact('matrix.tsv');stopifnot(identical(enc2utf8(input[[1]]),ids),identical(enc2utf8(names(input)[-1]),ids))
  matrix<-as.matrix(input[,-1,drop=FALSE]);storage.mode(matrix)<-'double';rownames(matrix)<-ids
  if(p$model=='RSS_Z'){
   common$z<-as.numeric(variants$z);common$R<-matrix;actual.method<-'susie_rss'
   if(is.numeric(p$sample_size))common$n<-p$sample_size
   if(profile$version=='0.11.92')adapter.warnings<<-c(adapter.warnings,
     'Legacy 0.11.92 susie_rss uses arbitrary internal n=2,yty=1 and fixed residual variance 1; no modern sample-size/PVE interface is substituted.')
  }else if(p$model %in% c('RSS_BETA_SE','SUFFICIENT_BETA_SE')){
   common$bhat<-as.numeric(variants$beta);common$shat<-as.numeric(variants$standard_error);common$R<-matrix
   common$n<-p$sample_size;common$var_y<-p$variance_y
   actual.method<-if(p$model=='RSS_BETA_SE')'susie_rss' else 'susie_suff_stat'
  }else if(p$model=='SUFFICIENT'){
   common$XtX<-matrix;common$Xty<-as.numeric(variants$xty);common$yty<-p$yty;common$n<-p$sample_size;actual.method<-'susie_suff_stat'
  }else stop('Unsupported closed model')
 }
 jsonlite::write_json(list(method=actual.method,arguments=as.list(names(common)),profile=p$profile,
   variable_order=as.list(ids),no_source_function_environment_changed=TRUE),'original-call.json',auto_unbox=TRUE,pretty=TRUE)
 fit.started<<-TRUE;fit<-do.call(get(actual.method,ns,inherits=FALSE),common);fit.returned<<-TRUE
 saveRDS(fit,'original-fit.rds',version=3)
 writeLines(capture.output(dput(before.rng)),'rng-before.txt');writeLines(capture.output(dput(get('.Random.seed',envir=.GlobalEnv))),'rng-after.txt')
 guard();stopifnot(is.matrix(fit$alpha),ncol(fit$alpha)==length(ids),nrow(fit$alpha)>=1)
 L<-nrow(fit$alpha);finite(fit$alpha,'alpha');finite(fit$mu,'mu');finite(fit$mu2,'mu2');finite(fit$pip,'pip')
 stopifnot(identical(dim(fit$mu),dim(fit$alpha)),identical(dim(fit$mu2),dim(fit$alpha)),length(fit$pip)==length(ids),
   all(fit$alpha>=0&fit$alpha<=1),all(abs(rowSums(fit$alpha)-1)<=1e-8),all(fit$mu2>=0),all(fit$pip>=0&fit$pip<=1))
 if(!is.null(colnames(fit$alpha)))stopifnot(identical(enc2utf8(colnames(fit$alpha)),ids))
 finite(fit$V,'prior variance');finite(fit$sigma2,'residual variance');finite(fit$elbo,'ELBO')
 stopifnot(length(fit$V)==L,all(fit$V>=0),length(fit$sigma2)==1L,fit$sigma2>0,
   is.numeric(fit$niter),length(fit$niter)==1L,fit$niter>=1)
 log.field(fit$lbf,'effect BF');log.field(fit$lbf_variable,'variable BF')
 stopifnot(length(fit$lbf)==L,identical(dim(fit$lbf_variable),dim(fit$alpha)))
 coefficients<-stats::coef(fit);stopifnot(length(coefficients)==length(ids)+1L);finite(unname(coefficients[-1]),'posterior coefficients')
 intercept.missing<-is.na(coefficients[1])&&!is.nan(coefficients[1])
 if(!intercept.missing)finite(unname(coefficients[1]),'intercept')
 if(intercept.missing)adapter.warnings<<-c(adapter.warnings,'Original summary fit has no identifiable intercept without supplied means; source NA is retained as typed metadata, never replaced by zero.')
 if(any(fit$lbf==-Inf)||any(fit$lbf_variable==-Inf))adapter.warnings<<-c(adapter.warnings,'Source negative-infinite log Bayes factors are retained as exact zero-support log values, not capped.')
 table.out(lapply(seq_along(ids),function(j)c(j,ids[j],number(fit$pip[j]),number(coefficients[j+1]))),
   'variant-results.tsv',c('variant_index','variant_id','pip','posterior_mean'))
 rows<-vector('list',L*length(ids));index<-0L
 for(l in seq_len(L))for(j in seq_along(ids)){index<-index+1L;rows[[index]]<-c(l,j,ids[j],number(fit$alpha[l,j]),number(fit$mu[l,j]),number(fit$mu2[l,j]),number(fit$lbf_variable[l,j]))}
 table.out(rows,'effect-results.tsv',c('effect_index','variant_index','variant_id','alpha','mu','mu2','log_bayes_factor'))
 table.out(lapply(seq_len(L),function(l)c(l,number(fit$V[l]),number(fit$lbf[l]))),'effect-summary.tsv',c('effect_index','prior_variance','log_bayes_factor'))
 table.out(lapply(seq_along(fit$elbo),function(i)c(i,number(fit$elbo[i]))),'elbo.tsv',c('iteration','elbo'))
 cs<-fit$sets$cs;members<-list();summaries<-list()
 if(length(cs))for(k in seq_along(cs)){
  indices<-cs[[k]];effect<-fit$sets$cs_index[k];purity<-fit$sets$purity[k,,drop=FALSE];coverage<-fit$sets$coverage[k]
  stopifnot(length(indices)>0,!anyDuplicated(indices),all(indices>=1&indices<=length(ids)),effect>=1,effect<=L)
  if(length(coverage)!=1L||!is.finite(coverage)||abs(sum(fit$alpha[effect,indices])-coverage)>1e-8){
   report$source_discrepancy<<-'Original credible-set coverage does not match its original effect/member alpha values.'
   stop('Original credible-set coverage/membership inconsistency; raw source model retained and no complete result admitted')
  }
  purity.values<-as.numeric(unlist(purity,use.names=FALSE));finite(purity.values,'credible-set purity');finite(coverage,'credible-set coverage')
  stopifnot(all(purity.values>=0&purity.values<=1),coverage>0,coverage<=1+1e-8)
  for(j in indices)members[[length(members)+1L]]<-c(k,names(cs)[k],effect,j,ids[j],number(fit$alpha[effect,j]))
  summaries[[k]]<-c(k,names(cs)[k],effect,length(indices),number(coverage),number(purity$min.abs.corr),number(purity$mean.abs.corr),number(purity$median.abs.corr))
 }
 if(!length(cs))adapter.warnings<<-c(adapter.warnings,'Original fit returned no qualifying credible sets; the empty set population is explicit and no fallback model is run.')
 table.out(members,'credible-sets.tsv',c('set_index','set_id','effect_index','variant_index','variant_id','alpha'))
 table.out(summaries,'credible-set-summary.tsv',c('set_index','set_id','effect_index','size','coverage','min_abs_corr','mean_abs_corr','median_abs_corr'))
 report$profile<<-p$profile;report$model<<-p$model;report$original_method<<-actual.method
 report$variables<<-length(ids);report$effects<<-L;report$samples<<-p$samples;report$niter<<-fit$niter
 report$converged<<-isTRUE(fit$converged);report$residual_variance<<-fit$sigma2
 report$intercept_token<<-number(coefficients[1]);report$intercept_source_missing<<-intercept.missing
 report$credible_sets<<-length(cs);report$source_function_guards<<-length(guards);report$elbo_entries<<-length(fit$elbo)
 if(!isTRUE(fit$converged))stop('Original SuSiE did not converge; raw object/partial exports retained without a complete result')
 report$status<<-'completed-with-warnings';report$results_admitted<<-TRUE
 invisible(TRUE)
}
code<-tryCatch(withCallingHandlers({main();0L},warning=function(w){observed.warnings<<-c(observed.warnings,conditionMessage(w));invokeRestart('muffleWarning')}),
 error=function(error){report$error<<-conditionMessage(error);if(inherits(error,'omics_unavailable'))3L else 2L})
if(code!=0L){report$status<-if(code==3L)'SKIP3_DEPENDENCY_UNAVAILABLE' else 'FAIL2_NO_COMPLETE_RESULT';report$results_admitted<-FALSE}
report$original_call_started<-fit.started;report$original_call_returned<-fit.returned
report$warnings<-as.list(c(adapter.warnings,observed.warnings));report$required_action<-required.action
writeLines(c(adapter.warnings,observed.warnings),'warnings.txt',useBytes=TRUE)
if(requireNamespace('jsonlite',quietly=TRUE))jsonlite::write_json(report,'report.json',auto_unbox=TRUE,pretty=TRUE,null='null',digits=NA)
detail<-if(is.null(report$error))'Original source fit and typed exports completed; inspect warnings and scope.' else report$error
status(report$status,detail);if(code!=0L)message(detail);quit(save='no',status=code)
