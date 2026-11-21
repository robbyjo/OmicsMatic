# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original-package neighbor construction; no author pipeline source is loaded.
args <- commandArgs(trailingOnly=TRUE);stopifnot(length(args)==1L)
root <- normalizePath(args[1],mustWork=TRUE);out <- file.path(root,"worker");stage <- "runtime"
warnings_seen <- character();messages_seen <- character();rng <- list();exit_code <- 2L
action <- "SKIP3: provide exact permitted R4.3.3/Monocle1.0.0/RANN2.6.2 runtime or retain the dependent skip. FAIL2: retain raw/original/partial evidence and original errors, correct the named cause and retry NEW output. Do not substitute editions, change ties, drop cells or repair nonfinite values. Graph weights alone are not a statistical test or paper reproduction."
write_json <- function(value,name){path<-file.path(out,name);stopifnot(!file.exists(path));jsonlite::write_json(value,path,pretty=TRUE,auto_unbox=TRUE,digits=NA,na="string",null="null")}
skip <- function(message)stop(structure(list(message=message,call=NULL),class=c("monocle_runtime_unavailable","error","condition")))
require <- function(ok,message)if(!isTRUE(ok))stop(message,call.=FALSE)
rows <- function(x)lapply(seq_len(nrow(x)),function(i)as.list(unname(x[i,])))
tokens <- function(x)as.list(vapply(as.numeric(x),function(value)if(is.nan(value))"NaN"else if(is.na(value))"NA"else if(is.infinite(value))if(value>0)"Inf"else"-Inf"else sprintf("%.17g",value),character(1)))
token_rows <- function(x)lapply(seq_len(nrow(x)),function(i)tokens(x[i,]))
labels <- function(x,kind){require(is.character(x)&&length(x)>0L&&!anyNA(x)&&!anyDuplicated(x)&&all(nzchar(x))&&!any(grepl("[\x01-\x1f\x7f]",x)),paste("Require complete unique literal",kind));x}
sha <- function(value)digest::digest(serialize(value,NULL,version=3),algo="sha256",serialize=FALSE)
definitions <- function(path){
 result<-list()
 for(expr in parse(path,keep.source=FALSE))if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]]) %in% c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))result[[as.character(expr[[2]])]]<-expr[[3]]
 result
}
guard <- function(profile){
 for(package in names(profile$packages)){
  if(!requireNamespace(package,quietly=TRUE))skip(paste("Required package unavailable:",package))
  if(as.character(packageVersion(package))!=profile$packages[[package]])skip(paste("Exact package version required:",package,profile$packages[[package]]))
 }
 result<-list()
 for(package in names(profile$guards)){
  ns<-asNamespace(package);original<-list()
  for(name in names(profile$files))if(startsWith(name,paste0("reference/",package,"/")))original<-c(original,definitions(file.path(root,"source",name)))
  for(name in unlist(profile$guards[[package]])){
   loaded<-get(name,ns,inherits=FALSE)
   if(package=="slam"){
    wanted<-profile$function_fingerprints$slam[[name]]
    require(identical(sha(body(loaded)),wanted$body_sha256)&&identical(sha(formals(loaded)),wanted$formals_sha256),paste("Changed canonical original function:",package,name))
   }else{
    require(!is.null(original[[name]]),paste("Missing original reference:",package,name));reference<-eval(original[[name]],ns)
    require(identical(body(reference),body(loaded))&&identical(formals(reference),formals(loaded)),paste("Changed original body/formals:",package,name))
   }
  }
  result[[package]]<-as.list(unlist(profile$guards[[package]]))
 }
 result
}
module_pins <- function(){
 result<-list()
 for(name in names(getLoadedDLLs())){
  path<-getLoadedDLLs()[[name]][["path"]]
  if(nzchar(path)&&file.exists(path))result[[name]]<-list(path=normalizePath(path),bytes=unname(file.info(path)$size),sha256=digest::digest(path,file=TRUE,algo="sha256"))
 }
 result
}
tryCatch(withCallingHandlers({
 .libPaths(readLines(file.path(out,"runtime-paths.txt"),warn=FALSE),include.site=FALSE)
 runtime<-jsonlite::read_json(file.path(out,"runtime.json"),simplifyVector=FALSE)
 .libPaths(unlist(runtime$r_libraries),include.site=FALSE)
 if(as.character(getRversion())!="4.3.3"||R.version$platform!="x86_64-conda-linux-gnu")skip("Exact Linux R4.3.3 conda profile required")
 profile<-jsonlite::read_json(file.path(root,"source/PROFILE.json"),simplifyVector=FALSE)
 guards_before<-guard(profile);modules_before<-module_pins()
 request<-jsonlite::read_json(file.path(out,"parameters.json"),simplifyVector=FALSE)
 stage<-"read complete source CDS";cds<-readRDS(file.path(root,"inputs/source-cds.rds"))
 require(identical(as.character(class(cds)),"cell_data_set"),"Input must be a complete original Monocle1.0.0 cell_data_set, not a wrapper list or subclass")
 methods::validObject(cds);require(identical(as.character(S4Vectors::metadata(cds)$cds_version),"1.0.0"),"Input CDS metadata must declare Monocle1.0.0; no version update is inferred")
 ids<-labels(colnames(cds),"cell IDs");genes<-labels(rownames(cds),"gene IDs");n<-length(ids)
 require(n<=request$maximum_cells,"Complete source CDS exceeds maximum_cells; no subset is admitted")
 count_matrix<-SingleCellExperiment::counts(cds);require(inherits(count_matrix,"dgCMatrix"),"This profile requires complete in-memory dgCMatrix counts; external/delayed backends need separate capture")
 require(length(SummarizedExperiment::assayNames(cds))==1L&&identical(SummarizedExperiment::assayNames(cds),"counts"),"Supply a counts-only original1.0.0 CDS; no assay is discarded")
 slot<-request$reduction_slot;coordinates<-SingleCellExperiment::reducedDims(cds)[[slot]]
 require(is.matrix(coordinates)&&is.numeric(coordinates)&&nrow(coordinates)==n&&ncol(coordinates)>0L&&all(is.finite(coordinates)),"Selected embedding must be complete, finite and in source CDS cell order")
 require(identical(rownames(coordinates),ids),"Selected embedding axes differ from the source CDS")
 require(as.double(n)*min(request$k+1L,n)<=request$maximum_rank_entries,"Complete source ranking exceeds capacity; no prefix or subset is admitted")
 projection<-list(cell_ids=as.list(ids),gene_ids=as.list(genes),count_dimensions=as.list(dim(count_matrix)),reduction_slot=slot,coordinates=token_rows(coordinates))
 state<-list(counts=count_matrix,row_data=SummarizedExperiment::rowData(cds),cell_data=SummarizedExperiment::colData(cds),metadata=S4Vectors::metadata(cds),cell_ids=ids,gene_ids=genes,coordinates=coordinates)
 native<-list(schema_version=1L,source_profile="monocle3-1.0.0-004c096f3a3bdaf1e6709a65fb36f40ba775f1ab",neighbor_graph=request$neighbor_graph,ranking_provenance="Actual original RANN2.6.2 nn2 standard search on the complete captured source-CDS embedding; first column retained for literal source discard.",cell_ids=as.list(ids))
 if(request$neighbor_graph=="principal_graph"){
  graph<-cds@principal_graph[[slot]];aux<-cds@principal_graph_aux[[slot]]
  require(inherits(graph,"igraph")&&!igraph::is_directed(graph),"Require the complete original undirected principal graph")
  labels(igraph::V(graph)$name,"original graph vertex IDs")
  dp<-aux$dp_mst;require(is.matrix(dp)&&is.numeric(dp)&&ncol(dp)>0L,"Missing complete principal-point matrix")
  point_ids<-labels(colnames(dp),"principal IDs");require(all(point_ids %in% igraph::V(graph)$name),"Principal point axis is absent from the original graph")
  closest<-aux$pr_graph_cell_proj_closest_vertex
  require(is.matrix(closest)&&ncol(closest)==1L&&is.numeric(closest)&&!is.null(rownames(closest))&&!anyDuplicated(rownames(closest)),"Require numeric original closest-vertex matrix with unique source rows")
  # These are the original source's explicit subsetting/reordering operations.
  selected<-closest[rownames(closest) %in% rownames(SummarizedExperiment::colData(cds)),,drop=FALSE]
  selected<-selected[colnames(cds),]
  require(length(selected)==n&&all(is.finite(selected))&&all(selected==floor(selected))&&all(selected>=1&selected<=ncol(dp)),"Selected principal membership indices are invalid; no coercion is inferred")
  raw_edges<-igraph::as_edgelist(graph,names=TRUE);from<-match(raw_edges[,1],point_ids);to<-match(raw_edges[,2],point_ids);selected_edges<-!is.na(from)&!is.na(to)
  indices<-cbind(from[selected_edges],to[selected_edges])
  native$principal_ids<-as.list(point_ids);native$cell_to_principal_zero_based<-as.list(as.integer(selected)-1L);native$principal_edges_zero_based<-rows(indices-1L)
  native$principal_graph_provenance<-"Complete captured source principal graph, projected through the original dp_mst column axis; no graph learning or vertex-label sorting."
  projection$principal_ids<-as.list(point_ids);projection$principal_coordinates<-token_rows(dp);projection$selected_membership_zero_based<-native$cell_to_principal_zero_based;projection$native_principal_edges_zero_based<-native$principal_edges_zero_based
  projection$raw_projection_rows<-as.list(rownames(closest));projection$raw_graph_vertex_ids<-as.list(igraph::V(graph)$name)
  state$principal_graph<-list(directed=igraph::is_directed(graph),attributes=igraph::graph_attr(graph),data=igraph::as_data_frame(graph,what="both"));state$principal_coordinates<-dp;state$closest_vertex<-closest
 }
 stage<-"capture complete input projection";saveRDS(state,file.path(out,"input-state-before.rds"));write_json(projection,"input-projection.json")
 RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(as.integer(request$seed));rng$initial<-.Random.seed
 stage<-"original RANN nearest neighbors";nearest<-RANN::nn2(coordinates,coordinates,min(request$k+1L,n),searchtype="standard");rng$after_nn<-.Random.seed
 replay_nearest<-file.exists(file.path(root,"inputs/replay-nearest.rds"))
 if(replay_nearest)require(identical(nearest,readRDS(file.path(root,"inputs/replay-nearest.rds"))),"Complete original nearest-neighbor replay differs")
 saveRDS(nearest,file.path(out,"original-nearest.rds"));write_json(list(indices_zero_based=rows(nearest$nn.idx-1L),distance_tokens=token_rows(nearest$nn.dists)),"original-nearest.json")
 require(all(is.finite(nearest$nn.idx))&&all(nearest$nn.idx>=1L&nearest$nn.idx<=n)&&all(is.finite(nearest$nn.dists)),"Original RANN returned invalid indices/distances; unsafe downstream indexing is refused")
 native$original_ranking_zero_based<-rows(nearest$nn.idx-1L);write_json(native,"prepared-neighbors.json")
 require(request$engine %in% c("original","java"),"Select engine=original or java explicitly; no fallback is provided")
 if(request$engine=="original"){
  stage<-"original calculateLW";rng$before_calculate<-.Random.seed
  lw<-get("calculateLW",asNamespace("monocle3"))(cds,k=as.integer(request$k),neighbor_graph=request$neighbor_graph,reduction_method=slot,verbose=FALSE)
  rng$after_calculate<-.Random.seed;saveRDS(lw,file.path(out,"original-listw.rds"));require(identical(lw,readRDS(file.path(out,"original-listw.rds"))),"Original complete listw save/reload differs")
  if(file.exists(file.path(root,"inputs/replay-listw.rds")))require(identical(lw,readRDS(file.path(root,"inputs/replay-listw.rds"))),"Complete original listw replay differs")
 }
 rng$final<-.Random.seed
 if(file.exists(file.path(root,"inputs/replay-RNG.rds")))require(identical(rng,readRDS(file.path(root,"inputs/replay-RNG.rds"))),"Complete original RNG replay differs")
 after<-list(counts=SingleCellExperiment::counts(cds),row_data=SummarizedExperiment::rowData(cds),cell_data=SummarizedExperiment::colData(cds),metadata=S4Vectors::metadata(cds),cell_ids=colnames(cds),gene_ids=rownames(cds),coordinates=SingleCellExperiment::reducedDims(cds)[[slot]])
 if(request$neighbor_graph=="principal_graph"){
  graph<-cds@principal_graph[[slot]];after$principal_graph<-list(directed=igraph::is_directed(graph),attributes=igraph::graph_attr(graph),data=igraph::as_data_frame(graph,what="both"));after$principal_coordinates<-cds@principal_graph_aux[[slot]]$dp_mst;after$closest_vertex<-cds@principal_graph_aux[[slot]]$pr_graph_cell_proj_closest_vertex
 }
 saveRDS(after,file.path(out,"input-state-after.rds"));require(identical(state,after),"Complete declared scientific input projection changed")
 guards_after<-guard(profile);modules_after<-module_pins()
 require(identical(guards_before,guards_after)&&all(vapply(names(modules_before),function(name)identical(modules_before[[name]],modules_after[[name]]),logical(1))),"Original functions or loaded native modules changed")
 if(request$engine=="original")write_json(list(style=lw$style,region_ids=as.list(attr(lw,"region.id")),classes=as.list(class(lw)),neighbors_zero_based=lapply(lw$neighbours,function(row)if(identical(row,0L))list()else as.list(unname(row-1L))),weights_exact_tokens=lapply(lw$weights,tokens)),"original-listw.json")
 saveRDS(rng,file.path(out,"RNG.rds"));write_json(list(status="PASS_ORIGINAL_RANN_NEIGHBORS",profile=profile$profile,R=as.character(getRversion()),platform=R.version$platform,engine=request$engine,source_cds_cells=n,source_cds_genes=length(genes),reduction_slot=slot,neighbor_graph=request$neighbor_graph,k=request$k,seed=request$seed,guards=guards_before,modules_before=modules_before,modules_after=modules_after,input_projection_unchanged=TRUE,original_calculateLW_executed=request$engine=="original",original_listw_roundtrip_identical=request$engine=="original",replayed_complete_original_returns=replay_nearest,RNG_unchanged=identical(rng$initial,rng$final),warnings=as.list(warnings_seen),messages=as.list(messages_seen),scientific_test_admitted=FALSE),"report.json")
 writeLines(paste(c("PASS",stage,"Complete original neighbor construction returned",action),collapse="\t"),file.path(out,"terminal.tsv"));exit_code<-0L
},warning=function(w){warnings_seen<<-c(warnings_seen,conditionMessage(w));invokeRestart("muffleWarning")},message=function(m){messages_seen<<-c(messages_seen,conditionMessage(m));invokeRestart("muffleMessage")}),error=function(error){
 exit_code<<-if(inherits(error,"monocle_runtime_unavailable"))3L else 2L
 record<-list(status=if(exit_code==3L)"SKIP3"else"FAIL2",stage=stage,class=as.list(class(error)),message=conditionMessage(error),call=as.list(deparse(conditionCall(error))),warnings=as.list(warnings_seen),messages=as.list(messages_seen),weights_admitted=FALSE,scientific_test_admitted=FALSE)
 saveRDS(record,file.path(out,"original-error.rds"));saveRDS(rng,file.path(out,"RNG.rds"));writeLines(c(conditionMessage(error),action),file.path(out,"original-error.txt"))
 if(requireNamespace("jsonlite",quietly=TRUE))try(write_json(record,"error.json"),silent=TRUE)
 detail<-gsub("[\r\n\t]"," ",conditionMessage(error));writeLines(paste(c(if(exit_code==3L)"SKIP3"else"FAIL2",stage,detail,action),collapse="\t"),file.path(out,"terminal.tsv"))
})
quit(save="no",status=exit_code,runLast=FALSE)
