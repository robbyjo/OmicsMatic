# Copyright (C) 2026 JLinAlg contributors
# SPDX-License-Identifier: GPL-3.0-or-later
# Rscript --vanilla SCRIPT PRIVATE_LIBRARY FIXTURE_DIRECTORY NEW_OUTPUT_DIRECTORY
# Independent calls to the original packages, without the adapter worker.
args <- commandArgs(trailingOnly=TRUE)
stopifnot(length(args)==3L, !dir.exists(args[3]))
.libPaths(c(normalizePath(args[1]),.Library));dir.create(args[3],recursive=TRUE)
suppressPackageStartupMessages(library(Seurat))
stopifnot(packageVersion("sctransform")=="0.4.3",packageVersion("Seurat")=="5.6.0",packageVersion("glmGamPoi")=="1.24.0")
p <- args[2];out <- args[3]
x <- as(Matrix::readMM(file.path(p,"counts.mtx")),"CsparseMatrix")
rownames(x) <- read.delim(file.path(p,"features.tsv"),header=FALSE,quote="",colClasses="character")[[1]]
colnames(x) <- readLines(file.path(p,"barcodes.tsv"))
meta <- data.frame(row.names=colnames(x),S.Score=seq_len(ncol(x))/ncol(x),G2M.Score=sin(seq_len(ncol(x))))
for (flavor in c("v1","v2")) {
  set.seed(42)
  fit <- sctransform::vst(x,vst.flavor=if(flavor=="v1") NULL else "v2",n_genes=300,n_cells=200,return_cell_attr=TRUE,return_gene_attr=TRUE,verbosity=0)
  saveRDS(fit,file.path(out,paste0("vst-",flavor,".rds")))
  object <- CreateSeuratObject(x,meta.data=meta)
  object <- SCTransform(object,vst.flavor=flavor,ncells=200,variable.features.n=100,return.only.var.genes=TRUE,vars.to.regress=c("S.Score","G2M.Score"),seed.use=42,verbose=FALSE)
  saveRDS(object,file.path(out,paste0("seurat-",flavor,".rds")))
}
rows <- list();set.seed(61)
for (flavor in c("v1","v2")) {
  fit <- readRDS(file.path(out,paste0("vst-",flavor,".rds")))
  design <- sctransform:::prepare_regressor_data(fit,fit$cell_attr)
  mu <- exp(tcrossprod(fit$model_pars_fit[,-1,drop=FALSE],design));theta <- fit$model_pars_fit[,"theta"]
  minimum <- if(identical(fit$arguments$min_variance,"umi_median")) (sctransform:::get_nz_median2(x)/5)^2 else 0
  expected <- sctransform::get_residuals(fit,x,verbosity=0)
  ix <- sample.int(length(mu),200);coords <- arrayInd(ix,dim(mu))
  rows[[flavor]] <- data.frame(flavor=flavor,gene=rownames(mu)[coords[,1]],cell=colnames(mu)[coords[,2]],count=as.matrix(x)[ix],mean=mu[ix],theta=theta[coords[,1]],variance_floor=if(length(minimum)==1)minimum else minimum[coords[,1]],clip_low=fit$arguments$res_clip_range[1],clip_high=fit$arguments$res_clip_range[2],expected=expected[ix])
}
options(digits=17)
write.table(do.call(rbind,rows),file.path(out,"sctransform-residuals.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
rows <- list();set.seed(912)
for (flavor in c("v1","v2")) {
  object <- readRDS(file.path(out,paste0("seurat-",flavor,".rds")))
  y <- GetAssayData(object,assay="SCT",layer="scale.data")
  ix <- sample.int(length(y),200);coords <- arrayInd(ix,dim(y))
  rows[[flavor]] <- data.frame(flavor=flavor,gene=rownames(y)[coords[,1]],cell=colnames(y)[coords[,2]],expected=y[ix])
}
write.table(do.call(rbind,rows),file.path(out,"seurat-regressed-residuals.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
write.table(data.frame(cell_id=rownames(meta),meta),file.path(out,"phase-covariates.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
capture.output(sessionInfo(),file=file.path(out,"session-info.txt"))
