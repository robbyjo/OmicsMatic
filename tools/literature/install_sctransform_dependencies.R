# Copyright (C) 2026 JLinAlg contributors
# SPDX-License-Identifier: GPL-3.0-or-later
# Private SCTransform runtime; scientific validation is separate from installation.
args <- commandArgs(trailingOnly = TRUE)
if (length(args) != 4L) stop("Usage: Rscript --vanilla install_sctransform_dependencies.R LIBRARY REPORT allow|forbid vst|seurat|vst-v2|seurat-v2")
lib <- normalizePath(args[[1]], winslash = "/", mustWork = FALSE)
report <- normalizePath(args[[2]], winslash = "/", mustWork = FALSE)
allowed <- identical(args[[3]], "allow")
profile <- args[[4]]
if (!profile %in% c("vst", "seurat", "vst-v2", "seurat-v2") || !args[[3]] %in% c("allow", "forbid")) stop("Invalid profile or installation policy")
pins <- c(sctransform = "0.4.3", jsonlite = "2.0.0")
if (startsWith(profile, "seurat")) pins <- c(pins, Seurat = "5.6.0", SeuratObject = "5.4.0")
if (endsWith(profile, "-v2")) pins <- c(pins, glmGamPoi = "1.24.0")
dir.create(report, recursive = TRUE, showWarnings = FALSE)
attempt <- file.path(report, format(Sys.time(), "%Y%m%dT%H%M%S"))
if (dir.exists(attempt)) stop("An attempt with this timestamp exists; preserve it and retry later")
dir.create(attempt)
marker <- file.path(lib, ".jlinalg-sctransform-library")
.libPaths(if (dir.exists(lib)) c(lib, .Library) else .Library)
options(repos = c(CRAN = "https://cloud.r-project.org"), timeout = 300)
if (endsWith(profile, "-v2")) options(repos = c(BioCsoft = "https://bioconductor.posit.co/packages/3.23/bioc", CRAN = "https://cloud.r-project.org"))
state <- "skipped-dependency-unavailable"
detail <- ""
warnings <- character()
version_matching <- function(p) {
  suppressWarnings(tryCatch(identical(as.character(packageVersion(p)), unname(pins[p])), error = function(e) FALSE))
}
matching <- function() {
  if (!all(vapply(names(pins), version_matching, logical(1)))) return(FALSE)
  all(vapply(names(pins), requireNamespace, logical(1), quietly = TRUE))
}
sink(file.path(attempt, "installation.log"), split = TRUE)
tryCatch(withCallingHandlers({
  if (matching() && file.exists(marker)) {
    state <- "available-not-yet-analysis-validated"
  } else if (!allowed) {
    state <- "skipped-install-not-permitted"
    detail <- "The pinned R packages are unavailable and installation was not permitted."
  } else if (dir.exists(lib) && !file.exists(marker)) {
    state <- "skipped-unmanaged-library"
    detail <- "Existing unmanaged R library is preserved. Select a new private LIBRARY."
  } else {
    if (!dir.exists(lib)) {
      dir.create(lib, recursive = TRUE)
      writeLines("JLinAlg private SCTransform runtime", marker)
    }
    .libPaths(c(lib, .Library))
    type <- if (.Platform$OS.type == "windows") "binary" else "source"
    available <- available.packages(type = type)
    write.table(available, file.path(attempt, "available-packages.tsv"), sep = "\t", quote = TRUE, row.names = TRUE)
    for (p in names(pins)) if (!p %in% rownames(available) || !identical(unname(available[p, "Version"]), unname(pins[p])))
      stop("Pinned ", p, " ", pins[p], " is unavailable from this repository; no version substitution was made")
    # Only Depends/Imports/LinkingTo, not the very large optional visualization/omics ecosystem.
    pending <- names(pins)[!vapply(names(pins), version_matching, logical(1))]
    if (!length(pending)) stop("Pinned package versions exist but cannot load; preserve this library and supply a new private library after correcting the exact DLL/package cause")
    install.packages(pending, lib = lib, dependencies = NA, type = type,
                     destdir = { p <- file.path(attempt, "downloads"); dir.create(p); p })
    if (!matching()) stop("Pinned R packages did not load after installation; inspect all retained package/DLL diagnostics")
    state <- "installed-not-yet-analysis-validated"
  }
}, warning = function(w) { warnings <<- c(warnings, conditionMessage(w)); }),
error = function(e) { detail <<- conditionMessage(e) })
installed <- installed.packages(lib.loc = .libPaths())
write.table(installed[, c("Package", "Version", "LibPath", "Built"), drop = FALSE],
            file.path(attempt, "installed-packages.tsv"), sep = "\t", quote = FALSE, row.names = FALSE)
status <- c(paste("status:", state), paste("profile:", profile), paste("library:", encodeString(lib, quote = '"')),
  paste("attempt:", encodeString(attempt, quote = '"')), paste("install_authorized:", tolower(allowed)),
  paste("detail:", encodeString(detail, quote = '"')), paste("r_version:", encodeString(R.version.string, quote = '"')))
writeLines(status, file.path(attempt, "dependency-status.yaml"))
writeLines(status, file.path(report, "dependency-status.yaml"))
action <- paste("Skip affected analysis while dependencies are unavailable. Where permitted rerun Rscript --vanilla tools/literature/install_sctransform_dependencies.R", lib, report, "allow", profile,
  ". Supply the resulting private library and Rscript to the adapter. Preserve existing libraries, failed attempts and archives. If installation is forbidden, request the pinned packages on a permitted supported host. Version/load checks do not establish scientific validity.")
text <- c(if (startsWith(state, "skipped")) "# ANALYSIS SKIPPED - SCTRANSFORM RUNTIME UNAVAILABLE" else "# SCTransform runtime available; scientific validation is separate",
  "", detail, "", paste("Required action:", action))
if (length(warnings)) text <- c(text, "", "## INSTALLATION WARNINGS", "", unique(warnings))
writeLines(text, file.path(report, "WARNINGS.md"))
cat("STATUS:", state, "\nDETAIL:", detail, "\n")
sink()
quit(status = if (startsWith(state, "skipped")) 3L else 0L)
