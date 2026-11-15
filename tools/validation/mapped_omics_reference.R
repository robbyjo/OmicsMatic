# Copyright (C) 2026 JLinAlg contributors
# SPDX-License-Identifier: GPL-3.0-or-later
# Independent base-R reference for mapped, complete-case cross-omics fits.
# Run from the repository root: Rscript tools/validation/mapped_omics_reference.R
set.seed(20261115)
destination <- "src/test/resources/mapped-omics"
dir.create(destination, recursive=TRUE, showWarnings=FALSE)
n <- 72L
ids <- paste0("S", seq_len(n))
age <- rnorm(n)
genes <- rbind(g1=rnorm(n), g2=rnorm(n))
protein <- rnorm(n)
isoforms <- rbind(tx1=.8*genes[1,]+rnorm(n), tx2=.5*genes[2,]+rnorm(n))
bmi <- 25 + .6*age + 1.5*genes[1,] + .4*isoforms[1,] + .3*protein + rnorm(n)
case <- rbinom(n, 1, plogis(-.2 + .3*age + .25*isoforms[1,] + .3*genes[1,]))
age[3] <- NA_real_
isoforms[1,7] <- NA_real_
isoforms[2,12] <- NA_real_
genes[1,11] <- NA_real_
genes[2,16] <- NA_real_
protein[19] <- NA_real_
pheno <- data.frame(id=ids, BMI=bmi, case=case, age=age)
colnames(genes) <- colnames(isoforms) <- ids
proteins <- matrix(protein, nrow=1, dimnames=list("p1", ids))
mapping <- data.frame(feature_id=rep(c("tx1","tx2"),each=2),
                      source=rep(c("genes","proteins"),2),
                      adjustment_id=c("g1","p1","g2","p1"))
write_tsv <- function(x, file) write.table(x, file.path(destination,file),
    sep="\t", quote=FALSE, row.names=FALSE, na="NA")
write_matrix <- function(x,file) write_tsv(data.frame(feature_id=rownames(x),x,check.names=FALSE),file)
# Deliberately permute every source independently and omit one gene sample.
write_tsv(pheno[rev(seq_len(n)),], "phenotype.tsv")
write_matrix(isoforms, "isoforms.tsv")
write_matrix(genes[,rev(setdiff(seq_len(n),5)),drop=FALSE], "genes.tsv")
write_matrix(proteins[,c(37:72,1:36),drop=FALSE], "proteins.tsv")
write_tsv(mapping,"mapping.tsv")
results <- list()
for (feature in rownames(isoforms)) {
    gene <- mapping$adjustment_id[mapping$feature_id==feature & mapping$source=="genes"]
    d <- data.frame(pheno, feature=isoforms[feature,], gene=genes[gene,], protein=protein)
    d$gene[5] <- NA_real_
    models <- list(
        ols=lm(BMI ~ age + gene + protein + feature, d),
        reverse=lm(feature ~ age + gene + protein + BMI, d),
        binomial=glm(case ~ age + gene + protein + feature, binomial(), d))
    for (name in names(models)) {
        fit <- models[[name]]
        coef <- coef(summary(fit))[if(name=="reverse") "BMI" else "feature",]
        results[[length(results)+1]] <- data.frame(model=name,feature_id=feature,
            n=nobs(fit), beta=coef[1], se=coef[2], statistic=coef[3],
            df=if(name=="binomial") Inf else df.residual(fit), p=coef[4])
    }
}
write_tsv(do.call(rbind,results),"expected-r.tsv")
writeLines(c(R.version.string, "Reference: stats::lm and stats::glm(binomial), full alternative models, default contrasts.",
    "Each model uses the intersection of response, tested feature, covariates and mapped adjustment observations."),
    file.path(destination,"reference.txt"))
