# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent matrix-sum and original stats calls on controller-captured objects.
# This script does not claim independent CellChat refitting of these edge families.
args <- commandArgs(TRUE);out <- args[1];manifest <- args[2]
# Bootstrap jsonlite through the already selected private library paths.
.libPaths(c(args[-(1:2)],.Library),include.site=FALSE);library(CellChat);r <- jsonlite::read_json(manifest,simplifyVector=TRUE)
stopifnot(!dir.exists(out),dir.create(out));status <- 'FAIL2_ORIGINAL_STRENGTH_EXPORT_REFERENCE';detail <- '';checks <- list();rowCounts <- list();nonfinite <- list()
same <- function(name,x,y){a <- all.equal(x,y,tolerance=2e-12,check.attributes=FALSE);if(!isTRUE(a))stop(name,': ',paste(a,collapse='; '));if(is.numeric(x)&&is.numeric(y))stopifnot(identical(unname(is.nan(x)),unname(is.nan(y))),identical(unname(is.na(x)&!is.nan(x)),unname(is.na(y)&!is.nan(y))));checks[[length(checks)+1L]] <<- name}
tryCatch({
  for(bundle in r$bundles) {
    request <- jsonlite::read_json(file.path(bundle,'source-request.json'),simplifyVector=TRUE)$analysis;samples <- request$sample_order;conditions <- request$condition_order;groups <- request$group_order
    metadata <- read.delim(file.path(bundle,'inputs','metadata.tsv'),sep='\t',quote='',comment.char='',colClasses='character',na.strings=NULL)
    sampleCondition <- vapply(samples,function(s)unique(metadata$condition[metadata$sample==s]),character(1));ids <- c(paste0('condition-',1:2),paste0('sample-',seq_along(samples)));networks <- list()
    for(id in ids) {o <- readRDS(file.path(bundle,'results','cases',id,'original-cellchat.rds'));a <- o@net$prob;a[o@net$pval>=request$network$pvalue_threshold] <- 0;networks[[id]] <- list(net=a,netP=o@netP$prob)}
    score <- function(id,slot,feature,group,direction) {
      a <- networks[[id]][[slot]];if(!(feature %in% dimnames(a)[[3]])||!(group %in% dimnames(a)[[1]]))return(NA_real_)
      if(direction=='outgoing')sum(a[group,,feature]) else sum(a[,group,feature])
    }
    expected <- list();long <- list()
    for(slot in c('net','netP'))for(feature in unique(unlist(lapply(networks,function(n)dimnames(n[[slot]])[[3]]),use.names=FALSE)))for(group in groups)for(direction in c('outgoing','incoming')) {
      scores <- vapply(ids,function(id)score(id,slot,feature,group,direction),numeric(1))
      for(i in seq_along(ids))long[[length(long)+1L]] <- data.frame(case=ids[i],slot=slot,feature=feature,group=group,direction=direction,value=scores[i])
      delta <- scores[2]-scores[1];cutoff <- request$comparison$strength_difference_threshold;positive <- if(is.finite(delta))delta>cutoff else NA;negative <- if(is.finite(delta))delta< -cutoff else NA;absolute <- if(is.finite(delta))abs(delta)>cutoff else NA;selected <- switch(request$comparison$difference_mode,positive=positive,negative=negative,absolute=absolute)
      values <- scores[-(1:2)];x <- values[sampleCondition==conditions[1]];y <- values[sampleCondition==conditions[2]];stat <- NA_real_;p <- NA_real_;method <- NA_character_
      disposition <- if(!is.finite(delta))'not-tested-incomplete-pooled-strength' else if(!selected)'not-tested-pooled-difference-below-or-equal-threshold' else if(any(!is.finite(values)))'not-tested-incomplete-sample-strength' else if(length(x)<2||length(y)<2)'not-tested-fewer-than-two-samples-per-condition' else 'test-required'
      if(disposition=='test-required') {fit <- suppressWarnings(stats::wilcox.test(x,y,alternative=request$comparison$alternative,exact=request$comparison$exact,correct=request$comparison$continuity_correction,paired=FALSE));stat <- unname(fit$statistic);p <- fit$p.value;method <- fit$method;disposition <- if(is.finite(p))'tested-original-wilcox' else 'original-nonfinite-wilcox-result'}
      expected[[length(expected)+1L]] <- data.frame(slot=slot,feature=feature,group=group,direction=direction,control=unname(scores[1]),treated=unname(scores[2]),treated_minus_control=unname(delta),positive_difference_above_threshold=unname(positive),negative_difference_above_threshold=unname(negative),absolute_difference_above_threshold=unname(absolute),selected=unname(selected),control_sample_count=sum(is.finite(x)),treated_sample_count=sum(is.finite(y)),control_missing_count=sum(!is.finite(x)),treated_missing_count=sum(!is.finite(y)),wilcox_statistic=stat,wilcox_pvalue=p,test_method=method,status=disposition)
    }
    expected <- do.call(rbind,expected);actual <- read.delim(file.path(bundle,'results','comparison','pooled-and-sample-strengths.tsv'),check.names=FALSE,stringsAsFactors=FALSE);stopifnot(nrow(actual)==nrow(expected));tag <- basename(dirname(bundle))
    for(column in names(expected))same(paste0(tag,':pooled:',column),expected[[column]],actual[[column]])
    expectedLong <- do.call(rbind,long);actualLong <- read.delim(file.path(bundle,'results','comparison','strengths-by-case.tsv'),check.names=FALSE,stringsAsFactors=FALSE)
    for(column in names(expectedLong))same(paste0(tag,':case:',column),unname(expectedLong[[column]]),unname(actualLong[[column]]))
    rowCounts[[tag]] <- nrow(expected);nonfinite[[tag]] <- sum(expected$status=='original-nonfinite-wilcox-result')
  }
  status <- 'PASS_ORIGINAL_STRENGTH_EXPORTS_AND_PARAMETER_SPECIFIC_TESTS'
},error=function(e){detail <<- conditionMessage(e)})
jsonlite::write_json(list(status=status,detail=detail,checks=checks,bundle_rows=rowCounts,nonfinite_tests=nonfinite,CellChat_inference_refitted=FALSE,independent_CellChat_edge_refit_claimed=FALSE),file.path(out,'COMPLETED.json'),pretty=TRUE,auto_unbox=TRUE)
quit(status=if(startsWith(status,'PASS_'))0L else 2L)
