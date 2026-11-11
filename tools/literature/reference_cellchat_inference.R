# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Fixed original default-family refits, independent of the controller's worker.
args <- commandArgs(TRUE);out <- args[1];root <- args[2];label <- args[3];bundles <- args[4:5]
.libPaths(c(args[-(1:5)],.Library),include.site=FALSE);library(CellChat);future::plan(future::sequential)
stopifnot(as.character(packageVersion('CellChat'))==label,label %in% c('1.0.0','2.1.2'),!dir.exists(out),dir.create(out))
checks <- list();warnings_seen <- list();status <- 'FAIL2_ORIGINAL_INFERENCE_REFIT';detail <- ''
compare <- function(name,x,y){result <- all.equal(x,y,tolerance=2e-12,check.attributes=TRUE);if(!isTRUE(result))stop(name,': ',paste(result,collapse='; '));checks[[length(checks)+1L]] <<- name}
tryCatch(withCallingHandlers({
  read <- function(file)read.delim(file,sep='\t',quote='',comment.char='',colClasses='character',check.names=FALSE,na.strings=NULL,encoding='UTF-8')
  d <- read(file.path(bundles[1],'inputs','expression.tsv'));meta <- read(file.path(bundles[1],'inputs','metadata.tsv'));data <- matrix(as.numeric(as.matrix(d[,-1,drop=FALSE])),nrow=nrow(d),dimnames=list(d[[1]],names(d)[-1]));stopifnot(nrow(data)==33L,identical(colnames(data),meta$cell_id))
  conditions <- if(label=='1.0.0')c('control','treated') else c('CTRL','MASL','MASH');samples <- unlist(lapply(conditions,function(x)paste0(x,'-',1:3)));groups <- c('sender','receiver','other')
  env <- new.env(parent=emptyenv());load(file.path(root,'data','CellChatDB.human.rda'),envir=env);load(file.path(root,'data','PPI.human.rda'),envir=env);database <- env$CellChatDB.human;if(label=='2.1.2')database <- subsetDB(database,search='Secreted Signaling')
  for(i in seq_along(conditions)) {
    use <- which(meta$condition==conditions[i]);stopifnot(length(use)==108L)
    frame <- data.frame(labels=factor(meta$group[use],levels=groups),samples=factor(meta$sample[use],levels=samples[samples %in% meta$sample[use]]),row.names=meta$cell_id[use]);constructor <- list(object=data[,use,drop=FALSE],meta=frame,group.by='labels');if(label=='2.1.2')constructor$datatype <- 'RNA'
    o <- do.call(createCellChat,constructor);o@DB <- database;o <- subsetData(o);o <- identifyOverExpressedGenes(o);o <- identifyOverExpressedInteractions(o)
    if(label=='1.0.0') {o <- projectData(o,env$PPI.human);o <- computeCommunProb(o);o <- filterCommunication(o)} else {o <- smoothData(o,adj=env$PPI.human);o <- computeCommunProb(o,raw.use=FALSE,population.size=FALSE)}
    o <- computeCommunProbPathway(o);o <- aggregateNet(o);o <- netAnalysis_computeCentrality(o,slot.name='net');o <- netAnalysis_computeCentrality(o,slot.name='netP');saveRDS(o,file.path(out,paste0('condition-',i,'.rds')))
    for(b in seq_along(bundles)) {
      saved <- readRDS(file.path(bundles[b],'results','cases',paste0('condition-',i),'original-cellchat.rds'))
      for(slot in c('data','data.signaling',if(label=='1.0.0')'data.project' else 'data.smooth','meta','idents','var.features','LR','net','netP'))compare(paste0('bundle',b,':condition',i,':',slot),methods::slot(o,slot),methods::slot(saved,slot))
    }
  }
  status <- 'PASS_SEPARATE_ORIGINAL_CELLCHAT_EDITION_REFITS'
},warning=function(w){warnings_seen[[length(warnings_seen)+1L]] <<- conditionMessage(w);invokeRestart('muffleWarning')}),error=function(e){detail <<- conditionMessage(e)})
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'));jsonlite::write_json(list(status=status,detail=detail,source_label=label,warnings=warnings_seen,checks=checks,original_condition_refits=if(label=='1.0.0')2L else 3L,native_independent_statistical_refit=FALSE,historical_paper_reproduction=FALSE),file.path(out,'COMPLETED.json'),auto_unbox=TRUE,pretty=TRUE)
quit(status=if(startsWith(status,'PASS_'))0L else 2L)
