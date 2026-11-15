# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Synthetic annotated references, never a substituted author reference dataset.
local({
  a <- commandArgs(TRUE);stopifnot(length(a)%in%c(3L,4L));.libPaths(c(a[[1L]],.libPaths()))
  edition <- a[[2L]];out <- a[[3L]];stopifnot(!dir.exists(out));dir.create(out,recursive=TRUE)
  requireNamespace("Matrix");requireNamespace("SingleCellExperiment");requireNamespace("SingleR")
  set.seed(8131L)
  make <- function(per.group) {
    group <- rep(seq_len(3L),each=per.group)
    rate <- outer(rep(seq_len(3L),each=30L),group,function(g,c)1+30*(g==c))
    value <- log1p(matrix(rpois(length(rate),rate),nrow=90L))
    dimnames(value) <- list(paste0("gene_",seq_len(90L)),paste0("sample_",seq_along(group)))
    list(expression=value,group=group)
  }
  reference <- make(6L);query <- make(3L)
  ref <- reference$expression;test <- query$expression
  colnames(ref) <- paste0("reference_",seq_len(ncol(ref)))
  colnames(test) <- c(" cell 1 ","cell,2","cell\t3",paste0("cell_",4:9))
  levels <- c("T cell","B_cell","neutrophil");labels <- levels[reference$group]
  expected <- levels[query$group];cases <- list()
  add <- function(name,args=list(test=test,ref=ref,labels=labels),operation="SingleR",known=NULL,seed=917L,opts=list()) {
    set.seed(seed)
    request <- list(schema=1L,edition=edition,operation=operation,arguments=args,
      rng=list(kind=RNGkind(),seed=.Random.seed),caller_packages=character(),options=opts)
    saveRDS(request,file.path(out,paste0(name,".rds")),version=3)
    cases[[name]] <<- list(operation=operation,argument_names=names(args),known_expected_labels=known,
      synthetic=TRUE,author_reference_or_settings_inferred=FALSE)
  }
  call <- function(extra=list(),te=test,re=ref,la=labels)c(list(test=te,ref=re,labels=la),extra)
  add("dense-default",known=expected)
  add("sparse-dgC",call(te=as(test,"dgCMatrix"),re=as(ref,"dgCMatrix")),known=expected)
  add("sparse-dgT",call(te=as(as(test,"dgCMatrix"),"TsparseMatrix"),re=as(as(ref,"dgCMatrix"),"TsparseMatrix")))
  add("delayed-matrices",call(te=DelayedArray::DelayedArray(test),re=DelayedArray::DelayedArray(ref)),known=expected)
  sce <- SingleCellExperiment::SingleCellExperiment(list(logcounts=test))
  rse <- SummarizedExperiment::SummarizedExperiment(list(logcounts=ref))
  add("SCE-and-SE-default-assays",call(te=sce,re=rse),known=expected)
  alt <- SummarizedExperiment::SummarizedExperiment(list(unusual=test));ral <- SummarizedExperiment::SummarizedExperiment(list(unusual=ref))
  add("explicit-alternative-assays",call(list(assay.type.test="unusual",assay.type.ref="unusual"),alt,ral),known=expected)
  add("fine-tune-disabled",call(list(fine.tune=FALSE)),known=expected)
  add("pruning-disabled",call(list(prune=FALSE)),known=expected)
  add("quantile-zero",call(list(quantile=0)))
  add("quantile-one",call(list(quantile=1)))
  add("five-DE-markers",call(list(de.n=5L)),known=expected)
  add("explicit-gene-vector",call(list(genes=rownames(ref)[seq(1L,90L,by=3L)])))
  add("restricted-genes",call(list(restrict=rownames(ref)[1:75])))
  add("reordered-test-genes",call(te=test[90:1,,drop=FALSE]),known=expected)
  add("reordered-reference-genes",call(re=ref[90:1,,drop=FALSE]),known=expected)
  add("reordered-cells",call(te=test[,9:1,drop=FALSE]),known=rev(expected))
  duplicate <- test;colnames(duplicate)[2L] <- colnames(duplicate)[1L]
  add("duplicate-literal-cell-identifiers",call(te=duplicate),known=expected)
  duplicate <- ref;rownames(duplicate)[2L] <- rownames(duplicate)[1L]
  add("duplicate-reference-gene-identifiers",call(re=duplicate))
  add("factor-label-order-and-unused-level",call(la=factor(labels,levels=c("unused",rev(levels)))))
  add("fine-tune-threshold-zero",call(list(tune.thresh=0)))
  add("fine-tune-threshold-large",call(list(tune.thresh=1)))
  add("aggregate-reference",call(list(aggr.ref=TRUE)),seed=2003L)
  add("three-query-clusters",call(list(clusters=paste0("cluster_",query$group))))
  add("multiple-references-recompute",call(re=list(first=ref,second=ref[,18:1,drop=FALSE]),la=list(first=labels,second=rev(labels))))
  add("multiple-references-common",call(list(recompute=FALSE),re=list(first=ref,second=ref[,18:1,drop=FALSE]),la=list(first=labels,second=rev(labels))))
  # Fresh R tempdir() values differ between processes. Replay explicitly supplies
  # the captured caller directory as an original constructor argument; no input,
  # parameter field or environment is removed or rewritten after generation.
  annoy <- if(length(a)==4L) {
    captured <- readRDS(a[[4L]])$arguments$BNPARAM
    stopifnot(is(captured,"AnnoyParam"))
    if("dir"%in%slotNames(captured)) {
      stopifnot(length(captured@dir)==1L)
      BiocNeighbors::AnnoyParam(ntrees=11L,dir=captured@dir)
    } else BiocNeighbors::AnnoyParam(ntrees=11L)
  } else BiocNeighbors::AnnoyParam(ntrees=11L)
  add("approximate-Annoy-neighbours",call(list(BNPARAM=annoy)))
  add("train-then-classify",args=list(training=list(ref=ref,labels=labels),classification=list(test=test)),operation="train-classify",known=expected)
  add("train-classify-no-fine-tune",args=list(training=list(ref=ref,labels=labels),classification=list(test=test,fine.tune=FALSE,prune=FALSE)),operation="train-classify",known=expected)
  add("no-common-genes",call(te=structure(test,dimnames=list(paste0("absent_",1:90),colnames(test)))))
  add("label-count-mismatch",call(la=labels[-1L]))
  na.labels <- labels;na.labels[1L] <- NA_character_;add("missing-reference-label",call(la=na.labels))
  add("single-reference-label",call(la=rep("one label",length(labels))))
  missing <- test;missing[1L,1L] <- NA_real_;add("NA-test-check-enabled",call(te=missing))
  add("NA-test-check-disabled",call(list(check.missing=FALSE),te=missing))
  missing <- ref;missing[1L,1L] <- NaN;add("NaN-reference-check-enabled",call(re=missing))
  infinite <- test;infinite[1L,1L] <- Inf;add("Inf-test-value",call(te=infinite))
  zero <- test;zero[,1L] <- 0;add("constant-zero-test-cell",call(te=zero))
  add("zero-query-cells",call(te=test[,FALSE,drop=FALSE]))
  add("identical-reference-values",call(re=ref*0))
  add("warning-as-error-default",opts=list(warn=2L))
  scores <- rbind(c(.9,.2,.1),c(.8,.3,.1),c(.2,.8,.3),c(.1,.9,.2),c(.1,.2,.7),c(.2,.1,.9))
  colnames(scores) <- levels;rownames(scores) <- paste0("score_cell_",1:6)
  result <- list(scores=scores,labels=rep(levels,each=2L),delta.next=rep(.1,6L),
    tuning.scores=list(first=rep(.7,6L),second=rep(.6,6L)))
  add("delta-from-median",list(results=result),"getDeltaFromMedian")
  add("prune-scores-default",list(results=result),"pruneScores")
  add("prune-thresholds",list(results=result,get.thresholds=TRUE),"pruneScores")
  add("prune-no-surviving-labels",list(results=result,min.diff.med=10,get.thresholds=TRUE),"pruneScores")
  add("prune-nmads-infinite",list(results=result,nmads=Inf),"pruneScores")
  add("prune-next-difference",list(results=result,min.diff.next=.2),"pruneScores")
  unknown <- result;unknown$scores[1L,2L] <- NA_real_
  add("delta-missing-score",list(results=unknown),"getDeltaFromMedian")
  add("prune-missing-score",list(results=unknown),"pruneScores")
  unknown <- result;unknown$labels <- factor(unknown$labels,levels=c(levels,"unused"))
  add("prune-unused-factor-label",list(results=unknown,get.thresholds=TRUE),"pruneScores")
  add("aggregate-reference-original",list(ref=ref,labels=labels),"aggregateReference",seed=312L)
  jsonlite::write_json(list(status="SYNTHETIC_REFERENCES_NOT_AUTHOR_DATA",edition=edition,cases=cases),
    file.path(out,"CONTROLS.json"),pretty=TRUE,auto_unbox=TRUE,null="null")
})
