# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original 1.3.3 reference controls, not user or paper biological analysis.
args <- commandArgs(trailingOnly=TRUE);stopifnot(length(args)==3L)
out <- args[1];.libPaths(args[2:3],include.site=FALSE)
stopifnot(as.character(getRversion())=="4.3.3",as.character(packageVersion("monocle3"))=="1.3.3")
suppressPackageStartupMessages(library(monocle3));RhpcBLASctl::blas_set_num_threads(1L);RhpcBLASctl::omp_set_num_threads(1L)
options(mc.cores=1L);dir.create(file.path(out,"cases"))
write_json <- function(value,path)jsonlite::write_json(value,path,pretty=TRUE,auto_unbox=TRUE,digits=NA,na="string",null="null")
tokens <- function(x)as.list(vapply(as.numeric(x),function(v)if(is.nan(v))"NaN" else if(is.na(v))"NA" else if(is.infinite(v))if(v>0)"Inf" else "-Inf" else sprintf("%.17g",v),character(1)))
rows <- function(x)lapply(seq_len(nrow(x)),function(i)as.list(unname(x[i,])))
n <- 360L;ids <- sprintf("C%04d",seq_len(n));genes <- sprintf("G%03d",seq_len(24L));index <- seq_len(n);branch <- rep(c("stem","upper","lower"),each=120L);t <- rep(seq(0,1,length.out=120L),3L)
coordinates <- cbind(UMAP_1=ifelse(branch=="stem",-1+t,t),UMAP_2=ifelse(branch=="stem",0,ifelse(branch=="upper",t,-t))+0.002*sin(index*1.31));rownames(coordinates) <- ids
counts <- matrix(as.numeric((seq_len(length(genes)*n)*7L)%%19L+1L),nrow=length(genes),dimnames=list(genes,ids))
cm <- data.frame(cell_id=ids,cluster=branch,partition=rep("1",n),coordinates,check.names=FALSE,row.names=ids)
gm <- data.frame(gene_short_name=genes,row.names=genes,stringsAsFactors=FALSE)
write.table(data.frame(gene_id=genes,counts,check.names=FALSE),file.path(out,"counts.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
write.table(cm,file.path(out,"cells.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
write.table(data.frame(gene_id=genes,gm,check.names=FALSE),file.path(out,"genes.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
write_json(list(cluster_levels=as.list(c("stem","upper","lower")),partition_levels=list("1"),count_units="raw-integer-counts",embedding_slot="UMAP",seed=173L,cells=n,genes=length(genes)),file.path(out,"fixture.json"))
all_warnings <- character();results <- list()
tryCatch(withCallingHandlers({
 for(center in c(130L,140L,150L))for(branch_len in c(15L,20L)) {
  label <- paste0("centers",center,"-branch",branch_len);directory <- file.path(out,"cases",label);dir.create(directory)
  set.seed(173L);initial_rng <- .Random.seed
  cds <- monocle3::new_cell_data_set(Matrix::Matrix(counts,sparse=TRUE),cell_metadata=cm,gene_metadata=gm)
  SingleCellExperiment::reducedDims(cds)[["UMAP"]] <- coordinates
  clusters <- factor(branch,levels=c("stem","upper","lower"));names(clusters) <- ids
  partitions <- factor(rep("1",n),levels="1");names(partitions) <- ids
  cds@clusters[["UMAP"]] <- list(cluster_result=NULL,clusters=clusters,partitions=partitions)
  cds <- monocle3::learn_graph(cds,use_partition=FALSE,close_loop=FALSE,learn_graph_control=list(ncenter=center,minimal_branch_len=branch_len),verbose=TRUE)
  principal_graph <- monocle3::principal_graph(cds)[["UMAP"]]
  leaves <- sort(names(igraph::degree(principal_graph))[igraph::degree(principal_graph)==1L])
  stopifnot(length(leaves)>0L);roots <- leaves[1]
  write_json(list(root_pr_nodes=as.list(roots),choice="synthetic-control first sorted terminal; not a recovered paper root",terminal_nodes=as.list(leaves)),file.path(directory,"root-declaration.json"))
  cds <- monocle3::order_cells(cds,root_pr_nodes=roots,verbose=TRUE)
  stopifnot(identical(colnames(cds),ids),identical(rownames(cds),genes),identical(SingleCellExperiment::reducedDims(cds)[["UMAP"]],coordinates))
  aux <- cds@principal_graph_aux[["UMAP"]];projected <- aux$pr_graph_cell_proj_tree;vertices <- igraph::V(projected)$name;edges <- igraph::as_data_frame(projected,what="edges");weighted <- "weight" %in% names(edges)
  original <- get("extract_general_graph_ordering",asNamespace("monocle3"))(cds,roots,verbose=FALSE,reduction_method="UMAP")
  nearest <- get("find_nearest_vertex",asNamespace("monocle3"))(aux$dp_mst[,roots,drop=FALSE],t(coordinates))
  edge_rows <- lapply(seq_len(nrow(edges)),function(i){row <- list(from=match(edges$from[i],vertices)-1L,to=match(edges$to[i],vertices)-1L);if(weighted)row$weight <- edges$weight[i];row})
  graph <- list(cell_ids=as.list(ids),cell_coordinates=rows(coordinates),principal_ids=as.list(colnames(aux$dp_mst)),principal_coordinates=rows(t(aux$dp_mst)),roots=as.list(roots),graph_vertex_ids=as.list(vertices),edge_weight_mode=if(weighted)"weighted" else "unweighted",edges=edge_rows,
                expected_cell_pseudotime=tokens(monocle3::pseudotime(cds)),expected_graph_pseudotime=tokens(original$pseudo_time),expected_root_indices=as.list(as.integer(nearest)-1L))
  write_json(graph,file.path(directory,"prepared-graph.json"));saveRDS(cds,file.path(directory,"cds.rds"));saveRDS(list(before=initial_rng,after=.Random.seed),file.path(directory,"rng.rds"))
  reloaded <- readRDS(file.path(directory,"cds.rds"))
  stopifnot(identical(as.matrix(SummarizedExperiment::assay(reloaded,"counts")),counts),identical(SingleCellExperiment::reducedDims(reloaded)[["UMAP"]],coordinates),identical(monocle3::pseudotime(reloaded),monocle3::pseudotime(cds)),
            identical(igraph::as_data_frame(reloaded@principal_graph_aux[["UMAP"]]$pr_graph_cell_proj_tree,what="both"),igraph::as_data_frame(projected,what="both")))
  results[[label]] <- list(cells=n,genes=length(genes),ncenter=center,minimal_branch_len=branch_len,use_partition=FALSE,close_loop=FALSE,root_pr_nodes=as.list(roots),principal_points=ncol(aux$dp_mst),projected_vertices=length(vertices),projected_edges=nrow(edges),infinite_pseudotime=sum(is.infinite(monocle3::pseudotime(cds))),full_required_model_roundtrip=TRUE)
  cat(label,"complete original graph/order/roundtrip passed\n")
 }
 write_json(list(status="PASS_SIX_ORIGINAL_MONOCLE133_REPORTED_GRAPH_CONTROL_COMBINATIONS",results=results,warnings=as.list(all_warnings),loaded_packages=setNames(lapply(loadedNamespaces(),function(p)as.character(getNamespaceVersion(p))),loadedNamespaces()),paper_root_recovered=FALSE,paper_embedding_reconstructed=FALSE,user_analysis_admitted=FALSE,whole_goal_complete=FALSE),file.path(out,"GRAPH-CONTROLS.json"))
},warning=function(w){all_warnings <<- c(all_warnings,conditionMessage(w));invokeRestart("muffleWarning")}),error=function(e){write_json(list(status="FAIL2_ORIGINAL_MONOCLE133_GRAPH_CONTROL",cause=conditionMessage(e),warnings=as.list(all_warnings),completed_cases=results,analysis_admitted=FALSE),file.path(out,"FAILURE.json"));stop(e)})
