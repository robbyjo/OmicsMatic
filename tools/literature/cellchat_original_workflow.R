# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Typed original-package workflow. The controller owns input/runtime capture and
# final admission; worker completion alone never admits user analysis.
args <- commandArgs(TRUE)
stopifnot(length(args)>=5L)
requestFile <- args[1]; sourceRoot <- args[2]; lockFile <- args[3]; out <- args[4]
.libPaths(c(args[-seq_len(4L)],.Library),include.site=FALSE)
options(warn=1,Ncpus=1L)
dir.create(out,recursive=TRUE)
status <- 'FAIL2_ORIGINAL_CELLCHAT_WORKER';detail <- '';warnings_seen <- list();messages_seen <- list();steps <- list()
writeJson <- function(value,file) jsonlite::write_json(value,file,auto_unbox=TRUE,pretty=TRUE,null='null',na='string',digits=NA)
writeTable <- function(value,file) {
  stopifnot(is.data.frame(value))
  text <- lapply(value,function(x) {
    if(is.numeric(x)) {
      v <- formatC(x,digits=17,format='g')
      v[is.na(x) & !is.nan(x)] <- 'NA';v[is.nan(x)] <- 'NaN';v[is.infinite(x) & x>0] <- 'Inf';v[is.infinite(x) & x<0] <- '-Inf'
      return(v)
    }
    as.character(x)
  })
  names(text) <- names(value)
  write.table(as.data.frame(text,check.names=FALSE,stringsAsFactors=FALSE),file,sep='\t',quote=TRUE,qmethod='double',row.names=FALSE,na='NA',fileEncoding='UTF-8')
}
readLiteral <- function(path) read.delim(path,header=TRUE,sep='\t',quote='',comment.char='',colClasses='character',check.names=FALSE,na.strings=NULL,fileEncoding='UTF-8')
recordStep <- function(name,fun) {
  before <- length(warnings_seen);value <- fun();steps[[length(steps)+1L]] <<- list(step=name,status='completed',warnings_start=before,warnings_end=length(warnings_seen));value
}
numberArray <- function(value,names3,axis3) {
  stopifnot(length(names3)>=1L)
  if(length(dim(value))==2L && length(names3)==1L) value <- array(value,dim=c(dim(value),1L),dimnames=list(rownames(value),colnames(value),names3))
  stopifnot(length(dim(value))==3L,dim(value)[3]==length(names3))
  index <- expand.grid(target=seq_len(dim(value)[2]),source=seq_len(dim(value)[1]),feature=seq_len(dim(value)[3]))
  index <- index[order(index$feature,index$source,index$target),,drop=FALSE]
  frame <- data.frame(feature=names3[index$feature],source=dimnames(value)[[1]][index$source],target=dimnames(value)[[2]][index$target],value=value[cbind(index$source,index$target,index$feature)],stringsAsFactors=FALSE)
  names(frame)[1] <- axis3;frame
}
tryCatch(withCallingHandlers({
  r <- jsonlite::read_json(requestFile,simplifyVector=TRUE)
  stopifnot(identical(r$schema_version,1L),identical(r$source_label,'2.2.0-2025-release'),identical(r$expression_units,'external-log-normalized-nonnegative'),identical(r$mode,'condition-and-sample'))
  stopifnot(identical(as.character(getRversion()),'4.6.1'),.Platform$OS.type=='windows',requireNamespace('CellChat',quietly=TRUE),identical(as.character(packageVersion('CellChat')),'2.2.0'))
  lock <- jsonlite::read_json(lockFile,simplifyVector=FALSE)$sources[[r$source_label]]
  stopifnot(!is.null(lock),identical(lock$version,'2.2.0'))
  sourceBefore <- vapply(names(lock$files),function(name) {
    file <- file.path(sourceRoot,name);wanted <- lock$files[[name]]
    if(!file.exists(file)) stop('Missing original CellChat source file: ',name)
    if(file.info(file)$size!=wanted$bytes) stop('Original CellChat source size mismatch: ',name)
    actual <- digest::digest(file=file,algo='sha256')
    if(!identical(actual,wanted$sha256)) stop('Original CellChat source hash mismatch: ',name)
    actual
  },character(1))
  definitions <- new.env(parent=baseenv())
  for(file in list.files(file.path(sourceRoot,'R'),pattern='\\.R$',full.names=TRUE)) for(statement in parse(file=file,keep.source=FALSE)) {
    if(is.call(statement) && identical(statement[[1]],as.name('<-')) && is.symbol(statement[[2]]) && is.call(statement[[3]]) && identical(statement[[3]][[1]],as.name('function'))) eval(statement,envir=definitions)
  }
  required <- c('createCellChat','subsetDB','subsetData','identifyOverExpressedGenes','identifyOverExpressedInteractions','computeCommunProb','filterCommunication','computeCommunProbPathway','aggregateNet','netAnalysis_computeCentrality','computeCentralityLocal','computeExpr_LR','computeExpr_complex','computeExpr_coreceptor','computeExpr_agonist','computeExpr_antagonist','triMean','geometricMean','thresholdedMean')
  functionForm <- function(f) list(formals=deparse(formals(f),width.cutoff=500L,control='all'),body=deparse(body(f),width.cutoff=500L,control='all'))
  guard <- function() {
    for(name in required) stopifnot(exists(name,envir=definitions,inherits=FALSE),identical(functionForm(get(name,envir=definitions)),functionForm(get(name,envir=asNamespace('CellChat')))))
    TRUE
  }
  guard();writeJson(setNames(lapply(required,function(n) functionForm(get(n,envir=asNamespace('CellChat')))),required),file.path(out,'guarded-original-functions.json'))
  if(r$overexpression$do_fast) stopifnot(requireNamespace('presto',quietly=TRUE),identical(as.character(packageVersion('presto')),'1.0.0'))
  future::plan(future::sequential)
  matrixText <- readLiteral(r$expression);meta <- readLiteral(r$metadata)
  stopifnot(identical(names(matrixText)[1],'gene_id'),identical(names(meta),c('cell_id','group','sample','condition')),identical(names(matrixText)[-1],meta$cell_id),!anyDuplicated(matrixText$gene_id),!anyDuplicated(meta$cell_id))
  data <- matrix(as.numeric(as.matrix(matrixText[,-1,drop=FALSE])),nrow=nrow(matrixText),dimnames=list(matrixText$gene_id,meta$cell_id))
  stopifnot(all(is.finite(data)),all(data>=0),max(data)>0)
  groupOrder <- unname(r$group_order);sampleOrder <- unname(r$sample_order);conditionOrder <- unname(r$condition_order)
  stopifnot(length(conditionOrder)==2L,!anyDuplicated(groupOrder),!anyDuplicated(sampleOrder),!anyDuplicated(conditionOrder),setequal(meta$group,groupOrder),setequal(meta$sample,sampleOrder),setequal(meta$condition,conditionOrder),all(vapply(split(meta$condition,meta$sample),function(x) length(unique(x))==1L,logical(1))))
  dbEnv <- new.env(parent=emptyenv());objectName <- paste0('CellChatDB.',r$species);load(file.path(sourceRoot,'data',paste0(objectName,'.rda')),envir=dbEnv)
  database <- get(objectName,envir=dbEnv,inherits=FALSE)
  stopifnot(!anyDuplicated(r$database_categories),all(r$database_categories %in% unique(database$interaction$annotation)))
  database <- CellChat::subsetDB(database,search=r$database_categories,key='annotation')
  writeTable(data.frame(resource_row=rownames(database$interaction),database$interaction,check.names=FALSE),file.path(out,'selected-database-interactions.tsv'))
  caseSpecifications <- c(lapply(seq_along(conditionOrder),function(i) list(id=paste0('condition-',i),kind='condition',value=conditionOrder[i],cells=which(meta$condition==conditionOrder[i]))),
    lapply(seq_along(sampleOrder),function(i) list(id=paste0('sample-',i),kind='sample',value=sampleOrder[i],cells=which(meta$sample==sampleOrder[i]))))
  dir.create(file.path(out,'cases'));caseReports <- list();objects <- list()
  for(spec in caseSpecifications) {
    home <- file.path(out,'cases',spec$id);dir.create(home)
    use <- spec$cells;counts <- table(factor(meta$group[use],levels=groupOrder))
    excluded <- names(counts)[counts<r$prefilter_min_group_cells]
    keep <- !(meta$group[use] %in% excluded);use <- use[keep]
    groupLevels <- groupOrder[groupOrder %in% meta$group[use]]
    stopifnot(length(groupLevels)>=2L,length(use)>0L)
    disposition <- data.frame(cell_id=meta$cell_id[spec$cells],included=keep,reason=ifelse(keep,'included','explicit-min-group-prefilter'),stringsAsFactors=FALSE)
    writeTable(disposition,file.path(home,'cell-dispositions.tsv'))
    frame <- data.frame(labels=factor(meta$group[use],levels=groupLevels),samples=factor(meta$sample[use],levels=sampleOrder[sampleOrder %in% meta$sample[use]]),row.names=meta$cell_id[use])
    object <- recordStep(paste0(spec$id,':createCellChat'),function() CellChat::createCellChat(data[,use,drop=FALSE],meta=frame,group.by='labels',datatype='RNA'))
    object@DB <- database
    object <- recordStep(paste0(spec$id,':subsetData'),function() CellChat::subsetData(object))
    object <- recordStep(paste0(spec$id,':identifyOverExpressedGenes'),function() CellChat::identifyOverExpressedGenes(object,only.pos=r$overexpression$only_positive,thresh.pc=r$overexpression$thresh_pc,thresh.fc=r$overexpression$thresh_fc,thresh.p=r$overexpression$thresh_p,do.fast=r$overexpression$do_fast))
    object <- recordStep(paste0(spec$id,':identifyOverExpressedInteractions'),function() CellChat::identifyOverExpressedInteractions(object,variable.both=r$overexpression$variable_both))
    stopifnot(nrow(object@LR$LRsig)>0L)
    writeTable(data.frame(resource_row=rownames(object@LR$LRsig),object@LR$LRsig,check.names=FALSE),file.path(home,'selected-interactions.tsv'))
    writeTable(object@var.features$features.info,file.path(home,'overexpression.tsv'))
    set.seed(as.integer(r$inference$seed));rngBefore <- .Random.seed
    object <- recordStep(paste0(spec$id,':computeCommunProb'),function() CellChat::computeCommunProb(object,type=r$inference$type,trim=r$inference$trim,raw.use=TRUE,population.size=r$inference$population_size,nboot=r$inference$nboot,seed.use=as.integer(r$inference$seed),Kh=r$inference$Kh,n=r$inference$n))
    rngAfter <- .Random.seed
    original <- numberArray(object@net$prob,dimnames(object@net$prob)[[3]],'interaction');original$pvalue <- numberArray(object@net$pval,dimnames(object@net$pval)[[3]],'interaction')$value
    writeTable(original,file.path(home,'probabilities-unfiltered.tsv'))
    settings <- if(spec$kind=='condition') r$condition_filter else r$sample_filter
    object <- recordStep(paste0(spec$id,':filterCommunication'),function() CellChat::filterCommunication(object,min.cells=settings$min_cells,min.samples=settings$min_samples,rare.keep=settings$rare_keep))
    probability <- numberArray(object@net$prob,dimnames(object@net$prob)[[3]],'interaction');probability$pvalue <- numberArray(object@net$pval,dimnames(object@net$pval)[[3]],'interaction')$value
    stopifnot(all(is.finite(probability$value)),all(is.finite(probability$pvalue)))
    writeTable(probability,file.path(home,'probabilities-filtered.tsv'))
    object <- recordStep(paste0(spec$id,':computeCommunProbPathway'),function() CellChat::computeCommunProbPathway(object,thresh=r$network$pvalue_threshold))
    object <- recordStep(paste0(spec$id,':aggregateNet'),function() CellChat::aggregateNet(object,thresh=r$network$pvalue_threshold))
    writeTable(numberArray(array(object@net$count,dim=c(nrow(object@net$count),ncol(object@net$count),1L),dimnames=list(rownames(object@net$count),colnames(object@net$count),'count')),'count','metric'),file.path(home,'network-counts.tsv'))
    writeTable(numberArray(array(object@net$weight,dim=c(nrow(object@net$weight),ncol(object@net$weight),1L),dimnames=list(rownames(object@net$weight),colnames(object@net$weight),'weight')),'weight','metric'),file.path(home,'network-weights.tsv'))
    pathway <- numberArray(object@netP$prob,object@netP$pathways,'pathway');writeTable(pathway,file.path(home,'pathways.tsv'))
    centralityRows <- data.frame(slot=character(),feature=character(),group=character(),metric=character(),value=numeric())
    for(slotName in c('net','netP')) {
      object <- recordStep(paste0(spec$id,':netAnalysis_computeCentrality:',slotName),function() CellChat::netAnalysis_computeCentrality(object,slot.name=slotName,thresh=r$network$pvalue_threshold))
      centr <- methods::slot(object,slotName)$centr
      for(feature in names(centr)) for(metric in names(centr[[feature]])) {
        values <- centr[[feature]][[metric]];stopifnot(length(values)==length(groupLevels))
        centralityRows <- rbind(centralityRows,data.frame(slot=slotName,feature=feature,group=groupLevels,metric=metric,value=as.numeric(values)))
      }
    }
    writeTable(centralityRows,file.path(home,'centrality.tsv'))
    # Retain original fallback results and independently report the source's
    # silent tryCatch-to-zero behavior for these two centrality measures.
    fallback <- data.frame(slot=character(),feature=character(),metric=character(),cause=character())
    for(slotName in c('net','netP')) {
      net <- methods::slot(object,slotName)$prob
      if(slotName=='net') net[object@net$pval>=r$network$pvalue_threshold] <- 0
      stopifnot(length(dim(net))==3L)
      for(k in seq_len(dim(net)[3])) for(metric in c('flowbet','info')) {
        cause <- tryCatch({if(metric=='flowbet') sna::flowbet(net[,,k]) else sna::infocent(net[,,k],diag=TRUE,rescale=TRUE,cmode='lower');''},error=function(e) conditionMessage(e))
        if(nzchar(cause)) fallback <- rbind(fallback,data.frame(slot=slotName,feature=dimnames(net)[[3]][k],metric=metric,cause=cause))
      }
    }
    writeTable(fallback,file.path(home,'original-centrality-fallbacks.tsv'))
    writeTable(data.frame(gene_id=rownames(data),signaling=rownames(data) %in% rownames(object@data.signaling),overexpressed=rownames(data) %in% object@var.features$features),file.path(home,'gene-dispositions.tsv'))
    saveRDS(object,file.path(home,'original-cellchat.rds'))
    report <- list(id=spec$id,kind=spec$kind,value=spec$value,cells=length(use),groups=groupLevels,samples=levels(frame$samples),selected_interactions=nrow(object@LR$LRsig),pathways=object@netP$pathways,original_centrality_fallbacks=nrow(fallback),rng_before=rngBefore,rng_after_computeCommunProb=rngAfter,worker_case_complete=TRUE)
    writeJson(report,file.path(home,'case-report.json'));caseReports[[spec$id]] <- report;objects[[spec$id]] <- object
    guard();cat('PASS original CellChat case',spec$id,'\n')
  }
  # Every declared key is retained. An interaction not selected in a fit is
  # explicitly not comparable; it is never silently filled with zero.
  dir.create(file.path(out,'comparison'))
  tables <- lapply(caseReports,function(report) read.delim(file.path(out,'cases',report$id,'probabilities-filtered.tsv'),check.names=FALSE,stringsAsFactors=FALSE))
  keys <- function(t) paste(t$interaction,t$source,t$target,sep='\r')
  allKeys <- unique(unlist(lapply(tables,keys),use.names=FALSE));comparison <- list()
  for(key in allKeys) {
    tokens <- strsplit(key,'\r',fixed=TRUE)[[1]]
    one <- function(id) {t <- tables[[id]];ix <- match(key,keys(t));if(is.na(ix)) NA_real_ else t$value[ix]}
    control <- one('condition-1');treated <- one('condition-2');diff <- treated-control
    sampleValues <- vapply(seq_along(sampleOrder),function(i) one(paste0('sample-',i)),numeric(1))
    sampleConditions <- vapply(sampleOrder,function(s) unique(meta$condition[meta$sample==s]),character(1))
    stat <- NA_real_;pvalue <- NA_real_;testStatus <- 'not-tested-incomplete-selected-interaction'
    if(all(is.finite(sampleValues))) {
      x <- sampleValues[sampleConditions==conditionOrder[1]];y <- sampleValues[sampleConditions==conditionOrder[2]]
      if(length(x)>=2L && length(y)>=2L) {
        fit <- wilcox.test(x,y,alternative=r$comparison$alternative,paired=FALSE,exact=r$comparison$exact,correct=r$comparison$continuity_correction)
        stat <- unname(fit$statistic);pvalue <- fit$p.value;testStatus <- if(is.finite(pvalue)) 'tested-original-wilcox' else 'original-nonfinite-wilcox-result'
      } else testStatus <- 'not-tested-fewer-than-two-samples-per-condition'
    }
    comparison[[length(comparison)+1L]] <- data.frame(interaction=tokens[1],source=tokens[2],target=tokens[3],control=control,treated=treated,treated_minus_control=diff,strict_difference_above_threshold=if(is.finite(diff)) diff>r$comparison$strength_difference_threshold else NA,wilcox_statistic=stat,wilcox_pvalue=pvalue,status=testStatus)
  }
  writeTable(do.call(rbind,comparison),file.path(out,'comparison','pooled-and-sample-probabilities.tsv'))
  guard()
  for(name in names(lock$files)) if(!identical(digest::digest(file=file.path(sourceRoot,name),algo='sha256'),sourceBefore[[name]])) stop('Original CellChat source changed during analysis: ',name)
  status <- 'COMPLETED_ORIGINAL_CELLCHAT_WORKER_ONLY'
},warning=function(w) {warnings_seen[[length(warnings_seen)+1L]] <<- conditionMessage(w);invokeRestart('muffleWarning')},message=function(m) {messages_seen[[length(messages_seen)+1L]] <<- conditionMessage(m);invokeRestart('muffleMessage')}),error=function(e) {detail <<- conditionMessage(e)})
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
writeJson(list(status=status,detail=detail,warnings=warnings_seen,messages=messages_seen,steps=steps,cases=if(exists('caseReports'))caseReports else list(),analysis_admitted=FALSE,original_package='CellChat2.2.0',historical_paper_runtime=FALSE),file.path(out,'worker-report.json'))
quit(status=if(status=='COMPLETED_ORIGINAL_CELLCHAT_WORKER_ONLY')0L else 2L)
