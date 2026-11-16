# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Population pair-LD Gaussian summary simulation, not a sampled GWAS cohort.
args <- commandArgs(TRUE);stopifnot(length(args)==1L)
out <- normalizePath(args[1],mustWork=TRUE)
RNGkind("L'Ecuyer-CMRG","Inversion","Rejection");set.seed(20261116L)
writeLines(as.character(.Random.seed),file.path(out,"generation-rng-before.txt"))
m <- 1100000L;rho <- 0.25;target_h2 <- 0.3;sample_n <- 100000
odd <- seq.int(1L,m,2L);even <- odd+1L;partner <- seq_len(m);partner[odd] <- even;partner[even] <- odd
causal <- runif(m)<0.1
truth <- numeric(m);truth[causal] <- rnorm(sum(causal))
truth <- truth*sqrt(target_h2/sum(truth*(truth+rho*truth[partner])))
frequency <- runif(m,0.05,0.5);genotype_sd <- sqrt(2*frequency*(1-frequency))
u <- rnorm(m/2L);v <- rnorm(m/2L);noise <- numeric(m)
noise[odd] <- u;noise[even] <- rho*u+sqrt(1-rho^2)*v
marginal <- truth+rho*truth[partner]+noise/sqrt(sample_n)
stopifnot(all(abs(marginal)<1),all(is.finite(marginal)))
beta <- marginal/genotype_sd
beta_se <- sqrt((1-marginal^2)/sample_n)/genotype_sd
n_eff <- rep(sample_n,m)
for(name in c("truth","frequency","beta","beta_se","n_eff"))writeBin(get(name),file.path(out,paste0(name,".f64")),size=8L,endian="little")
labels <- data.frame(variant=paste0("v",seq.int(0L,m-1L)),effect_allele="A",other_allele="G")
write.table(labels,file.path(out,"variants.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
summary <- data.frame(labels,beta=sprintf("%.17g",beta),beta_se=sprintf("%.17g",beta_se),n_eff=sprintf("%.17g",n_eff))
write.table(summary,file.path(out,"summary.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
write.table(data.frame(key=c("simulation","variants","population_pair_rho","target_h2","realized_population_genetic_variance","causal_count","n_eff","data_seed","sampled_GWAS_cohort","real_biological_LD"),value=c("population_pair_LD_Gaussian_summary",m,rho,target_h2,sprintf("%.17g",sum(truth*(truth+rho*truth[partner]))),sum(causal),sample_n,20261116,FALSE,FALSE)),file.path(out,"GENERATION.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
writeLines(as.character(.Random.seed),file.path(out,"generation-rng-after.txt"))
cat("GENERATED_1100000_VARIANT_POPULATION_PAIR_LD_SUMMARY_SIMULATION\n")
