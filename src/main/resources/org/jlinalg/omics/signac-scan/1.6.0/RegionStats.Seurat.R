RegionStats.Seurat <- function(
  object,
  genome,
  assay = NULL,
  verbose = TRUE,
  ...
) {
  assay <- SetIfNull(x = assay, y = DefaultAssay(object = object))
  assay.data <- GetAssay(object = object, assay = assay)
  assay.data <- RegionStats(
    object = assay.data,
    genome = genome,
    verbose = verbose,
    ...
  )
  object[[assay]] <- assay.data
  return(object)
}
