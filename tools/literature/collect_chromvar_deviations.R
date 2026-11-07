# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Unchanged original R cores on explicit current dependencies; no background sampling.
args<-commandArgs(TRUE);stopifnot(length(args)==1L);p<-readLines(args[[1]],encoding='UTF-8',warn=FALSE);stopifnot(length(p)==5L)
.libPaths(c(p[1:3],.Library),include.site=FALSE)
stopifnot(paste(R.version$major,R.version$minor,sep='.')=='4.6.1',as.character(packageVersion('chromVAR'))=='1.34.1',as.character(packageVersion('Matrix'))=='1.7.5')
library(Matrix);library(chromVAR);library(jsonlite);BiocParallel::register(BiocParallel::SerialParam())
input<-read_json(p[[4L]],simplifyVector=FALSE);sources<-read_json(file.path(p[[5L]],'SOURCE-PROFILES.json'),simplifyVector=FALSE)
number<-function(v){if(is.na(v))return(if(is.nan(v))'NaN' else 'NA');if(is.infinite(v))return(if(v>0)'Inf' else '-Inf');trimws(formatC(v,format='g',digits=17,decimal.mark='.'))}
encode<-function(x){
  if(inherits(x,'Matrix'))return(encode(as.matrix(x)))
  if(is.matrix(x))return(list(dim=dim(x),rows=lapply(seq_len(nrow(x)),function(i)vapply(x[i,,drop=FALSE],number,character(1)))))
  if(is.numeric(x))return(vapply(x,number,character(1)))
  if(is.list(x))return(lapply(x,encode))
  x
}
capture<-function(fun){warnings<-character();value<-tryCatch(withCallingHandlers(fun(),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')}),error=function(e)list(error=conditionMessage(e)));list(value=encode(value),warnings=warnings)}
matrix_rows<-function(x){do.call(rbind,lapply(x,function(row)as.numeric(unlist(row))))}
results<-list()
for(profile in sources$records){
  env<-new.env(parent=asNamespace('chromVAR'))
  for(f in profile$functions){source(file.path(p[[5L]],f$resource),local=env,encoding='UTF-8');if(profile$profile=='1.34.1-package'){actual<-get(f$name,asNamespace('chromVAR'));retained<-get(f$name,env);stopifnot(identical(body(actual),body(retained)),identical(formals(actual),formals(retained)))}}
  for(case in input$cases){
    dataset<-input$datasets[[case$dataset]];counts<-as(Matrix(matrix_rows(dataset$counts),sparse=TRUE),'dgCMatrix');rownames(counts)<-paste0('peak',seq_len(nrow(counts)));colnames(counts)<-paste0('cell',seq_len(ncol(counts)))
    background<-matrix_rows(dataset$background);storage.mode(background)<-'integer';sets<-lapply(case$peak_sets,function(x)as.integer(unlist(x)));names(sets)<-paste0('motif',seq_along(sets));group<-if(is.null(case$group))NULL else factor(unlist(case$group),levels=unlist(case$group_levels));norm<-isTRUE(case$norm)
    expectations<-capture(function()env$compute_expectations_core(counts,norm=norm,group=group))
    get_expectation<-function()if(is.null(case$expectation))env$compute_expectations_core(counts,norm=norm,group=group) else as.numeric(unlist(case$expectation))
    effective<-capture(get_expectation)
    single<-capture(function()env$compute_deviations_single(sets[[1L]],counts,background,expectation=get_expectation(),intermediate_results=TRUE,threshold=case$threshold))
    core<-capture(function(){v<-env$compute_deviations_core(counts,sets,background,get_expectation());list(deviations=SummarizedExperiment::assay(v,'deviations'),z=SummarizedExperiment::assay(v,'z'),fractionMatches=SummarizedExperiment::rowData(v)$fractionMatches,fractionBackgroundOverlap=SummarizedExperiment::rowData(v)$fractionBackgroundOverlap,motif_ids=rownames(v),cell_ids=colnames(v))})
    results[[length(results)+1L]]<-list(profile=profile$profile,id=case$id,expectations=expectations,effective_expectation=effective$value,single=single,core=core)
    saveRDS(results,file.path(dirname(p[[4L]]),'partial-results.rds'))
  }
}
write_json(list(profiles=sources$records,results=results,dependencies=list(chromVAR='1.34.1',Matrix='1.7.5',historical_whole_packages_executed=FALSE,background_sampling_performed=FALSE)),file.path(dirname(p[[4L]]),'original.json'),digits=17,pretty=TRUE,auto_unbox=FALSE,na='string')
writeLines(capture.output(sessionInfo()),file.path(dirname(p[[4L]]),'sessionInfo.txt'),useBytes=TRUE)
