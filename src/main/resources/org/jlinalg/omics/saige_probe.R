args <- commandArgs(TRUE)
stopifnot(length(args) == 1)
prefix <- normalizePath(args[1], mustWork=TRUE)
library.path <- normalizePath(file.path(prefix, 'lib/R/library'), mustWork=TRUE)
.libPaths(library.path)
stopifnot(identical(.libPaths(), library.path))
library(SAIGE)
library(optparse)
library(RhpcBLASctl)
library(jsonlite)
packages <- c('SAIGE','Matrix','Rcpp','RcppParallel','RcppArmadillo','data.table',
              'SPAtest','SKAT','MetaSKAT','RhpcBLASctl','optparse','dplyr','jsonlite','lintools')
versions <- setNames(lapply(packages, function(p) as.character(packageVersion(p))), packages)
invisible(lapply(packages, loadNamespace))
stopifnot(as.character(getRversion()) == '4.5.2', versions$SAIGE == '1.5.2')
result <- list(R=as.character(getRversion()), packages=versions,
               library_paths=.libPaths(), BLAS=sessionInfo()$BLAS,
               DLL_paths=sort(vapply(getLoadedDLLs(), function(x) x[['path']], character(1))))
cat(toJSON(result, auto_unbox=TRUE, digits=NA, pretty=FALSE), '\n')
