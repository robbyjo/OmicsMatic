# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Full original records remain intact; every comparison difference is retained.
args <- commandArgs(TRUE);stopifnot(length(args)>=2L,length(args)<=4L)
library(Matrix);library(bigstatsr)
case <- args[[1L]];inputs <- args[[2L]]
out <- if(length(args)==4L)args[[4L]]else file.path(case,"comparison")
stopifnot(!file.exists(out));dir.create(out)
reference <- file.path(case,"reference");adapter <- file.path(case,"adapter/original")
request <- readRDS(if(length(args)>=3L)args[[3L]]else jsonlite::fromJSON(file.path(reference,"control.json"))$request)
checks <- list();comparison.errors <- list()
compare <- function(label,left,right,tolerance=0) {
  difference <- tryCatch(all.equal(left,right,tolerance=tolerance,check.attributes=TRUE),
    error=function(c) {
      comparison.errors[[label]] <<- c
      paste("COMPARISON_ERROR_RETAINED:",conditionMessage(c))
    })
  checks[[label]] <<- list(identical=identical(left,right),all_equal=isTRUE(difference),
    tolerance=tolerance,complete_all_equal_return=difference)
  invisible(isTRUE(difference))
}
compare.plots <- function(rp,ap,prefix) {
  compare(paste0(prefix,"/member_names"),names(rp),names(ap))
  seed.exists <- exists(".Random.seed",globalenv(),inherits=FALSE)
  old.seed <- if(seed.exists)get(".Random.seed",globalenv())else NULL
  old.kind <- RNGkind()
  on.exit({do.call(RNGkind,as.list(old.kind));if(seed.exists)assign(".Random.seed",old.seed,globalenv())
    else if(exists(".Random.seed",globalenv(),inherits=FALSE))rm(".Random.seed",envir=globalenv())},add=TRUE)
  for(field in names(rp)) {
    compare(paste0(prefix,"/member/",field),rp[[field]],ap[[field]])
    if(inherits(rp[[field]],"ggplot")) {
      rb <- ggplot2::ggplot_build(rp[[field]]);ab <- ggplot2::ggplot_build(ap[[field]])
      saveRDS(list(reference=rb,adapter=ab),file.path(out,paste0("complete-build-",field,".rds")),version=3)
      compare(paste0(prefix,"/layer_data/",field),rb$data,ab$data)
      compare(paste0(prefix,"/panel_parameters/",field),rb$layout$panel_params,ab$layout$panel_params)
      stopifnot(capabilities("cairo"))
      hashes <- character()
      for(side in c("reference","adapter")) {
        do.call(RNGkind,as.list(request$rng$kind));assign(".Random.seed",request$rng$seed,globalenv())
        token <- paste0(gsub("[^A-Za-z0-9_-]","_",prefix),"-",match(field,names(rp)))
        filename <- file.path(out,paste0("derived-",token,"-",side,".png"))
        ggplot2::ggsave(filename,plot=if(side=="reference")rp[[field]]else ap[[field]],
          device=grDevices::png,type="cairo",width=25,height=25,units="cm",dpi=110,bg="white")
        hashes[[side]] <- digest::digest(file=filename,algo="sha256")
      }
      compare(paste0(prefix,"/complete_derived_PNG_SHA256/",field),unname(hashes[["reference"]]),unname(hashes[["adapter"]]))
    }
  }
}
rstatus <- jsonlite::fromJSON(file.path(reference,"REFERENCE-STATUS.json"))
astatus <- jsonlite::fromJSON(file.path(adapter,"R-status.json"))
compare("original_returned",rstatus$returned,astatus$original_function_returned)
rc <- readRDS(file.path(reference,"complete-conditions.rds"))
ac <- readRDS(file.path(adapter,"complete-conditions.rds"))
compare("complete_error",rc$error,ac$error)
compare("complete_R_conditions",rc$conditions,ac$conditions)
if(rstatus$returned&&astatus$original_function_returned) {
  rv <- readRDS(file.path(reference,"complete-result.rds"));av <- readRDS(file.path(adapter,"complete-result.rds"))
  compare("complete_RNG",readRDS(file.path(reference,"complete-rng-before-after.rds")),
    readRDS(file.path(adapter,"complete-rng-before-after.rds")))
  if(request$operation=="centroid_cross_validation") {
    calls <- list.files(file.path(reference,"calls"),pattern="[.]rds$",full.names=TRUE)
    for(path in calls) {
      label <- sub("[.]rds$","",basename(path));r <- readRDS(path)
      a <- readRDS(file.path(case,"workspace/cross-validation",label,"complete-original-call.rds"))
      for(field in c("operation","arguments","returned","value","error","conditions"))
        compare(paste("original_call",label,field,sep="/"),r[[field]],a[[field]])
      for(when in c("RNG_before","RNG_after"))for(field in c("kind","seed"))
        compare(paste("original_call",label,when,field,sep="/"),r[[when]][[field]],a[[when]][[field]])
    }
    # Independent ordinary dense rowMeans, not a BIGCAT summary or the adapter helper.
    source <- list(norm.dat=request$preparation$expression$arguments$mat,
      truth=request$arguments$cl,folds=request$arguments$fold_assignments)
    matrix <- as.matrix(source$norm.dat);largest <- 0;training.sizes <- integer()
    for(fold in names(av$folds)) {
      result <- av$folds[[fold]];train <- names(source$folds)[source$folds!=as.integer(fold)]
      compare(paste0("assembled/",fold,"/statistics"),rv[[fold]]$statistics,result$original_statistics)
      compare(paste0("assembled/",fold,"/centroids"),rv[[fold]]$statistics$means,result$centroids)
      test <- names(source$folds)[source$folds==as.integer(fold)]
      compare(paste0("CPU/fold-",fold,"/training_ids"),train,result$train_cells)
      compare(paste0("CPU/fold-",fold,"/heldout_ids"),test,result$query_cells)
      expected <- vapply(colnames(result$centroids),function(cluster) {
        ids <- train[as.character(source$truth[train])==cluster]
        training.sizes <<- c(training.sizes,length(ids))
        rowMeans(matrix[rownames(result$centroids),ids,drop=FALSE])
      },numeric(nrow(result$centroids)))
      dimnames(expected) <- dimnames(result$centroids)
      largest <- max(largest,max(abs(result$centroids-expected)))
      compare(paste0("CPU/fold-",fold,"/all_training_centroids"),expected,result$centroids,tolerance=1e-12)
      for(panel in names(result$panels)) {
        item <- result$panels[[panel]];mapping <- item$mapping
        compare(paste("assembled",fold,panel,"index",sep="/"),rv[[fold]]$panels[[panel]]$index,item$index)
        compare(paste("assembled",fold,panel,"complete_mapping",sep="/"),rv[[fold]]$panels[[panel]]$mapping,mapping)
        compare(paste("CPU",fold,panel,"complete_queries",sep="/"),sort(as.character(mapping$sample_id)),sort(test))
        correct <- as.character(mapping$cl)==as.character(source$truth[as.character(mapping$sample_id)])
        compare(paste("CPU",fold,panel,"correctness",sep="/"),correct,item$correct)
        compare(paste("CPU",fold,panel,"accuracy",sep="/"),mean(correct),item$accuracy)
      }
    }
    checks$CPU_centroid_details <- list(maximum_absolute_difference=largest,training_cells_per_centroid=training.sizes)
  } else if(request$operation=="constellation_from_embedding") {
    compare("complete_original_graph",rv$graph,av$original_graph)
    compare("complete_selected_edges",rv$selected,av$selected_edges)
    compare("complete_original_plot",rv$plot,av$original_plot)
    for(field in c("knn.index","knn.distance"))compare(paste0("graph_array/",field),
      rv$graph$knn.result[[field]],av$original_graph$knn.result[[field]])
    compare("complete_cluster_graph",rv$graph$knn.cl.df,av$original_graph$knn.cl.df)
    compare("complete_outlier_ids",rv$graph$outlier,av$original_graph$outlier)
    for(field in slotNames(rv$graph$knn.result$index))compare(paste0("complete_index_slot/",field),
      slot(rv$graph$knn.result$index,field),slot(av$original_graph$knn.result$index,field))
    compare.plots(rv$plot,av$original_plot,"complete_plot")
    links <- readRDS(file.path(adapter,"complete-original-asset-links.rds"))
    original.path <- slot(av$original_graph$knn.result$index,"path")
    link <- Filter(function(link)identical(link$original_path,original.path),links)
    stopifnot(length(link)==1L)
    compare("complete_copied_Annoy_payload_SHA256",digest::digest(file=file.path(reference,"independent-original-annoy.idx"),algo="sha256"),
      digest::digest(file=file.path(adapter,"original-return-assets",basename(link[[1L]]$retained_copy)),algo="sha256"))
    for(name in c("graph","plot")) {
      r <- readRDS(file.path(reference,"calls",paste0(name,".rds")))
      a <- readRDS(file.path(case,"workspace/constellation",name,"complete-original-call.rds"))
      for(field in c("operation","arguments","returned","value","error","conditions"))
        compare(paste("original_call",name,field,sep="/"),r[[field]],a[[field]])
      if(name=="graph")compare("assembled/complete_original_graph",a$value,av$original_graph)
      if(name=="plot")compare.plots(r$value,a$value,"original_plot_call")
    }
  } else {
    compare("complete_original_return",rv,av)
    if(request$operation=="plot_constellation")compare.plots(rv,av,"complete_plot")
    if(request$operation=="impute_cross_big") {
      compare("complete_return_member_names",names(rv),names(av))
      for(field in names(rv))if(field!="fbm")compare(paste0("complete_original_field/",field),rv[[field]],av[[field]])
      compare("complete_FBM_class_definition",rv$fbm$.refClassDef,av$fbm$.refClassDef)
      metadata <- function(fbm)list(nrow=fbm$nrow,ncol=fbm$ncol,type=fbm$type,
        backingfile=fbm$backingfile,is_read_only=fbm$is_read_only)
      compare("complete_FBM_metadata",metadata(rv$fbm),metadata(av$fbm))
      rb <- list.files(file.path(case,"reference-workspace"),pattern="[.]bk$",recursive=TRUE,full.names=TRUE)
      ab <- list.files(file.path(case,"workspace"),pattern="[.]bk$",recursive=TRUE,full.names=TRUE)
      stopifnot(length(rb)==1L,length(ab)==1L)
      compare("complete_FBM_payload_SHA256",digest::digest(file=rb,algo="sha256"),digest::digest(file=ab,algo="sha256"))
    }
  }
  if(file.exists(file.path(reference,"complete-post-selection.rds")))compare("complete_post_selection",
    readRDS(file.path(reference,"complete-post-selection.rds")),readRDS(file.path(adapter,"complete-post-selection.rds")))
}
saveRDS(list(checks=checks,complete_comparison_errors=comparison.errors),file.path(out,"complete-comparisons.rds"),version=3)
comparable <- Filter(function(x)!is.null(x$all_equal),checks)
failed <- names(comparable)[!vapply(comparable,function(x)x$all_equal,logical(1))]
jsonlite::write_json(list(status=if(length(failed))"DIFFERENCES_REQUIRE_REVIEW"else"PASS_RECORDED_COMPARISONS",
  checks=checks,comparison_count=length(comparable),different_checks=failed,
  final_workflow_qualification_established=FALSE,new_family_credit=0),file.path(out,"COMPARISON.json"),
  pretty=TRUE,auto_unbox=TRUE,null="null",na="string",digits=17)
cat("COMPARISONS",length(comparable),"DIFFERENT",length(failed),"\n")
