# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Compare complete original objects, not a selected coefficient or tolerance.
args <- commandArgs(TRUE)
stopifnot(length(args)==4L)
.libPaths(c(args[4],R.home("library")))
library(Matrix)
expected <- readRDS(file.path(args[1],paste0(args[3],".rds")))
actual <- readRDS(file.path(args[2],"results/original-results.rds"))
stopifnot(identical(expected,actual))
read_table <- function(p)read.delim(p,colClasses="character",na.strings=character(),check.names=FALSE,quote="",comment.char="")
labels <- read_table(file.path(args[2],"inputs/ld_variants.tsv"))
outcomes <- read_table(file.path(args[2],"results/models.tsv"))
models <- if(startsWith(args[3],"auto"))actual else if(is.matrix(actual))lapply(seq_len(ncol(actual)),function(j)list(beta_est=actual[,j])) else list(list(beta_est=actual))
stopifnot(length(models)==nrow(outcomes))
accepted <- 0L
rejected <- 0L
for(j in seq_along(models)) {
 folder <- file.path(args[2],"results",outcomes$model[j]);model <- models[[j]]
 if(outcomes$status[j]=="accepted") {
  accepted <- accepted+1L
  effects <- read_table(file.path(folder,"effects.tsv"))
  stopifnot(identical(labels,effects[1:3]),identical(as.numeric(effects$effect),as.vector(model$beta_est)))
  if(!is.null(model$beta_est_sparse)) {
   sparse <- read_table(file.path(folder,"sparse-effects.tsv"))
   stopifnot(identical(labels,sparse[1:3]),identical(as.numeric(sparse$effect),as.vector(model$beta_est_sparse)))
  }
  if(startsWith(args[3],"auto")) {
   paths <- read_table(file.path(folder,"paths.tsv"));posterior <- read_table(file.path(folder,"posterior.tsv"))
   stopifnot(identical(as.integer(paths$iteration),seq_along(model$path_p_est)))
   for(pair in list(c("p","path_p_est"),c("h2","path_h2_est"),c("alpha","path_alpha_est")))stopifnot(identical(as.numeric(paths[[pair[1]]]),as.vector(model[[pair[2]]])))
   stopifnot(identical(posterior$variant,labels$variant),identical(as.numeric(posterior$postp),as.vector(model$postp_est)),identical(as.numeric(posterior$corr),as.vector(model$corr_est)))
  }
 } else {
  rejected <- rejected+1L
  stopifnot(outcomes$status[j]=="rejected",any(!is.finite(model$beta_est)),!file.exists(file.path(folder,"effects.tsv")),file.exists(file.path(folder,"REFUSAL.txt")))
 }
}
if(args[3]!="inf")for(point in c("before","after")) {
 expected_rng <- scan(file.path(args[1],paste0(args[3],"-rng-",point,".txt")),quiet=TRUE,what=integer())
 actual_rng <- scan(file.path(args[2],paste0("results/rng-",point,".txt")),quiet=TRUE,what=integer())
 stopifnot(identical(expected_rng,actual_rng))
}
cat("PASS_COMPLETE_ORIGINAL_OBJECTS_AND_EXPORTED_RESULTS",args[3],"models",length(models),"accepted",accepted,"rejected",rejected,"\n")
