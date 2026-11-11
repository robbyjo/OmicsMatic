# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Decode the two unchanged original PPI resources, independently of inference.
args <- commandArgs(TRUE);out <- args[1];source <- args[2]
.libPaths(c(args[-(1:2)],.Library),include.site=FALSE);library(Matrix)
stopifnot(!dir.exists(out),dir.create(out))
for(species in c('human','mouse')) {
  env <- new.env(parent=emptyenv());name <- paste0('PPI.',species);load(file.path(source,'data',paste0(name,'.rda')),envir=env);ppi <- as.matrix(get(name,envir=env));stopifnot(identical(rownames(ppi),colnames(ppi)),all(is.finite(ppi)),all(ppi>=0))
  quote <- function(x)paste0('"',gsub('"','""',enc2utf8(x),fixed=TRUE),'"')
  lines <- c(paste(quote(c('gene_id',colnames(ppi))),collapse='\t'),vapply(seq_len(nrow(ppi)),function(i)paste(quote(c(rownames(ppi)[i],formatC(ppi[i,],digits=17,format='g'))),collapse='\t'),character(1)))
  writeLines(enc2utf8(lines),file.path(out,paste0(name,'.tsv')),useBytes=TRUE)
}
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
