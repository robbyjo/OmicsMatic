# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Separate fixed original-package refits for the three-group synthetic baseline.
args <- commandArgs(TRUE);out <- args[1];source <- args[2];bundles <- args[3:4]
.libPaths(c(args[-(1:4)],.Library),include.site=FALSE);library(CellChat);future::plan(future::sequential);options(warn=1)
stopifnot(as.character(packageVersion('CellChat'))=='1.1.3',!dir.exists(out),dir.create(out))
status <- 'FAIL2_FIXED_ORIGINAL_CELLCHAT_LEGACY_REFIT';detail <- '';warnings_seen <- list();checks <- list()
compare <- function(name,x,y) {result <- all.equal(x,y,tolerance=2e-12,check.attributes=TRUE);if(!isTRUE(result))stop(name,': ',paste(result,collapse='; '));checks[[length(checks)+1L]] <<- name}
tryCatch(withCallingHandlers({
  read <- function(file)read.delim(file,sep='\t',quote='',comment.char='',colClasses='character',check.names=FALSE,na.strings=NULL,encoding='UTF-8')
  data <- read(file.path(bundles[1],'inputs','expression.tsv'));meta <- read(file.path(bundles[1],'inputs','metadata.tsv'))
  expression <- matrix(as.numeric(as.matrix(data[,-1,drop=FALSE])),nrow=nrow(data),dimnames=list(data[[1]],names(data)[-1]))
  stopifnot(nrow(expression)==33L,ncol(expression)==216L,identical(colnames(expression),meta$cell_id),identical(names(meta),c('cell_id','group','condition')))
  env <- new.env(parent=emptyenv());load(file.path(source,'data','CellChatDB.human.rda'),envir=env);load(file.path(source,'data','PPI.mouse.rda'),envir=env)
  objects <- list();conditions <- c('control','treated');groups <- c('sender','receiver','other')
  for(i in 1:2) {
    use <- which(meta$condition==conditions[i]);stopifnot(all(table(factor(meta$group[use],levels=groups))==36L))
    frame <- data.frame(labels=factor(meta$group[use],levels=groups),row.names=meta$cell_id[use])
    o <- createCellChat(expression[,use,drop=FALSE],meta=frame,group.by='labels');o@DB <- env$CellChatDB.human
    o <- subsetData(o);o <- identifyOverExpressedGenes(o);o <- identifyOverExpressedInteractions(o);o <- projectData(o,env$PPI.mouse)
    o <- computeCommunProb(o,raw.use=TRUE,nboot=19,trim=.1);o <- filterCommunication(o,min.cells=10);o <- computeCommunProbPathway(o);o <- aggregateNet(o)
    o <- netAnalysis_computeCentrality(o,slot.name='net');o <- netAnalysis_computeCentrality(o,slot.name='netP')
    saveRDS(o,file.path(out,paste0('condition-',i,'.rds')))
    for(b in seq_along(bundles)) {
      saved <- readRDS(file.path(bundles[b],'results','cases',paste0('condition-',i),'original-cellchat.rds'))
      for(slot in c('data','data.signaling','data.project','meta','idents','var.features','LR','net','netP'))compare(paste0('bundle',b,':condition',i,':',slot),methods::slot(o,slot),methods::slot(saved,slot))
    }
    objects[[i]] <- o
  }
  m <- mergeCellChat(objects,add.names=conditions);m <- computeNetSimilarityPairwise(m,type='functional');m <- computeNetSimilarityPairwise(m,type='structural')
  ranked <- rankNet(m,mode='comparison',stacked=TRUE,do.stat=TRUE,return.data=TRUE,do.flip=FALSE)
  m <- identifyOverExpressedGenes(m,group.dataset='datasets',pos.dataset='treated',features.name='condition-differential',only.pos=FALSE,thresh.pc=.1,thresh.fc=.1,thresh.p=1)
  mapped <- netMappingDEG(m,features.name='condition-differential');up <- subsetCommunication(m,net=mapped,datasets='treated',ligand.logFC=.2,receptor.logFC=NULL);down <- subsetCommunication(m,net=mapped,datasets='control',ligand.logFC=-.1,receptor.logFC=-.1)
  plots <- list(all=netVisual_bubble(m,sources.use=groups,targets.use=groups,comparison=c(1,2),angle.x=90,remove.isolate=TRUE,return.data=TRUE),up=netVisual_bubble(m,pairLR.use=up[,'interaction_name',drop=FALSE],sources.use=groups,targets.use=groups,comparison=c(1,2),angle.x=90,remove.isolate=TRUE,return.data=TRUE),down=netVisual_bubble(m,pairLR.use=down[,'interaction_name',drop=FALSE],sources.use=groups,targets.use=groups,comparison=c(1,2),angle.x=90,remove.isolate=TRUE,return.data=TRUE))
  for(b in seq_along(bundles)) {
    home <- file.path(bundles[b],'results','comparison');saved <- readRDS(file.path(home,'original-merged-cellchat.rds'))
    for(slot in c('data.signaling','net','netP','meta','idents','var.features','LR'))compare(paste0('bundle',b,':merged:',slot),methods::slot(m,slot),methods::slot(saved,slot))
    compare(paste0('bundle',b,':rankNet'),ranked$signaling.contribution,readRDS(file.path(home,'original-rankNet.rds'))$signaling.contribution)
    for(kind in names(plots))compare(paste0('bundle',b,':bubble:',kind),plots[[kind]]$communication,readRDS(file.path(home,paste0('original-bubble-',kind,'.rds')))$communication)
  }
  saveRDS(list(merged=m,ranked=ranked$signaling.contribution,mapped=mapped,up=up,down=down,bubbles=lapply(plots,function(x)x$communication)),file.path(out,'original-comparison.rds'))
  status <- 'PASS_SEPARATE_FIXED_ORIGINAL_CELLCHAT_1_1_3_REFITS'
},warning=function(w){warnings_seen[[length(warnings_seen)+1L]] <<- conditionMessage(w);invokeRestart('muffleWarning')}),error=function(e){detail <<- conditionMessage(e)})
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
jsonlite::write_json(list(status=status,detail=detail,warnings=warnings_seen,checks=checks,separate_original_condition_refits=2L,native_independent_statistical_refit=FALSE,historical_paper_reproduction=FALSE),file.path(out,'COMPLETED.json'),auto_unbox=TRUE,pretty=TRUE)
quit(status=if(startsWith(status,'PASS_'))0L else 2L)
