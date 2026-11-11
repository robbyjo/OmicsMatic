# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Calls original namespaces and compiled models without replacing any function.
args <- commandArgs(TRUE)
library(jsonlite)
.libPaths(c(args[1], .libPaths()))
library(leafcutter)
library(rstan)
library(doMC)
registerDoMC(1)
stopifnot(normalizePath(getNamespaceInfo(asNamespace("leafcutter"), "path")) == normalizePath(file.path(args[1], "leafcutter")))
request <- fromJSON(args[2], simplifyVector=FALSE)
out <- args[3]
mat <- function(x) do.call(rbind, lapply(x, unlist))
save_json <- function(x, name) write_json(x, file.path(out, name), auto_unbox=TRUE, pretty=TRUE, digits=NA, null="null", na="string", matrix="rowmajor")
models <- get("stanmodels", asNamespace("leafcutter"))
completed <- list()
for (case in request$grids) {
  y <- mat(case$counts); x <- mat(case$design)
  model <- if (case$robust) models$dm_glm_robust else models$dm_glm_multi_conc
  data <- list(N=nrow(x), P=ncol(x), K=ncol(y), x=x, y=y, concShape=case$shape, concRate=case$rate, outlier_prior_a=case$outlier_a, outlier_prior_b=case$outlier_b)
  handle <- sampling(model, data=data, chains=0, iter=1, warmup=0, refresh=0, seed=case$seed)
  records <- list()
  for (point in case$points) {
    parameters <- list(beta_raw=mat(point$beta_raw), beta_scale=unlist(point$beta_scale), conc=unlist(point$concentration))
    if (case$robust) parameters$outlier_prob <- point$outlier_probability
    unconstrained <- unconstrain_pars(handle, parameters)
    value <- log_prob(handle, unconstrained, adjust_transform=FALSE)
    stopifnot(is.finite(value))
    records[[length(records)+1L]] <- list(id=point$id, parameters=parameters, objective=as.numeric(value), unconstrained=unconstrained, jacobian_adjustment=FALSE)
  }
  save_json(list(id=case$id, original_model=if (case$robust) "dm_glm_robust" else "dm_glm_multi_conc", data=data, points=records), paste0(case$id, ".json"))
  completed[[length(completed)+1L]] <- case$id
}
for (case in request$fits) {
  y <- mat(case$counts); full <- mat(case$full); null <- mat(case$null)
  colnames(y) <- unlist(case$introns); colnames(full) <- unlist(case$full_names); colnames(null) <- unlist(case$null_names)
  result <- dirichlet_multinomial_anova_mc(full, null, y, robust=case$robust, init=case$init, seed=case$seed, iter=2000)
  saveRDS(result, file.path(out, paste0(case$id, ".rds")))
  stopifnot(result$fit_null$return_code==0, result$fit_full$return_code==0, is.finite(result$loglr), is.finite(result$lrtp), result$lrtp>=0, result$lrtp<=1)
  effects <- leaf_cutter_effect_sizes(list(cluster=result))
  save_json(list(id=case$id, counts=y, full=full, null=null, introns=case$introns, robust=case$robust, fit=result, effects=effects), paste0(case$id, ".json"))
  completed[[length(completed)+1L]] <- case$id
}
for (case in request$differential) {
  counts <- mat(case$counts); rownames(counts) <- unlist(case$introns); colnames(counts) <- unlist(case$samples)
  x <- unlist(case$group); confounders <- if (is.null(case$confounders)) NULL else mat(case$confounders)
  result <- differential_splicing(counts, x, confounders=confounders, max_cluster_size=3, min_samples_per_intron=5, min_samples_per_group=4, min_coverage=20, timeout=60, robust=FALSE, debug=TRUE, seed=case$seed, iter=2000)
  saveRDS(result, file.path(out, paste0(case$id, ".rds")))
  table <- cluster_results_table(result); effects <- leaf_cutter_effect_sizes(result)
  expected <- unlist(case$expected_statuses)
  # table() follows the declared R locale, including punctuation collation.
  # Retain its actual order and check every named source disposition by identity.
  stopifnot(setequal(as.character(table$cluster), names(expected)), identical(as.character(table$status), unname(expected[as.character(table$cluster)])))
  exported <- list()
  for (cluster in names(result)) {
    fit <- result[[cluster]]
    if (is.character(fit)) {exported[[cluster]] <- list(status=fit); next}
    stopifnot(!inherits(fit, "error"), fit$fit_null$return_code==0, fit$fit_full$return_code==0)
    # Retain the exact source selection and fitted design for independent audit.
    members <- which(paste(get_intron_meta(rownames(counts))$chr, get_intron_meta(rownames(counts))$clu, sep=":") == cluster)
    y <- t(counts[members,,drop=FALSE]); totals <- rowSums(y); use <- totals>0
    y <- y[use,,drop=FALSE]; keep <- colSums(y>0)>=5; y <- y[,keep,drop=FALSE]
    full <- cbind(1,x[use]); null <- full[,1,drop=FALSE]
    if (!is.null(confounders)) {
      ch <- confounders[use,,drop=FALSE]; ch <- ch[,apply(ch,2,sd)>0,drop=FALSE]
      full <- cbind(full,ch); null <- cbind(null,ch)
    }
    exported[[cluster]] <- list(status="Success", sample_mask=use, intron_mask=keep, samples=colnames(counts)[use], introns=colnames(y), counts=unname(y), full=unname(full), null=unname(null), fit=fit)
  }
  save_json(list(id=case$id, samples=case$samples, introns=case$introns, counts=counts, groups=x, confounders=confounders, results=exported, cluster_table=table, effects=effects), paste0(case$id, ".json"))
  completed[[length(completed)+1L]] <- case$id
}
save_json(list(status="PASS_COMPLETE_ORIGINAL_LEAFCUTTER_MODEL_AND_DIFFERENTIAL_CONTROLS_ONLY", cases=completed, original_namespace_patched=FALSE, analysis_admitted=FALSE, implementation_credit=FALSE), "ORIGINAL-COMPLETED.json")
