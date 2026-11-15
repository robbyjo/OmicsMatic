# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Match installed functions to unchanged original ASTs; no function is replaced.
audit.bigcat.package <- function(package,source,expected=NULL) {
  definitions <- list();factories <- list();aliases <- list()
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
      if(is.call(node)&&as.character(node[[1L]])[[1L]] %in% c("<-","=")&&is.symbol(node[[2L]])&&
         is.call(node[[3L]])&&identical(node[[3L]][[1L]],as.name("colorRampPalette")))
        factories[[as.character(node[[2L]])]] <- list(path=basename(path),expression=node[[3L]])
  }
  ns <- asNamespace(package)
  functions <- Filter(function(n)is.function(get(n,envir=ns,inherits=FALSE)),ls(ns,all.names=TRUE))
  stopifnot(length(functions)>0L,is.null(expected)||length(functions)==expected)
  checked <- list()
  candidates <- function(name,visited=character()) {
    if(name %in% visited)return(list())
    c(definitions[[name]],unlist(lapply(aliases[[name]],function(alias)candidates(alias,c(visited,name))),recursive=FALSE))
  }
  for(name in functions) {
    actual <- get(name,envir=ns,inherits=FALSE)
    matches <- Filter(function(x)identical(formals(actual),x$formals)&&identical(body(actual),x$body),candidates(name))
    factory <- factories[[name]];factory.match <- FALSE
    if(!length(matches)&&!is.null(factory)) {
      direct <- eval(factory$expression,envir=ns)
      factory.match <- identical(formals(actual),formals(direct))&&identical(body(actual),body(direct))
    }
    if(!length(matches)&&!factory.match)stop(paste("Installed function does not match original AST:",package,name))
    checked[[name]] <- list(matches=lapply(matches,function(x)x[c("path","location")]),
      factory=if(factory.match)factory else NULL,formals=formals(actual),body=body(actual))
  }
  list(package=package,version=as.character(utils::packageVersion(package)),functions=checked)
}

audit.bigcat.arrow <- function(source) {
  stopifnot(identical(as.character(utils::packageVersion("arrow")),"12.0.1"))
  info <- arrow::arrow_info()
  stopifnot(identical(info$build_info$cpp_version,"12.0.1"),isTRUE(info$capabilities[["parquet"]]),
    isTRUE(info$capabilities[["dataset"]]),isTRUE(info$capabilities[["acero"]]))
  definitions <- new.env(parent=emptyenv())
  collect <- function(node) {
    if(is.call(node)&&is.symbol(node[[1L]])&&as.character(node[[1L]]) %in% c("<-","=")&&is.symbol(node[[2L]])) {
      assign(as.character(node[[2L]]),node[[3L]],envir=definitions);collect(node[[3L]])
    }
  }
  for(path in list.files(file.path(source,"R"),pattern="[.]R$",full.names=TRUE))
    for(node in parse(path,keep.source=FALSE))collect(node)
  targets <- c("read_parquet","open_dataset","filter.arrow_dplyr_query","filter.Dataset",
    "select.arrow_dplyr_query","select.Dataset","arrange.arrow_dplyr_query","arrange.Dataset",
    "collect.arrow_dplyr_query","collect.Dataset","collect.ArrowTabular","compute.arrow_dplyr_query","set_cpu_count",
    "write_parquet","write_dataset")
  checked <- list()
  for(name in targets) {
    original <- get(name,definitions,inherits=FALSE);visited <- name
    repeat {
      if(is.symbol(original)) {
        alias <- as.character(original);stopifnot(!alias %in% visited);visited <- c(visited,alias)
        original <- get(alias,definitions,inherits=FALSE)
      } else if(is.call(original)&&is.symbol(original[[1L]])&&as.character(original[[1L]]) %in% c("<-","=")) {
        original <- original[[3L]]
      } else break
    }
    actual <- get(name,asNamespace("arrow"),inherits=FALSE)
    stopifnot(is.call(original),identical(original[[1L]],as.name("function")),
      identical(formals(actual),original[[2L]]),identical(body(actual),original[[3L]]))
    checked[[name]] <- list(formals=formals(actual),body=body(actual))
  }
  list(functions=checked,info=info)
}

audit.bigcat <- function(control,output) {
  stopifnot(identical(as.character(getRversion()),control$selected_R))
  .libPaths(c(control$library,control$base_library))
  stopifnot(identical(normalizePath(find.package("scrattch.bigcat")),
    normalizePath(file.path(control$library,"scrattch.bigcat"))))
  bigcat <- audit.bigcat.package("scrattch.bigcat",control$source_paths[["scrattch.bigcat"]],control$namespace_functions)
  stopifnot(identical(bigcat$version,"0.0.5"),all(unlist(control$operations) %in% names(bigcat$functions)))
  dependencies <- list()
  if(!is.null(control$source_paths[["Rphenograph"]])) {
    stopifnot(identical(normalizePath(find.package("Rphenograph")),normalizePath(file.path(control$library,"Rphenograph"))))
    dependencies$Rphenograph <- audit.bigcat.package("Rphenograph",control$source_paths[["Rphenograph"]])
  }
  arrow <- audit.bigcat.arrow(control$source_paths[["arrow"]])
  if(isTRUE(control$arrow_from_source))stopifnot(identical(normalizePath(find.package("arrow")),
    normalizePath(file.path(control$library,"arrow"))))
  dependencies$arrow <- arrow
  saveRDS(list(status="PASS_COMPLETE_ORIGINAL_BIGCAT_PROFILE_SOURCE_AST",profile=control$profile,
    bigcat=bigcat,dependencies=dependencies,session=sessionInfo(),author_environment_inferred=FALSE),output,version=3)
  invisible(TRUE)
}
