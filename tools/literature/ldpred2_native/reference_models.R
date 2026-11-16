# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Numerical answers come from the installed unchanged original bigsnpr package.
args <- commandArgs(TRUE)
stopifnot(length(args) == 2L, !dir.exists(args[1]))
out <- normalizePath(dirname(args[1]), mustWork=TRUE)
out <- file.path(out, basename(args[1])); dir.create(out)
original <- normalizePath(args[2], mustWork=TRUE)
stopifnot(as.character(packageVersion("bigsnpr")) == "1.12.2")
library(bigsnpr)
options(digits=17, scipen=999)
source_expressions <- parse(file.path(original,"R/LDpred2.R"), keep.source=FALSE)
checked <- character()
for (expression in source_expressions) {
  if (is.call(expression) && identical(expression[[1]], as.name("<-")) &&
      as.character(expression[[2]]) %in% c("snp_ldpred2_inf", "snp_ldpred2_grid", "snp_ldpred2_auto")) {
    name <- as.character(expression[[2]])
    fn <- eval(expression[[3]], envir=asNamespace("bigsnpr"))
    stopifnot(identical(deparse(body(fn),width.cutoff=500L), deparse(body(get(name,asNamespace("bigsnpr"))),width.cutoff=500L)))
    checked <- c(checked,name)
  }
}
stopifnot(length(checked)==3L)
writeLines(c(capture.output(sessionInfo()),"",paste("CRAN original R function bodies checked:",paste(checked,collapse=", "))),file.path(out,"sessionInfo.txt"))
writeLines(normalizePath(getLoadedDLLs()[["bigsnpr"]][["path"]]),file.path(out,"loaded-dll.txt"))
writeLines(rownames(installed.packages()),file.path(out,"installed-package-names.txt"))
write_vector <- function(values,path) writeLines(if(is.integer(values)) as.character(values) else sprintf("%.17g",values),path)
write_matrix <- function(x,path) writeLines(apply(x,1,function(row)paste(sprintf("%.17g",row),collapse="\t")),path)
cases <- expand.grid(rho=c(0,0.35,-0.65,0.95), p=c(1,0.1,0.001), sparse=c(FALSE,TRUE), KEEP.OUT.ATTRS=FALSE)
cases$name <- sprintf("model-%02d",seq_len(nrow(cases)))
cases$m <- 6L;cases$h2 <- 0.3;cases$burn_in <- 20L;cases$num_iter <- 35L
cases <- rbind(cases,data.frame(rho=0,p=1,sparse=FALSE,name="expected-divergence-zero-signal",m=6L,h2=0.3,burn_in=0L,num_iter=5L))
cases$coherent <- FALSE
coherent <- cases[seq_len(24L),]
coherent$name <- sprintf("coherent-%02d",seq_len(nrow(coherent)))
coherent$coherent <- TRUE
cases <- rbind(cases,coherent)
for (i in seq_len(nrow(cases))) {
  spec <- cases[i,]; folder <- file.path(out,spec$name);dir.create(folder)
  m <- spec$m
  corr <- spec$rho ^ abs(outer(seq_len(m),seq_len(m),"-"))
  sparse_corr <- as(as(as(corr,"dMatrix"),"symmetricMatrix"),"CsparseMatrix")
  ld <- bigsparser::as_SFBM(sparse_corr,backingfile=file.path(folder,"private-ld"))
  df <- data.frame(beta=c(0.2,-0.15,0.01,0.08,-0.04,0.12),beta_se=c(0.01,0.02,0.015,0.01,0.02,0.025),n_eff=c(10000,14000,18000,22000,12000,16000))
  if (spec$name=="expected-divergence-zero-signal") df$beta <- 0
  if (spec$coherent) {
    truth <- 0.03 * (if(spec$rho < 0) (-1)^(seq_len(m)-1L) else rep(1,m))
    df$beta <- as.vector(corr %*% truth)
    df$beta_se <- sqrt(1-df$beta^2)/sqrt(df$n_eff)
  }
  scale <- with(df,sqrt(n_eff*beta_se^2+beta^2))
  write_matrix(as.matrix(df),file.path(folder,"summary.tsv"));write_matrix(corr,file.path(folder,"correlation.tsv"))
  writeLines(c(paste0("heritability=",spec$h2),paste0("causalProportion=",spec$p),paste0("sparse=",tolower(as.character(spec$sparse))),paste0("burnIn=",spec$burn_in),paste0("iterations=",spec$num_iter)),file.path(folder,"parameters.properties"))
  RNGkind("L'Ecuyer-CMRG","Inversion","Rejection");set.seed(8192L+i)
  before <- .Random.seed;write_vector(before,file.path(folder,"seed-before.txt"))
  core <- bigsnpr:::ldpred2_gibbs_one(ld,df$beta/scale,df$n_eff,seq_len(m)-1L,spec$h2,spec$p,spec$sparse,spec$burn_in,spec$num_iter)
  after <- .Random.seed;write_vector(after,file.path(folder,"seed-after.txt"))
  write_vector(core*scale,file.path(folder,"grid-core-allele-effects.txt"))
  writeLines(if(anyNA(core))"DIVERGENCE" else "FINITE",file.path(folder,"grid-core-status.txt"))
  inf <- snp_ldpred2_inf(ld,df,spec$h2);write_vector(inf,file.path(folder,"inf-allele-effects.txt"))
  # Original public sampling entry, reseeded independently. Its sampled effects
  # are a separate output from the grid core's averaged posterior effects.
  assign(".Random.seed",before,envir=.GlobalEnv)
  samples <- snp_ldpred2_grid(ld,df,data.frame(p=spec$p,h2=spec$h2,sparse=spec$sparse),burn_in=spec$burn_in,num_iter=spec$num_iter,ncores=1,return_sampling_betas=TRUE)
  write_matrix(samples,file.path(folder,"public-allele-samples.tsv"))
  write_vector(.Random.seed,file.path(folder,"public-sampling-seed-after.txt"))
}
writeLines(cases$name,file.path(out,"case-index.txt"))
writeLines(c("PASS_UNCHANGED_ORIGINAL_BIGSNPR_1122_MODEL_REFERENCES",paste0("cases=",nrow(cases)),"function_bodies_modified=FALSE","native_code_replaced=FALSE","full_author_environment_reproduced=FALSE"),file.path(out,"COMPLETED.txt"))
