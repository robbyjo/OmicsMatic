# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==2L)
.libPaths(c(normalizePath("build/literature-review/signac-runtime/reference-setup-1/R-lib"),normalizePath("build/omics-sctransform-r"),.Library),include.site=FALSE)
stopifnot(requireNamespace("Signac",quietly=TRUE),as.character(packageVersion("Signac"))=="1.17.1")
cases<-jsonlite::read_json(args[[1]],simplifyVector=FALSE)$matrices;results<-list()
for(case in cases){
  x<-new("dgCMatrix",i=as.integer(unlist(case$i)),p=as.integer(unlist(case$p)),x=as.numeric(unlist(case$x)),Dim=as.integer(c(length(case$features),length(case$cells))),Dimnames=list(unlist(case$features),unlist(case$cells)))
  y<-Signac::BinarizeCounts(x,verbose=FALSE)
  results[[length(results)+1L]]<-list(id=case$id,p=as.vector(y@p),i=as.vector(y@i),x=as.vector(y@x),features=rownames(y),cells=colnames(y))
}
jsonlite::write_json(list(status="captured-full-original-Signac-sparse-BinarizeCounts",package="1.17.1",results=results),args[[2]],pretty=TRUE,auto_unbox=TRUE,digits=17,na="string")
cat("COMPLETE_ORIGINAL_BINARIZE_CONTROLS",length(results),"\n")
