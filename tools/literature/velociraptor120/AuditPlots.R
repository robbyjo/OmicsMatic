# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Readiness for original R-only graph functions; no numeric fitting backend.
auditVelociraptorPlots <- function(library,source,directory) {
 if (!dir.create(directory)) stop('Use a NEW plot namespace audit directory')
 .libPaths(c(library,.libPaths()));stopifnot(as.character(getRversion())=='4.5.3')
 ns <- loadNamespace('velociraptor',lib.loc=library)
 stopifnot(as.character(packageVersion('velociraptor',lib.loc=library))=='1.20.0',normalizePath(getNamespaceInfo(ns,'path'))==normalizePath(file.path(library,'velociraptor')))
 files <- list.files(file.path(source,'R'),pattern='\\.R$',full.names=TRUE);stopifnot(length(files)==8L)
 functions <- list()
 for (file in files) {
  for (expr in parse(file,keep.source=FALSE)) {
   if (is.call(expr) && identical(expr[[1L]],as.name('<-')) && length(expr)==3L && is.symbol(expr[[2L]]) && is.call(expr[[3L]]) && identical(expr[[3L]][[1L]],as.name('function'))) {
    name <- as.character(expr[[2L]]);f <- get(name,envir=ns,inherits=FALSE)
    stopifnot(identical(formals(f),expr[[3L]][[2L]]),identical(body(f),expr[[3L]][[3L]]));functions[[name]] <- list(formals=formals(f),body=body(f))
   }
  }
 }
 stopifnot(length(functions)==11L)
 versions <- c(ggplot2='4.0.4',patchwork='1.3.2',metR='0.19.0')
 for (name in names(versions)) {
  if (!requireNamespace(name,quietly=TRUE) || as.character(packageVersion(name))!=versions[[name]]) stop(structure(list(message=paste('Required original plot dependency unavailable:',name,versions[[name]]),call=NULL),class=c('velociraptor_missing_dependency','error','condition')))
 }
 stopifnot(capabilities('cairo'),capabilities('png'),!reticulate::py_available(initialize=FALSE),identical(Sys.getenv('BASILISK_USE_SYSTEM_DIR'),'1'))
 if (dir.exists(file.path(library,'velociraptor','basilisk','env'))) stop('The R-only figure profile must not contain an undeclared fitting backend')
 methods <- setNames(lapply(c('scvelo','embedVelocity','gridVectors'),function(name)methods::findMethods(name,where=ns)),c('scvelo','embedVelocity','gridVectors'))
 stopifnot(identical(vapply(methods,length,integer(1)),c(scvelo=3L,embedVelocity=2L,gridVectors=2L)))
 saveRDS(list(functions=functions,complete.registered.methods=methods,declared.original.environment=get('velo.env',envir=ns,inherits=FALSE),session=sessionInfo(),loaded.DLLs=getLoadedDLLs()),file.path(directory,'COMPLETE-ORIGINAL-PLOT-NAMESPACE.rds'),version=3)
 result <- list(status='READY_ORIGINAL_R_ONLY_VELOCIRAPTOR120_PLOT_NAMESPACE_NOT_ANALYSIS',R_version=as.character(getRversion()),source.functions=names(functions),source.function.count=length(functions),versions=as.list(versions),python.initialized=FALSE,provides.Python.fitting.backend=FALSE,raw.console.diagnostic.counts.complete=FALSE,native.cause.resolved=FALSE)
 jsonlite::write_json(result,file.path(directory,'READINESS.json'),pretty=TRUE,auto_unbox=TRUE,null='null',digits=17);result
}
