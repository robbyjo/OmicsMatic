# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE)
stopifnot(length(args)==3L)
source(args[[1L]])
count <- audit.arrow1201(args[[2L]],args[[3L]])
cat("PASS_ORIGINAL_ARROW1201_IO_QUERY_SOURCE_AST",count,"\n")
