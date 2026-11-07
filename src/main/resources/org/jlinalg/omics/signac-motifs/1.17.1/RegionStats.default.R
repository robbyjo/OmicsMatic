RegionStats.default <- function(
  object,
  genome,
  verbose = TRUE,
  ...
) {
  if (!requireNamespace("BSgenome", quietly = TRUE)) {
    stop("Please install BSgenome: BiocManager::install('BSgenome')")
  }
  if (!requireNamespace("Biostrings", quietly = TRUE)) {
    stop("Please install Biostrings: BiocManager::install('Biostrings')")
  }
  sequence.length <- width(x = object)
  common.seq <- intersect(x = seqlevels(x = object), y = seqlevels(x = genome))
  if (length(x = common.seq) < length(x = seqlevels(x = object))) {
    warning("Not all seqlevels present in supplied genome", immediate. = TRUE)
  }
  seq.keep <- as.character(x = seqnames(x = object)) %in% common.seq
  enum <- seq_along(along.with = seq.keep)
  object <- object[seq.keep]
  object <- keepSeqlevels(x = object, value = common.seq, pruning.mode = "coarse")
  sequences <- Biostrings::getSeq(x = genome, object)
  gc <- Biostrings::letterFrequency(
    x = sequences, letters = "CG"
  ) / sequence.length[seq.keep] * 100
  colnames(gc) <- "GC.percent"
  dinuc <- Biostrings::dinucleotideFrequency(sequences)
  sequence.stats <- cbind(dinuc, gc)
  # fill missing seqnames with NA
  nadf <- as.data.frame(
    x = matrix(nrow = sum(!seq.keep), ncol = ncol(x = sequence.stats))
  )
  colnames(x = nadf) <- colnames(x = sequence.stats)
  rownames(x = nadf) <- enum[!seq.keep]
  rownames(x = sequence.stats) <- enum[seq.keep]
  sequence.stats <- rbind(sequence.stats, nadf)
  sequence.stats <- sequence.stats[enum, ]
  sequence.stats <- cbind(sequence.stats, sequence.length)
  return(sequence.stats)
}
