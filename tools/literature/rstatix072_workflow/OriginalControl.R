# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# The exact same caller script runs via the adapter and independent direct R.
cases <- readRDS(file.path(input_directory,'cases.rds'))
stopifnot(length(cases)==248L,length(unique(names(cases)))==248L)
complete <- list();index <- list()
run_one <- function(case) {
 # Formula execution environments contain only this case's data, rather than
 # the entire growing result population. They remain present in original RDS.
 env <- list2env(list(data=case$data),parent=globalenv())
 set.seed(case$seed)
 before <- .Random.seed;conditions <- list()
 result <- withCallingHandlers(eval(case$call,envir=env),
  warning=function(w) {conditions[[length(conditions)+1L]] <<- list(kind='warning',object=w,class=class(w),message=conditionMessage(w),call=conditionCall(w))},
  message=function(m) {conditions[[length(conditions)+1L]] <<- list(kind='message',object=m,class=class(m),message=conditionMessage(m),call=conditionCall(m))})
 list(value=result,conditions=conditions,seed_before=before,seed_after=.Random.seed,original_call=case$call,id=case$id)
}
for(i in seq_along(cases)) {
 case <- cases[[i]];record <- run_one(case);complete[[case$id]] <- record
 # Every returned row/column is retained; full attributes/arguments/formulas
 # remain in RDS. TSV is a supplementary display, not an RDS replacement.
 value <- record$value
 stopifnot(is.data.frame(value),nrow(value)>0L)
 write.table(value,file.path(result_directory,sprintf('%04d.tsv',i)),sep='\t',row.names=FALSE,quote=TRUE,na='NA')
 index[[i]] <- data.frame(ordinal=i,id=case$id,rows=nrow(value),columns=ncol(value),
  classes=paste(class(value),collapse=';'),warnings=sum(vapply(record$conditions,function(x)x$kind=='warning',logical(1))),stringsAsFactors=FALSE)
}
saveRDS(complete,file.path(result_directory,'complete-original-results.rds'),version=3)
write.table(do.call(rbind,index),file.path(result_directory,'complete-index.tsv'),sep='\t',row.names=FALSE,quote=TRUE)
stopifnot(length(complete)==248L)
analysis_complete <- 'ALL_248_ORIGINAL_RSTATIX072_CALLS_RETURNED'
