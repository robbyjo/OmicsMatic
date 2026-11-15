# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Diagnose complete stored objects without removing or rewriting a field.
args <- commandArgs(TRUE);stopifnot(length(args)==3L)
reference <- readRDS(args[[1L]])$error
adapter <- readRDS(args[[2L]])$error
stopifnot(!is.null(reference),!is.null(adapter))
fields <- union(names(reference),names(adapter))
rows <- setNames(lapply(fields,function(name)list(
  complete_field_strict=identical(reference[[name]],adapter[[name]]),
  complete_field_native_serialization_identical=identical(serialize(reference[[name]],NULL,version=3),
                                                         serialize(adapter[[name]],NULL,version=3)))),fields)
result <- list(complete_error_strict=identical(reference,adapter),
  complete_error_attributes_identical=identical(attributes(reference),attributes(adapter)),
  field_names_and_order_identical=identical(names(reference),names(adapter)),
  fields=rows,differing_fields=fields[!vapply(rows,function(r)r$complete_field_strict,logical(1))],
  no_fields_removed_or_coerced=TRUE,live_execution_context_identity_claimed=FALSE)
jsonlite::write_json(result,args[[3L]],pretty=TRUE,auto_unbox=TRUE)
writeLines(capture.output(all.equal(reference,adapter)),paste0(args[[3L]],".all-equal.txt"))
