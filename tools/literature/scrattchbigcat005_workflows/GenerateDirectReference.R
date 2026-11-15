# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent original calls for qualification, not the public Worker dispatch.
args <- commandArgs(TRUE);stopifnot(length(args)==1L)
control <- jsonlite::fromJSON(args[[1L]],simplifyVector=FALSE)
source(control$audit);source(control$request_helpers)
out <- control$output;events <- list();failure <- NULL;value <- NULL;returned <- FALSE;phase <- "READINESS"
rng <- function()list(kind=RNGkind(),seed_exists=exists(".Random.seed",globalenv(),inherits=FALSE),
  seed=if(exists(".Random.seed",globalenv(),inherits=FALSE))get(".Random.seed",globalenv())else NULL)
capture <- function(c,kind)events[[length(events)+1L]] <<-
  list(kind=kind,phase=phase,condition=c,class=class(c),message=conditionMessage(c),call=conditionCall(c))
invoke <- function(label,name,a) {
  before <- rng();conditions <- list();error <- NULL;done <- FALSE
  record <- function(c,kind)conditions[[length(conditions)+1L]] <<-
    list(kind=kind,condition=c,class=class(c),message=conditionMessage(c),call=conditionCall(c))
  answer <- tryCatch(withCallingHandlers({
    v <- do.call(getExportedValue("scrattch.bigcat",name),a);done <- TRUE;v
  },warning=function(c)record(c,"warning"),message=function(c)record(c,"message"),
    error=function(c)record(c,"error")),error=function(c){error <<- c;NULL})
  saveRDS(list(operation=name,arguments=a,returned=done,value=answer,error=error,conditions=conditions,
    RNG_before=before,RNG_after=rng()),file.path(out,"calls",paste0(label,".rds")),version=3)
  if(!is.null(error))stop(error)
  answer
}
tryCatch(withCallingHandlers({
  audit.bigcat(control,file.path(out,"before-original-source-audit.rds"))
  for(package in c("scrattch.bigcat","Matrix","matrixStats","data.table","arrow","ggplot2","foreach","doMC","dplyr"))
    library(package,character.only=TRUE)
  phase <- "READ_REQUEST";request <- bigcat.request(control)
  phase <- "ORIGINAL_PREPARATION";prepared <- bigcat.prepare(request,control)
  saveRDS(prepared,file.path(out,"complete-original-preparation.rds"),version=3)
  do.call(RNGkind,as.list(request$rng$kind));assign(".Random.seed",request$rng$seed,envir=globalenv())
  arrow::set_cpu_count(request$arrow_cpu_count);before <- rng();phase <- "ORIGINAL_ANALYSIS"
  a <- prepared$arguments;dir.create(file.path(out,"calls"))
  if(request$operation=="centroid_cross_validation") {
    # The reference writes only direct original returns. It does not call the project CV helper.
    dir.create(file.path(control$workspace,"cross-validation"));answer <- list()
    for(fold in sort(unique(a$fold_assignments))) {
      fi <- match(fold,sort(unique(a$fold_assignments)))
      train <- names(a$fold_assignments)[a$fold_assignments!=fold]
      test <- names(a$fold_assignments)[a$fold_assignments==fold]
      statistics <- invoke(sprintf("fold-%04d-centroids",fi),"get_cl_stats_parquet",
        list(big.dat.parquet=a$big.dat,cl=a$cl[train],mc.cores=a$mc.cores,stats="means",
          genes.allowed=unique(unlist(a$marker_sets,use.names=FALSE)),return.matrix=TRUE))
      panels <- list()
      for(panel in names(a$marker_sets)) {
        pi <- match(panel,names(a$marker_sets));means <- statistics$means[a$marker_sets[[panel]],,drop=FALSE]
        index <- invoke(sprintf("fold-%04d-set-%04d-index",fi,pi),"build_train_index",
          list(cl.dat=means,method=a$method,fn=file.path(control$workspace,"cross-validation",sprintf("fold-%04d-set-%04d.idx",fi,pi))))
        mapping <- invoke(sprintf("fold-%04d-set-%04d-mapping",fi,pi),"map_cells_knn_big",
          list(big.dat=a$big.dat,cl.dat=means,select.cells=test,train.index=index,method=a$method,
            block.size=a$block.size,mc.cores=a$mc.cores,clear.index=FALSE))
        panels[[panel]] <- list(index=index,mapping=mapping)
      }
      answer[[as.character(fold)]] <- list(statistics=statistics,panels=panels)
    }
    value <- answer
  } else if(request$operation=="constellation_from_embedding") {
    dir.create(file.path(control$workspace,"constellation"))
    graph <- invoke("graph","get_knn_graph",a$graph_arguments)
    e <- graph$knn.cl.df
    selected <- e[which(e$cl.from==e$cl.to|e$frac>a$outside_fraction_threshold),,drop=FALSE]
    plot <- invoke("plot","plot_constellation",c(list(knn.cl.df=selected),a$plot_arguments))
    value <- list(graph=graph,selected=selected,plot=plot)
  } else value <- do.call(getExportedValue("scrattch.bigcat",request$operation),prepared$arguments)
  returned <- TRUE;after <- rng();phase <- "COMPLETE_ORIGINAL_RETURN_RECORDING"
  saveRDS(value,file.path(out,"complete-result.rds"),version=3)
  saveRDS(list(before=before,after=after),file.path(out,"complete-rng-before-after.rds"),version=3)
  if(request$operation=="constellation_from_embedding") {
    index.path <- methods::slot(value$graph$knn.result$index,"path")
    saved.path <- file.path(out,"independent-original-annoy.idx")
    stopifnot(file.copy(index.path,saved.path,overwrite=FALSE))
    saveRDS(list(original_path=index.path,retained_copy=saved.path,
      original_sha256=digest::digest(file=index.path,algo="sha256"),
      retained_sha256=digest::digest(file=saved.path,algo="sha256")),
      file.path(out,"complete-independent-original-index-copy.rds"),version=3)
  }
  if(!is.null(request$post_selection))saveRDS(bigcat.post.selection(value,prepared$arguments,request$post_selection),
    file.path(out,"complete-post-selection.rds"),version=3)
  phase <- "POST_ANALYSIS_SOURCE_AUDIT";audit.bigcat(control,file.path(out,"after-original-source-audit.rds"))
  stopifnot(identical(after,rng()));phase <- "COMPLETE"
},warning=function(c)capture(c,"warning"),message=function(c)capture(c,"message"),error=function(c)capture(c,"error")),
error=function(c){failure <<- c})
saveRDS(list(phase=phase,conditions=events,error=failure),file.path(out,"complete-conditions.rds"),version=3)
writeLines(capture.output(sessionInfo()),file.path(out,"original-session-info.txt"))
jsonlite::write_json(list(status=if(is.null(failure))"ORIGINAL_REFERENCE_COMPLETE"else"ORIGINAL_REFERENCE_FAILED_RETAINED",
  returned=returned,phase=phase,error=if(is.null(failure))NULL else conditionMessage(failure)),
  file.path(out,"REFERENCE-STATUS.json"),pretty=TRUE,auto_unbox=TRUE,null="null")
if(!is.null(failure))quit(save="no",status=2L,runLast=FALSE)
