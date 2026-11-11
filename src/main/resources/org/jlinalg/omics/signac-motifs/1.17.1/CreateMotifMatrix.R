CreateMotifMatrix <- function(
  features,
  pwm,
  genome,
  score = FALSE,
  use.counts = FALSE,
  sep = c("-", "-"),
  ...
) {
  if (!requireNamespace("motifmatchr", quietly = TRUE)) {
    stop("Please install motifmatchr.
         https://www.bioconductor.org/packages/motifmatchr/")
  }

  # genome can be string
  if (is.character(x = genome)) {
    if (!requireNamespace("BSgenome", quietly = TRUE)) {
      stop("Please install BSgenome.
             https://www.bioconductor.org/packages/BSgenome/")
    }
    genome <- BSgenome::getBSgenome(genome = genome)
  }

  # check that all seqnames in features are in genome
  # remove missing, replace later with zeros and show warning
  miss_sn <- !(as.character(seqnames(x = features)) %in% seqlevels(x = genome))
  if (sum(miss_sn) > 0) {
    warning("Not all seqlevels present in supplied genome",
      immediate. = TRUE
    )
    # remove from features and remember original order
    feature_order <- features
    features <- features[!miss_sn]
  }
  motif_ix <- motifmatchr::matchMotifs(
    pwms = pwm,
    subject = features,
    genome = genome,
    out = "scores",
    ...
  )
  if (score) {
    motif.matrix <- motifmatchr::motifScores(object = motif_ix)
  } else {
    if (use.counts) {
      motif.matrix <- motifmatchr::motifCounts(object = motif_ix)
    } else {
      motif.matrix <- motifmatchr::motifMatches(object = motif_ix)
      motif.matrix <- as(Class = "CsparseMatrix", object = motif.matrix)
    }
  }
  rownames(motif.matrix) <- GRangesToString(grange = features, sep = sep)
  if (is.null(x = names(x = pwm))) {
    warning(
      "No 'names' attribute found in PFMatrixList. ",
      "Extracting names from individual entries."
    )
    colnames(x = motif.matrix) <- vapply(
      X = pwm, FUN = slot, FUN.VALUE = "character", "name"
    )
  }
  # add missing features
  if (sum(miss_sn) > 0) {
    replacement_matrix <- sparseMatrix(
      i = sum(miss_sn),
      j = ncol(x = motif.matrix)
    )
    rownames(x = replacement_matrix) <- GRangesToString(
      grange = feature_order[miss_sn], sep = sep
    )
    colnames(x = replacement_matrix) <- colnames(x = motif.matrix)
    motif.matrix <- rbind(motif.matrix, replacement_matrix)
    motif.matrix <- motif.matrix[GRangesToString(
      grange = feature_order, sep = sep
    ), ]
  }
  return(motif.matrix)
}
