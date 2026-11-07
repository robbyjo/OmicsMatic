# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-2.0-or-later
# Direct original same-matrix/no-cluster reference with literal identifiers.
a<-commandArgs(TRUE);out<-a[1];source<-a[2];version<-a[3]
.libPaths(c(a[-(1:3)],.Library),include.site=FALSE)
suppressPackageStartupMessages({library(Matrix);library(SoupX)})
stopifnot(as.character(getRversion())=="4.6.1",packageDescription("SoupX")$Version==version)
original<-new.env(parent=asNamespace("SoupX"));for(p in list.files(file.path(source,"R"),pattern="[.]R$",full.names=TRUE))sys.source(p,original,keep.source=FALSE)
guard<-function()for(n in Filter(function(n)is.function(get(n,original,inherits=FALSE)),ls(original,all.names=TRUE))){x<-get(n,original,inherits=FALSE);y<-get(n,asNamespace("SoupX"),inherits=FALSE);stopifnot(identical(formals(x),formals(y)),identical(body(x),body(y)))}
guard();genes<-c("NA","Inf","z");cells<-c("NA","TRUE","c space","\u03b4")
# Match all12 supplied Matrix Market coordinates, including4 explicit zero slots.
# Dense-to-sparse construction would discard those slots and changes historical
# SoupX floating-point operation order. Retain that previous failed comparison.
x<-Matrix::sparseMatrix(i=rep(1:3,each=4),j=rep(1:4,3),x=as.double(c(5,3,0,1,0,2,4,0,2,0,3,5)),dims=c(3L,4L),dimnames=list(genes,cells))
x<-methods::as(methods::as(x,"generalMatrix"),"CsparseMatrix")
if(version=="1.3.0")sc<-SoupX::SoupChannel(x,x,soupRange=c(0,100),keepDroplets=TRUE) else {sc<-SoupX::SoupChannel(x,x,calcSoupProfile=FALSE);sc<-SoupX::estimateSoup(sc,soupRange=c(0,100),keepDroplets=TRUE)}
sc<-SoupX::setContaminationFraction(sc,.1);RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(190731);warnings<-messages<-character()
result<-withCallingHandlers(SoupX::adjustCounts(sc,clusters=FALSE,method="subtraction",roundToInt=FALSE,verbose=0,tol=.001,pCut=.01),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")},message=function(m){messages<<-c(messages,conditionMessage(m));invokeRestart("muffleMessage")})
guard();saveRDS(list(input=x,channel=sc,corrected=result),file.path(out,"original-return.rds"),version=3);s<-summary(methods::as(methods::as(result,"generalMatrix"),"CsparseMatrix"));jsonlite::write_json(list(version=version,genes=as.list(genes),cells=as.list(cells),entries=lapply(seq_len(nrow(s)),function(i)list(gene=s$i[i]-1L,cell=s$j[i]-1L,value=sprintf("%.17g",s$x[i]))),warnings=as.list(warnings),messages=as.list(messages),source_bodies_and_formals_checked_before_and_after=TRUE,empty_droplet_calling_performed=FALSE),file.path(out,"controls.json"),auto_unbox=TRUE,pretty=TRUE,digits=NA)
