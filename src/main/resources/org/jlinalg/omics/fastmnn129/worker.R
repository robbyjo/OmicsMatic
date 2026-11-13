# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Calls the original source-bound fastMNN; it does not implement a replacement algorithm.
argv <- commandArgs(TRUE);out <- argv[[1]];.libPaths(argv[-1])
options(warn=1,digits=17)
warnings <- character();messages <- character();fit <- NULL;phase <- "runtime"
write_json <- function(value,path) jsonlite::write_json(value,path,auto_unbox=TRUE,pretty=TRUE,null="null",digits=NA)
fail <- function(message,code=2L) {
 writeLines(message,file.path(out,"worker-error.txt"),useBytes=TRUE)
 writeLines(as.character(code),file.path(out,"worker-exit.txt"))
 if (requireNamespace("jsonlite",quietly=TRUE)) try(write_json(list(status=if(code==3L)"SKIP3_RUNTIME_UNAVAILABLE" else "FAIL2_ORIGINAL_FASTMNN",phase=phase,cause=message,warnings=as.list(warnings),messages=as.list(messages),result_admitted=FALSE),file.path(out,"worker-failure.json")),silent=TRUE)
 if (!is.null(fit)) try(saveRDS(fit,file.path(out,"unadmitted-fit.rds")),silent=TRUE)
 quit(save="no",status=code)
}
sha <- function(text,path) {
 con<-file(path,"wb");writeChar(enc2utf8(text),con,eos=NULL,useBytes=TRUE);close(con)
 result<-system2("/usr/bin/sha256sum",shQuote(path),stdout=TRUE)
 if(!is.null(attr(result,"status")))stop("Function SHA-256 helper failed")
 strsplit(result," ",fixed=TRUE)[[1]][1]
}
canonical <- function(f) paste(c(deparse(formals(f),width.cutoff=500L),deparse(body(f),width.cutoff=500L)),collapse="\n")
as_merge <- function(x,nested=FALSE) {
 if (is.null(x)) return(NULL)
 if (!is.list(x)) return(as.integer(x))
 if (!nested&&all(!vapply(x,is.list,logical(1)))) return(as.integer(unlist(x)))
 lapply(x,function(child)as_merge(child,TRUE))
}
as_weight_tree <- function(x) if(is.list(x))lapply(x,as_weight_tree) else as.integer(x)
as_weights <- function(x,batch_labels) {
 if(is.null(x)||is.logical(x))return(x)
 if(!is.null(x$tree))return(as_weight_tree(x$tree))
 if(!is.null(x$batch_weights))return(unlist(x$batch_weights[batch_labels],use.names=TRUE))
 unlist(x,use.names=FALSE)
}
state <- function(x) list(dim=dim(x),dimnames=dimnames(x),colData=SummarizedExperiment::colData(x),rowData=SummarizedExperiment::rowData(x),assays=SummarizedExperiment::assays(x),reducedDims=SingleCellExperiment::reducedDims(x),metadata=S4Vectors::metadata(x))
write_matrix <- function(m,ids,first,path) {
 con<-file(path,"wt",encoding="UTF-8");on.exit(close(con))
 labels<-colnames(m);if(is.null(labels))labels<-as.character(seq_len(ncol(m)))
 writeLines(paste(c(first,labels),collapse="\t"),con,useBytes=TRUE)
 for(i in seq_len(nrow(m))) {values<-as.numeric(m[i,]);if(any(!is.finite(values)))stop("Original complete factor contains nonfinite values; no result admitted");writeLines(paste(c(ids[[i]],sprintf("%.17g",values)),collapse="\t"),con,useBytes=TRUE)}
}
write_reconstruction <- function(m,features,cells,path,block_cells) {
 con<-file(path,"wt",encoding="UTF-8");on.exit(close(con));writeLines(paste(c("cell_id",features),collapse="\t"),con,useBytes=TRUE)
 for(first in seq.int(1L,length(cells),by=block_cells)) {
  indices<-seq.int(first,min(length(cells),first+block_cells-1L));block<-as.matrix(m[,indices,drop=FALSE])
  for(j in seq_along(indices)) {values<-block[,j];if(any(!is.finite(values)))stop("Original materialized reconstruction contains nonfinite values; no result admitted");writeLines(paste(c(cells[[indices[[j]]]],sprintf("%.17g",values)),collapse="\t"),con,useBytes=TRUE)}
 }
}

