# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
celldex.audit<-function(control,output){
 .libPaths(c(control$library,control$base_library))
 stopifnot(identical(as.character(getRversion()),control$r_version))
 source(control$namespace_audit)
 results<-list()
 for(package in names(control$package_versions)){
  stopifnot(identical(normalizePath(find.package(package)),normalizePath(file.path(control$library,package))))
  result<-singler.audit.package(package,control$source_paths[[package]])
  stopifnot(identical(result$version,control$package_versions[[package]]));results[[package]]<-result
 }
 stopifnot(identical(getExportedValue('celldex','defineTextQuery'),getExportedValue('gypsum','defineTextQuery')))
 result<-list(status='PASS_COMPLETE_ORIGINAL_CELLDEX114_NAMESPACE_ASTS',packages=results,gypsum_query_reexport_verified=TRUE,
  dependency_versions=vapply(c('gypsum','alabaster.base','alabaster.matrix','alabaster.se','ExperimentHub','AnnotationHub','ensembldb'),function(x)as.character(packageVersion(x)),character(1)),
  session=sessionInfo(),source_or_dependency_algorithms_patched=FALSE,author_environment_inferred=FALSE)
 saveRDS(result,output,version=3);invisible(result)
}
if(!interactive()&&sys.nframe()==0L){
 a<-commandArgs(TRUE);stopifnot(length(a)==2L);c<-jsonlite::read_json(a[[1L]],simplifyVector=FALSE);celldex.audit(c,a[[2L]])
}
