compute_expectations_core <- function(object, norm = FALSE, group = NULL) {

  if (is.null(group)) {
    if (norm) {
      expectation <- rowSums(object/matrix(getFragmentsPerSample(object),
                                           nrow = nrow(object),
                                           ncol = ncol(object),
                                           byrow = TRUE))
    } else {
      expectation <- getFragmentsPerPeak(object) /
        getTotalFragments(object)
    }
  } else {
    n_anno <- length(levels(group))
    mat <- matrix(nrow = nrow(object), ncol = n_anno)
    if (norm) {
      for (i in seq_len(n_anno)) {
        ix <- which(group == levels(group)[i])
        mat[, i] <- rowSums(object[, ix] /
                              matrix(getFragmentsPerSample(object)[ix],
                                     nrow = nrow(object),
                                     ncol = length(ix),
                                     byrow = TRUE))
      }
    } else {
      for (i in seq_len(n_anno)) {
        ix <- which(group == levels(group)[i])
        mat[, i] <- rowSums(object[, ix])/sum(object[, ix])
      }
    }
    expectation <- rowMeans(mat)
  }
  return(expectation)
}
