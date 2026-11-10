# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Literal original default-family fits, separate from the controller's worker.
args <- commandArgs(TRUE);out <- args[1];input <- args[2];bundles <- args[3:4]
.libPaths(c(args[-(1:4)],.Library),include.site=FALSE);library(CellChat);future::plan(future::sequential)
stopifnot(as.character(packageVersion('CellChat'))=='2.2.0',!dir.exists(out),dir.create(out))
checks <- list();warnings <- list();status <- 'FAIL2_SEPARATE_STRENGTH_WORKFLOW_REFERENCE';detail <- ''
equal <- function(name,x,y) {a <- all.equal(x,y,tolerance=2e-12,check.attributes=TRUE);if(!isTRUE(a))stop(name,': ',paste(a,collapse='; '));checks[[length(checks)+1L]] <<- name}
tryCatch(withCallingHandlers({
  meta <- read.delim(file.path(input,'metadata.tsv'),sep='\t',quote='',comment.char='',colClasses='character',check.names=FALSE,na.strings=NULL)
  d <- read.delim(file.path(input,'expression.tsv'),sep='\t',quote='',comment.char='',check.names=FALSE);data <- as.matrix(d[,-1,drop=FALSE]);rownames(data) <- d[[1]]
  stopifnot(nrow(data)==33L,ncol(data)==252L,identical(colnames(data),meta$cell_id))
  conditions <- c('control','treated');samples <- c(paste0('control-',1:3),paste0('treated-',1:4));groups <- c('sender','receiver','other');database <- subsetDB(CellChatDB.human,search=c('Secreted Signaling','ECM-Receptor','Cell-Cell Contact'))
  cases <- list()
  for(i in 1:9) {
    pooled <- i<=2;id <- if(pooled)paste0('condition-',i) else paste0('sample-',i-2);use <- if(pooled)which(meta$condition==conditions[i]) else which(meta$sample==samples[i-2])
    frame <- data.frame(labels=factor(meta$group[use],levels=groups),samples=factor(meta$sample[use],levels=samples[samples %in% meta$sample[use]]),row.names=meta$cell_id[use])
    object <- createCellChat(data[,use,drop=FALSE],meta=frame,group.by='labels',datatype='RNA');object@DB <- database
    object <- subsetData(object);object <- identifyOverExpressedGenes(object);object <- identifyOverExpressedInteractions(object);object <- computeCommunProb(object)
    object <- if(pooled)filterCommunication(object,min.cells=10,min.samples=3) else filterCommunication(object)
    object <- computeCommunProbPathway(object);object <- aggregateNet(object);object <- netAnalysis_computeCentrality(object,slot.name='net');object <- netAnalysis_computeCentrality(object,slot.name='netP');cases[[id]] <- object;saveRDS(object,file.path(out,paste0(id,'.rds')))
    for(b in seq_along(bundles)) {
      saved <- readRDS(file.path(bundles[b],'results','cases',id,'original-cellchat.rds'))
      for(slot in c('data','data.signaling','meta','idents','var.features','LR','net','netP'))equal(paste0('bundle',b,':',id,':',slot),methods::slot(object,slot),methods::slot(saved,slot))
    }
  }
  # An independent matrix-sum route checks exported node strengths, selection
  # and actual original sample tests rather than replaying the worker's code.
  networks <- lapply(cases,function(o){net <- o@net$prob;net[o@net$pval>=0.05] <- 0;list(net=net,netP=o@netP$prob)})
  score <- function(id,slot,feature,group,direction) {
    a <- networks[[id]][[slot]];if(!(feature %in% dimnames(a)[[3]])||!(group %in% dimnames(a)[[1]]))return(NA_real_)
    if(direction=='outgoing')sum(a[group,,feature]) else sum(a[,group,feature])
  }
  references <- list()
  for(slot in c('net','netP'))for(feature in unique(unlist(lapply(networks,function(n)dimnames(n[[slot]])[[3]]),use.names=FALSE)))for(group in groups)for(direction in c('outgoing','incoming')) {
    control <- score('condition-1',slot,feature,group,direction);treated <- score('condition-2',slot,feature,group,direction);delta <- treated-control
    values <- vapply(1:7,function(i)score(paste0('sample-',i),slot,feature,group,direction),numeric(1));stat <- NA_real_;p <- NA_real_;method <- NA_character_;testStatus <- if(!is.finite(delta))'not-tested-incomplete-pooled-strength' else if(abs(delta)<=0.25)'not-tested-pooled-difference-below-or-equal-threshold' else if(any(!is.finite(values)))'not-tested-incomplete-sample-strength' else 'test-required'
    if(testStatus=='test-required') {fit <- wilcox.test(values[1:3],values[4:7]);stat <- unname(fit$statistic);p <- fit$p.value;method <- fit$method;testStatus <- if(is.finite(p))'tested-original-wilcox' else 'original-nonfinite-wilcox-result'}
    references[[length(references)+1L]] <- data.frame(slot=slot,feature=feature,group=group,direction=direction,control=control,treated=treated,treated_minus_control=delta,positive_difference_above_threshold=if(is.finite(delta))delta>0.25 else NA,negative_difference_above_threshold=if(is.finite(delta))delta< -0.25 else NA,absolute_difference_above_threshold=if(is.finite(delta))abs(delta)>0.25 else NA,selected=if(is.finite(delta))abs(delta)>0.25 else NA,wilcox_statistic=stat,wilcox_pvalue=p,test_method=method,status=testStatus)
  }
  expected <- do.call(rbind,references);write.table(expected,file.path(out,'independent-strength-reference.tsv'),sep='\t',quote=TRUE,row.names=FALSE,qmethod='double')
  for(b in seq_along(bundles)) {
    actual <- read.delim(file.path(bundles[b],'results','comparison','pooled-and-sample-strengths.tsv'),check.names=FALSE,stringsAsFactors=FALSE)
    stopifnot(nrow(actual)==nrow(expected))
    for(column in names(expected))equal(paste0('bundle',b,':strengths:',column),expected[[column]],actual[[column]])
  }
  status <- 'PASS_SEPARATE_ORIGINAL_DEFAULT_STRENGTH_WORKFLOW_AND_COMPARISONS'
},warning=function(w){warnings[[length(warnings)+1L]] <<- conditionMessage(w);invokeRestart('muffleWarning')}),error=function(e){detail <<- conditionMessage(e)})
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'));jsonlite::write_json(list(status=status,detail=detail,separate_original_fits=9L,checks=checks,warnings=warnings,historical_paper_reproduction=FALSE,native_pvalues_independently_reimplemented=FALSE),file.path(out,'COMPLETED.json'),pretty=TRUE,auto_unbox=TRUE)
quit(status=if(startsWith(status,'PASS_'))0L else 2L)
