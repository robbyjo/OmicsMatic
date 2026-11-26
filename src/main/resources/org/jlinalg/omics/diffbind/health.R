# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
suppressPackageStartupMessages(library(jsonlite))
suppressPackageStartupMessages(library(digest))
profile <- read_json('profile.json', simplifyVector=FALSE)
packages <- lapply(profile$packages,function(p) {
  loadNamespace(p$package)
  metadata <- read.dcf(file.path(find.package(p$package),'DESCRIPTION'))
  list(package=p$package,version=unname(metadata[1,'Version']))
})
namespace <- asNamespace('DiffBind')
functions <- list()
for(name in ls(namespace,all.names=TRUE,sorted=TRUE)) {
  value <- get(name,namespace,inherits=FALSE)
  if(!is.function(value))next
  text <- if(is.primitive(value))paste(deparse(value,width.cutoff=500L),collapse='\n')else
      paste(c(deparse(formals(value),width.cutoff=500L,control=c('keepInteger','keepNA')),
              deparse(body(value),width.cutoff=500L,control=c('keepInteger','keepNA'))),collapse='\n')
  functions[[name]] <- list(type=typeof(value),sha256=digest(text,algo='sha256',serialize=FALSE))
}
write_json(list(R=as.character(getRversion()),DiffBind=as.character(packageVersion('DiffBind')),
               packages=packages,functions=functions),'R-profile.json',auto_unbox=TRUE,pretty=TRUE)
png('R-health.png',width=640,height=480,type='cairo');plot(1:3,1:3);dev.off()
writeLines(c(R.home(),.libPaths()),'R-ROOTS.txt')
writeLines(readLines('/proc/self/maps'),'R-MAPS.txt')
