# Complete synthetic original-source controls; no author code or paper data executed.
args <- commandArgs(trailingOnly=TRUE);stopifnot(length(args)==4L)
out <- args[1];.libPaths(args[2:3],include.site=FALSE);source_root <- args[4]
stopifnot(as.character(getRversion())=="4.3.3")
suppressPackageStartupMessages(library(monocle3));stopifnot(as.character(packageVersion("monocle3"))=="1.0.0")
RhpcBLASctl::blas_set_num_threads(1L);RhpcBLASctl::omp_set_num_threads(1L);options(mc.cores=1L)
write_json <- function(x,path)jsonlite::write_json(x,path,pretty=TRUE,auto_unbox=TRUE,digits=NA,na="string",null="null")
rows <- function(x)lapply(seq_len(nrow(x)),function(i)as.list(unname(x[i,])))
definitions <- list();ns <- asNamespace("monocle3")
for(file in list.files(file.path(source_root,"R"),pattern="[.]R$",full.names=TRUE))for(expr in parse(file,keep.source=FALSE))
 if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]]) %in% c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))definitions[[as.character(expr[[2]])]] <- expr[[3]]
guard <- function(){
 for(name in c("calculateLW","jaccard_coeff","new_cell_data_set")){
  original <- eval(definitions[[name]],ns);loaded <- get(name,ns,inherits=FALSE)
  stopifnot(identical(body(original),body(loaded)),identical(formals(original),formals(loaded)))
 }
}
guard();set.seed(100021L);dir.create(file.path(out,"cases"));results <- list()
run <- function(label,n,k,mode,topology="path",ties=FALSE,point_count=6L,shuffled_map=FALSE){
 folder <- file.path(out,"cases",label);dir.create(folder);warnings_seen <- character();messages_seen <- character();error_seen <- NULL
 ids <- sprintf("cell-%03d",seq_len(n));genes <- paste0("gene-",1:3)
 counts <- matrix(as.numeric((seq_len(3L*n)*7L)%%19L+1L),nrow=3,dimnames=list(genes,ids))
 coordinates <- cbind(sin(seq_len(n)*1.13)+seq_len(n)*0.17,cos(seq_len(n)*0.71)+seq_len(n)*0.03);rownames(coordinates) <- ids
 if(ties)coordinates[seq_len(min(4L,n)),] <- matrix(rep(c(0.25,-0.5),each=min(4L,n)),ncol=2)
 point_labels <- paste0("Y_",seq_len(point_count));point_order <- c(3L,1L,4L,2L,if(point_count>4L)seq.int(5L,point_count) else integer())
 point_labels <- point_labels[point_order]
 membership <- rep(seq_len(4L),length.out=n)
 if(point_count>=10L)membership <- rep(c(1L,2L,10L),length.out=n)
 if(topology=="isolated")membership <- seq_len(n)
 if(topology=="same-point")membership <- rep(1L,n)
 if(topology=="pair-points"){
  membership <- rep(seq_len(n/2L),each=2L)
  coordinates <- cbind(rep(seq_len(n/2L)*100,each=2L)+rep(c(0,0.1),n/2L),seq_len(n)*0.001);rownames(coordinates) <- ids
 }
 if(topology %in% c("triple-points","six-points")){
  group_size <- if(topology=="triple-points")3L else 6L
  membership <- rep(seq_len(n/group_size),each=group_size)
  coordinates <- cbind(rep(seq_len(n/group_size)*100,each=group_size)+rep(seq_len(group_size)*0.1,n/group_size),seq_len(n)*0.001);rownames(coordinates) <- ids
 }
 if(topology=="mixed-points"){
  membership <- c(1L,1L,2L,2L,seq.int(3L,10L))
  coordinates <- cbind(c(0,0.01,10,10.01,seq(100,800,by=100)),seq_len(n)*0.000001);rownames(coordinates) <- ids
 }
 edge_indices <- if(topology=="disconnected")rbind(c(1L,2L),c(3L,4L)) else if(topology=="isolated")matrix(integer(),ncol=2) else cbind(seq_len(point_count-1L),seq.int(2L,point_count))
 if(topology=="pair-points")edge_indices <- matrix(integer(),ncol=2)
 if(topology %in% c("triple-points","six-points"))edge_indices <- matrix(integer(),ncol=2)
 if(topology=="mixed-points")edge_indices <- matrix(integer(),ncol=2)
 graph <- igraph::make_empty_graph(n=point_count,directed=FALSE);igraph::V(graph)$name <- paste0("Y_",seq_len(point_count))
 if(nrow(edge_indices))graph <- igraph::add_edges(graph,as.vector(t(edge_indices)))
 if(topology=="parallel-loop")graph <- igraph::add_edges(graph,c(1L,2L,1L,1L))
 dp <- matrix(seq_len(point_count*2L),nrow=2,dimnames=list(c("x","y"),point_labels))
 projection <- matrix(membership,ncol=1,dimnames=list(ids,"closest_vertex"))
 if(shuffled_map)projection <- rbind(projection[n:1,,drop=FALSE],extra_unselected_cell=1L)
 # Explicit sparse representation avoids the original dense-constructor R4.3 class error.
 # The earlier dense attempt remains retained; the original package is not patched.
 sparse_counts <- Matrix::Matrix(counts,sparse=TRUE)
 stopifnot(identical(unname(as.matrix(sparse_counts)),unname(counts)))
 cds <- monocle3::new_cell_data_set(sparse_counts,cell_metadata=data.frame(cell_id=ids,row.names=ids),gene_metadata=data.frame(gene_short_name=genes,row.names=genes))
 SingleCellExperiment::reducedDims(cds)[["UMAP"]] <- coordinates
 cds@principal_graph[["UMAP"]] <- graph
 cds@principal_graph_aux[["UMAP"]] <- list(dp_mst=dp,pr_graph_cell_proj_closest_vertex=projection)
 ranking <- RANN::nn2(coordinates,coordinates,min(k+1L,n),searchtype="standard")[[1]]
 edgelist <- igraph::as_edgelist(graph,names=TRUE)
 ordered_edges <- if(nrow(edgelist))cbind(match(edgelist[,1],point_labels)-1L,match(edgelist[,2],point_labels)-1L) else matrix(integer(),ncol=2)
 input <- list(mode=mode,cell_ids=as.list(ids),coordinates=rows(coordinates),k=k,original_ranking_zero_based=rows(ranking-1L),principal_points=point_count,principal_ids=as.list(point_labels),cell_to_principal_zero_based=as.list(membership-1L),principal_edges_zero_based=rows(ordered_edges),ties=ties,shuffled_projection_rows=shuffled_map)
 saveRDS(list(cds=cds,input=input),file.path(folder,"input.rds"));write_json(input,file.path(folder,"input.json"));before_rng <- .Random.seed
 lw <- tryCatch(withCallingHandlers(get("calculateLW",ns)(cds,k=k,neighbor_graph=mode,reduction_method="UMAP",verbose=FALSE),
  warning=function(w){warnings_seen <<- c(warnings_seen,conditionMessage(w));invokeRestart("muffleWarning")},
  message=function(m){messages_seen <<- c(messages_seen,conditionMessage(m));invokeRestart("muffleMessage")}),error=function(e){error_seen <<- list(class=as.list(class(e)),message=conditionMessage(e));NULL})
 stopifnot(identical(before_rng,.Random.seed));guard()
 reference <- if(is.null(error_seen)){
  saveRDS(lw,file.path(folder,"original-listw.rds"));restored <- readRDS(file.path(folder,"original-listw.rds"));stopifnot(identical(lw,restored))
  list(returned=TRUE,style=lw$style,region_ids=as.list(attr(lw,"region.id")),neighbors_zero_based=lapply(lw$neighbours,function(row)if(identical(row,0L))list() else as.list(unname(row-1L))),weights=lapply(lw$weights,function(row)as.list(unname(row))),classes=as.list(class(lw)),RNG_identical=TRUE,save_reload_identical=TRUE)
 }else list(returned=FALSE,error=error_seen,RNG_identical=TRUE)
 reference$warnings <- as.list(warnings_seen);reference$messages <- as.list(messages_seen)
 write_json(reference,file.path(folder,"reference.json"));saveRDS(list(before=before_rng,after=.Random.seed),file.path(folder,"RNG.rds"));results[[label]] <<- list(input=input,reference=reference)
 cat(label,if(reference$returned)"returned"else paste("error",error_seen$message),"\n")
}
for(n in c(12L,27L))for(k in c(2L,5L,40L))for(mode in c("knn","principal_graph"))run(paste(n,k,mode,sep="-"),n,k,mode)
for(topology in c("disconnected","parallel-loop"))for(k in c(2L,5L))run(paste(topology,k,sep="-"),24L,k,"principal_graph",topology=topology)
for(mode in c("knn","principal_graph"))for(k in c(2L,5L))run(paste("ties",mode,k,sep="-"),18L,k,mode,ties=TRUE)
run("noncontiguous-membership",24L,5L,"principal_graph",point_count=12L)
run("shuffled-projection-and-extra",24L,5L,"principal_graph",shuffled_map=TRUE)
run("all-isolated-original",6L,2L,"principal_graph",topology="isolated",point_count=6L)
run("singleton-neighbor-knn",12L,1L,"knn")
run("singleton-neighbor-principal",12L,1L,"principal_graph")
run("uniform-one-neighbor",12L,2L,"principal_graph",topology="pair-points",point_count=6L)
run("uniform-two-neighbors",12L,2L,"principal_graph",topology="same-point")
run("uniform-five-neighbors",12L,5L,"principal_graph",topology="same-point")
run("uniform-complete-neighbors",12L,40L,"principal_graph",topology="same-point")
run("uniform-two-with-multiple-levels",12L,2L,"principal_graph",topology="triple-points",point_count=4L)
run("uniform-five-with-multiple-levels",12L,5L,"principal_graph",topology="six-points",point_count=6L)
run("uniform-eleven-disconnected",24L,40L,"principal_graph",topology="disconnected")
run("mixed-isolated-and-single-neighbors",12L,2L,"principal_graph",topology="mixed-points",point_count=12L)
write_json(results,file.path(out,"cases.json"));writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"))
write_json(list(status="COMPLETED_ORIGINAL_MONOCLE100_WEIGHT_SOURCE_CONTROLS",cases=length(results),returned=sum(vapply(results,function(x)x$reference$returned,logical(1))),errors=sum(!vapply(results,function(x)x$reference$returned,logical(1))),original_functions_unchanged=TRUE,RNG_identical_all=TRUE,scientific_paper_execution=FALSE),file.path(out,"RESULT.json"))
