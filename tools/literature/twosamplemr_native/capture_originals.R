# Owned observer: source definitions are loaded unchanged from the pinned mr.R.
args <- commandArgs(TRUE)
stopifnot(length(args) == 2L)
input <- normalizePath(args[[1]], winslash = "/", mustWork = TRUE)
out <- normalizePath(args[[2]], winslash = "/", mustWork = TRUE)
e <- new.env(parent = globalenv())
sys.source(file.path(input, "mr.R"), envir = e, keep.source = TRUE)
saveRDS(list(R = R.version, session = sessionInfo(), external = extSoftVersion(),
             lm_fit_tolerance = formals(stats::lm.fit)$tol,
             lm_wfit_tolerance = formals(stats::lm.wfit)$tol,
             RNG = RNGkind()), file.path(out, "R-PROVENANCE.rds"), version = 3)
capture.output(sessionInfo(), file = file.path(out, "session.txt"))
cases <- read.delim(file.path(input, "cases.tsv"), check.names = FALSE)
data <- read.delim(file.path(input, "input.tsv"), check.names = FALSE)
fields <- c("b", "se", "pval", "nsnp", "b_i", "se_i", "pval_i", "Q", "Q_df", "Q_pval")
stats <- file(file.path(out, "statistics.tsv"), "wt")
draws <- file(file.path(out, "draws.tsv"), "wt")
errors <- file(file.path(out, "errors.tsv"), "wt")
writeLines("case_id\tmethod\tfield\tkind\tvalue", stats)
writeLines("case_id\tmethod\treplicate\tvariant\texposure\toutcome", draws)
writeLines("case_id\tmethod\terror", errors)
emit <- function(id, method, name, value) {
  if (length(value) == 0L) {
    writeLines(paste(id, method, name, "EMPTY", "", sep = "\t"), stats)
  } else {
    stopifnot(length(value) == 1L)
    kind <- if (is.nan(value)) "NAN" else if (is.na(value)) "NA" else if (is.infinite(value)) {
      if (value > 0) "POSITIVE_INFINITY" else "NEGATIVE_INFINITY"
    } else "FINITE"
    text <- if (kind == "FINITE") sprintf("%.17g", value) else ""
    writeLines(paste(id, method, name, kind, text, sep = "\t"), stats)
  }
}
methods <- c("mr_wald_ratio", "mr_meta_fixed_simple", "mr_ivw", "mr_ivw_fe", "mr_ivw_mre", "mr_uwr", "mr_egger_regression", "mr_sign", "mr_weighted_median", "mr_simple_median")
for (i in seq_len(nrow(cases))) {
  id <- cases$case_id[[i]]
  d <- data[data$case_id == id, , drop = FALSE]
  bx <- d$b_exp; by <- d$b_out; sx <- d$se_exp; sy <- d$se_out
  for (method in methods) {
    warnings <- character()
    seed <- 314159L + i
    set.seed(seed)
    parameters <- e$default_parameters(); parameters$nboot <- cases$nboot[[i]]
    value <- withCallingHandlers(tryCatch(e[[method]](bx, by, sx, sy, parameters),
        error = function(error) structure(list(message = conditionMessage(error)), class = "captured_error")),
        warning = function(warning) { warnings <<- c(warnings, conditionMessage(warning)); invokeRestart("muffleWarning") })
    rng_after <- .Random.seed
    saveRDS(list(result = value, warnings = warnings, rng_after = rng_after, seed = seed,
                 input = d, parameters = parameters), file.path(out, paste0(id, "--", method, ".rds")), version = 3)
    if (inherits(value, "captured_error")) {
      writeLines(paste(id, method, gsub("[\t\r\n]", " ", value$message), sep = "\t"), errors)
      next
    }
    for (name in intersect(names(value), fields)) emit(id, method, name, value[[name]])
    if (method %in% c("mr_weighted_median", "mr_simple_median") && length(bx) >= 3L) {
      nboot <- parameters$nboot
      set.seed(seed)
      sampled_x <- matrix(stats::rnorm(nboot * length(bx), rep(bx, each = nboot), rep(sx, each = nboot)), nrow = nboot, ncol = length(bx))
      sampled_y <- matrix(stats::rnorm(nboot * length(bx), rep(by, each = nboot), rep(sy, each = nboot)), nrow = nboot, ncol = length(bx))
      stopifnot(identical(.Random.seed, rng_after))
      weights <- if (method == "mr_weighted_median") 1 / (sy^2 / bx^2 + by^2 * sx^2 / bx^4) else rep(1 / length(bx), length(bx))
      replicate_medians <- suppressWarnings(vapply(seq_len(nboot), function(b) e$weighted_median(sampled_y[b, ] / sampled_x[b, ], weights), numeric(1)))
      stopifnot(identical(stats::sd(replicate_medians), value$se))
      for (b in seq_len(nboot)) for (j in seq_along(bx)) writeLines(paste(id, method, b, j, sprintf("%.17g", sampled_x[b, j]), sprintf("%.17g", sampled_y[b, j]), sep = "\t"), draws)
    }
  }
}
close(stats); close(draws); close(errors)
writeLines("COMPLETE_ORIGINAL_SOURCE_CAPTURE", file.path(out, "COMPLETED.txt"))
