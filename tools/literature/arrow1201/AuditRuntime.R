# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Audit the original source AST of the public I/O/query functions actually used.
audit.arrow1201 <- function(source, output) {
  stopifnot(identical(as.character(getRversion()), "4.2.3"),
            identical(as.character(utils::packageVersion("arrow")), "12.0.1"),
            identical(as.character(utils::packageVersion("dplyr")), "1.1.4"),
            identical(as.character(utils::packageVersion("jsonlite")), "1.8.8"),
            identical(as.character(utils::packageVersion("bit64")), "4.0.5"))
  info <- arrow::arrow_info()
  stopifnot(identical(info$build_info$cpp_version, "12.0.1"),
            isTRUE(info$capabilities[["parquet"]]), isTRUE(info$capabilities[["dataset"]]),
            isTRUE(info$capabilities[["acero"]]))
  definitions <- new.env(parent=emptyenv())
  collect <- function(node) {
    if(is.call(node) && is.symbol(node[[1L]]) && as.character(node[[1L]]) %in% c("<-", "=") && is.symbol(node[[2L]])) {
      assign(as.character(node[[2L]]), node[[3L]], envir=definitions)
      collect(node[[3L]])
    }
  }
  for(path in list.files(file.path(source,"R"), pattern="[.]R$", full.names=TRUE))
    for(node in parse(path, keep.source=FALSE)) collect(node)
  targets <- c("read_parquet", "open_dataset", "filter.arrow_dplyr_query", "filter.Dataset",
    "select.arrow_dplyr_query", "select.Dataset", "arrange.arrow_dplyr_query", "arrange.Dataset",
    "collect.arrow_dplyr_query", "collect.Dataset", "collect.ArrowTabular",
    "compute.arrow_dplyr_query", "set_cpu_count")
  checked <- list()
  for(name in targets) {
    original <- get(name, envir=definitions, inherits=FALSE)
    visited <- name
    repeat {
      if(is.symbol(original)) {
        alias <- as.character(original)
        stopifnot(!alias %in% visited)
        visited <- c(visited,alias)
        original <- get(alias,envir=definitions,inherits=FALSE)
      } else if(is.call(original) && is.symbol(original[[1L]]) &&
                as.character(original[[1L]]) %in% c("<-","=")) {
        original <- original[[3L]]
      } else break
    }
    stopifnot(is.call(original), identical(original[[1L]],as.name("function")))
    actual <- get(name,envir=asNamespace("arrow"),inherits=FALSE)
    stopifnot(is.function(actual), identical(formals(actual),original[[2L]]),
              identical(body(actual),original[[3L]]))
    checked[[name]] <- list(formals=formals(actual),body=body(actual))
  }
  saveRDS(list(status="PASS_ORIGINAL_ARROW1201_IO_QUERY_SOURCE_AST", functions=checked,
               arrow_info=info,session=sessionInfo()), output, version=3)
  invisible(length(checked))
}
