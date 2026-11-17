# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent original-spdep oracle; no Java implementation is loaded.
a <- commandArgs(TRUE)
stopifnot(length(a)>=3L,!file.exists(a[1]))
dir.create(a[1],recursive=TRUE);out<-normalizePath(a[1],winslash="/")
.libPaths(c(a[-(1:2)],.Library),include.site=FALSE)
if(!requireNamespace("spdep",quietly=TRUE)){message("SKIP3: required spdep reference package is unavailable");quit(status=3L)}
if(as.character(packageVersion("spdep"))!="1.4.1"){message("SKIP3: this reference requires the separately declared spdep1.4-1 profile");quit(status=3L)}
suppressPackageStartupMessages(library(spdep))
original<-new.env(parent=asNamespace("spdep"))
sys.source(a[2],envir=original,keep.source=FALSE)
stopifnot(identical(formals(original$lm.morantest),formals(spdep::lm.morantest)),
          identical(body(original$lm.morantest),body(spdep::lm.morantest)))
options(digits=17,OutDec=".");set.seed(20261117)
warnings<-list();results<-list()
capture<-function(label,expr)withCallingHandlers(expr,warning=function(w){
    warnings[[length(warnings)+1L]]<<-list(case=label,message=conditionMessage(w));invokeRestart("muffleWarning")})
