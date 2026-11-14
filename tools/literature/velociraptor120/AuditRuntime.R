# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Validate original source functions and retain the full original dispatch census.
auditVelociraptorRuntime <- function(library,source,backend,directory,expected.exports) {
 if (!dir.create(directory)) stop('Use a NEW original namespace readiness directory')
 .libPaths(c(library,.libPaths()))
 if (as.character(getRversion())!='4.5.3') stop('Required original dependency profile uses R 4.5.3')
 marker <- function(x) cat('ORIGINAL_NAMESPACE_STAGE: ',x,'\n',file=stderr(),sep='')
 marker('before-original-namespace-load')
 ns <- loadNamespace('velociraptor',lib.loc=library)
 marker('after-original-namespace-load')
 stopifnot(as.character(packageVersion('velociraptor',lib.loc=library))=='1.20.0',
  normalizePath(getNamespaceInfo(ns,'path'))==normalizePath(file.path(library,'velociraptor')))
 files <- sort(list.files(file.path(source,'R'),pattern='\\.R$',full.names=TRUE))
 stopifnot(length(files)==8L)
 functions <- list();parsed <- list();declarations <- list()
 for (file in files) {
  expressions <- parse(file,keep.source=FALSE);parsed[[basename(file)]] <- expressions
  for (expr in expressions) {
   if (is.call(expr) && identical(expr[[1L]],as.name('<-')) && length(expr)==3L && is.symbol(expr[[2L]]) && is.call(expr[[3L]]) && identical(expr[[3L]][[1L]],as.name('function'))) {
    name <- as.character(expr[[2L]]);installed <- get(name,envir=ns,inherits=FALSE)
    stopifnot(is.function(installed),identical(formals(installed),expr[[3L]][[2L]]),identical(body(installed),expr[[3L]][[3L]]))
    functions[[name]] <- list(formals=formals(installed),body=body(installed),environment=environmentName(environment(installed)))
   }
   if (is.call(expr) && as.character(expr[[1L]])[1L] %in% c('setGeneric','setMethod')) declarations[[length(declarations)+1L]] <- expr
  }
 }
 stopifnot(length(functions)==11L,length(declarations)==10L)
 generics <- c('scvelo','embedVelocity','gridVectors')
 methods <- setNames(lapply(generics,function(name) methods::findMethods(name,where=ns)),generics)
 stopifnot(identical(vapply(methods,length,integer(1)),c(scvelo=3L,embedVelocity=2L,gridVectors=2L)))
 exports <- sort(getNamespaceExports('velociraptor'))
 stopifnot(identical(exports,sort(expected.exports)))
 stopifnot(identical(Sys.getenv('BASILISK_USE_SYSTEM_DIR'),'1'),isTRUE(basilisk:::useSystemDir()))
 if (!file.exists(file.path(backend,'bin/python'))) stop(structure(list(message='Required install-time Python backend is missing; skip dependent analysis',call=NULL),class=c('velociraptor_missing_dependency','error','condition')))
 spec <- get('velo.env',envir=ns,inherits=FALSE)
 observed <- basilisk::obtainEnvironmentPath(spec)
 stopifnot(identical(observed,backend),!reticulate::py_available(initialize=FALSE))
 saveRDS(list(parsed=parsed,functions=functions,declarations=declarations,complete_registered_methods=methods,environment=spec,session=sessionInfo()),file.path(directory,'complete-original-namespace.rds'),version=3)
 report <- list(status='READY_ORIGINAL_VELOCIRAPTOR120_NAMESPACE_NOT_ANALYSIS',R_version=as.character(getRversion()),source_function_count=length(functions),source_functions=sort(names(functions)),exports=exports,generics=generics,registered_method_counts=vapply(methods,length,integer(1)),backend=observed,python_initialized=FALSE,original_algorithms_modified=FALSE)
 jsonlite::write_json(report,file.path(directory,'readiness.json'),pretty=TRUE,auto_unbox=TRUE,null='null',na='string',digits=17)
 report
}
