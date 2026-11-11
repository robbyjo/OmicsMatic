# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Execute unchanged original helper functions/classes/accessors, not a fitted package workflow.
args <- commandArgs(TRUE)
stopifnot(length(args) >= 5L)
source_dir <- args[[1]]; plan_path <- args[[2]]; out <- args[[3]]
.libPaths(c(args[-(1:3)], .Library), include.site=FALSE)
suppressPackageStartupMessages(library(SingleCellExperiment))
suppressPackageStartupMessages(library(igraph))
stopifnot(requireNamespace("proxy", quietly=TRUE), requireNamespace("jsonlite", quietly=TRUE))
options(digits=17)
plan <- jsonlite::read_json(plan_path, simplifyVector=FALSE)
env <- new.env(parent=globalenv())
loaded <- list()
load_assignment <- function(file, name) {
    expressions <- parse(file.path(source_dir,"R",file), keep.source=FALSE)
    selected <- Filter(function(x) is.call(x) && identical(x[[1]],as.name("<-")) &&
        identical(x[[2]],as.name(name)) && is.call(x[[3]]) && identical(x[[3]][[1]],as.name("function")), expressions)
    stopifnot(length(selected)==1L)
    eval(selected[[1]],env)
    stopifnot(identical(body(env[[name]]),selected[[1]][[3]][[3]]),
              identical(formals(env[[name]]),as.pairlist(selected[[1]][[3]][[2]])))
    loaded[[name]] <<- capture.output(dput(selected[[1]]))
}
# Use the original class and getter declarations. No substitute cell_data_set class,
# inferred graph, counts preprocessing, Monocle namespace or full-package fit is claimed.
class_expr <- Filter(function(x) is.call(x) && identical(x[[1]],as.name("setClass")) &&
    identical(x[[2]],"cell_data_set"),parse(file.path(source_dir,"R/cell_data_set.R")))
stopifnot(length(class_expr)==1L);eval(class_expr[[1]],env)
generics <- parse(file.path(source_dir,"R/generics.R"))
getter <- Filter(function(x) is.call(x) && identical(x[[1]],as.name("setGeneric")) &&
    identical(x[[2]],"principal_graph"),generics)
method <- Filter(function(x) is.call(x) && identical(x[[1]],as.name("setMethod")) &&
    identical(x[[2]],"principal_graph") && identical(x[[3]],"cell_data_set"),generics)
stopifnot(length(getter)==1L,length(method)==1L)
eval(getter[[1]],env);eval(method[[1]],env)
load_assignment("learn_graph.R","find_nearest_vertex")
load_assignment("order_cells.R","extract_general_graph_ordering")
jsonlite::write_json(loaded,file.path(out,"loaded-original-function-AST.json"),auto_unbox=TRUE,pretty=TRUE)
saveRDS(list(class=class_expr, getter=getter, method=method), file.path(out,"loaded-original-class-accessor-AST.rds"))
matrix_rows <- function(rows) do.call(rbind,lapply(rows,function(x) as.numeric(unlist(x))))
strings <- function(values) vapply(as.numeric(values),function(x) {
    if(is.infinite(x)) if(x>0) "Inf" else "-Inf" else if(is.na(x)) "NA" else sprintf("%.17g",x)
},character(1))
cases <- list()
for(case in plan$cases) {
    folder <- file.path(out,case$id);dir.create(folder)
    warnings <- character()
    record <- tryCatch(withCallingHandlers({
        ids <- unlist(case$cell_ids,use.names=FALSE)
        vertex_ids <- unlist(case$graph_vertex_ids,use.names=FALSE)
        principal_ids <- unlist(case$principal_ids,use.names=FALSE)
        roots <- unlist(case$roots,use.names=FALSE)
        positions <- matrix_rows(case$cell_coordinates)
        principal_positions <- matrix_rows(case$principal_coordinates)
        rownames(positions) <- ids;rownames(principal_positions) <- principal_ids
        sce <- SingleCellExperiment(list(counts=matrix(1L,nrow=1L,ncol=length(ids),dimnames=list("fixture-gene",ids))))
        cds <- new("cell_data_set",sce)
        reducedDims(cds)[["UMAP"]] <- positions
        if(length(case$edges)) {
            frame <- do.call(rbind,lapply(case$edges,function(e) data.frame(from=vertex_ids[[e$from+1L]],
                to=vertex_ids[[e$to+1L]],weight=e$weight,stringsAsFactors=FALSE)))
        } else frame <- data.frame(from=character(),to=character(),weight=numeric())
        graph <- igraph::graph_from_data_frame(frame,directed=FALSE,vertices=data.frame(name=vertex_ids))
        principal <- igraph::make_empty_graph(n=length(principal_ids),directed=FALSE)
        igraph::V(principal)$name <- principal_ids
        cds@principal_graph[["UMAP"]] <- principal
        cds@principal_graph_aux[["UMAP"]] <- list(dp_mst=t(principal_positions),
            pr_graph_cell_proj_tree=graph)
        stopifnot(identical(colnames(cds),ids),inherits(cds@principal_graph[["UMAP"]],"igraph"),
            identical(dim(cds@principal_graph_aux[["UMAP"]]$dp_mst),rev(dim(principal_positions))))
        original <- env$extract_general_graph_ordering(cds,roots,verbose=FALSE,reduction_method="UMAP")
        nearest <- env$find_nearest_vertex(t(principal_positions)[,roots,drop=FALSE],t(positions))
        saveRDS(list(input=cds, roots=roots, original=original, nearest=nearest),file.path(folder,"original.rds"))
        stopifnot(identical(as.character(original$sample_name),vertex_ids))
        list(id=case$id,returned=TRUE,source_error=NULL,
            selected_cell_indices_zero_based=as.list(as.integer(nearest)-1L),
            graph_vertex_order=as.list(as.character(original$sample_name)),
            graph_pseudotime=as.list(strings(original$pseudo_time)),
            input_cell_pseudotime=as.list(strings(original$pseudo_time[match(ids,original$sample_name)])))
    },warning=function(w){warnings <<- c(warnings,conditionMessage(w));invokeRestart("muffleWarning")}),
    error=function(e) list(id=case$id,returned=FALSE,source_error=list(class=as.list(class(e)),message=conditionMessage(e),
        call=paste(deparse(conditionCall(e)),collapse=" "))))
    record$warnings <- as.list(warnings)
    jsonlite::write_json(record,file.path(folder,"original.json"),auto_unbox=TRUE,pretty=TRUE,null="null",na="null",digits=17)
    cases[[length(cases)+1L]] <- record
}
jsonlite::write_json(list(cases=cases,original_whole_package_loaded=FALSE,original_fit_performed=FALSE,
    original_class_accessors_and_helpers_executed=TRUE,r_version=R.version.string,
    dependency_versions=lapply(c("SingleCellExperiment","igraph","proxy","jsonlite"),function(p)list(package=p,version=unname(packageDescription(p,fields="Version"))))),
    file.path(out,"ORIGINAL.json"),auto_unbox=TRUE,pretty=TRUE,null="null",na="null",digits=17)
writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"))
cat("ORIGINAL_HELPER_CALLS",length(cases),"RETURNED",sum(vapply(cases,function(x)x$returned,logical(1))),"\n")
