# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent direct original-package recipe: fixed control parameters, no
# workflow request parsing, worker helper, Java dispatcher or native audit.
args <- commandArgs(TRUE);inputs <- args[1];sourceRoot <- args[2];out <- args[3]
.libPaths(c(args[-seq_len(3L)],.Library),include.site=FALSE);dir.create(out,recursive=TRUE)
future::plan(future::sequential)
stopifnot(as.character(packageVersion('CellChat'))=='2.2.0',as.character(packageVersion('presto'))=='1.0.0')
text <- read.delim(file.path(inputs,'expression.tsv'),colClasses='character',check.names=FALSE,quote='',comment.char='',na.strings=NULL)
expression <- matrix(as.numeric(as.matrix(text[,-1])),nrow=nrow(text),dimnames=list(text$gene_id,names(text)[-1]))
metadata <- read.delim(file.path(inputs,'metadata.tsv'),colClasses='character',check.names=FALSE,quote='',comment.char='',na.strings=NULL)
stopifnot(identical(colnames(expression),metadata$cell_id))
env <- new.env(parent=emptyenv());load(file.path(sourceRoot,'data/CellChatDB.human.rda'),envir=env)
database <- CellChat::subsetDB(env$CellChatDB.human,search=c('Secreted Signaling','ECM-Receptor','Cell-Cell Contact'),key='annotation')
groups <- c('sender','receiver','other');samples <- paste0(rep(c('control','treated'),each=3L),'-',rep(seq_len(3L),2L))
warnings_seen <- character();reports <- list()
withCallingHandlers({
for(i in seq_len(8L)) {
  condition <- i<=2L
  value <- if(condition)c('control','treated')[i] else samples[i-2L]
  keep <- if(condition)which(metadata$condition==value) else which(metadata$sample==value)
  meta <- data.frame(labels=factor(metadata$group[keep],levels=groups),samples=factor(metadata$sample[keep],levels=samples[samples %in% metadata$sample[keep]]),row.names=metadata$cell_id[keep])
  object <- CellChat::createCellChat(expression[,keep,drop=FALSE],meta=meta,group.by='labels',datatype='RNA')
  object@DB <- database
  object <- CellChat::subsetData(object)
  object <- CellChat::identifyOverExpressedGenes(object,only.pos=TRUE,thresh.pc=0,thresh.fc=0,thresh.p=0.05,do.fast=TRUE)
  object <- CellChat::identifyOverExpressedInteractions(object,variable.both=TRUE)
  object <- CellChat::computeCommunProb(object,type='triMean',trim=0.1,raw.use=TRUE,population.size=FALSE,nboot=19,seed.use=1L,Kh=0.5,n=1)
  object <- CellChat::filterCommunication(object,min.cells=10,min.samples=if(condition)3 else 1,rare.keep=FALSE)
  object <- CellChat::computeCommunProbPathway(object,thresh=0.05)
  object <- CellChat::aggregateNet(object,thresh=0.05)
  object <- CellChat::netAnalysis_computeCentrality(object,slot.name='net',thresh=0.05)
  object <- CellChat::netAnalysis_computeCentrality(object,slot.name='netP',thresh=0.05)
  id <- if(condition)paste0('condition-',i) else paste0('sample-',i-2L)
  saveRDS(object,file.path(out,paste0(id,'.rds')))
  reports[[id]] <- list(cells=ncol(object@data),genes=nrow(object@data),selected_interactions=nrow(object@LR$LRsig),pathways=object@netP$pathways)
  cat('PASS direct original CellChat reference',id,'\n')
}
},warning=function(w){warnings_seen <<- c(warnings_seen,conditionMessage(w));invokeRestart('muffleWarning')},message=function(m)invokeRestart('muffleMessage'))
jsonlite::write_json(list(status='PASS_EIGHT_DIRECT_ORIGINAL_CELLCHAT_PACKAGE_REFITS_ONLY',cases=reports,warnings=warnings_seen,analysis_admitted=FALSE,historical_paper_runtime=FALSE),file.path(out,'COMPLETED.json'),auto_unbox=TRUE,pretty=TRUE)
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
