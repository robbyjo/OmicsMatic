# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==1L)
saveRDS(list(lambda=1,pval=NaN),file.path(args[1],'original-nonfinite-arguments.rds'),version=3)
saveRDS(1,file.path(args[1],'invalid-arguments.rds'),version=3)
saveRDS(list(anno=1:3,beta=c(.1,-.2,.3),se=rep(.2,3),return_beta=TRUE),file.path(args[1],'original-warning-arguments.rds'),version=3)
