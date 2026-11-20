# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original source/function controls only; no package/source patches or Java calls.
args <- commandArgs(TRUE)
stopifnot(length(args)==3)
source_root <- normalizePath(args[1],winslash="/",mustWork=TRUE)
out <- args[2]
stopifnot(!file.exists(out));dir.create(out,recursive=TRUE)
libs <- readLines(args[3]);.libPaths(libs)
stopifnot(as.character(getRversion())=="4.6.1",as.character(packageVersion("edgeR"))=="4.10.7")
stopifnot(requireNamespace("jsonlite",quietly=TRUE),requireNamespace("edgeR",quietly=TRUE))
writeLines(capture.output(sessionInfo()),file.path(out,"SESSION.txt"))
versions <- c("3.32.1","3.36.0","3.40.2","3.42.4","4.10.7")
sources <- lapply(versions,function(v){e<-new.env(parent=asNamespace("stats"));sys.source(file.path(source_root,v,"source/edgeR/R/calcNormFactors.R"),envir=e);e})
names(sources)<-versions
functions <- c("calcNormFactors.default",".calcFactorTMM",".calcFactorTMMwsp",".calcFactorQuantile",".calcFactorRLE")
for(n in functions)stopifnot(identical(formals(get(n,sources[["4.10.7"]])),formals(get(n,asNamespace("edgeR")))),
    identical(body(get(n,sources[["4.10.7"]])),body(get(n,asNamespace("edgeR")))))
capture <- function(f){warnings<-messages<-character();error<-NULL;value<-tryCatch(withCallingHandlers(f(),
    warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")},
    message=function(m){messages<<-c(messages,conditionMessage(m));invokeRestart("muffleMessage")}),error=function(e){error<<-conditionMessage(e);NULL});
    list(value=value,warnings=warnings,messages=messages,error=error)}
number <- function(x)vapply(x,function(v)if(is.nan(v))"NaN" else if(is.na(v))"NA" else if(is.infinite(v))if(v>0)"Inf" else "-Inf" else sprintf("%.17g",v),"")
rows <- function(x)lapply(seq_len(nrow(x)),function(i)unname(x[i,]))
rng.before <- .Random.seed <- {set.seed(650217);.Random.seed}
datasets<-list()
put <- function(name,x,libs=NULL){stopifnot(is.matrix(x),is.numeric(x));datasets[[name]]<<-list(matrix=x,libraries=libs)}
put("ties",rbind(c(5,5,9,9),c(5,9,9,5),c(9,9,5,5),c(9,5,5,9),c(5,5,5,5),c(0,0,0,0)))
put("singletons",rbind(c(0,0,8),c(2,0,4),c(6,0,0),c(0,3,0),c(0,9,0),c(1,1,1)))
put("no-shared",diag(c(5,8,13,21)))
put("all-zero",matrix(0,7,3));put("all-zero-sizes",matrix(0,7,3),c(13,17,21))
put("empty-features",matrix(numeric(),0,3),c(13,17,21));put("one-sample",matrix(c(0,2,5,10),ncol=1))
put("fractional",matrix(c(.5,1.5,3.2,0,1e-15,2,9,5.2,6.3,1e-14,7,8,0,5,1e-13),ncol=3))
put("scaled",outer(c(0,1,4,4,9,22,100),c(1,2,3,11)))
put("explicit-libraries",matrix(c(2,3,8,15,0,4,2,20,12,1,3,90),ncol=3),c(300,250,900))
put("singleton-fallback",rbind(diag(rep(100,8)),matrix(0,60,8)))
for(i in 1:36){nr<-sample(3:100,1);nc<-sample(2:10,1);x<-matrix(rpois(nr*nc,sample(c(1,3,10,100),1)),nr,nc)
    if(i%%3==0)x[matrix(runif(nr*nc)<.8,nr,nc)]<-0
    if(i%%3==1)x<-round(x/2)
    if(i%%5==0)x[1,]<-x[1,]*1e5
    x[1,]<-x[1,]+1;put(sprintf("random-%02d",i),x,if(i%%4==0)colSums(x)*runif(nc,1,3) else NULL)}
