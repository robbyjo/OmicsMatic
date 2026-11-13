# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original source control, not the production adapter or a study reproduction.
args <- commandArgs(trailingOnly=TRUE);stopifnot(length(args)==5L)
out <- args[1];lib <- args[2];base <- args[3];source_m3 <- args[4];source_b <- args[5]
.libPaths(c(lib,base),include.site=FALSE)
stage <- "namespace";warnings_seen <- character();messages_seen <- character()
write_json <- function(x,p)jsonlite::write_json(x,p,pretty=TRUE,auto_unbox=TRUE,digits=NA,na="string",null="null")
tokens <- function(x)as.list(vapply(as.numeric(x),function(v)if(is.nan(v))"NaN"else if(is.na(v))"NA"else if(is.infinite(v))if(v>0)"Inf"else"-Inf"else sprintf("%.17g",v),character(1)))
rows <- function(x)lapply(seq_len(nrow(x)),function(i)as.list(unname(x[i,])))
same_state <- function(a,b,path){
 if(inherits(a,"igraph")||inherits(b,"igraph")){
  stopifnot(inherits(a,"igraph"),inherits(b,"igraph"),identical(igraph::is_directed(a),igraph::is_directed(b)),identical(igraph::graph_attr(a),igraph::graph_attr(b)),identical(igraph::as_data_frame(a,what="both"),igraph::as_data_frame(b,what="both")))
 }else if(isS4(a)&&isS4(b)){
  stopifnot(identical(class(a),class(b)),identical(slotNames(a),slotNames(b)));for(n in slotNames(a))same_state(slot(a,n),slot(b,n),paste0(path,"@",n))
 }else if(is.list(a)&&is.list(b)){
  stopifnot(identical(attributes(a),attributes(b)),length(a)==length(b));for(i in seq_along(a))same_state(a[[i]],b[[i]],paste0(path,"/",if(is.null(names(a)))i else names(a)[i]))
 }else if(!identical(a,b))stop("Saved scientific state differs at ",path)
 invisible(TRUE)
}
guard <- function(package,source,names){
 ns <- asNamespace(package);definitions <- list()
 for(file in list.files(file.path(source,"R"),pattern="[.]R$",full.names=TRUE))for(expr in parse(file,keep.source=FALSE))if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]]) %in% c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))definitions[[as.character(expr[[2]])]] <- expr[[3]]
 for(name in names){stopifnot(!is.null(definitions[[name]]),exists(name,ns,inherits=FALSE));original <- eval(definitions[[name]],envir=ns);loaded <- get(name,ns,inherits=FALSE);stopifnot(identical(body(original),body(loaded)),identical(formals(original),formals(loaded)))}
 as.list(names)
}
tryCatch(withCallingHandlers({
 stopifnot(as.character(getRversion())=="4.3.3",R.version$platform=="x86_64-conda-linux-gnu")
 suppressPackageStartupMessages(library(monocle3));stopifnot(as.character(getNamespaceVersion("monocle3"))=="1.3.7",as.character(getNamespaceVersion("batchelor"))=="1.20.0")
 guards <- list(monocle3=guard("monocle3",source_m3,c("new_cell_data_set","estimate_size_factors","preprocess_cds","align_cds","reduce_dimension","cluster_cells","learn_graph","multi_component_RGE","project2MST","order_cells","find_nearest_vertex","extract_general_graph_ordering")),batchelor=guard("batchelor",source_b,c("fastMNN","reducedMNN","multiBatchNorm")))
 RhpcBLASctl::blas_set_num_threads(1L);RhpcBLASctl::omp_set_num_threads(1L);options(mc.cores=1L)
 stage <- "complete synthetic inputs";set.seed(251051);genes <- paste0("G",seq_len(64));ids <- sprintf("C%03d",seq_len(480));position <- rep(seq(-2.5,2.5,length.out=240),2);batch <- factor(rep(c("A","B"),each=240),levels=c("A","B"))
 mu <- exp(outer(seq_along(genes),seq_along(ids),function(g,i)3+0.8*sin(g/5)+0.9*sin((g%%7+1)*position[i]/3)+0.4*(batch[i]=="B")*(g%%3==0)))
 expression <- matrix(rnbinom(length(mu),size=8,mu=as.numeric(mu)),nrow=length(genes),dimnames=list(genes,ids));stopifnot(all(rowSums(expression)>0),all(colSums(expression)>0))
 cm <- data.frame(Batch=batch,position=position,row.names=ids);gm <- data.frame(gene_short_name=genes,row.names=genes)
 write.table(data.frame(gene_id=genes,expression,check.names=FALSE),file.path(out,"counts.tsv"),sep="\t",quote=FALSE,row.names=FALSE);write.table(data.frame(cell_id=ids,cm,check.names=FALSE),file.path(out,"cells.tsv"),sep="\t",quote=FALSE,row.names=FALSE);write.table(data.frame(gene_id=genes,gm,check.names=FALSE),file.path(out,"genes.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
 seed <- 251052L;set.seed(seed);rng <- list(before=.Random.seed)
 stage <- "original preprocessing";cds <- monocle3::new_cell_data_set(Matrix::Matrix(expression,sparse=TRUE),cell_metadata=cm,gene_metadata=gm);cds <- monocle3::preprocess_cds(cds,method="PCA",num_dim=10,norm_method="log",scaling=TRUE,verbose=TRUE);rng$after_preprocessing <- .Random.seed;pca <- SingleCellExperiment::reducedDims(cds)[["PCA"]]
 stage <- "original Batchelor alignment";cds <- monocle3::align_cds(cds,preprocess_method="PCA",alignment_group="Batch",alignment_k=20,verbose=TRUE);rng$after_alignment <- .Random.seed;aligned <- SingleCellExperiment::reducedDims(cds)[["Aligned"]]
 stopifnot(identical(rownames(pca),ids),identical(rownames(aligned),ids),nrow(aligned)==length(ids),ncol(aligned)==10L,all(is.finite(aligned)))
 write_json(list(cell_ids=as.list(ids),pca=rows(pca),aligned=rows(aligned),batch=as.list(as.character(batch)),batch_levels=as.list(levels(batch))),file.path(out,"alignment.json"))
 stage <- "original aligned UMAP";cds <- monocle3::reduce_dimension(cds,preprocess_method="Aligned",reduction_method="UMAP",max_components=2,umap.n_neighbors=20,umap.min_dist=0.1,umap.fast_sgd=FALSE,cores=1,verbose=TRUE);rng$after_dimension_reduction <- .Random.seed
 stage <- "original clustering";cds <- monocle3::cluster_cells(cds,reduction_method="UMAP",k=20,cluster_method="leiden",num_iter=2,resolution=0.01,random_seed=42,verbose=TRUE);rng$after_clustering <- .Random.seed
 stage <- "original graph learning";cds <- monocle3::learn_graph(cds,use_partition=FALSE,close_loop=FALSE,learn_graph_control=list(ncenter=130,minimal_branch_len=15),verbose=TRUE);rng$after_graph <- .Random.seed
 stage <- "original explicitly rooted ordering";cds <- monocle3::order_cells(cds,root_cells="C001",verbose=TRUE);rng$after_ordering <- .Random.seed
 coordinates <- SingleCellExperiment::reducedDims(cds)[["UMAP"]];aux <- cds@principal_graph_aux[["UMAP"]];roots <- aux$root_pr_nodes;projected <- aux$pr_graph_cell_proj_tree;vertices <- igraph::V(projected)$name;edges <- igraph::as_data_frame(projected,what="edges");weighted <- "weight" %in% names(edges);ns <- asNamespace("monocle3")
 original <- get("extract_general_graph_ordering",ns)(cds,roots,verbose=FALSE,reduction_method="UMAP");nearest <- get("find_nearest_vertex",ns)(aux$dp_mst[,roots,drop=FALSE],t(coordinates));stopifnot(identical(as.character(original$sample_name),vertices))
 edge_rows <- lapply(seq_len(nrow(edges)),function(i){e <- list(from=match(edges$from[i],vertices)-1L,to=match(edges$to[i],vertices)-1L);if(weighted)e$weight <- edges$weight[i];e})
 write_json(list(cell_ids=as.list(ids),cell_coordinates=rows(coordinates),principal_ids=as.list(colnames(aux$dp_mst)),principal_coordinates=rows(t(aux$dp_mst)),roots=as.list(roots),graph_vertex_ids=as.list(vertices),edge_weight_mode=if(weighted)"weighted"else"unweighted",edges=edge_rows,expected_cell_pseudotime=tokens(monocle3::pseudotime(cds)),expected_graph_pseudotime=tokens(original$pseudo_time),expected_root_indices=as.list(as.integer(nearest)-1L)),file.path(out,"prepared-graph.json"))
 write.table(data.frame(cell_id=ids,cluster=as.character(monocle3::clusters(cds)),partition=as.character(monocle3::partitions(cds)),pseudotime=unlist(tokens(monocle3::pseudotime(cds)),use.names=FALSE)),file.path(out,"cell-results.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
 stage <- "direct original reducedMNN comparison";set.seed(2016);direct <- batchelor::reducedMNN(as.matrix(pca),batch=batch,k=20);stopifnot(identical(unname(as.matrix(aligned)),unname(as.matrix(direct$corrected))));rng$after_direct_reference <- .Random.seed;saveRDS(direct,file.path(out,"direct-reducedMNN.rds"))
 stage <- "required model roundtrip";saveRDS(cds,file.path(out,"cds.rds"));saveRDS(rng,file.path(out,"rng.rds"));loaded <- readRDS(file.path(out,"cds.rds"))
 stopifnot(identical(colnames(cds),ids),identical(rownames(cds),genes),identical(colnames(loaded),ids),identical(rownames(loaded),genes),identical(as.matrix(SummarizedExperiment::assay(loaded,"counts")),expression),identical(SummarizedExperiment::colData(loaded),SummarizedExperiment::colData(cds)),identical(SingleCellExperiment::reducedDims(loaded),SingleCellExperiment::reducedDims(cds)),identical(monocle3::pseudotime(loaded),monocle3::pseudotime(cds)),identical(igraph::as_data_frame(monocle3::principal_graph(loaded)[["UMAP"]],what="both"),igraph::as_data_frame(monocle3::principal_graph(cds)[["UMAP"]],what="both")))
 # igraph rebuilds its private cache environments during serialization. Compare
 # every scientific attribute, vertex and edge; omit no other cluster/aux state.
 same_state(loaded@clusters,cds@clusters,"clusters");same_state(loaded@principal_graph_aux,cds@principal_graph_aux,"principal_graph_aux")
 result <- list(status="PASS_ORIGINAL_MONOCLE137_BATCHELOR120_COMPLETE_PIPELINE_CONTROL",seed=seed,genes=length(genes),cells=length(ids),original_alignment_equals_direct_reducedMNN=TRUE,complete_required_model_roundtrip=TRUE,root_cell_declaration="C001",root_direction_biologically_verified=FALSE,guards=guards,packages=setNames(lapply(loadedNamespaces(),function(n)as.character(getNamespaceVersion(n))),loadedNamespaces()),warnings=as.list(warnings_seen),messages=as.list(messages_seen),software_control_not_study_reproduction=TRUE,new_tool_credit=0,whole_goal_complete=FALSE)
 write_json(result,file.path(out,"COMPLETED.json"));writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"));cat(result$status,"\n")
},warning=function(w){warnings_seen <<- c(warnings_seen,paste0(stage,": ",conditionMessage(w)));invokeRestart("muffleWarning")},message=function(m){messages_seen <<- c(messages_seen,conditionMessage(m));invokeRestart("muffleMessage")}),error=function(e){writeLines(c(stage,conditionMessage(e),warnings_seen,messages_seen),file.path(out,"FAILURE.txt"));message(stage,": ",conditionMessage(e));quit(save="no",status=2L)})