tryCatch(withCallingHandlers({
 if(!requireNamespace("jsonlite",quietly=TRUE))fail("Required jsonlite runtime is unavailable",3L)
 profile<-jsonlite::fromJSON(file.path(out,"source-profile.json"),simplifyVector=FALSE)
 if(as.character(getRversion())!=profile$r_version)fail("Exact profiled R version is required",3L)
 for(name in names(profile$package_versions)) {
  if(!requireNamespace(name,quietly=TRUE))fail(paste("Missing profiled R package",name),3L)
  if(as.character(packageVersion(name))!=profile$package_versions[[name]])fail(paste("Exact profiled package version is required",name),3L)
 }
 if(!file.exists("/usr/bin/sha256sum"))fail("Permitted function SHA-256 capability is unavailable",3L)
 dir.create(file.path(out,"function-checks"));ns<-asNamespace("batchelor")
 for(i in seq_along(profile$functions)) {
  name<-names(profile$functions)[[i]];f<-get(name,ns,inherits=FALSE)
  if(!is.function(f)||sha(canonical(f),file.path(out,"function-checks",paste0(i,".txt")))!=profile$functions[[name]])stop(paste("Original source-bound function differs",name))
 }
 config<-jsonlite::fromJSON(file.path(out,"worker-request.json"),simplifyVector=FALSE);p<-config$parameters
 phase<-"prepared-inputs"
 features<-read.delim(file.path(out,"inputs/features.tsv"),check.names=FALSE,quote="",comment.char="",colClasses="character",na.strings=NULL)
 cells<-read.delim(file.path(out,"inputs/cells.tsv"),check.names=FALSE,quote="",comment.char="",colClasses="character",na.strings=NULL)
 if(config$format=="TSV") {
  raw<-read.delim(file.path(out,"inputs/matrix.data"),check.names=FALSE,quote="",comment.char="",colClasses="character",na.strings=NULL)
  stopifnot(identical(names(raw)[-1],cells$cell_id),identical(raw[[1]],features$feature_id))
  x<-matrix(as.numeric(as.matrix(raw[-1])),nrow=nrow(features),ncol=nrow(cells))
 } else x<-Matrix::readMM(file.path(out,"inputs/matrix.data"))
 stopifnot(nrow(x)==nrow(features),ncol(x)==nrow(cells));dimnames(x)<-list(features$feature_id,cells$cell_id)
 batch<-factor(cells$batch,levels=unlist(config$batch_order,use.names=FALSE));stopifnot(!anyNA(batch),all(table(batch)>0L))
 subset<-if(is.null(p$subset_features))NULL else match(unlist(p$subset_features,use.names=FALSE),rownames(x))
 restricted<-if(is.null(p$restrict_cells))NULL else match(unlist(p$restrict_cells,use.names=FALSE),colnames(x))
 stopifnot(!anyNA(subset),!anyNA(restricted))
 bs<-if(p$pca_method=="EXACT")BiocSingular::ExactParam() else BiocSingular::IrlbaParam()
 bn<-if(p$neighbor_method=="EXHAUSTIVE")BiocNeighbors::ExhaustiveParam() else BiocNeighbors::KmknnParam()
 bp<-if(p$workers==1L)BiocParallel::SerialParam() else BiocParallel::MulticoreParam(workers=as.integer(p$workers))
 original_args<-list(x,batch=batch,k=as.integer(p$k),prop.k=p$prop_k,cos.norm=p$cosine_normalization,ndist=p$ndist,d=if(is.null(p$dimensions))NA_integer_ else as.integer(p$dimensions),deferred=p$deferred,weights=as_weights(p$weights,levels(batch)),get.variance=p$get_variance,merge.order=as_merge(p$merge_order),auto.merge=p$auto_merge,min.batch.skip=p$min_batch_skip,subset.row=subset,correct.all=p$correct_all,restrict=if(is.null(restricted))NULL else list(restricted),BSPARAM=bs,BNPARAM=bn,BPPARAM=bp)
 write_json(list(cell_ids=as.list(colnames(x)),feature_ids=as.list(rownames(x)),batch_order=config$batch_order,input_units=config$input_units,input_provenance=config$input_provenance,analysis_population=config$analysis_population,input_preparation_transform_applied=FALSE),file.path(out,"input-population.json"))
 if(is.list(p$weights)&&is.null(names(p$weights)))warnings<-c(warnings,"Adapter note: original1.29 single-matrix processing indexes numeric weights by batch names. An unnamed numeric weights vector remains unnamed and may create NA centers and fail. No names or fallback are supplied. If intended, explicitly choose named batch_weights in a NEW request.")
 if(p$auto_merge&&!is.null(p$merge_order))warnings<-c(warnings,"Original fastMNN ignores merge.order when auto.merge=TRUE; both supplied fields are retained.")
 phase<-"original-fastMNN";RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(as.integer(p$seed))
 fit<-do.call(batchelor::fastMNN,original_args)
 phase<-"complete-original-state"
 stopifnot(identical(colnames(fit),cells$cell_id))
 expected_features<-if(is.null(subset)||p$correct_all)features$feature_id else features$feature_id[subset]
 stopifnot(identical(rownames(fit),expected_features))
 corrected<-SingleCellExperiment::reducedDim(fit,"corrected");rotation<-SummarizedExperiment::rowData(fit)$rotation;reconstructed<-SummarizedExperiment::assay(fit,"reconstructed")
 stopifnot(nrow(corrected)==nrow(cells),nrow(rotation)==length(expected_features),nrow(reconstructed)==length(expected_features),ncol(reconstructed)==nrow(cells),ncol(rotation)==ncol(corrected))
 seed<-DelayedArray::seed(SummarizedExperiment::assay(fit,"reconstructed",withDimnames=FALSE))
 stopifnot(identical(class(seed)[[1]],"LowRankMatrixSeed"),identical(slotNames(seed),c("rotation","components")))
 seed_rotation<-methods::slot(seed,"rotation");seed_components<-methods::slot(seed,"components")
 stopifnot(identical(dim(seed_rotation),dim(rotation)),identical(dim(seed_components),dim(corrected)),identical(as.numeric(seed_rotation),as.numeric(rotation)),identical(as.numeric(seed_components),as.numeric(corrected)))
 saveRDS(fit,file.path(out,"original-fit.rds"));loaded<-readRDS(file.path(out,"original-fit.rds"));stopifnot(identical(state(fit),state(loaded)))
 saveRDS(S4Vectors::metadata(fit),file.path(out,"metadata.rds"));capture.output(str(S4Vectors::metadata(fit),max.level=7),file=file.path(out,"metadata.txt"))
 write_matrix(corrected,colnames(fit),"cell_id",file.path(out,"corrected.tsv"));write_matrix(rotation,rownames(fit),"feature_id",file.path(out,"rotation.tsv"))
 write_matrix(seed_components,colnames(fit),"cell_id",file.path(out,"assay-components.tsv"));write_matrix(seed_rotation,rownames(fit),"feature_id",file.path(out,"assay-rotation.tsv"))
 block_cells<-as.integer(min(256L,max(1L,floor(1000000/length(expected_features)))))
 if(config$reconstruction_audit=="ALL_VALUES")write_reconstruction(reconstructed,rownames(fit),colnames(fit),file.path(out,"reconstructed-cells.tsv"),block_cells)
 for(name in loadedNamespaces()) {
  if(is.null(profile$package_versions[[name]]))stop(paste("Unprofiled runtime namespace was loaded",name))
  if(as.character(packageVersion(name))!=profile$package_versions[[name]])stop("Loaded runtime version changed")
 }
 result<-list(schema_version=2L,status="PASS_ORIGINAL_FASTMNN129_COMPLETE_PREPARED_BATCH_CORRECTION",matrix_encoding="COMPLETE_TSV",cell_ids=as.list(colnames(fit)),feature_ids=as.list(rownames(fit)),dimensions=ncol(corrected),batches=as.list(as.character(SummarizedExperiment::colData(fit)$batch)),original_seed_class=class(seed)[[1]],original_seed_package=attr(class(seed),"package"),original_seed_slots=as.list(slotNames(seed)),reconstruction_audit=config$reconstruction_audit,reconstruction_orientation="CELLS_BY_FEATURES",reconstruction_block_cells=block_cells,reconstruction_preserved_lazily_in_RDS=TRUE,complete_required_model_roundtrip=TRUE,original_source_functions_checked=length(profile$functions),warnings=as.list(warnings),messages=as.list(messages),input_units=config$input_units,input_preparation_transform_applied=FALSE,paper_patch_recovered=FALSE,whole_goal_complete=FALSE)
 write_json(result,file.path(out,"worker-result.json"));capture.output(sessionInfo(),file=file.path(out,"sessionInfo.txt"));writeLines("0",file.path(out,"worker-exit.txt"))
},warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")},message=function(m){messages<<-c(messages,conditionMessage(m));invokeRestart("muffleMessage")}),error=function(e)fail(conditionMessage(e),2L))
