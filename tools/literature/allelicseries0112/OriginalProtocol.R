# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Capture unchanged upstream calls; classification never changes a returned value.
original_exports <- c('ASBT','ASBTSS','ASKAT','ASKATSS','Aggregator','BaselineSS','COAST','COASTSS','CalcSumstats','Comparator','Counts','DGP','GenomicControl','OLS','SumCountSS','isPD')
validate_original_arguments <- function(request) {
  if (!is.list(request) || (length(request)>0L && (is.null(names(request)) || anyNA(names(request)) || !all(nzchar(names(request))) || anyDuplicated(names(request)))))
    stop('Supply a complete list with unique, nonmissing, nonempty argument names. No original call executed.')
  invisible(TRUE)
}
original_rng_state <- function() {
  if(exists('.Random.seed',envir=.GlobalEnv,inherits=FALSE))get('.Random.seed',envir=.GlobalEnv) else NULL
}
original_has_nonfinite <- function(x) {
  if(isS4(x))return(any(vapply(slotNames(x),function(n)original_has_nonfinite(slot(x,n)),logical(1))))
  if(is.list(x))return(any(vapply(x,original_has_nonfinite,logical(1))))
  if(is.numeric(x)||is.complex(x))return(any(!is.finite(x)))
  FALSE
}
original_zero_classification <- function(fun,request,value) {
  if(!fun %in% c('BaselineSS','SumCountSS') || !is.numeric(value) || length(value)!=1L || is.na(value) || value!=0)
    return(list(kind='ordinary-return',reason=''))
  # Resolve abbreviated R argument names without reevaluating language objects.
  matched <- tryCatch(match.call(definition=getExportedValue('AllelicSeries',fun),call=as.call(c(list(as.name(fun)),request))),error=function(e)NULL)
  se <- if(is.null(matched))NULL else matched[['se']]
  if((is.numeric(se)||is.logical(se)) && !is.object(se)) {
    if(any(se>0,na.rm=TRUE))return(list(kind='ordinary-return',reason=''))
    return(list(kind='no-positive-SE-scalar-zero',reason='The original low-level function returned scalar zero because no positive standard errors remained. This is not an association p-value. Required action: retain the original return, check upstream summary-statistic generation, and supply valid positive standard errors before interpreting an association; retry NEW output paths.'))
  }
  if(!is.null(matched) && isTRUE(matched[['return_beta']]))
    return(list(kind='no-positive-SE-scalar-zero',reason='The original low-level function returned scalar zero instead of the requested effects object. Required action: retain the original return and correct upstream standard errors before interpreting an association; retry NEW output paths.'))
  list(kind='unproven-low-level-zero',reason='The original low-level function returned zero, but its scalar-zero sentinel status cannot be established from the typed arguments. Required action: retain the raw return and use explicit numeric standard errors to distinguish a valid tail probability from the no-positive-SE return; retry NEW output paths. No association is admitted meanwhile.')
}
capture_original_call <- function(fun,request,seed_argument) {
  stopifnot(fun %in% original_exports);validate_original_arguments(request)
  before_rng<-original_rng_state();warnings<-list();messages<-list()
  value<-tryCatch(withCallingHandlers(do.call(getExportedValue('AllelicSeries',fun),request),
    warning=function(w){warnings[[length(warnings)+1L]]<<-w;invokeRestart('muffleWarning')},
    message=function(m){messages[[length(messages)+1L]]<<-m;invokeRestart('muffleMessage')}),error=function(e)e)
  after_rng<-original_rng_state();failed<-inherits(value,'error')
  nonfinite<-if(failed)FALSE else original_has_nonfinite(value)
  zero<-if(failed)list(kind='ordinary-return',reason='') else original_zero_classification(fun,request,value)
  status<-if(failed)'ORIGINAL_ERROR_NO_RESULT' else if(nonfinite)'ORIGINAL_NONFINITE_RETURN_NO_ANALYSIS_ADMISSION' else if(zero$kind=='no-positive-SE-scalar-zero')'ORIGINAL_SCALAR_ZERO_NO_ANALYSIS_ADMISSION' else if(zero$kind=='unproven-low-level-zero')'ORIGINAL_ZERO_UNPROVEN_NO_ANALYSIS_ADMISSION' else 'ORIGINAL_RETURN_CAPTURED'
  list(function_name=fun,arguments=request,seed_argument=seed_argument,RNG_kind=RNGkind(),RNG_before=before_rng,RNG_after=after_rng,result=value,warnings=warnings,messages=messages,status=status,source_return_kind=zero$kind,admission_reason=zero$reason,analysis_admitted=status=='ORIGINAL_RETURN_CAPTURED')
}
write_original_table <- function(tab,path) {
  # Human-readable projection; RDS retains complete types, attributes and dimnames.
  text<-if(is.data.frame(tab))tab else as.data.frame(tab,optional=TRUE)
  for(i in seq_along(text))if(is.numeric(text[[i]]))text[[i]]<-sprintf('%.17g',text[[i]])
  write.table(text,path,quote=TRUE,sep='\t',row.names=TRUE,col.names=NA,na='NA',qmethod='double',fileEncoding='UTF-8')
}
write_original_packet <- function(packet,out) {
  failed<-inherits(packet$result,'error');value<-packet$result
  saveRDS(packet,file.path(out,'complete-original-return.rds'),version=3)
  if(!failed) {
    saveRDS(value,file.path(out,'original-value.rds'),version=3)
    if(inherits(value,'COAST')) {
      for(name in c('Betas','Counts','Pvals'))if(!is.null(slot(value,name)))write_original_table(slot(value,name),file.path(out,paste0(name,'.tsv')))
    } else if(is.data.frame(value)||is.matrix(value))write_original_table(value,file.path(out,'returned-table.tsv'))
    writeLines(capture.output(str(value)),file.path(out,'returned-structure-preview.txt'))
  }
  writeLines(vapply(packet$warnings,conditionMessage,character(1)),file.path(out,'original-warnings.txt'))
  writeLines(vapply(packet$messages,conditionMessage,character(1)),file.path(out,'original-messages.txt'))
  if(failed)writeLines(c(class(value),conditionMessage(value),deparse(conditionCall(value))),file.path(out,'original-error.txt'))
  writeLines(packet$admission_reason,file.path(out,'RETURN_ADMISSION_REASON.txt'))
  writeLines(c(packet$status,paste(class(value),collapse=','),as.character(length(packet$warnings)),as.character(length(packet$messages))),file.path(out,'RETURN_STATUS.txt'))
}
