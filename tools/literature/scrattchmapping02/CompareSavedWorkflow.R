# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Full values/RNG remain strict objects. Compare complete persisted conditions
# using native R serialization: RDS does not retain live external-pointer addresses.
# The old reference-identity comparator and its failed gate remain unchanged.
args <- commandArgs(TRUE);stopifnot(length(args)==3L,!file.exists(args[[3L]]))
reference <- args[[1L]];actual <- args[[2L]]
strict <- function(x,y)identical(x,y,num.eq=FALSE,single.NA=FALSE,attrib.as.set=FALSE,
 ignore.bytecode=FALSE,ignore.environment=FALSE,ignore.srcref=FALSE,extptr.as.ref=TRUE)
saved <- function(x,y)strict(x,y) || identical(serialize(x,NULL,version=3),serialize(y,NULL,version=3))
ref <- readRDS(file.path(reference,"complete-conditions.rds"))
got <- readRDS(file.path(actual,"complete-conditions.rds"))
expected.return <- is.null(ref$error)
comparisons <- list(complete_saved_error=saved(ref$error,got$error))
if(expected.return) {
 comparisons$complete_return_strict <- strict(readRDS(file.path(reference,"complete-result.rds")),readRDS(file.path(actual,"complete-result.rds")))
 comparisons$complete_rng_strict <- strict(readRDS(file.path(reference,"complete-rng-before-after.rds")),readRDS(file.path(actual,"complete-rng-before-after.rds")))
} else {
 comparisons$no_complete_return <- !file.exists(file.path(actual,"complete-result.rds"))
 comparisons$complete_rng_strict <- strict(readRDS(file.path(reference,"complete-rng-before-after.rds")),readRDS(file.path(actual,"failed-analysis-rng-before-after.rds")))
}
analysis <- Filter(function(x)identical(x$phase,"ORIGINAL_ANALYSIS"),got$conditions)
conditions <- lapply(analysis,function(x)list(kind=x$kind,condition=x$condition))
comparisons$complete_saved_analysis_conditions <- saved(ref$conditions,conditions)
passed <- all(unlist(comparisons,use.names=FALSE))
report <- list(status=if(passed)"PASS_ORIGINAL_VALUES_RNG_AND_COMPLETE_SAVED_CONDITIONS"else"FAIL2_ORIGINAL_SAVED_WORKFLOW_DIFFERS",
 comparisons=comparisons,original_returned=expected.return,
 diagnostic_only=list(error_live_reference_identity_strict=strict(ref$error,got$error),
                     analysis_conditions_live_reference_identity_strict=strict(ref$conditions,conditions)),
 returned_values_and_RNG_numeric_bit_equality=TRUE,returned_values_and_RNG_NA_NaN_payload_equality=TRUE,
 returned_values_and_RNG_attributes_order_compared=TRUE,
 complete_condition_native_R_serialization_version=3L,condition_fields_deleted_or_coerced=FALSE,
 condition_comparison="Complete strict R identity; otherwise complete native-R serialized representation identity",
 live_external_pointer_address_identity_claimed=FALSE,live_execution_context_reproduced=FALSE,
 raw_console_byte_identity_claimed=FALSE,opaque_RDS_compressed_file_byte_identity_claimed=FALSE)
jsonlite::write_json(report,args[[3L]],pretty=TRUE,auto_unbox=TRUE,null="null")
cat(report$status,"\n");if(!passed)quit(save="no",status=2L,runLast=FALSE)
