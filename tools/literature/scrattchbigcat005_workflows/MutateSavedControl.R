# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Deliberate scientific changes in new test copies only; originals are read-only.
args <- commandArgs(TRUE);stopifnot(length(args)==2L)
library(Matrix);library(bigstatsr)
case <- args[[1L]];kind <- args[[2L]]
sentinel <- jsonlite::fromJSON(file.path(case,"TAMPER-COPY-ONLY.json"))
stopifnot(isTRUE(sentinel$allow_deliberate_mutation),identical(normalizePath(case,mustWork=TRUE),sentinel$owned_case_path))
file <- file.path(case,"adapter/original/complete-result.rds")
if(kind=="marker-value") {
  value <- readRDS(file);value[1,1] <- value[1,1]+.25;saveRDS(value,file,version=3)
} else if(kind=="marker-dimnames") {
  value <- readRDS(file);dimnames(value) <- list(rep("CHANGED_ROW",nrow(value)),rep("CHANGED_COLUMN",ncol(value)))
  saveRDS(value,file,version=3)
} else if(kind=="RNG") {
  file <- file.path(case,"adapter/original/complete-rng-before-after.rds")
  value <- readRDS(file);value$after$seed[[2L]] <- value$after$seed[[2L]]+1L;saveRDS(value,file,version=3)
} else if(kind=="condition") {
  file <- file.path(case,"adapter/original/complete-conditions.rds")
  value <- readRDS(file);value$error$message <- "DELIBERATELY_CHANGED_ORIGINAL_ERROR";saveRDS(value,file,version=3)
} else if(kind=="plot-title") {
  value <- readRDS(file);value$constellation$labels$title <- "DELIBERATELY_CHANGED_FIGURE";saveRDS(value,file,version=3)
} else if(kind=="plot-node") {
  value <- readRDS(file);value$nodes.df$cluster_size[[1L]] <- value$nodes.df$cluster_size[[1L]]+100L
  saveRDS(value,file,version=3)
} else if(kind=="CV-mapping-distance") {
  value <- readRDS(file);value$folds[[1L]]$panels[[1L]]$mapping$dist[[1L]] <-
    value$folds[[1L]]$panels[[1L]]$mapping$dist[[1L]]+1
  saveRDS(value,file,version=3)
} else if(kind=="CV-centroid") {
  value <- readRDS(file);value$folds[[1L]]$centroids[1L,1L] <- value$folds[[1L]]$centroids[1L,1L]+1
  saveRDS(value,file,version=3)
} else if(kind=="SPN-selection") {
  file <- file.path(case,"adapter/original/complete-post-selection.rds")
  value <- readRDS(file);value$counts[[1L]] <- value$counts[[1L]]+1L;saveRDS(value,file,version=3)
} else stop("Unknown explicit copied-control mutation")
