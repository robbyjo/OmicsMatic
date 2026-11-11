# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Compare all retained scientific slots, including every original graph measure.
args <- commandArgs(TRUE);reference <- args[1];actual <- args[2];out <- args[3]
.libPaths(c(args[-seq_len(3L)],.Library),include.site=FALSE);dir.create(out,recursive=TRUE)
stopifnot(requireNamespace('CellChat',quietly=TRUE))
ids <- c(paste0('condition-',1:2),paste0('sample-',1:6));checks <- list()
for(id in ids) {
  expected <- readRDS(file.path(reference,paste0(id,'.rds')));observed <- readRDS(file.path(actual,'cases',id,'original-cellchat.rds'))
  for(slot in c('data','data.signaling','meta','idents','DB','LR','var.features')) stopifnot(identical(methods::slot(expected,slot),methods::slot(observed,slot)))
  # Time metadata is excluded explicitly. Net/netP include complete original
  # probability, p-value, count, weight, pathway and centrality populations.
  net <- all.equal(expected@net,observed@net,tolerance=2e-12,check.attributes=TRUE)
  netP <- all.equal(expected@netP,observed@netP,tolerance=2e-12,check.attributes=TRUE)
  stopifnot(isTRUE(net),isTRUE(netP))
  maxDifference <- max(abs(expected@net$prob-observed@net$prob)/pmax(1,abs(expected@net$prob)))
  checks[[id]] <- list(scientific_slots=9L,maximum_scaled_probability_difference=maxDifference,all_original_centrality_measures_compared=TRUE,time_metadata_compared=FALSE)
}
jsonlite::write_json(list(status='PASS_COMPLETE_SCIENTIFIC_SLOTS_OF_EIGHT_DIRECT_ORIGINAL_PACKAGE_REFITS',checks=checks,independent_native_statistical_refit=FALSE,analysis_admitted=FALSE),file.path(out,'COMPLETED.json'),auto_unbox=TRUE,pretty=TRUE,digits=NA)
