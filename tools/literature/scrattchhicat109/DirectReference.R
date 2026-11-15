# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent fresh-process direct call; this driver does not use Worker.R.
local({
  args <- commandArgs(TRUE);stopifnot(length(args)==4L)
  .libPaths(c(args[[1L]],args[[2L]]));out <- args[[4L]]
  stopifnot(!dir.exists(out));dir.create(out,recursive=TRUE)
  requireNamespace("scrattch.hicat");requireNamespace("RANN");requireNamespace("Matrix")
  request <- readRDS(args[[3L]])
  for(package in request$caller_packages)library(package,character.only=TRUE)
  if(length(request$options))options(request$options)
  rng <- function()list(kind=RNGkind(),seed_exists=exists(".Random.seed",globalenv(),inherits=FALSE),
    seed=if(exists(".Random.seed",globalenv(),inherits=FALSE))get(".Random.seed",globalenv())else NULL)
  conditions <- list();error <- NULL;returned <- FALSE;value <- NULL
  do.call(RNGkind,as.list(request$rng$kind));assign(".Random.seed",request$rng$seed,globalenv())
  before <- rng()
  record <- function(c,kind)conditions[[length(conditions)+1L]] <<- list(kind=kind,condition=c,
    class=class(c),message=conditionMessage(c),call=conditionCall(c))
  tryCatch(withCallingHandlers({
    value <- do.call("doubletFinder",request$arguments,envir=asNamespace("scrattch.hicat"))
    returned <- TRUE
  },warning=function(c)record(c,"warning"),message=function(c)record(c,"message"),
    error=function(c) {
      record(c,"error");saveRDS(list(error=c,calls=sys.calls()),file.path(out,"complete-error-and-calls.rds"),version=3)
    }),error=function(c){error <<- c})
  after <- rng()
  if(returned)saveRDS(value,file.path(out,"complete-return.rds"),version=3)
  saveRDS(list(before=before,after=after),file.path(out,"complete-rng.rds"),version=3)
  saveRDS(list(conditions=conditions,error=error),file.path(out,"complete-conditions.rds"),version=3)
  saveRDS(list(request=request,search_path=search(),loaded_namespaces=loadedNamespaces(),
    selected_options=options()[c("warn","warnPartialMatchDollar")]),file.path(out,"complete-caller-context.rds"),version=3)
  writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"))
  jsonlite::write_json(list(status=if(returned)"ORIGINAL_COMPLETE_RETURN"else"ORIGINAL_ERROR_NO_RESULT",
    returned=returned,error=if(is.null(error))NULL else conditionMessage(error),
    conditions=length(conditions),author_inputs_inferred=FALSE),file.path(out,"STATUS.json"),
    pretty=TRUE,auto_unbox=TRUE,null="null")
  if(!returned)quit(save="no",status=2L,runLast=FALSE)
})
