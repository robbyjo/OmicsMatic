compute_deviations_core <- function(counts_mat,
                                    peak_indices,
                                    background_peaks,
                                    expectation,
                                    rowData = NULL,
                                    colData = NULL) {

  if (min(getFragmentsPerPeak(counts_mat)) <= 0)
    stop("All peaks must have at least one fragment in one sample")

  stopifnot(nrow(counts_mat) == nrow(background_peaks))
  stopifnot(length(expectation) == nrow(counts_mat))

  if (is.null(colData))
    colData <- DataFrame(seq_len(ncol(counts_mat)),
                         row.names = colnames(counts_mat))[,FALSE]

  # check that indices fall within appropriate bounds
  tmp <- unlist(peak_indices, use.names = FALSE)
  if (is.null(tmp) ||
      !(all.equal(tmp, as.integer(tmp))) ||
      max(tmp) > nrow(counts_mat) ||
      min(tmp) < 1) {
    stop("Annotations are not valid")
  }

  if (is.null(names(peak_indices))) {
    names(peak_indices) <- seq_along(peak_indices)
  }

  sample_names <- colnames(counts_mat)

  results <- bplapply(peak_indices,
                      compute_deviations_single,
                      counts_mat = counts_mat,
                      background_peaks = background_peaks,
                      expectation = expectation)

  z <- t(vapply(results, function(x) x[["z"]], rep(0, ncol(counts_mat))))
  dev <- t(vapply(results, function(x) x[["dev"]], rep(0, ncol(counts_mat))))

  colnames(z) <- colnames(dev) <- sample_names

  rowData$fractionMatches <- vapply(results, function(x) x[["matches"]], 0)
  rowData$fractionBackgroundOverlap <- vapply(results, 
                                              function(x) x[["overlap"]], 
                                              0)
  
  out <- SummarizedExperiment(assays = list(deviations = dev, z = z),
                              colData = colData,
                              rowData = rowData)

  return(new("chromVARDeviations", out))
}
