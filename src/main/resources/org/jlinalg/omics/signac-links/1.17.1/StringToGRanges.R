StringToGRanges <- function(regions, sep = c("-", "-"), ...) {
  lifecycle::deprecate_soft(when = "1.17.0", what = "StringToGRanges()")
  ranges.df <- data.frame(ranges = regions)
  ranges.df <- separate(
    data = ranges.df,
    col = "ranges",
    sep = paste0(sep[[1]], "|", sep[[2]]),
    into = c("chr", "start", "end")
  )
  granges <- makeGRangesFromDataFrame(df = ranges.df, ...)
  return(granges)
}
