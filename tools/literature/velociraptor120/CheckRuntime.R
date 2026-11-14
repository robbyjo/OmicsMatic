# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE)
stopifnot(length(args)==5L)
source(args[[1L]],local=TRUE)
conditions <- list();failure <- NULL
tryCatch(withCallingHandlers({
 profile <- jsonlite::fromJSON(file.path(dirname(args[[1L]]),'source-profile.json'),simplifyVector=FALSE)
 result <- auditVelociraptorRuntime(args[[2L]],args[[3L]],args[[4L]],file.path(args[[5L]],'namespace'),unlist(profile$namespace_exports,use.names=FALSE))
},warning=function(w) conditions[[length(conditions)+1L]] <<- list(kind='warning',condition=w,calls=sys.calls()),
 message=function(m) conditions[[length(conditions)+1L]] <<- list(kind='message',condition=m,calls=sys.calls())),
 error=function(e) {failure <<- e;conditions[[length(conditions)+1L]] <<- list(kind='error',condition=e,calls=sys.calls())})
saveRDS(list(conditions=conditions,error=failure),file.path(args[[5L]],'complete-readiness-conditions.rds'),version=3)
if (!is.null(failure)) {print(failure);quit(save='no',status=2L,runLast=FALSE)}
jsonlite::write_json(result,file.path(args[[5L]],'readiness.json'),pretty=TRUE,auto_unbox=TRUE,null='null',na='string',digits=17)
