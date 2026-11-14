# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Invoke the complete original package; preserve full results/conditions unchanged.
args <- commandArgs(TRUE)
stopifnot(length(args)==3L)
provider <- normalizePath(args[1],mustWork=TRUE)
input <- normalizePath(args[2],mustWork=TRUE)
out <- args[3]
stopifnot(!dir.exists(out))
dir.create(out,recursive=TRUE)
ready <- jsonlite::fromJSON(file.path(provider,'READY.json'))
.libPaths(c(ready$library,ready$base_library))
suppressPackageStartupMessages(library(rstatix))
stopifnot(as.character(packageVersion('rstatix'))=='0.7.2',as.character(getRversion())=='4.5.3')
cases <- jsonlite::fromJSON(input,simplifyVector=FALSE)$cases
objects <- list();summaries <- list()
number <- function(x) {
 if(is.numeric(x)) return(x)
 switch(x,'NaN'=NaN,'+Inf'=Inf,'-Inf'=-Inf,stop('Invalid explicit numeric token'))
}
for(c in cases) {
 values <- vapply(c$values,number,numeric(1))
 groups <- vapply(c$groups,function(x)if(is.null(x))NA_character_ else x,character(1))
 data <- data.frame(value=values,group=factor(groups))
 center <- switch(c$center,MEDIAN=stats::median,MEAN=base::mean,stop('Unknown center'))
 conditions <- list();error <- NULL
 result <- tryCatch(withCallingHandlers(
  rstatix::levene_test(data,value~group,center=center),
  warning=function(w) {conditions[[length(conditions)+1L]] <<- list(class=class(w),message=conditionMessage(w),call=deparse(conditionCall(w)));cat('ORIGINAL WARNING ',conditionMessage(w),'\n',file=stderr(),sep='');invokeRestart('muffleWarning')},
  message=function(m) {conditions[[length(conditions)+1L]] <<- list(class=class(m),message=conditionMessage(m),call=deparse(conditionCall(m)));invokeRestart('muffleMessage')}),
  error=function(e) {error <<- e;NULL})
 objects[[c$id]] <- list(input=c,data=data,result=result,error=error,conditions=conditions)
 # Portable records supplement the full literal original RDS objects.
 summaries[[length(summaries)+1L]] <- list(id=c$id,status=if(is.null(error))'ORIGINAL_RETURNED' else 'ORIGINAL_ERROR',
  result=if(is.null(result))NULL else as.list(result[1,,drop=FALSE]),conditions=conditions,
  error=if(is.null(error))NULL else list(class=class(error),message=conditionMessage(error),call=deparse(conditionCall(error))))
}
saveRDS(objects,file.path(out,'complete-original-objects.rds'),version=3)
jsonlite::write_json(summaries,file.path(out,'complete-original-values.json'),pretty=TRUE,auto_unbox=TRUE,digits=17,na='string',null='null')
file.copy(input,file.path(out,'input.json'))
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
writeLines(capture.output(getS3method('leveneTest','default',envir=asNamespace('car'))),file.path(out,'original-car-levene-default.txt'))
cat('PASS_COMPLETE_ORIGINAL_RSTATIX072_LEVENE_REFERENCE cases=',length(cases),'\n',sep='')
