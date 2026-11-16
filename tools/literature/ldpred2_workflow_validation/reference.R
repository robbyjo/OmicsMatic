# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE)
stopifnot(length(args)==4L)
out <- normalizePath(args[1],mustWork=TRUE)
input <- normalizePath(args[2],mustWork=TRUE)
.libPaths(c(normalizePath(args[3],mustWork=TRUE),R.home("library")))
library(bigsnpr)
stopifnot(as.character(packageVersion("bigsnpr"))==args[4])
summary <- read.delim(file.path(input,"summary.tsv"),colClasses="character",na.strings=character(),check.names=FALSE)
df <- data.frame(beta=as.numeric(summary$beta),beta_se=as.numeric(summary$beta_se),n_eff=as.numeric(summary$n_eff))
m <- nrow(df)
p <- readBin(file.path(input,"p.bin"),integer(),m+1L,size=4L,endian="little")
n <- file.info(file.path(input,"x.bin"))$size/8L
i <- readBin(file.path(input,"i.bin"),integer(),n,size=4L,endian="little")
x <- readBin(file.path(input,"x.bin"),numeric(),n,size=8L,endian="little")
matrix <- new("dgCMatrix",Dim=as.integer(c(m,m)),p=p,i=i,x=x)
methods::validObject(matrix)
ld <- bigsparser::as_SFBM(matrix,backingfile=file.path(out,"original-ld"))
settings <- list(
 auto_mlefalse=list(use_MLE=FALSE,sparse=FALSE),
 auto_mletrue=list(use_MLE=TRUE,sparse=FALSE),
 auto_sparse=list(use_MLE=FALSE,sparse=TRUE),
 auto_options=list(use_MLE=TRUE,sparse=TRUE,allow_jump_sign=FALSE,shrink_corr=0.95,p_bounds=c(0.05,0.8),alpha_bounds=c(-1.2,0.2)))
if(args[4]=="1.12.2")settings$auto_options$p_bounds <- NULL
rows <- list()
for (name in names(settings)) {
 RNGkind("L'Ecuyer-CMRG","Inversion","Rejection");set.seed(4096L)
 writeLines(as.character(.Random.seed),file.path(out,paste0(name,"-rng-before.txt")))
 call <- c(list(corr=ld,df_beta=df,h2_init=0.3,vec_p_init=c(0.01,0.9,0.1),burn_in=10L,num_iter=20L,report_step=5L,ncores=1L),settings[[name]])
 tryCatch({
  answer <- do.call(snp_ldpred2_auto,call)
  saveRDS(answer,file.path(out,paste0(name,".rds")),compress=FALSE)
  for(j in seq_along(answer)) {
   model <- answer[[j]]
   rows[[length(rows)+1L]] <- data.frame(case=name,model=j,effect_finite=all(is.finite(model$beta_est)),h2=model$h2_est,p=model$p_est,alpha=model$alpha_est,path_alpha_missing=sum(is.na(model$path_alpha_est)),sample_columns=ncol(model$sample_beta),status="ORIGINAL_RETURNED")
  }
 },error=function(e){
  writeLines(conditionMessage(e),file.path(out,paste0(name,"-ERROR.txt")))
  rows[[length(rows)+1L]] <<- data.frame(case=name,model=NA_integer_,effect_finite=FALSE,h2=NA_real_,p=NA_real_,alpha=NA_real_,path_alpha_missing=NA_integer_,sample_columns=NA_integer_,status=paste("ORIGINAL_ERROR",conditionMessage(e)))
 })
 writeLines(as.character(.Random.seed),file.path(out,paste0(name,"-rng-after.txt")))
}
# Separate source controls include public inf/grid and original rejections.
grid <- read.delim(file.path(input,"models.tsv"),colClasses="character",check.names=FALSE)
grid_param <- data.frame(h2=as.numeric(grid$h2),p=as.numeric(grid$p),sparse=grid$sparse=="true")
for(name in c("inf","grid","grid_rejections","auto_rejections")) {
 RNGkind("L'Ecuyer-CMRG","Inversion","Rejection");set.seed(4096L)
 writeLines(as.character(.Random.seed),file.path(out,paste0(name,"-rng-before.txt")))
 null_df <- df;null_df$beta[] <- 0
 answer <- switch(name,
  inf=snp_ldpred2_inf(ld,df,h2=0.3),
  grid=snp_ldpred2_grid(ld,df,grid_param,burn_in=10L,num_iter=20L,ncores=1L),
  grid_rejections=snp_ldpred2_grid(ld,null_df,data.frame(h2=c(0.3,0.3),p=c(1,0.5),sparse=c(FALSE,TRUE)),burn_in=10L,num_iter=20L,ncores=1L),
  auto_rejections=snp_ldpred2_auto(ld,null_df,h2_init=0.3,vec_p_init=c(0.01,0.9,0.1),burn_in=10L,num_iter=20L,report_step=5L,use_MLE=FALSE,ncores=1L))
 saveRDS(answer,file.path(out,paste0(name,".rds")),compress=FALSE)
 writeLines(as.character(.Random.seed),file.path(out,paste0(name,"-rng-after.txt")))
}
write.table(do.call(rbind,rows),file.path(out,"outcomes.tsv"),quote=FALSE,sep="\t",row.names=FALSE,na="NA")
writeLines(capture.output(sessionInfo()),file.path(out,"sessionInfo.txt"))
cat("ACTUAL_ORIGINAL_AUTO_REFERENCE_ATTEMPTS_FINISHED\n")
