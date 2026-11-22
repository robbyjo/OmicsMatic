# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Check saved objects without serializing upstream function bodies into a report.
local({
  args <- commandArgs(TRUE);stopifnot(length(args)==3L)
  .libPaths(c(args[[1L]],.libPaths()));requireNamespace("Matrix")
  ns <- asNamespace("scrattch.hicat");bodies <- list()
  for(name in ls(ns,all.names=TRUE)) {
    value <- get(name,ns,inherits=FALSE)
    if(is.function(value)) {
      key <- digest::digest(serialize(body(value),NULL,version=3),algo="sha256",serialize=FALSE)
      bodies[[key]] <- c(bodies[[key]],name)
    }
  }
  files <- readLines(args[[2L]],warn=FALSE);results <- list();hits <- list();uninspected <- list()
  for(path in files) {
    nodes <- 0L;functions <- 0L
    walk <- function(node,location="") {
      if(is.function(node)) {
        functions <<- functions+1L
        key <- digest::digest(serialize(body(node),NULL,version=3),algo="sha256",serialize=FALSE)
        if(!is.null(bodies[[key]]))hits[[length(hits)+1L]] <<- list(file=path,location=location,upstream_functions=bodies[[key]])
        return(invisible(NULL))
      }
      if(is.environment(node)||typeof(node)=="externalptr") {
        uninspected[[length(uninspected)+1L]] <<- list(file=path,location=location,type=typeof(node))
        return(invisible(NULL))
      }
      if(is.call(node)) {
        nodes <<- nodes+1L
        key <- digest::digest(serialize(node,NULL,version=3),algo="sha256",serialize=FALSE)
        if(!is.null(bodies[[key]]))hits[[length(hits)+1L]] <<- list(file=path,location=location,upstream_functions=bodies[[key]])
      }
      if(isS4(node))for(name in methods::slotNames(node))walk(methods::slot(node,name),paste0(location,"/@",name))
      else if(is.call(node)||is.expression(node)||is.pairlist(node)||is.list(node))
        for(i in seq_along(node))tryCatch(walk(node[[i]],paste0(location,"/",i)),error=function(c) {
          if(!grepl("argument.*missing",conditionMessage(c)))stop(c)
        })
    }
    object <- readRDS(path);walk(object)
    results[[path]] <- list(language_nodes=nodes,serialized_function_objects=functions)
  }
  okay <- !length(hits)&&!length(uninspected)
  jsonlite::write_json(list(status=if(okay)"PASS_RDS_NO_ORIGINAL_HICAT_FUNCTION_BODIES_ENVIRONMENTS_OR_POINTERS_DETECTED"else"PRIVATE_RDS_REVIEW_REQUIRED",
    files=results,upstream_body_hits=hits,uninspected_objects=uninspected,
    original_function_bodies_in_report=FALSE,license_discrepancy_resolved=FALSE),args[[3L]],pretty=TRUE,auto_unbox=TRUE,null="null")
  if(!okay)quit(save="no",status=2L,runLast=FALSE)
})
