# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==4L)
.libPaths(c(args[1],args[2]));suppressPackageStartupMessages(library(AllelicSeries,lib.loc=args[1]))
reference_root<-args[3];actual_root<-args[4];counts<-list();objects<-0L;errors<-0L
conditions_equal<-function(a,b)identical(lapply(a,function(x)list(class=class(x),message=conditionMessage(x))),lapply(b,function(x)list(class=class(x),message=conditionMessage(x))))
for(group in c('coast1000','mixed','stochastic','baseline-zero','sum-zero')) {
  ref<-readRDS(file.path(reference_root,paste0(group,'-reference.rds')))
  index<-readRDS(file.path(actual_root,group,'BATCH_INDEX.rds'))
  runtime<-readRDS(file.path(actual_root,group,'BATCH_RUNTIME.rds'))
  stopifnot(identical(index$identifier,ref$identifiers),identical(runtime$requested_identifiers,ref$identifiers),nrow(index)==length(ref$requests))
  accepted<-0L
  for(i in seq_along(ref$requests)) {
    expected<-ref$reference[[i]];value<-expected$result
    packet<-readRDS(file.path(actual_root,group,sprintf('entries/%09d',i),'complete-original-return.rds'))
    stopifnot(identical(packet$arguments,ref$requests[[i]]),identical(packet$entry_identifier,ref$identifiers[[i]]),identical(packet$entry_ordinal,i),identical(packet$function_name,ref$function_name),identical(packet$RNG_before,expected$RNG_before),identical(packet$RNG_after,expected$RNG_after),conditions_equal(packet$warnings,expected$warnings),conditions_equal(packet$messages,expected$messages))
    failed<-inherits(value,'error')
    if(failed){stopifnot(identical(class(packet$result),class(value)),identical(conditionMessage(packet$result),conditionMessage(value)));errors<-errors+1L}
    else{stopifnot(identical(packet$result,value));objects<-objects+1L}
    no_positive<-group %in% c('baseline-zero','sum-zero') && names(ref$requests)[i] %in% c('zero','negative','missing','partial-name','effects-zero')
    bad_numeric<-!failed && inherits(value,'COAST') && any(!is.finite(c(value@Pvals$pval,value@Betas$beta,value@Betas$se)))
    expected_status<-if(failed)'ORIGINAL_ERROR_NO_RESULT' else if(bad_numeric)'ORIGINAL_NONFINITE_RETURN_NO_ANALYSIS_ADMISSION' else if(no_positive)'ORIGINAL_SCALAR_ZERO_NO_ANALYSIS_ADMISSION' else 'ORIGINAL_RETURN_CAPTURED'
    stopifnot(identical(packet$status,expected_status),identical(packet$analysis_admitted,expected_status=='ORIGINAL_RETURN_CAPTURED'))
    if(packet$analysis_admitted)accepted<-accepted+1L
  }
  counts[[group]]<-list(requested=length(ref$requests),accepted=accepted,unadmitted=length(ref$requests)-accepted)
}
stopifnot(counts$coast1000$requested==1000L,counts$coast1000$accepted==1000L,counts$stochastic$accepted==3L,counts[['baseline-zero']]$accepted==2L,counts[['sum-zero']]$accepted==2L)
saveRDS(list(status='PASS_COMPLETE_INDEPENDENT_ORIGINAL_BATCH_OBJECTS_IDENTIFIERS_CONDITIONS_AND_RNG',counts=counts,complete_original_returned_objects=objects,original_error_objects_with_class_message_comparison=errors),file.path(actual_root,'COMPARISON.rds'),version=3)
writeLines(c('PASS_COMPLETE_INDEPENDENT_ORIGINAL_BATCH_OBJECTS_IDENTIFIERS_CONDITIONS_AND_RNG',paste('complete_original_objects',objects),paste('original_errors',errors),vapply(names(counts),function(n)paste(n,counts[[n]]$requested,counts[[n]]$accepted,counts[[n]]$unadmitted,sep='\t'),character(1))),file.path(actual_root,'COMPARISON.txt'))
cat(readLines(file.path(actual_root,'COMPARISON.txt')),sep='\n')
