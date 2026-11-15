# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Compare complete original values, conditions and RNG; delete no upstream fields.
local({
  args <- commandArgs(TRUE);stopifnot(length(args)==3L)
  .libPaths(c(args[[1L]],.libPaths()));requireNamespace("Matrix")
  parent <- args[[2L]];names <- list.dirs(parent,full.names=FALSE,recursive=FALSE)
  results <- list();failed <- FALSE
  for(name in names) {
    case <- file.path(parent,name);direct <- file.path(case,"direct");worker <- file.path(case,"worker/original")
    a <- jsonlite::read_json(file.path(direct,"STATUS.json"),simplifyVector=TRUE)
    b <- jsonlite::read_json(file.path(worker,"R-status.json"),simplifyVector=TRUE)
    checks <- list(same_completion=identical(a$returned,b$original_function_returned),
      worker_analysis_started=isTRUE(b$analysis_started))
    difference <- list()
    compare <- function(key,left,right) {
      checks[[key]] <<- identical(left,right)
      difference[[key]] <<- all.equal(left,right,check.attributes=TRUE)
    }
    left <- readRDS(file.path(direct,"complete-conditions.rds"))
    right <- readRDS(file.path(worker,"complete-original-conditions.rds"))
    # phase is project driver metadata. Each original condition, class, message
    # and call is compared intact, including warnings preceding a source error.
    compare("complete_original_conditions",left$conditions,
      lapply(right$conditions,function(x)x[c("kind","condition","class","message","call")]))
    compare("complete_original_error",left$error,right$error)
    rng.path <- if(isTRUE(b$original_function_returned))"complete-original-rng.rds"else"failed-original-rng.rds"
    compare("complete_rng_before_after",readRDS(file.path(direct,"complete-rng.rds")),readRDS(file.path(worker,rng.path)))
    details <- NULL
    if(isTRUE(a$returned)&&isTRUE(b$original_function_returned)) {
      left <- readRDS(file.path(direct,"complete-return.rds"));right <- readRDS(file.path(worker,"complete-original-return.rds"))
      compare("complete_original_return",left,right)
      details <- list(real_scores=length(left[[1L]]),artificial_scores=length(left[[2L]]),
        real_removed_strictly_gt_03=sum(left[[1L]]>.3,na.rm=TRUE),real_unknown=sum(is.na(left[[1L]])),
        artificial_unknown=sum(is.na(left[[2L]])),all_real_names=names(left[[1L]]),all_artificial_names=names(left[[2L]]))
    }
    okay <- all(unlist(checks));failed <- failed||!okay
    saveRDS(list(checks=checks,complete_differences=difference),file.path(case,"complete-comparison.rds"),version=3)
    results[[name]] <- list(passed=okay,checks=checks,original_returned=isTRUE(a$returned),
      original_error=a$error,details=details)
  }
  # Exercise the rebuilt original native ANN dependency against Euclidean distances.
  points <- matrix(c(0,0,1,0,0,3,6,2,10,9),ncol=2,byrow=TRUE)
  actual <- RANN::nn2(points,k=4L)
  distances <- as.matrix(dist(points))
  expected.index <- t(vapply(seq_len(nrow(points)),function(i)order(distances[i,])[1:4],integer(4)))
  expected.distance <- t(vapply(seq_len(nrow(points)),function(i)distances[i,expected.index[i,]],numeric(4)))
  ann <- list(indices=identical(actual$nn.idx,expected.index),
    distances=isTRUE(all.equal(actual$nn.dists,expected.distance,tolerance=1e-14,check.attributes=FALSE)))
  saveRDS(list(actual=actual,expected_index=expected.index,expected_distances=expected.distance,checks=ann),
    file.path(args[[3L]],"complete-RANN-native-probe.rds"),version=3)
  failed <- failed||!all(unlist(ann))
  jsonlite::write_json(list(status=if(failed)"FAIL2_ORIGINAL_COMPARISONS_DIFFER"else"PASS_COMPLETE_ORIGINAL_HICAT109_CONTROL_COMPARISONS",
    cases=results,RANN_native_probe=ann,whole_tool_qualified=FALSE,whole_paper_reproduced=FALSE),
    file.path(args[[3L]],"COMPARISONS.json"),pretty=TRUE,auto_unbox=TRUE,null="null",digits=17)
  if(failed)quit(save="no",status=2L,runLast=FALSE)
})
