# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Deliberately altered COPY controls; original records remain untouched.
args <- commandArgs(TRUE);stopifnot(length(args)==2L)
root <- args[[1L]];out <- args[[2L]];stopifnot(dir.exists(out))
error <- readRDS(file.path(root,"scrattch02-workflow-references-2/references/no_cells/complete-conditions.rds"))$error
repeat.error <- readRDS(file.path(root,"scrattch02-original-no-cells-repeat-1/original/complete-conditions.rds"))$error
adapter.error <- readRDS(file.path(root,"scrattch02-workflow-check-2/no_cells/analysis/original/complete-conditions.rds"))$error
saved <- function(x)serialize(x,NULL,version=3)
baseline <- saved(error)
stopifnot(identical(baseline,saved(repeat.error)),identical(baseline,saved(adapter.error)))
altered <- list()
x <- error;x$message <- paste0(x$message," [ALTERED CONTROL]");altered$message <- x
x <- error;class(x) <- c("altered_control",class(x));altered$class <- x
x <- error;attr(x,"new_attribute") <- "must be detected";altered$attribute <- x
x <- error;x$call <- quote(altered_call());altered$call <- x
x <- error;f <- x$call[[1L]];body(f) <- quote(stop("altered body control"));x$call[[1L]] <- f;altered$closure_body <- x
x <- error;f <- x$call[[1L]];formals(f) <- alist(altered=);x$call[[1L]] <- f;altered$closure_formals <- x
x <- error;f <- x$call[[1L]];e <- new.env(parent=parent.env(environment(f)));e$addr <- get("addr",environment(f));e$added_binding <- "must be detected";environment(f) <- e;x$call[[1L]] <- f;altered$closure_environment_binding <- x
x <- error;x$numeric_control <- c(NA_real_,NaN,Inf,-Inf,-0);altered$numeric_payload_added <- x
results <- list(original_repeat_same_complete_saved_object=TRUE,adapter_same_complete_saved_object=TRUE)
for(name in names(altered)) {
  results[[paste0("reject_",name)]] <- !identical(baseline,saved(altered[[name]]))
  saveRDS(altered[[name]],file.path(out,paste0("deliberately-altered-",name,".rds")),version=3)
}
stopifnot(all(unlist(results,use.names=FALSE)))
saveRDS(list(original=error,repeat_original=repeat.error,adapter=adapter.error),file.path(out,"complete-unaltered-condition-controls.rds"),version=3)
jsonlite::write_json(list(status="PASS_COMPLETE_SAVED_CONDITION_TAMPERING_CONTROLS",results=results,
 fields_deleted_or_coerced_for_comparison=FALSE,live_pointer_address_identity_claimed=FALSE,
 original_records_modified=FALSE,new_family_credit=0L,whole_goal_complete=FALSE),
 file.path(out,"COMPLETED.json"),pretty=TRUE,auto_unbox=TRUE)
cat("PASS_COMPLETE_SAVED_CONDITION_TAMPERING_CONTROLS\n")
