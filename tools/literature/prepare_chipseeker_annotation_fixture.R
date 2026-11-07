# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Explicitly synthetic TxDb; reference-assembly labels are not biological evidence.
args <- commandArgs(TRUE)
stopifnot(length(args)==3L,!dir.exists(args[[1]]),dir.create(args[[1]]))
out <- args[[1]]
.libPaths(c(strsplit(args[[2]],.Platform$path.sep,fixed=TRUE)[[1]],.Library),include.site=FALSE)
stopifnot(as.character(packageVersion("txdbmaker"))=="1.8.0")
expressions <- parse(args[[3]],keep.source=FALSE)
definition <- Filter(function(x)is.call(x)&&length(x)==3L&&identical(x[[1]],as.name("<-"))&&
    identical(x[[2]],as.name("makeTxDb"))&&is.call(x[[3]])&&identical(x[[3]][[1]],as.name("function")),expressions)
stopifnot(length(definition)==1L)
original <- eval(definition[[1]][[3]],envir=asNamespace("txdbmaker"))
stopifnot(identical(body(original),body(txdbmaker::makeTxDb)),identical(formals(original),formals(txdbmaker::makeTxDb)))
transcripts <- data.frame(tx_id=c(11L,12L,22L,33L,44L),tx_name=c("txA","txA-alt","txB","txC","txD"),
    tx_chrom=c("chr1","chr1","chr1","chr1","chr2"),tx_strand=c("+","+","-","+","+"),
    tx_start=c(10000L,10100L,30000L,50000L,10000L),tx_end=c(18000L,18000L,38000L,54000L,17000L),
    gene_id=c("101","101","102","103","104"))
splicings <- data.frame(tx_id=c(rep(11L,3),rep(12L,2),rep(22L,3),rep(33L,2),rep(44L,3)),
    exon_rank=c(1L,2L,3L,1L,2L,1L,2L,3L,1L,2L,1L,2L,3L),
    exon_start=c(10000L,13000L,17000L,10100L,17000L,37000L,33000L,30000L,50000L,53000L,10000L,13000L,16000L),
    exon_end=c(11000L,14000L,18000L,11100L,18000L,38000L,34000L,31000L,51000L,54000L,11000L,14000L,17000L),
    cds_start=c(10500L,13000L,17000L,10500L,17000L,37000L,33000L,30500L,NA_integer_,NA_integer_,10500L,13000L,16000L),
    cds_end=c(11000L,14000L,17500L,11100L,17500L,37500L,34000L,31000L,NA_integer_,NA_integer_,11000L,14000L,16500L))
chrominfo <- data.frame(chrom=c("chr1","chr2"),length=c(100000L,100000L),is_circular=c(FALSE,FALSE))
metadata <- data.frame(name=c("Genome","Data source","Organism","Taxonomy ID"),
                       value=c("SYNTHETIC_CHIPSEEKER_CONTROL_V1","Explicit constructed test fixture","Homo sapiens","9606"))
db <- txdbmaker::makeTxDb(transcripts,splicings,chrominfo=chrominfo,metadata=metadata)
AnnotationDbi::saveDb(db,file.path(out,"synthetic-txdb.sqlite"))
for(name in c("transcripts","splicings","chrominfo","metadata"))
    write.table(get(name),file.path(out,paste0(name,".tsv")),sep="\t",quote=FALSE,row.names=FALSE,na="NA")
positions <- c(1L,6000L,6999L,7000L,7999L,8000L,8999L,9000L,9999L,10000L,10001L,10100L,
               10499L,10500L,11000L,11001L,12000L,12999L,13000L,14000L,14001L,16000L,
               17000L,17500L,17501L,18000L,18001L,18999L,19000L,20000L,21000L,21500L,22000L,
               27000L,29999L,30000L,30500L,31000L,32000L,33000L,34000L,35000L,37000L,37500L,
               37501L,37999L,38000L,38001L,38999L,39000L,40000L,41000L,42000L,
               48000L,49999L,50000L,50500L,51001L,52000L,53000L,54000L,54001L,58000L,90000L)
peaks <- data.frame(peak_id=paste0("p",seq_along(positions)),contig="chr1",start=positions,end=positions)
extra <- data.frame(peak_id=c("crossA","crossB","crossTSS","dup","dup","missing"),
                    contig=c("chr1","chr1","chr1","chr2","chr2","chrUn"),
                    start=c(10990L,33990L,9990L,10500L,14500L,10L),end=c(13010L,37010L,10110L,10510L,14510L,20L))
peaks <- rbind(peaks,extra)
write.table(peaks,file.path(out,"peaks.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
jsonlite::write_json(list(status="prepared-synthetic-TxDb-not-user-analysis",rows=nrow(peaks),
    transcript_count=nrow(transcripts),exon_count=nrow(splicings),original_makeTxDb_body_formals_match=TRUE,
    fixture_scope="Constructed interval and label controls only; no paper, genome or biological reproduction."),
    file.path(out,"RESULT.json"),auto_unbox=TRUE,pretty=TRUE)
