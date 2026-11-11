# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE)
stopifnot(length(args)==1L)
paths <- readLines(args[[1]],encoding="UTF-8",warn=FALSE)
stopifnot(length(paths)==6L)
.libPaths(c(paths[1:3],.Library),include.site=FALSE)
stopifnot(as.character(getRversion())=="4.6.1")
versions <- c(Biostrings="2.80.2",Rsamtools="2.28.0",jsonlite="2.0.0")
for (pkg in names(versions)) stopifnot(as.character(packageVersion(pkg))==versions[[pkg]])
suppressPackageStartupMessages(library(Biostrings))
suppressPackageStartupMessages(library(Rsamtools))
input <- jsonlite::fromJSON(paths[[4]],simplifyVector=FALSE)
seqs <- DNAStringSet(vapply(input$cases,function(x)x$sequence,character(1)))
profiles <- c("1.16.0","1.34.1")
results <- list()
for (profile in profiles) {
  source <- readLines(file.path(paths[[6]],paste0("chromVAR-",profile),"source/chromVAR/R/background_peaks.R"),warn=FALSE)
  # Execute the exact, unchanged sequence-to-GC lines from each retained edition.
  start <- grep('nucfreqs <- letterFrequency',source,fixed=TRUE)
  stopifnot(length(start)==2L,identical(trimws(source[start[[1]]+1L]),'gc <- rowSums(nucfreqs[, 2:3]) / rowSums(nucfreqs)'))
  formula <- source[start[[1]]+0:1]
  stopifnot(identical(trimws(formula),trimws(source[start[[2]]+0:1])))
  e <- new.env(parent=environment());e$seqs <- seqs
  eval(parse(text=formula),envir=e)
  direct <- e$gc;frequency <- e$nucfreqs
  fa <- FaFile(paths[[5]])
  ranges <- input$ranges
  gr <- GenomicRanges::GRanges(vapply(ranges,function(x)x$chromosome,character(1)),IRanges::IRanges(vapply(ranges,function(x)as.integer(x$start),integer(1)),vapply(ranges,function(x)as.integer(x$end),integer(1))))
  # Real indexed FASTA extraction, followed by the same original GC expression.
  e$seqs <- getSeq(fa,gr);eval(parse(text=formula),envir=e)
  values <- function(x)vapply(x,function(v)if(is.nan(v))"NaN" else format(v,digits=17,scientific=FALSE,trim=TRUE),character(1))
  results[[profile]] <- list(direct=unname(as.list(values(direct))),frequencies=lapply(seq_len(nrow(frequency)),function(i)unname(as.list(as.numeric(frequency[i,])))),ranges=unname(as.list(values(e$gc))),formula=as.list(formula))
}
jsonlite::write_json(list(versions=as.list(versions),R_version=as.character(getRversion()),profiles=results),"original.json",auto_unbox=TRUE,pretty=TRUE,na="string")
cat("COMPLETE_ORIGINAL_CHROMVAR_GC\n")
