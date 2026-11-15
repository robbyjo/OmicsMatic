# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Compare complete installed namespace ASTs privately; record digests and locators.
hicat.audit.package <- function(package,source,expected=NULL) {
  definitions <- aliases <- factories <- list()
  walk <- function(node,path,location="") {
    if(is.call(node)&&is.symbol(node[[1L]])&&as.character(node[[1L]]) %in% c("<-","=")&&is.symbol(node[[2L]])) {
      name <- as.character(node[[2L]]);rhs <- node[[3L]]
      while(is.call(rhs)&&is.symbol(rhs[[1L]])&&as.character(rhs[[1L]]) %in% c("<-","="))rhs <- rhs[[3L]]
      if(is.call(rhs)&&identical(rhs[[1L]],as.name("function")))
        definitions[[name]] <<- c(definitions[[name]],list(list(path=path,location=location,formals=rhs[[2L]],body=rhs[[3L]])))
      if(is.symbol(rhs))aliases[[name]] <<- union(aliases[[name]],as.character(rhs))
    }
    if(is.call(node)||is.expression(node)||is.pairlist(node)||is.list(node))
      for(i in seq_along(node))tryCatch(walk(node[[i]],path,paste0(location,"/",i)),error=function(c) {
        if(!grepl("argument.*missing",conditionMessage(c)))stop(c)
      })
  }
  for(path in sort(list.files(file.path(source,"R"),pattern="[.][rR]$",full.names=TRUE))) {
    parsed <- parse(path,keep.source=FALSE);walk(parsed,basename(path))
    for(node in parsed)
      if(is.call(node)&&is.symbol(node[[1L]])&&as.character(node[[1L]]) %in% c("<-","=")&&is.symbol(node[[2L]])&&
        is.call(node[[3L]])&&identical(node[[3L]][[1L]],as.name("colorRampPalette")))
        factories[[as.character(node[[2L]])]] <- list(path=basename(path),expression=node[[3L]])
  }
  candidates <- function(name,visited=character()) {
    if(name %in% visited)return(list())
    c(definitions[[name]],unlist(lapply(aliases[[name]],function(alias)candidates(alias,c(visited,name))),recursive=FALSE))
  }
  ns <- asNamespace(package)
  functions <- Filter(function(n)is.function(get(n,ns,inherits=FALSE)),ls(ns,all.names=TRUE))
  stopifnot(length(functions)>0L,is.null(expected)||length(functions)==expected)
  checked <- list()
  for(name in functions) {
    actual <- get(name,ns,inherits=FALSE)
    matches <- Filter(function(x)identical(formals(actual),x$formals)&&identical(body(actual),x$body),candidates(name))
    factory <- factories[[name]];factory.match <- FALSE
    if(!length(matches)&&!is.null(factory)) {
      direct <- eval(factory$expression,envir=ns)
      factory.match <- identical(formals(actual),formals(direct))&&identical(body(actual),body(direct))
    }
    if(!length(matches)&&!factory.match)stop(paste("Installed function differs from all original AST candidates:",package,name))
    checked[[name]] <- list(matches=lapply(matches,function(x)x[c("path","location")]),
      factory_path=if(factory.match)factory$path else NULL,
      formal_sha256=digest::digest(serialize(formals(actual),NULL,version=3),algo="sha256",serialize=FALSE),
      body_sha256=digest::digest(serialize(body(actual),NULL,version=3),algo="sha256",serialize=FALSE))
  }
  list(package=package,version=as.character(utils::packageVersion(package)),functions=checked,
    original_function_bodies_in_report=FALSE)
}

hicat.audit <- function(control,output,expected=NULL) {
  .libPaths(c(control$library,control$base_library))
  stopifnot(identical(as.character(getRversion()),"4.2.3"))
  packages <- c("scrattch.hicat","RANN");audits <- list()
  for(package in packages) {
    stopifnot(identical(normalizePath(find.package(package)),normalizePath(file.path(control$library,package))))
    audits[[package]] <- hicat.audit.package(package,control$source_paths[[package]],
      if(is.null(expected))NULL else expected[[package]])
    dll <- getLoadedDLLs()[[package]][["path"]]
    stopifnot(identical(normalizePath(dll),normalizePath(file.path(control$library,package,"libs",paste0(package,.Platform$dynlib.ext)))))
    audits[[package]]$native_library_path <- dll
  }
  stopifnot(identical(audits[["scrattch.hicat"]]$version,"1.0.9"),identical(audits$RANN$version,"2.6.1"),
    "doubletFinder" %in% getNamespaceExports("scrattch.hicat"),
    all(c("doubletFinder","logCPM","cpm","rd_PCA","get_PCA") %in% names(audits[["scrattch.hicat"]]$functions)),
    "nn2" %in% names(audits$RANN$functions))
  versions <- vapply(c("scrattch.hicat","RANN","Matrix","Rcpp","RcppEigen","RcppParallel","BiocNeighbors",
    "beachmat","arrow","digest","ggplot2","S4Vectors"),function(p)as.character(utils::packageVersion(p)),character(1))
  if(!is.null(control$dependency_versions))stopifnot(identical(as.list(versions),control$dependency_versions))
  result <- list(status="PASS_COMPLETE_ORIGINAL_HICAT109_AND_RANN261_NAMESPACE_AST",packages=audits,
    selected_dependency_versions=versions,session=sessionInfo(),
    source_or_dependency_algorithms_patched=FALSE,author_environment_inferred=FALSE,
    upstream_source_bodies_or_runtime_redistribution_authorized=FALSE)
  saveRDS(result,output,version=3);invisible(result)
}
