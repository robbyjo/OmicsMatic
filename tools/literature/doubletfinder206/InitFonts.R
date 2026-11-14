# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Provision original Cairo/fontconfig caches before the exact runtime inventory.
a<-commandArgs(TRUE);stopifnot(length(a)==1L)
out<-a[[1L]];if(file.exists(out))stop("Use a NEW graphics readiness output")
dir.create(out)
png(file.path(out,"original-graphics-readiness.png"),width=800L,height=800L,type="cairo")
plot.new();text(.5,.5,"Original R graphics runtime")
dev.off()
saveRDS(list(status="ORIGINAL_GRAPHICS_FONT_CACHE_INITIALIZED_NOT_ANALYSIS",capabilities=capabilities(),session=sessionInfo(),models_fit=FALSE),file.path(out,"original-graphics-readiness.rds"),version=3)
cat("ORIGINAL_GRAPHICS_FONT_CACHE_INITIALIZED_NOT_ANALYSIS\n")
