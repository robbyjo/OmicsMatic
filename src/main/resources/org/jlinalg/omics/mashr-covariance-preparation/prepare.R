# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original mashr0.2.79 covariance preparation; no wrapper fitting or row padding.
args<-commandArgs(TRUE)
if(length(args)!=3)stop('Require exact private library, typed input directory and NEW output')
input<-normalizePath(args[2],mustWork=TRUE,winslash='/');output<-args[3]
if(file.exists(output)||!dir.create(output,recursive=FALSE))stop('Preserve existing output; use a NEW directory')
output<-normalizePath(output,mustWork=TRUE,winslash='/');phase<-'dependency-admission';notes<-character()
action<-paste('Required action: preserve all source inputs, outputs, logs and candidate covariances.',
 'SKIP3 skips unavailable exact dependencies. FAIL2 admits no result for invalid input, numerical, convergence or capacity failures.',
 'Correct the named cause and retry into a NEW output directory. No population truncation, jitter fallback or synthetic padding.',
 'This preparation stage alone does not admit an analysis; the parent workflow and native child require separate verification.')
need<-function(ok,message){if(!isTRUE(ok))stop(message)}
progress<-function(value){phase<<-value;writeLines(value,file.path(output,'source-progress.txt'))}
writeUTF8<-function(text,path){connection<-file(path,open='wb');on.exit(close(connection));writeLines(enc2utf8(text),connection,useBytes=TRUE)}
dump<-function(x,name){
 if(is.null(dim(x)))x<-matrix(x,nrow=1)
 need(all(is.finite(x)),paste('Nonfinite original output:',name))
 text<-matrix(format(as.vector(x),digits=17,scientific=TRUE,trim=TRUE),nrow=nrow(x))
 connection<-file(file.path(output,name),open='wt',encoding='UTF-8');on.exit(close(connection))
 write.table(text,connection,sep='\t',quote=FALSE,row.names=FALSE,col.names=FALSE)
}
readSettings<-function(name){
 frame<-read.delim(file.path(input,name),colClasses='character',check.names=FALSE,quote='',comment.char='',fileEncoding='UTF-8')
 need(identical(names(frame),c('key','value'))&&!anyNA(frame)&&!anyDuplicated(frame$key),paste('Invalid complete settings:',name))
 setNames(as.list(frame$value),frame$key)
}
number<-function(settings,key,lower=-Inf,upper=Inf,integer=FALSE){
 value<-settings[[key]];need(length(value)==1&&grepl('^[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)([eE][+-]?[0-9]+)?$',value),paste('Invalid explicit number:',key))
 x<-as.numeric(value);need(is.finite(x)&&x>=lower&&x<=upper&&(!integer||x==floor(x)),paste('Numeric setting outside range:',key));x
}
readWide<-function(name){
 frame<-read.delim(file.path(input,name),colClasses='character',check.names=FALSE,quote='',comment.char='',fileEncoding='UTF-8')
 need(ncol(frame)>=2&&nrow(frame)>0&&names(frame)[1]=='feature'&&!anyNA(frame)&&!anyDuplicated(names(frame)),paste('Invalid complete wide matrix:',name))
 need(all(nzchar(frame[[1]]))&&!anyDuplicated(frame[[1]]),'Feature identities must remain unique strings')
 raw<-as.matrix(frame[,-1,drop=FALSE]);need(all(grepl('^[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)([eE][+-]?[0-9]+)?$',raw)),'Require explicit decimal effect/SE values')
 result<-matrix(as.numeric(raw),nrow=nrow(raw),dimnames=list(frame[[1]],colnames(raw)));need(all(is.finite(result)),'Effects/SE must be finite; no missingness inferred');result
}
main<-function(){
 .libPaths(c(normalizePath(args[1],mustWork=FALSE,winslash='/'),.Library),include.site=FALSE)
 for(package in c('mashr','ashr'))if(!requireNamespace(package,quietly=TRUE)){cat(paste('SKIP3: required package unavailable:',package,'\n'),file=stderr());return(3L)}
 need(packageDescription('mashr')$Version=='0.2.79'&&packageDescription('ashr')$Version=='2.2-63','Exact original mashr/ashr editions differ')
 library(mashr);writeUTF8(capture.output(sessionInfo()),file.path(output,'session-info.txt'))
 progress('typed-population-and-method-admission')
 native<-readSettings('settings.tsv');preparation<-readSettings('preparation.tsv')
 expected<-c('schema_version','covariance_mode','npc','ed_tolerance','ed_max_iterations','seed','residual_initial','null_z_threshold','max_rows','max_conditions','max_input_cells','max_work','max_bytes')
 need(setequal(names(preparation),expected)&&preparation$schema_version=='1','Incomplete/unknown preparation schema')
 nativeKeys<-c('schema_version','operation','alpha','posterior_threshold','covariance_tolerance','minimum_se','numeric_sensitivity','basis','grid','normalize','point_mass','prior','optimizer','weight_maxiter','weight_dual_gap','residual','residual_maxiter','residual_tolerance')
 if(identical(native$residual,'simple'))nativeKeys<-c(nativeKeys,'null_z_threshold','null_correlation')
 need(setequal(names(native),nativeKeys)&&native$schema_version=='2'&&native$operation=='fit_predict'&&native$basis=='supplied'&&native$optimizer=='native_em','Require complete explicit native fit schema2 with supplied bases and native_em; no upstream optimizer substituted')
 need(native$normalize%in%c('true','false')&&native$point_mass%in%c('true','false')&&native$prior%in%c('uniform','nullbiased','supplied'),'Invalid explicit native normalization/null/prior choices')
 need(native$residual%in%c('fixed','simple','em_correlation','em_covariance'),'Unknown native residual choice')
 if(native$residual=='simple')need(native$null_correlation%in%c('true','false'),'Require explicit native null correlation boolean')
 alpha<-number(native,'alpha');npc<-number(preparation,'npc',2,256,TRUE)
 need(number(native,'posterior_threshold',0,1)<1,'Posterior threshold must be below one')
 number(native,'covariance_tolerance',0,1e-4);number(native,'minimum_se',0)
 need(number(native,'numeric_sensitivity',0)>0,'Numeric sensitivity must be positive')
 need(number(native,'weight_dual_gap',0,1)>0&&number(native,'weight_dual_gap',0,1)<1,'Native dual gap tolerance must be strictly between zero and one')
 for(key in c('weight_maxiter','residual_maxiter')){value<-number(native,key,1,1000000,TRUE);need(identical(native[[key]],as.character(as.integer(value))),paste('Native iteration setting needs a canonical decimal integer:',key))}
 number(native,'residual_tolerance',0)
 grid<-suppressWarnings(as.numeric(strsplit(native$grid,',',fixed=TRUE)[[1]]));need(length(grid)>0&&all(is.finite(grid))&&all(grid>0)&&all(diff(grid)>0)&&all(is.finite(grid^2))&&all(grid^2>0),'Explicit squared grid is invalid/unrepresentable')
 need(native$prior!='nullbiased'||native$point_mass=='true','Native null-biased prior requires explicit point mass')
 tolerance<-number(preparation,'ed_tolerance',1e-12,.01);maximum<-number(preparation,'ed_max_iterations',2,1000000,TRUE)
 seed<-number(preparation,'seed',0,2147483647,TRUE);threshold<-number(preparation,'null_z_threshold',.01,100)
 need(preparation$covariance_mode%in%c('canonical','canonical_pca_ed'),'Unknown source covariance mode')
 need(preparation$residual_initial%in%c('identity','supplied','simple_null'),'Unknown original residual initializer')
 caps<-sapply(c('max_rows','max_conditions','max_input_cells','max_work','max_bytes'),function(key)number(preparation,key,1,1e15,TRUE))
 bhat<-readWide('effects.tsv');shat<-readWide('standard-errors.tsv')
 need(identical(dimnames(bhat),dimnames(shat))&&all(shat>0),'Complete ordered effect/SE identities differ or SE are nonpositive')
 population<-read.delim(file.path(input,'populations.tsv'),colClasses='character',check.names=FALSE,quote='',comment.char='',fileEncoding='UTF-8')
 need(identical(names(population),c('feature','train','strong','target'))&&identical(population$feature,rownames(bhat))&&!anyNA(population),'Complete population identities differ')
 for(column in c('train','strong','target'))need(all(population[[column]]%in%c('0','1')),'Population flags must be literal0/1')
 need(all(rowSums(population[,-1,drop=FALSE]=='1')>0),'Unassigned rows cannot be dropped')
 train<-which(population$train=='1');strong<-which(population$strong=='1');target<-which(population$target=='1');n<-ncol(bhat)
 need(length(train)>0&&length(target)>0,'Require nonempty training and target populations')
 need(nrow(bhat)<=caps['max_rows']&&n<=caps['max_conditions']&&2*length(bhat)<=caps['max_input_cells'],'Complete population dimensions exceed declared capacity; no prefix used')
 work<-if(preparation$covariance_mode=='canonical_pca_ed')maximum*length(strong)*(npc+1)*n^3 else n^3
 bytes<-65536+160*length(bhat)+64*length(strong)*n^2+128*(n+6+npc)*n^2
 need(is.finite(work)&&work<=caps['max_work']&&is.finite(bytes)&&bytes<=caps['max_bytes'],'Original covariance work/storage plan exceeds declared capacity; increase only permitted limits or skip this request')
 dump(c(work,bytes),'declared-work-and-storage-plan.tsv')
 if(preparation$covariance_mode=='canonical_pca_ed')need(npc<=n&&length(strong)>=npc,'PCA/ED needs the declared PCs and enough real strong rows; no padding')
 needed<-c('settings.tsv','preparation.tsv','effects.tsv','standard-errors.tsv','populations.tsv')
 if(preparation$residual_initial=='supplied')needed<-c(needed,'residual.tsv')
 if(native$prior=='supplied')needed<-c(needed,'prior.tsv')
 need(setequal(list.files(input,recursive=TRUE,all.files=TRUE,no..=TRUE),needed),'Unexpected/unconsumed preparation input files')
 writeUTF8(colnames(bhat),file.path(output,'condition-ids.tsv'));writeUTF8(rownames(bhat)[train],file.path(output,'training-ids.tsv'));writeUTF8(rownames(bhat)[strong],file.path(output,'strong-ids.tsv'));writeUTF8(rownames(bhat)[target],file.path(output,'target-ids.tsv'))
 for(name in c('settings.tsv','preparation.tsv','populations.tsv'))need(file.copy(file.path(input,name),file.path(output,paste0('requested-',name))),paste('Cannot retain',name))
 progress('original-residual-initialization')
 v<-diag(n)
 if(preparation$residual_initial=='supplied'){
  frame<-read.delim(file.path(input,'residual.tsv'),header=FALSE,colClasses='character',quote='',comment.char='')
  need(identical(dim(frame),c(n,n)),'Supplied residual dimensions differ');v<-matrix(as.numeric(as.matrix(frame)),nrow=n);need(all(is.finite(v)),'Nonfinite supplied residual')
  symmetry<-number(native,'covariance_tolerance',0,1e-4)
  need(max(abs(v-t(v)))<=symmetry*max(1,max(abs(v))),'Supplied residual asymmetric beyond explicit tolerance')
  if(any(v!=t(v))){v<-(v+t(v))/2;notes<<-c(notes,'SYMMETRY_ROUNDOFF: original residual symmetrized only within the explicit requested tolerance.')}
 }
 if(preparation$residual_initial=='simple_null'){
  dataTrain<-mash_set_data(bhat[train,,drop=FALSE],shat[train,,drop=FALSE],alpha=alpha)
  v<-estimate_null_correlation_simple(dataTrain,z_thresh=threshold)
  nullRows<-train[apply(abs(dataTrain$Bhat/dataTrain$Shat)<threshold,1,all)]
  writeUTF8(rownames(bhat)[nullRows],file.path(output,'original-null-selected-ids.tsv'))
 }
 need(all(is.finite(v))&&identical(dim(v),c(n,n)),'Invalid complete residual matrix');dump(v,'initial-residual.tsv');chol(v)
 dataTrain<-mash_set_data(bhat[train,,drop=FALSE],shat[train,,drop=FALSE],alpha=alpha,V=v)
 need(all(is.finite(dataTrain$Bhat))&&all(is.finite(dataTrain$Shat))&&all(dataTrain$Shat>0),'Training alpha transform unreportable')
 canonical<-cov_canonical(dataTrain);u<-canonical;dir.create(file.path(output,'covariances'))
 writeUTF8(names(canonical),file.path(output,'covariances/canonical-names.tsv'))
 for(i in seq_along(canonical))dump(canonical[[i]],paste0('covariances/canonical-',i,'.tsv'))
 set.seed(seed);writeUTF8(capture.output(RNGkind()),file.path(output,'rng-kind.txt'));writeUTF8(as.character(.Random.seed),file.path(output,'rng-initial-state.tsv'))
 if(preparation$covariance_mode=='canonical_pca_ed'){
  progress('original-uncentered-PCA-on-explicit-strong-population')
  dataStrong<-mash_set_data(bhat[strong,,drop=FALSE],shat[strong,,drop=FALSE],alpha=alpha,V=v)
  need(all(is.finite(dataStrong$Bhat))&&all(is.finite(dataStrong$Shat))&&all(dataStrong$Shat>0),'Strong alpha transform unreportable')
  dump(dataStrong$Bhat,'strong-transformed-bhat.tsv');dump(dataStrong$Shat,'strong-transformed-shat.tsv')
  pca<-cov_pca(dataStrong,npc=npc)
  writeUTF8(names(pca),file.path(output,'covariances/pca-names.tsv'))
  for(i in seq_along(pca))dump(pca[[i]],paste0('covariances/pca-',i,'.tsv'))
  progress('original-Bovy-extreme-deconvolution')
  ed<-cov_ed(dataStrong,pca,algorithm='bovy',tol=tolerance,maxiter=maximum,logfile=file.path(output,'covariances/ed'))
  saveRDS(ed,file.path(output,'covariances/original-ed-candidates.rds'),compress='xz')
  writeUTF8(names(ed),file.path(output,'covariances/ed-names.tsv'))
  for(i in seq_along(ed))dump(ed[[i]],paste0('covariances/ed-',i,'.tsv'))
  logpath<-file.path(output,'covariances/ed_loglike.log');need(file.exists(logpath),'Original ED convergence log missing')
  log<-readLines(logpath,warn=FALSE);log<-log[nzchar(log)&!grepl('^#',log)];logged<-suppressWarnings(as.numeric(log))
  need(length(logged)>=2&&all(is.finite(logged)),'Original ED history incomplete or nonfinite');dump(logged,'covariances/ed-logged-likelihoods.tsv')
  need(length(logged)<maximum,'ED_TERMINATION_UNPROVEN: original log reached the allowed iteration limit; candidates retained, no prepared analysis admitted')
  if(any(grepl('Warning:',readLines(file.path(output,'covariances/ed_c.log'),warn=FALSE))))notes<<-c(notes,'ED_SOURCE_WARNING: original log reported a likelihood decrease; inspect full log. No monotonicity or global optimum certified.')
  notes<<-c(notes,'ORIGINAL_ED_EPSILON: cov_ed applies its own identity/sqrt(Nstrong) covariance addition (mashr issue91); retained as source method, not a generic numerical repair.')
  u<-c(canonical,ed)
 }
 writeUTF8(as.character(.Random.seed),file.path(output,'rng-final-state.tsv'))
 progress('prepare-exact-native-input-with-unexpanded-original-bases')
 dir.create(file.path(output,'prepared'));dir.create(file.path(output,'prepared/basis'))
 for(name in c('settings.tsv','effects.tsv','standard-errors.tsv','populations.tsv'))need(file.copy(file.path(input,name),file.path(output,'prepared',name)),paste('Cannot capture native input:',name))
 if(native$prior=='supplied')need(file.copy(file.path(input,'prior.tsv'),file.path(output,'prepared/prior.tsv')),'Cannot retain supplied prior')
 writeUTF8(names(u),file.path(output,'prepared/basis/component-names.tsv'))
 for(i in seq_along(u)){need(identical(dim(u[[i]]),c(n,n))&&all(is.finite(u[[i]])),'Original complete covariance shape/numerics invalid');dump(u[[i]],paste0('prepared/basis/component-',i,'.tsv'))}
 if(native$residual!='simple')dump(v,'prepared/residual.tsv') else notes<<-c(notes,'NATIVE_SIMPLE_REESTIMATION: source covariance learning used initial-residual.tsv; native fitting separately applies its explicit simple-null settings. No identity of those two residual estimators is inferred.')
 method<-if(preparation$covariance_mode=='canonical_pca_ed')c('Original PCA/ED learning used only the declared strong population. PCA seeds ED; fitting uses canonical+ED matrices, not an extra concatenated PCA prior.',
  'ED calibration mixture amplitudes are internal to original cov_ed. Representative native mixture proportions are fitted separately from training rows, not imported from the strong-data ED fit.') else 'Canonical-only request: no PCA/ED or strong-data covariance learning was performed.'
 writeUTF8(c(method,
  'Bases are unexpanded and unnormalized here; native settings explicitly control normalization, squared scale grid and null component.',
  'Native fitting uses only representative training rows; target-only values cannot affect original bases or native proportions.',notes),file.path(output,'METHOD-NOTES.txt'))
 progress('original-preparation-complete-awaiting-parent-and-native-verification')
 writeUTF8('ORIGINAL_COVARIANCE_PREPARATION_COMPLETE_UNADMITTED',file.path(output,'source-status.tsv'))
 0L
}
code<-tryCatch(withCallingHandlers(main(),warning=function(w){notes<<-c(notes,paste('ORIGINAL_R_WARNING:',conditionMessage(w)));invokeRestart('muffleWarning')}),error=function(error){
 writeUTF8(paste('FAIL2',phase,conditionMessage(error),sep='\t'),file.path(output,'source-status.tsv'));cat(paste('FAIL2 ORIGINAL PREPARATION:',conditionMessage(error),'\n',action,'\n'),file=stderr());2L
})
if(identical(code,3L))writeUTF8('SKIP3_DEPENDENCY_UNAVAILABLE',file.path(output,'source-status.tsv'))
writeUTF8(c('# Original mashr preparation stage only',paste('Status:',code),paste('Phase:',phase),notes,action),file.path(output,'WARNINGS.md'))
quit(status=code)
