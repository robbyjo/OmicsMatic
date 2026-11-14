# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Direct original calls: caller choices are explicit controls, not paper settings.
local({
 args <- commandArgs(TRUE);stopifnot(length(args) %in% c(6L,7L))
 library <- args[[1L]];input.path <- args[[2L]];fit.path <- args[[3L]]
 scenario <- args[[4L]];out <- args[[5L]];recorder <- args[[6L]]
 .libPaths(c(library,.libPaths()))
 conditions <- list();failure <- NULL;phase <- 'READINESS';value <- NULL;call <- NULL
 record <- function(c,kind) conditions[[length(conditions)+1L]] <<- list(kind=kind,object=c,class=class(c),message=conditionMessage(c),call=conditionCall(c),calls=sys.calls())
 error.before.unwind <- function(e) {
  saveRDS(list(error=e,calls=sys.calls()),file.path(out,'COMPLETE-ORIGINAL-ERROR-AND-CALLS.rds'),version=3)
  if (isNamespaceLoaded('reticulate')) {
   last <- tryCatch(reticulate::py_last_error(),error=function(error)error)
   saveRDS(last,file.path(out,'COMPLETE-PYTHON-LAST-ERROR.rds'),version=3)
   writeLines(capture.output(print(last)),file.path(out,'COMPLETE-PYTHON-LAST-ERROR.txt'))
  }
 }
 tryCatch(withCallingHandlers({
  ns <- loadNamespace('velociraptor',lib.loc=library)
  stopifnot(as.character(packageVersion('velociraptor',lib.loc=library))=='1.20.0',normalizePath(getNamespaceInfo(ns,'path'))==normalizePath(file.path(library,'velociraptor')))
  original <- readRDS(input.path);values <- original$list_input;sce <- original$SCE_input
  stopifnot(identical(dim(values$X),c(12L,160L)),identical(rownames(values$X)[1L],'NA'),identical(colnames(values$X)[1L],'NA'))
  curve <- SingleCellExperiment::reducedDim(sce,'caller_curve')
  parameters <- list(ncomponents=5L,BSPARAM=BiocSingular::ExactParam(),scvelo.params=list(neighbors=list(n_neighbors=15L,n_pcs=5L,random_state=882341L),velocity_graph=list(n_jobs=1L)))
  x <- values;export <- 'scvelo';expected.rows <- 12L;expected.columns <- 160L
  expected.rownames <- rownames(values$X);expected.colnames <- colnames(values$X)
  if (scenario=='default-mode-ANY') {
   # mode is intentionally omitted: exercise the actual public R default.
  } else if (scenario=='deterministic-SummarizedExperiment') {
   x <- SummarizedExperiment::SummarizedExperiment(assays=list(counts=values$X,spliced=values$spliced,unspliced=values$unspliced),colData=SummarizedExperiment::colData(sce))
   parameters$mode <- 'deterministic'
  } else if (scenario=='provided-embedding-subset-SCE') {
   x <- sce;parameters$mode <- 'stochastic';parameters$use.dimred <- 'caller_curve'
   parameters$subset.row <- c(1L,3L,4L,6L,7L,9L,10L,11L)
   parameters$scvelo.params$neighbors$n_pcs <- 2L
   expected.rows <- length(parameters$subset.row);expected.rownames <- expected.rownames[parameters$subset.row]
  } else if (scenario=='explicit-size-factors-ANY') {
   parameters$mode <- 'stochastic';parameters$dimred <- curve
   parameters$sf.X <- rep(1,160L);parameters$sf.spliced <- rep(1,160L);parameters$sf.unspliced <- rep(1,160L)
   parameters$scvelo.params$neighbors$n_pcs <- 2L
  } else if (scenario=='Python-preprocessing-dense-provided') {
   parameters$mode <- 'stochastic';parameters$use.theirs <- TRUE;parameters$dimred <- curve
   parameters$scvelo.params$neighbors$n_pcs <- 2L
   parameters$scvelo.params$filter_and_normalize <- list(min_shared_counts=0L,n_top_genes=NULL)
  } else if (scenario=='Python-preprocessing-sparse-no-embedding') {
   x <- lapply(values,function(v)Matrix::Matrix(v,sparse=TRUE));parameters$mode <- 'stochastic';parameters$use.theirs <- TRUE
   parameters$scvelo.params$filter_and_normalize <- list(min_shared_counts=0L,n_top_genes=NULL)
  } else if (scenario %in% c('gridVectors-ANY-intermediates','gridVectors-SCE-default')) {
   stopifnot(length(args)==7L)
   embedded <- readRDS(args[[7L]]);stopifnot(identical(dim(embedded),c(160L,2L)))
   export <- 'gridVectors';expected.rows <- expected.columns <- NULL
   expected.rownames <- expected.colnames <- NULL
   if (scenario=='gridVectors-ANY-intermediates') {
    x <- curve;parameters <- list(embedded=embedded,resolution=8L,scale=TRUE,as.data.frame=FALSE,return.intermediates=TRUE)
   } else {
    x <- sce;parameters <- list(embedded=embedded,use.dimred='caller_curve')
   }
  } else if (scenario %in% c('embedVelocity-ANY','embedVelocity-SCE','embedVelocity-default-PCA2-ANY')) {
   fit <- readRDS(fit.path);export <- 'embedVelocity'
   if (scenario=='embedVelocity-default-PCA2-ANY') {
    x <- SingleCellExperiment::reducedDim(fit,'X_pca')[,1:2,drop=FALSE]
    parameters <- list(vobj=fit)
   } else if (scenario=='embedVelocity-ANY') {
    x <- curve;parameters <- list(vobj=fit)
   } else {
    SingleCellExperiment::reducedDim(fit,'caller_curve') <- curve
    x <- fit;parameters <- list(vobj=fit,use.dimred='caller_curve')
   }
   expected.rows <- 160L;expected.columns <- 2L
   # The original projection matrix need not carry cell/gene dimension names.
   # Preserve its actual attributes; input ordering is separately retained.
   expected.rownames <- expected.colnames <- NULL
  } else stop('Unknown explicit original-control scenario: ',scenario)
  call <- list(scenario=scenario,export=export,arguments=c(list(x=x),parameters),seed=882341L,expected.rows=expected.rows,expected.columns=expected.columns,expected.rownames=expected.rownames,expected.colnames=expected.colnames,input.cell.order=colnames(values$X),input.gene.order=rownames(values$X),author.parameters.inferred=FALSE)
  saveRDS(call,file.path(out,'COMPLETE-ORIGINAL-CALL.rds'),version=3)
  set.seed(call$seed);spec <- get('velo.env',envir=ns,inherits=FALSE)
  basilisk::basiliskRun(env=spec,fun=function(seed) {reticulate::import('numpy')$random$seed(as.integer(seed));NULL},seed=call$seed)
  phase <- 'ORIGINAL_ANALYSIS'
  value <- do.call(getExportedValue('velociraptor',export),call$arguments)
  saveRDS(value,file.path(out,'COMPLETE-ORIGINAL-RESULT.rds'),version=3)
  saveRDS(call$arguments,file.path(out,'COMPLETE-ARGUMENTS-AFTER-CALL.rds'),version=3)
  phase <- 'RETURN_POPULATION_CHECK'
  if (scenario=='gridVectors-ANY-intermediates') {
   stopifnot(identical(names(value),c('start','end','limits','delta','categories','grp','vec')),length(value$grp)==160L,identical(dim(value$categories),c(160L,2L)),identical(dim(value$limits),c(2L,2L)),length(value$delta)==2L,nrow(value$start)==length(unique(value$grp)))
  } else if (scenario=='gridVectors-SCE-default') {
   stopifnot(is.data.frame(value),ncol(value)==4L,nrow(value)>0L)
  } else stopifnot(identical(dim(value),c(expected.rows,expected.columns)))
  if (export=='scvelo') stopifnot(identical(rownames(value),expected.rownames),identical(colnames(value),expected.colnames))
  phase <- 'COMPLETE_VALUE_CAPTURE';source(recorder,local=TRUE)
  captureRValues(value,file.path(out,'complete-value-tree'))
  phase <- 'COMPLETE'
 },warning=function(w)record(w,'warning'),message=function(m)record(m,'message'),error=error.before.unwind),error=function(e) {failure <<- e;record(e,'error')})
 saveRDS(list(phase=phase,error=failure,conditions=conditions,call=call),file.path(out,'COMPLETE-ORIGINAL-DIAGNOSTICS.rds'),version=3)
 if (!is.null(value)) saveRDS(value,file.path(out,'COMPLETE-AVAILABLE-RETURN.rds'),version=3)
 writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
 temporary <- tempdir();retained <- file.path(Sys.getenv('TMPDIR'),'retained-R-session-tempdir')
 temporary.retained <- !file.exists(retained) && isTRUE(file.rename(temporary,retained))
 status <- list(status=if(is.null(failure))'COMPLETE_DIRECT_ORIGINAL_ADDITIONAL_CASE' else 'DIRECT_ORIGINAL_CASE_UNADMITTED',scenario=scenario,phase=phase,original.return.available=!is.null(value),original.R.temporary.tree.retained=temporary.retained,
  warning.count=sum(vapply(conditions,function(c)c$kind=='warning',logical(1))),message.count=sum(vapply(conditions,function(c)c$kind=='message',logical(1))),error.count=sum(vapply(conditions,function(c)c$kind=='error',logical(1))),raw.console.diagnostic.counts.complete=FALSE,
  error=if(is.null(failure))NULL else list(class=class(failure),message=conditionMessage(failure),call=deparse(conditionCall(failure))),new.family.credit=0L,whole.goal.complete=FALSE)
 jsonlite::write_json(status,file.path(out,'REFERENCE-STATUS.json'),pretty=TRUE,auto_unbox=TRUE,null='null',na='string',digits=17)
 if (!is.null(failure)) {cat('DIRECT_ORIGINAL_ERROR: ',conditionMessage(failure),'\n',file=stderr(),sep='');quit(save='no',status=2L,runLast=FALSE)}
 cat(status$status,scenario,'\n');quit(save='no',status=0L,runLast=FALSE)
})
