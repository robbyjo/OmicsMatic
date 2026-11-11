# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Observer-only diagnostic: original base message/gettext domain conversion, no namespace writes.
args <- commandArgs(TRUE);.libPaths(args[-seq_len(2L)],include.site=FALSE)
stopifnot(requireNamespace("biomaRt",quietly=TRUE))
r <- jsonlite::fromJSON(args[[1L]],simplifyVector=FALSE);xml <- r$expected_native_queries$quote[[1L]]
capture <- function(expression){value <- NULL;withCallingHandlers(force(expression),message=function(m){value <<- conditionMessage(m);invokeRestart("muffleMessage")});value}
result <- list(encoding=Encoding(xml),global=capture(message(xml)),no_domain=capture(message(xml,domain=NA)),explicit_package_domain=capture(message(xml,domain="R-biomaRt")),original_namespace_domain=capture(eval(substitute(message(VALUE),list(VALUE=xml)),envir=asNamespace("biomaRt"))),namespace_message_environment=environmentName(environment(get("message",envir=asNamespace("biomaRt")))))
jsonlite::write_json(result,args[[2L]],auto_unbox=TRUE,pretty=TRUE)
