# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Typed original CellChat1.1.3 condition comparison; worker completion is provisional.
args <- commandArgs(TRUE);stopifnot(length(args)>=5L)
requestFile <- args[1];sourceRoot <- args[2];lockFile <- args[3];out <- args[4]
.libPaths(c(args[-seq_len(4)],.Library),include.site=FALSE);options(warn=1,Ncpus=1L)
dir.create(out,recursive=TRUE);status <- 'FAIL2_ORIGINAL_CELLCHAT_LEGACY_WORKER';detail <- '';warnings_seen <- list();messages_seen <- list();steps <- list();caseReports <- list()
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
  stopifnot(identical(r$schema_version,1L),identical(r$source_label,'1.1.3'),identical(r$mode,'condition-comparison'),identical(r$expression_units,'external-log-normalized-nonnegative'))
  stopifnot(identical(as.character(getRversion()),'4.6.1'),.Platform$OS.type=='windows',requireNamespace('CellChat',quietly=TRUE),identical(as.character(packageVersion('CellChat')),'1.1.3'))
  lock <- jsonlite::read_json(lockFile,simplifyVector=FALSE)$sources[['1.1.3']]
  sourceBefore <- vapply(names(lock$files),function(name) {f <- file.path(sourceRoot,name);p <- lock$files[[name]];stopifnot(file.exists(f),file.info(f)$size==p$bytes,identical(digest::digest(file=f,algo='sha256'),p$sha256));p$sha256},character(1))
  definitions <- new.env(parent=baseenv())
  for(file in list.files(file.path(sourceRoot,'R'),pattern='\\.R$',full.names=TRUE)) for(statement in parse(file=file,keep.source=FALSE)) if(is.call(statement) && identical(statement[[1]],as.name('<-')) && is.symbol(statement[[2]]) && is.call(statement[[3]]) && identical(statement[[3]][[1]],as.name('function'))) eval(statement,envir=definitions)
  required <- c('createCellChat','subsetDB','subsetData','identifyOverExpressedGenes','identifyOverExpressedInteractions','projectData','projectAndRecombine','randomWalkBySolve','projectOnNetwork','projectFromNetworkRecombine','l1NormalizeRows','l1NormalizeColumns','computeCommunProb','filterCommunication','subsetCommunication','subsetCommunication_internal','computeCommunProbPathway','aggregateNet','netAnalysis_computeCentrality','computeCentralityLocal','mergeCellChat','computeNetSimilarityPairwise','computeNetD_structure','nnd','node_distance','alpha_centrality','entropia','buildSNN','rankNet','netMappingDEG','netVisual_bubble')
  form <- function(f) list(formals=deparse(formals(f),width.cutoff=500L,control='all'),body=deparse(body(f),width.cutoff=500L,control='all'))
  guard <- function() {for(name in required)stopifnot(exists(name,envir=definitions,inherits=FALSE),identical(form(get(name,envir=definitions)),form(get(name,envir=asNamespace('CellChat')))));TRUE}
  guard();writeJson(setNames(lapply(required,function(n)form(get(n,envir=asNamespace('CellChat')))),required),file.path(out,'guarded-original-functions.json'));future::plan(future::sequential)
  matrixText <- readLiteral(r$expression);meta <- readLiteral(r$metadata)
  stopifnot(identical(names(matrixText)[1],'gene_id'),identical(names(meta),c('cell_id','group','condition')),identical(names(matrixText)[-1],meta$cell_id),!anyDuplicated(matrixText$gene_id),!anyDuplicated(meta$cell_id))
  data <- matrix(as.numeric(as.matrix(matrixText[,-1,drop=FALSE])),nrow=nrow(matrixText),dimnames=list(matrixText$gene_id,meta$cell_id));stopifnot(all(is.finite(data)),all(data>=0),max(data)>0)
  groupOrder <- unname(r$group_order);conditionOrder <- unname(r$condition_order)
  stopifnot(length(conditionOrder)==2L,!anyDuplicated(groupOrder),!anyDuplicated(conditionOrder),setequal(meta$group,groupOrder),setequal(meta$condition,conditionOrder))
  dbEnv <- new.env(parent=emptyenv());objectName <- paste0('CellChatDB.',r$species);load(file.path(sourceRoot,'data',paste0(objectName,'.rda')),envir=dbEnv);database <- get(objectName,envir=dbEnv)
  stopifnot(all(r$database_categories %in% unique(database$interaction$annotation)));database <- CellChat::subsetDB(database,search=r$database_categories,key='annotation')
  writeTable(data.frame(resource_row=rownames(database$interaction),database$interaction,check.names=FALSE),file.path(out,'selected-database-interactions.tsv'))
  ppiEnv <- new.env(parent=emptyenv());ppiName <- paste0('PPI.',r$projection$species);ppiPath <- file.path(sourceRoot,'data',paste0(ppiName,'.rda'));load(ppiPath,envir=ppiEnv);ppi <- get(ppiName,envir=ppiEnv)
  writeTable(expressionTable(ppi),file.path(out,'ppi-matrix.tsv'))
  writeJson(list(resource=ppiName,file_sha256=digest::digest(file=ppiPath,algo='sha256'),genes=rownames(ppi),columns=colnames(ppi),class=class(ppi),rows=nrow(ppi),cols=ncol(ppi)),file.path(out,'ppi-resource.json'))
  if(!identical(r$species,r$projection$species)) warnings_seen[[length(warnings_seen)+1L]] <- paste0('EXPLICIT DATABASE/PPI SPECIES DIFFERENCE: ',objectName,' with ',ppiName,'. No homolog mapping or species replacement is performed.')
  if(r$inference$raw_use) warnings_seen[[length(warnings_seen)+1L]] <- 'PROJECTION NOT CONSUMED BY INFERENCE: raw.use=TRUE uses original data.signaling even though projectData is executed and retained.'
  globalCounts <- table(factor(meta$group,levels=groupOrder),factor(meta$condition,levels=conditionOrder))
  globallyExcluded <- rownames(globalCounts)[apply(globalCounts<r$prefilter_min_group_cells,1,any)]
  dir.create(file.path(out,'cases'));objects <- list()
  for(i in seq_along(conditionOrder)) {
    id <- paste0('condition-',i);home <- file.path(out,'cases',id);dir.create(home);value <- conditionOrder[i];population <- which(meta$condition==value)
    counts <- table(factor(meta$group[population],levels=groupOrder));excluded <- if(r$prefilter_scope=='any-condition')globallyExcluded else names(counts)[counts<r$prefilter_min_group_cells]
    keep <- !(meta$group[population] %in% excluded);use <- population[keep];groups <- groupOrder[groupOrder %in% meta$group[use]];stopifnot(length(groups)>=2L)
    reason <- if(r$prefilter_scope=='any-condition')'explicit-any-condition-min-group-prefilter' else 'explicit-min-group-prefilter'
    writeTable(data.frame(cell_id=meta$cell_id[population],included=keep,reason=ifelse(keep,'included',reason)),file.path(home,'cell-dispositions.tsv'))
    frame <- data.frame(labels=factor(meta$group[use],levels=groups),row.names=meta$cell_id[use])
    object <- step(paste0(id,':createCellChat'),function()CellChat::createCellChat(data[,use,drop=FALSE],meta=frame,group.by='labels'));object@DB <- database
    object <- step(paste0(id,':subsetData'),function()CellChat::subsetData(object))
    object <- step(paste0(id,':identifyOverExpressedGenes'),function()CellChat::identifyOverExpressedGenes(object,only.pos=r$overexpression$only_positive,thresh.pc=r$overexpression$thresh_pc,thresh.fc=r$overexpression$thresh_fc,thresh.p=r$overexpression$thresh_p))
    object <- step(paste0(id,':identifyOverExpressedInteractions'),function()CellChat::identifyOverExpressedInteractions(object))
    stopifnot(nrow(object@LR$LRsig)>0);writeTable(data.frame(resource_row=rownames(object@LR$LRsig),object@LR$LRsig,check.names=FALSE),file.path(home,'selected-interactions.tsv'));writeTable(object@var.features$features.info,file.path(home,'overexpression.tsv'))
    writeTable(expressionTable(object@data.signaling),file.path(home,'signaling-expression.tsv'))
    object <- step(paste0(id,':projectData'),function()CellChat::projectData(object,ppi,alpha=r$projection$alpha,normalizeAdjMatrix=r$projection$normalization))
    writeTable(expressionTable(object@data.project),file.path(home,'projected-expression.tsv'))
    overlap <- intersect(rownames(object@data.signaling),rownames(ppi));writeJson(list(overlapping_genes=unname(as.list(overlap)),overlap_count=length(overlap),input_genes=nrow(object@data.signaling),projection_consumed=!r$inference$raw_use),file.path(home,'projection-report.json'))
    if(length(overlap)==0L) warnings_seen[[length(warnings_seen)+1L]] <- paste0(id,': PPI has no overlapping signaling genes; projected expression is unchanged.')
    set.seed(as.integer(r$inference$seed));rngBefore <- .Random.seed
    object <- step(paste0(id,':computeCommunProb'),function()CellChat::computeCommunProb(object,type=r$inference$type,trim=r$inference$trim,raw.use=r$inference$raw_use,population.size=r$inference$population_size,do.fast=r$inference$do_fast,nboot=r$inference$nboot,seed.use=as.integer(r$inference$seed),Kh=r$inference$Kh,n=r$inference$n));rngAfter <- .Random.seed
    probabilities <- cube(object@net$prob,dimnames(object@net$prob)[[3]]);probabilities$pvalue <- cube(object@net$pval,dimnames(object@net$pval)[[3]])$value;writeTable(probabilities,file.path(home,'probabilities-unfiltered.tsv'))
    object <- step(paste0(id,':filterCommunication'),function()CellChat::filterCommunication(object,min.cells=r$min_cells))
    probabilities <- cube(object@net$prob,dimnames(object@net$prob)[[3]]);probabilities$pvalue <- cube(object@net$pval,dimnames(object@net$pval)[[3]])$value;writeTable(probabilities,file.path(home,'probabilities-filtered.tsv'))
    communication <- step(paste0(id,':subsetCommunication'),function()CellChat::subsetCommunication(object,thresh=r$network$pvalue_threshold));writeTable(communication,file.path(home,'communications.tsv'))
    object <- step(paste0(id,':computeCommunProbPathway'),function()CellChat::computeCommunProbPathway(object,thresh=r$network$pvalue_threshold))
    object <- step(paste0(id,':aggregateNet'),function()CellChat::aggregateNet(object,thresh=r$network$pvalue_threshold))
    writeTable(matrixTable(object@net$count,'source','target'),file.path(home,'network-counts.tsv'));writeTable(matrixTable(object@net$weight,'source','target'),file.path(home,'network-weights.tsv'));writeTable(cube(object@netP$prob,object@netP$pathways,'pathway'),file.path(home,'pathways.tsv'))
    centr <- data.frame(slot=character(),feature=character(),group=character(),metric=character(),value=numeric());fallback <- data.frame(slot=character(),feature=character(),metric=character(),cause=character())
    # 1.1.3 centrality consumes RAW filtered LR probabilities; unlike 2.2.0 it
    # does not first mask non-significant LR scores by the requested threshold.
    for(slotName in c('net','netP')) {
      object <- step(paste0(id,':netAnalysis_computeCentrality:',slotName),function()CellChat::netAnalysis_computeCentrality(object,slot.name=slotName))
      for(feature in names(methods::slot(object,slotName)$centr))for(metric in names(methods::slot(object,slotName)$centr[[feature]])) centr <- rbind(centr,data.frame(slot=slotName,feature=feature,group=groups,metric=metric,value=as.numeric(methods::slot(object,slotName)$centr[[feature]][[metric]])))
      net <- methods::slot(object,slotName)$prob
      for(k in seq_len(dim(net)[3]))for(metric in c('flowbet','info')) {
        cause <- tryCatch({if(metric=='flowbet')sna::flowbet(net[,,k]) else sna::infocent(net[,,k],diag=TRUE,rescale=TRUE,cmode='weak');''},error=function(e)conditionMessage(e))
        if(nzchar(cause))fallback <- rbind(fallback,data.frame(slot=slotName,feature=dimnames(net)[[3]][k],metric=metric,cause=cause))
      }
    }
    writeTable(centr,file.path(home,'centrality.tsv'));writeTable(fallback,file.path(home,'original-centrality-fallbacks.tsv'));saveRDS(object,file.path(home,'original-cellchat.rds'))
    report <- list(id=id,kind='condition',value=value,cells=length(use),groups=groups,selected_interactions=nrow(object@LR$LRsig),pathways=object@netP$pathways,original_centrality_fallbacks=nrow(fallback),rng_before=rngBefore,rng_after_computeCommunProb=rngAfter,worker_case_complete=TRUE)
    writeJson(report,file.path(home,'case-report.json'));caseReports[[id]] <- report;objects[[id]] <- object;guard();cat('PASS legacy original condition',i,'\n')
  }
  stopifnot(identical(levels(objects[[1]]@idents),levels(objects[[2]]@idents)))
  merged <- step('mergeCellChat',function()CellChat::mergeCellChat(objects,add.names=conditionOrder,merge.data=FALSE,cell.prefix=FALSE));dir.create(file.path(out,'comparison'))
  # Both original similarities are returned without repairing original Inf/NaN
  # handling. Raw matrices and FNN ranks are exported for native transport audit.
  nets <- lapply(objects,function(o)o@netP$prob);labels <- unlist(lapply(seq_along(nets),function(i)paste0(dimnames(nets[[i]])[[3]],'--',conditionOrder[i])),use.names=FALSE)
  matrices <- unlist(lapply(nets,function(net)lapply(seq_len(dim(net)[3]),function(i)net[,,i])),recursive=FALSE)
  if(!is.null(r$similarity$trim_fraction)) matrices <- unlist(lapply(nets,function(net) {net[net<quantile(c(net[net!=0]),r$similarity$trim_fraction)] <- 0;lapply(seq_len(dim(net)[3]),function(i)net[,,i])}),recursive=FALSE)
  for(kind in c('functional','structural')) {
    merged <- step(paste0('computeNetSimilarityPairwise:',kind),function()CellChat::computeNetSimilarityPairwise(merged,type=kind,comparison=c(1,2),k=r$similarity$k,thresh=r$similarity$trim_fraction))
    result <- merged@netP$similarity[[kind]]$matrix[['1-2']];writeTable(matrixTable(result),file.path(out,'comparison',paste0(kind,'-similarity.tsv')))
    raw <- matrix(0,length(labels),length(labels),dimnames=list(labels,labels))
    for(i in seq_along(labels))for(j in seq_along(labels)) {
      a <- (matrices[[i]]>0)*1;b <- (matrices[[j]]>0)*1
      raw[i,j] <- if(kind=='functional')sum(a*b)/sum(a+b-a*b,na.rm=TRUE) else CellChat:::computeNetD_structure(a,b)
    }
    if(kind=='functional') {raw[is.na(raw)] <- 0;diag(raw) <- 1} else {raw[is.infinite(raw)] <- 0;raw[is.na(raw)] <- 0;raw <- 1-raw}
    nnet <- length(labels);k <- r$similarity$k;if(is.null(k))k <- ceiling(sqrt(nnet))+if(nnet>25)1L else 0L
    nn <- FNN::get.knn(t(as.matrix(raw)),k=min(10*k,nnet-1))$nn.index;ranked <- cbind(seq_len(nnet),nn[,1:(k-1)])
    snn <- as.matrix(CellChat:::ComputeSNN(ranked,1/15));diag(snn) <- 1
    stopifnot(isTRUE(all.equal(unname(result),unname(raw*snn),tolerance=1e-12)))
    writeTable(matrixTable(raw),file.path(out,'comparison',paste0(kind,'-raw.tsv')))
    writeTable(data.frame(feature=labels,rank=rep(seq_len(ncol(ranked)),each=nrow(ranked)),neighbor=as.vector(ranked),check.names=FALSE),file.path(out,'comparison',paste0(kind,'-knn.tsv')))
  }
  rank <- step('rankNet',function()CellChat::rankNet(merged,slot.name='netP',mode='comparison',comparison=c(1,2),stacked=r$rank_net$stacked,do.stat=r$rank_net$do_stat,tol=r$rank_net$relative_tolerance,cutoff.pvalue=r$rank_net$cutoff_pvalue,thresh=r$network$pvalue_threshold,return.data=TRUE,do.flip=r$rank_net$flip))
  writeTable(rank$signaling.contribution,file.path(out,'comparison','information-flow.tsv'));saveRDS(rank,file.path(out,'comparison','original-rankNet.rds'))
  # Original rankNet assigns p=0 when <=3 nonzero paired entries remain, a
  # pathway is absent, or the original paired test returns NA. Keep those
  # zeros and expose their cause; do not present them as estimated p-values.
  rankFallbacks <- data.frame(pathway=character(),retained_pairs=integer(),cause=character(),original_replacement_pvalue=numeric())
  if(r$rank_net$do_stat)for(pathway in unique(as.character(rank$signaling.contribution$name))) {
    probabilities <- matrix(NA_real_,nrow=nrow(nets[[1]])^2,ncol=2L)
    for(i in 1:2)if(pathway %in% dimnames(nets[[i]])[[3]])probabilities[,i] <- as.vector(nets[[i]][,,pathway])
    probabilities <- probabilities[rowSums(probabilities,na.rm=TRUE)!=0,,drop=FALSE]
    cause <- if(nrow(probabilities)<=3L)'three-or-fewer-nonzero-pairs' else if(anyNA(probabilities))'pathway-absent-in-a-condition' else ''
    if(!nzchar(cause) && is.na(wilcox.test(probabilities[,1],probabilities[,2],paired=TRUE)$p.value))cause <- 'original-nonfinite-wilcox-replaced-zero'
    if(nzchar(cause))rankFallbacks <- rbind(rankFallbacks,data.frame(pathway=pathway,retained_pairs=nrow(probabilities),cause=cause,original_replacement_pvalue=0))
  }
  writeTable(rankFallbacks,file.path(out,'comparison','original-rankNet-zero-fallbacks.tsv'))
  if(nrow(rankFallbacks)>0)warnings_seen[[length(warnings_seen)+1L]] <- paste0('ORIGINAL RANKNET ZERO FALLBACKS: ',nrow(rankFallbacks),' pathway tests were replaced with zero by original CellChat; these zeros do not establish estimated p-values. See original-rankNet-zero-fallbacks.tsv.')
  step('render:rankNet',function(){grDevices::pdf(file.path(out,'comparison','information-flow.pdf'),width=10,height=7);on.exit(grDevices::dev.off());print(rank$gg.obj);TRUE})
  diff <- r$differential;merged <- step('identifyOverExpressedGenes:conditions',function()CellChat::identifyOverExpressedGenes(merged,group.dataset='datasets',pos.dataset=conditionOrder[2],features.name='condition-differential',only.pos=FALSE,thresh.pc=diff$thresh_pc,thresh.fc=diff$thresh_fc,thresh.p=diff$thresh_p))
  writeTable(merged@var.features[['condition-differential.info']],file.path(out,'comparison','differential-genes.tsv'))
  mapping <- step('netMappingDEG',function()CellChat::netMappingDEG(merged,features.name='condition-differential',thresh=r$network$pvalue_threshold));writeTable(mapping,file.path(out,'comparison','differential-ligand-receptor.tsv'))
  up <- step('subsetCommunication:up',function()CellChat::subsetCommunication(merged,net=mapping,datasets=conditionOrder[2],ligand.logFC=diff$up_ligand_logfc,receptor.logFC=NULL,thresh=r$network$pvalue_threshold))
  down <- step('subsetCommunication:down',function()CellChat::subsetCommunication(merged,net=mapping,datasets=conditionOrder[1],ligand.logFC=diff$down_ligand_logfc,receptor.logFC=diff$down_receptor_logfc,thresh=r$network$pvalue_threshold))
  writeTable(up,file.path(out,'comparison','up-selected-interactions.tsv'));writeTable(down,file.path(out,'comparison','down-selected-interactions.tsv'))
  plots <- list(all=NULL,up=up,down=down);plotReports <- list()
  for(name in names(plots)) {
    selected <- plots[[name]]
    if(name!='all' && nrow(selected)==0L) {plotReports[[name]] <- list(status='not-rendered-no-selected-interactions',selected_rows=0L);warnings_seen[[length(warnings_seen)+1L]] <- paste0(name,': no selected differential interactions; dependent bubble plot skipped. No interactions were added.');next}
    pairs <- if(name=='all')NULL else selected[,'interaction_name',drop=FALSE]
    plot <- step(paste0('netVisual_bubble:',name),function()CellChat::netVisual_bubble(merged,pairLR.use=pairs,sources.use=r$bubble_sources,targets.use=r$bubble_targets,comparison=c(1,2),angle.x=90,remove.isolate=TRUE,thresh=r$network$pvalue_threshold,return.data=TRUE))
    writeTable(plot$communication,file.path(out,'comparison',paste0('bubble-',name,'.tsv')));saveRDS(plot,file.path(out,'comparison',paste0('original-bubble-',name,'.rds')))
    step(paste0('render:bubble:',name),function(){grDevices::pdf(file.path(out,'comparison',paste0('bubble-',name,'.pdf')),width=12,height=8);on.exit(grDevices::dev.off());print(plot$gg.obj);TRUE});plotReports[[name]] <- list(status='original-plot-rendered',rows=nrow(plot$communication))
  }
  writeJson(plotReports,file.path(out,'comparison','plot-reports.json'));saveRDS(merged,file.path(out,'comparison','original-merged-cellchat.rds'))
  guard();for(name in names(lock$files))stopifnot(identical(digest::digest(file=file.path(sourceRoot,name),algo='sha256'),sourceBefore[[name]]))
  status <- 'COMPLETED_ORIGINAL_CELLCHAT_LEGACY_WORKER_ONLY'
},warning=function(w){warnings_seen[[length(warnings_seen)+1L]] <<- conditionMessage(w);invokeRestart('muffleWarning')},message=function(m){messages_seen[[length(messages_seen)+1L]] <<- conditionMessage(m);invokeRestart('muffleMessage')}),error=function(e){detail <<- conditionMessage(e)})
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
writeJson(list(status=status,detail=detail,warnings=warnings_seen,messages=messages_seen,steps=steps,cases=caseReports,analysis_admitted=FALSE,original_package='CellChat1.1.3',historical_paper_runtime=FALSE),file.path(out,'worker-report.json'))
quit(status=if(status=='COMPLETED_ORIGINAL_CELLCHAT_LEGACY_WORKER_ONLY')0L else 2L)
