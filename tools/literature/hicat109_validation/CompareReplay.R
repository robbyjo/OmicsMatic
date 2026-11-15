# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Compare complete fresh original scientific objects with frozen worker records.
local({
  args <- commandArgs(TRUE);stopifnot(length(args)>=4L)
  .libPaths(c(args[[1L]],.libPaths()));requireNamespace("Matrix")
  frozen <- args[[2L]];fresh <- args[[3L]];out <- args[[4L]]
  cases <- if(length(args)>4L)args[5:length(args)]else sort(list.dirs(fresh,full.names=FALSE,recursive=FALSE))
  results <- differences <- list();failed <- FALSE
  for(name in cases) {
    reference <- file.path(fresh,name,"direct");worker <- file.path(frozen,name,"worker/original")
    actual <- jsonlite::read_json(file.path(worker,"R-status.json"),simplifyVector=TRUE)
    expected <- jsonlite::read_json(file.path(reference,"STATUS.json"),simplifyVector=TRUE)
    checks <- list(same_completion=identical(actual$original_function_returned,expected$returned),
      analysis_started=isTRUE(actual$analysis_started),source_complete_record_agrees=identical(actual$complete_original_return_recorded,expected$returned),
      source_exit_agrees=identical(as.integer(actual$exit_code),if(expected$returned)0L else 2L))
    diff <- list()
    compare <- function(key,left,right) {
      checks[[key]] <<- identical(left,right);diff[[key]] <<- all.equal(left,right,check.attributes=TRUE)
    }
    left <- readRDS(file.path(worker,"complete-original-conditions.rds"));right <- readRDS(file.path(reference,"complete-conditions.rds"))
    compare("whole_original_conditions",lapply(left$conditions,function(x)x[c("kind","condition","class","message","call")]),right$conditions)
    compare("whole_original_error",left$error,right$error)
    compare("whole_rng_before_and_after",readRDS(file.path(worker,if(expected$returned)"complete-original-rng.rds"else"failed-original-rng.rds")),
      readRDS(file.path(reference,"complete-rng.rds")))
    if(expected$returned)compare("whole_original_return",readRDS(file.path(worker,"complete-original-return.rds")),readRDS(file.path(reference,"complete-return.rds")))
    results[[name]] <- checks;differences[[name]] <- diff;failed <- failed||!all(unlist(checks))
  }
  saveRDS(list(cases=results,complete_differences=differences),file.path(out,"complete-replay-comparisons.rds"),version=3)
  jsonlite::write_json(list(status=if(failed)"FAIL2_FROZEN_SCIENCE_DIFFERS_FROM_FRESH_ORIGINAL"else"PASS_COMPLETE_FRESH_ORIGINAL_REPLAY_COMPARISONS",
    cases=results,scoped_case_selection=length(args)>4L,whole_paper_reproduced=FALSE),
    file.path(out,"REPLAY-COMPARISONS.json"),pretty=TRUE,auto_unbox=TRUE)
  if(failed)quit(save="no",status=2L,runLast=FALSE)
})
