# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-2.0-or-later
# Correct preserved original reference channels directly; independent of the typed worker program.
a<-commandArgs(TRUE);out<-a[1];source<-a[2];controls<-a[3];version<-a[4]
.libPaths(c(a[-(1:4)],.Library),include.site=FALSE)
suppressPackageStartupMessages({library(Matrix);library(SoupX)})
stopifnot(as.character(getRversion())=="4.6.1",packageDescription("SoupX")$Version==version)
original<-new.env(parent=asNamespace("SoupX"));for(p in list.files(file.path(source,"R"),pattern="[.]R$",full.names=TRUE))sys.source(p,original,keep.source=FALSE)
guards<-Filter(function(n)is.function(get(n,original,inherits=FALSE)),ls(original,all.names=TRUE));guard<-function()for(n in guards){x<-get(n,original,inherits=FALSE);y<-get(n,asNamespace("SoupX"),inherits=FALSE);stopifnot(identical(formals(x),formals(y)),identical(body(x),body(y)))}
guard();Sys.setlocale("LC_COLLATE","C");RNGkind("Mersenne-Twister","Inversion","Rejection");options(mc.cores=1)
input<-readRDS(file.path(controls,"inputs.rds"));clusters<-input$clusters;cells<-colnames(input$toc);rho<-setNames(.05+((seq_along(cells)-1L)%%5)*.025,cells)
writeLines(c("cell_id\trho",paste(cells,sprintf("%.17g",rho),sep="\t")),file.path(out,"rho-by-cell.tsv"),useBytes=TRUE)
base<-readRDS(file.path(controls,"fixed-rho-0","original-return.rds"));varied<-SoupX::setContaminationFraction(base,rho)
manual_provided<-readRDS(file.path(controls,"manual-supplied-mask","original-return.rds"))$channel
manual_estimated<-readRDS(file.path(controls,"manual-automatic-mask-supplied-clusters","original-return.rds"))$channel
auto<-if(version=="1.6.2")readRDS(file.path(controls,"auto-P145-overrides","original-return.rds")) else readRDS(file.path(controls,"auto-legacy-declared-apply","original-return.rds"))$channel
definitions<-list(list(id="varied-rho-cell-only",channel=varied,scope="cell-only"),list(id="varied-rho-supplied-clusters",channel=varied,scope="supplied-clusters"),list(id="manual-provided-adjust",channel=manual_provided,scope="supplied-clusters"),list(id="manual-estimated-adjust",channel=manual_estimated,scope="cell-only"),list(id="auto-adjust-cell-only",channel=auto,scope="cell-only"),list(id="auto-adjust-supplied-clusters",channel=auto,scope="supplied-clusters"))
rows<-list()
for(case in definitions){
 guard();warnings<-messages<-character();set.seed(190731)
 result<-withCallingHandlers(SoupX::adjustCounts(case$channel,clusters=if(case$scope=="cell-only")FALSE else clusters,method="subtraction",roundToInt=FALSE,verbose=0,tol=.001,pCut=.01),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")},message=function(m){messages<<-c(messages,conditionMessage(m));invokeRestart("muffleMessage")})
 dir.create(file.path(out,case$id));saveRDS(result,file.path(out,case$id,"original-return.rds"),version=3);s<-summary(methods::as(methods::as(result,"generalMatrix"),"CsparseMatrix"));guard()
 rows[[length(rows)+1L]]<-list(id=case$id,scope=case$scope,method="subtraction",round_to_integer=FALSE,tolerance=.001,p_cut=.01,seed=190731,genes=as.list(rownames(result)),cells=as.list(colnames(result)),entries=lapply(seq_len(nrow(s)),function(i)list(gene=s$i[i]-1L,cell=s$j[i]-1L,value=sprintf("%.17g",s$x[i]))),warnings=as.list(warnings),messages=as.list(messages))
}
guard();jsonlite::write_json(list(version=version,R_version=as.character(getRversion()),source_bodies_and_formals_checked_before_and_after=TRUE,guarded_functions=as.list(guards),cases=rows,user_analysis_admitted=FALSE),file.path(out,"controls.json"),auto_unbox=TRUE,pretty=TRUE,digits=NA);cat("Captured6 direct original correction references from preserved original channels\n")
