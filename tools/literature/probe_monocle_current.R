# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original current-profile scientific readiness probe on synthetic raw counts.
args <- commandArgs(TRUE);stopifnot(length(args)>2L)
out <- args[[1]];.libPaths(c(args[-1],.Library),include.site=FALSE)
warnings_seen <- list();messages_seen <- list();stage <- "load"
status <- "FAIL2_ORIGINAL_MONOCLE3_PROBE";detail <- ""
checkpoint <- function(name, object) {saveRDS(object,file.path(out,paste0(name,".rds")));stage <<- name}
tryCatch(withCallingHandlers({
    # Explicitly load the declared matrix dependency before Monocle's S4 methods.
    suppressPackageStartupMessages(library(BPCells))
    suppressPackageStartupMessages(library(monocle3))
    stopifnot(identical(as.character(packageVersion("monocle3")),"1.4.27"))
    RhpcBLASctl::blas_set_num_threads(1L);RhpcBLASctl::omp_set_num_threads(1L)
    set.seed(20261107)
    progress <- seq(0,1,length.out=160L)
    lambda <- outer(seq_len(80L),progress,function(g,t) 5+6*(sin(g/5+3*t)+1))
    counts <- matrix(rpois(length(lambda),lambda=as.vector(lambda)),nrow=80L,
        dimnames=list(sprintf("gene-%03d",1:80),sprintf("cell-%03d",1:160)))
    cell_meta <- data.frame(progress=progress,batch=factor(rep(c("a","b"),80L)),row.names=colnames(counts))
    gene_meta <- data.frame(gene_short_name=rownames(counts),row.names=rownames(counts))
    checkpoint("input",list(counts=counts,cell_metadata=cell_meta,gene_metadata=gene_meta,seed=20261107L))
    cds <- monocle3::new_cell_data_set(counts,cell_metadata=cell_meta,gene_metadata=gene_meta)
    checkpoint("constructed",cds)
    cds <- monocle3::preprocess_cds(cds,method="PCA",num_dim=10L,norm_method="log",pseudo_count=1,scaling=TRUE)
    checkpoint("preprocessed",cds)
    cds <- monocle3::reduce_dimension(cds,reduction_method="UMAP",preprocess_method="PCA",
        umap.metric="cosine",umap.n_neighbors=15L,umap.min_dist=0.1,umap.fast_sgd=FALSE,cores=1L)
    checkpoint("embedded",cds)
    cds <- monocle3::cluster_cells(cds,reduction_method="UMAP",cluster_method="leiden",k=15L,
        resolution=0.01,random_seed=20261107L,partition_qval=0.05)
    checkpoint("clustered",cds)
    cds <- monocle3::learn_graph(cds,use_partition=FALSE,close_loop=FALSE,
        learn_graph_control=list(ncenter=12L,minimal_branch_len=3L,prune_graph=TRUE,maxiter=10L))
    checkpoint("graph-learned",cds)
    cds <- monocle3::order_cells(cds,root_cells="cell-001")
    checkpoint("ordered",cds)
    pseudotime <- monocle3::pseudotime(cds)
    write.table(data.frame(cell_id=names(pseudotime),pseudotime=pseudotime),file.path(out,"pseudotime.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
    models <- monocle3::fit_models(cds,model_formula_str="~progress+batch",expression_family="quasipoisson",cores=1L)
    checkpoint("models",models)
    coefficients <- monocle3::coefficient_table(models)
    write.table(coefficients,file.path(out,"coefficients.tsv"),sep="\t",quote=TRUE,row.names=FALSE,na="NA")
    checkpoint("coefficients",coefficients)
    graph_test <- monocle3::graph_test(cds,neighbor_graph="principal_graph",cores=1L)
    checkpoint("graph-test",graph_test)
    write.table(graph_test,file.path(out,"graph-test.tsv"),sep="\t",quote=TRUE,row.names=TRUE,na="NA")
    principal <- monocle3::principal_graph(cds)[["UMAP"]]
    jsonlite::write_json(list(genes=nrow(cds),cells=ncol(cds),principal_vertices=igraph::vcount(principal),
        principal_edges=igraph::ecount(principal),finite_pseudotimes=sum(is.finite(pseudotime)),
        infinite_pseudotimes=sum(is.infinite(pseudotime)),models=nrow(models),coefficient_rows=nrow(coefficients),
        graph_test_rows=nrow(graph_test)),file.path(out,"summary.json"),auto_unbox=TRUE,pretty=TRUE)
    status <- "COMPLETED_ORIGINAL_MONOCLE3_SYNTHETIC_READINESS_PROBE"
},warning=function(w){warnings_seen[[length(warnings_seen)+1L]] <<- list(stage=stage,message=conditionMessage(w));invokeRestart("muffleWarning")},
message=function(m){messages_seen[[length(messages_seen)+1L]] <<- list(stage=stage,message=conditionMessage(m));invokeRestart("muffleMessage")}),
error=function(e){detail <<- paste(conditionMessage(e),paste(deparse(conditionCall(e)),collapse=" "),sep=" | ")})
writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"))
jsonlite::write_json(list(status=status,stage=stage,detail=detail,warnings=warnings_seen,messages=messages_seen,
    original_version="1.4.27",synthetic_only=TRUE,Java_workflow_adapter_validated=FALSE,whole_paper_reproduced=FALSE,
    implementation_credit=FALSE),file.path(out,"RESULT.json"),auto_unbox=TRUE,pretty=TRUE,null="null")
quit(status=if(status=="COMPLETED_ORIGINAL_MONOCLE3_SYNTHETIC_READINESS_PROBE")0L else 2L)
