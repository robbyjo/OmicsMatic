SparseSpearmanCor <- function(X, Y = NULL, cov = FALSE) {
  # Get sparsified ranks
  rankX <- SparsifiedRanks(X = X)
  if (is.null(Y)){
    # Calculate pearson correlation on rank matrices
    return (corSparse(X = rankX, cov = cov))
    }
  rankY <- SparsifiedRanks(X = Y)
  return(corSparse(X = rankX, Y = rankY, cov = cov))
}
