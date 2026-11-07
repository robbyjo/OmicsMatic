RegionStats.ChromatinAssay <- function(
  object,
  genome,
  verbose = TRUE,
  ...
) {
  regions <- granges(x = object)
  feature.metadata <- RegionStats(
    object = regions,
    genome = genome,
    verbose = verbose,
    ...
  )
  rownames(x = feature.metadata) <- rownames(x = object)
  meta.data <- GetAssayData(object = object, slot = "meta.features")
  feature.metadata <- feature.metadata[rownames(x = meta.data), ]
  meta.data <- cbind(meta.data, feature.metadata)
  slot(object = object, name = "meta.features") <- meta.data
  return(object)
}
