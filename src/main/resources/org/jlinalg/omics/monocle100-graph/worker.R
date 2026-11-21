# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Unchanged original graph learning/ordering; caller data are never evaluated as code.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==1L)
root<-normalizePath(args[1],mustWork=TRUE);out<-file.path(root,"worker");stage<-"runtime";exit_code<-2L
warnings_seen<-messages_seen<-character();rng<-list()
require<-function(ok,message)if(!isTRUE(ok))stop(message,call.=FALSE)
skip<-function(message)stop(structure(list(message=message,call=NULL),class=c("monocle_runtime_unavailable","error","condition")))
write_json<-function(value,name){path<-file.path(out,name);stopifnot(!file.exists(path));jsonlite::write_json(value,path,pretty=TRUE,auto_unbox=TRUE,digits=NA,na="string",null="null")}
tokens<-function(x)as.list(vapply(as.numeric(x),function(v)if(is.nan(v))"NaN"else if(is.na(v))"NA"else if(is.infinite(v))if(v>0)"Inf"else"-Inf"else sprintf("%.17g",v),character(1)))
rows<-function(x)lapply(seq_len(nrow(x)),function(i)tokens(x[i,]))
labels<-function(x,kind){require(is.character(x)&&length(x)>0L&&!anyNA(x)&&all(nzchar(x))&&!anyDuplicated(x)&&!any(grepl("[\x01-\x1f\x7f]",x)),paste("Require unique complete literal",kind));x}
sha<-function(x)digest::digest(serialize(x,NULL,version=2),algo="sha256",serialize=FALSE)
canonical<-function(x,depth=0L,path="model"){
 require(depth<64L,paste("Complete scientific model nesting exceeds64 at",path))
 if(is.null(x))return(NULL)
 if(inherits(x,"igraph"))return(list(type="igraph",directed=igraph::is_directed(x),graph_attributes=canonical(igraph::graph_attr(x),depth+1L,paste0(path,"/graph_attributes")),data=canonical(igraph::as_data_frame(x,what="both"),depth+1L,paste0(path,"/graph_data"))))
 require(!is.environment(x)&&!is.function(x)&&!typeof(x)%in%c("externalptr","weakref","language","symbol","pairlist"),paste0("Complete self-contained model capture refuses ",typeof(x)," at ",path,"; nothing is dropped"))
 map<-function(values,kind){result<-lapply(seq_along(values),function(i)canonical(values[[i]],depth+1L,paste0(path,kind,if(is.null(names(values)))i else names(values)[i])));names(result)<-names(values);result}
 attrs<-attributes(x)
 if(isS4(x)){
  slot_names<-slotNames(x);values<-lapply(slot_names,function(name)canonical(methods::slot(x,name),depth+1L,paste0(path,"@",name)));names(values)<-slot_names
  return(list(type=typeof(x),S4=TRUE,slot_names=as.list(slot_names),slots=values,attributes=map(attrs[!names(attrs)%in%slot_names],"@")))
 }
 if(is.list(x))return(list(type=typeof(x),data=map(unclass(x),"/"),attributes=map(attrs,"@")))
 attributes(x)<-NULL;list(type=typeof(x),data=x,attributes=map(attrs,"@"))
}
invariants<-function(cds){
 other_graph<-cds@principal_graph;other_graph[["UMAP"]]<-NULL;other_aux<-cds@principal_graph_aux;other_aux[["UMAP"]]<-NULL
 # Masking the selected slot can leave NULL versus character(0) empty names.
 # Normalize that empty projection holder only; no CDS field is changed.
 if(length(other_graph)==0L)names(other_graph)<-character(0)
 if(length(other_aux)==0L)names(other_aux)<-character(0)
 canonical(list(counts=SingleCellExperiment::counts(cds),row_data=SummarizedExperiment::rowData(cds),cell_data=SummarizedExperiment::colData(cds),metadata=S4Vectors::metadata(cds),cell_ids=colnames(cds),gene_ids=rownames(cds),reduced_dims=SingleCellExperiment::reducedDims(cds),clusters=cds@clusters,preprocess_aux=cds@preprocess_aux,reduce_dim_aux=cds@reduce_dim_aux,unselected_graphs=other_graph,unselected_aux=other_aux))
}
guard<-function(profile){
 for(name in names(profile$packages))if(!requireNamespace(name,quietly=TRUE)||as.character(getNamespaceVersion(name))!=profile$packages[[name]])skip(paste("Exact graph package/version required:",name,profile$packages[[name]]))
 definitions<-list();ns<-asNamespace("monocle3")
 for(name in names(profile$files))for(expr in parse(file.path(root,"source",name),keep.source=FALSE))if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]])%in%c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))definitions[[as.character(expr[[2]])]]<-expr[[3]]
 for(name in unlist(profile$guarded_functions,use.names=FALSE)){
  require(!is.null(definitions[[name]]),paste("Missing original graph definition:",name));original<-eval(definitions[[name]],ns);loaded<-get(name,ns,inherits=FALSE)
  require(identical(body(original),body(loaded),ignore.srcref=FALSE,ignore.bytecode=FALSE)&&identical(formals(original),formals(loaded),ignore.srcref=FALSE,ignore.bytecode=FALSE),paste("Changed original graph function:",name))
 }
 as.list(unlist(profile$guarded_functions,use.names=FALSE))
}
module_pins<-function(){result<-list();for(name in names(getLoadedDLLs())){path<-getLoadedDLLs()[[name]][["path"]];if(nzchar(path)&&file.exists(path))result[[name]]<-list(path=normalizePath(path),bytes=unname(file.info(path)$size),sha256=digest::digest(path,file=TRUE,algo="sha256"))};result}
math_pins<-function(){paths<-unique(c(La_library(),sessionInfo()$BLAS,sessionInfo()$LAPACK));result<-list();for(path in paths)if(!is.na(path)&&nzchar(path)&&file.exists(path)){actual<-normalizePath(path);result[[actual]]<-list(bytes=unname(file.info(actual)$size),sha256=digest::digest(actual,file=TRUE,algo="sha256"))};require(length(result)>0L,"Cannot capture the selected BLAS/LAPACK library");result}
action<-"SKIP3: supply the exact permitted Linux R4.3.3/original1.0.0 graph runtime or retain the dependent skip. FAIL2: no complete graph/pseudotime admitted; preserve raw/original/partial/error/RNG evidence, correct the named source/input/numerical/capacity/process cause and retry NEW output. No UMAT/UMAP rename, inferred roots, dropped cells, silent control changes or source patches."
tryCatch(withCallingHandlers({
 .libPaths(readLines(file.path(out,"runtime-paths.txt"),warn=FALSE),include.site=FALSE)
 runtime<-jsonlite::read_json(file.path(out,"runtime.json"),simplifyVector=FALSE);.libPaths(unlist(runtime$r_libraries),include.site=FALSE)
 if(as.character(getRversion())!="4.3.3"||R.version$platform!="x86_64-conda-linux-gnu")skip("Exact Linux x86_64 conda R4.3.3 graph profile required")
 profile<-jsonlite::read_json(file.path(root,"source/PROFILE.json"),simplifyVector=FALSE);require(identical(runtime$profile,profile$profile),"Runtime/graph profile differs")
 guards_before<-guard(profile);modules_before<-module_pins();math_before<-math_pins();RhpcBLASctl::blas_set_num_threads(1L);RhpcBLASctl::omp_set_num_threads(1L);options(mc.cores=1L)
 request<-jsonlite::read_json(file.path(out,"parameters.json"),simplifyVector=FALSE);learns<-request$operation!="ORDER";orders<-request$operation!="LEARN"
 stage<-"complete prepared CDS validation";cds<-readRDS(file.path(root,"inputs/source-cds.rds"))
 require(identical(as.character(class(cds)),"cell_data_set")&&identical(as.character(S4Vectors::metadata(cds)$cds_version),"1.0.0"),"Require a complete original1.0.0 cell_data_set; no wrapper, subclass or version update")
 methods::validObject(cds);ids<-labels(colnames(cds),"cell IDs");genes<-labels(rownames(cds),"gene IDs");n<-length(ids)
 require(n<=request$maximum_cells,"Complete cell population exceeds capacity; no prefix admitted")
 require(inherits(SingleCellExperiment::counts(cds),"dgCMatrix")&&identical(SummarizedExperiment::assayNames(cds),"counts"),"Require a counts-only complete in-memory dgCMatrix CDS; external/delayed backends need separate capture")
 require(identical(request$reduction_slot,"UMAP"),"Original graph functions hard-code UMAP; P021 UMAT is not renamed")
 coordinates<-SingleCellExperiment::reducedDims(cds)[["UMAP"]];require(is.matrix(coordinates)&&is.numeric(coordinates)&&nrow(coordinates)==n&&ncol(coordinates)>0L&&all(is.finite(coordinates))&&identical(rownames(coordinates),ids),"Complete finite literal UMAP in source cell order required")
 clusters<-cds@clusters[["UMAP"]]
 for(name in c("clusters","partitions"))require(is.factor(clusters[[name]])&&identical(names(clusters[[name]]),ids)&&!anyNA(clusters[[name]])&&all(nzchar(as.character(clusters[[name]]))),paste("Require complete named explicit source",name,"factor"))
 before<-invariants(cds);saveRDS(before,file.path(out,"input-invariants.rds"));saveRDS(canonical(cds),file.path(out,"input-scientific-state.rds"))
 projection<-list(cell_ids=as.list(ids),gene_ids=as.list(genes),count_dimensions=as.list(dim(SingleCellExperiment::counts(cds))),reduction_slot="UMAP",coordinates=rows(coordinates),cluster_values=as.list(as.character(clusters$clusters)),cluster_levels=as.list(levels(clusters$clusters)),partition_values=as.list(as.character(clusters$partitions)),partition_levels=as.list(levels(clusters$partitions)),invariants_sha256=sha(before))
 write_json(projection,"input-projection.json");RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(as.integer(request$seed));rng$initial<-.Random.seed
 convergence<-list(applicable=learns,policy=if(learns)request$convergence_policy else "caller-prepared-graph-not-refitted",all_converged=if(learns)FALSE else NULL,partitions=list())
 if(learns){
  stage<-"original learn_graph";control<-request$learn_graph_control;if(length(control)==0L)control<-list()
  cds<-monocle3::learn_graph(cds,use_partition=request$use_partition,close_loop=request$close_loop,learn_graph_control=control,verbose=TRUE);rng$after_graph<-.Random.seed
  saveRDS(cds,file.path(out,"learned-cds.rds"));objectives<-cds@principal_graph_aux[["UMAP"]]$objective_vals;require(is.list(objectives)&&length(objectives)>0L,"Original graph has no complete objective history")
  eps<-if(is.null(control$eps))1e-5 else control$eps
  for(i in seq_along(objectives)){
   values<-as.numeric(objectives[[i]]);require(length(values)>0L&&all(is.finite(values)),"Original graph objective contains nonfinite/empty values")
   relative<-if(length(values)>1L)abs(values[length(values)-1L]-values[length(values)])/abs(values[length(values)-1L])else NA_real_
   converged<-is.finite(relative)&&relative<eps
   convergence$partitions[[i]]<-list(iterations=length(values),objective_tokens=tokens(values),last_relative_change=if(is.finite(relative))sprintf("%.17g",relative)else NULL,eps_token=sprintf("%.17g",eps),converged=converged)
  }
  convergence$kmeans_nonzero_ifault_messages<-as.list(messages_seen[grepl("Warning: kmeans returned ifault",messages_seen,fixed=TRUE)])
  convergence$all_converged<-all(vapply(convergence$partitions,function(value)value$converged,logical(1)))&&length(convergence$kmeans_nonzero_ifault_messages)==0L
  write_json(convergence,"convergence.json")
  if(!convergence$all_converged&&request$convergence_policy=="require")stop("Original graph returned without the declared objective convergence; raw learned CDS retained, no graph admitted",call.=FALSE)
  if(!convergence$all_converged)warnings_seen<-c(warnings_seen,"RETAIN_ORIGINAL_NONCONVERGED_GRAPH: explicitly requested source return retained; convergence is not established")
 }else write_json(convergence,"convergence.json")
 graph<-cds@principal_graph[["UMAP"]];aux<-cds@principal_graph_aux[["UMAP"]]
 stage<-"complete original principal graph projection";require(inherits(graph,"igraph")&&!igraph::is_directed(graph),"Complete named undirected principal graph required");vertex_ids<-labels(igraph::V(graph)$name,"principal vertices")
 dp<-aux$dp_mst;require(is.matrix(dp)&&is.numeric(dp)&&all(is.finite(dp))&&nrow(dp)==ncol(coordinates),"Complete finite principal coordinates required");points<-labels(colnames(dp),"principal coordinate IDs");require(setequal(vertex_ids,points),"Complete graph/principal coordinate axes differ")
 edge_data<-igraph::as_data_frame(graph,what="edges");weighted<-"weight"%in%names(edge_data)
 edge_rows<-lapply(seq_len(nrow(edge_data)),function(i){row<-list(from=match(edge_data$from[i],points)-1L,to=match(edge_data$to[i],points)-1L);if(weighted)row$weight<-tokens(edge_data$weight[i])[[1]];row})
 principal<-list(principal_ids=as.list(points),principal_coordinates=rows(t(dp)),graph_vertex_ids=as.list(vertex_ids),edge_weight_mode=if(weighted)"weighted"else"unweighted",edges=edge_rows)
 saveRDS(graph,file.path(out,"original-principal-graph.rds"));write_json(principal,"principal-graph.json")
 if(orders){
  stage<-"original explicit-root order_cells"
  if(!is.null(request$root_pr_nodes))cds<-monocle3::order_cells(cds,root_pr_nodes=unlist(request$root_pr_nodes,use.names=FALSE),verbose=TRUE)else cds<-monocle3::order_cells(cds,root_cells=unlist(request$root_cells,use.names=FALSE),verbose=TRUE)
  rng$after_order<-.Random.seed;aux<-cds@principal_graph_aux[["UMAP"]];roots<-aux$root_pr_nodes
  ordering<-get("extract_general_graph_ordering",asNamespace("monocle3"))(cds,roots,verbose=FALSE,reduction_method="UMAP")
  nearest<-get("find_nearest_vertex",asNamespace("monocle3"))(dp[,roots,drop=FALSE],t(coordinates))
  projected<-aux$pr_graph_cell_proj_tree;require(inherits(projected,"igraph")&&!igraph::is_directed(projected),"Complete undirected projected graph required");vertices<-labels(igraph::V(projected)$name,"projected vertices");edges<-igraph::as_data_frame(projected,what="edges");has_weights<-"weight"%in%names(edges)
  require(identical(as.character(ordering$sample_name),vertices),"Original complete projected ordering axis differs")
  closest<-aux$pr_graph_cell_proj_closest_vertex;require(is.matrix(closest)||is.data.frame(closest),"Complete closest-vertex matrix/data frame required");require(ncol(closest)==1L&&all(ids%in%rownames(closest)),"Original closest-vertex axis is incomplete")
  projected_edges<-lapply(seq_len(nrow(edges)),function(i){value<-list(from=match(edges$from[i],vertices)-1L,to=match(edges$to[i],vertices)-1L);if(has_weights)value$weight<-tokens(edges$weight[i])[[1]];value})
  write_json(list(cell_ids=as.list(ids),cell_coordinates=rows(coordinates),principal_ids=as.list(points),principal_coordinates=rows(t(dp)),roots=as.list(roots),closest_vertex_source_labels=as.list(as.character(closest[ids,1])),graph_vertex_ids=as.list(vertices),edge_weight_mode=if(has_weights)"weighted"else"unweighted",edges=projected_edges,expected_cell_pseudotime=tokens(monocle3::pseudotime(cds)),expected_graph_pseudotime=tokens(ordering$pseudo_time),expected_root_indices=as.list(as.integer(nearest)-1L)),"prepared-ordering.json")
  saveRDS(ordering,file.path(out,"original-ordering.rds"));saveRDS(projected,file.path(out,"original-projected-graph.rds"))
 }
 rng$final<-.Random.seed;stage<-"complete output and original replay comparison"
 after<-invariants(cds);saveRDS(after,file.path(out,"output-invariants.rds"));require(identical(before,after),"Original call changed an invariant input axis, assay, metadata, embedding, factor or unselected model field")
 state<-canonical(cds);saveRDS(cds,file.path(out,"output-cds.rds"));require(identical(state,canonical(readRDS(file.path(out,"output-cds.rds")))),"Complete output scientific state did not survive RDS roundtrip")
 saveRDS(state,file.path(out,"output-scientific-state.rds"));saveRDS(rng,file.path(out,"RNG.rds"));replayed<-file.exists(file.path(root,"inputs/replay-output-cds.rds"))
 if(replayed){
  expected_state<-canonical(readRDS(file.path(root,"inputs/replay-output-cds.rds")));expected_rng<-readRDS(file.path(root,"inputs/replay-RNG.rds"))
  require(identical(state,expected_state)&&identical(rng,expected_rng),"Complete original output scientific state or RNG replay differs")
  if(orders){expected_ordering<-readRDS(file.path(root,"inputs/replay-ordering.rds"));require(identical(ordering,expected_ordering),"Complete original ordering replay differs")}
 }
 guards_after<-guard(profile);modules_after<-module_pins();math_after<-math_pins();require(identical(guards_before,guards_after)&&identical(math_before,math_after)&&all(vapply(names(modules_before),function(name)identical(modules_before[[name]],modules_after[[name]]),logical(1))),"Original graph functions or native/math modules changed")
 write_json(list(status="PASS_ORIGINAL100_COMPLETE_GRAPH_OPERATION",profile=profile$profile,R=as.character(getRversion()),platform=R.version$platform,operation=request$operation,seed=request$seed,cells=n,genes=length(genes),principal_points=length(points),graph_learned_by_this_run=learns,pseudotime_computed=orders,convergence=convergence,input_invariants_preserved=TRUE,input_invariants_sha256=sha(before),output_scientific_state_sha256=sha(state),output_RDS_scientific_roundtrip_identical=TRUE,complete_original_replay_identical=replayed,guarded_functions=guards_before,modules_before=modules_before,modules_after=modules_after,math_before=math_before,math_after=math_after,warnings=as.list(warnings_seen),messages=as.list(messages_seen),root_direction_biologically_verified=FALSE,scientific_test_admitted=FALSE),"report.json")
 writeLines(paste(c("PASS",stage,"Complete unchanged original graph operation returned",action),collapse="\t"),file.path(out,"terminal.tsv"));exit_code<-0L
},warning=function(w){warnings_seen<<-c(warnings_seen,conditionMessage(w));invokeRestart("muffleWarning")},message=function(m){messages_seen<<-c(messages_seen,conditionMessage(m));invokeRestart("muffleMessage")}),error=function(error){
 exit_code<<-if(inherits(error,"monocle_runtime_unavailable"))3L else 2L
 rng$seed_exists_at_error<-exists(".Random.seed",envir=.GlobalEnv,inherits=FALSE)
 if(rng$seed_exists_at_error)rng$at_error<-get(".Random.seed",envir=.GlobalEnv,inherits=FALSE)
 record<-list(status=if(exit_code==3L)"SKIP3"else"FAIL2",stage=stage,class=as.list(class(error)),message=conditionMessage(error),call=as.list(deparse(conditionCall(error))),warnings=as.list(warnings_seen),messages=as.list(messages_seen),graph_admitted=FALSE,pseudotime_admitted=FALSE,scientific_test_admitted=FALSE)
 saveRDS(record,file.path(out,"original-error.rds"));saveRDS(rng,file.path(out,"RNG.rds"));writeLines(c(conditionMessage(error),action),file.path(out,"original-error.txt"));if(requireNamespace("jsonlite",quietly=TRUE))try(write_json(record,"error.json"),silent=TRUE)
 writeLines(paste(c(if(exit_code==3L)"SKIP3"else"FAIL2",stage,gsub("[\r\n\t]"," ",conditionMessage(error)),action),collapse="\t"),file.path(out,"terminal.tsv"))
})
quit(save="no",status=exit_code,runLast=FALSE)
