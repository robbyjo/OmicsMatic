# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Complete separate-original comparisons for the four declared JVM25 execution cases.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==4L);analysis<-args[1L];retained<-args[2L];fresh<-args[3L];out<-args[4L]
ids<-c("mixed-libraries","fractional-overlap","fresh-empty-junctions","duplicate-basenames");ledger<-list()
for(id in ids) {
    edge<-startsWith(id,"fresh-");reference.id<-if(edge)substring(id,7L)else id
    a<-readRDS(file.path(analysis,"cases",id,"worker/complete-original-result.rds"));b<-readRDS(file.path(if(edge)fresh else retained,reference.id,"complete-original-result.rds"))
    stopifnot(identical(names(a),names(b)),identical(a$annotation,b$annotation),identical(unname(a$counts),unname(b$counts)),identical(rownames(a$counts),rownames(b$counts)),identical(a$stat[[1L]],b$stat[[1L]]),length(a$targets)==length(b$targets))
    for(i in seq_along(a$targets))stopifnot(identical(a$stat[[i+1L]],b$stat[[i+1L]]))
    if("counts_junction"%in%names(a)){stopifnot(identical(a$counts_junction[1:14],b$counts_junction[1:14]));for(i in seq_along(a$targets))stopifnot(identical(a$counts_junction[[i+14L]],b$counts_junction[[i+14L]]))}
    for(i in seq_along(a$targets))ledger[[length(ledger)+1L]]<-data.frame(case=id,position=i,public_target=a$targets[[i]],separate_original_target=b$targets[[i]])
}
write.table(do.call(rbind,ledger),file.path(out,"explicit-reference-target-ledger.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
writeLines("PASS4_COMPLETE_JVM25_PUBLIC_ORIGINAL_COUNT_LIST_COMPARISONS",file.path(out,"R-status.txt"))
