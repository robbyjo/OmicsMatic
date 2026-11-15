# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Explicit caller preparation and post-selection; original functions are untouched.
bigcat.named <- function(x)is.list(x)&&(length(x)==0L||
  (!is.null(names(x))&&!anyNA(names(x))&&all(nzchar(names(x)))&&!anyDuplicated(names(x))))
bigcat.scalar <- function(x)is.character(x)&&length(x)==1L&&!is.na(x)&&nzchar(x)
bigcat.get <- function(x,name)x[[name,exact=TRUE]]
bigcat.unavailable <- function(message)stop(structure(list(message=message,call=NULL),
  class=c("bigcat_requirement_unavailable","error","condition")))

bigcat.output.path <- function(path,control) {
  stopifnot(is.character(path),length(path)==1L,!is.na(path),!grepl("\\\\",path))
  absolute <- if(startsWith(path,"/"))path else file.path(control$workspace,path)
  stopifnot(!".." %in% strsplit(absolute,"/",fixed=TRUE)[[1L]],
    absolute==control$workspace||startsWith(absolute,paste0(control$workspace,"/")))
  parent <- absolute
  while(!file.exists(parent)) {
    next.parent <- dirname(parent);stopifnot(!identical(parent,next.parent));parent <- next.parent
  }
  resolved <- normalizePath(parent,mustWork=TRUE)
  stopifnot(resolved==control$workspace||startsWith(resolved,paste0(control$workspace,"/")))
  invisible(TRUE)
}

bigcat.input.path <- function(path,control) {
  stopifnot(bigcat.scalar(path),file.exists(path))
  actual <- normalizePath(path,mustWork=TRUE)
  stopifnot(any(vapply(c(control$assets,control$workspace),function(root)
    actual==root||startsWith(actual,paste0(root,"/")),logical(1))))
  invisible(TRUE)
}

bigcat.request <- function(control) {
  request <- readRDS(control$request)
  stopifnot(bigcat.named(request),setequal(names(request),c("schema_version","profile","operation",
    "arguments","rng","globals","preparation","post_selection","arrow_cpu_count")),
    identical(request$schema_version,1L),identical(request$profile,control$profile),
    bigcat.scalar(request$operation),bigcat.named(request$arguments),bigcat.named(request$globals),
    all(names(request$globals) %in% "sets"),bigcat.named(request$preparation),
    bigcat.named(request$rng),setequal(names(request$rng),c("kind","seed")),
    is.character(request$rng$kind),length(request$rng$kind)==3L,!anyNA(request$rng$kind),
    is.integer(request$rng$seed),length(request$rng$seed)>0L,!anyNA(request$rng$seed),
    is.integer(request$arrow_cpu_count),length(request$arrow_cpu_count)==1L,
    !is.na(request$arrow_cpu_count),request$arrow_cpu_count>=1L)
  if(!request$operation %in% unlist(c(control$operations,control$workflow_operations)))
    bigcat.unavailable("The explicitly selected source profile does not contain this operation; select a compatible profile explicitly")
  if(length(request$globals))stopifnot(is.character(request$globals$sets),!anyNA(request$globals$sets))
  if(!is.null(request$post_selection))stopifnot(identical(request$operation,"i_harmonize"),
    bigcat.named(request$post_selection),setequal(names(request$post_selection),
      c("resolution_prefix","platform_column","platform_value","minimum")),
    all(vapply(request$post_selection[c("resolution_prefix","platform_column","platform_value")],bigcat.scalar,logical(1))),
    is.integer(request$post_selection$minimum),length(request$post_selection$minimum)==1L,
    !is.na(request$post_selection$minimum),request$post_selection$minimum>=0L)
  request
}

