# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent fresh-session original public call. Does not source adapter code.
args <- commandArgs(TRUE);stopifnot(length(args)==2L)
out <- args[[2L]];stopifnot(dir.exists(out))
conditions <- list();readiness.conditions <- list();failure <- NULL;result <- NULL
withCallingHandlers({
 library(scrattch.mapping)
 stopifnot(as.character(getRversion())=="4.2.3",as.character(packageVersion("scrattch.mapping"))=="0.2",
   normalizePath(reticulate::py_config()$python)==normalizePath(Sys.getenv("RETICULATE_PYTHON")))
},warning=function(c)readiness.conditions[[length(readiness.conditions)+1L]]<<-c,
  message=function(c)readiness.conditions[[length(readiness.conditions)+1L]]<<-c)
request <- readRDS(args[[1L]])
do.call(RNGkind,as.list(request$rng$kind));assign(".Random.seed",request$rng$seed,envir=globalenv())
before <- list(kind=RNGkind(),seed=.Random.seed)
tryCatch(withCallingHandlers({
 result <- do.call(scrattch.mapping::run_mapping_on_taxonomy,request$arguments)
},warning=function(c)conditions[[length(conditions)+1L]]<<-list(kind="warning",condition=c),
  message=function(c)conditions[[length(conditions)+1L]]<<-list(kind="message",condition=c)),
 error=function(c){failure<<-c;conditions[[length(conditions)+1L]]<<-list(kind="error",condition=c)})
after <- list(kind=RNGkind(),seed=.Random.seed)
if(is.null(failure))saveRDS(result,file.path(out,"complete-result.rds"),version=3)
saveRDS(list(before=before,after=after),file.path(out,"complete-rng-before-after.rds"),version=3)
saveRDS(list(error=failure,conditions=conditions),file.path(out,"complete-conditions.rds"),version=3)
saveRDS(list(conditions=readiness.conditions,session=sessionInfo()),file.path(out,"complete-readiness-and-session.rds"),version=3)
jsonlite::write_json(list(returned=is.null(failure),conditions=length(conditions),
 error=if(is.null(failure))NULL else conditionMessage(failure)),file.path(out,"ORIGINAL-CASE.json"),
 pretty=TRUE,auto_unbox=TRUE,null="null")
cat("ORIGINAL_PUBLIC_CALL_CAPTURED\n")
