# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent base-R fit/oracle; the Java implementation is not sourced here.
args <- commandArgs(trailingOnly = TRUE)
if (length(args) != 1L || file.exists(args[[1L]])) stop("Require NEW output directory")
dir.create(args[[1L]], recursive = TRUE)
out <- normalizePath(args[[1L]], winslash = "/", mustWork = TRUE)
options(OutDec = ".", digits = 17)
Sys.setenv(LANG = "C")
fmt <- function(x) sprintf("%.17g", x)
cases <- list(
    balanced = list(level = .95, groups = list(c(1, 2, 3, 4, 5), c(2, 3, 4, 5, 6), c(6, 8, 7, 9, 5))),
    unbalanced = list(level = .95, groups = list(c(-2, 0, 1, 3), c(1, 2, 3), c(2, 5, 4, 6, 3, 8), c(-4, -2, -1, 0, 1))),
    constant_group = list(level = .95, groups = list(c(2, 2, 2), c(1, 2, 4, 5), c(4, 4, 4))),
    singleton = list(level = .95, groups = list(2, c(1, 2, 4), c(-2, -1, 2, 3))),
    two_groups = list(level = .95, groups = list(c(1, 4, 2, 6), c(2, 3, 5, 8, 7))),
    confidence99 = list(level = .99, groups = list(c(-2, 0, 1, 3), c(1, 2, 3), c(2, 5, 4, 6, 3, 8), c(-4, -2, -1, 0, 1))),
    confidence80 = list(level = .80, groups = list(c(1, 2, 3, 4, 5), c(2, 3, 4, 5, 6), c(6, 8, 7, 9, 5))),
    eight_groups = list(level = .95, groups = lapply(1:8, function(i) i * .2 + sin(1:(i+2)) + (1:(i+2)) * .07)),
    equal_means = list(level = .95, groups = list(c(-1, 0, 1), c(-2, 0, 2), c(-3, 0, 3))),
    minimum_df = list(level = .95, groups = list(c(0, 1), c(2, 4)))
)
input <- c("case\tconfidence\tgroup\tvalue")
for (name in names(cases)) {
    example <- cases[[name]]
    for (i in seq_along(example$groups)) {
        for (value in example$groups[[i]]) input <- c(input, paste(name, fmt(example$level), i-1L, fmt(value), sep = "\t"))
    }
}
writeLines(input, file.path(out, "inputs.tsv"), useBytes = TRUE)
# Fit the bytes exported above, so rounding/transport are also part of the oracle.
data <- read.delim(file.path(out, "inputs.tsv"), check.names = FALSE, stringsAsFactors = FALSE)
answers <- c("case\tfirst\tsecond\tdifference\tstandard_error\tq\tadjusted_p\tlower\tupper\tresidual_sd\tdf\tcritical_q")
two_group_answers <- answers
warnings <- list()
for (name in names(cases)) {
    current <- data[data$case == name, , drop = FALSE]
    current$group <- factor(current$group, levels = sort(unique(current$group)))
    level <- unique(current$confidence)
    stopifnot(length(level) == 1L)
    answer <- withCallingHandlers({
        fit <- stats::aov(value ~ group, data = current)
        table <- stats::TukeyHSD(fit, "group", ordered = FALSE, conf.level = level)$group
        means <- as.numeric(tapply(current$value, current$group, mean))
        sizes <- as.numeric(table(current$group))
        df <- stats::df.residual(fit)
        residual_sd <- sqrt(sum(stats::residuals(fit)^2) / df)
        list(table = table, means = means, sizes = sizes, df = df, residual_sd = residual_sd,
             critical = stats::qtukey(level, length(sizes), df))
    }, warning = function(w) {
        warnings[[length(warnings)+1L]] <<- list(case = name, message = conditionMessage(w))
        invokeRestart("muffleWarning")
    })
    for (first in seq_len(length(answer$sizes)-1L)) for (second in (first+1L):length(answer$sizes)) {
        label <- paste(second-1L, first-1L, sep = "-")
        row <- answer$table[label, ]
        se <- answer$residual_sd * sqrt(1/answer$sizes[first] + 1/answer$sizes[second])
        q <- abs(row[["diff"]]) / (se/sqrt(2))
        numbers <- c(row[["diff"]], se, q, row[["p adj"]], row[["lwr"]], row[["upr"]], answer$residual_sd, answer$df, answer$critical)
        stopifnot(all(is.finite(numbers)))
        answers <- c(answers, paste(c(name, first-1L, second-1L, fmt(numbers)), collapse = "\t"))
        if (length(answer$sizes)==2L) {
            # Preserve TukeyHSD's original values above. For two groups the exact
            # identity Q=sqrt(2)*abs(T) avoids its quadrature/quantile errors.
            critical <- sqrt(2)*stats::qt((1-level)/2,answer$df,lower.tail=FALSE)
            width <- se*critical/sqrt(2)
            numbers <- c(row[["diff"]],se,q,2*stats::pt(q/sqrt(2),answer$df,lower.tail=FALSE),
                         row[["diff"]]-width,row[["diff"]]+width,answer$residual_sd,answer$df,critical)
            two_group_answers <- c(two_group_answers,paste(c(name,first-1L,second-1L,fmt(numbers)),collapse="\t"))
        }
    }
}
writeLines(answers, file.path(out, "expected.tsv"), useBytes = TRUE)
writeLines(two_group_answers, file.path(out,"two-group-exact.tsv"), useBytes=TRUE)
capture.output(sessionInfo(), file = file.path(out, "SESSION.txt"))
capture.output(dput(warnings), file = file.path(out, "WARNINGS.R.txt"))
stopifnot(length(warnings) == 0L)
writeLines(c("PASS_ORIGINAL_R_AOV_TUKEYHSD_REFERENCE", paste("cases", length(cases)), paste("comparisons", length(answers)-1L)), file.path(out, "COMPLETED.txt"))
