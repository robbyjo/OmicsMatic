# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE);stopifnot(length(args)==2L)
.libPaths(c(strsplit(args[[2]],.Platform$path.sep,fixed=TRUE)[[1]],.Library),include.site=FALSE)
out <- args[[1]];stopifnot(!dir.exists(out),dir.create(out))
db <- get("TxDb.Hsapiens.UCSC.hg19.knownGene",asNamespace("TxDb.Hsapiens.UCSC.hg19.knownGene"))
tx <- GenomicFeatures::transcripts(db)
first <- which(as.character(GenomicRanges::strand(tx))=="+" & BiocGenerics::start(tx)>10000L)[[1]]
point <- function(x)GenomicRanges::GRanges(seqnames=as.character(GenomicRanges::seqnames(x)),
    ranges=IRanges::IRanges(start=BiocGenerics::start(x),width=1L),strand="*")
features <- get("getGene",asNamespace("ChIPseeker"))(db,by="transcript")
source_first <- which(as.character(GenomicRanges::strand(features))=="+" & BiocGenerics::start(features)>10000L)[[1]]
cases <- list(unfiltered_transcripts_first=tx[first],source_gene_associated_first=features[source_first])
for(name in names(cases)) {
    peak <- point(cases[[name]])
    anno <- ChIPseeker::annotatePeak(peak,TxDb=db,level="transcript",verbose=FALSE)
    write.table(as.data.frame(anno),file.path(out,paste0(name,"-annotation.tsv")),sep="\t",quote=TRUE,row.names=FALSE)
    selected <- features[as.character(GenomicRanges::seqnames(features))==as.character(GenomicRanges::seqnames(peak)) &
                         BiocGenerics::start(features)==BiocGenerics::start(peak) &
                         as.character(GenomicRanges::strand(features))=="+"]
    jsonlite::write_json(list(selected_transcript=as.data.frame(cases[[name]]),
        original_annotation=as.data.frame(anno),matching_gene_associated_TSS=as.data.frame(selected)),
        file.path(out,paste0(name,".json")),dataframe="rows",auto_unbox=FALSE,pretty=TRUE,na="string",null="null")
}
writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"))
