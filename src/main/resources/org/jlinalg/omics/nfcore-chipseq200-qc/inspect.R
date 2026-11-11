#!/usr/bin/env Rscript
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Read complete original saved QC objects. Never refit DESeq2 or recalculate VST/rlog.
args <- commandArgs(trailingOnly=TRUE)
if (length(args)!=6) stop("Require original DDS RData, original RDS, complete featureCounts, original size-factor RData, explicit transform and NEW output")
if (getRversion()!=numeric_version("4.0.3")) stop("Require exact original R4.0.3")
suppressPackageStartupMessages(library(DESeq2))
if (packageVersion("DESeq2")!=numeric_version("1.28.0")) stop("Require exact original DESeq2_1.28.0")
if (packageVersion("matrixStats")!=numeric_version("0.57.0")) stop("Require exact original matrixStats_0.57.0")
transform <- args[5]
if (!transform %in% c("vst","rlog")) stop("Declare actual original transform")
if (file.exists(args[6])) stop("QC worker output already exists; preserve it and select a NEW path")
dir.create(args[6],recursive=FALSE)
out <- normalizePath(args[6],mustWork=TRUE)
environment <- new.env(parent=baseenv())
loaded <- load(args[1],envir=environment)
if (!identical(loaded,"dds")) stop("Original source QC RData object family differs")
dds <- environment$dds
other <- readRDS(args[2])
if (!inherits(dds,"DESeqDataSet") || !inherits(other,"DESeqDataSet")) stop("Original QC objects have wrong classes")
if (!identical(deparse(design(dds)),"~1")) stop("Original QC design differs; no differential design inferred")
if (!identical(assayNames(dds),c("counts",transform))) stop("Complete original counts/transform assay family differs")
raw <- counts(dds,normalized=FALSE)
stored <- assay(dds,transform)
normalized <- counts(dds,normalized=TRUE)
if (nrow(raw)<2 || ncol(raw)<2 || !identical(dimnames(raw),dimnames(stored))) stop("Original PCA/count population is unassessable")
if (!identical(raw,counts(other,normalized=FALSE)) || !identical(stored,assay(other,transform)) || !identical(colData(dds),colData(other)) || !identical(sizeFactors(dds),sizeFactors(other))) stop("Original RData/RDS complete facts disagree")
if (any(!is.finite(raw)) || any(raw<0) || any(raw!=round(raw)) || any(!is.finite(stored)) || any(!is.finite(normalized))) stop("Original complete count/transform values are unassessable; no imputation")
factors <- sizeFactors(dds)
if (length(factors)!=ncol(raw) || any(!is.finite(factors)) || any(factors<=0)) stop("Original size factors are unassessable")
factorEnvironment <- new.env(parent=baseenv())
factorNames <- load(args[4],envir=factorEnvironment)
if (!identical(factorNames,"normFactors") || !identical(factorEnvironment$normFactors,factors)) stop("Original complete size-factor RData and DDS factors disagree")
feature <- read.delim(args[3],header=TRUE,row.names=NULL,skip=1,check.names=FALSE)
if (ncol(feature)!=ncol(raw)+6 || !identical(as.character(feature[[1]]),rownames(raw))) stop("Complete original featureCounts gene population differs")
sourceSamples <- gsub(".mLb.clN.sorted.bam","",names(feature)[7:ncol(feature)])
sourceSamples <- gsub("\\.$","",sourceSamples)
if (!identical(sourceSamples,colnames(raw)) || !identical(unname(as.matrix(feature[,7:ncol(feature),drop=FALSE])),unname(raw))) stop("Original featureCounts/sample/rounded count facts disagree")

# Export every cell with full source double precision, without rounded published tokens.
cell <- function(x) {
  x <- as.character(x)
  if (any(grepl("[\t\r\n]",x)) || any(is.na(x))) stop("Unrepresentable original TSV label; not repaired")
  x
}
number <- function(x) {
  if (any(!is.finite(x))) stop("Nonfinite QC value retained as failure, not replaced")
  sprintf("%.17g",x)
}
writeRows <- function(path,header,rows) {
  con <- file(file.path(out,path),open="wt",encoding="UTF-8")
  on.exit(close(con))
  writeLines(paste(cell(header),collapse="\t"),con)
  for (row in rows) writeLines(paste(cell(row),collapse="\t"),con)
}
matrixRows <- function(path,value) writeRows(path,c("gene",colnames(value)),lapply(seq_len(nrow(value)),function(i)c(rownames(value)[i],number(value[i,]))))
matrixRows("raw-counts.tsv",raw)
matrixRows("normalized-counts.tsv",normalized)
matrixRows("stored-transform.tsv",stored)
writeRows("samples.tsv",c("sample","size_factor"),lapply(seq_along(factors),function(i)c(colnames(raw)[i],number(factors[i]))))
writeRows("model-metadata.tsv",c("key","value"),list(c("schema_version","1"),c("R_version",as.character(getRversion())),c("DESeq2_version",as.character(packageVersion("DESeq2"))),c("matrixStats_version",as.character(packageVersion("matrixStats"))),c("design","~1"),c("transform",transform),c("genes",nrow(raw)),c("samples",ncol(raw)),c("nonlinear_transform_recomputed","FALSE"),c("negative_binomial_model_refitted","FALSE"),c("original_RData_RDS_facts_identical","TRUE")))

# The source writes PC coordinates after its ntop=c(500,Inf) loop: published table uses ALL genes.
# Recompute only original linear PCA from the unchanged stored transform, exporting its full basis.
pca <- stats::prcomp(t(stored),center=TRUE,scale.=FALSE)
if (any(!is.finite(pca$x)) || any(!is.finite(pca$sdev)) || sum(pca$sdev^2)<=0) stop("Original all-gene PCA is unassessable")
fraction <- pca$sdev^2/sum(pca$sdev^2)
writeRows("pca-scores.tsv",c("sample",colnames(pca$x)),lapply(seq_len(nrow(pca$x)),function(i)c(rownames(pca$x)[i],number(pca$x[i,]))))
writeRows("pca-spectrum.tsv",c("PC","sdev","fraction","source_rounded_percent"),lapply(seq_along(pca$sdev),function(i)c(paste0("PC",i),number(pca$sdev[i]),number(fraction[i]),as.character(round(100*fraction[i])))))
distances <- as.matrix(stats::dist(t(stored)))
writeRows("source-distances.tsv",c("sample",colnames(distances)),lapply(seq_len(nrow(distances)),function(i)c(rownames(distances)[i],number(distances[i,]))))

# Retain actual original function bodies for provider/source checks; this is not binary authentication.
dir.create(file.path(out,"source-functions"))
functions <- list(DESeq2_estimateSizeFactorsForMatrix=getFromNamespace("estimateSizeFactorsForMatrix","DESeq2"),DESeq2_varianceStabilizingTransformation=getFromNamespace("varianceStabilizingTransformation","DESeq2"),DESeq2_getVarianceStabilizedData=getFromNamespace("getVarianceStabilizedData","DESeq2"),stats_prcomp_default=getFromNamespace("prcomp.default","stats"),stats_scale_default=getFromNamespace("scale.default","base"),stats_dist=stats::dist)
for (name in names(functions)) dput(functions[[name]],file=file.path(out,"source-functions",paste0(name,".R")))
capture.output(sessionInfo(),file=file.path(out,"sessionInfo.txt"))
writeLines("COMPLETE_ORIGINAL_DESEQ128_QC_FACT_EXPORT_ONLY",file.path(out,"WORKER_COMPLETED.txt"))
