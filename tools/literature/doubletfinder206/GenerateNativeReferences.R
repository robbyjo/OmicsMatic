# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Execute installed, source-audited original functions; retain complete controls.
args <- commandArgs(TRUE)
stopifnot(length(args)==3L)
lib <- args[[1L]]; root <- args[[2L]]; out <- args[[3L]]
if (file.exists(out)) stop("Use a NEW original reference output")
dir.create(out,recursive=FALSE)
.libPaths(c(lib,.libPaths()))
options(warn=1)
library(DoubletFinder)
loadNamespace("Seurat")
source(file.path(root,"tools/literature/velociraptor120/RValues.R"))
set.seed(2061901L)
cases <- list()
text <- function(x) if (is.na(x)) "!NA" else paste(sprintf("%02x",as.integer(charToRaw(enc2utf8(x)))),collapse="")
number <- function(x) if(is.nan(x))"NaN" else if(is.na(x))"NA" else sprintf("%.17g",x)
write.scalar <- function(x,path) { con <- file(path,"wb");on.exit(close(con));writeBin(as.double(x),con,size=8L,endian="little") }
record <- function(kind,name,inputs,call,arguments=list()) {
 path <- file.path(out,name);dir.create(path)
 saveRDS(inputs,file.path(path,"native-inputs.rds"),version=3)
 saveRDS(arguments,file.path(path,"original-arguments.rds"),version=3)
 conditions <- list();value <- NULL;error <- NULL
 pdf(file.path(path,"original-plot.pdf"),width=7,height=7)
 tryCatch(withCallingHandlers({value <- call()},warning=function(c)conditions[[length(conditions)+1L]]<<-c,message=function(c)conditions[[length(conditions)+1L]]<<-c),error=function(c){error<<-c;conditions[[length(conditions)+1L]]<<-c})
 dev.off()
 saveRDS(list(value=value,error=error,conditions=conditions),file.path(path,"complete-original-return-and-conditions.rds"),version=3)
 captureRValues(list(inputs=inputs,result=value),file.path(path,"complete-native-scope-R-values"))
 status <- if(is.null(error))"PASS"else"ERROR"
 cases[[length(cases)+1L]] <<- data.frame(kind=kind,name=name,auc=as.integer(!is.null(inputs$auc)),n=length(if(kind=="homotypic")inputs$annotations else if(kind=="pk")inputs$pK else inputs$scores),expected=if(kind=="classify")inputs$nExp else -1L,status=status)
 list(path=path,value=value,error=error)
}
homotypic <- list(empty=character(),one="cell",all_missing=rep(NA_character_,4),literal_na=c("NA",NA_character_,"NA","a"),literal_labels=c("", " ","NA","\u03b1","\u03b1",NA_character_),unequal=c(rep("x",7),rep("y",3)),only_one_observed=c("x",rep(NA_character_,9)))
for(i in seq_len(100L))homotypic[[paste0("random_",i)]] <- sample(c(letters[1:11],"NA","",NA_character_,"\u03b2"),sample(0:400,1),replace=TRUE)
for(name in names(homotypic)) {
 values <- homotypic[[name]];x <- record("homotypic",paste0("homotypic_",name),list(annotations=values),function()DoubletFinder::modelHomotypic(values))
 writeLines(vapply(values,text,character(1)),file.path(x$path,"annotations.tsv"),useBytes=TRUE)
 stopifnot(is.null(x$error));write.scalar(x$value,file.path(x$path,"value.bin"))
}
pk <- list(
 simple=list(pK=c("0.2","0.01","0.2","0.01"),BCreal=c(.5,.2,.7,.6)),
 constant_and_finite=list(pK=c("z","x","z","x"),BCreal=c(.5,.3,.5,.7)),
 singleton_and_finite=list(pK=c("one","two","two"),BCreal=c(.4,.2,.8)),
 missing_label=list(pK=c(NA_character_,"NA","a","a","NA"),BCreal=c(.5,.2,.1,.3,.6)),
 missing_BC=list(pK=c("x","x","y","y"),BCreal=c(NA_real_,.2,.3,.8)),
 infinite_BC=list(pK=c("x","x","y","y"),BCreal=c(Inf,.2,.3,.8)),
 zero_variance=list(pK=rep("x",3),BCreal=rep(.4,3)),
 one_row=list(pK="x",BCreal=.3),
 all_missing_labels=list(pK=rep(NA_character_,3),BCreal=c(.1,.2,.5)),
 all_missing_values=list(pK=rep("x",3),BCreal=rep(NA_real_,3)),
 empty=list(pK=character(),BCreal=numeric()),
 high_offset=list(pK=c("x","x","x","y","y"),BCreal=c(1e10,1e10+.01,1e10+.02,.1,.3)),
 tiny=list(pK=c("x","x","y","y"),BCreal=c(1e-150,2e-150,3e-150,7e-150)))
