GRangesToString <- function(grange, sep = c("-", "-")) {
  lifecycle::deprecate_soft(when = "1.17.0", what = "GRangesToString()")
  regions <- paste0(
    as.character(x = seqnames(x = grange)),
    sep[[1]],
    start(x = grange),
    sep[[2]],
    end(x = grange)
  )
  return(regions)
}
