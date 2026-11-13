# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Invoke an unchanged original function with complete retained typed arguments.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==7L)
lib<-normalizePath(args[1],mustWork=TRUE);base<-normalizePath(args[2],mustWork=TRUE)
fun<-args[3];input<-args[4];out<-normalizePath(args[5],mustWork=TRUE);seed<-args[6]
source(args[7],local=TRUE)
.libPaths(c(lib,base));suppressPackageStartupMessages(library(AllelicSeries,lib.loc=lib))
stopifnot(as.character(getRversion())=='4.5.3',packageDescription('AllelicSeries',lib.loc=lib)$Version=='0.1.1.2')
request<-readRDS(input);validate_original_arguments(request)
if(seed!='MISSING')set.seed(as.integer(seed))
packet<-capture_original_call(fun,request,seed);write_original_packet(packet,out)
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
cat(packet$status,'\n');quit(status=if(packet$analysis_admitted)0L else 2L)
