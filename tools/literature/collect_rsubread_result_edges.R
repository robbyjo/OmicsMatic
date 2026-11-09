# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Fresh original upstream counting controls. No namespace or scientific-source changes.
args <- commandArgs(trailingOnly=TRUE); stopifnot(length(args)==7L)
lib<-args[1L];out<-args[2L];input<-args[3L];version<-args[4L];rversion<-args[5L];reference.source<-args[6L]
source(args[7L],local=TRUE);stopifnot(as.character(getRversion())==rversion)
.libPaths(c(lib,.Library));stopifnot(as.character(utils::packageVersion("Rsubread",lib.loc=lib))==version)
namespace<-loadNamespace("Rsubread",lib.loc=lib);fun<-get("featureCounts",envir=namespace,inherits=FALSE)
reference<-new.env(parent=baseenv());sys.source(reference.source,envir=reference)
stopifnot(identical(body(fun),body(reference$featureCounts)),identical(formals(fun),formals(reference$featureCounts)))
calls<-list(
    list(id="union-strands",files="single.sam",annotation="strand-union.saf"),
    list(id="numeric-ids",files="single.sam",annotation="numeric.saf"),
    list(id="logical-ids",files="single.sam",annotation="logical.saf"),
    list(id="empty-junctions",files="no-junction.sam",annotation="genes.gtf",juncCounts=TRUE),
    list(id="extra-attributes",files="single.sam",annotation="genes.gtf",GTF.attrType.extra="transcript_id"),
    list(id="bam-single",files="single.bam",annotation="genes.gtf"),
    list(id="bam-paired-fragments",files="paired.bam",annotation="genes.gtf",isPairedEnd=TRUE,countReadPairs=TRUE),
    list(id="bam-paired-reads",files="paired.bam",annotation="genes.gtf",isPairedEnd=TRUE,countReadPairs=FALSE),
    list(id="bam-junctions",files="single.bam",annotation="genes.gtf",juncCounts=TRUE,genome="genome.fasta"))
results<-list();manifest<-list()
for(spec in calls) {
    id<-spec$id;home<-file.path(out,id);stopifnot(dir.create(home));previous<-setwd(home)
    arguments<-spec;arguments$id<-NULL;annotation<-arguments$annotation;arguments$annotation<-NULL
    arguments$files<-file.path(input,arguments$files);arguments$annot.ext<-file.path(input,annotation)
    arguments$isGTFAnnotationFile<-endsWith(annotation,".gtf");arguments$nthreads<-1L;arguments$tmpDir<-home
    if(is.null(arguments$isPairedEnd))arguments$isPairedEnd<-FALSE
    if(is.null(arguments$countReadPairs))arguments$countReadPairs<-FALSE
    if(!is.null(arguments$genome))arguments$genome<-file.path(input,arguments$genome)
    saveRDS(arguments,"arguments.rds",version=3L);dput(arguments,file="arguments.R")
    x<-withCallingHandlers(do.call(fun,arguments),warning=function(w)cat("ORIGINAL_WARNING:",conditionMessage(w),"\n",file=stderr()))
    saveRDS(x,"complete-original-result.rds",version=3L);dput(x,file="complete-original-result.R")
    rsubread_write_result(x,"values");results[[id]]<-x
    manifest[[length(manifest)+1L]]<-data.frame(id=id,rows=nrow(x$counts),columns=ncol(x$counts),storage=typeof(x$counts),gene_id_storage=typeof(x$annotation$GeneID),junction_rows=if(is.null(x$counts_junction))-1L else nrow(x$counts_junction))
    setwd(previous)
}
stopifnot(identical(results[["union-strands"]]$annotation$Length,175L),
          identical(typeof(results[["numeric-ids"]]$annotation$GeneID),"integer"),
          identical(typeof(results[["logical-ids"]]$annotation$GeneID),"logical"),
          nrow(results[["empty-junctions"]]$counts_junction)==0L,ncol(results[["extra-attributes"]]$annotation)==7L)
write.table(do.call(rbind,manifest),file.path(out,"cases.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
writeLines(capture.output(sessionInfo()),file.path(out,"sessionInfo.txt"))
writeLines("PASS9_FRESH_ORIGINAL_RSUBREAD_RESULT_EDGE_AND_BAM_CONTROLS",file.path(out,"R-status.txt"))
