# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
spotlight.audit<-function(control,output){
 .libPaths(c(control$library,control$base_library))
 stopifnot(identical(as.character(getRversion()),control$r_version))
 source(control$namespace_audit)
 results<-list()
 for(package in names(control$package_versions)){
  stopifnot(identical(normalizePath(find.package(package)),normalizePath(file.path(control$library,package))))
  result<-singler.audit.package(package,control$source_paths[[package]])
  stopifnot(identical(result$version,control$package_versions[[package]]));results[[package]]<-result
 }
 dll<-getLoadedDLLs()[['SPOTlight']]
 stopifnot(identical(normalizePath(dll[['path']]),normalizePath(file.path(control$library,'SPOTlight','libs',paste0('SPOTlight',.Platform$dynlib.ext)))))
 results$SPOTlight$native_library_path<-dll[['path']]
 results$SPOTlight$registered_routines<-getDLLRegisteredRoutines(dll)
 result<-list(status='PASS_COMPLETE_ORIGINAL_SPOTLIGHT103_NAMESPACE_ASTS_AND_NATIVE_PATH',packages=results,
  dependency_versions=vapply(c('Rcpp','RcppEigen','Matrix','ggplot2','SingleCellExperiment','sparseMatrixStats'),function(x)as.character(packageVersion(x)),character(1)),
  session=sessionInfo(),source_or_dependency_algorithms_patched=FALSE,author_environment_inferred=FALSE)
 saveRDS(result,output,version=3);invisible(result)
}
if(!interactive()&&sys.nframe()==0L){
 a<-commandArgs(TRUE);stopifnot(length(a)==2L);c<-jsonlite::read_json(a[[1L]],simplifyVector=FALSE);spotlight.audit(c,a[[2L]])
}
