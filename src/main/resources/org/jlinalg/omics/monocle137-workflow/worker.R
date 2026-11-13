# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Typed original1.3.7/Batchelor1.20 workflow; evaluates no caller-provided R code.
args <- commandArgs(trailingOnly=TRUE);stopifnot(length(args)>=4L)
out <- args[1];source_m3 <- args[2];source_b <- args[3];.libPaths(args[-c(1,2,3)],include.site=FALSE)
stage <- "runtime";warnings_seen <- character();messages_seen <- character()
unavailable <- function(message)stop(structure(list(message=message,call=NULL),class=c("runtime_unavailable","error","condition")))
write_json <- function(x,p)jsonlite::write_json(x,p,pretty=TRUE,auto_unbox=TRUE,digits=NA,na="string",null="null")
tokens <- function(x)as.list(vapply(as.numeric(x),function(v)if(is.nan(v))"NaN"else if(is.na(v))"NA"else if(is.infinite(v))if(v>0)"Inf"else"-Inf"else sprintf("%.17g",v),character(1)))
rows <- function(x)lapply(seq_len(nrow(x)),function(i)as.list(unname(x[i,])))
read_tsv <- function(name)read.delim(file.path(out,"inputs",name),header=TRUE,sep="\t",quote="",comment.char="",check.names=FALSE,colClasses="character",na.strings=NULL,strip.white=FALSE,row.names=NULL,fileEncoding="UTF-8")
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
 if(as.character(getRversion())!="4.3.3"||R.version$platform!="x86_64-conda-linux-gnu")unavailable("Provide qualified Linux x86_64 R4.3.3")
 if(!requireNamespace("jsonlite",quietly=TRUE))unavailable("Missing jsonlite in declared private libraries")
 profile <- jsonlite::read_json(file.path(out,"source-profile.json"),simplifyVector=FALSE)
 for(name in names(profile$packages))if(!requireNamespace(name,quietly=TRUE)||as.character(getNamespaceVersion(name))!=profile$packages[[name]])unavailable(paste("Missing/wrong qualified package:",name,profile$packages[[name]]))
 suppressPackageStartupMessages(library(monocle3));stopifnot(as.character(getNamespaceVersion("monocle3"))=="1.3.7",as.character(getNamespaceVersion("batchelor"))=="1.20.0")
 stage <- "original source guards";guards <- list(monocle3=guard("monocle3",source_m3,unlist(profile$guarded_functions$monocle3,use.names=FALSE)),batchelor=guard("batchelor",source_b,unlist(profile$guarded_functions$batchelor,use.names=FALSE)))
 RhpcBLASctl::blas_set_num_threads(1L);RhpcBLASctl::omp_set_num_threads(1L);options(mc.cores=1L)
 p <- jsonlite::read_json(file.path(out,"parameters.json"),simplifyVector=FALSE);stage <- "complete declared input population"
 cells <- read_tsv("cells.tsv");genes <- read_tsv("genes.tsv");stopifnot(identical(names(genes),c("gene_id","gene_short_name")),names(cells)[1]=="cell_id",setequal(names(cells)[-1],names(p$metadata_types)))
 ids <- cells$cell_id;gene_ids <- genes$gene_id;stopifnot(!anyDuplicated(ids),!anyDuplicated(gene_ids),length(ids)>=5L)
 if(p$expression_format=="TSV"){
  d <- read_tsv("expression.data");stopifnot(identical(d$gene_id,gene_ids),identical(names(d)[-1],ids));expression <- as.matrix(d[-1]);storage.mode(expression) <- "double";dimnames(expression) <- list(gene_ids,ids);expression <- Matrix::Matrix(expression,sparse=TRUE)
 }else{expression <- Matrix::readMM(file.path(out,"inputs/expression.data"));stopifnot(identical(dim(expression),as.integer(c(length(gene_ids),length(ids)))));dimnames(expression) <- list(gene_ids,ids)}
 stopifnot(all(is.finite(expression@x)),all(expression@x>=0));if(p$expression_units=="raw-integer-counts")stopifnot(all(expression@x==floor(expression@x)),all(expression@x<=9007199254740991))
 cm <- cells[-1];rownames(cm) <- ids
 for(name in names(p$metadata_types)){type <- p$metadata_types[[name]];if(type$type=="numeric"){cm[[name]] <- as.numeric(cm[[name]]);stopifnot(all(is.finite(cm[[name]])))}else{levels <- unlist(type$levels,use.names=FALSE);stopifnot(all(cm[[name]] %in% levels));cm[[name]] <- factor(cm[[name]],levels=levels)}}
 gm <- data.frame(gene_short_name=genes$gene_short_name,row.names=gene_ids,stringsAsFactors=FALSE)
 set.seed(p$seed);rng <- list(before=.Random.seed);cds <- monocle3::new_cell_data_set(expression,cell_metadata=cm,gene_metadata=gm)
 pp <- p$preprocessing;preprocess_call <- list(cds=cds,method=pp$method,num_dim=pp$num_dim,norm_method=pp$norm_method,scaling=pp$scaling,verbose=TRUE)
 if(!is.null(pp$pseudo_count)&&!identical(pp$pseudo_count,"ORIGINAL_DEFAULT"))preprocess_call$pseudo_count <- pp$pseudo_count
 stage <- "original preprocessing";cds <- do.call(monocle3::preprocess_cds,preprocess_call);rng$after_preprocessing <- .Random.seed;before_alignment <- SingleCellExperiment::reducedDims(cds)[[pp$method]]
 a <- p$alignment;residual_terms <- unlist(a$residual_terms,use.names=FALSE);residual_formula <- if(length(residual_terms))paste0("~1 + ",paste(residual_terms,collapse=" + "))else NULL
 stage <- "original Batchelor alignment";cds <- monocle3::align_cds(cds,preprocess_method=pp$method,alignment_group=a$batch_column,alignment_k=a$k,residual_model_formula_str=residual_formula,verbose=TRUE);rng$after_alignment <- .Random.seed;aligned <- SingleCellExperiment::reducedDims(cds)[["Aligned"]]
 stopifnot(identical(rownames(before_alignment),ids),identical(rownames(aligned),ids),nrow(aligned)==length(ids),all(is.finite(aligned)))
 write_json(list(cell_ids=as.list(ids),preprocessing_method=pp$method,before_alignment=rows(before_alignment),aligned=rows(aligned),batch=as.list(as.character(cm[[a$batch_column]])),batch_levels=as.list(levels(cm[[a$batch_column]]))),file.path(out,"alignment.json"))
 red <- p$reduction;stage <- "original aligned UMAP";cds <- monocle3::reduce_dimension(cds,preprocess_method="Aligned",reduction_method="UMAP",max_components=red$max_components,umap.n_neighbors=red$n_neighbors,umap.min_dist=red$min_dist,umap.metric=red$metric,umap.fast_sgd=red$fast_sgd,cores=1,verbose=TRUE);rng$after_dimension_reduction <- .Random.seed
 c <- p$clustering;stage <- "original clustering";cds <- monocle3::cluster_cells(cds,reduction_method="UMAP",k=c$k,cluster_method=c$method,num_iter=c$num_iter,partition_qval=c$partition_qval,weight=c$weight,resolution=c$resolution,random_seed=c$random_seed,verbose=TRUE);rng$after_clustering <- .Random.seed
 g <- p$graph;control <- list();if(!identical(g$ncenter,"ORIGINAL_DEFAULT"))control$ncenter <- g$ncenter;if(!identical(g$minimal_branch_len,"ORIGINAL_DEFAULT"))control$minimal_branch_len <- g$minimal_branch_len
 stage <- "original graph learning";cds <- monocle3::learn_graph(cds,use_partition=g$use_partition,close_loop=g$close_loop,learn_graph_control=control,verbose=TRUE);rng$after_graph <- .Random.seed
 coordinates <- SingleCellExperiment::reducedDims(cds)[["UMAP"]];aux <- cds@principal_graph_aux[["UMAP"]];graph <- monocle3::principal_graph(cds)[["UMAP"]];degrees <- igraph::degree(graph)
 stopifnot(identical(rownames(coordinates),ids),all(is.finite(coordinates)))
 write_json(list(principal_ids=as.list(colnames(aux$dp_mst)),principal_coordinates=rows(t(aux$dp_mst)),edges=lapply(seq_len(igraph::ecount(graph)),function(i)as.list(igraph::ends(graph,i,names=TRUE)[1,])),degrees=as.list(degrees)),file.path(out,"principal-graph.json"))
 if(p$mode=="RUN"){
  stage <- "declared roots and original ordering"
  if(!is.null(p$root_pr_nodes)){roots <- unlist(p$root_pr_nodes,use.names=FALSE);stopifnot(all(roots %in% names(degrees)))}else{root_cells <- unlist(p$root_cells,use.names=FALSE);stopifnot(all(root_cells %in% ids));roots <- paste0("Y_",unique(aux$pr_graph_cell_proj_closest_vertex[root_cells,]))}
  if(p$require_terminal_roots)stopifnot(all(degrees[roots]==1L))
  if(!is.null(p$root_pr_nodes))cds <- monocle3::order_cells(cds,root_pr_nodes=roots,verbose=TRUE)else cds <- monocle3::order_cells(cds,root_cells=root_cells,verbose=TRUE)
  rng$after_ordering <- .Random.seed;aux <- cds@principal_graph_aux[["UMAP"]];stopifnot(identical(aux$root_pr_nodes,roots));projected <- aux$pr_graph_cell_proj_tree;vertices <- igraph::V(projected)$name;edges <- igraph::as_data_frame(projected,what="edges");weighted <- "weight" %in% names(edges);ns <- asNamespace("monocle3")
  original <- get("extract_general_graph_ordering",ns)(cds,roots,verbose=FALSE,reduction_method="UMAP");nearest <- get("find_nearest_vertex",ns)(aux$dp_mst[,roots,drop=FALSE],t(coordinates));stopifnot(identical(as.character(original$sample_name),vertices))
  edge_rows <- lapply(seq_len(nrow(edges)),function(i){e <- list(from=match(edges$from[i],vertices)-1L,to=match(edges$to[i],vertices)-1L);if(weighted)e$weight <- edges$weight[i];e})
  write_json(list(cell_ids=as.list(ids),cell_coordinates=rows(coordinates),principal_ids=as.list(colnames(aux$dp_mst)),principal_coordinates=rows(t(aux$dp_mst)),roots=as.list(roots),graph_vertex_ids=as.list(vertices),edge_weight_mode=if(weighted)"weighted"else"unweighted",edges=edge_rows,expected_cell_pseudotime=tokens(monocle3::pseudotime(cds)),expected_graph_pseudotime=tokens(original$pseudo_time),expected_root_indices=as.list(as.integer(nearest)-1L)),file.path(out,"prepared-graph.json"))
 }
 stopifnot(identical(names(monocle3::clusters(cds)),ids),identical(names(monocle3::partitions(cds)),ids))
 cell_result <- data.frame(cell_id=ids,cluster=as.character(monocle3::clusters(cds)),partition=as.character(monocle3::partitions(cds)));if(p$mode=="RUN")cell_result$pseudotime <- unlist(tokens(monocle3::pseudotime(cds)),use.names=FALSE)
 write.table(cell_result,file.path(out,"cell-results.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
 stage <- "required saved model state";saveRDS(cds,file.path(out,"cds.rds"));saveRDS(rng,file.path(out,"rng.rds"));loaded <- readRDS(file.path(out,"cds.rds"))
 stopifnot(identical(colnames(cds),ids),identical(rownames(cds),gene_ids),identical(colnames(loaded),ids),identical(rownames(loaded),gene_ids),identical(as.matrix(SummarizedExperiment::assay(loaded,"counts")),as.matrix(expression)),identical(SummarizedExperiment::colData(loaded),SummarizedExperiment::colData(cds)),identical(SingleCellExperiment::reducedDims(loaded),SingleCellExperiment::reducedDims(cds)))
 same_state(loaded@clusters,cds@clusters,"clusters");same_state(loaded@principal_graph,cds@principal_graph,"principal_graph");same_state(loaded@principal_graph_aux,cds@principal_graph_aux,"principal_graph_aux")
 if(p$mode=="RUN")stopifnot(identical(monocle3::pseudotime(loaded),monocle3::pseudotime(cds)))
 write_json(list(cell_ids=as.list(ids),gene_ids=as.list(gene_ids),preprocessing_method=pp$method,preprocessing_dimensions=ncol(before_alignment),aligned_dimensions=ncol(aligned),umap_dimensions=ncol(coordinates),size_factors=tokens(monocle3::size_factors(cds)),umap=rows(coordinates),clusters=as.list(as.character(monocle3::clusters(cds))),partitions=as.list(as.character(monocle3::partitions(cds)))),file.path(out,"population.json"))
 write_json(list(parameters=p,residual_formula=residual_formula,cores=1L,original_align_cds_seed_reset=2016L,other_function_controls="original defaults in the pinned source"),file.path(out,"original-arguments.json"))
 write_json(list(status="PASS_ORIGINAL_MONOCLE137_BATCHELOR120_COMPLETE_PIPELINE_REQUIRED_STATE",mode=p$mode,genes=length(gene_ids),cells=length(ids),complete_required_model_roundtrip=TRUE,igraph_private_cache_identity_compared=FALSE,all_scientific_graph_attributes_compared=TRUE,all_external_nn_pointers_certified=FALSE,guards=guards,warnings=as.list(warnings_seen),messages=as.list(messages_seen),packages=setNames(lapply(loadedNamespaces(),function(n)as.character(getNamespaceVersion(n))),loadedNamespaces()),root_direction_biologically_verified=FALSE,study_reproduced=FALSE,whole_goal_complete=FALSE),file.path(out,"worker-result.json"))
 writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"));writeLines("0",file.path(out,"worker-exit.txt"))
},warning=function(w){warnings_seen <<- c(warnings_seen,paste0(stage,": ",conditionMessage(w)));invokeRestart("muffleWarning")},message=function(m){messages_seen <<- c(messages_seen,conditionMessage(m));invokeRestart("muffleMessage")}),error=function(e){
 code <- if(inherits(e,"runtime_unavailable"))3L else 2L;writeLines(as.character(code),file.path(out,"worker-exit.txt"));writeLines(c(stage,conditionMessage(e),warnings_seen,messages_seen),file.path(out,"worker-error.txt"));if(exists("cds",envir=.GlobalEnv,inherits=FALSE))try(saveRDS(get("cds",envir=.GlobalEnv,inherits=FALSE),file.path(out,"unadmitted-last-cds.rds")),silent=TRUE);message(stage,": ",conditionMessage(e));quit(save="no",status=code)
})
