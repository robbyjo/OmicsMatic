SparsifiedRanks <- function(X){
  if (!inherits(x = X, what = "CsparseMatrix")) {
    X <- as(object = X, Class = "CsparseMatrix")
  }
  non_zeros_per_col <- diff(x = X@p)
  n_zeros_per_col <- nrow(x = X) - non_zeros_per_col
  offsets <- (n_zeros_per_col - 1) / 2
  x <- X@x
  ## split entries to columns
  col_lst <- split(
    x = x,
    f = rep.int(x = 1:ncol(x = X), times = non_zeros_per_col)
  )
  ## calculate sparsified ranks and do shifting
  sparsified_ranks <- unlist(
    x = lapply(
      X = seq_along(col_lst),
      FUN = function(i) rank(x = col_lst[[i]]) + offsets[i]
      )
    )
  ## Create template rank matrix
  X.ranks <- X
  X.ranks@x <- sparsified_ranks
  return(X.ranks)
}
