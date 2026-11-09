# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Compare fresh public executions to separately executed original reference objects.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==4L);analysis<-args[1L];retained<-args[2L];fresh<-args[3L];out<-args[4L]
ids<-utils::read.delim(file.path(retained,"original-cases.tsv"),stringsAsFactors=FALSE)$id
edges<-utils::read.delim(file.path(fresh,"cases.tsv"),stringsAsFactors=FALSE)$id
ledger<-list()
for(id in c(ids,paste0("fresh-",edges))) {
    is.fresh<-startsWith(id,"fresh-");reference.id<-if(is.fresh)substring(id,7L)else id
    source<-if(is.fresh)fresh else retained
    a<-readRDS(file.path(analysis,"cases",id,"worker/complete-original-result.rds"));b<-readRDS(file.path(source,reference.id,"complete-original-result.rds"))
    stopifnot(identical(names(a),names(b)),identical(a$annotation,b$annotation),identical(unname(a$counts),unname(b$counts)),
              identical(rownames(a$counts),rownames(b$counts)),identical(a$stat[[1L]],b$stat[[1L]]),length(a$targets)==length(b$targets),
              identical(colnames(a$counts),a$targets),identical(colnames(b$counts),b$targets))
    for(i in seq_along(a$targets))stopifnot(identical(a$stat[[i+1L]],b$stat[[i+1L]]))
    if("counts_junction"%in%names(a)) {
        stopifnot(identical(a$counts_junction[1:14],b$counts_junction[1:14]))
        for(i in seq_along(a$targets))stopifnot(identical(a$counts_junction[[i+14L]],b$counts_junction[[i+14L]]))
    }
    for(i in seq_along(a$targets))ledger[[length(ledger)+1L]]<-data.frame(case=id,position=i,public_target=a$targets[[i]],separate_original_target=b$targets[[i]])
}
stopifnot(length(ids)==16L,length(edges)==9L)
write.table(do.call(rbind,ledger),file.path(out,"explicit-reference-target-ledger.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
writeLines("PASS25_COMPLETE_PUBLIC_ORIGINAL_COUNT_LIST_COMPARISONS_WITH_EXPLICIT_POSITIONAL_TARGET_LEDGER",file.path(out,"R-status.txt"))
