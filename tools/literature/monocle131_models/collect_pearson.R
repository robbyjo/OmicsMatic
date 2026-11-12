# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(trailingOnly=TRUE);stopifnot(length(args)>=5L);.libPaths(args[-c(1,2,3,4)],include.site=FALSE)
out <- args[1];dll <- dyn.load(args[2]);source <- args[3];fixture <- args[4]
ns <- new.env(parent=asNamespace("stats"));ns$F_rcorr <- getNativeSymbolInfo("rcorr_",dll)
definition <- NULL
for(expr in parse(source,keep.source=FALSE))if(is.call(expr)&&length(expr)==3L&&identical(expr[[2]],as.name("rcorr")))definition <- expr[[3]]
stopifnot(!is.null(definition));original <- eval(definition,envir=ns)
token <- function(v)if(is.nan(v))"NaN"else if(is.na(v))"NA"else if(is.infinite(v))if(v>0)"Inf"else"-Inf"else sprintf("%.17g",v)
numeric_tokens <- function(x)as.list(unname(vapply(x,token,character(1))))
inputs <- list(
 increasing=list(x=1:7,y=1:7),decreasing=list(x=1:7,y=7:1),
 constant=list(x=rep(4,7),y=1:7),zero_cross_product=list(x=c(-2,-1,0,1,2),y=c(2,-1,-2,-1,2)),
 missing_pairs=list(x=c(1,2,NA,4,5,NA,7),y=c(7,6,5,4,3,NA,1)),
 sentinel=list(x=c(1,2,1e49,4,5,1e50,7),y=1:7),
 offset=list(x=1e12+c(1,5,2,7,3,8,4),y=c(2,1,4,3,6,5,7)),
 tiny=list(x=1e-180*(1:7),y=1e-180*(7:1)))
d <- read.delim(file.path(fixture,"normalized.tsv"),check.names=FALSE);m <- as.matrix(d[-1]);meta <- read.delim(file.path(fixture,"cells.tsv"),check.names=FALSE)
for(i in seq_len(nrow(m)))inputs[[paste0("normalized-",d$gene_id[i])]] <- list(x=m[i,],y=meta$Gi)
results <- lapply(names(inputs),function(name){x <- inputs[[name]]$x;y <- inputs[[name]]$y;r <- original(x,y);v <- r$r[1,2];list(id=name,x=numeric_tokens(x),y=numeric_tokens(y),coefficient=token(v),kind=if(is.nan(v))"NaN"else if(is.na(v))"NA"else"value",pairs=r$n[1,2],positive=isTRUE(v>0))})
jsonlite::write_json(list(status="CAPTURED_ORIGINAL_HMISC_PEARSON_PRIMITIVE_CONTROLS_NOT_FULL_PACKAGE",results=results,source_function_modified=FALSE,paper_version_known=FALSE,whole_package_qualified=FALSE),file.path(out,"PEARSON-CONTROLS.json"),pretty=TRUE,auto_unbox=TRUE,digits=NA,na="string")
dyn.unload(args[2]);cat("CAPTURED_ORIGINAL_HMISC_PEARSON_PRIMITIVE_CONTROLS_NOT_FULL_PACKAGE\n")
