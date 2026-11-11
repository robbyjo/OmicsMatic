# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Read-only original DESeq2 1.26.0 fitted-object inspection. No model refit or input repair.
args <- commandArgs(trailingOnly=TRUE)
if (length(args) != 4L) stop("Require original RData, complete featureCounts, exact source BAM suffix and NEW worker output")
library(DESeq2)
stopifnot(as.character(getRversion()) == "3.6.2",
          as.character(packageVersion("DESeq2")) == "1.26.0",
          as.character(packageVersion("genefilter")) == "1.68.0")
out <- args[4]
if (file.exists(out)) stop("Worker output already exists; preserve it and use a new path")
if (!dir.create(out)) stop("Cannot create new worker output")
warnings_seen <- character()
withCallingHandlers({
  emit <- function(data, name) {
    data <- as.data.frame(data, check.names=FALSE, stringsAsFactors=FALSE)
    for (i in seq_along(data)) {
      value <- data[[i]]
      if (is.numeric(value)) value <- ifelse(is.nan(value), "NaN", ifelse(is.na(value), "NA", sprintf("%.17g", value)))
      else if (is.logical(value)) value <- ifelse(is.na(value), "NA", as.character(value))
      else value <- as.character(value)
      if (anyNA(value) || any(grepl("[\t\r\n]", value))) stop("Unrepresentable metadata field; no identifier escaping/repair inferred")
      data[[i]] <- value
    }
    write.table(data, file=file.path(out, name), sep="\t", quote=FALSE, row.names=FALSE, col.names=TRUE)
  }
  matrix_out <- function(matrix, name) emit(data.frame(gene=rownames(matrix), matrix, check.names=FALSE), name)
  source_dir <- file.path(out, "source-guards"); dir.create(source_dir)
  for (name in c("results", "pvalueAdjustment", "estimateSizeFactorsForMatrix", "DESeq", "replaceOutliers", "refitWithoutOutliers")) {
    fn <- get(name, asNamespace("DESeq2"))
    writeLines(capture.output(dput(formals(fn))), file.path(source_dir, paste0(name, ".formals.txt")))
    writeLines(deparse(body(fn), width.cutoff=500), file.path(source_dir, paste0(name, ".body.R")))
  }
  fn <- genefilter::filtered_p
  writeLines(deparse(body(fn), width.cutoff=500), file.path(source_dir, "filtered_p.body.R"))
  env <- new.env(); loaded <- load(args[1], envir=env)
  if (!setequal(loaded, c("dds", "rld"))) stop("Original fitted-object family differs")
  dds <- env$dds; rld <- env$rld
  if (!is(dds, "DESeqDataSet") || !is(rld, "DESeqTransform")) stop("Original model/transform classes differ")
  if (!identical(paste(deparse(design(dds)), collapse=""), "~condition")) stop("Original model design differs from source ~condition")
  if (!identical(attr(dds, "test"), "Wald") || !identical(attr(dds, "betaPrior"), FALSE) || "tDegreesFreedom" %in% names(mcols(dds)))
    stop("Model is outside original default Wald/no-beta-prior/no-t-test contract")
  if (!is.null(normalizationFactors(dds))) stop("Unexpected gene-specific normalization factors")
  # These are the exact read.delim/gsub/tail operations in the locked original script.
  table <- read.delim(file=args[2], header=TRUE, skip=1)
  colnames(table) <- gsub(args[3], "", colnames(table))
  colnames(table) <- as.character(lapply(colnames(table), function(x) tail(strsplit(x, '.', fixed=TRUE)[[1]], 1)))
  rownames(table) <- table$Geneid
  interval <- table[, 1:6, drop=FALSE]
  count_table <- table[, 7:ncol(table), drop=FALSE]
  samples <- sort(colnames(count_table)); groups <- sub("_[^_]+$", "", samples)
  if (length(unique(groups)) < 2L) stop("Original differential branch has fewer than two groups")
  prepared <- as.matrix(count_table[, samples, drop=FALSE])
  if (anyNA(prepared) || any(!is.finite(prepared)) || any(prepared < 0) || any(prepared != round(prepared)))
    stop("Source featureCounts profile requires complete nonnegative integer counts; no rounding performed")
  raw <- counts(dds, normalized=FALSE)
  if (!identical(rownames(raw), rownames(prepared)) || !identical(colnames(raw), samples) || !all(raw == prepared))
    stop("Fitted model raw population, sample order or values differ from complete original featureCounts")
  if (!identical(as.character(dds$condition), groups)) stop("Fitted conditions differ from original final-underscore group rule")
  if (!identical(rownames(rld), rownames(raw)) || !identical(colnames(rld), samples)) stop("Transform identities differ from fitted population")
  factors <- sizeFactors(dds)
  if (length(factors) != length(samples) || anyNA(factors) || any(!is.finite(factors)) || any(factors <= 0)) stop("Unassessable original size factors")
  replacement <- mcols(dds)$replace
  if (is.null(replacement)) replacement <- rep(FALSE, nrow(dds))
  if (length(replacement) != nrow(dds) || !is.logical(replacement) || any(is.na(replacement) & rowSums(raw) != 0))
    stop("Unassessable original replacement flags outside source all-zero rows")
  effective <- raw
  replaced <- which(replacement)
  if (length(replaced)) {
    if (!"replaceCounts" %in% assayNames(dds)) stop("Original replaced-count audit data are absent")
    effective[replaced, ] <- assay(dds, "replaceCounts")[replaced, ]
  }
  emit(data.frame(sample=samples, condition=groups, size_factor=as.numeric(factors)), "samples.tsv")
  matrix_out(raw, "raw-counts.tsv")
  matrix_out(effective, "effective-fit-counts.tsv")
  matrix_out(counts(dds, normalized=TRUE), "normalized-counts.tsv")
  matrix_out(assay(rld), "transform.tsv")
  emit(data.frame(gene=rownames(dds), replacement=as.logical(replacement)), "replacement.tsv")
  default_matrix <- model.matrix(design(dds), data=as.data.frame(colData(dds)))
  saved_matrix <- attr(dds, "modelMatrix")
  if (is.null(saved_matrix) || !identical(dim(saved_matrix), dim(default_matrix)) || !all(saved_matrix == default_matrix) || !identical(rownames(saved_matrix), samples))
    stop("Saved fitted design matrix differs from the source default treatment-contrast design")
  emit(data.frame(sample=samples, saved_matrix, check.names=FALSE), "model-matrix.tsv")
  emit(data.frame(gene=rownames(dds), all_zero=mcols(dds)$allZero, beta_converged=mcols(dds)$betaConv,
                 max_cooks=mcols(dds)$maxCooks, dispersion=dispersions(dds)), "gene-diagnostics.tsv")
  writeLines(resultsNames(dds), file.path(out, "result-names.txt"))
  comparisons <- combn(unique(groups), 2)
  ids <- sprintf("comparison-%06d", seq_len(ncol(comparisons)))
  prefixes <- apply(comparisons, 2, function(x) paste(x, collapse="vs"))
  if (anyDuplicated(prefixes)) stop("Original comparison prefixes collide; no renaming inferred")
  emit(data.frame(id=ids, control=comparisons[1, ], treatment=comparisons[2, ], source_prefix=prefixes), "comparisons.tsv")
  for (i in seq_len(ncol(comparisons))) {
    control <- comparisons[1, i]; treatment <- comparisons[2, i]
    result <- results(dds, contrast=c("condition", control, treatment))
    emit(data.frame(gene=rownames(result), as.data.frame(result), check.names=FALSE), paste0(ids[i], ".results.tsv"))
    meta <- metadata(result); theta <- meta$filterNumRej$theta
    emit(data.frame(theta=theta, cutoff=quantile(result$baseMean, theta), rejections=meta$filterNumRej$numRej, smoothed=meta$lo.fit$y), paste0(ids[i], ".filter.tsv"))
    emit(data.frame(alpha=meta$alpha, theta=meta$filterTheta, cutoff=meta$filterThreshold), paste0(ids[i], ".selection.tsv"))
  }
  fit_type <- attr(dispersionFunction(dds), "fitType")
  if (is.null(fit_type)) fit_type <- "UNREPORTED"
  emit(data.frame(key=c("schema_version", "R_version", "DESeq2_version", "genefilter_version", "design", "test", "beta_prior", "use_t", "fit_type", "genes", "samples", "comparisons"),
      value=c("1", as.character(getRversion()), as.character(packageVersion("DESeq2")), as.character(packageVersion("genefilter")), "~condition", "Wald", "FALSE", "FALSE", fit_type, nrow(dds), ncol(dds), ncol(comparisons))), "model-metadata.tsv")
}, warning=function(w) {
  warnings_seen <<- c(warnings_seen, conditionMessage(w))
  invokeRestart("muffleWarning")
})
writeLines(warnings_seen, file.path(out, "ORIGINAL_WARNINGS.txt"))
writeLines(capture.output(sessionInfo()), file.path(out, "sessionInfo.txt"))
writeLines("COMPLETE_ORIGINAL_DESEQ126_POSTFIT_EXPORT_ONLY", file.path(out, "WORKER_COMPLETED.txt"))
