# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent direct original calls, sharing exact saved inputs with adapter requests.
args <- commandArgs(TRUE);stopifnot(length(args)==3L)
lib<-args[[1L]];input<-args[[2L]];out<-args[[3L]]
if(file.exists(out))stop("Use a NEW original reference output")
dir.create(out);.libPaths(c(lib,.libPaths()));options(warn=1)
library(Seurat);library(DoubletFinder)
seu <- readRDS(input);cases<-list()
run <- function(name,operation,arguments,seed,expected.error=FALSE) {
 path<-file.path(out,name);dir.create(path)
 set.seed(as.integer(seed));request<-list(operation=operation,arguments=arguments,rng=list(kind=RNGkind(),seed=.Random.seed))
 saveRDS(request,file.path(path,"request.rds"),version=3)
 conditions<-list();error<-NULL;value<-NULL
 png(file.path(path,"original-plot-%03d.png"),width=800L,height=800L,type="cairo");dev.control(displaylist="enable")
 do.call(RNGkind,as.list(request$rng$kind));assign(".Random.seed",request$rng$seed,envir=globalenv())
 tryCatch(withCallingHandlers({value<-do.call(getExportedValue("DoubletFinder",operation),arguments)},warning=function(c)conditions[[length(conditions)+1L]]<<-c,message=function(c)conditions[[length(conditions)+1L]]<<-c,error=function(c)saveRDS(list(error=c,calls=sys.calls()),file.path(path,"complete-original-failure-and-calls.rds"),version=3)),error=function(c){error<<-c;conditions[[length(conditions)+1L]]<<-c})
 if(is.null(error))saveRDS(value,file.path(path,"complete-original-return.rds"),version=3)
 saveRDS(list(before=request$rng,after=list(kind=RNGkind(),seed=.Random.seed)),file.path(path,"complete-rng-before-after.rds"),version=3)
 saveRDS(recordPlot(),file.path(path,"complete-original-recorded-plot.rds"),version=3);dev.off()
 saveRDS(list(error=error,conditions=conditions),file.path(path,"complete-original-conditions.rds"),version=3)
 status<-list(name=name,operation=operation,original_complete=is.null(error),expected_error=expected.error,error_class=if(is.null(error))NULL else class(error),error_message=if(is.null(error))NULL else conditionMessage(error))
 jsonlite::write_json(status,file.path(path,"ORIGINAL-STATUS.json"),pretty=TRUE,auto_unbox=TRUE,null="null")
 cases[[length(cases)+1L]]<<-status
 if(identical(is.null(error),expected.error))stop("Original control outcome differs from the declared expectation: ",name)
 value
}
base.args<-list(seu=seu,PCs=1:5,pN=.25,pK=.1,nExp=16L,reuse.pANN=NULL,sct=FALSE)
plain<-run("lognorm-default-pN","doubletFinder",base.args[names(base.args)!="pN"],206282L)
annotation.args<-base.args;annotation.args$annotations<-rep(c("typeA","typeB","NA","typeC"),length.out=ncol(seu))
annotated<-run("lognorm-annotations","doubletFinder",annotation.args,206282L)
reuse.args<-base.args;reuse.args$seu<-plain;reuse.args$reuse.pANN<-"pANN_0.25_0.1_16";reuse.args$nExp<-8L
reused<-run("reuse-pANN","doubletFinder",reuse.args,206283L)
invalid.args<-annotation.args;invalid.args$annotations<-factor(invalid.args$annotations)
run("factor-annotations-error","doubletFinder",invalid.args,206282L,TRUE)
old.example.args<-base.args;old.example.args$reuse.pANN<-FALSE
run("old-reuse-FALSE-error","doubletFinder",old.example.args,206282L,TRUE)
set.seed(206285L)
sct<-SCTransform(CreateSeuratObject(counts=LayerData(seu,assay="RNA",layer="counts")),variable.features.n=100L,verbose=FALSE)
sct<-RunPCA(sct,npcs=5L,verbose=FALSE)
sct.args<-base.args;sct.args$seu<-sct;sct.args$sct<-TRUE
transformed<-run("SCTransform","doubletFinder",sct.args,206286L)
sweep<-run("parameter-sweep","paramSweep",list(seu=seu,PCs=1:5,sct=FALSE,num.cores=1L),206287L)
stats<-run("summarize-no-GT","summarizeSweep",list(sweep.list=sweep,GT=FALSE,GT.calls=NULL),206288L)
gt.stats<-run("summarize-GT","summarizeSweep",list(sweep.list=sweep,GT=TRUE,GT.calls=rep(c("Doublet","Singlet"),length.out=ncol(seu))),206289L)
metrics<-run("pK-no-AUC","find.pK",list(sweep.stats=stats),206290L)
auc.metrics<-run("pK-AUC","find.pK",list(sweep.stats=gt.stats),206291L)
homotypic<-run("homotypic","modelHomotypic",list(annotations=c("typeA","typeA",NA_character_,"NA","typeB")),206292L)
saveRDS(list(cases=cases,session=sessionInfo()),file.path(out,"complete-generator-record.rds"),version=3)
jsonlite::write_json(list(status="COMPLETE_DIRECT_ORIGINAL_WORKFLOW_REFERENCES_NOT_ADAPTER_QUALIFICATION",cases=cases,successful=sum(vapply(cases,function(x)x$original_complete,logical(1))),matching_expected_errors=sum(vapply(cases,function(x)!x$original_complete,logical(1))),whole_tool_qualified=FALSE,new_family_credit=0L),file.path(out,"COMPLETED.json"),pretty=TRUE,auto_unbox=TRUE,null="null")
cat("COMPLETE_DIRECT_ORIGINAL_WORKFLOW_REFERENCES",length(cases),"\n")
