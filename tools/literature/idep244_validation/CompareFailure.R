# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Complete original failed-stage values/RNG/conditions, retaining caller stacks.
p <- commandArgs(TRUE)
stopifnot(length(p)==4L)
.libPaths(readLines(p[[4]],warn=FALSE),include.site=FALSE)
adapter <- readRDS(file.path(p[[1]],"original/complete-failed-returns.rds"))
direct <- readRDS(file.path(p[[2]],"original/complete-original-returns.rds"))
exact <- function(a,b)identical(a,b,num.eq=FALSE,single.NA=FALSE,
  attrib.as.set=FALSE,ignore.bytecode=FALSE,ignore.environment=FALSE,ignore.srcref=FALSE)
same <- exact(adapter$original_returns,direct$original_returns) &&
  exact(adapter$RNG_before,direct$RNG_before) && exact(adapter$RNG_after,direct$RNG_after)
conditions <- adapter$original_conditions
known <- c(
  "Original input_data returned no expression matrix; original return retained",
  "Original pre_process returned no usable count matrix; complete return retained",
  "Original limma_value returned an error/no complete DEG matrix; original value retained")
own.guard <- FALSE
if (length(conditions)>0L) {
  last <- conditions[[length(conditions)]]
  own.guard <- identical(last$kind,"error") && last$message %in% known
  if (own.guard)conditions <- conditions[-length(conditions)]
}
stack.comparisons <- list()
if (length(conditions)==length(direct$original_conditions)) {
  for (i in seq_along(conditions)) {
    x <- conditions[[i]]$condition;y <- direct$original_conditions[[i]]$condition
    if (exact(x,y))next
    tx <- attr(x,"stack.trace",exact=TRUE);ty <- attr(y,"stack.trace",exact=TRUE)
    # Shiny's source error attaches the whole caller stack. Validate and compare
    # the namespace-internal suffix, not the different literal/public wrappers.
    stopifnot(identical(conditionMessage(x),"attempt to apply non-function"),
      identical(conditionMessage(y),"attempt to apply non-function"),
      exact(conditionCall(x),conditionCall(y)),
      identical(paste(deparse(conditionCall(x)[[1]]),collapse="\n"),"session$sendNotification"))
    stopifnot(is.pairlist(tx),is.pairlist(ty),
      setequal(names(attributes(tx)),c("parents","shiny.stack.digest")),
      setequal(names(attributes(ty)),c("parents","shiny.stack.digest")))
    native <- function(trace) {
      frames <- as.list(trace)
      first <- which(vapply(frames,function(call)
        is.call(call)&&is.symbol(call[[1]])&&identical(as.character(call[[1]]),"isolate"),logical(1)))
      stopifnot(length(first)==1L,first>1L)
      index <- seq.int(first,length(frames))
      parents <- attr(trace,"parents",exact=TRUE)
      digest <- attr(trace,"shiny.stack.digest",exact=TRUE)
      stopifnot(is.integer(parents),length(parents)==length(frames),
        # Actual Shiny/base error stacks can contain self-parent references.
        # Preserve them; compare the complete rebased native metadata exactly.
        all(parents>=0L),all(parents<=length(frames)),
        is.character(digest),length(digest)==1L,grepl("^[0-9a-f]{32}$",digest))
      native.parents <- parents[index]
      native.parents[native.parents<first] <- first-1L
      list(frames=unname(frames[index]),parents=native.parents-(first-1L),
           prefix_frames=first-1L,full_stack_digest=digest)
    }
    nx <- native(tx);ny <- native(ty)
    native.same <- exact(nx$frames,ny$frames)&&exact(nx$parents,ny$parents)
    same <- same && native.same
    stack.comparisons[[length(stack.comparisons)+1L]] <- list(
      condition=i,native_frames_compared=length(nx$frames),native_frames_and_parent_graph_equal=native.same,
      public_wrapper_prefix_frames=nx$prefix_frames,literal_wrapper_prefix_frames=ny$prefix_frames,
      public_full_stack_digest=nx$full_stack_digest,literal_full_stack_digest=ny$full_stack_digest)
    # Local comparison copies only. The complete original RDS/trace is untouched.
    attr(x,"stack.trace") <- NULL;attr(y,"stack.trace") <- NULL
    conditions[[i]]$condition <- x
    direct$original_conditions[[i]]$condition <- y
  }
}
same <- same && exact(conditions,direct$original_conditions)
if ("limma_value"%in%names(adapter$original_returns) &&
    is.character(adapter$original_returns$limma_value))
  stopifnot(grepl("^DEG_ERROR:",adapter$original_returns$limma_value))
jsonlite::write_json(list(status=if(same)"PASS_COMPLETE_ORIGINAL_FAILED_STAGE_VALUES_CONDITIONS_AND_RNG"else
  "FAIL2_ORIGINAL_FAILURE_COMPARISON_DIFFERS",complete_original_values_equal=same,
  exact_known_adapter_guard_error_separated=own.guard,
  all_non_stack_condition_fields_compared_without_normalization=TRUE,
  both_complete_native_caller_stacks_retained=TRUE,
  different_wrapper_stack_frames_compared=FALSE,
  checked_native_stack_suffixes=stack.comparisons,
  raw_original_RDS_files_modified=FALSE,
  result_admitted=FALSE,catalog_credit_added=0),
  p[[3]],auto_unbox=TRUE,pretty=TRUE,null="null")
quit(status=if(same)0L else 2L,save="no",runLast=FALSE)
