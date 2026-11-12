# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Typed data/parameters only; never evaluates caller-provided R expressions.
args <- commandArgs(trailingOnly=TRUE);stopifnot(length(args)>=4L)
out <- args[1];source_root <- args[2];.libPaths(args[-c(1,2)],include.site=FALSE)
unavailable <- function(message)stop(structure(list(message=message,call=NULL),class=c("runtime_unavailable","error","condition")))
warnings <- character();stage <- "runtime"
write_json <- function(x,p)jsonlite::write_json(x,p,pretty=TRUE,auto_unbox=TRUE,digits=NA,na="string",null="null")
tokens <- function(x)as.list(vapply(as.numeric(x),function(v)if(is.nan(v))"NaN" else if(is.na(v))"NA" else if(is.infinite(v))if(v>0)"Inf" else "-Inf" else sprintf("%.17g",v),character(1)))
rows <- function(x)lapply(seq_len(nrow(x)),function(i)as.list(unname(x[i,])))
read_tsv <- function(name)read.delim(file.path(out,"inputs",name),header=TRUE,sep="\t",quote="",comment.char="",check.names=FALSE,colClasses="character",na.strings=NULL,strip.white=FALSE,fileEncoding="UTF-8")
tryCatch(withCallingHandlers({
 if(as.character(getRversion())!="4.3.3"||R.version$platform!="x86_64-conda-linux-gnu")unavailable("Supply the qualified Linux x86_64 R4.3.3 profile")
 if(!requireNamespace("jsonlite",quietly=TRUE))unavailable("Missing jsonlite in declared libraries")
 profile <- jsonlite::read_json(file.path(out,"source-profile.json"),simplifyVector=FALSE)
 for(name in names(profile$packages))if(!requireNamespace(name,quietly=TRUE)||as.character(getNamespaceVersion(name))!=profile$packages[[name]])unavailable(paste("Missing/wrong qualified package version:",name,profile$packages[[name]]))
 suppressPackageStartupMessages(library(monocle3));stopifnot(normalizePath(find.package("monocle3"))==normalizePath(file.path(args[3],"monocle3")))
 RhpcBLASctl::blas_set_num_threads(1L);RhpcBLASctl::omp_set_num_threads(1L);options(mc.cores=1L)
 stage <- "original source guards";definitions <- list();ns <- asNamespace("monocle3")
 for(file in list.files(file.path(source_root,"R"),pattern="[.]R$",full.names=TRUE))for(expr in parse(file,keep.source=FALSE))if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]]) %in% c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))definitions[[as.character(expr[[2]])]] <- expr[[3]]
 for(name in unlist(profile$guarded_functions,use.names=FALSE)){
  stopifnot(!is.null(definitions[[name]]),exists(name,ns,inherits=FALSE));original <- eval(definitions[[name]],envir=ns);loaded <- get(name,ns,inherits=FALSE)
  if(!identical(body(original),body(loaded))||!identical(formals(original),formals(loaded)))stop("Original function body/formals mismatch: ",name)
 }
 p <- jsonlite::read_json(file.path(out,"parameters.json"),simplifyVector=FALSE);stage <- "complete input validation"
 counts <- read_tsv("counts.tsv");cells <- read_tsv("cells.tsv");genes <- read_tsv("genes.tsv")
 stopifnot(identical(names(genes),c("gene_id","gene_short_name")),names(counts)[1]=="gene_id",ncol(cells)>=5L,ncol(cells)<=1027L,identical(names(cells),c("cell_id","cluster","partition",paste0("UMAP_",seq_len(ncol(cells)-3L)))))
 valid_ids <- function(x)!anyNA(x)&&all(nzchar(x))&&!anyDuplicated(x)&&!any(grepl("[[:cntrl:]]",x))
 stopifnot(nrow(cells)>=4L,nrow(cells)<=1000000L,nrow(genes)>=1L,nrow(genes)<=1000000L,as.double(nrow(genes))*nrow(cells)<=10000000,valid_ids(cells$cell_id),valid_ids(genes$gene_id),identical(counts$gene_id,genes$gene_id),identical(names(counts)[-1],cells$cell_id))
 ids <- cells$cell_id;gene_ids <- genes$gene_id;cluster_levels <- unlist(p$cluster_levels,use.names=FALSE);partition_levels <- unlist(p$partition_levels,use.names=FALSE)
 stopifnot(setequal(unique(cells$cluster),cluster_levels),setequal(unique(cells$partition),partition_levels),identical(partition_levels,as.character(seq_along(partition_levels))))
 raw_count <- as.matrix(counts[-1]);stopifnot(all(grepl("^(0|[1-9][0-9]*)$",raw_count)));storage.mode(raw_count) <- "double";stopifnot(all(is.finite(raw_count)),all(raw_count<=9007199254740991));rownames(raw_count) <- gene_ids;colnames(raw_count) <- ids
 coordinates <- as.matrix(cells[-c(1,2,3)]);storage.mode(coordinates) <- "double";stopifnot(all(is.finite(coordinates)));rownames(coordinates) <- ids
 cm <- data.frame(row.names=ids);gm <- data.frame(gene_short_name=genes$gene_short_name,row.names=gene_ids,stringsAsFactors=FALSE)
 clusters <- factor(cells$cluster,levels=cluster_levels);names(clusters) <- ids;partitions <- factor(cells$partition,levels=partition_levels);names(partitions) <- ids
 stage <- "original graph learning";set.seed(p$seed);rng_before <- .Random.seed
 cds <- monocle3::new_cell_data_set(Matrix::Matrix(raw_count,sparse=TRUE),cell_metadata=cm,gene_metadata=gm)
 SingleCellExperiment::reducedDims(cds)[["UMAP"]] <- coordinates
 cds@clusters[["UMAP"]] <- list(cluster_result=NULL,clusters=clusters,partitions=partitions)
 cds <- monocle3::learn_graph(cds,use_partition=p$use_partition,close_loop=p$close_loop,learn_graph_control=list(ncenter=p$ncenter,minimal_branch_len=p$minimal_branch_len),verbose=TRUE)
 graph <- monocle3::principal_graph(cds)[["UMAP"]];degrees <- igraph::degree(graph);aux <- cds@principal_graph_aux[["UMAP"]]
 write_json(list(principal_ids=as.list(colnames(aux$dp_mst)),principal_coordinates=rows(t(aux$dp_mst)),edges=lapply(seq_len(igraph::ecount(graph)),function(i)as.list(igraph::ends(graph,i,names=TRUE)[1,])),degrees=as.list(degrees)),file.path(out,"principal-graph.json"))
 if(p$mode=="RUN"){
  stage <- "declared root validation and original ordering"
  if(!is.null(p$root_pr_nodes)){roots <- unlist(p$root_pr_nodes,use.names=FALSE);stopifnot(all(roots %in% names(degrees)))}else{
   root_cells <- unlist(p$root_cells,use.names=FALSE);stopifnot(all(root_cells %in% ids));roots <- paste0("Y_",unique(aux$pr_graph_cell_proj_closest_vertex[root_cells,]))
  }
  if(p$require_terminal_roots)stopifnot(all(degrees[roots]==1L))
  if(!is.null(p$root_pr_nodes))cds <- monocle3::order_cells(cds,root_pr_nodes=roots,verbose=TRUE)else cds <- monocle3::order_cells(cds,root_cells=root_cells,verbose=TRUE)
  aux <- cds@principal_graph_aux[["UMAP"]];stopifnot(identical(aux$root_pr_nodes,roots))
  projected <- aux$pr_graph_cell_proj_tree;vertices <- igraph::V(projected)$name;edges <- igraph::as_data_frame(projected,what="edges");weighted <- "weight" %in% names(edges)
  original <- get("extract_general_graph_ordering",ns)(cds,roots,verbose=FALSE,reduction_method="UMAP")
  nearest <- get("find_nearest_vertex",ns)(aux$dp_mst[,roots,drop=FALSE],t(coordinates))
  stopifnot(identical(as.character(original$sample_name),vertices))
  edge_rows <- lapply(seq_len(nrow(edges)),function(i){e <- list(from=match(edges$from[i],vertices)-1L,to=match(edges$to[i],vertices)-1L);if(weighted)e$weight <- edges$weight[i];e})
  write_json(list(cell_ids=as.list(ids),cell_coordinates=rows(coordinates),principal_ids=as.list(colnames(aux$dp_mst)),principal_coordinates=rows(t(aux$dp_mst)),roots=as.list(roots),graph_vertex_ids=as.list(vertices),edge_weight_mode=if(weighted)"weighted" else "unweighted",edges=edge_rows,expected_cell_pseudotime=tokens(monocle3::pseudotime(cds)),expected_graph_pseudotime=tokens(original$pseudo_time),expected_root_indices=as.list(as.integer(nearest)-1L)),file.path(out,"prepared-graph.json"))
  write.table(data.frame(cell_id=ids,pseudotime=unlist(tokens(monocle3::pseudotime(cds)),use.names=FALSE)),file.path(out,"cell-pseudotime.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
 }
 stopifnot(identical(colnames(cds),ids),identical(rownames(cds),gene_ids),identical(SingleCellExperiment::reducedDims(cds)[["UMAP"]],coordinates))
 stage <- "complete required model save/reload";saveRDS(cds,file.path(out,"cds.rds"));saveRDS(list(before=rng_before,after=.Random.seed),file.path(out,"rng.rds"));reloaded <- readRDS(file.path(out,"cds.rds"))
 # igraph's private cache environment is recreated by serialization. Compare
 # every scientific graph attribute/vertex/edge value and all other aux fields.
 same_state <- function(a,b,path="principal_graph_aux"){
  if(inherits(a,"igraph")||inherits(b,"igraph")){
   stopifnot(inherits(a,"igraph"),inherits(b,"igraph"),identical(igraph::is_directed(a),igraph::is_directed(b)),identical(igraph::graph_attr(a),igraph::graph_attr(b)),identical(igraph::as_data_frame(a,what="both"),igraph::as_data_frame(b,what="both")))
  }else if(isS4(a)&&isS4(b)){
   stopifnot(identical(class(a),class(b)),identical(slotNames(a),slotNames(b)));for(name in slotNames(a))same_state(slot(a,name),slot(b,name),paste0(path,"@",name))
  }else if(is.list(a)&&is.list(b)){
   stopifnot(identical(names(a),names(b)),length(a)==length(b));for(i in seq_along(a))same_state(a[[i]],b[[i]],paste0(path,"/",if(is.null(names(a)))i else names(a)[i]))
  }else if(!identical(a,b))stop("Saved model state differs at ",path)
  invisible(TRUE)
 }
 stopifnot(identical(as.matrix(SummarizedExperiment::assay(reloaded,"counts")),raw_count),identical(SingleCellExperiment::reducedDims(reloaded)[["UMAP"]],coordinates),identical(reloaded@clusters,cds@clusters),identical(igraph::as_data_frame(monocle3::principal_graph(reloaded)[["UMAP"]],what="both"),igraph::as_data_frame(graph,what="both")))
 same_state(reloaded@principal_graph_aux,cds@principal_graph_aux)
 if(p$mode=="RUN")stopifnot(identical(monocle3::pseudotime(reloaded),monocle3::pseudotime(cds)))
 write_json(list(status="PASS_ORIGINAL_MONOCLE133_GRAPH_REQUIRED_MODEL_ROUNDTRIP",mode=p$mode,parameters=p,cells=length(ids),genes=length(gene_ids),principal_points=ncol(aux$dp_mst),warnings=as.list(warnings),packages=setNames(lapply(loadedNamespaces(),function(n)as.character(getNamespaceVersion(n))),loadedNamespaces()),root_direction_biologically_verified=FALSE,embedding_or_clusters_learned=FALSE,guarded_functions=profile$guarded_functions),file.path(out,"worker-result.json"))
 writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"));writeLines("0",file.path(out,"worker-exit.txt"))
},warning=function(w){warnings <<- c(warnings,conditionMessage(w));invokeRestart("muffleWarning")}),error=function(e){
 code <- if(inherits(e,"runtime_unavailable"))3L else 2L
 writeLines(as.character(code),file.path(out,"worker-exit.txt"));writeLines(c(stage,conditionMessage(e),warnings),file.path(out,"worker-error.txt"));message(stage,": ",conditionMessage(e));quit(save="no",status=code)
})
