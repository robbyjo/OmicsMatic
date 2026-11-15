# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Synthetic project inputs. No inferred author gene panel or scoring options.
local({
  args <- commandArgs(TRUE);stopifnot(length(args)==2L)
  .libPaths(c(args[[1L]],.libPaths()));requireNamespace("Matrix")
  out <- args[[2L]];stopifnot(!dir.exists(out));dir.create(out,recursive=TRUE)
  make <- function(n=80L,g=40L,seed=7101L) {
    set.seed(seed)
    group <- rep(0:2,length.out=n)
    rate <- outer(seq_len(g),group,function(row,col)2+16*((row-1L)%%3L==col))
    values <- matrix(rpois(g*n,rate),nrow=g)
    dimnames(values) <- list(paste0("gene_",seq_len(g)),paste0("cell_",seq_len(n)))
    values
  }
  cases <- list();base <- make()
  add <- function(name,data=base,genes=rownames(data),seed=19L,extra=list(),attached=character(),opts=list()) {
    set.seed(seed)
    request <- list(schema=1L,operation="doubletFinder",arguments=c(list(data=data,select.genes=genes),extra),
      rng=list(kind=RNGkind(),seed=.Random.seed),caller_packages=attached,options=opts)
    saveRDS(request,file.path(out,paste0(name,".rds")),version=3)
    cases[[name]] <<- list(dim=dim(data),class=class(data),seed=seed,argument_names=names(request$arguments),
      caller_packages=attached,options=opts,synthetic=TRUE,author_settings_inferred=FALSE)
  }
  add("dense-default")
  add("dense-odd-k5",extra=list(k=5L))
  add("dense-odd-k3",extra=list(k=3L),seed=73L)
  add("dense-p035",extra=list(proportion.artificial=.35,k=12L),seed=999L)
  add("sparse-dgC-default",data=as(base,"dgCMatrix"))
  add("sparse-dgT-default",data=as(as(base,"dgCMatrix"),"TsparseMatrix"))
  add("panel-reordered",genes=rev(rownames(base)[1:18]),extra=list(k=7L))
  add("cells-reordered",data=base[,rev(seq_len(ncol(base)))])
  literal <- base;colnames(literal)[1:4] <- c(" Xreal ","cell,comma","cell\ttab","cell|pipe")
  add("literal-identifiers",data=literal)
  duplicate <- base;colnames(duplicate)[2L] <- colnames(duplicate)[1L]
  add("duplicate-identifiers",data=duplicate)
  collision <- base;colnames(collision)[1:2] <- c("X1","X2")
  add("artificial-name-collision",data=collision)
  add("missing-gene",genes=c(rownames(base),"absent_gene"))
  add("too-few-real",data=base[,1:8,drop=FALSE])
  add("single-artificial",data=base[,1:20,drop=FALSE],extra=list(proportion.artificial=1/21))
  add("zero-artificial",extra=list(proportion.artificial=0))
  add("k1",extra=list(k=1L));add("k2",extra=list(k=2L))
  zero <- base;zero[,1L] <- 0
  add("zero-count-column-dense",data=zero)
  add("zero-count-column-sparse",data=as(zero,"dgCMatrix"))
  missing <- base;missing[1L,1L] <- NA_real_;add("NA-count",data=missing)
  infinite <- base;infinite[1L,1L] <- Inf;add("Inf-count",data=infinite)
  add("plot-without-ggplot2",extra=list(plot=TRUE))
  add("plot-with-ggplot2",extra=list(plot=TRUE),attached="ggplot2")
  add("partial-match-warning",opts=list(warnPartialMatchDollar=TRUE))
  add("partial-match-warning-as-error",opts=list(warnPartialMatchDollar=TRUE,warn=2L))
  # Cross the original >10000 real-cell branch, including sampled PCA projection.
  add("large-10001-real",data=make(n=10001L,g=36L,seed=9101L),seed=101L)
  jsonlite::write_json(list(status="SYNTHETIC_ORIGINAL_CALLER_INPUTS_NOT_AUTHOR_DATA",cases=cases),
    file.path(out,"CONTROLS.json"),pretty=TRUE,auto_unbox=TRUE,null="null")
})
