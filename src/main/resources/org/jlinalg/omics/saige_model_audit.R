# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Static adapter inspector. It does not fit or alter the original SAIGE model.
args <- commandArgs(TRUE)
stopifnot(length(args)==3)
limit <- as.double(args[3]);stopifnot(is.finite(limit),limit>=1,limit<=20000000)
environment <- new.env(parent=baseenv())
loaded <- load(args[1],envir=environment)
stopifnot('modglmm' %in% loaded)
model <- environment$modglmm
stopifnot(is.list(model),isTRUE(model$converged),is.matrix(model$X),is.numeric(model$y))
n <- length(model$sampleID);p <- ncol(model$X)
loco <- if(isTRUE(model$LOCO))model$LOCOResult else list()
stopifnot(n>=4,nrow(model$X)==n,p>=1,p<=128,length(model$y)==n,length(loco)%in%c(0L,22L))
cells <- n*(p+5+2*length(loco));stopifnot(cells<=limit)
primitive <- function(o) {
  mu <- as.numeric(o$fitted.values);res <- as.numeric(o$residuals)
  stopifnot(length(mu)==n,length(res)==n,all(is.finite(mu)),all(is.finite(res)))
  list(fitted=mu,residuals=res)
}
base <- primitive(model)
stopifnot(all(is.finite(model$X)),all(is.finite(model$y)),all(is.finite(model$theta)))
result <- list(schema_version=1,trait_type=model$traitType,converged=model$converged,
  sample_ids=as.character(model$sampleID),outcomes=as.numeric(model$y),design=unname(model$X),
  theta=as.numeric(model$theta),covariate_offset=model$isCovariateOffset,
  sparse_fit=model$useSparseGRMtoFitNULL,loco=isTRUE(model$LOCO),base=base,
  loco_models=lapply(loco,primitive),exported_cells=cells)
jsonlite::write_json(result,args[2],auto_unbox=TRUE,digits=NA,na='string',null='null',pretty=FALSE)
