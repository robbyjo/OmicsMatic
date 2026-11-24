# Copyright (C) 2026 JLinAlg contributors; GPL-3.0-or-later.
# Original APIs only; no dependency installation or replacement statistics.
options(warn=1,digits=17)
a<-commandArgs(trailingOnly=TRUE);stopifnot(length(a)==1L)
cfg<-jsonlite::read_json(a[1],simplifyVector=FALSE);root<-normalizePath(cfg$root,winslash='/',mustWork=TRUE);home<-normalizePath(cfg$runtime_home,winslash='/',mustWork=TRUE)
addons<-if(is.null(cfg$runtime_addons))NULL else normalizePath(cfg$runtime_addons,winslash='/',mustWork=TRUE)
.libPaths(c(if(!is.null(addons))file.path(addons,'library'),file.path(home,'library'),.Library),include.site=FALSE)
req<-cfg$request;o<-req$options;p<-req$paths;op<-req$operation;initial_conditions<-list();conditions<-list();original_calls<-0L
source(file.path(root,'source/object_codec.R'),local=TRUE)
write_json<-function(x,name){file<-file.path(root,'worker',name);tmp<-paste0(file,'.partial');jsonlite::write_json(x,tmp,auto_unbox=TRUE,pretty=TRUE,null='null',digits=17);stopifnot(file.rename(tmp,file))}
canonical<-function(f)paste0(paste(c(deparse(formals(f),width.cutoff=500L),deparse(body(f),width.cutoff=500L)),collapse='\n'),'\n')
snapshot<-function(name){
 con<-file(file.path(root,'worker',name),'wt',encoding='UTF-8');on.exit(close(con));writeLines('namespace\tname_hex\tcanonical_hex',con)
 for(pkg in c('GenomicSEM','lavaan')){ns<-asNamespace(pkg);for(n in sort(ls(ns,all.names=TRUE))){f<-get(n,ns,inherits=FALSE);if(is.function(f))writeLines(paste(pkg,prep_hex(n),prep_hex(canonical(f)),sep='\t'),con)}}
}
numbers<-function(x)vapply(x,function(v)if(is.null(v))NA_real_ else as.numeric(v),numeric(1))
prep_vector<-function(x)unlist(x,use.names=FALSE)
address<-function(x){out<-file.path(root,'inputs/bundle',x);if(grepl('/$',x))out<-paste0(sub('/+$','',out),'/');out}
addresses<-function(x)vapply(x,address,character(1))
original<-file.path(root,'worker/original');dir.create(original);setwd(original);products<-file.path(root,'products');dir.create(products)
format_column<-function(v){
 if(is.factor(v))v<-as.character(v)
 if(is.double(v))return(vapply(v,function(x)if(is.nan(x))'NaN'else if(is.na(x))'NA'else if(is.infinite(x))if(x>0)'Inf'else'-Inf'else sprintf('%.17g',x),character(1)))
 ifelse(is.na(v),'NA',as.character(v))
}
write_tsv<-function(d,file){
 stopifnot(is.data.frame(d),!anyDuplicated(colnames(d)));cols<-lapply(d,format_column);con<-file(file,'wt',encoding='UTF-8');on.exit(close(con));writeLines(paste(colnames(d),collapse='\t'),con)
 if(nrow(d))for(start in seq.int(1L,nrow(d),by=65536L)){end<-min(nrow(d),start+65535L);lines<-vapply(start:end,function(i)paste(vapply(cols,'[',character(1),i),collapse='\t'),character(1));writeLines(lines,con,useBytes=TRUE)}
}
write_matrix<-function(x,labels,name){stopifnot(is.matrix(x),nrow(x)==length(labels),ncol(x)==length(labels));d<-as.data.frame(x,check.names=FALSE);colnames(d)<-labels;write_tsv(data.frame(id=labels,d,check.names=FALSE),file.path(products,name))}
project_associations<-function(result,tw){
 selected<-c(if(tw)c('Gene','Panel','HSQ')else c('SNP','CHR','BP','MAF','A1','A2'),unlist(lapply(prep_vector(o$trait_names),function(t)c(paste0('beta.',t),paste0('se.',t)))))
 stopifnot(is.data.frame(result),!anyDuplicated(names(result)),all(selected%in%names(result)))
 write_tsv(result[,selected,drop=FALSE],file.path(products,'associations.tsv'))
 list(kind=if(tw)'TSEM_named_column_projection'else'GWAS_named_column_projection',columns=as.list(selected),original_columns=as.list(names(result)),omitted_original_columns=as.list(setdiff(names(result),selected)),numeric_format='17 significant digits;original nonfinite/missing values retained',original_values_or_identifiers_repaired=FALSE)
}
outcome<-tryCatch({
 profile<-jsonlite::read_json(file.path(root,'source/BASE-PROFILE.json'),simplifyVector=FALSE)
 if(as.character(getRversion())!=profile$R$version||R.version$platform!=profile$R$platform)stop('UNAVAILABLE: require R4.6.1 Windows x86_64')
 withCallingHandlers({
  for(package in profile$dependencies)if(packageDescription(package$package,fields='Version')!=package$version)stop(paste('UNAVAILABLE: require package',package$package,package$version))
  if(packageDescription('Matrix',fields='Version')!=profile$R$Matrix||as.character(packageVersion('lavaan'))!=profile$lavaan$runtime_version)stop('UNAVAILABLE: require Matrix1.7-5 and lavaan0.6-19')
  library(GenomicSEM)
  if(!is.null(addons)){
   extra<-jsonlite::read_json(file.path(root,'source/ADDONS.json'),simplifyVector=FALSE)
   library(Rmpfr)
   for(package in extra$packages){stopifnot(as.character(packageVersion(package$package))==package$runtime_version,normalizePath(find.package(package$package),winslash='/',mustWork=TRUE)==file.path(addons,'library',package$package))}
  }
 },warning=function(w){initial_conditions[[length(initial_conditions)+1L]]<<-list(message=conditionMessage(w),call=paste(deparse(conditionCall(w)),collapse='\n'))})
 ns<-asNamespace('GenomicSEM');reference<-new.env(parent=ns)
 for(file in sort(list.files(file.path(home,'source/R'),full.names=TRUE)))sys.source(file,envir=reference,keep.source=FALSE)
 funcs<-ls(reference,all.names=TRUE)[vapply(ls(reference,all.names=TRUE),function(n)is.function(get(n,reference,inherits=FALSE)),logical(1))];stopifnot(length(funcs)==44)
 for(n in funcs)stopifnot(exists(n,ns,inherits=FALSE),identical(deparse(formals(get(n,reference))),deparse(formals(get(n,ns)))),identical(deparse(body(get(n,reference))),deparse(body(get(n,ns)))))
 snapshot('namespace-before.tsv');dlls<-vapply(getLoadedDLLs(),function(d)d[['path']],character(1));dlls<-dlls[nzchar(dlls)]
 write_json(list(nonce=cfg$nonce,R_home=normalizePath(R.home(),winslash='/',mustWork=TRUE),R_version=as.character(getRversion()),platform=R.version$platform,DLLs=unname(dlls),namespaces=sort(loadedNamespaces()),namespace_functions=44),'runtime-probe.json')
 write_json(list(conditions=initial_conditions),'initial-conditions.json');write_json(list(nonce=cfg$nonce,status='READY_BEFORE_FIRST_ORIGINAL_CALL'),'READY.json');deadline<-Sys.time()+300
 while(!file.exists(file.path(root,'worker/ACK.json'))){if(Sys.time()>deadline)stop('Java runtime/input fingerprint handshake was not acknowledged');Sys.sleep(.025)}
 ack<-jsonlite::read_json(file.path(root,'worker/ACK.json'));stopifnot(identical(ack$nonce,cfg$nonce))
 if(op=='ENRICH'){
  stratified<-prep_read_object(address(p$stratified),req$limits);model<-paste(readLines(address(p$model),warn=FALSE,encoding='UTF-8'),collapse='\n')
  all_annotations<-names(stratified$S);chosen<-if(identical(o$annotations,'ALL'))all_annotations else prep_vector(o$annotations);indices<-match(chosen,all_annotations)
  stopifnot(length(indices)>0L,!anyNA(indices),!anyDuplicated(indices),indices[1]==1L)
  for(field in c('S','V','S_Tau','V_Tau'))stratified[[field]]<-stratified[[field]][indices]
  stratified$m<-stratified$m[indices,,drop=FALSE];stratified$Prop<-stratified$Prop[indices,,drop=FALSE];stratified$Select<-stratified$Select[indices[-1]-1L,,drop=FALSE]
  write_json(list(original_annotations=as.list(all_annotations),selected_annotations=as.list(chosen),omitted_annotations=as.list(setdiff(all_annotations,chosen)),original_baseline_retained_first=TRUE,annotation_object_projected=length(indices)!=length(all_annotations)||!identical(indices,seq_along(all_annotations)),original_values_changed=FALSE),'enrichment-selection.json')
  source_S<-if(o$tau)stratified$S_Tau else stratified$S;source_V<-if(o$tau)stratified$V_Tau else stratified$V
  trait_names<-colnames(source_S[[1]]);used<-vapply(trait_names,function(t)grepl(paste0('\\b',t,'\\b'),model),logical(1));stopifnot(any(used))
  pairs<-expand.grid(trait_names,trait_names);pairs<-pairs[!duplicated(apply(pairs,1,function(x)paste(sort(x),collapse=''))),];stopifnot(nrow(pairs)==nrow(source_V[[1]]));pair_used<-pairs$Var1%in%trait_names[used]&pairs$Var2%in%trait_names[used]
  flags<-c(1,stratified$Select$V2)
  eigenvalues<-lapply(seq_along(source_S),function(i){s<-source_S[[i]][used,used,drop=FALSE];v<-source_V[[i]][pair_used,pair_used,drop=FALSE];skip<-i!=1L&&(flags[i]!=1||all(diag(s)<0));list(annotation=names(source_S)[i],source_rule_skips_annotation=skip,S_min_eigenvalue=if(skip)NULL else min(eigen(s)$values),V_min_eigenvalue=if(skip)NULL else min(eigen(v)$values),used_traits=as.list(trait_names[used]),original_inputs_changed=FALSE)})
  write_json(list(annotations=eigenvalues,smoothing_policy=o$smoothing_policy),'enrichment-input-audit.json')
  if(o$smoothing_policy=='FAIL'&&any(vapply(eigenvalues,function(v)!v$source_rule_skips_annotation&&(v$S_min_eigenvalue<=0||v$V_min_eigenvalue<=0),logical(1))))stop('Original enrichment would smooth a non-positive-definite selected model S/V;policy FAIL admits no original call')
 }
 RNGkind('Mersenne-Twister','Inversion','Rejection');set.seed(as.integer(o$seed));original_calls<-1L
 result<-withCallingHandlers({
  if(op=='MUNGE')GenomicSEM::munge(addresses(p$files),hm3=address(p$hm3),trait.names=prep_vector(o$trait_names),N=numbers(o$N),info.filter=o$info_filter,maf.filter=o$maf_filter,log.name='original',column.names=o$column_names,parallel=o$parallel,cores=as.integer(o$cores),overwrite=FALSE)
  else if(op=='LDSC')GenomicSEM::ldsc(addresses(p$traits),sample.prev=numbers(o$sample_prevalence),population.prev=numbers(o$population_prevalence),ld=address(p$ld),wld=address(p$wld),trait.names=prep_vector(o$trait_names),sep_weights=o$separate_weights,chr=as.integer(o$chromosomes),n.blocks=as.integer(o$blocks),ldsc.log='original',stand=o$standardized,select=if(is.list(o$select))prep_vector(o$select)else o$select,chisq.max=if(is.null(o$chisq_max))NA_real_ else o$chisq_max)
  else if(op=='SUMSTATS')GenomicSEM::sumstats(addresses(p$files),ref=address(p$reference),trait.names=prep_vector(o$trait_names),se.logit=prep_vector(o$se_logit),OLS=prep_vector(o$OLS),linprob=prep_vector(o$linprob),N=numbers(o$N),betas=prep_vector(o$betas),info.filter=o$info_filter,maf.filter=o$maf_filter,keep.indel=o$keep_indel,parallel=o$parallel,cores=as.integer(o$cores),ambig=o$remove_ambiguous,direct.filter=o$direction_filter)
  else if(op=='READ_FUSION')GenomicSEM::read_fusion(addresses(p$files),trait.names=prep_vector(o$trait_names),binary=prep_vector(o$binary),N=numbers(o$N),perm=o$permutation)
  else if(op=='S_LDSC')GenomicSEM::s_ldsc(addresses(p$traits),sample.prev=numbers(o$sample_prevalence),population.prev=numbers(o$population_prevalence),ld=addresses(p$ld_prefixes),wld=address(p$weights_prefix),frq=address(p$frequency_prefix),trait.names=prep_vector(o$trait_names),n.blocks=as.integer(o$blocks),ldsc.log='original',exclude_cont=o$exclude_continuous)
  else if(op=='ENRICH')GenomicSEM::enrich(stratified,model=model,params=prep_vector(o$parameters),fix=o$fix,std.lv=o$std_lv,rm_flank=o$remove_flanking,tau=o$tau,base=o$include_baseline,toler=o$tolerance,fixparam=if(length(o$fixed_parameters))prep_vector(o$fixed_parameters)else NULL)
  else stop('Unsupported original preparation operation')
 },warning=function(w){conditions[[length(conditions)+1L]]<<-list(message=conditionMessage(w),call=paste(deparse(conditionCall(w)),collapse='\n'))})
 saveRDS(result,file.path(root,'worker/original-result.rds'),version=3);snapshot('namespace-after.tsv');typed<-if(op=='S_LDSC')file.path(products,'stratified')else file.path(root,'worker/typed-result');prep_write_object(result,typed,req$limits)
 projection<-list()
 if(op=='MUNGE'){
  dir.create(file.path(products,'munged'));for(trait in prep_vector(o$trait_names)){file<-paste0(trait,'.sumstats.gz');stopifnot(file.copy(file,file.path(products,'munged',file),overwrite=FALSE))}
 }
 if(op=='LDSC'){
  labels<-prep_vector(o$trait_names);pairs<-unlist(lapply(seq_along(labels),function(i)paste(labels[i],labels[i:length(labels)],sep=':')))
  write_matrix(result$S,labels,'S.tsv');write_matrix(result$V,pairs,'V.tsv');write_matrix(result$I,labels,'I.tsv')
  if(o$standardized){write_matrix(result$S_Stand,labels,'S_Stand.tsv');write_matrix(result$V_Stand,pairs,'V_Stand.tsv')}
  projection<-list(kind='named_matrix_serialization',traits=as.list(labels),sampling_pairs=as.list(pairs),original_numeric_values_changed=FALSE,full_joint_sampling_covariance_from_one_original_ldsc_call=TRUE)
 }
 if(op=='SUMSTATS')projection<-project_associations(result,FALSE)
 if(op=='READ_FUSION')projection<-project_associations(result,TRUE)
 write_json(projection,'product-projection.json');write_json(list(original_function_calls=original_calls,operation=op,conditions=conditions),'conditions.json')
 list(status='ORIGINAL_PREPARATION_RETURNED_REVIEW_REQUIRED',original_function_calls=original_calls,returned_original_result=TRUE,typed_object=substring(typed,nchar(root)+2L),operation=op)
},error=function(e){
 try(saveRDS(e,file.path(root,'worker/original-error.rds'),version=3),silent=TRUE)
 try(write_json(list(original_function_calls=original_calls,operation=op,conditions=conditions),'conditions.json'),silent=TRUE)
 message<-conditionMessage(e);missing<-startsWith(message,'UNAVAILABLE:')||grepl("there is no package called ['\"](?:Rmpfr|gmp)['\"]",message,perl=TRUE)
 list(status=if(missing)'SKIP3_DEPENDENT_ANALYSIS_SKIPPED'else'FAIL2_NO_COMPLETE_RESULT',message=message,original_function_calls=original_calls,returned_original_result=FALSE,partial_original_attempt_retained=TRUE)
})
write_json(outcome,'worker-status.json')
if(outcome$status=='SKIP3_DEPENDENT_ANALYSIS_SKIPPED')quit(status=3)
if(outcome$status!='ORIGINAL_PREPARATION_RETURNED_REVIEW_REQUIRED')quit(status=2)
