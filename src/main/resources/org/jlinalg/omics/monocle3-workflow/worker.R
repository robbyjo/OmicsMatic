# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original current Monocle3 only. Closed Java-validated inputs; no installs or input RDS.
warnings_seen <- character();messages_seen <- character();guards <- character();cases <- list()
ready <- FALSE;status <- "SKIP3_DEPENDENCY_UNAVAILABLE";detail <- "";error_record <- NULL;current_stage <- "runtime"
action <- "Preserve this attempt. Read WARNINGS.md, worker-report.json, each source-status.json and stdout/stderr. Supply the exact SOURCE-PROFILE.json R/platform/dependencies in private libraries only where permitted; otherwise skip dependent analysis. No fallback edition or installation occurs. FAIL2 admits no batch result. Correct the named input/source/model/capacity cause and retry into NEW paths; never drop failed genes, repair IDs or replace Inf/NA to force success."
.libPaths(c(readLines("runtime-paths.txt",encoding="UTF-8",warn=FALSE),.Library),include.site=FALSE)
write_json <- function(x,path)jsonlite::write_json(x,path,pretty=TRUE,auto_unbox=TRUE,na="string",null="null",digits=NA)
value_kind <- function(x) {
    if(is.numeric(x)&&is.nan(x))"NaN" else if(is.na(x))"NA" else
    if(is.numeric(x)&&is.infinite(x)){if(x>0)"positive-infinity" else "negative-infinity"} else "value"
}
value_token <- function(x) {
    k <- value_kind(x)
    if(k=="NaN")"NaN" else if(k=="NA")"NA" else if(k=="positive-infinity")"Inf" else if(k=="negative-infinity")"-Inf" else
    if(is.double(x))sprintf("%.17g",x) else as.character(x)
}
atomic_table <- function(frame,path,row_ids=FALSE) {
    saveRDS(frame,paste0(path,".rds"))
    original_columns <- lapply(frame,function(x)list(class=as.list(class(x)),type=typeof(x),atomic_export=is.atomic(x)&&is.null(dim(x))))
    keep <- vapply(frame,function(x)is.atomic(x)&&is.null(dim(x)),logical(1))
    omitted <- names(frame)[!keep];frame <- as.data.frame(frame[,keep,drop=FALSE],check.names=FALSE)
    if(row_ids) {
        stopifnot(!"source_row_name" %in% names(frame))
        frame <- cbind(data.frame(source_row_name=rownames(frame),stringsAsFactors=FALSE),frame)
    }
    columns <- lapply(seq_along(frame),function(i){
        x <- frame[[i]]
        list(name=names(frame)[i],class=as.list(class(x)),type=typeof(x),tokens=as.list(vapply(seq_along(x),function(j)value_token(x[j]),character(1))),
            kinds=as.list(vapply(seq_along(x),function(j)value_kind(x[j]),character(1))))
    })
    encoded <- setNames(lapply(columns,function(x)unlist(x$tokens,use.names=FALSE)),names(frame))
    write.table(as.data.frame(encoded,check.names=FALSE),paste0(path,".tsv"),sep="\t",quote=TRUE,qmethod="double",row.names=FALSE,fileEncoding="UTF-8")
    write_json(list(rows=nrow(frame),columns=columns,original_columns=original_columns,omitted_from_TSV=as.list(omitted),complete_original_RDS=basename(paste0(path,".rds"))),paste0(path,".json"))
}
read_literal <- function(path) {
    # read.table strips whitespace from header fields even with quote="" and
    # check.names=FALSE. Parse the already Java-validated literal TSV directly.
    rows <- lapply(readLines(path,encoding="UTF-8",warn=FALSE),strsplit,split="\t",fixed=TRUE)
    rows <- lapply(rows,`[[`,1L);header <- rows[[1]];rows <- rows[-1L]
    columns <- lapply(seq_along(header),function(j)vapply(rows,function(row)row[[j]],character(1)))
    names(columns) <- header
    as.data.frame(columns,check.names=FALSE,stringsAsFactors=FALSE)
}
formula_text <- function(spec) {
    terms <- vapply(spec$terms,function(t)if(is.null(t$spline_df))t$column else paste0("splines::ns(",t$column,", df=",as.integer(t$spline_df),")"),character(1))
    paste0("~",paste(c(if(spec$intercept)"1" else "0",terms),collapse="+"))
}
prepared_graph <- function(cds,path) {
    aux <- cds@principal_graph_aux[["UMAP"]];coords <- SingleCellExperiment::reducedDims(cds)[["UMAP"]]
    principal <- aux$dp_mst;roots <- aux$root_pr_nodes;graph <- aux$pr_graph_cell_proj_tree
    vertices <- igraph::V(graph)$name;edges <- igraph::as_data_frame(graph,what="edges")
    has_weights <- "weight" %in% colnames(edges);if(!has_weights)edges$weight <- rep(1,nrow(edges))
    rows <- function(x)lapply(seq_len(nrow(x)),function(i)as.list(unname(x[i,])))
    tokens <- function(x)as.list(vapply(as.numeric(x),value_token,character(1)))
    original <- get("extract_general_graph_ordering",asNamespace("monocle3"))(cds,roots,verbose=FALSE,reduction_method="UMAP")
    selected <- get("find_nearest_vertex",asNamespace("monocle3"))(principal[,roots,drop=FALSE],t(coords))
    stopifnot(identical(as.character(original$sample_name),vertices),all(is.finite(coords)),all(is.finite(principal)))
    write_json(list(cell_ids=as.list(unname(colnames(cds))),cell_coordinates=rows(coords),principal_ids=as.list(unname(colnames(principal))),
        principal_coordinates=rows(t(principal)),roots=as.list(unname(roots)),graph_vertex_ids=as.list(unname(vertices)),
        edges=lapply(seq_len(nrow(edges)),function(i)list(from=match(edges$from[i],vertices)-1L,to=match(edges$to[i],vertices)-1L,weight=edges$weight[i])),
        expected_cell_pseudotime=tokens(monocle3::pseudotime(cds)),expected_graph_pseudotime=tokens(original$pseudo_time),
        expected_root_indices=as.list(as.integer(selected)-1L),source_had_weight_attribute=has_weights),path)
}
tryCatch(withCallingHandlers({
    if(!requireNamespace("jsonlite",quietly=TRUE))stop("Required jsonlite unavailable")
    p <- jsonlite::fromJSON("parameters.json",simplifyVector=FALSE)
    profile <- jsonlite::fromJSON("../source/SOURCE-PROFILE.json",simplifyVector=FALSE)
    Sys.setlocale("LC_CTYPE",if(.Platform$OS.type=="windows")"English_United States.utf8" else "C.UTF-8")
    if(!isTRUE(l10n_info()[["UTF-8"]])||Sys.getlocale("LC_COLLATE")!="C"||Sys.getlocale("LC_NUMERIC")!="C")stop("Required UTF-8 character / C collation and numeric locale unavailable")
    if(p$version!=profile$version)stop("Exact requested Monocle3 edition unavailable in this profile: ",p$version,". Available: ",profile$version,"; no fallback")
    if(as.character(getRversion())!=profile$r_version||R.version$platform!=profile$platform)stop("Exact declared R/platform unavailable: ",profile$r_version," / ",profile$platform)
    for(name in names(profile$required_packages)) {
        if(!requireNamespace(name,quietly=TRUE)||as.character(package_version(getNamespaceVersion(name)))!=profile$required_packages[[name]]||
           as.character(packageVersion(name))!=profile$required_packages[[name]])stop("Exact dependency unavailable: ",name," ",profile$required_packages[[name]])
    }
    suppressPackageStartupMessages(library(BPCells));suppressPackageStartupMessages(library(monocle3))
    ns <- asNamespace("monocle3");definitions <- list()
    for(file in list.files("../source/1.4.27/R",pattern="[.]R$",full.names=TRUE))for(expr in parse(file,keep.source=FALSE)){
        if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]]) %in% c("<-","=")&&is.symbol(expr[[2]])&&
           is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))definitions[[as.character(expr[[2]])]] <- expr[[3]]
    }
    for(name in unlist(profile$guarded_functions,use.names=FALSE)) {
        if(is.null(definitions[[name]])||!exists(name,ns,inherits=FALSE))stop("Original function unavailable: ",name)
        original <- eval(definitions[[name]],envir=ns);loaded <- get(name,ns,inherits=FALSE)
        if(!identical(body(original),body(loaded))||!identical(formals(original),formals(loaded)))stop("Original body/formals mismatch: ",name)
        guards <- c(guards,name)
    }
    RhpcBLASctl::blas_set_num_threads(1L);RhpcBLASctl::omp_set_num_threads(1L)
    ready <- TRUE;current_stage <- "input"
    raw <- read_literal("../inputs/counts.tsv");gene_ids <- raw[[1]];cell_ids <- names(raw)[-1]
    counts <- matrix(as.numeric(as.matrix(raw[,-1,drop=FALSE])),nrow=nrow(raw),dimnames=list(gene_ids,cell_ids))
    cm <- read_literal("../inputs/cell-metadata.tsv");gm <- read_literal("../inputs/gene-metadata.tsv")
    stopifnot(identical(cm[[1]],cell_ids),identical(gm[[1]],gene_ids),all(is.finite(counts)),all(counts>=0),all(colSums(counts)>0))
    rownames(cm) <- cm[[1]];cm <- cm[,-1,drop=FALSE];rownames(gm) <- gm[[1]];gm <- gm[,-1,drop=FALSE]
    for(name in names(p$metadata_types)) {
        spec <- p$metadata_types[[name]]
        cm[[name]] <- if(spec$type=="numeric")as.numeric(cm[[name]]) else factor(cm[[name]],levels=unlist(spec$levels,use.names=FALSE))
        stopifnot(!anyNA(cm[[name]]))
    }
    saveRDS(list(counts=counts,cell_metadata=cm,gene_metadata=gm),"input-transport.rds")
    write_json(list(cell_ids=as.list(cell_ids),gene_ids=as.list(gene_ids),genes=nrow(counts),cells=ncol(counts),
        metadata_columns=as.list(names(cm)),count_units="raw-integer-counts"),"input-ledger.json")
    dir.create("cases")
    for(a in p$analyses) {
        target <- file.path("cases",a$id);dir.create(target);cw <- character();cmessages <- character();failure <- NULL;current_stage <- "construct"
        returned <- tryCatch(withCallingHandlers({
            set.seed(as.integer(a$seed));write_json(list(seed=a$seed,RNGkind=as.list(RNGkind())),file.path(target,"rng.json"))
            cds <- monocle3::new_cell_data_set(counts,cell_metadata=cm,gene_metadata=gm)
            stopifnot(identical(colnames(cds),cell_ids),identical(rownames(cds),gene_ids))
            used <- list();infinite <- 0L
            if(!is.null(a$trajectory)) {
                t <- a$trajectory;pre <- t$preprocess;u <- t$umap;c <- t$cluster;g <- t$graph;current_stage <- "preprocess"
                cds <- monocle3::preprocess_cds(cds,method=pre$method,num_dim=as.integer(pre$num_dim),norm_method=pre$norm_method,pseudo_count=pre$pseudo_count,scaling=pre$scaling)
                current_stage <- "UMAP"
                cds <- monocle3::reduce_dimension(cds,reduction_method="UMAP",preprocess_method=pre$method,
                    umap.metric=u$metric,umap.n_neighbors=as.integer(u$n_neighbors),umap.min_dist=u$min_dist,umap.fast_sgd=u$fast_sgd,cores=1L)
                current_stage <- "cluster"
                cds <- monocle3::cluster_cells(cds,reduction_method="UMAP",cluster_method=c$method,k=as.integer(c$k),resolution=c$resolution,
                    random_seed=as.integer(a$seed),partition_qval=c$partition_qval,weight=c$weight)
                current_stage <- "learn_graph"
                cds <- monocle3::learn_graph(cds,use_partition=g$use_partition,close_loop=g$close_loop,
                    learn_graph_control=list(ncenter=as.integer(g$ncenter),minimal_branch_len=as.integer(g$minimal_branch_len),prune_graph=g$prune_graph,maxiter=as.integer(g$maxiter)))
                current_stage <- "order_cells"
                if(!is.null(t$root_cells))cds <- monocle3::order_cells(cds,root_cells=unlist(t$root_cells,use.names=FALSE)) else
                    cds <- monocle3::order_cells(cds,root_pr_nodes=unlist(t$root_pr_nodes,use.names=FALSE))
                pt <- monocle3::pseudotime(cds);stopifnot(identical(names(pt),cell_ids));infinite <- sum(is.infinite(pt))
                if(infinite>0)warning("INFINITE_PSEUDOTIME: ",infinite," cells unreachable from supplied roots; Inf retained")
                atomic_table(data.frame(cell_id=cell_ids,cluster=monocle3::clusters(cds)[cell_ids],partition=monocle3::partitions(cds)[cell_ids],pseudotime=pt),file.path(target,"cells"))
                prepared_graph(cds,file.path(target,"prepared-graph.json"));used$trajectory <- t
            }
            current_stage <- "full_save"
            monocle3::save_monocle_objects(cds,file.path(target,"cds"),hdf5_assays=FALSE,verbose=FALSE,archive_control=list(archive_type="none"))
            reloaded <- monocle3::load_monocle_objects(file.path(target,"cds"))
            stopifnot(identical(colnames(reloaded),cell_ids),identical(rownames(reloaded),gene_ids),identical(as.matrix(SummarizedExperiment::assay(reloaded,"counts")),counts))
            if(!is.null(a$trajectory))stopifnot(identical(monocle3::pseudotime(reloaded),monocle3::pseudotime(cds)),
                identical(SingleCellExperiment::reducedDims(reloaded)[["UMAP"]],SingleCellExperiment::reducedDims(cds)[["UMAP"]]),
                identical(monocle3::clusters(reloaded),monocle3::clusters(cds)),identical(monocle3::partitions(reloaded),monocle3::partitions(cds)))
            if(!is.null(a$fit_models)) {
                current_stage <- "fit_models";f <- a$fit_models;formula <- formula_text(f)
                models <- monocle3::fit_models(cds,model_formula_str=formula,expression_family=f$family,cores=1L,clean_model=FALSE,verbose=TRUE)
                atomic_table(models,file.path(target,"models"))
                coefficients <- monocle3::coefficient_table(models);atomic_table(coefficients,file.path(target,"coefficients"))
                used$fit_models <- list(family=f$family,formula=formula,cores=1L,clean_model=FALSE,verbose=TRUE,coefficient_q_adjustment="Original stats::p.adjust default Holm, grouped by model_component and term")
                stopifnot(identical(as.character(models$gene_id),gene_ids))
                if(anyNA(models$status)||any(models$status!="OK"))stop("Original gene model status is non-OK; complete models/coefficients retained, entire batch unadmitted")
            }
            if(!is.null(a$graph_test)) {
                current_stage <- "graph_test";gt <- a$graph_test
                tests <- monocle3::graph_test(cds,neighbor_graph=gt$neighbor_graph,k=as.integer(gt$k),alternative=gt$alternative,cores=1L,verbose=TRUE)
                atomic_table(tests,file.path(target,"graph-test"),row_ids=TRUE);used$graph_test <- gt
                stopifnot(identical(rownames(tests),gene_ids))
                if(anyNA(tests$status)||any(tests$status!="OK"))stop("Original graph-test gene status is non-OK; complete tests retained, entire batch unadmitted")
            }
            stopifnot(identical(colnames(cds),cell_ids),identical(rownames(cds),gene_ids))
            write_json(used,file.path(target,"original-arguments.json"))
            list(genes=length(gene_ids),cells=length(cell_ids),full_save_reload_identical=TRUE,infinite_pseudotimes=infinite)
        },warning=function(w){cw <<- c(cw,paste0(current_stage,": ",conditionMessage(w)));invokeRestart("muffleWarning")},
          message=function(m){cmessages <<- c(cmessages,paste0(current_stage,": ",conditionMessage(m)));invokeRestart("muffleMessage")} ),
            error=function(e){failure <<- list(stage=current_stage,class=as.list(class(e)),message=conditionMessage(e),call=paste(deparse(conditionCall(e)),collapse="\n"));NULL})
        entry <- list(id=a$id,status=if(is.null(failure))"original-returned" else "FAIL2_ORIGINAL_ERROR",original_error=failure,
            warnings=as.list(cw),messages=as.list(cmessages),population=returned,admission_authority="Java analysis-status.json; source return alone is not admission")
        write_json(entry,file.path(target,"source-status.json"));cases[[length(cases)+1L]] <- entry
        write_json(list(status="INCOMPLETE_NO_RESULT_ADMITTED",cases=cases),"worker-progress.json")
    }
    if(any(vapply(cases,function(x)x$status!="original-returned",logical(1))))stop("One or more original cases failed; no atomic batch result admitted")
    status <- "completed-with-warnings"
},warning=function(w){warnings_seen <<- c(warnings_seen,paste0(current_stage,": ",conditionMessage(w)));invokeRestart("muffleWarning")},
  message=function(m){messages_seen <<- c(messages_seen,conditionMessage(m));invokeRestart("muffleMessage")} ),
    error=function(e){detail <<- conditionMessage(e);error_record <<- list(class=as.list(class(e)),message=conditionMessage(e),call=paste(deparse(conditionCall(e)),collapse="\n"));if(ready)status <<- "FAIL2_ORIGINAL_ERROR"})
writeLines(capture.output(sessionInfo()),"session-info.txt",useBytes=TRUE)
if(requireNamespace("jsonlite",quietly=TRUE))write_json(list(status=status,detail=detail,original_error=error_record,warnings=as.list(warnings_seen),messages=as.list(messages_seen),cases=cases,
    guarded_functions=as.list(guards),locale=Sys.getlocale(),scope="Original Monocle3 1.4.27 raw-count workflow; no historical edition, paper-specific covariate graph_test, Monocle2 differentialGeneTest, activity inputs or biological certification"),"worker-report.json")
clean <- function(x)gsub("[\t\r\n]"," ",x)
writeLines(c("status\tdetail\trequired_action",paste(status,clean(detail),clean(action),sep="\t")),"worker-status.tsv",useBytes=TRUE)
quit(status=if(status=="completed-with-warnings")0L else if(ready)2L else 3L)
