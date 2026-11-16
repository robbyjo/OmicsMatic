# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# An independent call to the unmodified selected original public function.
args <- commandArgs(TRUE);stopifnot(length(args)==3L)
input <- normalizePath(args[1],mustWork=TRUE);out <- normalizePath(args[2],mustWork=TRUE)
.libPaths(c(args[3],R.home("library")));library(bigsnpr)
stopifnot(as.character(packageVersion("bigsnpr"))=="1.12.4",normalizePath(find.package("bigsnpr"))==normalizePath(file.path(args[3],"bigsnpr")))
m <- 1100000L
p <- readBin(file.path(input,"p.bin"),integer(),m+1L,size=4L,endian="little")
i <- readBin(file.path(input,"i.bin"),integer(),2L*m,size=4L,endian="little")
x <- readBin(file.path(input,"x.bin"),numeric(),2L*m,size=8L,endian="little")
corr <- new("dgCMatrix",Dim=as.integer(c(m,m)),p=p,i=i,x=x)
methods::validObject(corr);stopifnot(all(Matrix::diag(corr)==1),Matrix::isSymmetric(corr,tol=0,checkDN=FALSE))
ld <- bigsparser::as_SFBM(corr,backingfile=file.path(out,"original-ld"))
read_double <- function(name)readBin(file.path(input,paste0(name,".f64")),numeric(),m,size=8L,endian="little")
df <- data.frame(beta=read_double("beta"),beta_se=read_double("beta_se"),n_eff=read_double("n_eff"))
RNGkind("L'Ecuyer-CMRG","Inversion","Rejection");set.seed(4096L)
writeLines(as.character(.Random.seed),file.path(out,"auto_defaults-rng-before.txt"))
# Deliberately use the original defaults: 500 burn-in, 200 retained, MLE TRUE,
# sparse FALSE, sign jumps TRUE, shrinkage 1, default bounds, zero sample columns.
answer <- snp_ldpred2_auto(ld,df,h2_init=0.3,vec_p_init=c(0.01,0.9,0.1),ncores=1L)
saveRDS(answer,file.path(out,"auto_defaults.rds"),compress=FALSE)
writeLines(as.character(.Random.seed),file.path(out,"auto_defaults-rng-after.txt"))
rows <- lapply(seq_along(answer),function(j){
 model <- answer[[j]]
 stopifnot(length(model$beta_est)==m,length(model$path_p_est)==700L,length(model$path_h2_est)==700L,length(model$path_alpha_est)==700L,nrow(model$sample_beta)==m,ncol(model$sample_beta)==0L)
 finite <- all(is.finite(c(model$beta_est,model$postp_est,model$corr_est,model$path_p_est,model$path_h2_est,model$path_alpha_est,model$p_est,model$h2_est,model$alpha_est,model$sample_beta@x)))
 data.frame(model=j,p_init=model$p_init,accepted=finite,h2=model$h2_est,p=model$p_est,alpha=model$alpha_est)
})
write.table(do.call(rbind,rows),file.path(out,"REFERENCE.tsv"),quote=FALSE,sep="\t",row.names=FALSE,na="NA")
writeLines(capture.output(sessionInfo()),file.path(out,"sessionInfo.txt"))
cat("ACTUAL_ORIGINAL_AUTO_DEFAULT_700_ITERATION_1100000_VARIANT_CALL_RETURNED\n")
