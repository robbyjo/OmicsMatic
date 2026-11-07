RunChromVAR.ChromatinAssay <- function(
  object,
  genome,
  motif.matrix = NULL,
  verbose = TRUE,
  ...
) {
  if (!requireNamespace("chromVAR", quietly = TRUE)) {
    stop("Please install chromVAR. https://greenleaflab.github.io/chromVAR/")
  }
  if (!requireNamespace("SummarizedExperiment", quietly = TRUE)) {
    stop("Please install SummarizedExperiment")
  }
  motif.matrix <- SetIfNull(
    x = motif.matrix,
    y = GetMotifData(object = object, slot = "data")
  )
  peak.matrix <- GetAssayData(object = object, slot = "counts")
  if (!(all(peak.matrix@x == floor(peak.matrix@x)))) {
    warning("Count matrix contains non-integer values.
            ChromVAR should only be run on integer counts.")
  }
  idx.keep <- rowSums(x = peak.matrix) > 0
  peak.matrix <- peak.matrix[idx.keep, , drop = FALSE]
  motif.matrix <- motif.matrix[idx.keep, , drop = FALSE]
  peak.ranges <- granges(x = object)
  peak.ranges <- peak.ranges[idx.keep]
  chromvar.obj <- SummarizedExperiment::SummarizedExperiment(
    assays = list(counts = peak.matrix),
    rowRanges = peak.ranges
  )
  if (verbose) {
    message("Computing GC bias per region")
  }
  chromvar.obj <- chromVAR::addGCBias(
    object = chromvar.obj,
    genome = genome
  )
  if (verbose) {
    message("Selecting background regions")
  }
  bg <- chromVAR::getBackgroundPeaks(
    object = chromvar.obj,
    ...
  )
  if (verbose) {
    message("Computing deviations from background")
  }
  dev <- chromVAR::computeDeviations(
    object = chromvar.obj,
    annotations = motif.matrix,
    background_peaks = bg
  )
  chromvar.z <- SummarizedExperiment::assays(dev)[[2]]
  rownames(x = chromvar.z) <- colnames(x = motif.matrix)
  if (verbose) {
    message("Constructing chromVAR assay")
  }
  obj <- CreateAssayObject(data = chromvar.z)
  return(obj)
}
