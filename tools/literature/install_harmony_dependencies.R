# Copyright (C) 2026 JLinAlg contributors
# SPDX-License-Identifier: GPL-3.0-or-later
# Private CRAN Harmony installation; runtime validation is a separate step.
args <- commandArgs(trailingOnly = TRUE)
if (length(args) != 3L) stop("Usage: Rscript --vanilla install_harmony_dependencies.R LIBRARY REPORT_DIR allow|forbid")
library_path <- normalizePath(args[[1]], winslash = "/", mustWork = FALSE)
report_path <- normalizePath(args[[2]], winslash = "/", mustWork = FALSE)
allowed <- identical(args[[3]], "allow")
dir.create(report_path, recursive = TRUE, showWarnings = FALSE)
marker <- file.path(library_path, ".jlinalg-harmony-library")
if (dir.exists(library_path)) .libPaths(c(library_path, .Library)) else .libPaths(.Library)
expected <- "2.0.5"
state <- "skipped-dependency-unavailable"
detail <- ""
log_path <- file.path(report_path, "installation.log")
sink(log_path, split = TRUE)
tryCatch({
  if (requireNamespace("harmony", quietly = TRUE) && identical(as.character(packageVersion("harmony")), expected)) {
    state <- "available-not-yet-analysis-validated"
  } else if (!allowed) {
    state <- "skipped-install-not-permitted"
    detail <- "Installation is forbidden and the pinned Harmony package is unavailable."
  } else if (dir.exists(library_path) && !file.exists(marker)) {
    state <- "skipped-unmanaged-library"
    detail <- "Existing unmanaged R library was preserved. Choose a new private library directory."
  } else {
    dir.create(library_path, recursive = TRUE, showWarnings = FALSE)
    writeLines("JLinAlg private Harmony runtime", marker)
    .libPaths(c(library_path, .Library))
    options(repos = c(CRAN = "https://cran.r-project.org"), timeout = 300)
    type <- if (.Platform$OS.type == "windows") "binary" else "source"
    available <- available.packages(type = type)
    if (!"harmony" %in% rownames(available) || !identical(unname(available["harmony", "Version"]), expected))
      stop("Pinned harmony 2.0.5 is unavailable from the selected CRAN runtime repository; no version substitution was made.")
    # Use declared Imports/Depends/LinkingTo; do not install suggested Seurat or vignettes.
    install.packages("harmony", lib = library_path, dependencies = NA, type = type)
    if (!requireNamespace("harmony", quietly = TRUE) || !identical(as.character(packageVersion("harmony")), expected))
      stop("Harmony did not load with the pinned version after installation; inspect the dependency/build log.")
    state <- "installed-not-yet-analysis-validated"
  }
}, error = function(error) { detail <<- conditionMessage(error) })
cat("STATUS:", state, "\nDETAIL:", detail, "\n")
writeLines(c(paste("status:", state), paste("version:", expected), paste("library:", library_path),
  paste("install_authorized:", allowed), paste("detail:", encodeString(detail, quote = '"')),
  paste("r_version:", encodeString(R.version.string, quote = '"'))), file.path(report_path, "dependency-status.yaml"))
installed <- installed.packages(lib.loc = .libPaths())
write.table(installed[, c("Package", "Version", "LibPath", "Built"), drop = FALSE],
  file.path(report_path, "installed-packages.tsv"), sep = "\t", quote = FALSE, row.names = FALSE)
action <- "Use Rscript --vanilla tools/literature/install_harmony_dependencies.R build/omics-harmony-r build/literature-review/harmony-r-dependency allow in an approved environment. Choose a new private library when an unmanaged library exists. Supply --rscript and --r-library to the R Harmony adapter only after installation/load succeeds. See installation.log; Python harmonypy and R Harmony are separate runtimes."
writeLines(if (startsWith(state, "skipped")) c("# R HARMONY DEPENDENCY UNAVAILABLE - ANALYSIS SKIPPED", "", detail, "", paste("Required action:", action)) else c("# R Harmony dependency available", "", "Installation/load success is separate from analysis validation."), file.path(report_path, "WARNINGS.md"))
sink()
quit(status = if (startsWith(state, "skipped")) 3L else 0L)
