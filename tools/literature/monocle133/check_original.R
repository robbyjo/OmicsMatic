# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(trailingOnly=TRUE)
stopifnot(length(args)==4L)
out <- args[1]; .libPaths(args[2:3],include.site=FALSE); source_root <- args[4]
stopifnot(as.character(getRversion())=="4.3.3")
suppressPackageStartupMessages(library(monocle3))
stopifnot(as.character(packageVersion("monocle3"))=="1.3.3",
          normalizePath(find.package("monocle3"))==normalizePath(file.path(args[2],"monocle3")))
definitions <- list(); ns <- asNamespace("monocle3")
for(file in list.files(file.path(source_root,"R"),pattern="[.]R$",full.names=TRUE))
 for(expr in parse(file,keep.source=FALSE))
  if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]]) %in% c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))definitions[[as.character(expr[[2]])]] <- expr[[3]]
guarded <- c("new_cell_data_set","learn_graph","multi_component_RGE","project2MST","find_nearest_vertex","order_cells","extract_general_graph_ordering")
for(name in guarded) {
 stopifnot(!is.null(definitions[[name]]),exists(name,ns,inherits=FALSE))
 original <- eval(definitions[[name]],envir=ns); loaded <- get(name,ns,inherits=FALSE)
 if(!identical(body(original),body(loaded))||!identical(formals(original),formals(loaded)))stop("Original function body/formals mismatch: ",name)
}
versions <- setNames(lapply(loadedNamespaces(),function(p)as.character(getNamespaceVersion(p))),loadedNamespaces())
result <- list(status="PASS_EXACT_ORIGINAL_MONOCLE133_NAMESPACE_AND_SOURCE_NOT_ANALYSIS",R=as.character(getRversion()),platform=R.version$platform,
               monocle3=as.character(packageVersion("monocle3")),monocle_path=find.package("monocle3"),guarded_functions=as.list(guarded),loaded_packages=versions,analysis_admitted=FALSE)
jsonlite::write_json(result,file.path(out,"READINESS.json"),pretty=TRUE,auto_unbox=TRUE,digits=NA)
writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"))
cat(result$status,"\n")
