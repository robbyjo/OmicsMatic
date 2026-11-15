# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Export input types from saved original objects; no original function is rerun.
args <- commandArgs(TRUE);stopifnot(length(args)==3L)
out <- args[[2L]];input <- args[[3L]]
stopifnot(!file.exists(out));dir.create(out)
records <- readRDS(input)$records
lines <- "case\tgroup\ttype"
for(id in names(records)) {
  groups <- records[[id]]$arguments[["markers",exact=TRUE]]
  for(i in seq_along(groups)) {
    stopifnot(is.null(groups[[i]])||is.character(groups[[i]]))
    lines <- c(lines,paste(id,i-1L,if(is.null(groups[[i]]))"NULL"else"CHARACTER",sep="\t"))
  }
}
writeLines(lines,file.path(out,"marker-group-types.tsv"))
jsonlite::write_json(list(status="PASS_SAVED_ORIGINAL_MARKER_INPUT_TYPES_EXPORTED",cases=length(records),
  group_records=length(lines)-1L,input_sha256=digest::digest(file=input,algo="sha256",serialize=FALSE),
  original_function_rerun=FALSE),file.path(out,"EXPORT.json"),pretty=TRUE,auto_unbox=TRUE)
cat("PASS_SAVED_ORIGINAL_MARKER_INPUT_TYPES_EXPORTED",length(records),"\n")
