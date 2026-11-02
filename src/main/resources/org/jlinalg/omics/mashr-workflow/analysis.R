# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Typed original mashr0.2.79 development workflow. Scientific admission requires
# a separate native audit and complete bundle verification; exit0 alone is not admission.
args <- commandArgs(TRUE)
if(length(args)!=3)stop('Supply private R library, normalized input directory and NEW output directory')
libraryPath <- normalizePath(args[1],mustWork=FALSE,winslash='/')
input <- normalizePath(args[2],mustWork=TRUE,winslash='/')
output <- args[3]
if(file.exists(output))stop('Preserve existing output; use a NEW output directory')
if(!dir.create(output,recursive=FALSE))stop('Cannot create NEW output directory')
output <- normalizePath(output,mustWork=TRUE,winslash='/')
stage <- 'dependency-admission'
progress <- function(value){stage<<-value;writeLines(value,file.path(output,'source-progress.txt'))}
warnings <- character()
action <- paste('Required action: preserve this output, inputs, original logs and fitted models.',
 'Correct the named input, numerical or convergence failure; repeat into a NEW directory.',
 'If the exact R/package dependencies cannot be installed in a permitted environment, keep the dependent analysis skipped.',
 'This original-process result is not admitted until the independent Java and bundle audits pass.')
