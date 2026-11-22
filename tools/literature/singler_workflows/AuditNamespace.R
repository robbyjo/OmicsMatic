# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Compare every installed namespace function with parsed unchanged original source.
singler.audit.package <- function(package,source) {
  definitions <- aliases <- method.definitions <- list()
  walk <- function(node,path,location="") {
    if(is.call(node)&&is.symbol(node[[1L]])&&identical(as.character(node[[1L]]),"setGeneric")) {
      declaration <- match.call(methods::setGeneric,node)
      if(is.character(declaration$name)&&is.call(declaration$def)&&identical(declaration$def[[1L]],as.name("function"))) {
        name <- declaration$name;rhs <- declaration$def
        definitions[[name]] <<- c(definitions[[name]],list(list(path=path,location=location,formals=rhs[[2L]],body=rhs[[3L]])))
      }
    }
    if(is.call(node)&&is.symbol(node[[1L]])&&identical(as.character(node[[1L]]),"setMethod")) {
      declaration <- match.call(methods::setMethod,node)
      if(!is.character(declaration$f))stop("Nonliteral original S4 generic declaration requires explicit audit")
      name <- declaration$f
      method.definitions[[name]] <<- c(method.definitions[[name]],list(list(path=path,location=location,
        signature=declaration$signature,definition=declaration$definition)))
    }
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
  for(path in sort(list.files(file.path(source,"R"),pattern="[.][rR]$",full.names=TRUE)))walk(parse(path,keep.source=FALSE),basename(path))
  candidates <- function(name,visited=character()) {
    if(name %in% visited)return(list())
    c(definitions[[name]],unlist(lapply(aliases[[name]],function(alias)candidates(alias,c(visited,name))),recursive=FALSE))
  }
  ns <- asNamespace(package)
  functions <- Filter(function(n)is.function(get(n,ns,inherits=FALSE)),ls(ns,all.names=TRUE))
  if(package!="GenomeInfoDbData")stopifnot(length(functions)>0L)
  checked <- list()
  for(name in functions) {
    actual <- get(name,ns,inherits=FALSE)
    matches <- Filter(function(x)identical(formals(actual),x$formals)&&identical(body(actual),x$body),candidates(name))
    if(!length(matches))stop(paste("Installed function differs from all original AST candidates:",package,name))
    checked[[name]] <- list(matches=lapply(matches,function(x)x[c("path","location")]),
      formal_sha256=digest::digest(serialize(formals(actual),NULL,version=3),algo="sha256",serialize=FALSE),
      body_sha256=digest::digest(serialize(body(actual),NULL,version=3),algo="sha256",serialize=FALSE))
  }
  method.checks <- list()
  descriptor <- environment.descriptor <- NULL;environment.stack <- list()
  binding.descriptor <- function(value) {
    if(is.function(value))for(name in functions)
      if(identical(value,get(name,ns,inherits=FALSE)))return(list(kind="already_audited_namespace_function",package=package,name=name))
    descriptor(value)
  }
  environment.descriptor <- function(env) {
    if(isNamespace(env))return(list(kind="namespace",name=getNamespaceName(env)))
    if(identical(env,baseenv()))return(list(kind="base"))
    if(identical(env,emptyenv()))return(list(kind="empty"))
    if(identical(env,globalenv()))stop("Unexpected global closure environment in original S4 method")
    if(any(vapply(environment.stack,function(previous)identical(previous,env),logical(1))))stop("Unexpected cyclic local S4 factory frame")
    previous <- environment.stack;environment.stack <<- c(environment.stack,list(env))
    on.exit(environment.stack <<- previous)
    values <- lapply(ls(env,all.names=TRUE),function(n)binding.descriptor(get(n,env,inherits=FALSE)))
    names(values) <- ls(env,all.names=TRUE)
    list(kind="original_factory_frame",bindings=values,parent=environment.descriptor(parent.env(env)))
  }
  descriptor <- function(x) {
    if(is.function(x))return(list(kind="closure",formals=descriptor(formals(x)),body=descriptor(body(x)),environment=environment.descriptor(environment(x))))
    if(is.environment(x))return(environment.descriptor(x))
    if(is.call(x)||is.expression(x)||is.pairlist(x)||is.list(x)) {
      values <- lapply(seq_along(x),function(i)tryCatch(descriptor(x[[i]]),error=function(c) {
        if(grepl("argument.*missing",conditionMessage(c)))list(kind="missing_argument")else stop(c)
      }))
      return(list(kind=typeof(x),attributes=attributes(x),values=values))
    }
    x
  }
  factories <- c(".BUILDINDEX_GENERATOR",".FINDKNN_GENERATOR",".FINDKNN_GENERATOR_NOX",
    ".FINDNEIGHBORS_GENERATOR",".FINDNEIGHBORS_GENERATOR_NOX",".QUERYKNN_GENERATOR",
    ".QUERYKNN_GENERATOR_NOX",".QUERYNEIGHBORS_GENERATOR",".QUERYNEIGHBORS_GENERATOR_NOX")
  for(generic.name in names(method.definitions)) {
    generic <- methods::getGeneric(generic.name,where=ns)
    actual.methods <- methods::findMethods(generic.name,where=ns,inherited=FALSE)
    for(signature.name in names(actual.methods)) {
      actual <- actual.methods[[signature.name]];matches <- list()
      for(entry in method.definitions[[generic.name]]) {
        signature <- methods:::matchSignature(eval(entry$signature,envir=ns),generic,where=ns)
        if(!identical(as.character(signature),as.character(actual@defined))||!identical(names(signature),names(actual@defined)))next
        expr <- entry$definition
        alternatives <- if(is.symbol(expr))lapply(candidates(as.character(expr)),function(x)
          as.call(list(as.name("function"),x$formals,x$body))) else list(expr)
        for(expression in alternatives) {
          # These pinned original BiocNeighbors factories only create closures;
          # their complete source functions have already passed the AST audit.
          # Captured FUN/ARGS values are compared, so identical wrapper code
          # cannot hide a substituted neighbour algorithm.
          factory <- is.call(expression)&&is.symbol(expression[[1L]])&&as.character(expression[[1L]]) %in% factories
          delegate <- is.call(expression)&&length(expression)==3L&&is.symbol(expression[[1L]])&&
            as.character(expression[[1L]]) %in% c("::",":::")&&
            (is.symbol(expression[[2L]])||is.character(expression[[2L]]))&&
            (is.symbol(expression[[3L]])||is.character(expression[[3L]]))
          if(!is.call(expression)||(!identical(expression[[1L]],as.name("function"))&&!factory&&!delegate))
            stop(paste("Unsupported original S4 method factory:",package,generic.name,signature.name,entry$path,paste(deparse(expression),collapse=" ")))
          definition <- eval(expression,envir=ns)
          stopifnot(is.function(definition))
          if(!identical(names(formals(definition)),names(formals(generic))))
            definition <- methods:::rematchDefinition(definition,generic,names(formals(definition)),names(formals(generic)),signature)
          if(identical(descriptor(actual),descriptor(definition)))
            matches[[length(matches)+1L]] <- entry[c("path","location")]
        }
      }
      if(!length(matches))stop(paste("Installed S4 method differs from original signature and canonical R dispatch wrapper:",package,generic.name,signature.name))
      method.checks[[paste(generic.name,signature.name,sep=":")]] <- list(matches=matches,
        formal_sha256=digest::digest(serialize(formals(actual),NULL,version=3),algo="sha256",serialize=FALSE),
        closure_descriptor_sha256=digest::digest(serialize(descriptor(actual),NULL,version=3),algo="sha256",serialize=FALSE))
    }
  }
  list(package=package,version=as.character(utils::packageVersion(package)),functions=checked,
    S4_methods=method.checks,original_function_bodies_in_report=FALSE)
}

singler.audit <- function(control,output) {
  .libPaths(c(control$library,control$base_library))
  stopifnot(identical(as.character(getRversion()),control$r_version))
  audits <- list()
  for(package in names(control$package_versions)) {
    stopifnot(identical(normalizePath(find.package(package)),normalizePath(file.path(control$library,package))))
    audits[[package]] <- singler.audit.package(package,control$source_paths[[package]])
    stopifnot(identical(audits[[package]]$version,control$package_versions[[package]]))
    if(package %in% c("SingleR","beachmat","BiocNeighbors")) {
      dll <- getLoadedDLLs()[[package]][["path"]]
      stopifnot(identical(normalizePath(dll),normalizePath(file.path(control$library,package,"libs",paste0(package,.Platform$dynlib.ext)))))
      audits[[package]]$native_library_path <- dll
      audits[[package]]$registered_routines <- getDLLRegisteredRoutines(getLoadedDLLs()[[package]])
    }
  }
  stopifnot("SingleR" %in% getNamespaceExports("SingleR"))
  result <- list(status="PASS_COMPLETE_ORIGINAL_SINGLER_PROFILE_NAMESPACE_ASTS",packages=audits,
    session=sessionInfo(),source_or_dependency_algorithms_patched=FALSE,author_environment_inferred=FALSE)
  saveRDS(result,output,version=3);invisible(result)
}
