MatchRegionStats <- function(
  meta.feature,
  query.feature,
  features.match = c("GC.percent"),
  n = 10000,
  verbose = TRUE,
  ...
) {
  if (!inherits(x = meta.feature, what = 'data.frame')) {
    stop("meta.feature should be a data.frame")
  }
  if (!inherits(x = query.feature, what = "data.frame")) {
    stop("query.feature should be a data.frame")
  }
  if (length(x = features.match) == 0) {
    stop("Must supply at least one sequence characteristic to match")
  }
  for (i in seq_along(along.with = features.match)) {
    featmatch <- features.match[[i]]
    if (!(featmatch %in% colnames(x = query.feature))) {
      if (featmatch == "GC.percent") {
        stop("GC.percent not present in meta.features.",
             " Run RegionStats to compute GC.percent for each feature.")
      } else {
        stop(featmatch, " not present in meta.features")
      }
    }
    # remove features that have NA for any of the features to match
    meta.feature <- na.omit(object = meta.feature[, features.match, drop = FALSE])
    if (nrow(x = meta.feature) < n) {
      n <- nrow(x = meta.feature)
      warning("Requested more features than present in supplied data.
            Returning ", n, " features")
    }
    if (verbose) {
      message("Matching ", featmatch, " distribution")
    }
    density.query <- density(
      x = query.feature[[featmatch]], kernel = "gaussian", bw = 1
    )
    density.meta  <- density(
      x = meta.feature[[featmatch]], kernel = "gaussian", bw = 1
    )
    
    qvals <- approx(
      x = density.query$x,
      y = density.query$y,
      xout = meta.feature[[featmatch]],
      yleft = 1e-6,
      yright = 1e-6
    )$y

    mvals <- approx(
      x = density.meta$x,
      y = density.meta$y,
      xout = meta.feature[[featmatch]],
      yleft = 1e-6,
      yright = 1e-6
    )$y

    weights <- qvals / mvals
    weights[!is.finite(weights)] <- 0
    
    if (i > 1) {
      feature.weights <- feature.weights * weights
    } else {
      feature.weights <- weights
    }
  }
  
  if (requireNamespace(package = "wrswoR", quietly = TRUE)) {
    feature.select <- wrswoR::sample_int_crank(
      n = nrow(x = meta.feature),
      size = n,
      prob = feature.weights
    )
  } else {
    feature.select <- sample.int(
      n = nrow(x = meta.feature),
      size = n,
      prob = feature.weights
    )
  }
  feature.select <- rownames(x = meta.feature)[feature.select]
  return(feature.select)
}
