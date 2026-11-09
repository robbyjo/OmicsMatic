# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Fixture-only inverse, independent of Java: check every emitted native value in R.
args <- commandArgs(trailingOnly=TRUE); stopifnot(length(args)==4L)
original <- args[1L]; exported <- args[2L]; native <- args[3L]; out <- args[4L]
hexraw <- function(x) {
    if (!nchar(x)) return(raw())
    starts <- seq.int(1L, nchar(x), by=2L); as.raw(strtoi(substring(x, starts, starts+1L), 16L))
}
text <- function(x) if (x=="NA") NA_character_ else {
    stopifnot(startsWith(x,"S")); value <- rawToChar(hexraw(substring(x,2L))); Encoding(value)<-"UTF-8"; value
}
readframe <- function(path) {
    lines <- readLines(path, warn=TRUE); at <- 1L
    nextline <- function() {value<-strsplit(lines[[at]],"\t",fixed=TRUE)[[1L]];at<<-at+1L;value}
    h<-nextline();stopifnot(h[1L]=="RSUBREAD_FRAME_V1");nr<-as.integer(h[2L]);nc<-as.integer(h[3L]);kind<-h[4L]
    rn<-vapply(seq_len(nr),function(i){q<-nextline();stopifnot(q[1L]=="R");text(q[2L])},"")
    columns<-vector("list",nc);cn<-character(nc)
    for(i in seq_len(nc)) {
        q<-nextline();stopifnot(q[1L]=="C");storage<-tolower(q[2L]);cn[i]<-text(q[3L])
        values<-vector(storage,nr)
        for(j in seq_len(nr)) {
            q<-nextline();stopifnot(q[1L]=="V")
            values[j]<-if(storage=="character")text(q[2L]) else if(storage=="double")readBin(hexraw(q[2L]),"double",n=1L,size=8L,endian="big") else {
                value<-readBin(hexraw(q[2L]),"integer",n=1L,size=4L,endian="big");if(storage=="logical")as.logical(value)else value
            }
        }
        columns[[i]]<-values
    }
    stopifnot(at==length(lines)+1L)
    structure(columns,names=cn,class="data.frame",row.names=if(kind=="AUTOMATIC")if(nr)c(NA_integer_,-nr)else integer()else rn)
}
sameframe <- function(expected, actual) {
    stopifnot(identical(names(expected),names(actual)),identical(row.names(expected),row.names(actual)),nrow(expected)==nrow(actual))
    for(i in seq_along(expected)) {
        stopifnot(identical(typeof(expected[[i]]),typeof(actual[[i]])),identical(expected[[i]],actual[[i]]))
        if(typeof(expected[[i]])=="double")stopifnot(identical(writeBin(expected[[i]],raw(),size=8L,endian="big"),writeBin(actual[[i]],raw(),size=8L,endian="big")))
    }
}
cases<-utils::read.delim(file.path(original,"original-cases.tsv"),stringsAsFactors=FALSE)
for(id in cases$id) {
    x<-readRDS(file.path(original,id,"complete-original-result.rds"));home<-file.path(native,id)
    for(member in c("annotation","stat",if("counts_junction"%in%names(x))"counts_junction"))sameframe(x[[member]],readframe(file.path(home,paste0(member,".frame"))))
    targets<-readframe(file.path(home,"targets.frame"));stopifnot(identical(x$targets,targets[[1L]]))
}
for(id in c("scalar-edge","zero-rows","zero-columns"))sameframe(readRDS(file.path(exported,paste0(id,".rds"))),readframe(file.path(native,paste0(id,".frame"))))
writeLines("PASS16_NATIVE_RESULT_VALUE_ROUNDTRIPS_AND3_SCALAR_CONTROLS_IN_ORIGINAL_R",out)
