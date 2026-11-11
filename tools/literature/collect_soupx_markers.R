# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-2.0-or-later
# Separate reference program: executes the unchanged GPL-2 SoupX package.
a <- commandArgs(TRUE); out <- a[1]; source <- a[2]; version <- a[3]
.libPaths(c(a[-(1:3)],.Library),include.site=FALSE)
stopifnot(as.character(getRversion())=="4.6.1")
Sys.setlocale("LC_COLLATE","C")
suppressPackageStartupMessages(library(SoupX))
stopifnot(packageDescription("SoupX")$Version==version)
original <- new.env(parent=asNamespace("SoupX"))
sys.source(file.path(source,"R","quickMarkers.R"),original,keep.source=FALSE)
guard <- function()stopifnot(identical(body(original$quickMarkers),body(SoupX::quickMarkers)),identical(formals(original$quickMarkers),formals(SoupX::quickMarkers)))
guard(); set.seed(741031); RNGkind("Mersenne-Twister","Inversion","Rejection")
cases <- list(); raw_objects<-list()
exact<-function(df)lapply(seq_len(nrow(df)),function(i)lapply(as.list(df[i,,drop=FALSE]),function(v)if(is.numeric(v))sprintf("%.17g",v) else v))
capture <- function(id,x,clusters,N,FDR,expressCut,native=TRUE){
  warnings<-messages<-character()
  result<-withCallingHandlers(tryCatch({
    y<-SoupX::quickMarkers(Matrix::Matrix(x,sparse=TRUE),setNames(clusters,colnames(x)),N=N,FDR=FDR,expressCut=expressCut)
    # Diagnostic-only second original call exposes every gene/cluster statistic.
    # Inf FDR is not a user-analysis setting or the selected marker family.
    all<-SoupX::quickMarkers(Matrix::Matrix(x,sparse=TRUE),setNames(clusters,colnames(x)),N=Inf,FDR=Inf,expressCut=expressCut)
    raw_objects[[id]]<<-list(input=x,clusters=clusters,N=N,FDR=FDR,expressCut=expressCut,selected=y,diagnostic_all=all)
    list(returned=TRUE,columns=as.list(names(y)),rows=exact(y),all_statistics=exact(all))
  },error=function(e)list(returned=FALSE,error=conditionMessage(e))),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")},message=function(m){messages<<-c(messages,conditionMessage(m));invokeRestart("muffleMessage")})
  entries<-which(x!=0,arr.ind=TRUE)
  cases[[length(cases)+1]]<<-list(id=id,genes=as.list(rownames(x)),cells=as.list(colnames(x)),clusters=as.list(clusters),entries=lapply(seq_len(nrow(entries)),function(i)list(gene=entries[i,1]-1L,cell=entries[i,2]-1L,value=x[entries[i,1],entries[i,2]])),N=if(is.infinite(N))"ALL" else N,FDR=FDR,expressCut=expressCut,native_domain=native,original=c(result,list(warnings=as.list(warnings),messages=as.list(messages))))
}
for(i in seq_len(72)){
  ng<-sample(3:45,1); nc<-sample(15:150,1); nk<-sample(2:5,1)
  groups<-sample(rep(c("NA","A","z","02","Inf")[seq_len(nk)],length.out=nc))
  x<-matrix(rbinom(ng*nc,1,sample(c(.02,.1,.35,.7),1))*sample(c(.5,.9,1,2,8),ng*nc,replace=TRUE),ng,nc)
  for(k in unique(groups))x[1,which(groups==k)[1]]<-2
  dimnames(x)<-list(paste0("g",sample(seq_len(ng))),paste0("cell",seq_len(nc)))
  capture(paste0("random-",i),x,groups,sample(c(1,2,5,Inf),1),sample(c(.01,.1,.5,1),1),sample(c(0,.9,1),1))
}
x<-matrix(0,10,1000,dimnames=list(paste0("g",seq_len(10)),paste0("c",seq_len(1000))));cl<-c(rep("A",20),rep("B",980));x[1,1:2]<-1;x[2,1:20]<-1;x[2,21:620]<-1;x[3,21:1000]<-1
capture("top-N-is-not-an-FDR-filter",x,cl,1,.001,0)
for(cut in c(0,.9,1)){
 x<-matrix(c(0,.5,.9,1,2,0,1,0,0,2,.9,.5,0,1,3,0,0,1,0,1),5,4,dimnames=list(c("z","NA","a","Inf","zero"),c("c4","c3","c2","c1")))
 capture(paste0("threshold-",cut),x,c("z","z","02","02"),Inf,1,cut)
}
x<-matrix(c(1,0,0,0,1,0,1,0,0,0,1,0),3,4,dimnames=list(c("z","a","zero"),paste0("c",1:4)))
capture("ties-zero-gene",x,c("B","A","B","A"),Inf,1,0)
x<-matrix(c(4,0,0,4,4,0,0,4),2,4,dimnames=list(c("\u03b2","\u00e9"),paste0("c",1:4)))
capture("unicode-C-collation",x,c("\u03b2","\u00e9","\u03b2","\u00e9"),Inf,1,0)
x<-matrix(1,3,4,dimnames=list(c("a","b","c"),paste0("c",1:4)))
capture("no-significant-markers",x,c("A","A","B","B"),10,.01,0)
capture("single-cluster",x,rep("A",4),1,1,0,FALSE)
capture("one-gene",x[1,,drop=FALSE],c("A","A","B","B"),1,1,0,FALSE)
capture("empty-expression-group",replace(x,rep(c(FALSE,FALSE,TRUE,TRUE),each=3),0),c("A","A","B","B"),1,1,0,FALSE)
capture("all-zero",x*0,c("A","A","B","B"),1,1,0,FALSE)
guard()
saveRDS(raw_objects,file.path(out,"original-objects.rds"),version=3)
jsonlite::write_json(list(version=version,R_version=as.character(getRversion()),Matrix_version=packageDescription("Matrix")$Version,locale=as.list(Sys.getlocale()),RNG=as.list(RNGkind()),source_bodies_and_formals_checked_before_and_after=TRUE,cases=cases),file.path(out,"controls.json"),auto_unbox=TRUE,pretty=TRUE,digits=NA,na="string")
cat("Captured",length(cases),"unchanged SoupX",version,"marker calls\n")
