# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Read a preserved original result without refitting any model.
a<-commandArgs(TRUE);out<-a[1];result_file<-a[2];.libPaths(c(a[-(1:2)],.Library),include.site=FALSE);suppressPackageStartupMessages(library(dndscv));result<-readRDS(result_file)
token<-function(x){if(is.numeric(x)&&is.nan(x))"NaN" else if(is.na(x))"NA" else if(is.numeric(x)&&is.infinite(x)){if(x>0)"Inf" else "-Inf"} else if(is.double(x))sprintf("%.17g",x) else as.character(x)}
kind<-function(x){if(is.numeric(x)&&is.nan(x))"NaN" else if(is.na(x))"NA" else if(is.numeric(x)&&is.infinite(x)){if(x>0)"positive-infinity" else "negative-infinity"} else "value"}
for(name in names(result)[vapply(result,is.data.frame,logical(1))]){frame<-result[[name]];columns<-lapply(seq_along(frame),function(j){x<-frame[[j]];list(name=names(frame)[j],class=as.list(class(x)),type=typeof(x),tokens=as.list(vapply(seq_along(x),function(i)token(x[i]),character(1))),kinds=as.list(vapply(seq_along(x),function(i)kind(x[i]),character(1))))});jsonlite::write_json(list(rows=nrow(frame),row_names=as.list(rownames(frame)),columns=columns),file.path(out,paste0(name,".json")),pretty=TRUE,auto_unbox=TRUE,digits=NA,na="string",null="null")}
for(name in c("N","L")){con<-gzfile(file.path(out,paste0(name,".f64be.gz")),open="wb");writeBin(as.double(result[[name]]),con,size=8,endian="big");close(con)}
cat("Exported complete preserved tables/N/L without a model call\n")
