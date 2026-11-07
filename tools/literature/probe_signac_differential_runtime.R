# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Read-only source compatibility probe; no installation and no user-data inference.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==2L)
.libPaths(c(normalizePath('build/literature-review/signac-runtime/motif-reference-setup-1/R-lib'),normalizePath('build/literature-review/signac-runtime/reference-setup-1/R-lib'),normalizePath('build/omics-sctransform-r'),.Library),include.site=FALSE)
out<-args[[1]];stopifnot(!dir.exists(out));dir.create(out)
versions<-sapply(c('Seurat','SeuratObject','Signac','Matrix','jsonlite','limma','future','future.apply'),function(n)if(requireNamespace(n,quietly=TRUE))as.character(packageVersion(n)) else 'UNAVAILABLE')
source<-parse(args[[2]],keep.source=FALSE);ns<-asNamespace('Seurat');comparisons<-list()
for(expr in source)if(is.call(expr)&&identical(expr[[1]],as.name('<-'))&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name('function'))){
 name<-as.character(expr[[2]]);original<-eval(expr[[3]],envir=ns)
 loaded<-get0(name,envir=ns,inherits=FALSE)
 comparisons[[name]]<-list(present=is.function(loaded),body_identical=is.function(loaded)&&identical(body(original),body(loaded)),formals_identical=is.function(loaded)&&identical(formals(original),formals(loaded)))
}
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
jsonlite::write_json(list(status='read-only-original-Seurat-source-compatibility-not-analysis-admission',versions=as.list(versions),presto_present=requireNamespace('presto',quietly=TRUE),source_functions=comparisons),file.path(out,'RESULT.json'),auto_unbox=TRUE,pretty=TRUE)
cat('SEURAT_DIFFERENTIAL_SOURCE_PROBE_COMPLETE\n')
