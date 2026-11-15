# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Literal R comparisons on caller-supplied IEEE-754 fixtures; no HICAT source.
local({
  args <- commandArgs(TRUE);stopifnot(length(args)==2L)
  input <- jsonlite::read_json(args[[1L]],simplifyVector=TRUE);out <- args[[2L]]
  stopifnot(!dir.exists(out));dir.create(out,recursive=TRUE)
  decode <- function(hex)readBin(as.raw(strtoi(substring(hex,seq(1L,15L,2L),seq(2L,16L,2L)),16L)),
    what="double",n=1L,size=8L,endian="big")
  values <- vapply(input$value_bits,decode,numeric(1));cutoffs <- vapply(input$cutoff_bits,decode,numeric(1))
  classification <- file(file.path(out,"classifications.tsv"),open="wt")
  selection <- file(file.path(out,"selections.tsv"),open="wt")
  writeLines("cutoff_bits\tvalue_bits\tdecision",classification)
  writeLines("cutoff_bits\tretained_rows\tremoved_rows\tunknown_rows",selection)
  records <- list()
  for(i in seq_along(cutoffs)) {
    comparison <- values>cutoffs[[i]]
    decision <- ifelse(is.na(comparison),"UNKNOWN",ifelse(comparison,"REMOVE","RETAIN"))
    rows <- list(retained=which(comparison %in% FALSE)-1L,removed=which(comparison %in% TRUE)-1L,
      unknown=which(is.na(comparison))-1L)
    writeLines(paste(input$cutoff_bits[[i]],input$value_bits,decision,sep="\t"),classification)
    writeLines(paste(c(input$cutoff_bits[[i]],vapply(rows,paste,character(1),collapse=",")),collapse="\t"),selection)
    records[[input$cutoff_bits[[i]]]] <- list(cutoff=cutoffs[[i]],complete_comparison=comparison,
      decisions=decision,rows=rows)
  }
  close(classification);close(selection)
  saveRDS(list(input=input,values=values,cutoffs=cutoffs,records=records,session=sessionInfo()),
    file.path(out,"complete-R-oracle.rds"),version=3)
  jsonlite::write_json(list(status="COMPLETE_LITERAL_R_NUMERIC_COMPARISON_ORACLE",R=as.character(getRversion()),
    values=length(values),cutoffs=length(cutoffs),comparisons=length(values)*length(cutoffs),
    source_derived_HICAT_scoring_port=FALSE,missing_classification="UNKNOWN",equality_retained=TRUE),
    file.path(out,"ORACLE.json"),pretty=TRUE,auto_unbox=TRUE)
})
