# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Caller-level fivefold/panel workflow, using unchanged original BIGCAT functions.
bigcat.cross.validate <- function(arguments,control) {
  required <- c("big.dat","cl","fold_assignments","marker_sets","method","block.size","mc.cores")
  stopifnot(bigcat.named(arguments),setequal(names(arguments),required),
    identical(arguments$big.dat$type,"parquet"),!is.null(names(arguments$cl)),
    !anyNA(arguments$cl),!anyNA(names(arguments$cl)),!anyDuplicated(names(arguments$cl)),
    is.integer(arguments$fold_assignments),!is.null(names(arguments$fold_assignments)),
    !anyNA(arguments$fold_assignments),!anyDuplicated(names(arguments$fold_assignments)),
    setequal(names(arguments$fold_assignments),names(arguments$cl)),
    all(names(arguments$cl) %in% arguments$big.dat$col_id),
    bigcat.named(arguments$marker_sets),length(arguments$marker_sets)>0L,
    bigcat.scalar(arguments$method),arguments$method %in% c("Annoy.Cosine","Annoy.Euclidean","cor"))
  for(genes in arguments$marker_sets)stopifnot(is.character(genes),length(genes)>0L,!anyNA(genes))
  all.genes <- unique(unlist(arguments$marker_sets,use.names=FALSE))
  stopifnot(all(all.genes %in% arguments$big.dat$row_id))
  root <- file.path(control$workspace,"cross-validation")
  original.call <- bigcat.original.recorder(root)
  folds <- sort(unique(arguments$fold_assignments));stopifnot(length(folds)>1L)
  result <- list()
  for(fi in seq_along(folds)) {
    fold <- folds[[fi]]
    query.cells <- names(arguments$fold_assignments)[arguments$fold_assignments==fold]
    train.cells <- names(arguments$fold_assignments)[arguments$fold_assignments!=fold]
    train.cl <- arguments$cl[train.cells]
    stopifnot(length(train.cells)>0L,length(query.cells)>0L,!anyNA(train.cl))
    # Direct Parquet summaries include every training cell, with no cluster-size sampling cap.
    statistics <- original.call(sprintf("fold-%04d-centroids",fi),"get_cl_stats_parquet",
      list(big.dat.parquet=arguments$big.dat,cl=train.cl,mc.cores=arguments$mc.cores,
        stats="means",genes.allowed=all.genes,return.matrix=TRUE))
    centroids <- statistics$means
    stopifnot(is.matrix(centroids),setequal(colnames(centroids),as.character(unique(train.cl))))
    panels <- list()
    for(pi in seq_along(arguments$marker_sets)) {
      panel <- names(arguments$marker_sets)[[pi]];genes <- arguments$marker_sets[[pi]]
      cl.dat <- centroids[genes,,drop=FALSE]
      index.path <- file.path(root,sprintf("fold-%04d-set-%04d.idx",fi,pi))
      index <- original.call(sprintf("fold-%04d-set-%04d-index",fi,pi),"build_train_index",
        list(cl.dat=cl.dat,method=arguments$method,fn=index.path))
      mapping <- original.call(sprintf("fold-%04d-set-%04d-mapping",fi,pi),"map_cells_knn_big",
        list(big.dat=arguments$big.dat,cl.dat=cl.dat,select.cells=query.cells,train.index=index,
          method=arguments$method,block.size=arguments$block.size,mc.cores=arguments$mc.cores,clear.index=FALSE))
      truth <- arguments$cl[as.character(mapping$sample_id)]
      correct <- as.character(mapping$cl)==as.character(truth)
      missing <- setdiff(query.cells,as.character(mapping$sample_id))
      unexpected <- setdiff(as.character(mapping$sample_id),query.cells)
      duplicates <- as.character(mapping$sample_id[duplicated(mapping$sample_id)])
      complete <- !anyNA(mapping$sample_id)&&!length(missing)&&!length(unexpected)&&
        !length(duplicates)&&nrow(mapping)==length(query.cells)
      panels[[panel]] <- list(genes=genes,index=index,mapping=mapping,truth=truth,correct=correct,
        accuracy=if(!complete||anyNA(correct))NA_real_ else mean(correct),unknown=sum(is.na(correct)),
        expected=length(query.cells),tested=nrow(mapping),complete_query_coverage=complete,
        missing_query_ids=missing,unexpected_query_ids=unexpected,duplicate_query_ids=duplicates,
        source_distance_values_unchanged=TRUE)
      saveRDS(list(completed_folds=result,current_fold=fold,completed_panels=names(panels)),
        file.path(root,"partial-workflow-progress.rds"),version=3)
    }
    result[[as.character(fold)]] <- list(fold=fold,train_cells=train.cells,query_cells=query.cells,
      original_statistics=statistics,centroids=centroids,panels=panels)
  }
  list(folds=result,marker_sets=arguments$marker_sets,fold_assignments=arguments$fold_assignments,
    caller_assignments_and_marker_lists_preserved=TRUE,all_training_cells_used_for_centroids=TRUE,
    package_functions=c("get_cl_stats_parquet","build_train_index","map_cells_knn_big"),
    author_fold_assignments_or_random_panels_inferred=FALSE,live_index_persistence_qualified=FALSE)
}
