# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Compare fresh original BAM counts with independently retained original SAM counts.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==3L);fresh<-args[1L];original<-args[2L];out<-args[3L]
mapping<-list(c("bam-single","single-meta"),c("bam-paired-fragments","paired-fragments"),c("bam-paired-reads","paired-reads"),c("bam-junctions","junction-genome"))
ledger<-list()
for(pair in mapping) {
    a<-readRDS(file.path(fresh,pair[1L],"complete-original-result.rds"));b<-readRDS(file.path(original,pair[2L],"complete-original-result.rds"))
    stopifnot(identical(a$annotation,b$annotation),identical(unname(a$counts),unname(b$counts)),identical(rownames(a$counts),rownames(b$counts)),
              identical(a$stat[[1L]],b$stat[[1L]]),identical(a$stat[[2L]],b$stat[[2L]]),identical(names(a),names(b)),
              length(a$targets)==1L,length(b$targets)==1L,identical(colnames(a$counts),a$targets),identical(colnames(b$counts),b$targets))
    if("counts_junction"%in%names(a)) {
        stopifnot(identical(a$counts_junction[1:14],b$counts_junction[1:14]),identical(a$counts_junction[[15L]],b$counts_junction[[15L]]))
    }
    ledger[[length(ledger)+1L]]<-data.frame(fresh_case=pair[1L],original_case=pair[2L],bam_target=a$targets,sam_target=b$targets)
}
write.table(do.call(rbind,ledger),file.path(out,"explicit-SAM-BAM-target-ledger.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
writeLines("PASS4_COMPLETE_ORIGINAL_SAM_BAM_COUNT_ANNOTATION_SUMMARY_JUNCTION_COMPARISONS_WITH_EXPLICIT_TARGET_LEDGER",file.path(out,"R-status.txt"))
