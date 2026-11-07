# extend filter to Peak (GRanges class object)
#' @method filter GRanges
#' @importFrom dplyr filter
#' @export
filter.GRanges = function(.data, ..., .by = NULL, .preserve = FALSE) {
  dots = rlang::quos(...)
  as.data.frame(.data) |> 
    dplyr::filter(!!!dots, .by = .by, .preserve = .preserve) |> 
    droplevels() |> 
    GenomicRanges::makeGRangesFromDataFrame(keep.extra.columns = TRUE)
}

# extend mutate to Peak (GRanges class object)
#' @method mutate GRanges
#' @importFrom dplyr mutate
#' @export
mutate.GRanges = function(.data, ..., .by = NULL, 
                           .keep = c("all", "used", "unused", "none"),
                           .before = NULL,
                           .after = NULL) {
  dots = rlang::quos(...)
  df = as.data.frame(.data)
  
  if (!is.null(.before) && !is.null(.after)) {
    stop("You can't supply both `.before` and `.after`.")
  }
  
  if (!is.null(.before)) {
    df = df |> 
      dplyr::mutate(!!!dots, .by = .by, .keep = .keep, .before = .before)
  } else if (!is.null(.after)) {
    df = df |> 
      dplyr::mutate(!!!dots, .by = .by, .keep = .keep, .after = .after)
  } else {
    df = df |> dplyr::mutate(!!!dots, .by = .by, .keep = .keep)
  }
  
  df |> 
    GenomicRanges::makeGRangesFromDataFrame(keep.extra.columns = TRUE)
}

# S4Vectors::rename
#' @method rename GRanges
#' @importFrom rlang quos
#' @export
rename.GRanges = function(x, ...){
  dots = rlang::quos(...)
  as.data.frame(x) |> 
    dplyr::rename(!!!dots) |> 
    GenomicRanges::makeGRangesFromDataFrame(keep.extra.columns = TRUE)
}

#' @method arrange GRanges
#' @importFrom dplyr arrange
#' @export
arrange.GRanges = function(.data, ..., .by_group = FALSE){
  dots = rlang::quos(...)
  as.data.frame(.data) |> 
    dplyr::arrange(!!!dots, .by_group = .by_group) |> 
    GenomicRanges::makeGRangesFromDataFrame(keep.extra.columns = TRUE)
}


