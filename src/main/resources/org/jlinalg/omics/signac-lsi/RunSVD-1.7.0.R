RunSVD.default <- function(
  object,
  assay = NULL,
  n = 50,
  scale.embeddings = TRUE,
  reduction.key = "LSI_",
  scale.max = NULL,
  verbose = TRUE,
  irlba.work = n * 3,
  ...
) {
  n <- min(n, (ncol(x = object) - 1))
  if (verbose) {
    message("Running SVD")
  }
  components <- irlba(A = t(x = object), nv = n, work = irlba.work)
  feature.loadings <- components$v
  sdev <- components$d / sqrt(x = max(1, nrow(x = object) - 1))
  cell.embeddings <- components$u
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
