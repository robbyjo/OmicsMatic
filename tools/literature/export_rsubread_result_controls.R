# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Export all already retained original count lists; do not refit or load Rsubread.
args <- commandArgs(trailingOnly=TRUE); stopifnot(length(args)==4L)
source(args[1L], local=TRUE); original <- args[2L]; out <- args[3L]; version <- args[4L]
stopifnot(as.character(getRversion())==version, dir.exists(out))
cases <- utils::read.delim(file.path(original, "original-cases.tsv"), stringsAsFactors=FALSE)
stopifnot(nrow(cases)==16L)
for (id in cases$id) rsubread_write_result(readRDS(file.path(original, id, "complete-original-result.rds")), file.path(out, id))
edge <- structure(list(
    c("NA", NA_character_, "", "dup", "dup", "beta-\u03b2", "tab\tnewline\n\U0001f9ec"),
    c(NA_integer_, -.Machine$integer.max, 0L, 1L, .Machine$integer.max, 123L, -123L),
    c(NA_real_, NaN, Inf, -Inf, -0.0, 1/3, 2^53+2),
    c(NA, TRUE, FALSE, TRUE, FALSE, NA, TRUE)),
    names=c("duplicate", "duplicate", "", NA_character_), class="data.frame",
    row.names=c("r7", "r6", "r5", "r4", "r3", "r2", "r1"))
rsubread_write_frame(edge, file.path(out, "scalar-edge.frame")); saveRDS(edge, file.path(out, "scalar-edge.rds"), version=3L)
empty <- structure(list(integer(), logical(), character(), double()), names=c("integer", "logical", "character", "double"), class="data.frame", row.names=integer())
rsubread_write_frame(empty, file.path(out, "zero-rows.frame")); saveRDS(empty, file.path(out, "zero-rows.rds"), version=3L)
no.columns <- structure(list(), names=character(), class="data.frame", row.names=c("a", "b"))
rsubread_write_frame(no.columns, file.path(out, "zero-columns.frame")); saveRDS(no.columns, file.path(out, "zero-columns.rds"), version=3L)
rejected <- list()
for (pair in list(list(id="factor", value=data.frame(x=factor("a"))), list(id="list", value=data.frame(x=I(list(1L)))))) {
    result <- tryCatch(rsubread_write_frame(pair$value, file.path(out, paste0(pair$id, ".not-admitted"))), error=function(e)e)
    stopifnot(inherits(result, "error")); rejected[[pair$id]] <- conditionMessage(result)
}
dput(rejected, file=file.path(out, "unsupported-attribute-errors.R"))
writeLines(capture.output(sessionInfo()), file.path(out, "sessionInfo.txt"))
writeLines("PASS16_ORIGINAL_RESULT_VALUE_EXPORTS_3_SCALAR_CONTROLS_2_ATTRIBUTE_REJECTIONS", file.path(out, "R-status.txt"))
