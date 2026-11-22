# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Apply the literal paper predicate to every successful original control score.
local({
  args <- commandArgs(TRUE);stopifnot(length(args)==2L)
  cases <- args[[1L]];out <- args[[2L]];stopifnot(!dir.exists(out));dir.create(out,recursive=TRUE)
  originals <- list();pieces <- list();mapping <- list();offset <- 0L
  for(name in sort(list.dirs(cases,full.names=FALSE,recursive=FALSE))) {
    path <- file.path(cases,name,"direct/complete-return.rds")
    if(!file.exists(path))next
    whole <- readRDS(path);stopifnot(is.list(whole),length(whole)==2L)
    originals[[name]] <- whole
    for(j in 1:2) {
      score <- whole[[j]];n <- length(score)
      pieces[[length(pieces)+1L]] <- score
      mapping[[length(mapping)+1L]] <- list(control=name,vector=if(j==1L)"real"else"artificial",
        first_aggregate_row=offset,rows=n,complete_original_names=names(score))
      offset <- offset+n
    }
  }
  values <- unlist(pieces,use.names=FALSE);comparison <- values>.3
  bytes <- sprintf("%02x",as.integer(writeBin(values,raw(),size=8L,endian="big")))
  bits <- vapply(seq_along(values),function(i)paste0(bytes[(8L*i-7L):(8L*i)],collapse=""),character(1))
  decision <- ifelse(is.na(comparison),"UNKNOWN",ifelse(comparison,"REMOVE","RETAIN"))
  writeLines(c("cutoff_bits\tvalue_bits\tdecision",paste("3fd3333333333333",bits,decision,sep="\t")),
    file.path(out,"classifications.tsv"))
  rows <- list(retained=which(comparison %in% FALSE)-1L,removed=which(comparison %in% TRUE)-1L,unknown=which(is.na(comparison))-1L)
  writeLines(c("cutoff_bits\tretained_rows\tremoved_rows\tunknown_rows",
    paste(c("3fd3333333333333",vapply(rows,paste,character(1),collapse=",")),collapse="\t")),file.path(out,"selections.tsv"))
  saveRDS(list(complete_original_returns=originals,original_order_mapping=mapping,aggregate_values=values,
    comparison=comparison,decisions=decision,rows=rows,session=sessionInfo()),file.path(out,"complete-original-score-selection-oracle.rds"),version=3)
  jsonlite::write_json(list(status="LITERAL_R_THRESHOLD_ON_ALL_SUCCESSFUL_ORIGINAL_HICAT_CONTROL_SCORES",
    controls=length(originals),score_vectors=length(pieces),scores=length(values),
    complete_original_name_mapping=mapping,removed=length(rows$removed),retained=length(rows$retained),unknown=length(rows$unknown),
    native_HICAT_scoring_port=FALSE),file.path(out,"ORACLE.json"),pretty=TRUE,auto_unbox=TRUE,null="null",digits=17)
})
