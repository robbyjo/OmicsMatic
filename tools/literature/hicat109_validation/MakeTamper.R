# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Deliberately corrupt NEW copied negative controls, never qualified originals.
local({
  args <- commandArgs(TRUE);stopifnot(length(args)==2L)
  root <- args[[1L]];kind <- args[[2L]];worker <- file.path(root,"worker/original");direct <- file.path(root,"direct")
  if(kind %in% c("score-real","score-artificial","names")) {
    for(path in c(file.path(worker,"complete-original-return.rds"),file.path(direct,"complete-return.rds"))) {
      whole <- readRDS(path)
      if(kind=="score-real")whole[[1L]][[1L]] <- whole[[1L]][[1L]]+1
      if(kind=="score-artificial")whole[[2L]][[1L]] <- NaN
      if(kind=="names")names(whole[[1L]])[[1L]] <- "REPAIRED_ID"
      saveRDS(whole,path,version=3)
    }
  } else if(kind=="rng") {
    for(path in c(file.path(worker,"complete-original-rng.rds"),file.path(direct,"complete-rng.rds"))) {
      whole <- readRDS(path);whole$after$seed[[3L]] <- bitwXor(whole$after$seed[[3L]],1L);saveRDS(whole,path,version=3)
    }
  } else if(kind=="warning") {
    for(path in c(file.path(worker,"complete-original-conditions.rds"),file.path(direct,"complete-conditions.rds"))) {
      whole <- readRDS(path);stopifnot(any(vapply(whole$conditions,function(x)identical(x$kind,"warning"),logical(1))))
      whole$conditions <- Filter(function(x)!identical(x$kind,"warning"),whole$conditions);saveRDS(whole,path,version=3)
    }
    path <- file.path(worker,"R-status.json");status <- jsonlite::read_json(path);status$R_handler_counts$warning <- 0L
    jsonlite::write_json(status,path,pretty=TRUE,auto_unbox=TRUE,null="null")
  } else if(kind=="failure") {
    fake <- list(setNames(rep(0,80),paste0("cell_",1:80)),setNames(rep(0,20),paste0("X",1:20)))
    saveRDS(fake,file.path(worker,"complete-original-return.rds"),version=3);saveRDS(fake,file.path(direct,"complete-return.rds"),version=3)
    file.copy(file.path(worker,"failed-original-rng.rds"),file.path(worker,"complete-original-rng.rds"),overwrite=FALSE)
    for(path in c(file.path(worker,"complete-original-conditions.rds"),file.path(direct,"complete-conditions.rds"))) {
      whole <- readRDS(path);whole$error <- NULL;whole$conditions <- Filter(function(x)!identical(x$kind,"error"),whole$conditions);saveRDS(whole,path,version=3)
    }
    path <- file.path(worker,"R-status.json");status <- jsonlite::read_json(path)
    status$phase <- "COMPLETE";status$exit_code <- 0L;status$original_function_returned <- TRUE
    status$complete_original_return_recorded <- TRUE;status$error <- NULL;status$R_handler_counts$error <- 0L
    jsonlite::write_json(status,path,pretty=TRUE,auto_unbox=TRUE,null="null")
    path <- file.path(direct,"STATUS.json");status <- jsonlite::read_json(path);status$returned <- TRUE;status$error <- NULL
    jsonlite::write_json(status,path,pretty=TRUE,auto_unbox=TRUE,null="null")
  } else stop("Unknown deliberate corruption")
})
