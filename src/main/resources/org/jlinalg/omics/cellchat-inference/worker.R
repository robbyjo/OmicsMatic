# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Typed original CellChat1.0.0/2.1.2 condition inference; worker completion is provisional.
args <- commandArgs(TRUE);stopifnot(length(args)>=5L)
requestFile <- args[1];sourceRoot <- args[2];lockFile <- args[3];out <- args[4]
.libPaths(c(args[-seq_len(4)],.Library),include.site=FALSE);options(warn=1,Ncpus=1L)
dir.create(out,recursive=TRUE);status <- 'FAIL2_ORIGINAL_CELLCHAT_INFERENCE_WORKER';detail <- '';warnings_seen <- list();messages_seen <- list();steps <- list();caseReports <- list()
writeJson <- function(value,file) jsonlite::write_json(value,file,auto_unbox=TRUE,pretty=TRUE,null='null',na='string',digits=NA)
writeTable <- function(value,file) {
  stopifnot(is.data.frame(value))
  quoted <- function(x) paste0('"',gsub('"','""',enc2utf8(x),fixed=TRUE),'"')
  columns <- lapply(value,function(x) {
    text <- if(is.numeric(x)) {v <- formatC(x,digits=17,format='g');v[is.nan(x)] <- 'NaN';v[is.infinite(x)&x>0] <- 'Inf';v[is.infinite(x)&x<0] <- '-Inf';v} else as.character(x)
    missing <- is.na(x)&!is.nan(if(is.numeric(x))x else rep(0,length(x)));text <- quoted(text);text[missing] <- 'NA';text
  })
  lines <- c(paste(quoted(names(value)),collapse='\t'),vapply(seq_len(nrow(value)),function(i)paste(vapply(columns,function(x)x[i],character(1)),collapse='\t'),character(1)))
  writeLines(enc2utf8(lines),file,useBytes=TRUE)
}
readLiteral <- function(path) read.delim(path,header=TRUE,sep='\t',quote='',comment.char='',colClasses='character',check.names=FALSE,na.strings=NULL,encoding='UTF-8')
step <- function(name,fun) {before <- length(warnings_seen);value <- fun();steps[[length(steps)+1L]] <<- list(step=name,status='completed',warnings_start=before,warnings_end=length(warnings_seen));value}
cube <- function(value,features,axis='interaction') {
  stopifnot(length(dim(value))==3L,dim(value)[3]==length(features))
  index <- expand.grid(target=seq_len(dim(value)[2]),source=seq_len(dim(value)[1]),feature=seq_len(dim(value)[3]));index <- index[order(index$feature,index$source,index$target),,drop=FALSE]
  d <- data.frame(feature=features[index$feature],source=dimnames(value)[[1]][index$source],target=dimnames(value)[[2]][index$target],value=value[cbind(index$source,index$target,index$feature)]);names(d)[1] <- axis;d
}
matrixTable <- function(value,row='row',column='column') {
  stopifnot(length(dim(value))==2L)
  ix <- expand.grid(j=seq_len(ncol(value)),i=seq_len(nrow(value)));ix <- ix[order(ix$i,ix$j),,drop=FALSE]
  d <- data.frame(row=rownames(value)[ix$i],column=colnames(value)[ix$j],value=value[cbind(ix$i,ix$j)]);names(d)[1:2] <- c(row,column);d
}
expressionTable <- function(value) {d <- data.frame(gene_id=rownames(value),as.matrix(value),check.names=FALSE);d}
tryCatch(withCallingHandlers({
  r <- jsonlite::read_json(requestFile,simplifyVector=TRUE)
  stopifnot(identical(r$schema_version,1L),r$source_label %in% c('1.0.0','2.1.2'),identical(r$mode,'condition-inference'),identical(r$expression_units,'external-log-normalized-nonnegative'))
  stopifnot(identical(as.character(getRversion()),'4.6.1'),.Platform$OS.type=='windows',requireNamespace('CellChat',quietly=TRUE),identical(as.character(packageVersion('CellChat')),r$source_label))
  lock <- jsonlite::read_json(lockFile,simplifyVector=FALSE)$sources[[r$source_label]]
  sourceBefore <- vapply(names(lock$files),function(name) {f <- file.path(sourceRoot,name);p <- lock$files[[name]];stopifnot(file.exists(f),file.info(f)$size==p$bytes,identical(digest::digest(file=f,algo='sha256'),p$sha256));p$sha256},character(1))
  definitions <- new.env(parent=baseenv())
  for(file in list.files(file.path(sourceRoot,'R'),pattern='\\.R$',full.names=TRUE)) for(statement in parse(file=file,keep.source=FALSE)) if(is.call(statement) && identical(statement[[1]],as.name('<-')) && is.symbol(statement[[2]]) && is.call(statement[[3]]) && identical(statement[[3]][[1]],as.name('function'))) eval(statement,envir=definitions)
  required <- c('createCellChat','subsetDB','subsetData','identifyOverExpressedGenes','identifyOverExpressedInteractions','computeCommunProb','filterCommunication','subsetCommunication','subsetCommunication_internal','computeCommunProbPathway','aggregateNet','netAnalysis_computeCentrality','computeCentralityLocal','computeExpr_complex','computeExpr_coreceptor','computeExpr_agonist','computeExpr_antagonist','triMean','geometricMean')
  if(r$source_label=='2.1.2')required <- c(required,'computeExpr_LR','thresholdedMean')
  if(!is.null(r$projection))required <- c(required,if(r$source_label=='1.0.0')'projectData' else 'smoothData','projectAndRecombine','randomWalkBySolve','projectOnNetwork','projectFromNetworkRecombine','l1NormalizeRows','l1NormalizeColumns')

  form <- function(f) list(formals=deparse(formals(f),width.cutoff=500L,control='all'),body=deparse(body(f),width.cutoff=500L,control='all'))
  guard <- function() {for(name in required){if(!exists(name,envir=definitions,inherits=FALSE))stop('Original source has no required function: ',name);if(!identical(form(get(name,envir=definitions)),form(get(name,envir=asNamespace('CellChat')))))stop('Original namespace body/formals differ: ',name)};TRUE}
  guard();writeJson(setNames(lapply(required,function(n)form(get(n,envir=asNamespace('CellChat')))),required),file.path(out,'guarded-original-functions.json'));future::plan(future::sequential)
  matrixText <- readLiteral(r$expression);meta <- readLiteral(r$metadata)
  stopifnot(identical(names(matrixText)[1],'gene_id'),identical(names(meta),c('cell_id','group','sample','condition')),identical(names(matrixText)[-1],meta$cell_id),!anyDuplicated(matrixText$gene_id),!anyDuplicated(meta$cell_id))
  data <- matrix(as.numeric(as.matrix(matrixText[,-1,drop=FALSE])),nrow=nrow(matrixText),dimnames=list(matrixText$gene_id,meta$cell_id));stopifnot(all(is.finite(data)),all(data>=0),max(data)>0)
  groupOrder <- unname(r$group_order);conditionOrder <- unname(r$condition_order)
  stopifnot(length(conditionOrder)>=1L,!anyDuplicated(groupOrder),!anyDuplicated(conditionOrder),setequal(meta$group,groupOrder),setequal(meta$condition,conditionOrder))
  dbEnv <- new.env(parent=emptyenv());objectName <- paste0('CellChatDB.',r$species);load(file.path(sourceRoot,'data',paste0(objectName,'.rda')),envir=dbEnv);database <- get(objectName,envir=dbEnv)
  stopifnot(all(r$database_categories %in% unique(database$interaction$annotation)))
  # Original1.0.0 subsetDB uses == search, which recycles multi-category vectors.
  # A complete-database request assigns the unchanged original resource instead.
  if(r$source_label=='1.0.0' && length(r$database_categories)>1L) {
    stopifnot(setequal(r$database_categories,unique(database$interaction$annotation)))
  } else database <- CellChat::subsetDB(database,search=r$database_categories,key='annotation')
  writeTable(data.frame(resource_row=rownames(database$interaction),database$interaction,check.names=FALSE),file.path(out,'selected-database-interactions.tsv'))
  if(!is.null(r$projection)) {
    ppiEnv <- new.env(parent=emptyenv());ppiName <- paste0('PPI.',r$projection$species);ppiPath <- file.path(sourceRoot,'data',paste0(ppiName,'.rda'));load(ppiPath,envir=ppiEnv);ppi <- get(ppiName,envir=ppiEnv)
    con <- file(file.path(out,'ppi-values.f64le'),'wb');for(i in seq_len(nrow(ppi)))writeBin(as.double(ppi[i,]),con,size=8,endian='little');close(con)
    writeJson(list(resource=ppiName,file_sha256=digest::digest(file=ppiPath,algo='sha256'),genes=unname(as.list(rownames(ppi))),columns=unname(as.list(colnames(ppi))),rows=nrow(ppi),cols=ncol(ppi),encoding='float64-le-row-major',binary_bytes=file.info(file.path(out,'ppi-values.f64le'))$size,binary_sha256=digest::digest(file=file.path(out,'ppi-values.f64le'),algo='sha256')),file.path(out,'ppi-resource.json'))
    if(!identical(r$species,r$projection$species))warnings_seen[[length(warnings_seen)+1L]] <- 'EXPLICIT DATABASE/PPI SPECIES DIFFERENCE: no homolog mapping or replacement is performed.'
    if(r$inference$raw_use)warnings_seen[[length(warnings_seen)+1L]] <- 'PROJECTION NOT CONSUMED: raw.use=TRUE consumes data.signaling.'
  }
  globalCounts <- table(factor(meta$group,levels=groupOrder),factor(meta$condition,levels=conditionOrder))
  globallyExcluded <- rownames(globalCounts)[apply(globalCounts<r$prefilter_min_group_cells,1,any)]
  dir.create(file.path(out,'cases'));objects <- list()
  for(i in seq_along(conditionOrder)) {
    id <- paste0('condition-',i);home <- file.path(out,'cases',id);dir.create(home);value <- conditionOrder[i];population <- which(meta$condition==value)
    counts <- table(factor(meta$group[population],levels=groupOrder));excluded <- if(r$prefilter_scope=='any-condition')globallyExcluded else names(counts)[counts<r$prefilter_min_group_cells]
    keep <- !(meta$group[population] %in% excluded);use <- population[keep];groups <- groupOrder[groupOrder %in% meta$group[use]];stopifnot(length(groups)>=2L)
    reason <- if(r$prefilter_scope=='any-condition')'explicit-any-condition-min-group-prefilter' else 'explicit-min-group-prefilter'
    writeTable(data.frame(cell_id=meta$cell_id[population],included=keep,reason=ifelse(keep,'included',reason)),file.path(home,'cell-dispositions.tsv'))
    frame <- data.frame(labels=factor(meta$group[use],levels=groups),samples=factor(meta$sample[use],levels=unname(r$sample_order[r$sample_order %in% meta$sample[use]])),row.names=meta$cell_id[use])
    constructor <- list(object=data[,use,drop=FALSE],meta=frame,group.by='labels');if(r$source_label=='2.1.2')constructor$datatype <- 'RNA'
    object <- step(paste0(id,':createCellChat'),function()do.call(CellChat::createCellChat,constructor));object@DB <- database
    object <- step(paste0(id,':subsetData'),function()CellChat::subsetData(object))
    object <- step(paste0(id,':identifyOverExpressedGenes'),function()CellChat::identifyOverExpressedGenes(object,only.pos=r$overexpression$only_positive,thresh.pc=r$overexpression$thresh_pc,thresh.fc=r$overexpression$thresh_fc,thresh.p=r$overexpression$thresh_p))
    object <- step(paste0(id,':identifyOverExpressedInteractions'),function()CellChat::identifyOverExpressedInteractions(object))
    stopifnot(nrow(object@LR$LRsig)>0);writeTable(data.frame(resource_row=rownames(object@LR$LRsig),object@LR$LRsig,check.names=FALSE),file.path(home,'selected-interactions.tsv'));writeTable(object@var.features$features.info,file.path(home,'overexpression.tsv'))
    writeTable(expressionTable(object@data.signaling),file.path(home,'signaling-expression.tsv'))
    if(!is.null(r$projection)) {
      object <- step(paste0(id,':original-PPI-projection'),function()if(r$source_label=='1.0.0')CellChat::projectData(object,ppi,alpha=r$projection$alpha,normalizeAdjMatrix=r$projection$normalization) else CellChat::smoothData(object,adj=ppi,alpha=r$projection$alpha,normalizeAdjMatrix=r$projection$normalization))
      projected <- if(r$source_label=='1.0.0')object@data.project else object@data.smooth
      writeTable(expressionTable(projected),file.path(home,'projected-expression.tsv'));overlap <- intersect(rownames(object@data.signaling),rownames(ppi))
      writeJson(list(overlapping_genes=unname(as.list(overlap)),overlap_count=length(overlap),input_genes=nrow(object@data.signaling),projection_consumed=!r$inference$raw_use),file.path(home,'projection-report.json'))
      if(length(overlap)==0L)warnings_seen[[length(warnings_seen)+1L]] <- paste0(id,': no PPI overlap; projected expression is unchanged.')
    }
    set.seed(as.integer(r$inference$seed));rngBefore <- .Random.seed
    object <- step(paste0(id,':computeCommunProb'),function()CellChat::computeCommunProb(object,type=r$inference$type,trim=r$inference$trim,raw.use=r$inference$raw_use,population.size=r$inference$population_size,nboot=r$inference$nboot,seed.use=as.integer(r$inference$seed),Kh=r$inference$Kh,n=r$inference$n));rngAfter <- .Random.seed
    probabilities <- cube(object@net$prob,dimnames(object@net$prob)[[3]]);probabilities$pvalue <- cube(object@net$pval,dimnames(object@net$pval)[[3]])$value;writeTable(probabilities,file.path(home,'probabilities-unfiltered.tsv'))
    if(!is.null(r$filter))object <- step(paste0(id,':filterCommunication'),function()if(r$source_label=='1.0.0')CellChat::filterCommunication(object,min.cells=r$filter$min_cells) else CellChat::filterCommunication(object,min.cells=r$filter$min_cells,min.samples=r$filter$min_samples,rare.keep=r$filter$rare_keep))
    probabilities <- cube(object@net$prob,dimnames(object@net$prob)[[3]]);probabilities$pvalue <- cube(object@net$pval,dimnames(object@net$pval)[[3]])$value;writeTable(probabilities,file.path(home,'probabilities-filtered.tsv'))
    communication <- step(paste0(id,':subsetCommunication'),function()CellChat::subsetCommunication(object,thresh=r$network$pvalue_threshold));writeTable(communication,file.path(home,'communications.tsv'))
    object <- step(paste0(id,':computeCommunProbPathway'),function()CellChat::computeCommunProbPathway(object,thresh=r$network$pvalue_threshold))
    object <- step(paste0(id,':aggregateNet'),function()CellChat::aggregateNet(object,thresh=r$network$pvalue_threshold))
    writeTable(matrixTable(object@net$count,'source','target'),file.path(home,'network-counts.tsv'));writeTable(matrixTable(object@net$weight,'source','target'),file.path(home,'network-weights.tsv'));writeTable(cube(object@netP$prob,object@netP$pathways,'pathway'),file.path(home,'pathways.tsv'))
    centr <- data.frame(slot=character(),feature=character(),group=character(),metric=character(),value=numeric());fallback <- data.frame(slot=character(),feature=character(),metric=character(),cause=character())
    # Original1.0 centrality is unmasked; original2.1.2 masks LR scores at >=thresh.
    for(slotName in c('net','netP')) {
      object <- step(paste0(id,':netAnalysis_computeCentrality:',slotName),function()if(r$source_label=='1.0.0')CellChat::netAnalysis_computeCentrality(object,slot.name=slotName) else CellChat::netAnalysis_computeCentrality(object,slot.name=slotName,thresh=r$network$pvalue_threshold))
      for(feature in names(methods::slot(object,slotName)$centr))for(metric in names(methods::slot(object,slotName)$centr[[feature]])) centr <- rbind(centr,data.frame(slot=slotName,feature=feature,group=groups,metric=metric,value=as.numeric(methods::slot(object,slotName)$centr[[feature]][[metric]])))
      net <- methods::slot(object,slotName)$prob
      if(r$source_label=='2.1.2' && slotName=='net')net[object@net$pval>=r$network$pvalue_threshold] <- 0
      for(k in seq_len(dim(net)[3]))for(metric in c('flowbet','info')) {
        cause <- tryCatch({if(metric=='flowbet')sna::flowbet(net[,,k]) else sna::infocent(net[,,k],diag=TRUE,rescale=TRUE,cmode='lower');''},error=function(e)conditionMessage(e))
        if(nzchar(cause))fallback <- rbind(fallback,data.frame(slot=slotName,feature=dimnames(net)[[3]][k],metric=metric,cause=cause))
      }
    }
    writeTable(centr,file.path(home,'centrality.tsv'));writeTable(fallback,file.path(home,'original-centrality-fallbacks.tsv'));saveRDS(object,file.path(home,'original-cellchat.rds'))
    report <- list(id=id,kind='condition',value=value,cells=length(use),groups=groups,selected_interactions=nrow(object@LR$LRsig),pathways=object@netP$pathways,original_centrality_fallbacks=nrow(fallback),rng_before=rngBefore,rng_after_computeCommunProb=rngAfter,worker_case_complete=TRUE)
    writeJson(report,file.path(home,'case-report.json'));caseReports[[id]] <- report;objects[[id]] <- object;guard();cat('PASS legacy original condition',i,'\n')
  }
  guard();for(name in names(lock$files))stopifnot(identical(digest::digest(file=file.path(sourceRoot,name),algo='sha256'),sourceBefore[[name]]))
  status <- 'COMPLETED_ORIGINAL_CELLCHAT_INFERENCE_WORKER_ONLY'
},warning=function(w){warnings_seen[[length(warnings_seen)+1L]] <<- conditionMessage(w);invokeRestart('muffleWarning')},message=function(m){messages_seen[[length(messages_seen)+1L]] <<- conditionMessage(m);invokeRestart('muffleMessage')}),error=function(e){detail <<- conditionMessage(e)})
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
writeJson(list(status=status,detail=detail,warnings=warnings_seen,messages=messages_seen,steps=steps,cases=caseReports,analysis_admitted=FALSE,original_package=paste0('CellChat',if(exists('r'))r$source_label else 'unavailable'),historical_paper_runtime=FALSE),file.path(out,'worker-report.json'))
quit(status=if(status=='COMPLETED_ORIGINAL_CELLCHAT_INFERENCE_WORKER_ONLY')0L else 2L)
