# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# The original installed public bigsnpr functions execute without replacement.
args <- commandArgs(TRUE)
stopifnot(length(args)==2L)
root <- normalizePath(args[1],mustWork=TRUE)
runtime <- normalizePath(args[2],mustWork=TRUE)
read_tsv <- function(path) read.delim(path,header=TRUE,sep="\t",quote="",comment.char="",colClasses="character",na.strings=character(),check.names=FALSE,row.names=NULL,fill=FALSE,blank.lines.skip=FALSE,strip.white=FALSE,stringsAsFactors=FALSE,fileEncoding="UTF-8")
control <- read_tsv(file.path(root,"control.tsv"))
stopifnot(identical(names(control),c("key","value")),!anyDuplicated(control$key))
config <- setNames(as.list(control$value),control$key)
integer_value <- function(name) as.integer(config[[name]])
real_value <- function(name) as.numeric(config[[name]])
bool_value <- function(name) identical(config[[name]],"true")
runtime_lines <- readLines(file.path(runtime,"runtime.properties"),warn=FALSE,encoding="UTF-8")
runtime_lines <- runtime_lines[nzchar(runtime_lines)&!startsWith(runtime_lines,"#")]
parts <- regexpr("=",runtime_lines,fixed=TRUE);stopifnot(all(parts>1L))
runtime_config <- setNames(as.list(substring(runtime_lines,parts+1L)),substring(runtime_lines,1L,parts-1L))
.libPaths(c(runtime_config$r_library,R.home("library")))
stopifnot(as.character(packageVersion("bigsnpr"))==runtime_config$edition)
library(bigsnpr)
stopifnot(normalizePath(find.package("bigsnpr"),mustWork=TRUE)==normalizePath(file.path(runtime_config$r_library,"bigsnpr"),mustWork=TRUE))
expressions <- parse(file.path(runtime,"original-LDpred2.R"),keep.source=FALSE)
checked <- character()
for(expression in expressions) {
 if(is.call(expression)&&identical(expression[[1]],as.name("<-"))&&as.character(expression[[2]])%in%c("snp_ldpred2_inf","snp_ldpred2_grid","snp_ldpred2_auto")) {
  name <- as.character(expression[[2]])
  expected <- eval(expression[[3]],envir=asNamespace("bigsnpr"))
  actual <- get(name,asNamespace("bigsnpr"))
  stopifnot(identical(formals(expected),formals(actual)))
  stopifnot(identical(deparse(body(expected),width.cutoff=500L),deparse(body(actual),width.cutoff=500L)))
  checked <- c(checked,name)
 }
}
stopifnot(length(checked)==3L)
summary <- read_tsv(file.path(root,"inputs/summary.tsv"));variants <- read_tsv(file.path(root,"inputs/ld_variants.tsv"))
stopifnot(identical(names(summary),c("variant","effect_allele","other_allele","beta","beta_se","n_eff")),identical(names(variants),c("variant","effect_allele","other_allele")),identical(summary[1:3],variants),!anyDuplicated(variants$variant))
stopifnot(all(nzchar(as.matrix(variants))),!any(grepl("[[:space:][:cntrl:]]",as.matrix(variants))))
df <- data.frame(beta=as.numeric(summary$beta),beta_se=as.numeric(summary$beta_se),n_eff=as.numeric(summary$n_eff))
stopifnot(all(is.finite(as.matrix(df))),all(df$beta_se>0),all(df$n_eff>0))
m <- nrow(df);stopifnot(m>0L,m<=integer_value("max_variants"))
read_binary <- function(name,what,size,count) {
 path <- file.path(root,"inputs",name)
 stopifnot(file.info(path)$size==count*size)
 value <- readBin(path,what=what,n=count,size=size,endian="little")
 stopifnot(length(value)==count);value
}
entries <- file.info(file.path(root,"inputs/ld_values.bin"))$size/8
stopifnot(entries==floor(entries),entries<=integer_value("max_entries"))
p <- read_binary("ld_pointers.bin","integer",4L,m+1L)
i <- read_binary("ld_rows.bin","integer",4L,entries)
x <- read_binary("ld_values.bin","double",8L,entries)
stopifnot(!anyNA(p),!anyNA(i),all(is.finite(x)),all(abs(x)<=1),p[1]==0L,tail(p,1)==entries,all(diff(p)>=0L),all(i>=0L),all(i<m))
matrix <- new("dgCMatrix",Dim=as.integer(c(m,m)),p=p,i=i,x=x)
methods::validObject(matrix)
stopifnot(all(diag(matrix)==1),isSymmetric(matrix,tol=0,checkDN=FALSE))
ld <- bigsparser::as_SFBM(matrix,backingfile=file.path(root,"original-ld"))
rm(matrix,p,i,x);invisible(gc())
result_dir <- file.path(root,"results");stopifnot(!dir.exists(result_dir));dir.create(result_dir)
write_vector <- function(x,path)writeLines(as.character(x),path)
write_table <- function(x,path)write.table(x,path,sep="\t",quote=FALSE,row.names=FALSE,na="NA",fileEncoding="UTF-8")
write_weights <- function(beta,path) {
 stopifnot(length(beta)==m,all(is.finite(beta)))
 write_table(data.frame(variants,effect=sprintf("%.17g",beta),check.names=FALSE),path)
}
if(config$method=="inf") {
 model_ids <- "inf"
 answer <- snp_ldpred2_inf(ld,df,h2=real_value("heritability"))
 answers <- list(list(beta_est=answer))
} else {
 models <- read_tsv(file.path(root,"inputs/models.tsv"));stopifnot(nrow(models)>0L,nrow(models)<=integer_value("max_models"))
 model_ids <- models$model
 stopifnot(!anyDuplicated(model_ids),all(grepl("^[A-Za-z][A-Za-z0-9_-]{0,63}$",model_ids)))
 RNGkind("L'Ecuyer-CMRG","Inversion","Rejection");set.seed(integer_value("seed"))
 write_vector(.Random.seed,file.path(result_dir,"rng-before.txt"))
 if(config$method=="grid") {
  stopifnot(identical(names(models),c("model","h2","p","sparse")),all(models$sparse%in%c("true","false")))
  grid <- data.frame(h2=as.numeric(models$h2),p=as.numeric(models$p),sparse=models$sparse=="true")
  stopifnot(all(is.finite(grid$h2)),all(grid$h2>0),all(is.finite(grid$p)),all(grid$p>0&grid$p<=1))
  answer <- snp_ldpred2_grid(ld,df,grid,burn_in=integer_value("burn_in"),num_iter=integer_value("iterations"),ncores=1L)
  answers <- lapply(seq_len(ncol(answer)),function(j)list(beta_est=answer[,j]))
 } else {
  stopifnot(config$method=="auto",identical(names(models),c("model","p_init")))
  initial <- as.numeric(models$p_init);stopifnot(all(is.finite(initial)),all(initial>0&initial<=1))
  settings <- list(corr=ld,df_beta=df,h2_init=real_value("heritability"),vec_p_init=initial,burn_in=integer_value("burn_in"),num_iter=integer_value("iterations"),
      sparse=bool_value("sparse"),verbose=FALSE,report_step=integer_value("report_step"),allow_jump_sign=bool_value("allow_jump_sign"),shrink_corr=real_value("shrink_corr"),use_MLE=bool_value("use_mle"),
      alpha_bounds=c(real_value("alpha_min"),real_value("alpha_max")),ncores=1L)
  if(!is.null(config$p_min)) {
   stopifnot(runtime_config$edition=="1.12.4")
   settings$p_bounds <- c(real_value("p_min"),real_value("p_max"))
  }
  answers <- do.call(snp_ldpred2_auto,settings)
 }
 write_vector(.Random.seed,file.path(result_dir,"rng-after.txt"))
}
stopifnot(length(answers)==length(model_ids))
# All original fields and missing values are preserved, including rejected
# candidates. They are not repaired or averaged into an accepted score.
saveRDS(if(config$method=="auto")answers else answer,file.path(result_dir,"original-results.rds"),compress=FALSE)
outcomes <- data.frame(model=model_ids,status="accepted",reason="",stringsAsFactors=FALSE)
accepted <- 0L
for(j in seq_along(answers)) {
 folder <- file.path(result_dir,model_ids[j]);dir.create(folder)
 model <- answers[[j]]
 stopifnot(length(model$beta_est)==m)
 valid <- all(is.finite(model$beta_est))
 if(config$method=="auto") {
  stopifnot(all(c("postp_est","corr_est","sample_beta","path_p_est","path_h2_est","path_alpha_est","h2_est","p_est","alpha_est","h2_init","p_init")%in%names(model)))
  stopifnot(length(model$postp_est)==m,length(model$corr_est)==m,length(model$path_p_est)==integer_value("burn_in")+integer_value("iterations"),length(model$path_h2_est)==length(model$path_p_est),length(model$path_alpha_est)==length(model$path_p_est),nrow(model$sample_beta)==m)
  required <- c(model$postp_est,model$corr_est,model$path_p_est,model$path_h2_est,model$h2_est,model$p_est,model$sample_beta@x)
  if(bool_value("use_mle"))required <- c(required,model$path_alpha_est,model$alpha_est)
  if(!is.null(model$beta_est_sparse))required <- c(required,model$beta_est_sparse)
  valid <- valid&&all(is.finite(required))
  write_table(data.frame(iteration=seq_along(model$path_p_est),p=sprintf("%.17g",model$path_p_est),h2=sprintf("%.17g",model$path_h2_est),alpha=sprintf("%.17g",model$path_alpha_est)),file.path(folder,"paths.tsv"))
  write_table(data.frame(variant=variants$variant,postp=sprintf("%.17g",model$postp_est),corr=sprintf("%.17g",model$corr_est)),file.path(folder,"posterior.tsv"))
 }
 if(valid) {
  write_weights(model$beta_est,file.path(folder,"effects.tsv"));accepted <- accepted+1L
  if(!is.null(model$beta_est_sparse))write_weights(model$beta_est_sparse,file.path(folder,"sparse-effects.tsv"))
 } else {
  outcomes$status[j] <- "rejected";outcomes$reason[j] <- "original-nonfinite-estimates"
  writeLines("Original model has nonfinite estimates. No accepted weights; retain original-results.rds and diagnostics. Check source/model agreement before a new run; do not repair missing effects.",file.path(folder,"REFUSAL.txt"))
 }
}
write_table(outcomes,file.path(result_dir,"models.tsv"))
write_table(data.frame(key=c("variants","models","accepted","rejected","engine","method"),value=c(m,length(model_ids),accepted,length(model_ids)-accepted,"original",config$method)),file.path(root,"WORKER_DONE.tsv"))
writeLines(capture.output(sessionInfo()),file.path(root,"runtime-sessionInfo.txt"))
if(accepted==0L)stop("All original candidates were rejected; no accepted weights")
