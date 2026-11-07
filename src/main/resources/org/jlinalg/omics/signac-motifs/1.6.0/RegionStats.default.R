RegionStats.default <- function(
  object,
  genome,
  verbose = TRUE,
  ...
) {
  if (!requireNamespace('BSgenome', quietly = TRUE)) {
    stop("Please install BSgenome: BiocManager::install('BSgenome')")
  }
  if (!requireNamespace('Biostrings', quietly = TRUE)) {
    stop("Please install Biostrings: BiocManager::install('Biostrings')")
  }
  sequence.length <- width(x = object)
  sequences <- BSgenome::getSeq(x = genome, names = object)
  gc <- Biostrings::letterFrequency(
    x = sequences, letters = 'CG'
  ) / sequence.length * 100
  colnames(gc) <- 'GC.percent'
  dinuc <- Biostrings::dinucleotideFrequency(sequences)
  sequence.stats <- cbind(dinuc, gc, sequence.length)
  return(sequence.stats)
}
