# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Typed original-package calls. No upstream source or fitted values are replaced.
options(warn=1,stringsAsFactors=FALSE)
warnings_seen=character()
status=function(kind,cause='')jsonlite::write_json(list(status=kind,cause=cause),'status.json',auto_unbox=TRUE,pretty=TRUE)
skip=function(cause)stop(structure(list(message=cause,call=NULL),class=c('unavailable','error','condition')))
fingerprint=function(f){p=tempfile();on.exit(unlink(p));writeLines(c(deparse(formals(f),width.cutoff=500),deparse(body(f),width.cutoff=500)),p,useBytes=TRUE);unname(tools::md5sum(p))}
main=function(){
 if(!requireNamespace('jsonlite',quietly=TRUE)){writeLines('{"status":"SKIP3","cause":"jsonlite unavailable; restore declared exact runtime"}','status.json');cat('SKIP3: jsonlite unavailable; restore declared runtime\n',file=stderr());quit(status=3)}
 libraries=unlist(jsonlite::read_json('runtime-paths.json'));.libPaths(libraries)
 request=jsonlite::read_json('../effective-request.json',simplifyVector=TRUE)
 profile=jsonlite::read_json('../source/SOURCE-PROFILE.json',simplifyVector=FALSE)
 guard=function(){
  if(as.character(getRversion())!=profile$r_version||R.version$platform!=profile$platform)skip('Exact R version/platform unavailable; restore declared qualified runtime')
  for(pkg in names(profile$package_versions)){
   if(!requireNamespace(pkg,quietly=TRUE))skip(paste('Required package unavailable:',pkg,'; restore exact qualified runtime'))
   if(as.character(packageVersion(pkg))!=profile$package_versions[[pkg]])skip(paste('Required exact package version unavailable:',pkg,profile$package_versions[[pkg]],'; no adjacent edition substituted'))
  }
  for(pkg in names(profile$package_files_md5)){
   base=find.package(pkg);expected=profile$package_files_md5[[pkg]];actual=sort(list.files(base,recursive=TRUE,all.files=TRUE,no..=TRUE));actual=actual[!file.info(file.path(base,actual))$isdir]
   if(!identical(sort(names(expected)),actual))stop(paste('Core package member population changed:',pkg))
   hashes=tools::md5sum(file.path(base,names(expected)));if(!identical(unname(hashes),unname(unlist(expected))))stop(paste('Core package bytes changed:',pkg))
  }
  for(name in names(profile$source_function_text_md5))if(fingerprint(get(name,asNamespace('WGCNA')))!=profile$source_function_text_md5[[name]])stop(paste('Original function body/formals changed:',name))
  invisible(TRUE)
 }
 guard();suppressPackageStartupMessages(library(WGCNA));suppressPackageStartupMessages(library(qvalue))
 matrix_input=function(name){tab=read.delim(file.path('../inputs',name),header=TRUE,sep='\t',quote='',comment.char='',check.names=FALSE,colClasses='character',na.strings=character(),encoding='UTF-8');x=matrix(as.numeric(as.matrix(tab[,-1,drop=FALSE])),nrow=nrow(tab),dimnames=list(tab[[1]],names(tab)[-1]));x}
 exports=list()
 emit=function(x,name,ids=rownames(x)){
  x=as.data.frame(x,check.names=FALSE);if(is.null(ids))ids=as.character(seq_len(nrow(x)));stopifnot(length(ids)==nrow(x));x=cbind(data.frame(id=ids,check.names=FALSE),x)
  exports[[name]]<<-list(rows=nrow(x),numeric_columns=sum(vapply(x,is.numeric,TRUE)),missing=sum(is.na(x)),infinite=sum(vapply(x,function(z)if(is.numeric(z))sum(is.infinite(z))else 0,0)))
  write.table(x,file=file.path('../results',name),sep='\t',row.names=FALSE,col.names=TRUE,quote=FALSE,na='NA',eol='\n')
 }
 options(digits=17);x=matrix_input('matrix');op=request$operation;original=list()
 if(op=='RANK_PVALUE'){
  na_last=switch(request$na_last,keep='keep',first=FALSE,last=TRUE)
  weights=if(is.null(request$weights))NULL else request$weights
  original$result=WGCNA::rankPvalue(x,columnweights=weights,na.last=na_last,ties.method=request$ties,calculateQvalue=request$calculate_qvalue,pValueMethod=request$p_value_method)
  emit(original$result,'rank-pvalues.tsv',rownames(x))
 }else if(op=='CONNECTIVITY'){
  colors=read.delim('../inputs/modules',header=TRUE,sep='\t',quote='',comment.char='',colClasses='character',na.strings=character(),check.names=FALSE)[[2]]
  original$result=WGCNA::intramodularConnectivity(x,colors,scaleByMax=request$scale_by_max);emit(original$result,'connectivity.tsv',rownames(x))
 }else if(op=='SIGNED_KME'){
  me=matrix_input('eigengenes');stopifnot(identical(rownames(x),rownames(me)))
  cor_options=if(request$cor_type=='bicor')paste0("use = 'p', maxPOutliers = ",format(request$max_p_outliers,digits=17))else "use = 'p'"
  original$result=WGCNA::signedKME(x,me,corFnc=if(request$cor_type=='bicor')'bicor'else'cor',corOptions=cor_options);emit(original$result,'membership.tsv',colnames(x))
 }else if(op=='NETWORK'){
  set.seed(request$seed);original$rng_before=.Random.seed
  qc=WGCNA::goodSamplesGenes(x,verbose=3);original$qc=qc;saveRDS(original,'objects.rds',version=3)
  if(!qc$allOK)stop('Original goodSamplesGenes requires filtering; no samples/features removed automatically. Inspect objects.rds and prepare explicit input')
  original$fit=WGCNA::blockwiseModules(x,power=request$power,networkType=request$network_type,TOMType=request$tom_type,corType=request$cor_type,maxPOutliers=request$max_p_outliers,maxBlockSize=ncol(x),deepSplit=request$deep_split,minModuleSize=request$min_module_size,mergeCutHeight=request$merge_cut_height,detectCutHeight=request$detect_cut_height,reassignThreshold=request$reassign_threshold,pamRespectsDendro=request$pam_respects_dendro,numericLabels=TRUE,randomSeed=request$seed,nThreads=request$threads,saveTOMs=TRUE,saveTOMFileBase='original-TOM',verbose=3)
  original$rng_after=.Random.seed;saveRDS(original,'objects.rds',version=3)
  original$adjacency=WGCNA::adjacency(x,power=request$power,type=request$network_type,corFnc=if(request$cor_type=='bicor')'bicor'else'cor',corOptions=if(request$cor_type=='bicor')list(use='p',maxPOutliers=request$max_p_outliers)else list(use='p'))
  colors=WGCNA::labels2colors(original$fit$colors)
  original$connectivity=WGCNA::intramodularConnectivity(original$adjacency,colors,scaleByMax=FALSE)
  original$membership=WGCNA::signedKME(x,original$fit$MEs,corFnc=if(request$cor_type=='bicor')'bicor'else'cor',corOptions=if(request$cor_type=='bicor')paste0("use = 'p', maxPOutliers = ",format(request$max_p_outliers,digits=17))else "use = 'p'")
  emit(data.frame(label=original$fit$colors,module=colors),'modules.tsv',colnames(x));emit(original$fit$MEs,'eigengenes.tsv',rownames(x));emit(original$membership,'membership.tsv',colnames(x));emit(original$connectivity,'connectivity.tsv',colnames(x));emit(original$adjacency,'adjacency.tsv',colnames(x))
 }else stop('Unsupported typed operation')
 saveRDS(original,'objects.rds',version=3);guard()
 writeLines(warnings_seen,'warnings.txt',useBytes=TRUE)
 jsonlite::write_json(list(profile=profile$profile,operation=op,input_rows=nrow(x),input_columns=ncol(x),input_missing=sum(is.na(x)),input_infinite=sum(is.infinite(x)),exports=exports,warnings=as.list(warnings_seen),original_objects='worker/objects.rds',source_guard='pre/post exact core file populations/MD5, original function bodies/formals and package versions',network_single_block=op=='NETWORK',whole_paper_reproduced=FALSE),'report.json',auto_unbox=TRUE,pretty=TRUE)
 status('COMPLETE')
}
tryCatch(withCallingHandlers(main(),warning=function(w){warnings_seen<<-c(warnings_seen,conditionMessage(w));cat('ORIGINAL WARNING:',conditionMessage(w),'\n',file=stderr())}),error=function(e){if(requireNamespace('jsonlite',quietly=TRUE)){writeLines(warnings_seen,'warnings.txt',useBytes=TRUE);status(if(inherits(e,'unavailable'))'SKIP3'else'FAIL2',conditionMessage(e))};cat(if(inherits(e,'unavailable'))'SKIP3 - DEPENDENT ANALYSIS SKIPPED: 'else'FAIL2 - NO COMPLETE RESULT: ',conditionMessage(e),'\nRestore exact permitted prerequisites, or correct the named data/model/capacity/integrity failure and retry NEW output. Preserve partials and warnings.\n',file=stderr());quit(status=if(inherits(e,'unavailable'))3 else 2)})
