# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE)
stopifnot(length(args)==4L)
version <- args[[1]]; archive <- args[[2]]; lib <- args[[3]]
stopifnot(!dir.exists(lib),dir.create(lib))
.libPaths(c(lib,strsplit(args[[4]],.Platform$path.sep,fixed=TRUE)[[1]],.Library),include.site=FALSE)
install.packages(archive,lib=lib,repos=NULL,type="source",dependencies=FALSE,INSTALL_opts="--no-multiarch")
stopifnot(requireNamespace("ChIPseeker",quietly=TRUE),as.character(packageVersion("ChIPseeker"))==version,
          normalizePath(find.package("ChIPseeker"),winslash="/")==normalizePath(file.path(lib,"ChIPseeker"),winslash="/"))
writeLines(capture.output(sessionInfo()),file.path(dirname(lib),"installation-session-info.txt"))
