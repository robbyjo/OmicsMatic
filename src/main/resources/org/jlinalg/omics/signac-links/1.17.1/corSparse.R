corSparse <- function(X, Y = NULL, cov = FALSE) {
  X <- as(object = X, Class = "CsparseMatrix")
  n <- nrow(x = X)
  muX <- colMeans(x = X)
  
  if (!is.null(x = Y)) {
    if (nrow(x = X) != nrow(x = Y)) {
      stop("Matrices must contain the same number of rows")
    }
    Y <- as(object = Y, Class = "CsparseMatrix")
    muY <- colMeans(x = Y)
    covmat <- ( as.matrix(x = crossprod(x = X, y = Y)) - n * tcrossprod(x = muX, y = muY) ) / (n-1)
    sdvecX <- sqrt( (colSums(x = X^2) - n*muX^2) / (n-1) )
    sdvecY <- sqrt( (colSums(x = Y^2) - n*muY^2) / (n-1) )
    cormat <- covmat / tcrossprod(x = sdvecX, y = sdvecY)
  } else {		
    covmat <- ( as.matrix(crossprod(x = X)) - n * tcrossprod(x = muX) ) / (n-1)
    sdvec <- sqrt(x = diag(x = covmat))
    cormat <- covmat / tcrossprod(x = sdvec)
  }
  if (cov) {
    dimnames(x = covmat) <- NULL
    return(covmat)
  } else {
    dimnames(x = cormat) <- NULL
    return(cormat)
  }
}
