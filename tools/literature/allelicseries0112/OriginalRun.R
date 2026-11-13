# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Invoke an unchanged original function with a complete, retained named R argument list.
args <- commandArgs(trailingOnly=TRUE)
stopifnot(length(args)==6L)
lib<-normalizePath(args[1],mustWork=TRUE);base<-normalizePath(args[2],mustWork=TRUE)
fun<-args[3];input<-args[4];out<-normalizePath(args[5],mustWork=TRUE);seed<-args[6]
.libPaths(c(lib,base));suppressPackageStartupMessages(library(AllelicSeries,lib.loc=lib))
stopifnot(as.character(getRversion())=='4.5.3',packageDescription('AllelicSeries',lib.loc=lib)$Version=='0.1.1.2')
exports<-c('ASBT','ASBTSS','ASKAT','ASKATSS','Aggregator','BaselineSS','COAST','COASTSS','CalcSumstats','Comparator','Counts','DGP','GenomicControl','OLS','SumCountSS','isPD')
stopifnot(fun %in% exports)
request<-readRDS(input)
stopifnot(is.list(request),length(request)==0L || (!is.null(names(request)) && all(nzchar(names(request))) && !anyDuplicated(names(request))))
if(seed!='MISSING')set.seed(as.integer(seed))
before_rng<-if(exists('.Random.seed',envir=.GlobalEnv,inherits=FALSE))get('.Random.seed',envir=.GlobalEnv) else NULL
warnings<-list();messages<-list()
value<-tryCatch(withCallingHandlers(do.call(getExportedValue('AllelicSeries',fun),request),
  warning=function(w){warnings[[length(warnings)+1L]]<<-w;invokeRestart('muffleWarning')},
  message=function(m){messages[[length(messages)+1L]]<<-m;invokeRestart('muffleMessage')}),error=function(e)e)
failed<-inherits(value,'error')
after_rng<-if(exists('.Random.seed',envir=.GlobalEnv,inherits=FALSE))get('.Random.seed',envir=.GlobalEnv) else NULL
nonfinite<-function(x){
  if(isS4(x))return(any(vapply(slotNames(x),function(n)nonfinite(slot(x,n)),logical(1))))
  if(is.list(x))return(any(vapply(x,nonfinite,logical(1))))
  if(is.numeric(x)||is.complex(x))return(any(!is.finite(x)))
  FALSE
}
has_nonfinite<-if(failed)FALSE else nonfinite(value)
status<-if(failed)'ORIGINAL_ERROR_NO_RESULT' else if(has_nonfinite)'ORIGINAL_NONFINITE_RETURN_NO_ANALYSIS_ADMISSION' else 'ORIGINAL_RETURN_CAPTURED'
saveRDS(list(function_name=fun,arguments=request,seed_argument=seed,RNG_kind=RNGkind(),RNG_before=before_rng,RNG_after=after_rng,result=value,warnings=warnings,messages=messages,status=status),file.path(out,'complete-original-return.rds'),version=3)
if(!failed){
  saveRDS(value,file.path(out,'original-value.rds'),version=3)
  if(inherits(value,'COAST')){
    for(name in c('Betas','Counts','Pvals')){
      tab<-slot(value,name)
      if(!is.null(tab))write.table(tab,file.path(out,paste0(name,'.tsv')),quote=TRUE,sep='\t',row.names=FALSE,na='NA')
    }
  }else if(is.data.frame(value)||is.matrix(value))write.table(value,file.path(out,'returned-table.tsv'),quote=TRUE,sep='\t',row.names=TRUE,col.names=NA,na='NA')
  writeLines(capture.output(str(value)),file.path(out,'returned-structure-preview.txt'))
}
writeLines(vapply(warnings,conditionMessage,character(1)),file.path(out,'original-warnings.txt'))
writeLines(vapply(messages,conditionMessage,character(1)),file.path(out,'original-messages.txt'))
if(failed)writeLines(c(class(value),conditionMessage(value),deparse(conditionCall(value))),file.path(out,'original-error.txt'))
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
writeLines(c(status,paste(class(value),collapse=','),as.character(length(warnings)),as.character(length(messages))),file.path(out,'RETURN_STATUS.txt'))
cat(status,'\n')
quit(status=if(failed||has_nonfinite)2L else 0L)
