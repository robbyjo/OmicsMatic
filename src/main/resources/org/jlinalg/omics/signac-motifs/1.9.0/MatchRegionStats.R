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
  if (nrow(x = meta.feature) < n) {
    n <- nrow(x = meta.feature)
    warning("Requested more features than present in supplied data.
            Returning ", n, " features")
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
    if (verbose) {
      message("Matching ", featmatch, " distribution")
    }
    density.estimate <- density(
      x = query.feature[[featmatch]], kernel = "gaussian", bw = 1
    )
    mf.use <- meta.feature[!is.na(x = meta.feature[[featmatch]]), ]
    weights <- approx(
      x = density.estimate$x,
      y = density.estimate$y,
      xout = mf.use[[featmatch]],
      yright = 0.0001,
      yleft = 0.0001
    )$y
    if (i > 1) {
      feature.weights <- feature.weights * weights
    } else {
      feature.weights <- weights
    }
  }
  feature.select <- sample.int(
    n = nrow(x = mf.use),
    size = n,
    prob = feature.weights
  )
  feature.select <- rownames(x = mf.use)[feature.select]
  return(feature.select)
}
