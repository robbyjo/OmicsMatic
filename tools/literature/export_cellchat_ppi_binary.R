# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent complete resource decode; no CellChat inference or source patching.
args <- commandArgs(TRUE);out <- args[1];root <- args[2]
.libPaths(c(args[-(1:2)],.Library),include.site=FALSE);library(Matrix)
stopifnot(!dir.exists(out),dir.create(out))
for(species in c('human','mouse')) {
  home <- file.path(out,species);dir.create(home);name <- paste0('PPI.',species);source <- file.path(root,'data',paste0(name,'.rda'));env <- new.env(parent=emptyenv());load(source,envir=env);ppi <- as.matrix(get(name,envir=env))
  stopifnot(identical(rownames(ppi),colnames(ppi)),all(is.finite(ppi)),all(ppi>=0))
  con <- file(file.path(home,'ppi-values.f64le'),'wb');for(i in seq_len(nrow(ppi)))writeBin(as.double(ppi[i,]),con,size=8,endian='little');close(con)
  meta <- list(resource=name,file_sha256=digest::digest(file=source,algo='sha256'),genes=unname(as.list(rownames(ppi))),columns=unname(as.list(colnames(ppi))),rows=nrow(ppi),cols=ncol(ppi),encoding='float64-le-row-major',binary_bytes=file.info(file.path(home,'ppi-values.f64le'))$size,binary_sha256=digest::digest(file=file.path(home,'ppi-values.f64le'),algo='sha256'))
  jsonlite::write_json(meta,file.path(home,'ppi-resource.json'),auto_unbox=TRUE,pretty=TRUE,null='null',na='string',digits=NA)
}
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