settings<-list(
    list(method="TMM",refColumn=NULL,logratioTrim=.3,sumTrim=.05,doWeighting=TRUE,Acutoff=-1e10,p=.75),
    list(method="TMM",refColumn=1L,logratioTrim=.1,sumTrim=.1,doWeighting=FALSE,Acutoff=-1e10,p=.75),
    list(method="TMM",refColumn=NULL,logratioTrim=0,sumTrim=0,doWeighting=TRUE,Acutoff=-5,p=.75),
    list(method="TMMwsp",refColumn=NULL,logratioTrim=.3,sumTrim=.05,doWeighting=TRUE,Acutoff=-1e10,p=.75),
    list(method="TMMwsp",refColumn=1L,logratioTrim=.2,sumTrim=.15,doWeighting=FALSE,Acutoff=-1e10,p=.75),
    list(method="TMMwsp",refColumn=NULL,logratioTrim=0,sumTrim=0,doWeighting=TRUE,Acutoff=0,p=.75),
    list(method="RLE",refColumn=NULL,logratioTrim=.3,sumTrim=.05,doWeighting=TRUE,Acutoff=-1e10,p=.75),
    list(method="upperquartile",refColumn=NULL,logratioTrim=.3,sumTrim=.05,doWeighting=TRUE,Acutoff=-1e10,p=.75),
    list(method="upperquartile",refColumn=NULL,logratioTrim=.3,sumTrim=.05,doWeighting=TRUE,Acutoff=-1e10,p=0),
    list(method="none",refColumn=NULL,logratioTrim=.3,sumTrim=.05,doWeighting=TRUE,Acutoff=-1e10,p=.75))
cases<-originals<-list();k<-0L
for(v in versions)for(id in names(datasets))for(s in seq_along(settings)){
    d<-datasets[[id]];o<-settings[[s]]
    f<-if(v=="4.10.7")edgeR::calcNormFactors else get("calcNormFactors.default",sources[[v]])
    r<-capture(function()do.call(f,c(list(object=d$matrix,lib.size=d$libraries),o)))
    stopifnot(is.null(r$error))
    k<-k+1L;originals[[k]]<-r
    cases[[k]]<-list(name=paste(v,id,s,sep="/"),edition=paste0("EDGER_",gsub("\\.","_",v)),dataset=id,
        options=c(o,list(reference_zero_based=if(is.null(o$refColumn))NULL else o$refColumn-1L)),
        factors=unname(number(r$value)),warnings=as.list(r$warnings),messages=as.list(r$messages))
}
transforms<-list();t<-0L
for(id in names(datasets)){
    d<-datasets[[id]];ls<-if(is.null(d$libraries))colSums(d$matrix) else d$libraries
    if(any(ls<=0))next
    for(log in c(FALSE,TRUE))for(prior in c(0,2,.5)){
        gene.length<-seq_len(nrow(d$matrix))*100+500
        for(op in c("CPM","RPKM")){
            r<-capture(function()if(op=="CPM")edgeR::cpm(d$matrix,lib.size=ls,log=log,prior.count=prior) else edgeR::rpkm(d$matrix,gene.length=gene.length,lib.size=ls,log=log,prior.count=prior))
            stopifnot(is.null(r$error));t<-t+1L
            transforms[[t]]<-list(name=paste(id,op,log,prior,sep="/"),dataset=id,operation=op,log=log,prior=prior,libraries=unname(ls),gene_lengths=gene.length,
                values=lapply(seq_len(nrow(r$value)),function(i)unname(number(r$value[i,]))),warnings=as.list(r$warnings),messages=as.list(r$messages))
            transforms[[t]]$libraries<-unname(number(ls))
        }
    }
}
encoded.datasets<-lapply(datasets,function(d)list(counts=lapply(seq_len(nrow(d$matrix)),function(i)unname(number(d$matrix[i,]))),
    samples=ncol(d$matrix),libraries=if(is.null(d$libraries))NULL else unname(number(d$libraries))))
value<-list(schema_version=1,status="ORIGINAL_EDGER_NORMALIZATION_REFERENCE_ONLY",versions=as.list(versions),
    datasets=encoded.datasets,normalizations=cases,transforms=transforms,whole_tool_qualified=FALSE,model_inference_run=FALSE)
jsonlite::write_json(value,file.path(out,"reference.json"),auto_unbox=TRUE,null="null",digits=NA,pretty=FALSE)
saveRDS(list(datasets=datasets,normalization_returns=originals,reference=value,RNG_before=rng.before,RNG_after=.Random.seed,namespace_sources_verified=functions),file.path(out,"original-returns.rds"),version=3)
jsonlite::write_json(list(status="PASS_ORIGINAL_EDGER_SOURCE_FUNCTIONS_AND_CURRENT_PACKAGE_CONTROLS",versions=as.list(versions),datasets=length(datasets),
    normalizations=length(cases),transforms=length(transforms),current_namespace_functions_equal_original_source=as.list(functions),
    historical_function_source_overlay=FALSE,historical_package_deployment=FALSE,inference_run=FALSE),file.path(out,"COMPLETED.json"),auto_unbox=TRUE,pretty=TRUE)
cat("PASS original normalization references:",length(cases),"normalizations and",length(transforms),"transforms\n")
