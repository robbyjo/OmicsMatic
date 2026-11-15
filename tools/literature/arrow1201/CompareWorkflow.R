# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE);stopifnot(length(args)==3L)
reference <- args[[1L]];adapter <- args[[2L]]
original <- readRDS(file.path(reference,"complete-conditions.rds"))
actual <- readRDS(file.path(adapter,"complete-conditions.rds"))
bits <- function(a,b) {
  if(!identical(a,b))return(FALSE)
  if(is.double(a)) {
    # Extract payloads on copies after whole-object identity. Attribute values
    # are also traversed below. Never dispatch as.double() on integer64.
    aa <- a;bb <- b;attributes(aa) <- NULL;attributes(bb) <- NULL
    if(!identical(writeBin(aa,raw(),size=8L,endian="little"),
                  writeBin(bb,raw(),size=8L,endian="little")))return(FALSE)
  }
  if(is.list(a) && !all(vapply(seq_along(a),function(i)bits(a[[i]],b[[i]]),logical(1))))return(FALSE)
  if(!is.null(attributes(a)) && !all(vapply(seq_along(attributes(a)),
    function(i)bits(attributes(a)[[i]],attributes(b)[[i]]),logical(1))))return(FALSE)
  TRUE
}
returned <- is.null(original$error)
result <- list(reference_returned=returned,adapter_returned=is.null(actual$error),
  complete_error_strict=identical(original$error,actual$error),
  complete_error_native_serialization_identical=identical(serialize(original$error,NULL,version=3),serialize(actual$error,NULL,version=3)),
  complete_conditions_strict=identical(original$conditions,actual$conditions),
  complete_conditions_native_serialization_identical=identical(serialize(original$conditions,NULL,version=3),serialize(actual$conditions,NULL,version=3)),
  live_context_identity_claimed=FALSE,condition_fields_deleted_or_coerced=FALSE)
if(returned) {
  expected <- readRDS(file.path(reference,"complete-result.rds"))
  value <- readRDS(file.path(adapter,"complete-result.rds"))
  result$complete_return_strict <- identical(expected,value)
  result$complete_return_numeric_bits_and_NA_NaN_payloads <- bits(expected,value)
} else {
  result$error_classes_identical <- identical(class(original$error),class(actual$error))
  result$error_message_identical <- identical(conditionMessage(original$error),conditionMessage(actual$error))
  result$error_call_identical <- identical(conditionCall(original$error),conditionCall(actual$error))
}
result$complete_RNG_strict <- bits(readRDS(file.path(reference,"complete-rng-before-after.rds")),
  readRDS(file.path(adapter,if(returned)"complete-rng-before-after.rds"else"failed-analysis-rng-before-after.rds")))
jsonlite::write_json(result,args[[3L]],pretty=TRUE,auto_unbox=TRUE)
stopifnot(identical(result$reference_returned,result$adapter_returned),isTRUE(result$complete_RNG_strict))
if(returned) {
  stopifnot(result$complete_return_strict,result$complete_return_numeric_bits_and_NA_NaN_payloads)
} else {
  stopifnot(result$error_classes_identical,result$error_message_identical,result$error_call_identical)
}
