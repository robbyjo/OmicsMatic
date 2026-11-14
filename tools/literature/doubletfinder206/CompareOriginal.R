# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Compare complete original values, including every Seurat slot, closure and attribute.
args <- commandArgs(TRUE);stopifnot(length(args)==4L)
.libPaths(c(args[[1L]],.libPaths()));loadNamespace("DoubletFinder");loadNamespace("Seurat")
if(file.exists(args[[4L]]))stop("Use a NEW strict comparison record")
reference <- readRDS(args[[2L]]);actual <- readRDS(args[[3L]])
equal <- identical(reference,actual,num.eq=FALSE,single.NA=FALSE,attrib.as.set=FALSE,ignore.bytecode=FALSE,ignore.environment=FALSE,ignore.srcref=FALSE,extptr.as.ref=TRUE)
report <- list(status=if(equal)"PASS_COMPLETE_R_OBJECT_STRICT"else"FAIL2_COMPLETE_R_OBJECT_DIFFERS",complete_original_object_compared=TRUE,numeric_bit_equality=TRUE,NA_and_NaN_payload_equality=TRUE,attribute_order_compared=TRUE,closure_bodies_and_environments_compared=TRUE,fields_or_cells_omitted=FALSE,opaque_RDS_file_byte_identity_claimed=FALSE)
if(!equal)report$diagnostic_only <- capture.output(all.equal(reference,actual,check.environment=TRUE))
jsonlite::write_json(report,args[[4L]],pretty=TRUE,auto_unbox=TRUE,null="null")
cat(report$status,"\n");if(!equal)quit(save="no",status=2L,runLast=FALSE)
