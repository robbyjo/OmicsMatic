# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Read-only package resolution. No package installation or annotation query.
args <- commandArgs(TRUE)
.libPaths(args[-1L],include.site=FALSE)
stopifnot(requireNamespace("biomaRt",quietly=TRUE),requireNamespace("jsonlite",quietly=TRUE))
ip <- installed.packages(lib.loc=.libPaths())
dependencies <- tools::package_dependencies(c("biomaRt","jsonlite"),db=ip,which=c("Depends","Imports","LinkingTo"),recursive=TRUE)
packages <- sort(unique(c("biomaRt","jsonlite",unlist(dependencies,use.names=FALSE),loadedNamespaces())))
packages <- setdiff(packages,"R")
paths <- setNames(vapply(packages,function(p)normalizePath(find.package(p),winslash="/"),character(1)),packages)
jsonlite::write_json(list(version=as.character(packageVersion("biomaRt")),R=R.version.string,r_home=normalizePath(R.home(),winslash="/"),libraries=.libPaths(),packages=as.list(paths)),args[[1L]],auto_unbox=TRUE,pretty=TRUE)
