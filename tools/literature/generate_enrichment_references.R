# Copyright (C) 2026 JLinAlg contributors
# SPDX-License-Identifier: GPL-3.0-or-later
# Independent original-program references; no Java outputs enter expected values.
args <- commandArgs(trailingOnly=TRUE)
if(length(args)!=2L)stop("Usage: Rscript --vanilla generate_enrichment_references.R LIBRARY OUTPUT_DIRECTORY")
lib<-normalizePath(args[[1]],winslash="/",mustWork=TRUE);out<-normalizePath(args[[2]],winslash="/",mustWork=FALSE)
if(dir.exists(out))stop("Output exists; preserve original references and select a new directory")
dir.create(out,recursive=TRUE);.libPaths(c(lib,.Library))
stopifnot(as.character(packageVersion("fgsea"))=="1.38.0",as.character(packageVersion("enrichit"))=="0.2.6")
warnings<-character();fmt<-function(x)paste(vapply(x,function(v)if(is.nan(v)||is.na(v))"NaN"else format(v,digits=17,scientific=TRUE,trim=TRUE),character(1)),collapse=";")
arrays<-list(c(6,5,4,3,2,1),c(5,4,2,1,-1,-2,-4,-5),c(1,1,1,0,0,0,-1,-1,-1),c(10,1,0,0,0,-1,-10),c(9,7,5,3,1,-2,-4,-6,-8),c(1e-100,5e-101,0,-5e-101,-1e-100))
set.seed(362725)
for(n in c(7,16,31))arrays[[length(arrays)+1L]]<-sort(rnorm(n),decreasing=TRUE)
gsea<-list();case<-0L
withCallingHandlers({
  for(a in seq_along(arrays))for(exponent in c(0,0.5,1,2)) {
    stats<-arrays[[a]];names(stats)<-sprintf("gene%03d",seq_along(stats));n<-length(stats)
    selections<-list(1L,n,seq_len(max(1L,n%/%3L)),seq.int(2L,n,by=2L),unique(c(1L,n)))
    if(any(stats==0))selections[[length(selections)+1L]]<-which(stats==0)
    prepared<-getFromNamespace("prepareStats","fgsea")(stats,scoreType="std",gseaParam=exponent)
    if(any(!is.finite(prepared)))next
    for(s in seq_along(selections)) {
      selected<-selections[[s]];if(length(selected)==n)next
      for(tail in c("std","pos","neg")) {
        value<-fgsea::calcGseaStat(prepared,selected,returnAllExtremes=TRUE,returnLeadingEdge=TRUE,scoreType=tail)
        case<-case+1L;gsea[[case]]<-data.frame(case=paste(a,exponent,s,tail,sep="-"),engine="fgsea-integer",ranking=fmt(stats),exponent=exponent,selected=paste(selected-1L,collapse=";"),tail=tail,weights=fmt(prepared),score=fmt(value$res),maximum=fmt(max(value$tops)),minimum=fmt(min(value$bottoms)),leading=paste(value$leadingEdge-1L,collapse=";"),stringsAsFactors=FALSE)
      }
      raw<-enrichit::gseaScores(stats,names(stats)[selected],exponent=exponent,fortify=TRUE)
      value<-enrichit::gseaScores(stats,names(stats)[selected],exponent=exponent,fortify=FALSE)
      case<-case+1L;gsea[[case]]<-data.frame(case=paste(a,exponent,s,"enrichit",sep="-"),engine="enrichit-raw",ranking=fmt(stats),exponent=exponent,selected=paste(selected-1L,collapse=";"),tail="std",weights=fmt(abs(stats)^exponent),score=fmt(value),maximum=fmt(max(raw$runningScore)),minimum=fmt(min(raw$runningScore)),leading="not-returned-by-helper",stringsAsFactors=FALSE)
    }
  }
},warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")})
gsea<-do.call(rbind,gsea);write.table(gsea,file.path(out,"gsea.tsv"),sep="\t",quote=FALSE,row.names=FALSE,na="NaN")
ora<-list();at<-0L
for(N in c(3L,10L,100L,1000L,1000000L))for(n in unique(c(0L,1L,N%/%3L,N)))for(m in unique(c(0L,1L,N%/%4L,N))) {
  lower<-max(0L,n+m-N);upper<-min(n,m)
  for(k in unique(c(lower,upper,(lower+upper)%/%2L))) {
    at<-at+1L;ora[[at]]<-data.frame(population=N,selected=n,term=m,overlap=k,log_p=fmt(stats::phyper(k-1,m,N-m,n,lower.tail=FALSE,log.p=TRUE)))
  }
}
ora<-do.call(rbind,ora);write.table(ora,file.path(out,"ora.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
adj<-list();at<-0L;families<-list(c(.01,.05,.2,.9),c(.01,NA,.05),c(0,1,0,1),c(.1,.1,.5,.00001),c(NA,NA),c(1e-100,1e-50,.2),runif(23))
for(i in seq_along(families))for(method in c("BH","BY","holm","bonferroni","none")) {
  at<-at+1L;adj[[at]]<-data.frame(case=i,method=method,input=fmt(families[[i]]),adjusted=fmt(stats::p.adjust(families[[i]],method=method)))
}
adj<-do.call(rbind,adj);write.table(adj,file.path(out,"adjust.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
sha<-function(p)digest::digest(file=p,algo="sha256",serialize=FALSE)
script<-sub("^--file=","",commandArgs()[grepl("^--file=",commandArgs())])
record<-list(status="passed-original-deterministic-arithmetic-reference-generation",r_version=R.version.string,fgsea="1.38.0",enrichit="0.2.6",gsea_cases=nrow(gsea),ora_cases=nrow(ora),adjustment_cases=nrow(adj),generator_sha256=sha(script),runtime_marker_sha256=sha(file.path(lib,".jlinalg-enrichment-runtime.json")),reference_sha256=setNames(lapply(c("gsea.tsv","ora.tsv","adjust.tsv"),function(n)sha(file.path(out,n))),c("gsea.tsv","ora.tsv","adjust.tsv")),warnings=unique(warnings),scope="Actual original fgsea::prepareStats/calcGseaStat,enrichit::gseaScores,stats::phyper and stats::p.adjust. Deterministic arithmetic only;no permutation P-values,NES,annotation or whole-paper equivalence inferred.")
jsonlite::write_json(record,file.path(out,"reference-provenance.json"),auto_unbox=TRUE,pretty=TRUE)
cat("ORIGINAL REFERENCES:",nrow(gsea),"GSEA;",nrow(ora),"ORA;",nrow(adj),"P-adjustment\n")
