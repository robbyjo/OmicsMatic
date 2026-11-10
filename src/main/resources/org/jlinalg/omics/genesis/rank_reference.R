# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original .rankNorm controls only; no residual/model fitting or rescaling.
args <- commandArgs(TRUE);stopifnot(length(args)==3L)
.libPaths(c(args[[1L]],file.path(R.home(),"library")),include.site=FALSE)
stopifnot(getRversion()=="4.6.1",requireNamespace("GENESIS",quietly=TRUE),utils::packageDescription("GENESIS",fields="Version")=="2.42.0")
stopifnot(nzchar(Sys.setlocale("LC_CTYPE","English_United States.utf8")))
manifest <- normalizePath(args[[2L]],winslash="/",mustWork=TRUE);out <- args[[3L]]
read_tsv <- function(file)utils::read.table(file,header=TRUE,sep="\t",quote="",comment.char="",colClasses="character",na.strings=character(),fileEncoding="UTF-8",check.names=FALSE)
cases <- read_tsv(manifest);stopifnot(identical(names(cases),c("case","mode","input")),!anyDuplicated(cases$case))
original <- get(".rankNorm",envir=asNamespace("GENESIS"));writeLines(deparse(original),file.path(out,"original-rankNorm-function.txt"))
records <- list()
for(k in seq_len(nrow(cases))) {
    row <- cases[k,];stopifnot(grepl("^[a-z0-9-]+$",row$case),row$input==paste0(row$case,".tsv"),row$mode %in% c("all","by-group"))
    x <- read_tsv(file.path(dirname(manifest),row$input));stopifnot(identical(names(x),c("input_row","group","value_hex")))
    values <- vapply(x$value_hex,function(s)if(s=="NA")NA_real_ else as.numeric(s),numeric(1L));ranks <- scores <- numeric(length(values));sizes <- integer(length(values))
    groups <- if(row$mode=="all")list(seq_along(values)) else lapply(unique(x$group),function(label)which(x$group==label))
    for(indices in groups) { ranks[indices] <- rank(values[indices]);scores[indices] <- original(values[indices]);sizes[indices] <- length(indices) }
    result <- data.frame(input_row=seq_along(values),group=x$group,group_size=sizes,average_rank_hex=sprintf("%a",ranks),score_hex=sprintf("%a",scores))
    utils::write.table(result,file.path(out,paste0(row$case,".tsv")),sep="\t",quote=FALSE,row.names=FALSE,fileEncoding="UTF-8")
    records[[k]] <- data.frame(case=row$case,status="ORIGINAL_RANK_SUCCESS",rows=length(values))
}
utils::write.table(do.call(rbind,records),file.path(out,"original-dispositions.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
cat("PASS_ORIGINAL_GENESIS242_RANK_CONTROLS_NO_MODEL_FITTING\n")
