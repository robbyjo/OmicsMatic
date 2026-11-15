# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Mutate only NEW copied synthetic bundles, leaving originals untouched.
p <- commandArgs(TRUE)
stopifnot(length(p)==2L,p[[2]]%in%c("scientific-value","script-method"))
file <- file.path(p[[1]],"original/complete-original-returns.rds")
x <- readRDS(file)
if (p[[2]]=="scientific-value") {
  column <- grep("___log2FoldChange$",names(x$original_returns$limma_value$top_genes[[1]]),value=TRUE)
  stopifnot(length(column)==1L)
  v <- x$original_returns$limma_value$top_genes[[1]][[column]]
  stopifnot(is.numeric(v),length(v)>0L,is.finite(v[[1]]))
  x$original_returns$limma_value$top_genes[[1]][[column]][[1]] <- v[[1]]+0.125
} else {
  text <- x$original_returns$limma_value$expr
  stopifnot(grepl("FC <- 2 # Fold-change cutoff",text,fixed=TRUE))
  x$original_returns$limma_value$expr <- sub(
    "FC <- 2 # Fold-change cutoff","FC <- 3 # Fold-change cutoff",text,fixed=TRUE)
}
saveRDS(x,file,version=3,compress=FALSE)
saveRDS(x$original_returns$limma_value,file.path(p[[1]],"original/stages/limma_value.rds"),
  version=3,compress=FALSE)
