# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Compare complete captured original results, conditions and RNG without field removal.
local({
  a <- commandArgs(TRUE);stopifnot(length(a)==4L);.libPaths(c(a[[1L]],.libPaths()))
  requireNamespace("SingleR");requireNamespace("SingleCellExperiment")
  parent <- a[[2L]];expected <- jsonlite::read_json(file.path(a[[3L]],"CONTROLS.json"),simplifyVector=FALSE)$cases
  results <- list();failed <- FALSE
  for(name in names(expected)) {
    folder <- file.path(parent,name);direct <- file.path(folder,"direct");worker <- file.path(folder,"worker/original")
    ds <- jsonlite::read_json(file.path(direct,"STATUS.json"),simplifyVector=TRUE)
    ws <- jsonlite::read_json(file.path(worker,"R-status.json"),simplifyVector=TRUE)
    checks <- list(same_completion=identical(ds$returned,ws$original_function_returned),worker_analysis_started=isTRUE(ws$analysis_started),
      complete_worker_status=identical(ws$exit_code,if(isTRUE(ds$returned))0L else 2L))
    differences <- list()
    compare <- function(key,left,right) {
      # Whole-object serialization retains all values, attributes, condition
      # calls, closure environments and reference topology. It compares fresh
      # SerialParam environments by their complete captured state, rather than
      # their necessarily different process-local memory identities.
      checks[[key]] <<- identical(serialize(left,NULL,version=3),serialize(right,NULL,version=3))
      differences[[key]] <<- all.equal(left,right,check.attributes=TRUE)
    }
    left <- readRDS(file.path(direct,"complete-conditions.rds"));right <- readRDS(file.path(worker,"complete-original-conditions.rds"))
    compare("complete_original_conditions",left$conditions,lapply(right$conditions,function(x)x[c("kind","condition","class","message","call")]))
    compare("complete_original_error",left$error,right$error)
    compare("complete_rng_before_separately_recorded",readRDS(file.path(direct,"complete-rng-before.rds")),readRDS(file.path(worker,"complete-original-rng-before.rds")))
    rng.path <- if(isTRUE(ws$original_function_returned))"complete-original-rng.rds"else"failed-original-rng.rds"
    compare("complete_rng_before_after",readRDS(file.path(direct,"complete-rng.rds")),readRDS(file.path(worker,rng.path)))
    training <- file.exists(file.path(direct,"complete-training-return.rds"))
    checks$training_record_presence <- identical(training,file.exists(file.path(worker,"complete-original-training-return.rds")))
    if(training&&checks$training_record_presence)
      compare("complete_serialized_training_record_not_live_pointer_equivalence",readRDS(file.path(direct,"complete-training-return.rds")),readRDS(file.path(worker,"complete-original-training-return.rds")))
    details <- NULL
    if(isTRUE(ds$returned)&&isTRUE(ws$original_function_returned)) {
      left <- readRDS(file.path(direct,"complete-return.rds"));right <- readRDS(file.path(worker,"complete-original-return.rds"))
      compare("complete_original_return_all_fields_and_attributes",left,right)
      known <- expected[[name]]$known_expected_labels
      if(!is.null(known)) {
        compare("independent_synthetic_known_labels",as.character(left$labels),unname(unlist(known)))
        request <- readRDS(file.path(a[[3L]],paste0(name,".rds")))
        test <- if(request$operation=="train-classify")request$arguments$classification$test else request$arguments$test
        compare("original_literal_cell_identifiers",rownames(left),colnames(test))
      }
      details <- list(return_class=class(left),dimensions=dim(left),names=names(left),
        result_rows=if(is.null(dim(left)))length(left)else nrow(left))
    }
    okay <- all(unlist(checks));failed <- failed||!okay
    saveRDS(list(checks=checks,complete_differences=differences),file.path(folder,"complete-comparison.rds"),version=3)
    results[[name]] <- list(passed=okay,checks=checks,original_returned=isTRUE(ds$returned),original_error=ds$error,details=details)
  }
  jsonlite::write_json(list(status=if(failed)"FAIL2_ORIGINAL_SINGLER_COMPARISONS_DIFFER"else"PASS_COMPLETE_ORIGINAL_SINGLER_CONTROL_COMPARISONS",
    cases=results,all_original_return_fields_compared=TRUE,serialized_training_pointers_are_reusable=FALSE,
    live_external_pointer_internal_equivalence_claimed=FALSE,whole_tool_qualified=FALSE,whole_paper_reproduced=FALSE),
    file.path(a[[4L]],"COMPARISONS.json"),pretty=TRUE,auto_unbox=TRUE,null="null",digits=17)
  if(failed)quit(save="no",status=2L,runLast=FALSE)
})
