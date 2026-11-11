# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-2.0-or-later
# Independent GPL-2-or-later R preparation program for the separate GPL-2 SoupX runtime.
a <- commandArgs(TRUE); out <- a[1]; source <- a[2]; version <- a[3]; library <- a[4]
.libPaths(c(library,a[-(1:4)],.Library),include.site=FALSE)
stopifnot(as.character(getRversion())=="4.6.1")
install.packages(source,repos=NULL,type="source",lib=library,dependencies=FALSE)
suppressPackageStartupMessages(library(SoupX))
stopifnot(packageDescription("SoupX")$Version==version)
original <- new.env(parent=asNamespace("SoupX"))
for(p in list.files(file.path(source,"R"),pattern="[.]R$",full.names=TRUE))sys.source(p,envir=original,keep.source=FALSE)
guards <- c("SoupChannel","estimateSoup","setClusters","setContaminationFraction","estimateNonExpressingCells","calculateContaminationFraction","autoEstCont","adjustCounts","quickMarkers","expandClusters")
if(exists("setSoupProfile",envir=original,inherits=FALSE))guards<-c(guards,"setSoupProfile")
guard <- function()for(name in guards){expected<-get(name,original,inherits=FALSE);actual<-get(name,asNamespace("SoupX"),inherits=FALSE);stopifnot(identical(formals(expected),formals(actual)),identical(body(expected),body(actual)))}
guard();warnings<-character();messages<-character()
health <- withCallingHandlers({
 x<-Matrix::Matrix(matrix(c(5,0,2,4,0,1,0,5,1,0,4,2),nrow=3,dimnames=list(c("g1","g2","g3"),c("c1","c2","c3","c4"))),sparse=TRUE)
 if(version=="1.3.0")sc<-SoupX::SoupChannel(x,x,soupRange=c(0,100),keepDroplets=TRUE) else {sc<-SoupX::SoupChannel(x,x,calcSoupProfile=FALSE);sc<-SoupX::estimateSoup(sc,soupRange=c(0,100),keepDroplets=TRUE)}
 sc<-SoupX::setClusters(sc,setNames(c("A","A","B","B"),colnames(x)));sc<-SoupX::setContaminationFraction(sc,0.1)
 corrected<-SoupX::adjustCounts(sc,clusters=FALSE,method="subtraction",roundToInt=FALSE,verbose=0);markers<-SoupX::quickMarkers(x,setNames(c("A","A","B","B"),colnames(x)),N=1,FDR=1,expressCut=0.9)
 stopifnot(identical(dim(corrected),dim(x)),identical(dimnames(corrected),dimnames(x)),all(is.finite(corrected@x)),is.data.frame(markers))
 saveRDS(list(input=x,channel=sc,corrected=corrected,markers=markers),file.path(out,"health.rds"));list(genes=nrow(x),cells=ncol(x),marker_rows=nrow(markers),corrected_nonzero=length(corrected@x))
},warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")},message=function(m){messages<<-c(messages,conditionMessage(m));invokeRestart("muffleMessage")})
guard()
versions<-as.list(vapply(c("SoupX","Seurat","SeuratObject","Matrix","ggplot2","jsonlite"),function(p)packageDescription(p)$Version,character(1)))
functions<-lapply(guards,function(name){f<-get(name,asNamespace("SoupX"));list(name=name,formals=paste(deparse(formals(f),width.cutoff=500),collapse="\n"),body=paste(deparse(body(f),width.cutoff=500),collapse="\n"))});names(functions)<-guards
jsonlite::write_json(list(status="loaded-source-bound-original-SoupX-reference-only",version=version,R_version=as.character(getRversion()),platform=R.version$platform,versions=versions,guarded_functions=as.list(guards),functions=functions,health=health,warnings=as.list(warnings),messages=as.list(messages),user_analysis_admitted=FALSE),file.path(out,"R-RESULT.json"),auto_unbox=TRUE,pretty=TRUE,digits=NA)
cat("Installed original SoupX",version,"into new private library; source bodies/formals and tiny marker/correction health passed\n")
