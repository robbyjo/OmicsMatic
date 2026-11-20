# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Optional independent original-package check, not a runtime for the native tool.
p<-commandArgs(TRUE);stopifnot(length(p)==3,!file.exists(p[2]));dir.create(p[2],recursive=TRUE)
.libPaths(readLines(p[3]));stopifnot(as.character(packageVersion("edgeR"))=="3.42.4",as.character(getRversion())=="4.6.1")
d<-read.delim(file.path(p[1],"counts.tsv"),check.names=FALSE,row.names=1)
l<-read.delim(file.path(p[1],"libraries.tsv"),check.names=FALSE)
g<-read.delim(file.path(p[1],"gene-lengths.tsv"),check.names=FALSE)
stopifnot(identical(colnames(d),l$sample_id),identical(rownames(d),g$feature_id))
objects<-list()
for(method in c("TMM","TMMwsp")){
    y<-edgeR::DGEList(as.matrix(d),lib.size=l$library_size)
    normalized<-edgeR::calcNormFactors(y,method=method)
    result<-if(method=="TMM")edgeR::cpm(normalized,normalized.lib.sizes=TRUE,log=FALSE) else edgeR::rpkm(normalized,gene.length=g$length_bp,normalized.lib.sizes=TRUE,log=TRUE,prior.count=2)
    objects[[method]]<-list(input=y,normalized=normalized,result=result)
    rows<-lapply(seq_len(nrow(result)),function(i)unname(sprintf("%.17g",result[i,])))
    jsonlite::write_json(list(samples=as.list(colnames(result)),features=as.list(rownames(result)),factors=as.list(unname(sprintf("%.17g",normalized$samples$norm.factors))),values=rows),file.path(p[2],paste0(method,".json")),auto_unbox=TRUE,pretty=TRUE)
}
saveRDS(objects,file.path(p[2],"original-objects.rds"),version=3)
writeLines(capture.output(sessionInfo()),file.path(p[2],"SESSION.txt"))
cat("PASS two independent original edgeR 3.42.4 example pipelines recorded\n")
