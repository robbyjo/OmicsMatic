# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
local({
  args <- commandArgs(TRUE);stopifnot(length(args)==5L)
  .libPaths(c(args[[1L]],.libPaths()));requireNamespace("Matrix")
  direct <- args[[2L]];out <- args[[5L]];stopifnot(!dir.exists(out));dir.create(out,recursive=TRUE)
  checks <- list();differences <- list()
  for(i in 3:4) {
    folder <- file.path(args[[i]],"original")
    actual.conditions <- readRDS(file.path(folder,"complete-original-conditions.rds"))
    expected.conditions <- readRDS(file.path(direct,"complete-conditions.rds"))
    actual <- list(complete_return=readRDS(file.path(folder,"complete-original-return.rds")),
      complete_rng=readRDS(file.path(folder,"complete-original-rng.rds")),
      complete_original_conditions=lapply(actual.conditions$conditions,function(x)x[c("kind","condition","class","message","call")]),
      complete_error=actual.conditions$error)
    expected <- list(complete_return=readRDS(file.path(direct,"complete-return.rds")),
      complete_rng=readRDS(file.path(direct,"complete-rng.rds")),
      complete_original_conditions=expected.conditions$conditions,complete_error=expected.conditions$error)
    checks[[basename(args[[i]])]] <- mapply(identical,actual,expected,SIMPLIFY=FALSE)
    differences[[basename(args[[i]])]] <- all.equal(actual,expected,check.attributes=TRUE)
  }
  saveRDS(list(checks=checks,complete_differences=differences),file.path(out,"complete-installed-comparisons.rds"),version=3)
  passed <- all(unlist(checks))
  jsonlite::write_json(list(status=if(passed)"PASS_BOTH_PUBLIC_INSTALLERS_ACTUAL_ANALYSIS_COMPLETE_ORIGINAL_COMPARISONS"else"FAIL2_INSTALLED_ANALYSIS_DIFFERS",checks=checks),
    file.path(out,"COMPLETED.json"),pretty=TRUE,auto_unbox=TRUE)
  if(!passed)quit(save="no",status=2L,runLast=FALSE)
})
