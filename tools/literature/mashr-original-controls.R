# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original mashr0.2.79 calls only. No Java-generated expected outputs.
args <- commandArgs(trailingOnly=TRUE)
stopifnot(length(args) == 2)
.libPaths(c(args[1], .Library), include.site=FALSE)
library(mashr)
stopifnot(as.character(packageVersion('mashr')) == '0.2.79')
root <- args[2]
options(digits=17, warn=1)
writeLines(capture.output(sessionInfo()), file.path(root, 'original-session-info.txt'))
write.table(installed.packages()[,c('Package','Version','LibPath')], file.path(root, 'original-installed-packages.tsv'),
            sep='\t', quote=FALSE, row.names=FALSE)
readm <- function(path) as.matrix(read.table(path, header=FALSE, sep='\t', check.names=FALSE))
dump <- function(value, path) {
  if (is.null(dim(value))) value <- matrix(value, nrow=1)
  text <- matrix(format(as.vector(value), digits=17, scientific=TRUE, trim=TRUE), nrow=nrow(value))
  write.table(text, path, sep='\t', quote=FALSE, row.names=FALSE, col.names=FALSE)
}
cases <- read.table(file.path(root,'cases.tsv'), header=TRUE, sep='\t', stringsAsFactors=FALSE)
for (c in seq_len(nrow(cases))) {
  spec <- cases[c,]
  folder <- file.path(root, spec$case)
  bhat <- readm(file.path(folder,'bhat.tsv'))
  shat <- readm(file.path(folder,'shat.tsv'))
  v <- readm(file.path(folder,'residual.tsv'))
  u <- lapply(0:(spec$components-1), function(i) readm(file.path(folder,paste0('prior-',i,'.tsv'))))
  names(u) <- paste0('supplied_', seq_along(u))
  pi <- as.vector(readm(file.path(folder,'proportions.tsv')))
  data <- mash_set_data(bhat, shat, V=v, alpha=spec$alpha)
  g <- list(pi=pi, Ulist=u, grid=1, usepointmass=FALSE)
  # Capture the actual source expansion; no assumed grid/order in the Java test.
  expanded <- mashr:::expand_cov(g$Ulist, g$grid, g$usepointmass)
  for(i in seq_along(expanded)) dump(expanded[[i]], file.path(folder,paste0('expanded-',i-1,'.tsv')))
  canon <- cov_canonical(data)
  writeLines(names(canon), file.path(folder,'canonical-names.txt'))
  for(i in seq_along(canon)) dump(canon[[i]], file.path(folder,paste0('canonical-',i-1,'.tsv')))
  for (algorithm in c('R', 'Rcpp')) {
    destination <- file.path(folder, algorithm)
    dir.create(destination)
    warnings <- character()
    stage <- 'original-mash-call'
    status <- tryCatch(withCallingHandlers({
      fit <- mash(data, g=g, fixg=TRUE, pi_thresh=spec$threshold,
                  algorithm.version=algorithm, outputlevel=4, output_lfdr=TRUE, verbose=FALSE)
      result <- fit$result
      writeLines(capture.output(dput(lapply(result, dim))), file.path(destination,'result-dimensions.txt'))
      stage <- 'capture-original-marginals'
      for (pair in list(c('PosteriorMean','mean'), c('PosteriorSD','sd'), c('NegativeProb','negative'),
                        c('lfdr','zero'), c('lfsr','lfsr'))) dump(result[[pair[1]]], file.path(destination,paste0(pair[2],'.tsv')))
      stage <- 'capture-original-covariances'
      stopifnot(length(result$PosteriorCov) == ncol(bhat)^2*nrow(bhat))
      completeCov <- array(result$PosteriorCov, dim=c(ncol(bhat),ncol(bhat),nrow(bhat)))
      cov <- t(matrix(vapply(seq_len(nrow(bhat)), function(j) as.vector(completeCov[,,j]), numeric(ncol(bhat)^2)),
                      nrow=ncol(bhat)^2, ncol=nrow(bhat)))
      dump(cov, file.path(destination,'covariance-column-major.tsv'))
      stage <- 'capture-original-weights-and-likelihoods'
      weights <- matrix(0,nrow=nrow(bhat),ncol=length(pi))
      weights[,pi > spec$threshold] <- fit$posterior_weights
      dump(weights, file.path(destination,'weights.tsv'))
      dump(as.vector(fit$vloglik), file.path(destination,'loglik.tsv'))
      component <- sweep(fit$lm$loglik_matrix, 1, fit$lm$lfactors-rowSums(log(data$Shat_alpha)), '+')
      dump(component, file.path(destination,'component-loglik.tsv'))
      saveRDS(fit, file.path(destination,'original-fit.rds'), compress='xz')
      'COMPLETED'
    }, warning=function(w) {warnings <<- c(warnings, conditionMessage(w)); invokeRestart('muffleWarning')}),
    error=function(e) {writeLines(c(paste0('stage: ',stage),conditionMessage(e)), file.path(destination,'error.txt')); 'FAILED'})
    writeLines(warnings, file.path(destination,'warnings.txt'))
    writeLines(status, file.path(destination,'status.txt'))
  }
}
