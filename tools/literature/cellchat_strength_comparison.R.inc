# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Build-time included after the unchanged original inference/centrality calls.
# Values come from the original S4 objects, never from NA-coercing TSV readers.
  stopifnot(r$comparison$difference_mode %in% c('absolute','positive','negative'))
  statsFunctions <- function() {
    original <- list(generic=stats::wilcox.test,default=getS3method('wilcox.test','default',envir=asNamespace('stats')))
    lapply(original,function(f)list(formals=deparse(formals(f),width.cutoff=500L,control='all'),body=deparse(body(f),width.cutoff=500L,control='all')))
  }
  statsBefore <- statsFunctions()
  directions <- c(outgoing='outdeg',incoming='indeg');caseOrder <- names(caseReports)
  sampleConditions <- vapply(sampleOrder,function(s)unique(meta$condition[meta$sample==s]),character(1))
  longStrengths <- list();pooledStrengths <- list();selectedStrengths <- list();strengthWarnings <- 0L
  valueFor <- function(id,slot,feature,group,metric) {
    groups <- levels(objects[[id]]@idents)
    if(!(group %in% groups))return(list(value=NA_real_,status='group-absent-after-prefilter'))
    centr <- methods::slot(objects[[id]],slot)$centr
    if(!(feature %in% names(centr)))return(list(value=NA_real_,status='feature-not-selected'))
    values <- centr[[feature]][[metric]];stopifnot(length(values)==length(groups))
    value <- as.numeric(values[match(group,groups)]);stopifnot(is.finite(value),value>=0)
    list(value=value,status='present-original-strength')
  }
  for(slot in c('net','netP')) {
    features <- unique(unlist(lapply(caseOrder,function(id)names(methods::slot(objects[[id]],slot)$centr)),use.names=FALSE))
    for(feature in features)for(group in groupOrder)for(direction in names(directions)) {
      values <- lapply(caseOrder,function(id)valueFor(id,slot,feature,group,directions[[direction]]));names(values) <- caseOrder
      for(id in caseOrder) {
        report <- caseReports[[id]];condition <- if(report$kind=='condition')report$value else sampleConditions[match(report$value,sampleOrder)]
        longStrengths[[length(longStrengths)+1L]] <- data.frame(case=id,kind=report$kind,case_value=report$value,condition=condition,slot=slot,feature=feature,group=group,direction=direction,value=values[[id]]$value,status=values[[id]]$status)
      }
      control <- values[['condition-1']]$value;treated <- values[['condition-2']]$value;delta <- treated-control;threshold <- r$comparison$strength_difference_threshold
      positive <- if(is.finite(delta))delta>threshold else NA;negative <- if(is.finite(delta))delta< -threshold else NA;absolute <- if(is.finite(delta))abs(delta)>threshold else NA
      selected <- switch(r$comparison$difference_mode,positive=positive,negative=negative,absolute=absolute)
      samples <- vapply(seq_along(sampleOrder),function(i)values[[paste0('sample-',i)]]$value,numeric(1));x <- samples[sampleConditions==conditionOrder[1]];y <- samples[sampleConditions==conditionOrder[2]]
      stat <- NA_real_;pvalue <- NA_real_;method <- NA_character_;rowWarnings <- character()
      testStatus <- if(!is.finite(delta))'not-tested-incomplete-pooled-strength' else if(!selected)'not-tested-pooled-difference-below-or-equal-threshold' else if(any(!is.finite(samples)))'not-tested-incomplete-sample-strength' else if(length(x)<2L||length(y)<2L)'not-tested-fewer-than-two-samples-per-condition' else 'test-required'
      if(testStatus=='test-required') {
        fit <- withCallingHandlers(stats::wilcox.test(x,y,alternative=r$comparison$alternative,paired=FALSE,exact=r$comparison$exact,correct=r$comparison$continuity_correction),warning=function(w){rowWarnings <<- c(rowWarnings,conditionMessage(w))})
        stat <- unname(fit$statistic);pvalue <- fit$p.value;method <- fit$method;testStatus <- if(is.finite(pvalue))'tested-original-wilcox' else 'original-nonfinite-wilcox-result'
      }
      strengthWarnings <- strengthWarnings+length(rowWarnings)
      row <- data.frame(slot=slot,feature=feature,group=group,direction=direction,control=control,treated=treated,treated_minus_control=delta,positive_difference_above_threshold=positive,negative_difference_above_threshold=negative,absolute_difference_above_threshold=absolute,difference_mode=r$comparison$difference_mode,selected=selected,control_sample_count=sum(is.finite(x)),treated_sample_count=sum(is.finite(y)),control_missing_count=sum(!is.finite(x)),treated_missing_count=sum(!is.finite(y)),wilcox_statistic=stat,wilcox_pvalue=pvalue,test_method=method,test_warning_count=length(rowWarnings),status=testStatus)
      pooledStrengths[[length(pooledStrengths)+1L]] <- row
      if(isTRUE(selected))selectedStrengths[[length(selectedStrengths)+1L]] <- row
    }
  }
  pooled <- do.call(rbind,pooledStrengths)
  writeTable(do.call(rbind,longStrengths),file.path(out,'comparison','strengths-by-case.tsv'))
  writeTable(pooled,file.path(out,'comparison','pooled-and-sample-strengths.tsv'))
  writeTable(if(length(selectedStrengths)>0L)do.call(rbind,selectedStrengths) else pooled[0,,drop=FALSE],file.path(out,'comparison','selected-strengths.tsv'))
  statsAfter <- statsFunctions();stopifnot(identical(statsBefore,statsAfter))
  writeJson(list(before=statsBefore,after=statsAfter),file.path(out,'comparison','guarded-original-stats.json'))
  writeJson(list(comparison_rows=nrow(pooled),selected_rows=sum(pooled$selected,na.rm=TRUE),not_tested_rows=sum(startsWith(pooled$status,'not-tested-')),nonfinite_test_rows=sum(pooled$status=='original-nonfinite-wilcox-result'),original_strength_test_warnings=strengthWarnings,difference_mode=r$comparison$difference_mode,missing_strengths_filled=FALSE,multiplicity_correction_inferred=FALSE),file.path(out,'comparison','strength-report.json'))
