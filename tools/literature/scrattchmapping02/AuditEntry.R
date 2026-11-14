# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE);stopifnot(length(args)==1L)
control <- jsonlite::fromJSON(args[[1L]],simplifyVector=FALSE)
.libPaths(c(control$library,control$base_library))
source(control$audit)
conditions <- list();failure <- NULL
tryCatch(withCallingHandlers(audit.mapping02(control,"audit"),
 warning=function(c)conditions[[length(conditions)+1L]]<<-c,
 message=function(c)conditions[[length(conditions)+1L]]<<-c),error=function(c)failure<<-c)
saveRDS(list(conditions=conditions,error=failure),file.path(control$output,"audit-conditions.rds"),version=3)
jsonlite::write_json(list(status=if(is.null(failure))"PASS_ORIGINAL_RUNTIME_AUDIT_ONLY"else"SKIP3_RUNTIME_AUDIT_FAILED",
 original_warning_count=sum(vapply(conditions,function(c)inherits(c,"warning"),logical(1))),
 error=if(is.null(failure))NULL else conditionMessage(failure),analysis_executed=FALSE),
 file.path(control$output,"audit-status.json"),pretty=TRUE,auto_unbox=TRUE,null="null")
if(!is.null(failure))quit(save="no",status=3L,runLast=FALSE)
