FindMotifs <- function(
  object,
  features,
  background = 40000,
  assay = NULL,
  verbose = TRUE,
  p.adjust.method = "BH",
  ...
) {
  assay <- SetIfNull(x = assay, y = DefaultAssay(object = object))
  background <- SetIfNull(x = background, y = rownames(x = object))
  if (is(object = background, class2 = "numeric")) {
    if (verbose) {
      message("Selecting background regions to match input ",
              "sequence characteristics")
    }
    meta.feature <- GetAssayData(
      object = object,
      assay = assay,
      layer = "meta.features"
    )
    mf.choose <- meta.feature[
      setdiff(x = rownames(x = meta.feature), y = features), , drop = FALSE
    ]
    missing.features <- setdiff(x = features, y = rownames(x = meta.feature))
    if (length(x = missing.features) > 0) {
      warning(
        "The following features were not found in the assay: ",
        missing.features,
        "\nRemoving missing features", immediate. = TRUE)
      features <- intersect(x = features, y = rownames(x = meta.feature))
    }
    mf.query <- meta.feature[features, , drop = FALSE]
    background <- MatchRegionStats(
      meta.feature = mf.choose,
      query.feature = mf.query,
      regions = features,
      n = background,
      verbose = verbose,
      ...
    )
  }
  if (verbose) {
    msg <- ifelse(
      test = length(x = features) > 1,
      yes = " regions",
      no = " region"
    )
    message("Testing motif enrichment in ", length(x = features), msg)
  }
  if (length(x = features) < 10) {
    warning("Testing motif enrichment using a small number of regions is ",
            "not recommended")
  }
  motif.all <- GetMotifData(
    object = object, assay = assay, slot = "data"
  )
  motif.names <- GetMotifData(
    object = object, assay = assay, slot = "motif.names"
  )
  query.motifs <- motif.all[features, , drop = FALSE]
  background.motifs <- motif.all[background, , drop = FALSE]
  query.counts <- colSums(x = query.motifs)
  background.counts <- colSums(x = background.motifs)
  percent.observed <- query.counts / length(x = features) * 100
  percent.background <- background.counts / length(x = background) * 100
  fold.enrichment <- percent.observed / percent.background
  p.list <- vector(mode = "numeric")
  for (i in seq_along(along.with = query.counts)) {
    p.list[[i]] <- phyper(
      q = query.counts[[i]] - 1,
      m = background.counts[[i]],
      n = nrow(x = background.motifs) - background.counts[[i]],
      k = length(x = features),
      lower.tail = FALSE
    )
  }
  results <- data.frame(
    motif = names(x = query.counts),
    observed = query.counts,
    background = background.counts,
    percent.observed = percent.observed,
    percent.background = percent.background,
    fold.enrichment = fold.enrichment,
    pvalue = p.list,
    motif.name = as.vector(
      x = unlist(x = motif.names[names(x = query.counts)])
    ),
    p.adjust = p.adjust(p = p.list, method = p.adjust.method),
    stringsAsFactors = FALSE
  )
  if (nrow(x = results) == 0) {
    return(results)
  } else {
    return(results[order(results[, 7], -results[, 6]), ])
  }
}
