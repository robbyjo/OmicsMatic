# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Complete original values and RNG, and complete analysis condition objects.
args <- commandArgs(TRUE);stopifnot(length(args)==3L,!file.exists(args[[3L]]))
reference <- args[[1L]];actual <- args[[2L]]
equal <- function(x,y)identical(x,y,num.eq=FALSE,single.NA=FALSE,attrib.as.set=FALSE,
  ignore.bytecode=FALSE,ignore.environment=FALSE,ignore.srcref=FALSE,extptr.as.ref=TRUE)
ref.conditions <- readRDS(file.path(reference,"complete-conditions.rds"))
got.conditions <- readRDS(file.path(actual,"complete-conditions.rds"))
expected.return <- is.null(ref.conditions$error)
comparisons <- list(original_error=equal(ref.conditions$error,got.conditions$error))
if(expected.return) {
 comparisons$complete_return <- equal(readRDS(file.path(reference,"complete-result.rds")),readRDS(file.path(actual,"complete-result.rds")))
 comparisons$complete_rng <- equal(readRDS(file.path(reference,"complete-rng-before-after.rds")),readRDS(file.path(actual,"complete-rng-before-after.rds")))
} else {
 comparisons$no_complete_return <- !file.exists(file.path(actual,"complete-result.rds"))
 comparisons$complete_rng <- equal(readRDS(file.path(reference,"complete-rng-before-after.rds")),readRDS(file.path(actual,"failed-analysis-rng-before-after.rds")))
}
actual.analysis <- Filter(function(x)identical(x$phase,"ORIGINAL_ANALYSIS"),got.conditions$conditions)
actual.conditions <- lapply(actual.analysis,function(x)list(kind=x$kind,condition=x$condition))
comparisons$complete_original_analysis_conditions <- equal(ref.conditions$conditions,actual.conditions)
passed <- all(unlist(comparisons,use.names=FALSE))
jsonlite::write_json(list(status=if(passed)"PASS_COMPLETE_ORIGINAL_WORKFLOW_OBJECTS"else"FAIL2_ORIGINAL_WORKFLOW_DIFFERS",
 comparisons=comparisons,original_returned=expected.return,numeric_bit_equality=TRUE,
 NA_and_NaN_payload_equality=TRUE,attribute_order_compared=TRUE,fields_or_cells_omitted=FALSE,
 all_analysis_condition_objects_compared=TRUE,raw_console_byte_identity_claimed=FALSE,
 opaque_RDS_file_byte_identity_claimed=FALSE),args[[3L]],pretty=TRUE,auto_unbox=TRUE)
cat(if(passed)"PASS_COMPLETE_ORIGINAL_WORKFLOW_OBJECTS\n"else"FAIL2_ORIGINAL_WORKFLOW_DIFFERS\n")
if(!passed)quit(save="no",status=2L,runLast=FALSE)