for(i in seq_len(40L)) {
 labels <- sample(c("0.2","0.005","0.03","\u03b2"),sample(12:40,1),replace=TRUE)
 pk[[paste0("random_",i)]] <- list(pK=labels,BCreal=runif(length(labels)))
}
for(name in names(pk))for(with.auc in c(FALSE,TRUE)) {
 inputs <- pk[[name]]
 if(with.auc)inputs$auc <- seq(.6,.9,length.out=length(inputs$pK))
 sweep <- data.frame(pK=inputs$pK,BCreal=inputs$BCreal,stringsAsFactors=FALSE)
 if(with.auc)sweep$AUC <- inputs$auc
 x <- record("pk",paste0("pk_",name,if(with.auc)"_auc"else"_noauc"),inputs,function()DoubletFinder::find.pK(sweep),list(sweep.stats=sweep))
 writeLines(vapply(seq_along(inputs$pK),function(i)paste(text(inputs$pK[[i]]),number(inputs$BCreal[[i]]),if(with.auc)number(inputs$auc[[i]])else"absent",sep="\t"),character(1)),file.path(x$path,"sweep.tsv"),useBytes=TRUE)
 if(is.null(x$error)) {
  writeLines(paste(names(x$value),collapse="\t"),file.path(x$path,"table.tsv"))
  lines <- vapply(seq_len(nrow(x$value)),function(i){row<-x$value[i,,drop=FALSE];paste(c(number(row$ParamID),text(as.character(row$pK)),if(with.auc)number(row$MeanAUC),number(row$MeanBC),number(row$VarBC),number(row$BCmetric)),collapse="\t")},character(1))
  cat(paste0(lines,collapse="\n"),"\n",file=file.path(x$path,"table.tsv"),append=TRUE,sep="")
 }
}
scores <- list(ties=c(.4,.8,.8,.4,0,-0),nonfinite=c(NA_real_,NaN,Inf,-Inf,0,-0),all_missing=rep(NA_real_,4),zero_count=c(.3,.9,.6))
for(i in seq_len(40L))scores[[paste0("random_",i)]] <- sample(c(seq(0,1,by=.1),NA_real_,NaN,Inf,-Inf),sample(4:24,1),replace=TRUE)
for(name in names(scores))for(count in unique(c(0L,1L,length(scores[[name]])%/%2L,length(scores[[name]])))) {
 values <- scores[[name]];cells <- paste0("cell",seq_along(values));cells[[1L]]<-"NA"
 seu <- SeuratObject::CreateSeuratObject(matrix(1,nrow=3,ncol=length(values),dimnames=list(c("g1","g2","g3"),cells)))
 seu$original.pANN <- values
 original.args <- list(seu=seu,PCs=1:2,pN=.25,pK=.1,nExp=as.integer(count),reuse.pANN="original.pANN")
 path <- file.path(out,paste0("classify_",name,"_",count))
 original <- do.call(DoubletFinder::doubletFinder,original.args)
 column <- paste("DF.classifications",.25,.1,count,sep="_")
 expected <- seu;expected[[column]] <- original[[column]][,1L]
 stopifnot(identical(expected,original,num.eq=FALSE,single.NA=FALSE,attrib.as.set=FALSE,ignore.environment=FALSE))
 x <- record("classify",basename(path),list(scores=values,nExp=as.integer(count)),function()original[[column]][,1L],original.args)
 saveRDS(original,file.path(path,"complete-original-Seurat-return.rds"),version=3)
 writeLines(vapply(values,number,character(1)),file.path(path,"scores.tsv"))
 writeLines(x$value,file.path(path,"classifications.tsv"))
}
index <- do.call(rbind,cases)
write.table(index,file.path(out,"cases.tsv"),sep="\t",quote=FALSE,row.names=FALSE,na="NA")
saveRDS(list(cases=index,session=sessionInfo(),RNGkind=RNGkind(),final_random_seed=.Random.seed),file.path(out,"complete-generator-record.rds"),version=3)
jsonlite::write_json(list(status="COMPLETE_ORIGINAL_NATIVE_COMPONENT_REFERENCES_NOT_WHOLE_PACKAGE",cases=nrow(index),successful=sum(index$status=="PASS"),original_errors=sum(index$status=="ERROR"),version=as.character(packageVersion("DoubletFinder")),scope=c("modelHomotypic","find.pK tables","existing pANN classifications"),floating_comparison_tolerance="1e-12 * max(abs(original), 1e-300); absolute 1e-14 for homotypic",whole_tool_qualified=FALSE,new_family_credit=0L),file.path(out,"COMPLETED.json"),pretty=TRUE,auto_unbox=TRUE)
cat("COMPLETE_ORIGINAL_NATIVE_REFERENCES",nrow(index),"\n")
