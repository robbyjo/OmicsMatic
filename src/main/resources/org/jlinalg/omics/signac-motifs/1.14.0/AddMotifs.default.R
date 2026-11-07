AddMotifs.default <- function(
  object,
  genome,
  pfm,
  verbose = TRUE,
  ...
) {
  if (!requireNamespace("motifmatchr", quietly = TRUE)) {
    stop("Please install motifmatchr.\n",
         "https://www.bioconductor.org/packages/motifmatchr/")
  }
  if (is.null(x = names(x = pfm))) {
    warning("No 'names' attribute found in PFMatrixList. ",
            "Extracting names from individual entries.", immediate. = TRUE)
    names(x = pfm) <- vapply(
      X = pfm, FUN = slot, FUN.VALUE = "character", "name"
    )
  }
  if (verbose) {
    message("Building motif matrix")
  }
  # genome can be string
  if (is.character(x = genome)) {
    if (!requireNamespace("BSgenome", quietly = TRUE)) {
      stop("Please install BSgenome.
             https://www.bioconductor.org/packages/BSgenome/")
    }
    genome <- BSgenome::getBSgenome(genome = genome)
  }
  motif.matrix <- CreateMotifMatrix(
    features = object,
    pwm = pfm,
    genome = genome,
    use.counts = FALSE
  )
  if (verbose) {
    message("Finding motif positions")
  }
  
  # for positions, a list of granges is returned
  # each element of list is a PFM name
  # each entry in granges is the position within a feature that matches motif
  obj_keep <- as.character(seqnames(x = object)) %in% seqlevels(x = genome)
  motif.positions <- motifmatchr::matchMotifs(
    pwms = pfm,
    subject = object[obj_keep],
    out = 'positions',
    genome = genome
  )
  if (verbose) {
    message("Creating Motif object")
  }
  motif <- CreateMotifObject(
    data = motif.matrix,
    positions = motif.positions,
    pwm = pfm
  )
  return(motif)
}