bigcat.prepare <- function(request,control) {
  prepared <- list();constructors <- list()
  resolve <- function(value) {
    if(identical(class(value),"omicsmatic_bigcat_path_v1")) {
      stopifnot(is.list(value),identical(names(value),"relative"),bigcat.scalar(value$relative),
        !startsWith(value$relative,"/"),!".." %in% strsplit(value$relative,"/",fixed=TRUE)[[1L]])
      result <- file.path(control$workspace,value$relative);bigcat.output.path(result,control);return(result)
    }
    if(identical(class(value),"omicsmatic_bigcat_reference_v1")) {
      stopifnot(is.list(value),setequal(names(value),c("id","path")),bigcat.scalar(value$id),
        value$id %in% names(prepared),is.character(value$path),!anyNA(value$path))
      result <- prepared[[value$id]]
      for(key in value$path) {
        stopifnot(is.list(result),key %in% names(result));result <- result[[key,exact=TRUE]]
      }
      return(result)
    }
    if(is.list(value)&&!is.object(value)) {
      result <- lapply(value,resolve);attributes(result) <- attributes(value);return(result)
    }
    value
  }
  for(id in names(request$preparation)) {
    recipe <- request$preparation[[id]]
    stopifnot(bigcat.named(recipe),all(names(recipe) %in% c("constructor","arguments","initial_values")),
      bigcat.scalar(recipe$constructor),recipe$constructor %in%
        c("convert_big.dat_parquet","create_big.dat_fbm","build_train_index"),bigcat.named(recipe$arguments))
    a <- resolve(recipe$arguments)
    if(recipe$constructor=="convert_big.dat_parquet") {
      stopifnot("dir" %in% names(a));bigcat.output.path(a$dir,control)
      for(key in intersect(c("parquet.dir","col.fn","row.fn"),names(a)))bigcat.output.path(a[[key]],control)
    } else if(recipe$constructor=="create_big.dat_fbm") {
      stopifnot("backingfile" %in% names(a));bigcat.output.path(a$backingfile,control)
    } else {
      stopifnot("fn" %in% names(a));bigcat.output.path(a$fn,control)
    }
    value <- do.call(getExportedValue("scrattch.bigcat",recipe$constructor),a)
    if("initial_values" %in% names(recipe)) {
      stopifnot(identical(recipe$constructor,"create_big.dat_fbm"))
      scrattch.bigcat::set_cols_fbm(value,recipe$initial_values)
    }
    prepared[[id]] <- value;constructors[[id]] <- recipe$constructor
  }
  arguments <- resolve(request$arguments)
  for(name in names(request$globals))assign(name,request$globals[[name]],envir=globalenv())
  if(request$operation=="i_harmonize") {
    stopifnot("dir" %in% names(arguments));bigcat.output.path(arguments$dir,control)
  }
  if(request$operation=="plot_constellation") {
    stopifnot("out.dir" %in% names(arguments));bigcat.output.path(arguments$out.dir,control)
  }
  if(!is.null(bigcat.get(arguments,"prefix")))bigcat.output.path(arguments$prefix,control)
  validate.data <- function(value) {
    if(methods::is(value,"AnnoyIndex"))bigcat.input.path(methods::slot(value,"path"),control)
    else if(is.list(value)&&identical(bigcat.get(value,"type"),"parquet")) {
      stopifnot(is.null(bigcat.get(value,"ds")))
      for(key in c("parquet.dir","col.fn","row.fn"))bigcat.input.path(value[[key,exact=TRUE]],control)
    } else if(is.list(value)&&!is.object(value))for(item in value)validate.data(item)
    invisible(NULL)
  }
  validate.data(arguments)
  if(request$operation=="impute_cross_big") {
    owned <- names(constructors)[unlist(constructors)=="create_big.dat_fbm"]
    stopifnot(length(owned)>0L,any(vapply(owned,function(id)identical(arguments$impute.dat.big,prepared[[id]]),logical(1))))
  }
  list(arguments=arguments,prepared=prepared,constructors=constructors)
}

bigcat.post.selection <- function(value,arguments,selection) {
  stopifnot(selection$resolution_prefix %in% names(value))
  cl <- value[[selection$resolution_prefix]][["cl",exact=TRUE]]
  metadata <- arguments$comb.dat$meta.df
  stopifnot(!is.null(names(cl)),!anyNA(cl),!anyNA(names(cl)),!anyDuplicated(names(cl)),
    all(names(cl) %in% rownames(metadata)),selection$platform_column %in% colnames(metadata))
  platform <- metadata[names(cl),selection$platform_column]
  stopifnot(is.character(platform)||is.factor(platform),!anyNA(platform))
  counts <- table(factor(as.character(cl[platform==selection$platform_value]),levels=unique(as.character(cl))))
  selected <- names(counts)[counts>=selection$minimum]
  list(resolution_prefix=selection$resolution_prefix,platform_column=selection$platform_column,
    platform_value=selection$platform_value,minimum=selection$minimum,counts=counts,
    selected_clusters=selected,selected_cells=names(cl)[as.character(cl) %in% selected],
    selection_after_original_integration=TRUE,author_hierarchy_level_inferred=FALSE,
    correspondence_mapping_performed=FALSE)
}
