# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Read-only dependency and original-definition probe. This does not run inference.
args <- commandArgs(trailingOnly=TRUE)
out <- args[1]
.libPaths(c(args[-1], .libPaths()))
options(warn=1)
versions <- vapply(c('dplyr','future','future.apply','Matrix','igraph','ggplot2',
  'NMF','ggalluvial','stringr','svglite','ggrepel','circlize','cowplot',
  'ComplexHeatmap','RSpectra','Rcpp','RcppEigen','reticulate','scales','sna',
  'reshape2','FNN','shape','BiocGenerics','magrittr','patchwork','colorspace',
  'plyr','ggpubr','ggnetwork','BiocNeighbors','plotly','shiny','bslib',
  'expm','Rtsne','forcats','gg.gap','irlba','pbapply'),function(name) {
    found <- find.package(name, quiet=TRUE)
    if(length(found)==0L) return('MISSING')
    paste(as.character(packageVersion(name,lib.loc=dirname(found))),found,sep='|')
  },character(1))
write.table(data.frame(package=names(versions),version_path=unname(versions)),
  file.path(out,'dependencies.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
writeLines(c(R.version.string,.libPaths()),file.path(out,'runtime.txt'))
cat('PASS_READ_ONLY_CELLCHAT_DEPENDENCY_PROBE_ONLY\n')
