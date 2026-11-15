# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
local({
  a<-commandArgs(TRUE);stopifnot(length(a)==4L);.libPaths(c(a[[1L]],.libPaths()))
  requireNamespace("SingleR");direct<-a[[2L]];worker<-file.path(a[[3L]],"original")
  d<-readRDS(file.path(direct,"complete-conditions.rds"));w<-readRDS(file.path(worker,"complete-original-conditions.rds"))
  eq<-function(x,y)identical(serialize(x,NULL,version=3),serialize(y,NULL,version=3))
  checks<-list(whole_error=eq(d$error,w$error),whole_conditions=eq(d$conditions,lapply(w$conditions,function(x)x[c("kind","condition","class","message","call")])),
    whole_rng=eq(readRDS(file.path(direct,"complete-rng.rds")),readRDS(file.path(worker,"failed-original-rng.rds"))))
  saveRDS(checks,a[[4L]],version=3);stopifnot(all(unlist(checks)))
})
