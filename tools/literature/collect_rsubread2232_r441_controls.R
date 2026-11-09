# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original-package controls only: no source/namespace changes and no adapter credit.
args <- commandArgs(trailingOnly=TRUE)
stopifnot(length(args)==3L,getRversion()=="4.4.1")
lib <- args[[1L]]; out <- args[[2L]]; input <- args[[3L]]
.libPaths(c(lib,.Library)); stopifnot(as.character(utils::packageVersion("Rsubread",lib.loc=lib))=="2.23.2")
fun <- get("featureCounts",envir=loadNamespace("Rsubread",lib.loc=lib),inherits=FALSE)
hex <- function(x) paste(sprintf("%02x",as.integer(x)),collapse="")
namewire <- function(x) if(is.na(x))"NA" else paste0("S",hex(charToRaw(enc2utf8(x))))
wire <- function(x,file) {
    stopifnot(is.matrix(x),typeof(x)%in%c("integer","double"),ncol(x)>0L,length(rownames(x))==nrow(x),length(colnames(x))==ncol(x))
    lines <- c(paste("RSUBREAD_MATRIX_V1",toupper(if(typeof(x)=="integer")"INTEGER" else "DOUBLE"),nrow(x),ncol(x),sep="\t"),if(nrow(x)>0L)paste0("R\t",vapply(rownames(x),namewire,""))else character(),paste0("C\t",vapply(colnames(x),namewire,"")),vapply(as.double(x),function(v)paste0("D\t",hex(writeBin(v,raw(),size=8L,endian="big"))),""))
    con <- file(file,"wb");on.exit(close(con));writeLines(lines,con,useBytes=TRUE)
}
calls <- list(
    list(id="rna-paper-options",files="single.sam",annotation="genes.gtf",isPairedEnd=FALSE,countReadPairs=FALSE,useMetaFeatures=TRUE,allowMultiOverlap=FALSE,countMultiMappingReads=FALSE,isLongRead=FALSE,ignoreDup=FALSE,strandSpecific=0L,juncCounts=TRUE,genome=NULL,requireBothEndsMapped=FALSE,checkFragLength=FALSE,countChimericFragments=TRUE,autosort=TRUE),
    list(id="single-meta",files="single.sam",annotation="genes.gtf",isPairedEnd=FALSE,countReadPairs=FALSE),
    list(id="paired-fragments",files="paired.sam",annotation="genes.gtf",isPairedEnd=TRUE,countReadPairs=TRUE),
    list(id="paired-reads",files="paired.sam",annotation="genes.gtf",isPairedEnd=TRUE,countReadPairs=FALSE),
    list(id="mixed-libraries",files=c("single.sam","paired.sam"),annotation="genes.gtf",isPairedEnd=c(FALSE,TRUE),countReadPairs=c(FALSE,TRUE),strandSpecific=c(0L,0L)),
    list(id="exon-features",files="single.sam",annotation="genes.gtf",isPairedEnd=FALSE,countReadPairs=FALSE,useMetaFeatures=FALSE,juncCounts=TRUE),
    list(id="fractional-overlap",files="single.sam",annotation="genes.gtf",isPairedEnd=FALSE,countReadPairs=FALSE,allowMultiOverlap=TRUE,countMultiMappingReads=TRUE,fraction=TRUE),
    list(id="strand-forward",files="single.sam",annotation="genes.gtf",isPairedEnd=FALSE,countReadPairs=FALSE,strandSpecific=1L),
    list(id="strand-reverse",files="single.sam",annotation="genes.gtf",isPairedEnd=FALSE,countReadPairs=FALSE,strandSpecific=2L),
    list(id="atac-100-inactive",files="paired.sam",annotation="peaks.saf",isPairedEnd=TRUE,countReadPairs=TRUE,countMultiMappingReads=FALSE,maxFragLength=100L,checkFragLength=FALSE,autosort=TRUE),
    list(id="atac-600-inactive",files="paired.sam",annotation="peaks.saf",isPairedEnd=TRUE,countReadPairs=TRUE,countMultiMappingReads=FALSE,maxFragLength=600L,checkFragLength=FALSE,autosort=TRUE),
    list(id="atac-100-active",files="paired.sam",annotation="peaks.saf",isPairedEnd=TRUE,countReadPairs=TRUE,countMultiMappingReads=FALSE,minFragLength=0L,maxFragLength=100L,checkFragLength=TRUE,requireBothEndsMapped=TRUE,autosort=TRUE),
    list(id="junction-genome",files="single.sam",annotation="genes.gtf",isPairedEnd=FALSE,countReadPairs=FALSE,juncCounts=TRUE,genome="genome.fasta"),
    list(id="junction-saf",files="single.sam",annotation="peaks.saf",isPairedEnd=FALSE,countReadPairs=FALSE,juncCounts=TRUE),
    list(id="gzip-annotation",files="single.sam",annotation="genes.gtf.gz",isPairedEnd=FALSE,countReadPairs=FALSE),
    list(id="duplicate-basenames",files=c("one/same.sam","two/same.sam"),annotation="genes.gtf",isPairedEnd=c(FALSE,FALSE),countReadPairs=c(FALSE,FALSE))
)
results <- list();manifest <- list()
for (spec in calls) {
    id <- spec$id;home <- file.path(out,id);dir.create(home);previous <- setwd(home)
    arguments <- spec;arguments$id <- NULL;annotation <- arguments$annotation;arguments$annotation <- NULL
    arguments$files <- file.path(input,arguments$files);arguments$annot.ext <- file.path(input,annotation);arguments$isGTFAnnotationFile <- grepl("\\.gtf(?:\\.gz)?$",annotation);arguments$GTF.featureType <- "exon";arguments$GTF.attrType <- "gene_id";arguments$nthreads <- 1L;arguments$tmpDir <- home
    if(!is.null(arguments$genome))arguments$genome <- file.path(input,arguments$genome)
    saveRDS(arguments,"arguments.rds",version=3L);dput(arguments,file="arguments.R")
    result <- withCallingHandlers(do.call(fun,arguments),warning=function(w)cat("ORIGINAL_WARNING:",conditionMessage(w),"\n",file=stderr()))
    stopifnot(is.list(result),all(c("counts","annotation","targets","stat")%in%names(result)),is.matrix(result$counts),ncol(result$counts)==length(arguments$files),identical(rownames(result$counts),as.character(result$annotation$GeneID)),identical(colnames(result$counts),result$targets))
    saveRDS(result,"complete-original-result.rds",version=3L);dput(result,file="complete-original-result.R");wire(result$counts,"counts.matrix")
    for(name in c("counts","annotation","stat","counts_junction"))if(!is.null(result[[name]]))write.table(result[[name]],paste0(name,".tsv"),sep="\t",quote=TRUE,row.names=TRUE,col.names=NA,na="NA")
    manifest[[length(manifest)+1L]] <- data.frame(id=id,rows=nrow(result$counts),columns=ncol(result$counts),storage=typeof(result$counts),junctions=if(is.null(result$counts_junction))-1L else nrow(result$counts_junction),stringsAsFactors=FALSE)
    results[[id]] <- result;setwd(previous)
}
stopifnot(identical(results[["atac-100-inactive"]]$counts,results[["atac-600-inactive"]]$counts),!identical(results[["atac-100-inactive"]]$stat,results[["atac-100-active"]]$stat),identical(cbind(results[["single-meta"]]$counts,results[["paired-fragments"]]$counts),results[["mixed-libraries"]]$counts),anyDuplicated(rownames(results[["exon-features"]]$counts))>0L,any(is.na(rownames(results[["single-meta"]]$counts))),any(results[["fractional-overlap"]]$counts%%1!=0))
bindhome <- file.path(out,"matrix-bind");dir.create(bindhome)
literal <- c("dup","dup",NA_character_,"","NA","beta-\u03b2","tab\tnewline\n\U0001f9ec")
a <- matrix(seq_len(14L),nrow=7L,dimnames=list(literal,c("library","library")))
b <- matrix(c(0,0.5,1/3,2^53+2,1e20,-0.0,1,2,3,4,5,6,7,8),nrow=7L,dimnames=list(literal,c("",NA_character_)))
reverse <- b;rownames(reverse)<-rev(literal)
zero <- matrix(integer(),nrow=0L,ncol=2L,dimnames=list(character(),c("zero","zero")))
empty.double <- matrix(double(),nrow=0L,ncol=1L,dimnames=list(character(),"double"))
bindcases <- list(list(id="real-single-paired",inputs=list(results[["single-meta"]]$counts,results[["paired-fragments"]]$counts)),list(id="real-duplicate-features",inputs=list(results[["exon-features"]]$counts,results[["exon-features"]]$counts)),list(id="real-fractions",inputs=list(results[["single-meta"]]$counts,results[["fractional-overlap"]]$counts)),list(id="synthetic-literal-names",inputs=list(a,a)),list(id="synthetic-storage-promotion",inputs=list(a,b)),list(id="synthetic-different-row-names",inputs=list(a,reverse)),list(id="synthetic-many",inputs=list(a,b,a)),list(id="synthetic-zero-rows",inputs=list(zero,empty.double)),list(id="synthetic-single",inputs=list(b)),list(id="source-row-count-error",inputs=list(a,matrix(1L,nrow=3L,ncol=1L,dimnames=list(c("x","y","z"),"bad")))))
bindmanifest <- list()
for(spec in bindcases){home<-file.path(bindhome,spec$id);dir.create(home);for(i in seq_along(spec$inputs))wire(spec$inputs[[i]],file.path(home,paste0("input",i,".matrix")));result<-tryCatch(do.call(base::cbind,spec$inputs),error=function(e)e);mismatch<-which(!vapply(spec$inputs,function(x)identical(rownames(x),rownames(spec$inputs[[1L]])),TRUE))-1L
    if(inherits(result,"error")){writeLines(conditionMessage(result),file.path(home,"original-error.txt"));status<-"original-error"}else{wire(result,file.path(home,"expected.matrix"));saveRDS(result,file.path(home,"expected.rds"),version=3L);status<-"passed-original-cbind"}
    bindmanifest[[length(bindmanifest)+1L]]<-data.frame(id=spec$id,inputs=length(spec$inputs),status=status,differing_inputs=paste(mismatch,collapse=","),stringsAsFactors=FALSE)
}
write.table(do.call(rbind,manifest),file.path(out,"original-cases.tsv"),sep="\t",row.names=FALSE,quote=TRUE)
write.table(do.call(rbind,bindmanifest),file.path(out,"matrix-bind-cases.tsv"),sep="\t",row.names=FALSE,quote=TRUE)
negativehome<-file.path(out,"invalid-fragment-options");dir.create(negativehome);previous<-setwd(negativehome)
invalid<-tryCatch(fun(files=file.path(input,"paired.sam"),annot.ext=file.path(input,"peaks.saf"),isGTFAnnotationFile=FALSE,isPairedEnd=TRUE,countReadPairs=TRUE,checkFragLength=TRUE,requireBothEndsMapped=FALSE,maxFragLength=100L,nthreads=1L,tmpDir=negativehome),error=function(e)e)
stopifnot(inherits(invalid,"error"));writeLines(c("EXPECTED_ORIGINAL_ERROR",class(invalid),conditionMessage(invalid)),"original-error.txt");setwd(previous)
writeLines(capture.output(sessionInfo()),file.path(out,"sessionInfo.txt"))
writeLines("PASS16_ORIGINAL_RSUBREAD2232_R441_CONTROLS_AND10_ORIGINAL_CBIND_CASES_ONLY",file.path(out,"R-status.txt"))
