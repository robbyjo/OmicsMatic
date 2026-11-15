# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Complete real public-runner result versus an independent original reference.
local({
  a<-commandArgs(TRUE);stopifnot(length(a)==4L);.libPaths(c(a[[1L]],.libPaths()))
  requireNamespace("SingleR");direct<-a[[2L]];public<-a[[3L]]
  d<-readRDS(file.path(direct,"complete-conditions.rds"));w<-readRDS(file.path(public,"original/complete-original-conditions.rds"))
  eq<-function(x,y)identical(serialize(x,NULL,version=3),serialize(y,NULL,version=3))
  checks<-list(complete_original_return=eq(readRDS(file.path(direct,"complete-return.rds")),readRDS(file.path(public,"original/complete-original-return.rds"))),
    complete_original_conditions=eq(d$conditions,lapply(w$conditions,function(x)x[c("kind","condition","class","message","call")])) ,
    complete_original_rng=eq(readRDS(file.path(direct,"complete-rng.rds")),readRDS(file.path(public,"original/complete-original-rng.rds"))),
    separately_recorded_rng_before=eq(readRDS(file.path(direct,"complete-rng-before.rds")),readRDS(file.path(public,"original/complete-original-rng-before.rds"))))
  saveRDS(checks,a[[4L]],version=3);stopifnot(all(unlist(checks)))
})