ring<-function(n,shift=1L,weight=1){data.frame(from=seq_len(n),to=((seq_len(n)-1L+shift)%%n)+1L,weight=rep(weight,length.out=n))}
both<-function(n){rbind(ring(n),ring(n,-1L))}
configuration<-function(id,n,arcs,X,zero=FALSE,large=FALSE)list(id=id,n=n,arcs=arcs,X=X,zero=zero,large=large)
specifications<-list()
n<-36L;specifications[[1]]<-configuration("ring-intercept",n,both(n),matrix(1,n,1))
n<-40L;i<-seq_len(n);arcs<-rbind(ring(n,1L,1+i%%3),ring(n,3L,.3+i%%5),ring(n,7L,.7+i%%2))
arcs$weight<-arcs$weight/as.numeric(tapply(arcs$weight,arcs$from,sum))[arcs$from]
specifications[[2]]<-configuration("directed-row-standardized",n,arcs,cbind(1,i/n,sin(i*.4),i%%2))
n<-33L;i<-seq_len(n);specifications[[3]]<-configuration("asymmetric-reciprocal",n,rbind(ring(n,1L,1+i%%3),ring(n,-1L,.2+i%%4)),cbind(1,i/n,cos(i*.3)))
n<-40L;i<-seq_len(n);arcs<-both(20L);arcs2<-arcs;arcs2$from<-arcs2$from+20L;arcs2$to<-arcs2$to+20L
specifications[[4]]<-configuration("disconnected-partitions",n,rbind(arcs,arcs2),cbind(1,i>20L,sin(i*.4)))
arcs<-both(n);arcs<-arcs[!(arcs$from%in%c(7L,20L,33L)),]
X<-cbind(1,i/n,sin(i*.4),i%%2)
specifications[[5]]<-configuration("outgoing-isolates-adjust-N",n,arcs,X,TRUE)
specifications[[6]]<-configuration("incoming-neighbors-full-N",n,arcs,X,FALSE)
arcs<-both(n);arcs<-arcs[arcs$from<=38L&arcs$to<=38L,]
specifications[[7]]<-configuration("true-isolates-adjust-N",n,arcs,X,TRUE)
n<-32L;i<-seq_len(n);specifications[[8]]<-configuration("no-intercept",n,both(n),cbind(1+i/n,sin(i*.21)))
n<-50L;i<-seq_len(n);specifications[[9]]<-configuration("scaled-reordered-design",n,both(n),cbind(1e4*i/n,.001*cos(i*.31),1,(i%%3==0)*1.0))
n<-60L;i<-seq_len(n);d<-data.frame(Batch=factor(rep(c("A","B","C"),length.out=n)),Sex=factor(rep(c("F","F","M","M"),length.out=n)),PMI=3+(i%%17),log_n_genes=log(100+3*i))
specifications[[10]]<-configuration("explicit-factor-design",n,both(n),model.matrix(~Batch+Sex+PMI+log_n_genes,d))
n<-38L;i<-seq_len(n);specifications[[11]]<-configuration("general-unnormalized-weights",n,rbind(ring(n,1L,.1+(i%%11)/3),ring(n,4L,.2+(i%%7)/5)),cbind(1,i/n,cos(i*.23)))
n<-10000L;specifications[[12]]<-configuration("large-sparse-finite-log-tails",n,both(n),matrix(1,n,1),large=TRUE)
for(spec in specifications){
    n<-spec$n;X<-unname(spec$X);i<-seq_len(n);arcs<-spec$arcs
    # Construct the documented sparse nb/listw objects; style B retains every
    # explicitly supplied glist weight, including previously row-standardized ones.
    nb<-vector("list",n);glist<-vector("list",n)
    indices<-split(seq_len(nrow(arcs)),factor(arcs$from,levels=seq_len(n)))
    for(j in seq_len(n)){
        rows<-indices[[j]];rows<-rows[order(arcs$to[rows])]
        nb[[j]]<-if(length(rows))as.integer(arcs$to[rows]) else 0L
        glist[[j]]<-as.double(arcs$weight[rows])
    }
    class(nb)<-"nb";attr(nb,"region.id")<-paste0("C",i);attr(nb,"sym")<-FALSE
    lw<-capture(spec$id,spdep::nb2listw(nb,glist=glist,style="B",zero.policy=TRUE))
    stopifnot(identical(unname(unlist(lw$weights)),unname(unlist(glist))))
    U<-spdep::listw2U(lw);s0<-sum(unlist(U$weights));active<-sum(spdep::card(nb)>0L)
    if(spec$large){ys<-list(sin(2*pi*i/n),(-1)^i,rnorm(n))}else{
        fitted<-drop(X%*%seq(.1,.1*ncol(X),by=.1))
        ys<-list(fitted+sin(i*.25)+rnorm(n,sd=.1),fitted+(-1)^i+rnorm(n,sd=.15),fitted+rnorm(n))
    }
    for(alternative in c("greater","less","two.sided")){
        genes<-list();pvalues<-numeric(length(ys))
        for(g in seq_along(ys)){
            response<-ys[[g]];fit<-stats::lm(response~0+X,na.action=stats::na.fail,tol=1e-7)
            stopifnot(fit$rank==ncol(X),!anyNA(coef(fit)))
            answer<-capture(spec$id,spdep::lm.morantest(fit,lw,zero.policy=spec$zero,alternative=alternative))
            numbers<-c(answer$estimate,answer$statistic,answer$p.value);stopifnot(all(is.finite(numbers)),answer$estimate[3]>0)
            z<-unname(answer$statistic)
            logp<-if(alternative=="two.sided")log(2)+pnorm(abs(z),lower.tail=FALSE,log.p=TRUE)else pnorm(z,lower.tail=alternative=="less",log.p=TRUE)
            pvalues[g]<-answer$p.value
            genes[[g]]<-list(id=paste0("gene",g),response=response,expected=list(moran_I=unname(answer$estimate[1]),expectation=unname(answer$estimate[2]),variance=unname(answer$estimate[3]),statistic=z,p_value=answer$p.value,log_p_value=logp))
        }
        q<-stats::p.adjust(pvalues,method="BH");for(g in seq_along(genes))genes[[g]]$expected$q_value<-q[g]
        results[[length(results)+1L]]<-list(id=paste(spec$id,alternative,sep="/"),design=X,weights=transform(arcs,from=from-1L,to=to-1L),zero_policy=spec$zero,alternative=alternative,rank=ncol(X),observations=n,effective_n=if(spec$zero)active else n,outgoing_isolates=n-active,weight_sum=s0,genes=genes)
    }
}
jsonlite::write_json(list(status="PASS_ORIGINAL_SPDEP_LM_MORANTEST_REFERENCE",spdep_version=as.character(packageVersion("spdep")),R_version=as.character(getRversion()),official_source_body_identical=TRUE,cases=results),file.path(out,"reference.json"),auto_unbox=TRUE,digits=17,pretty=FALSE,na="string",matrix="rowmajor",dataframe="rows")
jsonlite::write_json(warnings,file.path(out,"WARNINGS.json"),auto_unbox=TRUE,pretty=TRUE)
writeLines(deparse(spdep::lm.morantest,width.cutoff=500),file.path(out,"lm.morantest.R"))
capture.output(sessionInfo(),file=file.path(out,"SESSION.txt"))
writeLines("PASS_ORIGINAL_SPDEP_LM_MORANTEST_REFERENCE",file.path(out,"COMPLETED.txt"))
cat("PASS 36 sparse design/graph/alternative cases; 108 original gene tests. Original warnings:",length(warnings),"\n")
