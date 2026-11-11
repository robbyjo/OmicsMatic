# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-2.0-or-later
# Separate program running the unchanged GPL-2 SoupX reference.
a<-commandArgs(TRUE);out<-a[1];source<-a[2];version<-a[3]
.libPaths(c(a[-(1:3)],.Library),include.site=FALSE)
Sys.setlocale("LC_COLLATE","C");suppressPackageStartupMessages(library(SoupX))
stopifnot(packageDescription("SoupX")$Version==version)
original<-new.env(parent=asNamespace("SoupX"));sys.source(file.path(source,"R","quickMarkers.R"),original,keep.source=FALSE)
guard<-function()stopifnot(identical(body(original$quickMarkers),body(SoupX::quickMarkers)),identical(formals(original$quickMarkers),formals(SoupX::quickMarkers)))
guard();x<-Matrix::sparseMatrix(i=1:2,j=1:2,x=c(1,1),dims=c(2,2));dimnames(x)<-list(c("b","a"),c("c1","c2"));cl<-c("NA","A");warnings<-character()
exact<-function(df)lapply(seq_len(nrow(df)),function(i)lapply(as.list(df[i,,drop=FALSE]),function(v)if(is.numeric(v))sprintf("%.17g",v) else v))
calls<-withCallingHandlers({
  all<-SoupX::quickMarkers(x,cl,N=Inf,FDR=Inf,expressCut=0)
  cutoff<-all$qval[all$gene=="a" & all$cluster=="A"]
  lapply(c(1,cutoff),function(fdr){y<-SoupX::quickMarkers(x,cl,N=Inf,FDR=fdr,expressCut=0);list(fdr=sprintf("%.17g",fdr),rows=exact(y),all_statistics=exact(all))})
},warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")})
guard();jsonlite::write_json(list(version=version,R_version=as.character(getRversion()),source_bodies_and_formals_checked_before_and_after=TRUE,calls=calls,warnings=as.list(warnings)),file.path(out,"reference.json"),auto_unbox=TRUE,pretty=TRUE,digits=NA)
