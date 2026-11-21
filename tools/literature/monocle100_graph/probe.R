# Source-bound original1.0.0 graph probe; no author pipeline source or monkeypatches.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==5L)
out<-args[1];.libPaths(args[2:3],include.site=FALSE);source_root<-args[4];input<-args[5]
stage<-"runtime";warnings_seen<-messages_seen<-character();rng<-list();exit_code<-2L
write_json<-function(value,name)jsonlite::write_json(value,file.path(out,name),pretty=TRUE,auto_unbox=TRUE,digits=NA,na="string",null="null")
tryCatch(withCallingHandlers({
 stopifnot(as.character(getRversion())=="4.3.3",as.character(packageVersion("monocle3"))=="1.0.0")
 library(monocle3);RhpcBLASctl::blas_set_num_threads(1L);RhpcBLASctl::omp_set_num_threads(1L)
 stage<-"original function guards";definitions<-list();selected<-character();ns<-asNamespace("monocle3")
 for(file in list.files(file.path(source_root,"R"),pattern="[.]R$",full.names=TRUE))for(expr in parse(file,keep.source=FALSE))if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]]) %in% c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function"))){name<-as.character(expr[[2]]);definitions[[name]]<-expr[[3]];if(basename(file)%in%c("learn_graph.R","order_cells.R"))selected<-c(selected,name)}
 selected<-unique(c(selected,"new_cell_data_set"))
 for(name in selected){original<-eval(definitions[[name]],ns);loaded<-get(name,ns,inherits=FALSE);stopifnot(identical(body(original),body(loaded),ignore.srcref=FALSE,ignore.bytecode=FALSE),identical(formals(original),formals(loaded),ignore.srcref=FALSE,ignore.bytecode=FALSE))}
 stage<-"complete synthetic prepared input";old<-readRDS(input);ids<-colnames(old)
 cds<-monocle3::new_cell_data_set(SingleCellExperiment::counts(old),cell_metadata=as.data.frame(SummarizedExperiment::colData(old)),gene_metadata=as.data.frame(SummarizedExperiment::rowData(old)))
 coordinates<-SingleCellExperiment::reducedDims(old)[["UMAP"]];SingleCellExperiment::reducedDims(cds)[["UMAP"]]<-coordinates
 clusters<-factor(rep(c("C1","C2","C3"),length.out=length(ids)),levels=c("C1","C2","C3"));names(clusters)<-ids
 partitions<-factor(rep("1",length(ids)),levels="1");names(partitions)<-ids
 cds@clusters[["UMAP"]]<-list(cluster_result=NULL,clusters=clusters,partitions=partitions)
 saveRDS(cds,file.path(out,"input-cds.rds"));params<-list(use_partition=TRUE,close_loop=FALSE,learn_graph_control=list(ncenter=5L,minimal_branch_len=2L,rann.k=6L,maxiter=10L))
 write_json(params,"parameters.json");RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(100022L);rng$initial<-.Random.seed
 stage<-"original learn_graph";cds<-monocle3::learn_graph(cds,use_partition=params$use_partition,close_loop=params$close_loop,learn_graph_control=params$learn_graph_control,verbose=TRUE);rng$after_graph<-.Random.seed
 saveRDS(cds,file.path(out,"learned-cds.rds"));aux<-cds@principal_graph_aux[["UMAP"]];graph<-cds@principal_graph[["UMAP"]]
 write_json(list(principal_ids=as.list(igraph::V(graph)$name),edges=igraph::as_data_frame(graph,what="edges"),objective_values=aux$objective_vals,principal_coordinates=unclass(aux$dp_mst)),"graph.json")
 # Roots are deliberately supplied synthetic IDs, not a biological inference.
 stage<-"original order_cells";root<-ids[1];ordered<-monocle3::order_cells(cds,root_cells=root,verbose=TRUE);rng$after_order<-.Random.seed
 saveRDS(ordered,file.path(out,"ordered-cds.rds"));saveRDS(rng,file.path(out,"RNG.rds"))
 for(name in selected){original<-eval(definitions[[name]],ns);loaded<-get(name,ns,inherits=FALSE);stopifnot(identical(body(original),body(loaded)),identical(formals(original),formals(loaded)))}
 write_json(list(status="PASS_ORIGINAL100_PREPARED_GRAPH_AND_EXPLICIT_ORDER_PROBE",cells=ncol(cds),genes=nrow(cds),principal_points=igraph::vcount(graph),guarded_functions=as.list(selected),warnings=as.list(warnings_seen),messages=as.list(messages_seen),versions=setNames(lapply(loadedNamespaces(),function(name)as.character(getNamespaceVersion(name))),loadedNamespaces()),graph_input_unchanged=identical(SingleCellExperiment::counts(old),SingleCellExperiment::counts(cds))&&identical(coordinates,SingleCellExperiment::reducedDims(cds)[["UMAP"]]),root_cell=root,root_direction_biologically_verified=FALSE,scientific_paper_result_admitted=FALSE),"RESULT.json");exit_code<-0L
},warning=function(w){warnings_seen<<-c(warnings_seen,conditionMessage(w));invokeRestart("muffleWarning")},message=function(m){messages_seen<<-c(messages_seen,conditionMessage(m));invokeRestart("muffleMessage")}),error=function(error){
 saveRDS(rng,file.path(out,"RNG.rds"));record<-list(status="FAIL2_ORIGINAL100_GRAPH_PROBE_NO_COMPLETE_PIPELINE_ADMITTED",stage=stage,class=as.list(class(error)),message=conditionMessage(error),call=as.list(deparse(conditionCall(error))),warnings=as.list(warnings_seen),messages=as.list(messages_seen),scientific_paper_result_admitted=FALSE)
 if(requireNamespace("jsonlite",quietly=TRUE))write_json(record,"RESULT.json");writeLines(c(conditionMessage(error),warnings_seen,messages_seen),file.path(out,"ERROR.txt"))
})
quit(save="no",status=exit_code,runLast=FALSE)
