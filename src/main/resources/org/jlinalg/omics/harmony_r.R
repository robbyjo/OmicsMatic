# Copyright (C) 2026 JLinAlg contributors
# SPDX-License-Identifier: GPL-3.0-or-later
# Pinned R Harmony adapter; called through typed Java options, never a shell template.
args <- commandArgs(trailingOnly = TRUE)
if (length(args) %% 2L != 0L) stop("Expected option/value pairs")
o <- setNames(as.list(args[seq.int(2L, length(args), 2L)]), sub("^--", "", args[seq.int(1L, length(args), 2L)]))
action <- "In an approved environment run Rscript --vanilla tools/literature/install_harmony_dependencies.R build/omics-harmony-r build/literature-review/harmony-r-dependency allow. Then supply that Rscript and --r-library directory. The R Harmony 2.0.5 and Python harmonypy 2.1.0 adapters are separate runtimes. Correct input/options and rerun to a new output directory."
state <- "failed"
detail <- "R Harmony did not complete"
warnings_seen <- character()
write_tsv <- function(x, file) write.table(x, file, sep = "\t", quote = TRUE, qmethod = "double", row.names = FALSE, fileEncoding = "UTF-8")
tryCatch({
  if (!dir.exists(o[["r-library"]])) {
    state <- "skipped-dependency-unavailable"; stop("The explicit private R library directory is unavailable")
  }
  .libPaths(c(normalizePath(o[["r-library"]], winslash = "/"), .Library))
  if (!requireNamespace("harmony", quietly = TRUE) || !identical(as.character(packageVersion("harmony")), "2.0.5")) {
    state <- "skipped-dependency-unavailable"; stop("Require loadable R harmony 2.0.5 and its dependencies")
  }
  capture.output(sessionInfo(), file = "r-session.txt")
  installed <- installed.packages(lib.loc = .libPaths())
  write_tsv(as.data.frame(installed[, c("Package", "Version", "LibPath", "Built"), drop = FALSE]), "installed-packages.tsv")
  read_table <- function(path) {
    if (file.info(path)$size > 128 * 1024^2) stop("Input table exceeds 128 MiB limit")
    read.delim(path, header = TRUE, check.names = FALSE, colClasses = "character", na.strings = character(),
      stringsAsFactors = FALSE, quote = '"', comment.char = "", fileEncoding = "UTF-8")
  }
  matrix_table <- read_table(o$matrix)
  metadata <- read_table(o$metadata)
  if (ncol(matrix_table) < 3L || names(matrix_table)[[1]] != "obs_id" || anyDuplicated(names(matrix_table))) stop("Matrix requires obs_id and unique PCA component columns")
  ids <- matrix_table[[1]]; n <- length(ids); d <- ncol(matrix_table) - 1L
  if (n < 4L || n > 100000L || d > 256L || n * d > 5000000L || any(!nzchar(trimws(ids))) || anyDuplicated(ids)) stop("Require 4..100000 unique observations, <=256 components and N*components <=5 million")
  x <- matrix(suppressWarnings(as.numeric(as.matrix(matrix_table[-1]))), nrow = n, ncol = d)
  if (any(!is.finite(x)) || any(rowSums(abs(x)) == 0)) stop("Require finite numeric coordinates and no all-zero observations")
  if (names(metadata)[[1]] != "obs_id" || anyDuplicated(names(metadata)) || !o$batch %in% names(metadata)
      || nrow(metadata) != n || anyDuplicated(metadata[[1]]) || !setequal(ids, metadata[[1]])) stop("Metadata requires the same unique obs_id universe and named batch column")
  metadata <- metadata[match(ids, metadata[[1]]), , drop = FALSE]
  batches <- metadata[[o$batch]]
  if (any(!nzchar(trimws(batches))) || length(unique(batches)) < 2L) stop("Require nonblank batch labels and at least two batches")
  clusters <- if (is.null(o$clusters)) min(100L, max(2L, round(n / 30))) else as.integer(o$clusters)
  if (!is.finite(clusters) || clusters < 2L || clusters >= n || n * clusters > 10000000L) stop("Require 2 <= clusters < observations and N*clusters <=10 million")
  seed <- as.integer(o$seed); iterations <- as.integer(o$iterations)
  theta <- as.numeric(o$theta); lambda <- as.numeric(o$lambda); sigma <- as.numeric(o$sigma)
  early <- identical(o[["early-stop"]], "true")
  if (is.na(seed) || seed < 0L || is.na(iterations) || iterations < 1L || iterations > 1000L
      || !is.finite(theta) || theta < 0 || !is.finite(lambda) || lambda <= 0 || !is.finite(sigma) || sigma <= 0) stop("Invalid seed/iteration/Harmony penalty settings")
  RNGkind("Mersenne-Twister", "Inversion", "Rejection"); set.seed(seed)
  options(digits = 17)
  defaults <- harmony::harmony_options()
  capture.output(dput(defaults), file = "algorithm-options.R.txt")
  corrected <- withCallingHandlers(harmony::RunHarmony(x, metadata, vars_use = o$batch, theta = theta,
    lambda = lambda, sigma = sigma, nclust = clusters, max_iter = iterations, early_stop = early,
    ncores = 1L, plot_convergence = FALSE, return_object = FALSE, verbose = TRUE, .options = defaults),
    warning = function(warning) { warnings_seen <<- c(warnings_seen, conditionMessage(warning)); message("UPSTREAM WARNING: ", conditionMessage(warning)); invokeRestart("muffleWarning") })
  if (!identical(dim(corrected), c(n, d)) || any(!is.finite(corrected))) stop("Harmony output has invalid dimensions or nonfinite coordinates")
  result <- data.frame(obs_id = ids, corrected, check.names = FALSE)
  names(result) <- c("obs_id", paste0("Harmony", seq_len(d)))
  write_tsv(result, "results.tsv")
  centroids <- do.call(rbind, lapply(unique(batches), function(label) {
    selected <- batches == label
    data.frame(batch = label, component = seq_len(d), observations = sum(selected),
      input_mean = colMeans(x[selected, , drop = FALSE]), corrected_mean = colMeans(corrected[selected, , drop = FALSE]))
  }))
  write_tsv(centroids, "batch-centroids.tsv")
  values <- c(engine = "R harmony", version = "2.0.5", observations = n, input_components = d,
    batch_column = o$batch, batches = length(unique(batches)), seed = seed, rng = "Mersenne-Twister/Inversion/Rejection",
    theta = theta, lambda = lambda, sigma = sigma, clusters = clusters, requested_iterations = iterations,
    early_stop = early, ncores = 1L, convergence = "Upstream early-stop criterion or requested iteration budget; no independent convergence claim",
    scope = "Supplied PCA batch correction only; fixed positive lambda and explicit nclust; no automatic count normalization, cell annotation, inference or Python/legacy Harmony coordinate parity")
  write_tsv(data.frame(parameter = names(values), value = unname(values)), "effective-settings.tsv")
  state <- if (length(warnings_seen)) "completed-with-qc-warnings" else "completed"
  detail <- "Pinned R Harmony completed; inspect corrected coordinates, settings and exploratory batch centroids"
}, error = function(error) { detail <<- conditionMessage(error) })
if (!startsWith(state, "completed") && file.exists("results.tsv")) file.rename("results.tsv", "partial-results.tsv")
if (!startsWith(state, "completed") || length(warnings_seen)) writeLines(c(
  paste0("# ", if (startsWith(state, "skipped")) "ANALYSIS SKIPPED" else if (startsWith(state, "completed")) "UPSTREAM ANALYSIS WARNINGS" else "ANALYSIS FAILED"),
  "", detail, warnings_seen, "", paste("Required action:", if (startsWith(state, "completed")) "Review the upstream warnings and batch/biology confounding before using corrected coordinates; inspect external.log and batch-centroids.tsv." else action)), "WARNINGS.md", useBytes = TRUE)
write_tsv(data.frame(status = state, detail = gsub("[\r\n]", " ", detail), required_action = action), "r-analysis-status.tsv")
quit(status = if (startsWith(state, "completed")) 0L else if (startsWith(state, "skipped")) 3L else 2L)
