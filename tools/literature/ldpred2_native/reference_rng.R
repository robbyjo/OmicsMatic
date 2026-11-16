# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Runs the actual R generators; no replacement normal or uniform algorithm.
args <- commandArgs(TRUE)
stopifnot(length(args) == 2L, !file.exists(args[1]), !file.exists(args[2]))
options(digits=17, scipen=999)
RNGkind("L'Ecuyer-CMRG", "Inversion", "Rejection")
set.seed(4096)
rows <- list()
append_rows <- function(kind, values) {
  text <- if (is.integer(values)) as.character(values) else sprintf("%.17g", values)
  rows[[length(rows) + 1L]] <<- data.frame(kind=kind, index=seq_along(values)-1L, value=text)
}
append_rows("seed_before", .Random.seed)
append_rows("uniform", runif(128))
append_rows("seed_after_uniform", .Random.seed)
append_rows("normal", rnorm(128))
append_rows("seed_after_normal", .Random.seed)
for (i in seq_len(128)) {
  append_rows("mixed_uniform", runif(1))
  append_rows("mixed_normal", rnorm(1))
}
append_rows("seed_after_mixed", .Random.seed)
write.table(do.call(rbind, rows), args[1], sep="\t", quote=FALSE, row.names=FALSE)
writeLines(c(capture.output(sessionInfo()), "", "R RNG routines executed unmodified; explicit L'Ecuyer-CMRG / Inversion state."), args[2])
