# Copyright (C) 2026 JLinAlg contributors
# SPDX-License-Identifier: GPL-3.0-or-later
# Explicit standalone vst versus Seurat SCTransform process; no downstream integration/DE model.
args <- commandArgs(trailingOnly = TRUE)
if (length(args) != 3L) stop("Require config.json, private R library, and output directory")
.libPaths(c(normalizePath(args[[2]], winslash = "/", mustWork = FALSE), .Library))
out <- normalizePath(args[[3]], winslash = "/", mustWork = TRUE)
if (!requireNamespace("jsonlite", quietly = TRUE)) {
  writeLines("jsonlite2.0.0 is unavailable in the selected private library",file.path(out,"dependency-error.txt"))
  quit(status=3L)
}
cfg <- jsonlite::fromJSON(args[[1]], simplifyVector=TRUE)
record <- list(status="failed", warnings=character())
code <- 2L
pins <- c(sctransform="0.4.3",jsonlite="2.0.0")
if (identical(cfg$engine,"seurat")) pins <- c(pins,Seurat="5.6.0",SeuratObject="5.4.0")
needs_glm <- identical(cfg$flavor,"v2") || cfg$fit_method %in% c("glmGamPoi","glmGamPoi_offset")
if (needs_glm && !isTRUE(cfg$allow_nb_fallback)) pins <- c(pins,glmGamPoi="1.24.0")
tryCatch(withCallingHandlers({
  for (p in names(pins)) {
    if (!requireNamespace(p,quietly=TRUE) || !identical(as.character(packageVersion(p)),unname(pins[p]))) {
      code <- 3L
      stop("Required pinned R package unavailable/changed: ",p," ",pins[p])
    }
  }
  record$versions <- as.list(vapply(names(pins),function(p) as.character(packageVersion(p)),character(1)))
  if (needs_glm && requireNamespace("glmGamPoi",quietly=TRUE) && !identical(as.character(packageVersion("glmGamPoi")),"1.24.0")) {
    code <- 3L;stop("Installed glmGamPoi differs from pinned1.24.0; allowing absence does not permit a silent version substitution")
  }
  record$r_version <- R.version.string
  record$library_paths <- .libPaths()
  if (needs_glm && !requireNamespace("glmGamPoi",quietly=TRUE)) record$warnings <- c(record$warnings,"EXPLICIT V2 FALLBACK: glmGamPoi is unavailable; the user allowed upstream nb_offset fitting. This is a different fitting engine and must be retained in provenance.")
  if (isTRUE(cfg$probe)) {
    record$status <- "available-not-yet-analysis-validated"
    code <- 0L
  } else {
    future::plan(future::sequential)
    x <- as(Matrix::readMM(file.path(cfg$counts,"matrix.mtx")),"CsparseMatrix")
    features <- read.delim(file.path(cfg$counts,"features.tsv"),header=FALSE,stringsAsFactors=FALSE,check.names=FALSE,fileEncoding="UTF-8",quote="",comment.char="",colClasses="character")[[1]]
    cells <- readLines(file.path(cfg$counts,"barcodes.tsv"),encoding="UTF-8")
    if (nrow(x)!=length(features)||ncol(x)!=length(cells)||any(!is.finite(x@x))||any(x@x<0)||any(Matrix::colSums(x)<=0)) stop("Sparse count/ID/positive-cell-total contract changed")
    # Seurat otherwise rewrites underscores/pipes and can collide distinct original IDs.
    alias <- if (any(grepl("[_|]",features))) sprintf("JLF%09d",seq_along(features)) else features
    names(features) <- alias
    rownames(x) <- alias;colnames(x) <- cells
    write.table(data.frame(internal_id=alias,feature_id=unname(features)),file.path(out,"feature-id-map.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
    if (!identical(alias,unname(features))) record$warnings <- c(record$warnings,"FEATURE ID MAP: internal stable aliases prevent Seurat feature-name rewriting/collisions. Exported matrices use original IDs; retained R objects use the explicit feature-id-map.tsv aliases.")
    meta <- data.frame(row.names=cells)
    if (!is.null(cfg$metadata)) {
      meta <- read.delim(cfg$metadata,stringsAsFactors=FALSE,check.names=FALSE,fileEncoding="UTF-8",na.strings=character(),quote="",comment.char="",colClasses="character")
      if (names(meta)[1]!="cell_id" || anyDuplicated(names(meta)) || anyDuplicated(meta[[1]]) || !setequal(meta[[1]],cells)) stop("Metadata must contain each selected cell_id exactly once, with unique columns")
      rownames(meta) <- meta[[1]];meta <- meta[cells,-1,drop=FALSE]
      if (any(!grepl("^[A-Za-z][A-Za-z0-9_.]*$",names(meta))) || any(names(meta) %in% c("umi","log_umi","gene","log_gene","umi_per_gene","log_umi_per_gene","nCount_RNA","nFeature_RNA","orig.ident"))) stop("Metadata names are invalid or override authoritative count attributes")
      for (p in names(meta)) {
        if (p %in% cfg$categorical) {
          if (any(meta[[p]]==""|meta[[p]] %in% c("NA","NaN","Inf","-Inf"))) stop("Missing categorical metadata: ",p)
          meta[[p]] <- factor(meta[[p]])
        } else {
          meta[[p]] <- suppressWarnings(as.numeric(meta[[p]]))
          if (any(!is.finite(meta[[p]]))) stop("Nonfinite or nonnumeric metadata: ",p,"; declare categorical variables explicitly")
        }
      }
    }
    if (identical(cfg$count_kind,"estimate")) record$warnings <- c(record$warnings,"FRACTIONAL/PROCESSED INPUT: user-declared nonnegative estimates are not raw UMI counts. Negative-binomial count assumptions and corrected-count interpretation require separate justification.")
    set.seed(cfg$seed)
    if (identical(cfg$engine,"vst")) {
      if (length(cfg$regress)) stop("Seurat post-fit vars.to.regress is distinct from vst latent variables")
      values <- list(umi=x,cell_attr=if(ncol(meta)) meta else NULL,latent_var=cfg$latent,method=cfg$fit_method,n_genes=cfg$fit_genes,n_cells=cfg$fit_cells,min_cells=cfg$min_cells,return_cell_attr=TRUE,return_gene_attr=TRUE,return_corrected_umi=isTRUE(cfg$corrected_counts),residual_type=cfg$residual_type,verbosity=1)
      if (identical(cfg$flavor,"v2")) values$vst.flavor <- "v2"
      if (!is.null(cfg$clip)) values$res_clip_range <- cfg$clip
      fit <- do.call(sctransform::vst,values)
      residual <- fit$y
      corrected <- fit$umi_corrected
      genes <- rownames(fit$model_pars_fit)
      model <- fit$model_pars_fit
      attributes <- fit$gene_attr
      effective <- fit$arguments
      saveRDS(fit,file.path(out,"vst-model.rds"))
      selected <- rownames(residual)
    } else if (identical(cfg$engine,"seurat")) {
      if (length(cfg$latent)!=1L || cfg$latent[[1]]!="log_umi") stop("Seurat engine uses its own depth model; standalone latent variables require engine vst")
      if (length(cfg$regress) && !all(cfg$regress %in% names(meta))) stop("Regression variables are absent from cell metadata")
      object <- Seurat::CreateSeuratObject(counts=x,meta.data=meta)
      values <- list(object=object,vst.flavor=cfg$flavor,ncells=cfg$fit_cells,variable.features.n=cfg$variable_features,vars.to.regress=if(length(cfg$regress)) cfg$regress else NULL,return.only.var.genes=isTRUE(cfg$variable_only),do.correct.umi=isTRUE(cfg$corrected_counts),conserve.memory=isTRUE(cfg$conserve_memory),seed.use=cfg$seed,verbose=FALSE,min_cells=cfg$min_cells)
      if (!is.null(cfg$clip)) values$clip.range <- cfg$clip
      object <- do.call(Seurat::SCTransform,values)
      residual <- SeuratObject::GetAssayData(object,assay="SCT",layer="scale.data")
      corrected <- if(isTRUE(cfg$corrected_counts)) SeuratObject::GetAssayData(object,assay="SCT",layer="counts") else NULL
      logdata <- SeuratObject::GetAssayData(object,assay="SCT",layer="data")
      if (length(logdata@x) && any(!is.finite(logdata@x))) stop("Nonfinite Seurat SCT log-expression layer")
      Matrix::writeMM(logdata,file.path(out,"sct-logdata.mtx"))
      writeLines(unname(features[rownames(logdata)]),file.path(out,"logdata-features.tsv"),useBytes=TRUE)
      model <- object[["SCT"]]@SCTModel.list[[1]]@feature.attributes
      attributes <- model
      effective <- object[["SCT"]]@SCTModel.list[[1]]@arguments
      genes <- rownames(model)
      selected <- SeuratObject::VariableFeatures(object[["SCT"]])
      saveRDS(object,file.path(out,"seurat-model.rds"))
    } else stop("Unknown explicit SCT engine")
    if (ncol(residual)!=length(cells)||!identical(colnames(residual),cells)||any(!is.finite(residual))) stop("Residual matrix changed cell identities/order or contains nonfinite values")
    if (any(!rownames(residual) %in% names(features))) stop("Residual matrix introduced unknown features")
    Matrix::writeMM(as(residual,"CsparseMatrix"),file.path(out,"residuals.mtx"))
    writeLines(unname(features[rownames(residual)]),file.path(out,"residual-features.tsv"),useBytes=TRUE)
    writeLines(cells,file.path(out,"barcodes.tsv"),useBytes=TRUE)
    if (!is.null(corrected)) {
      if(ncol(corrected)!=length(cells)||!identical(colnames(corrected),cells)||any(!is.finite(corrected@x))||any(corrected@x<0)) stop("Corrected counts changed cells or contain invalid values")
      Matrix::writeMM(corrected,file.path(out,"corrected-counts.mtx"))
      writeLines(unname(features[rownames(corrected)]),file.path(out,"corrected-features.tsv"),useBytes=TRUE)
    }
    write.table(data.frame(feature_id=unname(features[genes]),model,check.names=FALSE),file.path(out,"model-parameters.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
    write.table(data.frame(feature_id=unname(features[rownames(attributes)]),attributes,check.names=FALSE),file.path(out,"gene-attributes.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
    writeLines(unname(features[selected]),file.path(out,"selected-features.tsv"),useBytes=TRUE)
    effective <- lapply(effective,function(value) if(is.numeric(value)&&any(!is.finite(value))) as.character(value) else value)
    record$status <- "completed";record$engine <- cfg$engine;record$flavor <- cfg$flavor;record$input_features <- length(features);record$modeled_features <- length(genes);record$residual_features <- nrow(residual);record$cells <- length(cells);record$effective_arguments <- effective;record$config <- cfg
    code <- 0L
  }
},warning=function(w){record$warnings <<- c(record$warnings,conditionMessage(w));invokeRestart("muffleWarning")}),error=function(e){
  record$status <<- if (code==3L) "skipped-dependency-unavailable" else "failed"
  record$error <<- conditionMessage(e)
  message(conditionMessage(e))
})
jsonlite::write_json(record,file.path(out,"worker-status.json"),pretty=TRUE,auto_unbox=TRUE,na="null",null="null",digits=NA)
quit(status=code)
