# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE)
stopifnot(length(args)==6L)
lib <- normalizePath(args[1],mustWork=TRUE);base <- normalizePath(args[2],mustWork=TRUE)
source <- normalizePath(args[3],mustWork=TRUE);profile <- jsonlite::fromJSON(args[4],simplifyVector=FALSE)
version <- args[5];out <- normalizePath(args[6],mustWork=TRUE)
.libPaths(c(lib,base));stopifnot(as.character(getRversion())=='4.3.3',version %in% c('0.4.0','0.6.0'))
suppressPackageStartupMessages(library(ggpubr,lib.loc=lib))
selected <- list(ggplot2='3.4.3',rstatix='0.7.2',ggpubr=version)
checks <- list()
is_ggproto_call <- function(expr) {
 if(!is.call(expr))return(FALSE)
 head <- expr[[1]]
 identical(head,as.name('ggproto')) || (is.call(head)&&identical(head[[1]],as.name('::'))&&identical(head[[2]],as.name('ggplot2'))&&identical(head[[3]],as.name('ggproto')))
}
for(package in names(selected)) {
 stopifnot(as.character(packageVersion(package,lib.loc=lib))==selected[[package]])
 ns <- asNamespace(package);spec <- profile$profiles[[paste(package,selected[[package]],sep='-')]]
 stopifnot(identical(sort(getNamespaceExports(package)),sort(unlist(spec$namespace_exports,use.names=FALSE))))
 functions <- character();prototypes <- list()
 for(file in list.files(file.path(source,package,'R'),pattern='\\.[Rr]$',full.names=TRUE)) {
  for(expr in parse(file,keep.source=FALSE)) {
   if(!is.call(expr)||!identical(expr[[1]],as.name('<-'))||length(expr)!=3L||!is.symbol(expr[[2]]))next
   name <- as.character(expr[[2]]);rhs <- expr[[3]]
   if(is.call(rhs)&&identical(rhs[[1]],as.name('function'))) {
    original <- eval(rhs,envir=ns);installed <- get(name,envir=ns,inherits=FALSE)
    if(!identical(formals(original),formals(installed))||!identical(body(original),body(installed)))stop('Original function differs: ',package,'::',name)
    functions <- c(functions,name)
   } else if(is_ggproto_call(rhs)) {
    original <- eval(rhs,envir=ns);installed <- get(name,envir=ns,inherits=FALSE)
    stopifnot(inherits(original,'ggproto'),inherits(installed,'ggproto'))
    fields <- sort(ls(original,all.names=TRUE));stopifnot(identical(fields,sort(ls(installed,all.names=TRUE))))
    for(field in fields) {
     wanted <- get(field,envir=original,inherits=FALSE);actual <- get(field,envir=installed,inherits=FALSE)
     if(is.function(wanted)) {
      if(!is.function(actual)||!identical(formals(wanted),formals(actual))||!identical(body(wanted),body(actual)))stop('Original prototype method differs: ',package,'::',name,'$',field)
     } else if(!isTRUE(all.equal(wanted,actual,check.environment=FALSE,tolerance=0)))stop('Original prototype field differs: ',package,'::',name,'$',field)
    }
    prototypes[[name]] <- fields
   }
  }
 }
 checks[[package]] <- list(version=selected[[package]],exports=sort(getNamespaceExports(package)),direct_source_functions=sort(unique(functions)),source_ggproto_objects=prototypes)
}
stopifnot(length(checks$rstatix$direct_source_functions)==339L,
 all(c('stat_compare_means','stat_cor','ggviolin','compare_means') %in% checks$ggpubr$direct_source_functions))
fonts <- lapply(c('sans','serif','mono','Arial','DejaVu Sans'),function(name) {
 matched <- suppressWarnings(systemfonts::match_font(name))
 list(requested_family=name,path=matched$path,index=matched$index)
})
stopifnot(all(vapply(fonts,function(x)is.character(x$path)&&length(x$path)==1L&&file.exists(x$path),logical(1))))
result <- list(status='READY_UNCHANGED_ORIGINAL_GGPUBR_NAMESPACE_NOT_ANALYSIS',version=version,R_version=as.character(getRversion()),
 checks=checks,fonts=fonts,capabilities=capabilities(),
 dependency_packages=as.data.frame(installed.packages(lib.loc=c(lib,base))[,c('Package','Version','LibPath')],stringsAsFactors=FALSE),
 source_modified=FALSE,paper_analysis_executed=FALSE)
saveRDS(result,file.path(out,'readiness.rds'),version=3)
jsonlite::write_json(result,file.path(out,'readiness.json'),pretty=TRUE,auto_unbox=TRUE)
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
writeLines(capture.output(ggplot2::Stat$compute_layer),file.path(out,'original-ggplot-Stat-compute-layer.txt'))
cat('READY_UNCHANGED_ORIGINAL_GGPUBR_NAMESPACE_NOT_ANALYSIS version=',version,' direct_functions=',length(checks$ggpubr$direct_source_functions),' prototypes=',length(checks$ggpubr$source_ggproto_objects),'\n',sep='')
