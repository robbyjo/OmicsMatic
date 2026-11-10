# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Read retained original pilot objects; no statistical refitting or source repair.
args <- commandArgs(TRUE);stopifnot(length(args)==4L)
.libPaths(c(args[[1L]],file.path(R.home(),"library")),include.site=FALSE)
stopifnot(getRversion()=="4.6.1",requireNamespace("GENESIS",quietly=TRUE),utils::packageDescription("GENESIS",fields="Version")=="2.42.0")
original <- args[[2L]];adapter <- args[[3L]];out <- args[[4L]];records <- list()
read_original <- function(name)readRDS(file.path(original,name,"original-result.rds"))
read_adapter <- function(name,stem)readRDS(file.path(adapter,name,"worker",paste0(stem,".rds")))
normalise <- function(x,sample_labels=FALSE) {
    if(inherits(x,"Matrix"))x <- as.matrix(x)
    if(sample_labels && is.matrix(x))dimnames(x) <- NULL
    if(sample_labels && is.data.frame(x))rownames(x) <- NULL
    x
}
cells <- function(x)if(is.data.frame(x))sum(vapply(x,length,integer(1L))) else length(x)
check <- function(name,a,b,sample_labels=FALSE) {
    a <- normalise(a,sample_labels);b <- normalise(b,sample_labels)
    # all.equal alone can hide the distinction between original R NA and NaN.
    if(is.numeric(a) && is.numeric(b))stopifnot(identical(is.na(a),is.na(b)),identical(is.nan(a),is.nan(b)))
    if(is.data.frame(a) && is.data.frame(b))for(i in seq_along(a))if(is.numeric(a[[i]]) && is.numeric(b[[i]]))stopifnot(identical(is.na(a[[i]]),is.na(b[[i]])),identical(is.nan(a[[i]]),is.nan(b[[i]])))
    equal <- all.equal(a,b,tolerance=1e-12,check.attributes=TRUE)
    if(!isTRUE(equal))stop(name,": ",paste(equal,collapse="; "))
    records[[length(records)+1L]] <<- data.frame(comparison=name,compared_cells=cells(a),sample_alias_attributes_omitted=sample_labels,tolerance="1e-12")
}
pc <- read_original("GENESIS-PC-AiR-three-PCs");got <- read_adapter("pcair","original-result")
for(field in c("vectors","values","varprop","unrels","rels","sample.id","nsamp","nsnps","kin.thresh","div.thresh","method"))check(paste0("PC-AiR/",field),pc[[field]],got[[field]])
rel <- read_original("GENESIS-PC-Relate");got <- read_adapter("pcrelate","original-result")
for(field in c("kinSelf","kinBtwn"))check(paste0("PC-Relate/",field),rel[[field]],got[[field]])
check("PC-Relate/full-derived-covariance",read_original("GENESIS-PCRelate-block-covariance"),read_adapter("pcrelate","original-derived-covariance"))
for(family in c("gaussian","binomial")) {
    reference_name <- paste0("GENESIS-null-",family,"-mixed-TRUE");model <- read_original(reference_name)
    for(mode in c("null-model","association")) {
        name <- paste0(mode,"-",family);got <- read_adapter(name,"original-null-model")
        for(field in c("varComp","varCompCov","fixef","betaCov","converged","zeroFLAG","RSS","niter","logLik","logLikR","AIC"))check(paste0(name,"/",field),model[[field]],got[[field]])
        # Null-only aliases are assigned in phenotype order. The source pilot
        # and association transport use FAM aliases; participant rows have the
        # same declared phenotype order and original FID/IID mappings are saved.
        for(field in c("fit","model.matrix","cholSigmaInv","W"))check(paste0(name,"/",field),model[[field]],got[[field]],sample_labels=TRUE)
        for(field in c("family","link","mixedmodel"))check(paste0(name,"/effective-family/",field),model$model$family[[field]],got$model$family[[field]])
        check(paste0(name,"/heterogeneous-residuals"),model$model$hetResid,got$model$hetResid)
        if(mode=="association")check(paste0(name,"/complete-variant-fields"),read_original(paste0(reference_name,"-Score")),read_adapter(name,"original-association"))
    }
}
utils::write.table(do.call(rbind,records),file.path(out,"complete-original-field-comparisons.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
cat("PASS_RETAINED_ORIGINAL_GENESIS_FIELDS_NO_REFITTING\n")
