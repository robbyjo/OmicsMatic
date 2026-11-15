# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Compare active original function bodies and formals; never replace bindings.
idep244.audit <- function(source, destination) {
  ns <- asNamespace("idepGolem")
  canon <- function(x)paste(deparse(x,width.cutoff=500L,control="keepNA"),collapse="\n")
  checks <- list()
  for (file in sort(list.files(file.path(source,"R"),pattern="[.]R$",full.names=TRUE))) {
    bytes <- readBin(file,"raw",n=file.info(file)$size)
    if (length(bytes)>=3L&&identical(bytes[1:3],as.raw(c(239,187,191))))
      bytes <- bytes[-(1:3)]
    text <- rawToChar(bytes)
    Encoding(text) <- "UTF-8"
    if (!validUTF8(text))stop("Pinned source is not valid UTF-8: ",basename(file))
    parsed <- parse(text=text,keep.source=FALSE,encoding="UTF-8")
    for (expression in parsed) {
      if (!is.call(expression)||length(expression)!=3L||!is.symbol(expression[[1]])||
          !as.character(expression[[1]])%in%c("<-","="))next
      if (!is.symbol(expression[[2]])||!is.call(expression[[3]])||
          !identical(expression[[3]][[1]],as.name("function")))next
      name <- as.character(expression[[2]])
      if (!exists(name,envir=ns,inherits=FALSE))stop("Missing original function: ",name)
      fun <- get(name,envir=ns,inherits=FALSE)
      same <- identical(canon(formals(fun)),canon(expression[[3]][[2]])) &&
        identical(canon(body(fun)),canon(expression[[3]][[3]]))
      checks[[length(checks)+1L]] <- data.frame(file=basename(file),function_name=name,
        body_and_formals_match=same,stringsAsFactors=FALSE)
    }
  }
  checks <- do.call(rbind,checks)
  saveRDS(checks,destination,version=3,compress=FALSE)
  # Original source defines data_frame_with_list twice. A later definition
  # legitimately replaces an earlier one; each active name must match a definition.
  active <- tapply(checks$body_and_formals_match,checks$function_name,any)
  stopifnot(nrow(checks)==260L,length(active)==259L,all(active))
  invisible(checks)
}
