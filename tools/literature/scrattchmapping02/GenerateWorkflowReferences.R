# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Prepare independent synthetic assets using original builders. Analysis is
# captured by DirectWorkflow.R in a NEW process for each prepared request.
args <- commandArgs(TRUE);stopifnot(length(args)==1L)
out <- args[[1L]];stopifnot(dir.exists(out),!length(list.files(out,all.files=TRUE,no..=TRUE)))
options(warn=1)
library(scrattch.mapping)
stopifnot(normalizePath(reticulate::py_config()$python)==normalizePath(Sys.getenv("RETICULATE_PYTHON")))
original <- function(name)getFromNamespace(name,"scrattch.mapping")
for(name in c("assets","requests","references"))dir.create(file.path(out,name))
set.seed(20261117)
genes <- paste0("g",1:24);clusters <- paste0("c",1:8)
reference <- matrix(runif(192,.1,5),24,8,dimnames=list(genes,clusters))
reference[1:6,1:4] <- reference[1:6,1:4]+8
reference[7:12,5:8] <- reference[7:12,5:8]+8
query <- reference[,rep(1:8,each=2)]+matrix(runif(384,0,.2),24,16)
colnames(query) <- c("NA",paste0("cell",2:16))
annotations <- data.frame(cl=clusters,group=rep(c("A","B"),each=4),
  Level1_label=rep(c("A","B"),each=4),Level2_label=rep(paste0("S",1:4),each=2),
  cluster_label=paste0("type",1:8),stringsAsFactors=FALSE)
saveRDS(list(reference=reference,query=query,annotations=annotations),file.path(out,"assets/complete-synthetic-inputs.rds"),version=3)
requests <- list()
for(depth in 1:3) {
  directory <- file.path(out,"assets",paste0("depth_",depth));dir.create(directory)
  ancestor <- original("look_up_ancestor")(annotations,nlvl=depth)
  children <- original("look_up_children")(annotations,nlvl=depth)
  marker_index <- list()
  for(level in seq_len(depth)) {
    paths <- apply(as.matrix(ancestor[,seq_len(level),drop=FALSE]),1,paste,collapse=":")
    for(node in unique(paths)) {
      ids <- as.character(ancestor[paths==node,ncol(ancestor)])
      indices <- original("build_train_index_bs")(reference[,ids,drop=FALSE],method="cor",
        sample.markers.prop=.75,iter=3,mc.cores=1,fn=file.path(directory,paste0("node_",length(marker_index)+1L)))
      marker_index[[node]] <- list(index_tree=indices,marker_tree=genes)
    }
  }
  marker_index_fn <- file.path(directory,"marker_index.rda");save(marker_index,file=marker_index_fn,version=3)
  train.list <- list(cl.df=annotations,cl.dat=reference,ancestor=ancestor,children=children,
    all.markers=genes,nlvl=depth+1L,MI_FN=marker_index_fn,
    ancestor_markers=setNames(rep(list(genes),length(clusters)),clusters))
  train_fn <- file.path(directory,"train.list.rda");save(train.list,file=train_fn,version=3)
  arguments <- list(query.dat=query,Taxonomy=NA_character_,TaxFN=train_fn,mapping.method="hierarchy",
    nlevel=depth+1L,iter=3,mc.cores=1,blocksize=8,dist.method="cor",topk=1,
    flag.serial=TRUE,flag.parallel.tmp=FALSE,flag.fuzzy=FALSE)
  requests[[paste0("depth_",depth)]] <- list(operation="run_mapping_on_taxonomy",arguments=arguments,
    rng=list(kind=RNGkind(),seed=.Random.seed))
}
base <- requests$depth_3
requests$sparse <- base
requests$sparse$arguments$query.dat <- as(Matrix::Matrix(query,sparse=TRUE),"dgCMatrix")
requests$one_iteration_topk3 <- base
requests$one_iteration_topk3$arguments$iter <- 1
requests$one_iteration_topk3$arguments$topk <- 3
requests$clipped_iterations <- base
requests$clipped_iterations$arguments$iter <- 7
requests$duplicate_cells <- base
colnames(requests$duplicate_cells$arguments$query.dat)[2L] <- "NA"
requests$single_cell <- base
requests$single_cell$arguments$query.dat <- query[,1L,drop=FALSE]
requests$missing_marker <- base
requests$missing_marker$arguments$query.dat <- query[-1L,,drop=FALSE]
requests$no_cells <- base
requests$no_cells$arguments$query.dat <- query[,integer(),drop=FALSE]
requests$zero_expression <- base
requests$zero_expression$arguments$query.dat[,] <- 0
summary <- list()
for(i in seq_along(requests)) {
  name <- names(requests)[[i]];request <- requests[[i]]
  saveRDS(request,file.path(out,"requests",paste0(name,".rds")),version=3)
  summary[[name]] <- list(request=file.path(out,"requests",paste0(name,".rds")))
}
jsonlite::write_json(list(status="ORIGINAL_ASSETS_PREPARED_NO_ANALYSIS_CAPTURED",cases=summary,
  controls=length(requests),new_family_credit=0L,whole_goal_complete=FALSE),
  file.path(out,"PREPARED-REQUESTS.json"),pretty=TRUE,auto_unbox=TRUE,null="null")
cat("ORIGINAL_ASSETS_PREPARED",length(requests),"\n")
