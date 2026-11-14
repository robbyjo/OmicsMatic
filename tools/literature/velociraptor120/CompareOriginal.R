# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Compare every serialized R value/attribute with strict numeric and NA/NaN bit semantics.
args <- commandArgs(TRUE)
stopifnot(length(args)==4L)
lib <- args[[1L]];reference.path <- args[[2L]];actual.path <- args[[3L]];out <- args[[4L]]
if (file.exists(out)) stop("Use a NEW complete original R comparison output")
.libPaths(c(lib,.libPaths()))
reference <- readRDS(reference.path);actual <- readRDS(actual.path)
equal <- identical(reference,actual,num.eq=FALSE,single.NA=FALSE,attrib.as.set=FALSE,ignore.bytecode=FALSE,ignore.environment=FALSE,ignore.srcref=FALSE,extptr.as.ref=TRUE)
report <- list(status=if(equal)"PASS_COMPLETE_ORIGINAL_R_VALUES_STRICT"else"FAIL2_COMPLETE_ORIGINAL_R_VALUES_DIFFER",complete_value_comparison=equal,require_numeric_bit_equality=TRUE,require_NA_and_NaN_payload_equality=TRUE,attribute_order_compared=TRUE,environment_and_pointer_identity_ignored=FALSE,opaque_RDS_file_byte_parity_claimed=FALSE,R_version=as.character(getRversion()),reference=reference.path,actual=actual.path,reference_class=class(reference),actual_class=class(actual))
if (!equal) report$required_action <- "Preserve both complete original objects and all conditions. Inspect the full returned values; do not waive differences, replace NA/NaN/Inf or omit fields/cells/genes. No complete result is admitted."
jsonlite::write_json(report,out,pretty=TRUE,auto_unbox=TRUE,null="null",na="string",digits=17)
cat(report$status,sep=intToUtf8(10L))
if (!equal) quit(save="no",status=2L,runLast=FALSE)
