# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
local({
  args <- commandArgs(TRUE);stopifnot(length(args)==3L)
  .libPaths(c(args[[1L]],.libPaths()));requireNamespace("Matrix")
  parent <- args[[3L]];cases <- c("dense-default","sparse-dgC-default","zero-count-column-sparse",
    "large-10001-real","missing-gene","plot-with-ggplot2")
  results <- list()
  for(name in cases) {
    original <- file.path(args[[2L]],name,"direct");actual <- file.path(parent,name,"original")
    status <- jsonlite::read_json(file.path(original,"STATUS.json"),simplifyVector=TRUE)
    expected.conditions <- readRDS(file.path(original,"complete-conditions.rds"))
    actual.conditions <- readRDS(file.path(actual,"complete-original-conditions.rds"))
    checks <- list(complete_conditions=identical(expected.conditions$conditions,
      lapply(actual.conditions$conditions,function(x)x[c("kind","condition","class","message","call")])),
      complete_error=identical(expected.conditions$error,actual.conditions$error),
      complete_rng=identical(readRDS(file.path(original,"complete-rng.rds")),
        readRDS(file.path(actual,if(status$returned)"complete-original-rng.rds"else"failed-original-rng.rds"))))
    if(status$returned)checks$complete_return <- identical(readRDS(file.path(original,"complete-return.rds")),
      readRDS(file.path(actual,"complete-original-return.rds")))
    results[[name]] <- checks
  }
  saveRDS(results,file.path(parent,"complete-public-comparisons.rds"),version=3)
  passed <- all(unlist(results))
  jsonlite::write_json(list(status=if(passed)"PASS_COMPLETE_PUBLIC_RUNNER_ORIGINAL_VALUES_CONDITIONS_AND_RNG"else"FAIL2_PUBLIC_RUNNER_DIFFERENCE",
    cases=results),file.path(parent,"PUBLIC-COMPARISONS.json"),pretty=TRUE,auto_unbox=TRUE)
  if(!passed)quit(save="no",status=2L,runLast=FALSE)
})