dump <- function(x,name){
 if(is.null(dim(x)))x<-matrix(x,nrow=1)
 text<-matrix(format(as.vector(x),digits=17,scientific=TRUE,trim=TRUE),nrow=nrow(x))
 write.table(text,file.path(output,name),sep='\t',quote=FALSE,row.names=FALSE,col.names=FALSE)
}
status <- function(code,message){write.table(data.frame(status=code,stage=stage,message=message),file.path(output,'source-status.tsv'),sep='\t',quote=TRUE,row.names=FALSE)}
note <- function(code,message){warnings<<-c(warnings,paste0(code,': ',message))}
need <- function(condition,message){if(!isTRUE(condition))stop(message)}
unavailable <- function(message){stop(structure(list(message=message,call=NULL),class=c('mash_dependency_unavailable','error','condition')))}
readMatrix <- function(name){
 frame<-read.delim(file.path(input,name),header=TRUE,check.names=FALSE,stringsAsFactors=FALSE,quote='',comment.char='',na.strings='NA',colClasses='character',fileEncoding='UTF-8')
 need(ncol(frame)>=3&&nrow(frame)>0,'A complete effect/SE table needs feature IDs and at least two conditions')
 need(names(frame)[1]=='feature'&&!anyDuplicated(names(frame))&&!anyNA(frame),'Invalid feature matrix header or missing values')
 ids<-frame[[1]];need(is.character(ids)&&all(nzchar(ids))&&!anyDuplicated(ids),'Feature IDs must be nonempty unique strings')
 raw<-as.matrix(frame[,-1,drop=FALSE])
 need(all(grepl('^[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)([eE][+-]?[0-9]+)?$',raw)),'Effect/SE cells must be explicit decimal numbers')
 x<-matrix(as.numeric(raw),nrow=nrow(raw),dimnames=list(ids,colnames(raw)));need(all(is.finite(x)),'Effect/SE matrices must be finite numeric values')
 rownames(x)<-ids;x
}
settings <- NULL
main <- function(){
 progress('dependency-admission')
 .libPaths(c(libraryPath,.Library),include.site=FALSE)
 for(package in c('mashr','ashr'))if(!requireNamespace(package,quietly=TRUE))unavailable(paste('Required original package unavailable:',package))
 if(packageDescription('mashr')$Version!='0.2.79'||packageDescription('ashr')$Version!='2.2-63')unavailable('Require exact mashr0.2.79 / ashr2.2-63 profile')
 library(mashr)
 writeLines(capture.output(sessionInfo()),file.path(output,'session-info.txt'))
 progress('typed-input-admission')
 request<-read.delim(file.path(input,'settings.tsv'),header=TRUE,check.names=FALSE,stringsAsFactors=FALSE,quote='',comment.char='',colClasses='character')
 keys<-c('schema_version','alpha','covariance_mode','npc','ed_tolerance','ed_max_iterations','residual_mode','residual_initial',
  'null_z_threshold','residual_max_iterations','residual_tolerance','prior','null_weight','optimizer','optimizer_tolerance',
  'optimizer_max_iterations','pi_threshold','normalize_u','use_point_mass','grid','algorithm_version','seed','fit_dual_gap_tolerance')
 need(identical(names(request),c('key','value'))&&!anyDuplicated(request$key)&&setequal(request$key,keys)&&!anyNA(request),'Settings keys differ from the complete typed schema')
 settings<<-setNames(as.list(request$value),request$key)
 number<-function(key,lower,upper,integer=FALSE){
  raw<-settings[[key]];need(grepl('^[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)([eE][+-]?[0-9]+)?$',raw),paste('Invalid numeric setting:',key))
  x<-as.numeric(raw);need(is.finite(x)&&x>=lower&&x<=upper&&(!integer||x==floor(x)),paste('Setting outside admitted range:',key));x
 }
 choice<-function(key,values){need(settings[[key]]%in%values,paste('Unsupported setting:',key));settings[[key]]}
 need(settings$schema_version=='1','Unsupported workflow schema')
 alpha<-number('alpha',-8,8);npc<-number('npc',2,256,TRUE)
 covarianceMode<-choice('covariance_mode',c('canonical','canonical_pca_ed'))
 residualMode<-choice('residual_mode',c('fixed','simple_null','em'))
 initialMode<-choice('residual_initial',c('identity','supplied','simple_null'))
 prior<-choice('prior',c('uniform','nullbiased'));nullweight<-number('null_weight',1,1e6)
 optimizer<-choice('optimizer',c('mixEM','mixSQP'));algorithm<-choice('algorithm_version',c('R','Rcpp'))
 optimizerTolerance<-number('optimizer_tolerance',1e-14,.01);optimizerLimit<-number('optimizer_max_iterations',1,1000000,TRUE)
 edTolerance<-number('ed_tolerance',1e-12,.01);edLimit<-number('ed_max_iterations',2,1000000,TRUE)
 residualTolerance<-number('residual_tolerance',0,1e6);residualLimit<-number('residual_max_iterations',2,1000000,TRUE)
 nullThreshold<-number('null_z_threshold',.01,100);piThreshold<-number('pi_threshold',0,.999999)
 normalize<-number('normalize_u',0,1,TRUE)==1;pointmass<-number('use_point_mass',0,1,TRUE)==1
 need(pointmass||prior=='uniform','A null-biased prior requires an explicit null component')
 fitTolerance<-number('fit_dual_gap_tolerance',1e-12,.01);seed<-number('seed',0,2147483647,TRUE);set.seed(seed)
 gridText<-strsplit(settings$grid,',',fixed=TRUE)[[1]]
 need(length(gridText)>0&&all(grepl('^([0-9]+([.][0-9]*)?|[.][0-9]+)([eE][+-]?[0-9]+)?$',gridText)),'Supply an explicit positive comma-separated grid')
 grid<-as.numeric(gridText);need(all(is.finite(grid))&&all(grid>0)&&all(diff(grid)>0),'Grid must be finite, positive and strictly increasing')
 bhat<-readMatrix('effects.tsv');shat<-readMatrix('standard-errors.tsv')
 need(identical(dimnames(bhat),dimnames(shat))&&all(shat>.Machine$double.eps),'Effects and SE must have identical ordered identities; SE must exceed the explicit source zero threshold')
 population<-read.delim(file.path(input,'populations.tsv'),header=TRUE,check.names=FALSE,stringsAsFactors=FALSE,quote='',comment.char='',colClasses='character')
 need(identical(names(population),c('feature','train','strong','target'))&&identical(population$feature,rownames(bhat)),'Complete population IDs must match the ordered effect rows')
 for(column in c('train','strong','target'))need(all(population[[column]]%in%c('0','1')),paste('Population flags must be literal0/1:',column))
 train<-which(population$train=='1');strong<-which(population$strong=='1');target<-which(population$target=='1')
 need(length(train)>0&&length(target)>0,'Require nonempty representative training and target populations')
 need(algorithm!='Rcpp'||length(train)!=1&&length(target)!=1,
  'SOURCE_RCPP_SINGLE_FEATURE_UNAVAILABLE: the inspected mashr0.2.79 native posterior path can abort for one feature with multiple retained components. The original R wrapper also has singleton dimension failures. Use the tested MashGaussian/MashMixtureWeights Java API while integrated native singleton execution is completed; preserve the original failures. No rows are padded or silently rerouted.')
 need(all(rowSums(population[,-1,drop=FALSE]=='1')>0),'Unassigned rows are not silently dropped; declare a population for every row')
 if(covarianceMode=='canonical_pca_ed')need(npc<=ncol(bhat)&&length(strong)>=npc,'PCA/ED requires the declared number of PCs and enough complete strong rows')
 write.table(population,file.path(output,'populations.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
 write.table(request,file.path(output,'settings.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
 writeLines(colnames(bhat),file.path(output,'condition-ids.tsv'))
 writeLines(rownames(bhat)[train],file.path(output,'training-ids.tsv'))
 writeLines(rownames(bhat)[strong],file.path(output,'strong-ids.tsv'))
 writeLines(rownames(bhat)[target],file.path(output,'target-ids.tsv'))
 dump(bhat[train,,drop=FALSE],'training-bhat.tsv');dump(shat[train,,drop=FALSE],'training-shat.tsv')
 dump(bhat[target,,drop=FALSE],'target-bhat.tsv');dump(shat[target,,drop=FALSE],'target-shat.tsv')
 training<-mash_set_data(bhat[train,,drop=FALSE],shat[train,,drop=FALSE],alpha=alpha)
 progress('residual-initialization')
 v<-diag(ncol(bhat))
 if(initialMode=='supplied'){
  supplied<-read.delim(file.path(input,'residual.tsv'),header=TRUE,check.names=FALSE,stringsAsFactors=FALSE,quote='',comment.char='')
  need(names(supplied)[1]=='condition'&&identical(names(supplied)[-1],colnames(bhat))&&identical(supplied[[1]],colnames(bhat)),'Supplied residual covariance identities differ')
  v<-as.matrix(supplied[,-1,drop=FALSE]);need(is.numeric(v)&&all(is.finite(v))&&identical(dim(v),c(ncol(bhat),ncol(bhat))),'Invalid supplied residual matrix')
 }
 if(initialMode=='simple_null'||residualMode=='simple_null')v<-estimate_null_correlation_simple(training,z_thresh=nullThreshold)
 training<-mash_update_data(training,V=v)
 dump(v,'initial-residual.tsv')
 progress('canonical-and-data-driven-covariances')
 u<-cov_canonical(training);dir.create(file.path(output,'covariances'))
 if(covarianceMode=='canonical_pca_ed'){
  strongData<-mash_set_data(bhat[strong,,drop=FALSE],shat[strong,,drop=FALSE],alpha=alpha,V=v)
  dump(strongData$Bhat,'strong-transformed-bhat.tsv');dump(strongData$Shat,'strong-transformed-shat.tsv')
  pca<-cov_pca(strongData,npc=npc)
  writeLines(names(pca),file.path(output,'covariances/pca-names.tsv'))
  for(i in seq_along(pca))dump(pca[[i]],paste0('covariances/pca-',i-1,'.tsv'))
  progress('original-extreme-deconvolution')
  ed<-cov_ed(strongData,pca,algorithm='bovy',tol=edTolerance,maxiter=edLimit,logfile=file.path(output,'covariances/ed'))
  saveRDS(ed,file.path(output,'covariances/original-ed-covariances.rds'),compress='xz')
  writeLines(names(ed),file.path(output,'covariances/ed-names.tsv'))
  for(i in seq_along(ed))dump(ed[[i]],paste0('covariances/ed-',i-1,'.tsv'))
  logPath<-file.path(output,'covariances/ed_loglike.log')
  need(file.exists(logPath),'Original ED failed to retain its convergence log')
  likelihoodLog<-readLines(logPath,warn=FALSE)
  likelihoodLog<-likelihoodLog[nzchar(likelihoodLog)&!grepl('^#',likelihoodLog)]
  logged<-suppressWarnings(as.numeric(likelihoodLog));need(length(logged)>=2&&all(is.finite(logged)),'ED log has incomplete/nonfinite likelihood history')
  dump(logged,'covariances/ed-logged-likelihoods.tsv')
  need(length(logged)<edLimit,'ED_TERMINATION_UNPROVEN: logged iterations reached the declared limit; preserve learned candidates and increase the permitted limit before admission')
  note('ED_LOCAL_OPTIMUM','Original Bovy ED is a local fit. It reports no convergence flag; finite outputs and termination before the declared iteration limit are checked, and its six-decimal logs are retained. No global optimum is certified.')
  if(any(grepl('Warning:',readLines(file.path(output,'covariances/ed_c.log'),warn=FALSE))))note('ED_SOURCE_WARNING','Inspect ed_c.log: the original implementation reported a log-likelihood decrease. Do not infer a monotonic or globally optimal covariance fit.')
  u<-c(u,ed)
 }
 writeLines(names(u),file.path(output,'covariances/fitting-names.tsv'))
 for(i in seq_along(u))dump(u[[i]],paste0('covariances/fitting-',i-1,'.tsv'))
 control<-if(optimizer=='mixEM')list(tol=optimizerTolerance,maxiter=optimizerLimit,trace=FALSE) else
  list(verbose=FALSE,eps=1e-12,convtol.sqp=optimizerTolerance,maxiter.sqp=optimizerLimit,numiter.em=20)
 common<-list(grid=grid,normalizeU=normalize,usepointmass=pointmass,optmethod=optimizer,control=control,
  nullweight=nullweight,pi_thresh=piThreshold,algorithm.version=algorithm,seed=seed)
 writeLines(capture.output(dput(common)),file.path(output,'original-fit-controls.txt'))
 progress('representative-training-mixture-fit')
 if(residualMode=='em'){
  progress('original-residual-correlation-em')
  residual<-do.call(mash_estimate_corr_em,c(list(data=training,Ulist=u,init=v,max_iter=residualLimit,
   tol=residualTolerance,est_cor=TRUE,track_fit=FALSE,prior=prior,details=TRUE),common))
  saveRDS(residual,file.path(output,'original-residual-em.rds'),compress='xz')
  dump(residual$loglik,'residual-em-source-history.tsv')
  dump(residual$niter,'residual-em-source-niter.tsv')
  dump(residual$V,'residual-em-returned-V.tsv');dump(get_estimated_pi(residual$mash.model,dimension='all'),'residual-em-returned-proportions.tsv')
  dump(get_loglik(residual$mash.model),'residual-em-returned-model-loglik.tsv')
  need(is.finite(residual$niter)&&residual$niter<residualLimit,'RESIDUAL_EM_TERMINATION_UNPROVEN: source counter reached the declared limit; preserve the returned model and increase the limit before admission')
  v<-residual$V;fit<-residual$mash.model
  note('SOURCE_RESIDUAL_EM_STOP','Original ad hoc residual EM can roll back after a likelihood decrease. Its source niter/history are retained verbatim; the history can omit the last accepted likelihood. Final-model likelihood is independently recomputed. No global residual-correlation convergence certificate is asserted.')
 }else fit<-do.call(mash,c(list(data=training,Ulist=u,prior=prior,verbose=FALSE,outputlevel=4),common))
 training<-mash_update_data(training,V=v)
 saveRDS(fit,file.path(output,'original-training-fit.rds'),compress='xz')
 g<-get_fitted_g(fit);expanded<-mashr:::expand_cov(g$Ulist,g$grid,g$usepointmass)
 dir.create(file.path(output,'model'));writeLines(names(expanded),file.path(output,'model/component-names.tsv'))
 for(i in seq_along(expanded))dump(expanded[[i]],paste0('model/component-',i-1,'.tsv'))
 dump(g$pi,'model/proportions.tsv');dump(v,'model/residual.tsv');dump(g$grid,'model/grid.tsv')
 writeLines(names(g$Ulist),file.path(output,'model/normalized-covariance-names.tsv'))
 for(i in seq_along(g$Ulist))dump(g$Ulist[[i]],paste0('model/normalized-covariance-',i-1,'.tsv'))
 progress('independent-training-mixture-check')
 trainLm<-mashr:::calc_relative_lik_matrix(training,expanded,algorithm.version=algorithm)
 trainLl<-sweep(trainLm$loglik_matrix,1,trainLm$lfactors-rowSums(log(training$Shat_alpha)),'+')
 dump(trainLl,'training-component-loglik.tsv')
 centered<-trainLl-apply(trainLl,1,max);likelihood<-exp(centered);denominator<-as.vector(likelihood%*%g$pi)
 need(all(is.finite(denominator))&&all(denominator>0),'Training mixture has lost observation support')
 priorVector<-mashr:::set_prior(length(g$pi),prior,nullweight);pseudo<-priorVector-1
 gradient<-colSums(likelihood/denominator)
 if(any(pseudo>0)){need(all(g$pi[pseudo>0]>0),'Positive Dirichlet prior lost support');gradient[pseudo>0]<-gradient[pseudo>0]+pseudo[pseudo>0]/g$pi[pseudo>0]}
 lambda<-length(train)+sum(pseudo);gap<-max(0,max(gradient)/lambda-1)
 relativeObjective<-sum(log(denominator))+sum(pseudo[pseudo>0]*log(g$pi[pseudo>0]))
 dump(c(relativeObjective,gap,lambda),'training-objective-gap-lambda.tsv');dump(priorVector,'model/dirichlet.tsv')
 need(is.finite(gap)&&gap<=fitTolerance,'MIXTURE_NONCONVERGED: independent fixed-covariance dual-gap check failed; preserve the model and increase optimizer limits/tolerance appropriately')
 recomputed<-mash_compute_loglik(fit,training,algorithm.version=algorithm)
 dump(c(fit$loglik,recomputed),'training-source-and-recomputed-loglik.tsv')
 need(isTRUE(all.equal(fit$loglik,recomputed,tolerance=1e-8)),'Source fitted and recomputed training likelihoods differ')
 progress('complete-target-posterior')
 targets<-mash_set_data(bhat[target,,drop=FALSE],shat[target,,drop=FALSE],alpha=alpha,V=v)
 result<-mash_compute_posterior_matrices(fit,targets,pi_thresh=piThreshold,algorithm.version=algorithm,output_posterior_cov=TRUE)
 saveRDS(result,file.path(output,'original-target-posterior.rds'),compress='xz')
 dir.create(file.path(output,'posterior'))
 for(pair in list(c('PosteriorMean','mean'),c('PosteriorSD','sd'),c('NegativeProb','negative'),c('lfdr','zero'),c('lfsr','lfsr'))){
  value<-result[[pair[1]]];need(length(value)==length(target)*ncol(bhat)&&all(is.finite(value)),paste('Incomplete/nonfinite original target field:',pair[1]))
  dump(matrix(value,nrow=length(target),ncol=ncol(bhat)),paste0('posterior/',pair[2],'.tsv'))
 }
 need(length(result$PosteriorCov)==length(target)*ncol(bhat)^2&&all(is.finite(result$PosteriorCov)),'Incomplete/nonfinite target covariance tensor')
 cov<-array(result$PosteriorCov,dim=c(ncol(bhat),ncol(bhat),length(target)))
 covarianceRows<-t(matrix(vapply(seq_along(target),function(j)as.vector(cov[,,j]),numeric(ncol(bhat)^2)),nrow=ncol(bhat)^2,ncol=length(target)))
 dump(covarianceRows,'posterior/covariance-column-major.tsv')
 lm<-mashr:::calc_relative_lik_matrix(targets,expanded,algorithm.version=algorithm)
 component<-sweep(lm$loglik_matrix,1,lm$lfactors-rowSums(log(targets$Shat_alpha)),'+')
 dump(component,'posterior/component-loglik.tsv')
 keep<-g$pi>piThreshold;need(any(keep),'Prior threshold removes every component')
 weights<-matrix(0,nrow=length(target),ncol=length(g$pi))
 weights[,keep]<-mashr:::compute_posterior_weights(g$pi[keep],exp(lm$loglik_matrix[,keep,drop=FALSE]))
 dump(weights,'posterior/weights.tsv');dump(as.vector(mash_compute_vloglik(fit,targets,algorithm.version=algorithm)),'posterior/loglik.tsv')
 note('DECLARED_POPULATIONS','Only the explicitly flagged representative training, strong covariance-learning and target rows are used. Their selection is user supplied, not inferred or certified random by this adapter.')
 writeLines(capture.output(sessionInfo()),file.path(output,'final-session-info.txt'))
 write.table(data.frame(Package=loadedNamespaces(),Version=vapply(loadedNamespaces(),function(p)as.character(packageVersion(p)),character(1))),file.path(output,'loaded-packages.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
 dlls<-getLoadedDLLs();write.table(data.frame(name=names(dlls),path=vapply(dlls,function(x)x[['path']],character(1))),file.path(output,'loaded-DLLs.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
 progress('original-process-complete-awaiting-independent-audit')
 status('ORIGINAL_PROCESS_COMPLETE_UNAUDITED','All original stages returned; no scientific result is admitted by this process alone')
 0
}
code<-tryCatch(withCallingHandlers(main(),warning=function(w){note('ORIGINAL_R_WARNING',paste(stage,conditionMessage(w)));invokeRestart('muffleWarning')}),
 error=function(e){
  code<-if(inherits(e,'mash_dependency_unavailable'))3 else 2
  if(identical(settings$algorithm_version,'R')&&grepl('logical subscript too long|dims \\[product',conditionMessage(e)))
   note('SOURCE_R_SINGLE_FEATURE_DIMENSION','The inspected original R wrapper has a singleton likelihood/posterior dimension failure. Input observations remain unchanged. Use the tested MashGaussian/MashMixtureWeights Java API; integrated native singleton execution is still pending. Preserve this source error and the returned training model when available.')
  writeLines(conditionMessage(e),file.path(output,'error.txt'))
  status(if(code==3)'SKIP3_DEPENDENCY_UNAVAILABLE' else 'FAIL2_NO_RESULT_ADMITTED',conditionMessage(e))
  note(if(code==3)'SKIP3_DEPENDENCY_UNAVAILABLE' else 'FAIL2_NO_RESULT_ADMITTED',conditionMessage(e));code
 })
writeLines(c('# Original mashr workflow warnings','',paste0('- ',warnings),'',action),file.path(output,'WARNINGS.md'))
if(code!=0)cat(if(code==3)'MASHR ANALYSIS SKIPPED: ' else 'MASHR ANALYSIS FAILED: ',file.path(output,'WARNINGS.md'),'\n',action,'\n',file=stderr())
quit(save='no',status=code,runLast=FALSE)
