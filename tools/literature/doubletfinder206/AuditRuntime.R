# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Audit installed original bindings against unchanged original source expressions.
args <- commandArgs(TRUE)
stopifnot(length(args)==3L)
lib <- args[[1L]]; original <- args[[2L]]; out <- args[[3L]]
if (file.exists(out)) stop("Use a NEW runtime audit output")
dir.create(out,recursive=FALSE)
.libPaths(c(lib,.libPaths()))
packages <- c("DoubletFinder","Seurat","SeuratObject","fields","ROCR","KernSmooth","Matrix","sctransform","jsonlite")
for (package in packages) loadNamespace(package)
stopifnot(as.character(packageVersion("DoubletFinder"))=="2.0.6")
namespace <- asNamespace("DoubletFinder")
bindings <- list()
for (file in sort(list.files(file.path(original,"R"),full.names=TRUE,pattern="[.]R$"))) {
 for (expression in parse(file,keep.source=FALSE)) {
  if (is.call(expression) && identical(expression[[1L]],as.name("<-")) && is.symbol(expression[[2L]]) && is.call(expression[[3L]]) && identical(expression[[3L]][[1L]],as.name("function"))) {
   name <- as.character(expression[[2L]])
   expected <- eval(expression[[3L]],envir=namespace)
   actual <- get(name,envir=namespace,inherits=FALSE)
   equal <- identical(formals(expected),formals(actual)) && identical(body(expected),body(actual))
   bindings[[name]] <- list(source_file=basename(file),formals_and_body_identical=equal)
   if (!equal) stop("Installed original function differs from original source: ",name)
  }
 }
}
stopifnot(length(bindings)==9L)
versions <- lapply(packages,function(package)list(package=package,version=as.character(packageVersion(package)),path=find.package(package)))
result <- list(status="PASS_ORIGINAL_DOUBLET_FINDER206_NINE_INSTALLED_FUNCTION_BODIES_AND_FORMALS",bindings=bindings,packages=versions,R_version=R.version.string,session=sessionInfo(),analysis_executed=FALSE,author_environment_inferred=FALSE,new_family_credit=0L)
saveRDS(result,file.path(out,"original-runtime-audit.rds"),version=3)
result$session <- capture.output(sessionInfo())
jsonlite::write_json(result,file.path(out,"COMPLETED.json"),pretty=TRUE,auto_unbox=TRUE,null="null",digits=17)
cat(result$status,"\n")
