# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE);stopifnot(length(args)==1L)
control <- jsonlite::fromJSON(args[[1L]],simplifyVector=FALSE)
source(control$audit)
audit.bigcat(control,control$audit_output)
cat("PASS_COMPLETE_ORIGINAL_BIGCAT_PROFILE_SOURCE_AST\n")
