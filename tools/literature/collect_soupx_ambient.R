# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-2.0-or-later
# Separate original-reference program; does not patch or translate SoupX functions.
a<-commandArgs(TRUE);out<-a[1];source<-a[2];version<-a[3];fixture_source<-a[4]
.libPaths(c(a[-(1:4)],.Library),include.site=FALSE)
Sys.setlocale("LC_COLLATE","C");options(mc.cores=1)
suppressPackageStartupMessages({library(Matrix);library(SoupX)});stopifnot(packageDescription("SoupX")$Version==version,as.character(getRversion())=="4.6.1")
original<-new.env(parent=asNamespace("SoupX"))
for(p in list.files(file.path(source,"R"),pattern="[.]R$",full.names=TRUE))sys.source(p,original,keep.source=FALSE)
guards<-c("SoupChannel","estimateSoup","setClusters","setContaminationFraction","estimateNonExpressingCells","calculateContaminationFraction","autoEstCont","adjustCounts","quickMarkers","expandClusters")
if(version=="1.6.2")guards<-c(guards,"alloc")
guard<-function()for(name in guards){f<-get(name,asNamespace("SoupX"));g<-get(name,original,inherits=FALSE);stopifnot(identical(body(f),body(g)),identical(formals(f),formals(g)))}
guard();RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(781641)
toy<-file.path(fixture_source,"inst","extdata","toyData")
literal<-function(p)read.table(p,sep="\t",header=FALSE,quote="",comment.char="",check.names=FALSE,colClasses="character",na.strings=NULL,stringsAsFactors=FALSE)
read_counts<-function(folder){g<-literal(file.path(folder,"genes.tsv"));c<-literal(file.path(folder,"barcodes.tsv"))[[1]];x<-methods::as(Matrix::readMM(file.path(folder,"matrix.mtx")),"dgCMatrix");stopifnot(!anyDuplicated(g[[2]]),!anyDuplicated(c));dimnames(x)<-list(g[[2]],c);x}
raw_folder<-list.dirs(file.path(toy,"raw_gene_bc_matrices"),recursive=FALSE,full.names=TRUE);filtered_folder<-list.dirs(file.path(toy,"filtered_gene_bc_matrices"),recursive=FALSE,full.names=TRUE);stopifnot(length(raw_folder)==1,length(filtered_folder)==1)
tod<-read_counts(raw_folder);toc<-read_counts(filtered_folder);metadata<-read.table(file.path(toy,"metaData.tsv"),sep="\t",header=TRUE,row.names=1,quote="",comment.char="",check.names=FALSE,colClasses="character",na.strings=NULL,stringsAsFactors=FALSE)
clusters<-setNames(metadata[colnames(toc),"res.1"],colnames(toc));stopifnot(identical(rownames(tod),rownames(toc)),!anyNA(clusters),all(colnames(toc)%in%colnames(tod)))
exact<-function(df)lapply(seq_len(nrow(df)),function(i)lapply(as.list(df[i,,drop=FALSE]),function(v)if(is.numeric(v))sprintf("%.17g",v) else v))
sparse<-function(x){s<-summary(methods::as(x,"dgCMatrix"));list(genes=as.list(rownames(x)),cells=as.list(colnames(x)),entries=lapply(seq_len(nrow(s)),function(i)list(gene=s$i[i]-1L,cell=s$j[i]-1L,value=sprintf("%.17g",s$x[i]))))}
json<-function(x,p)jsonlite::write_json(x,p,auto_unbox=TRUE,pretty=TRUE,digits=NA,na="string")
saveRDS(list(tod=tod,toc=toc,clusters=clusters),file.path(out,"inputs.rds"),version=3)
json(list(raw=sparse(tod),filtered=sparse(toc),clusters=as.list(clusters),raw_subset_equal=identical(tod[,colnames(toc),drop=FALSE],toc),seed=781641,RNG=as.list(RNGkind())),file.path(out,"inputs.json"))
construct<-function(range=c(0,100),x=tod,y=toc,cl=clusters){
 if(version=="1.3.0")sc<-SoupX::SoupChannel(x,y,soupRange=range,keepDroplets=TRUE) else {sc<-SoupX::SoupChannel(x,y,calcSoupProfile=FALSE);sc<-SoupX::estimateSoup(sc,soupRange=range,keepDroplets=TRUE)}
 SoupX::setClusters(sc,cl)
}
fixed<-function(sc,rho,force=FALSE){if(version=="1.3.0")SoupX::setContaminationFraction(sc,rho) else SoupX::setContaminationFraction(sc,rho,forceAccept=force)}
cases<-list()
capture<-function(id,operation,settings=list()){
 guard();target<-file.path(out,id);dir.create(target);warnings<-messages<-character();started<-proc.time()[[3]]
 result<-withCallingHandlers(tryCatch({
  value<-operation();saveRDS(value,file.path(target,"original-return.rds"),version=3)
  result<-list(returned=TRUE,class=as.list(class(value)))
  if(is(value,"SoupChannel")){result$metadata<-exact(value$metaData);result$cell_ids<-as.list(rownames(value$metaData));result$soup_profile<-exact(value$soupProfile);result$gene_ids<-as.list(rownames(value$soupProfile));if(is.list(value$fit)&&!is(value$fit,"glm"))result$fit_names<-as.list(names(value$fit))}
  if(is(value,"Matrix")||is.matrix(value))result$matrix<-sparse(value)
  if(is.data.frame(value))result$table<-exact(value)
  if(is.list(value)&&!is(value,"SoupChannel")&&!is.data.frame(value)&&"channel"%in%names(value)){result$channel_metadata<-exact(value$channel$metaData);result$cell_ids<-as.list(rownames(value$channel$metaData));if("corrected"%in%names(value))result$corrected<-sparse(value$corrected);if("nonexpressing"%in%names(value)){result$nonexpressing<-unname(lapply(seq_len(nrow(value$nonexpressing)),function(i)as.list(value$nonexpressing[i,])));result$nonexpressing_cells<-as.list(rownames(value$nonexpressing));result$nonexpressing_groups<-as.list(colnames(value$nonexpressing))};if("estimate_table"%in%names(value))result$estimate_table<-exact(value$estimate_table)}
  result
 },error=function(e)list(returned=FALSE,error=conditionMessage(e))),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")},message=function(m){messages<<-c(messages,conditionMessage(m));invokeRestart("muffleMessage")})
 guard();result<-c(list(id=id,settings=settings),result,list(warnings=as.list(warnings),messages=as.list(messages),elapsed_seconds=proc.time()[[3]]-started));json(result,file.path(target,"result.json"));cases[[length(cases)+1]]<<-result
}
for(range in list(c(0,10),c(0,100),c(1,20)))capture(paste0("profile-",range[1],"-",range[2]),function()construct(range),list(soup_range=as.list(range)))
base<-construct();gene_list<-list(CD7="CD7");stopifnot("CD7"%in%rownames(toc))
for(scope in c("cell-only","supplied-clusters"))for(method in c("subtraction","soupOnly","multinomial"))for(round in c(FALSE,TRUE)){
 capture(paste("fixed",scope,method,round,sep="-"),function(){set.seed(190731);sc<-fixed(base,.1);x<-SoupX::adjustCounts(sc,clusters=if(scope=="cell-only")FALSE else clusters,method=method,roundToInt=round,verbose=0,tol=1e-3,pCut=.01);list(channel=sc,corrected=x)},list(rho=.1,scope=scope,method=method,round_to_integer=round,tolerance=.001,p_cut=.01,seed=190731))
}
for(rho in c(0,.5,.9))capture(paste0("fixed-rho-",rho),function()fixed(base,rho),list(rho=rho,force_accept=FALSE))
if(version=="1.6.2")capture("fixed-rho-0.9-forced",function()fixed(base,.9,TRUE),list(rho=.9,force_accept=TRUE))
for(scope in c("cell-only","supplied-clusters"))capture(paste0("manual-automatic-mask-",scope),function(){
  use<-SoupX::estimateNonExpressingCells(base,gene_list,clusters=if(scope=="cell-only")FALSE else clusters,maximumContamination=1,FDR=.05)
  sc<-if(version=="1.3.0")SoupX::calculateContaminationFraction(base,gene_list,use,verbose=FALSE,cellSpecificEstimates=FALSE) else SoupX::calculateContaminationFraction(base,gene_list,use,verbose=FALSE,forceAccept=FALSE)
  list(channel=sc,nonexpressing=use)
 },list(genes=gene_list,mask_mode="estimateNonExpressingCells",scope=scope,maximum_contamination=1,FDR=.05,cell_specific=FALSE))
capture("manual-supplied-mask",function(){
 use<-matrix(clusters!=clusters[1],ncol=1,dimnames=list(names(clusters),"CD7"));sc<-if(version=="1.3.0")SoupX::calculateContaminationFraction(base,gene_list,use,verbose=FALSE,cellSpecificEstimates=FALSE) else SoupX::calculateContaminationFraction(base,gene_list,use,verbose=FALSE,forceAccept=FALSE);list(channel=sc,nonexpressing=use)
},list(genes=gene_list,mask_mode="caller-supplied-toy-test-only",cell_specific=FALSE))
if(version=="1.6.2"){
 capture("auto-current-defaults",function()SoupX::autoEstCont(base,doPlot=FALSE,forceAccept=FALSE,verbose=FALSE),list(api="current-SoupChannel-return",do_plot=FALSE,force_accept=FALSE))
 capture("auto-P145-overrides",function()SoupX::autoEstCont(base,tfidfMin=.95,soupQuantile=.6,priorRho=.1,contaminationRange=c(.05,.8),doPlot=FALSE,forceAccept=FALSE,verbose=FALSE),list(api="current-SoupChannel-return",tfidfMin=.95,soupQuantile=.6,priorRho=.1,contaminationRange=as.list(c(.05,.8)),do_plot=FALSE,force_accept=FALSE))
}else{
 capture("auto-legacy-defaults",function()SoupX::autoEstCont(base,doPlot=FALSE),list(api="legacy-dataframe-return",do_plot=FALSE))
 capture("auto-legacy-declared-apply",function(){dd<-SoupX::autoEstCont(base,doPlot=FALSE);rho<-unique(dd$rhoEst);stopifnot(length(rho)==1L,is.finite(rho));sc<-SoupX::setContaminationFraction(base,rho);list(channel=sc,estimate_table=dd)},list(api="legacy-dataframe-return",apply_estimate="explicit-unique-density-mode",do_plot=FALSE))
}
capture("first-cluster-FALSE-source-branch",function(){sc<-fixed(base,.1);cl<-clusters;cl[cl==cl[1]]<-"FALSE";SoupX::adjustCounts(sc,clusters=cl,method="subtraction",roundToInt=FALSE,verbose=0)},list(first_literal_cluster="FALSE",method="subtraction"))
capture("manual-no-accepted-cells",function(){use<-matrix(FALSE,nrow=ncol(toc),ncol=1,dimnames=list(colnames(toc),"CD7"));if(version=="1.3.0")SoupX::calculateContaminationFraction(base,gene_list,use,verbose=FALSE,cellSpecificEstimates=FALSE) else SoupX::calculateContaminationFraction(base,gene_list,use,verbose=FALSE)},list(mask="all-FALSE"))
capture("constructor-gene-order-mismatch",function()construct(x=tod[rev(seq_len(nrow(tod))),,drop=FALSE]),list(raw_gene_order="reversed"))
capture("profile-no-selected-droplets",function()construct(range=c(1000000,1000001)),list(soup_range=as.list(c(1000000,1000001))))
guard();json(list(version=version,R_version=as.character(getRversion()),Matrix_version=packageDescription("Matrix")$Version,source_bodies_and_formals_checked_before_and_after=TRUE,guarded_functions=as.list(guards),cases=cases,user_analysis_admitted=FALSE),file.path(out,"controls.json"));cat("Captured",length(cases),"original SoupX",version,"ambient control cases\n")
