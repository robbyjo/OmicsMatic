# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==8L)
lib<-normalizePath(args[1],mustWork=TRUE);base<-normalizePath(args[2],mustWork=TRUE)
fun<-args[3];input<-args[4];out<-normalizePath(args[5],mustWork=TRUE);seed<-args[6];maximum<-as.integer(args[7])
source(args[8],local=TRUE)
.libPaths(c(lib,base));suppressPackageStartupMessages(library(AllelicSeries,lib.loc=lib))
stopifnot(as.character(getRversion())=='4.5.3',packageDescription('AllelicSeries',lib.loc=lib)$Version=='0.1.1.2',fun %in% original_exports)
requests<-readRDS(input);ids<-names(requests)
reject_batch<-function(message){writeLines(message,file.path(out,'BATCH_INPUT_FAILURE.txt'));stop(message)}
if(!is.list(requests)||length(requests)<1L||length(requests)>maximum||is.null(ids)||anyNA(ids)||!all(nzchar(ids))||anyDuplicated(ids))
  reject_batch('Batch requires1..maximum-calls uniquely named entries with literal nonmissing/nonempty identifiers. No original call executed; no prefix admitted. Required action: correct the full named batch or explicit capacity and retry NEW paths.')
for(i in seq_along(requests)) {
  bad<-tryCatch({validate_original_arguments(requests[[i]]);NULL},error=function(e)conditionMessage(e))
  if(!is.null(bad))reject_batch(paste('Invalid arguments at entry',i,encodeString(ids[[i]],quote='"'),bad,'No original call executed. Correct this entry and retry NEW paths.'))
}
n<-length(requests);writeLines(as.character(n),file.path(out,'REQUESTED_COUNT.txt'))
if(seed!='MISSING')set.seed(as.integer(seed))
start_rng<-original_rng_state();rows<-vector('list',n);dir.create(file.path(out,'entries'))
index_file<-file(file.path(out,'BATCH_INDEX.tsv'),open='wt',encoding='UTF-8')
report<-file(file.path(out,'BATCH_WARNINGS.md'),open='wt',encoding='UTF-8')
writeLines(c('# Original AllelicSeries batch conditions','',
  'Every requested entry is retained in input order. A failed/nonfinite/scalar-zero entry prevents complete-batch admission. Read each recorded reason, preserve all outputs, correct the named cause and retry NEW paths.',
  'Identifiers below use R quoted display; complete typed identifiers and values remain in RDS.',''),report)
for(i in seq_len(n)) {
  relative<-sprintf('entries/%09d',i);directory<-file.path(out,relative);dir.create(directory)
  packet<-capture_original_call(fun,requests[[i]],paste0('shared stream; initial seed=',seed))
  packet$entry_ordinal<-i;packet$entry_identifier<-ids[[i]]
  write_original_packet(packet,directory)
  row<-data.frame(ordinal=i,identifier=ids[[i]],directory=relative,status=packet$status,analysis_admitted=packet$analysis_admitted,warnings=length(packet$warnings),messages=length(packet$messages),source_return_kind=packet$source_return_kind,stringsAsFactors=FALSE,check.names=FALSE)
  rows[[i]]<-row
  write.table(row,index_file,sep='\t',quote=TRUE,row.names=FALSE,col.names=i==1L,qmethod='double',na='NA');flush(index_file)
  if(!packet$analysis_admitted || length(packet$warnings)>0L || length(packet$messages)>0L) {
    writeLines(c(paste0('## Entry ',sprintf('%09d',i)),paste0('    Identifier: ',encodeString(ids[[i]],quote='"')),paste0('    Status: ',packet$status),packet$admission_reason),report)
    for(w in packet$warnings)writeLines(paste0('    Original warning: ',encodeString(conditionMessage(w))),report)
    for(m in packet$messages)writeLines(paste0('    Original message: ',encodeString(conditionMessage(m))),report)
    if(inherits(packet$result,'error'))writeLines(paste0('    Original error: ',encodeString(conditionMessage(packet$result))),report)
    writeLines('',report);flush(report)
  }
  cat('ENTRY',i,packet$status,'\n');flush.console()
}
close(index_file);close(report)
index<-do.call(rbind,rows);saveRDS(index,file.path(out,'BATCH_INDEX.rds'),version=3)
saveRDS(list(function_name=fun,seed_argument=seed,seed_policy='Set once before first entry; preserve input order and a shared original RNG stream.',RNG_kind=RNGkind(),RNG_before=start_rng,RNG_after=original_rng_state(),requested_identifiers=ids),file.path(out,'BATCH_RUNTIME.rds'),version=3)
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
failed<-sum(!index$analysis_admitted);warned<-sum(index$warnings>0L)
status<-if(failed)'ORIGINAL_BATCH_COMPLETE_WITH_UNADMITTED_ENTRIES' else 'ORIGINAL_BATCH_ALL_RETURNS_CAPTURED'
writeLines(c(status,as.character(n),as.character(n-failed),as.character(failed),as.character(warned),as.character(sum(index$warnings)),as.character(sum(index$messages))),file.path(out,'BATCH_STATUS.txt'))
cat(status,'requested=',n,'failed=',failed,'\n');quit(status=if(failed)2L else 0L)
