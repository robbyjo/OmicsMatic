# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE);stopifnot(length(args)==4L)
library <- args[[2L]];source.path <- args[[3L]];out <- args[[4L]]
.libPaths(c(library,.libPaths()));source(args[[1L]],local=TRUE)
conditions <- list();failure <- NULL;result <- NULL
tryCatch(withCallingHandlers({result <- auditVelociraptorPlots(library,source.path,file.path(out,'namespace'))},warning=function(w)conditions[[length(conditions)+1L]] <<- list(kind='warning',object=w,calls=sys.calls()),message=function(m)conditions[[length(conditions)+1L]] <<- list(kind='message',object=m,calls=sys.calls())),error=function(e){failure <<- e;conditions[[length(conditions)+1L]] <<- list(kind='error',object=e,calls=sys.calls())})
saveRDS(list(error=failure,conditions=conditions),file.path(out,'COMPLETE-PLOT-READINESS-CONDITIONS.rds'),version=3)
if (!is.null(failure)) {print(failure);quit(save='no',status=2L,runLast=FALSE)}
jsonlite::write_json(result,file.path(out,'PLOT-READINESS.json'),pretty=TRUE,auto_unbox=TRUE,null='null',digits=17)
