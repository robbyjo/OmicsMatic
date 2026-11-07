LinksToGRanges <- function(linkmat, gene.coords, sep = c("-", "-")) {
  # get TSS for each gene
  tss <- resize(gene.coords, width = 1, fix = 'start')
  gene.idx <- sapply(
    X = rownames(x = linkmat),
    FUN = function(x) {
      which(x = x == tss$gene_name)[[1]]
    }
  )
  tss <- tss[gene.idx]

  # get midpoint of each peak
  peak.ranges <- StringToGRanges(
    regions = colnames(x = linkmat),
    sep = sep
  )
  midpoints <- start(x = peak.ranges) + (width(x = peak.ranges) / 2)

  # convert to triplet form
  dgtm <- as(object = linkmat, Class = "TsparseMatrix")

  # create dataframe
  df <- data.frame(
    chromosome = as.character(x = seqnames(x = peak.ranges)[dgtm@j + 1]),
    tss = start(x = tss)[dgtm@i + 1],
    pk = midpoints[dgtm@j + 1],
    score = dgtm@x,
    gene = rownames(x = linkmat)[dgtm@i + 1],
    peak = colnames(x = linkmat)[dgtm@j + 1]
  )

  # work out start and end coords
  df$start <- ifelse(test = df$tss < df$pk, yes = df$tss, no = df$pk)
  df$end <- ifelse(test = df$tss < df$pk, yes = df$pk, no = df$tss)
  df$tss <- NULL
  df$pk <- NULL

  # convert to granges
  gr.use <- makeGRangesFromDataFrame(df = df, keep.extra.columns = TRUE)
  return(sort(x = gr.use))
}
