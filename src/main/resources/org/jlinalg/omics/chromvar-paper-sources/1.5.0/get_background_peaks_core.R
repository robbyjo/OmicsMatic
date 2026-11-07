get_background_peaks_core <- function(object, 
                                      bias, 
                                      niterations = 50, 
                                      w = 0.1, 
                                      bs = 50) {
  
  fragments_per_peak <- getFragmentsPerPeak(object)
  stopifnot(length(bias) == length(fragments_per_peak))
  if (min(fragments_per_peak) <= 0) 
    stop("All peaks must have at least one fragment in one sample")
  
  intensity <- log10(fragments_per_peak)
  norm_mat <- matrix(c(intensity, bias), ncol = 2, byrow = FALSE)
  
  chol_cov_mat <- chol(cov(norm_mat))
  trans_norm_mat <- t(forwardsolve(t(chol_cov_mat), t(norm_mat)))
  
  # make bins
  bins1 <- seq(min(trans_norm_mat[, 1]), max(trans_norm_mat[, 1]), 
               length.out = bs)
  bins2 <- seq(min(trans_norm_mat[, 2]), max(trans_norm_mat[, 2]), 
               length.out = bs)
  
  bin_data <- do.call(rbind, lapply(seq_len(bs), 
                                    function(x) matrix(c(rep(bins1[x], bs), 
                                                         bins2), ncol = 2, 
                                                       byrow = FALSE)))
  
  bin_dist <- euc_dist(bin_data)
  bin_p <- dnorm(bin_dist, 0, w)
  
  bin_membership <- nabor::knn(bin_data, query = trans_norm_mat, k = 1)$nn.idx
  
  bin_density <- tabulate2(bin_membership, min_val = 1, max_val = bs^2)
  
  background_peaks <- bg_sample_helper(bin_membership - 1, bin_p, bin_density, 
                                       niterations)
  
  return(background_peaks)
  
}
