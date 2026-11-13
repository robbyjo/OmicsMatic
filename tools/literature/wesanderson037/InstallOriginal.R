# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Invoked only by an explicitly permitted provider setup; no image/omics analysis.
args <- commandArgs(TRUE)
if (dir.exists(args[2])) stop("NEW private library required")
dir.create(args[2],recursive=TRUE)
install.packages(args[1],lib=args[2],repos=NULL,type="source",INSTALL_opts="--no-byte-compile")
library(wesanderson,lib.loc=args[2])
stopifnot(as.character(getRversion())=="4.5.3",as.character(packageVersion("wesanderson"))=="0.3.7",length(wes_palettes)==24)
writeLines(c(R.version.string,as.character(packageVersion("wesanderson")),names(wes_palettes)),file.path(args[2],"READY.txt"))
cat("READY_PRIVATE_ORIGINAL_WESANDERSON037_NO_ANALYSIS\n")
