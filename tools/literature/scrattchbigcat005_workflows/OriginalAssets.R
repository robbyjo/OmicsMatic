# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Copy returned temporary Annoy files before R cleanup; never rewrite original objects.
bigcat.archive.original.assets <- function(value,control) {
  root <- file.path(control$output,"original-return-assets")
  stopifnot(!file.exists(root));dir.create(root)
  records <- list();known <- new.env(parent=emptyenv())
  roots <- vapply(c(control$workspace,dirname(control$output),control$assets),normalizePath,character(1),mustWork=TRUE)
  visit <- function(object,trail) {
    if(methods::is(object,"AnnoyIndex")) {
      source <- methods::slot(object,"path")
      stopifnot(bigcat.scalar(source),file.exists(source),!dir.exists(source))
      resolved <- normalizePath(source,mustWork=TRUE)
      stopifnot(any(vapply(roots,function(directory)resolved==directory||
        startsWith(resolved,paste0(directory,"/")),logical(1))))
      if(exists(resolved,known,inherits=FALSE)) {
        index <- get(resolved,known,inherits=FALSE)
        records[[index]]$locations[[length(records[[index]]$locations)+1L]] <<- trail
      } else {
        index <- length(records)+1L;destination <- file.path(root,sprintf("annoy-%06d.idx",index))
        before <- digest::digest(file=resolved,algo="sha256")
        stopifnot(file.copy(resolved,destination,overwrite=FALSE,copy.mode=TRUE),
          identical(before,digest::digest(file=resolved,algo="sha256")),
          identical(before,digest::digest(file=destination,algo="sha256")))
        records[[index]] <<- list(class=class(object),original_path=source,resolved_original_path=resolved,
          retained_copy=destination,bytes=unname(file.info(destination)$size),sha256=before,
          locations=list(trail),original_object_path_rewritten=FALSE,
          live_index_persistence_qualified=FALSE)
        assign(resolved,index,known)
      }
    } else if(is.list(object)&&!is.object(object)) {
      for(i in seq_along(object)) {
        key <- if(is.null(names(object)))as.character(i)else names(object)[[i]]
        visit(object[[i]],c(trail,key))
      }
    }
    invisible(NULL)
  }
  visit(value,character())
  saveRDS(records,file.path(control$output,"complete-original-asset-links.rds"),version=3)
  records
}
