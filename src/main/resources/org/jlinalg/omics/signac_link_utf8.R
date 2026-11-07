# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Preserve UTF-8 character data under a C locale; never let a text connection transliterate IDs.
omicsmatic_link_read_tsv <- function(path) {
  lines<-readLines(path,encoding='UTF-8',warn=FALSE)
  if(length(lines)==0L)stop('Empty typed LinkPeaks TSV')
  fields<-function(line){value<-strsplit(paste0(line,'\t__omicsmatic_end__'),'\t',fixed=TRUE)[[1L]];value[-length(value)]}
  header<-fields(lines[[1L]])
  if(anyNA(header)||any(header=='')||anyDuplicated(header))stop('Invalid typed TSV header')
  rows<-lapply(lines[-1L],fields)
  if(any(lengths(rows)!=length(header)))stop('Ragged typed LinkPeaks TSV')
  columns<-lapply(seq_along(header),function(i)vapply(rows,`[[`,character(1),i))
  names(columns)<-header
  as.data.frame(columns,stringsAsFactors=FALSE,check.names=FALSE)
}
