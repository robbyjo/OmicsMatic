# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Resource-only export using base R; no CellChat package or inference is loaded.
args <- commandArgs(TRUE)
stopifnot(length(args)==3L)
file <- args[1]; species <- args[2]; out <- args[3]
stopifnot(species %in% c('human','mouse','zebrafish'))
env <- new.env(parent=emptyenv())
loaded <- load(file,envir=env)
objectName <- paste0('CellChatDB.',species)
stopifnot(identical(loaded,objectName))
db <- get(objectName,envir=env,inherits=FALSE)
stopifnot(is.list(db),setequal(names(db),c('interaction','complex','cofactor','geneInfo')))
dir.create(file.path(out,'tables'))
index <- data.frame(table=character(),rows=integer(),columns=integer(),missing_values=integer())
for (name in c('interaction','complex','cofactor','geneInfo')) {
  value <- db[[name]]
  stopifnot(is.data.frame(value),!anyDuplicated(names(value)),!anyDuplicated(rownames(value)),
    !('resource_row' %in% names(value)),!is.null(rownames(value)),
    all(vapply(value,function(x) is.character(x) || is.factor(x) || is.numeric(x) || is.logical(x),logical(1))))
  text <- lapply(value,function(x) {
    if(is.factor(x)) return(as.character(x))
    if(is.numeric(x)) return(ifelse(is.na(x),NA_character_,format(x,digits=17,scientific=FALSE,trim=TRUE)))
    as.character(x)
  })
  text <- data.frame(resource_row=rownames(value),text,check.names=FALSE,stringsAsFactors=FALSE)
  missing <- which(is.na(value),arr.ind=TRUE)
  missingTable <- data.frame(row=missing[,1],column=missing[,2])
  home <- file.path(out,'tables',name); dir.create(home)
  write.table(text,file.path(home,'data.tsv'),sep='\t',quote=TRUE,qmethod='double',row.names=FALSE,na='NA',fileEncoding='UTF-8')
  write.table(missingTable,file.path(home,'missing-values.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
  write.table(data.frame(column=names(value),class=vapply(value,function(x) paste(class(x),collapse=','),character(1))),
    file.path(home,'column-classes.tsv'),sep='\t',quote=TRUE,row.names=FALSE,fileEncoding='UTF-8')
  # Independent parse/restore check preserves literal NA strings separately from
  # actual missing positions, exact identifiers, row order and component order.
  restored <- read.delim(file.path(home,'data.tsv'),colClasses='character',check.names=FALSE,
    na.strings=NULL,quote='"',comment.char='',fileEncoding='UTF-8',allowEscapes=FALSE)
  stopifnot(identical(names(restored),names(text)),identical(dim(restored),dim(text)))
  if(nrow(missing)>0L) for(i in seq_len(nrow(missing))) restored[missing[i,1],missing[i,2]+1L] <- NA_character_
  stopifnot(identical(restored,text))
  index <- rbind(index,data.frame(table=name,rows=nrow(value),columns=ncol(value),missing_values=nrow(missing)))
}
write.table(index,file.path(out,'table-index.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
writeLines(c(R.version.string,capture.output(sessionInfo())),file.path(out,'runtime.txt'))
writeLines('PASS_ORIGINAL_CELLCHAT_DATABASE_TABLES_ROUNDTRIP_ONLY',file.path(out,'R-COMPLETED.txt'))
