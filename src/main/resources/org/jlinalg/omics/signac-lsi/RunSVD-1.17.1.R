RunSVD.default <- function(
    object,
    assay = NULL,
    n = 50,
    scale.embeddings = !pca,
    pca = FALSE,
    reduction.key = ifelse(pca, "PCA_", "LSI_"),
    scale.max = NULL,
    verbose = TRUE,
    tol = 1e-05,
    ...
) {
  if (is.null(x = rownames(x = object))) {
    rownames(x = object) <- seq_len(length.out = nrow(x = object))
  }
  if (is.null(x = colnames(x = object))) {
    colnames(x = object) <- seq_len(length.out = ncol(x = object))
  }
  n <- min(n, (ncol(x = object) - 1))
  
  opts <- list("tol" = tol)
  if (pca) {
    if (verbose) {
      message("Running PCA")
    }
    # data needs to be standardized
    opts <- c(opts, list("center" = TRUE, "scale" = TRUE))
  } else {
    if (verbose) {
      message("Running SVD")
    }
  }
  
  components <- svds(A = t(x = object), k = n, opts = opts)
  feature.loadings <- components$v
  sdev <- components$d / sqrt(x = max(1, nrow(x = object) - 1))
  if (pca) {
    # weight by eigenvalues
    cell.embeddings <- components$u %*% diag(components$d)
  } else {
    cell.embeddings <- components$u
  }
  if (scale.embeddings) {
    if (verbose) {
      message("Scaling cell embeddings")
    }
    embed.mean <- apply(X = cell.embeddings, MARGIN = 2, FUN = mean)
    embed.sd <- apply(X = cell.embeddings, MARGIN = 2, FUN = sd)
    norm.embeddings <- t((t(cell.embeddings) - embed.mean) / embed.sd)
    if (!is.null(x = scale.max)) {
      norm.embeddings[norm.embeddings > scale.max] <- scale.max
      norm.embeddings[norm.embeddings < -scale.max] <- -scale.max
    }
  } else {
    norm.embeddings <- cell.embeddings
  }
  rownames(x = feature.loadings) <- rownames(x = object)
  colnames(x = feature.loadings) <- paste0(
    reduction.key, seq_len(length.out = n)
  )
  rownames(x = norm.embeddings) <- colnames(x = object)
  colnames(x = norm.embeddings) <- paste0(
    reduction.key, seq_len(length.out = n)
  )
  reduction.data <- CreateDimReducObject(
    embeddings = norm.embeddings,
    loadings = feature.loadings,
    assay = assay,
    stdev = sdev,
    key = reduction.key,
    misc = components
  )
  return(reduction.data)
}
