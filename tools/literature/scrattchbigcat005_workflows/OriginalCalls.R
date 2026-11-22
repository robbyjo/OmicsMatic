# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Complete original call records for caller-level workflows; no source replacement.
bigcat.original.recorder <- function(root) {
  stopifnot(!file.exists(root));dir.create(root)
  rng <- function()list(kind=RNGkind(),seed=get(".Random.seed",globalenv()))
  function(label,operation,a) {
    directory <- file.path(root,label);stopifnot(!file.exists(directory));dir.create(directory)
    before <- rng();failure <- NULL;returned <- FALSE;conditions <- list()
    capture <- function(c,kind)conditions[[length(conditions)+1L]] <<-
      list(kind=kind,condition=c,class=class(c),message=conditionMessage(c),call=conditionCall(c))
    value <- tryCatch(withCallingHandlers({
      answer <- do.call(getExportedValue("scrattch.bigcat",operation),a);returned <- TRUE;answer
    },warning=function(c)capture(c,"warning"),message=function(c)capture(c,"message"),
      error=function(c)capture(c,"error")),error=function(c){failure <<- c;NULL})
    saveRDS(list(operation=operation,arguments=a,returned=returned,value=value,error=failure,
      conditions=conditions,RNG_before=before,RNG_after=rng()),file.path(directory,"complete-original-call.rds"),version=3)
    if(!is.null(failure))stop(failure)
    value
  }
}
