# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Compare exported communication/centrality fields with SEPARATE original refits.
args <- commandArgs(TRUE);out <- args[1];reference <- args[2];label <- args[3];bundles <- args[4:5]
.libPaths(c(args[-(1:5)],.Library),include.site=FALSE);library(CellChat)
stopifnot(as.character(packageVersion('CellChat'))==label,!dir.exists(out),dir.create(out))
checks <- list();status <- 'FAIL2_ORIGINAL_INFERENCE_EXPORT_REFERENCE';detail <- ''
equal <- function(name,a,b) {test <- all.equal(a,b,tolerance=2e-12,check.attributes=FALSE);if(!isTRUE(test))stop(name,': ',paste(test,collapse='; '))}
tryCatch({
  for(i in seq_len(if(label=='1.0.0')2L else 3L)) {
    original <- readRDS(file.path(reference,paste0('condition-',i,'.rds')))
    expected <- CellChat::subsetCommunication(original,thresh=0.05)
    for(b in seq_along(bundles)) {
      home <- file.path(bundles[b],'results','cases',paste0('condition-',i))
      exported <- read.delim(file.path(home,'communications.tsv'),sep='\t',check.names=FALSE,stringsAsFactors=FALSE)
      stopifnot(identical(names(expected),names(exported)),nrow(expected)==nrow(exported))
      for(column in names(expected)) {
        if(is.numeric(expected[[column]]))equal(column,expected[[column]],exported[[column]]) else stopifnot(identical(as.character(expected[[column]]),as.character(exported[[column]])))
      }
      checks[[length(checks)+1L]] <- paste0('bundle',b,':condition',i,':communications-all-fields')
      exported <- read.delim(file.path(home,'centrality.tsv'),sep='\t',check.names=FALSE,stringsAsFactors=FALSE)
      expectedCount <- 0L
      for(slot in c('net','netP'))for(feature in names(methods::slot(original,slot)$centr))for(metric in names(methods::slot(original,slot)$centr[[feature]])) {
        node <- methods::slot(original,slot)$centr[[feature]][[metric]];groups <- levels(original@idents)
        selected <- exported[exported$slot==slot & exported$feature==feature & exported$metric==metric,,drop=FALSE]
        stopifnot(identical(selected$group,groups));equal(paste(slot,feature,metric),as.numeric(node),selected$value);expectedCount <- expectedCount+length(node)
      }
      stopifnot(nrow(exported)==expectedCount);checks[[length(checks)+1L]] <- paste0('bundle',b,':condition',i,':centrality-all-fields')
    }
  }
  status <- 'PASS_EXPORTED_FIELDS_MATCH_SEPARATE_ORIGINAL_REFITS'
},error=function(e){detail <<- conditionMessage(e)})
jsonlite::write_json(list(status=status,detail=detail,source_label=label,checks=checks,R_inference_refitted_by_this_script=FALSE,reference='Separate original refits, not controller objects',native_graph_estimators_independently_reimplemented=FALSE),file.path(out,'COMPLETED.json'),pretty=TRUE,auto_unbox=TRUE)
quit(status=if(startsWith(status,'PASS_'))0L else 2L)
