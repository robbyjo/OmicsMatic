# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Execute unchanged original S4 class and modeling function definitions. This is
# a function-boundary reference, not installation or a full CellChat workflow.
args <- commandArgs(TRUE)
input <- scan(args[1],what=character(),quiet=TRUE)
sourceRoot <- args[2]; label <- args[3]; out <- args[4]
.libPaths(c(args[-seq_len(4L)],.libPaths()))
options(warn=1,digits=17)
suppressPackageStartupMessages(library(Matrix))
suppressPackageStartupMessages(library(dplyr))
future::plan(future::sequential)
original <- new.env(parent=globalenv())
for (statement in parse(file=file.path(sourceRoot,'R/CellChat_class.R'),keep.source=TRUE)) {
  if (!is.call(statement)) next
  if (identical(statement[[1]],as.name('setClassUnion')) ||
      (identical(statement[[1]],as.name('<-')) && identical(statement[[2]],as.name('CellChat'))))
    eval(statement,envir=original)
}
definitions <- character()
for (statement in parse(file=file.path(sourceRoot,'R/modeling.R'),keep.source=TRUE)) {
  if (is.call(statement) && identical(statement[[1]],as.name('<-')) &&
      is.symbol(statement[[2]]) && is.call(statement[[3]]) &&
      identical(statement[[3]][[1]],as.name('function'))) {
    eval(statement,envir=original); definitions <- c(definitions,as.character(statement[[2]]))
  }
}
dir.create(out,recursive=TRUE)
writeLines(definitions,file.path(out,'original-function-names.txt'))
for (name in definitions) dput(get(name,envir=original),file=file.path(out,paste0('loaded-',name,'.R')))
at <- 1L
nextToken <- function() { value <- input[at]; at <<- at+1L; value }
nextNumber <- function() as.numeric(nextToken())
takeNumbers <- function(n) { result <- as.numeric(input[seq.int(at,length.out=n)]); at <<- at+n; result }
readResource <- function(n,prefix) {
  entries <- list()
  for (i in seq_len(n)) {
    name <- nextToken(); count <- nextNumber()
    entries[[name]] <- vapply(seq_len(count),function(j) nextToken(),character(1))
  }
  width <- max(2L,lengths(entries))
  value <- matrix('',nrow=n,ncol=width,dimnames=list(names(entries),paste0(prefix,seq_len(width))))
  for (i in seq_len(n)) value[i,seq_along(entries[[i]])] <- entries[[i]]
  as.data.frame(value,stringsAsFactors=FALSE)
}
none <- function(x) if(x=='-') '' else x
records <- list()
while(at<=length(input)) {
  id <- nextToken(); caseLabel <- nextToken(); profile <- nextToken()
  nr <- nextNumber(); nc <- nextNumber(); ng <- nextNumber(); average <- nextToken()
  trim <- nextNumber(); kh <- nextNumber(); hill <- nextNumber(); population <- nextNumber()==1
  ni <- nextNumber(); nx <- nextNumber(); nf <- nextNumber(); nb <- nextNumber(); seed <- nextNumber()
  genes <- character(nr); data <- matrix(0,nrow=nr,ncol=nc)
  for (i in seq_len(nr)) { genes[i] <- nextToken(); data[i,] <- takeNumbers(nc) }
  groupIndices <- takeNumbers(nc)
  pairs <- matrix('',nrow=ni,ncol=7L)
  for (i in seq_len(ni)) pairs[i,] <- vapply(seq_len(7L),function(j) nextToken(),character(1))
  pair <- as.data.frame(pairs,stringsAsFactors=FALSE)
  colnames(pair) <- c('interaction_name','ligand','receptor','agonist','antagonist','co_A_receptor','co_I_receptor')
  for (column in 4:7) pair[[column]] <- vapply(pair[[column]],none,character(1))
  rownames(pair) <- pair$interaction_name
  pair$annotation <- 'Secreted Signaling'; pair$pathway_name <- rep(c('PATH_A','PATH_B'),length.out=ni)
  complex <- readResource(nx,'subunit'); cofactor <- readResource(nf,'cofactor')
  if(caseLabel!=label) next
  rownames(data) <- genes; colnames(data) <- paste0('cell',seq_len(nc)-1L)
  group <- factor(paste0('G',groupIndices),levels=paste0('G',seq_len(ng)-1L))
  object <- original$CellChat()
  object@data.signaling <- data; object@idents <- group
  object@DB <- list(complex=complex,cofactor=cofactor)
  object@LR <- list(LRsig=pair); object@options <- list(datatype='RNA',mode='single')
  home <- file.path(out,id); dir.create(home)
  set.seed(seed); permutation <- replicate(nb,sample.int(nc,size=nc)); expectedRng <- .Random.seed
  write.table(t(permutation-1L),file.path(home,'permutations.tsv'),sep='\t',quote=FALSE,row.names=FALSE,col.names=FALSE)
  method <- switch(average,TRI_MEAN='triMean',MEDIAN='median',TRUNCATED_MEAN='truncatedMean',THRESHOLDED_MEAN='thresholdedMean')
  parameters <- list(object=object,type=method,trim=trim,raw.use=TRUE,population.size=population,nboot=nb,seed.use=as.integer(seed),Kh=kh,n=hill)
  if(grepl('CELLCHAT_1_1_3',profile)) parameters$do.fast <- grepl('FAST$',profile)
  old <- serialize(object,NULL)
  result <- do.call(original$computeCommunProb,parameters)
  stopifnot(identical(old,serialize(object,NULL)),identical(.Random.seed,expectedRng))
  metrics <- expand.grid(target=seq_len(ng)-1L,source=seq_len(ng)-1L,interaction=seq_len(ni)-1L)
  # Explicit row indexing avoids relying on R-versus-Java flattening conventions.
  metrics <- metrics[order(metrics$interaction,metrics$source,metrics$target),c('interaction','source','target')]
  ix <- cbind(metrics$source+1L,metrics$target+1L,metrics$interaction+1L)
  metrics$probability <- result@net$prob[ix]; metrics$pvalue <- result@net$pval[ix]
  stopifnot(all(is.finite(metrics$probability)),all(is.finite(metrics$pvalue)))
  write.table(metrics,file.path(home,'metrics.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
  records[[id]] <- list(profile=profile,cells=nc,genes=nr,groups=ng,interactions=ni,nboot=nb,seed=seed,
    original_rng_end_state_matches_independent_sample_int=TRUE,original_input_object_unchanged=TRUE)
  cat('PASS original CellChat function boundary',label,id,'\n')
}
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
jsonlite::write_json(list(status='PASS_ORIGINAL_CELLCHAT_RNA_FUNCTION_BOUNDARY_ONLY',label=label,cases=records,
  package_installed=FALSE,full_workflow_available=FALSE,historical_paper_runtime=FALSE),file.path(out,'COMPLETED.json'),auto_unbox=TRUE,pretty=TRUE)
