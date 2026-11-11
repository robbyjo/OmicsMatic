# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original package functions on a captured complete parent; completion provisional.
args <- commandArgs(TRUE);requestFile <- args[1];sourceRoot <- args[2];lockFile <- args[3];out <- args[4]
.libPaths(c(args[-(1:4)],.Library),include.site=FALSE);options(warn=1,Ncpus=1L)
dir.create(out,recursive=TRUE);setwd(out);status <- 'FAIL2_ORIGINAL_CELLCHAT_NETWORK_WORKER';detail <- '';warnings <- list();reports <- list()
json <- function(value,file)jsonlite::write_json(value,file,pretty=TRUE,auto_unbox=TRUE,null='null',na='string',digits=NA)
table <- function(data,file) {
  stopifnot(is.data.frame(data));quote <- function(x)paste0('"',gsub('"','""',enc2utf8(x),fixed=TRUE),'"')
  values <- lapply(data,function(x){v <- if(is.numeric(x))formatC(x,digits=17,format='g') else as.character(x);if(is.numeric(x)){v[is.nan(x)] <- 'NaN';v[is.infinite(x)&x>0] <- 'Inf';v[is.infinite(x)&x<0] <- '-Inf'};missing <- is.na(x)&!is.nan(if(is.numeric(x))x else rep(0,length(x)));v <- quote(v);v[missing] <- 'NA';v})
  lines <- c(paste(quote(names(data)),collapse='\t'),vapply(seq_len(nrow(data)),function(i)paste(vapply(values,function(x)x[i],character(1)),collapse='\t'),character(1)));writeLines(enc2utf8(lines),file,useBytes=TRUE)
}
form <- function(f)list(formals=deparse(formals(f),width.cutoff=500L,control='all'),body=deparse(body(f),width.cutoff=500L,control='all'))
tryCatch(withCallingHandlers({
  r <- jsonlite::read_json(requestFile,simplifyVector=TRUE);stopifnot(r$schema_version==1L,r$source_label=='1.1.3',r$mode=='network-embedding-clustering',as.character(getRversion())=='4.6.1',.Platform$OS.type=='windows')
  stopifnot(as.character(packageVersion('CellChat'))=='1.1.3',as.character(packageVersion('future'))=='1.23.0',as.character(packageVersion('future.apply'))=='1.8.1',as.character(packageVersion('reticulate'))=='1.47.0')
  lock <- jsonlite::read_json(lockFile,simplifyVector=FALSE)$sources[['1.1.3']]
  before <- vapply(names(lock$files),function(n){p <- lock$files[[n]];f <- file.path(sourceRoot,n);stopifnot(file.info(f)$size==p$bytes,digest::digest(file=f,algo='sha256')==p$sha256);p$sha256},character(1))
  definitions <- new.env(parent=baseenv())
  for(file in list.files(file.path(sourceRoot,'R'),pattern='\\.R$',full.names=TRUE))for(statement in parse(file=file,keep.source=FALSE))if(is.call(statement)&&identical(statement[[1]],as.name('<-'))&&is.symbol(statement[[2]])&&is.call(statement[[3]])&&identical(statement[[3]][[1]],as.name('function')))eval(statement,envir=definitions)
  required <- c('netEmbedding','runUMAP','netClustering','computeEigengap','netVisual_embeddingPairwise')
  guard <- function(){for(name in required)stopifnot(identical(form(get(name,envir=definitions)),form(get(name,envir=asNamespace('CellChat')))));TRUE}
  guard();json(setNames(lapply(required,function(n)form(get(n,envir=asNamespace('CellChat')))),required),'guarded-original-functions.json');future::plan(future::sequential)
  config <- reticulate::py_config();stopifnot(normalizePath(config$python,winslash='/')==normalizePath(r$python,winslash='/'),reticulate::py_module_available('umap'))
  umap <- reticulate::import('umap');stopifnot(umap$`__version__`=='0.5.12')
  json(list(python=config$python,libpython=config$libpython,version=config$version_string,umap=umap$`__version__`,managed_environment_used=FALSE),'python-runtime.json')
  object <- readRDS(file.path(r$parent_bundle,'results','comparison','original-merged-cellchat.rds'));original <- object
  comparison <- seq_along(unique(object@meta$datasets));comparisonName <- paste(comparison,collapse='-');stopifnot(length(comparison)==2L)
  for(type in r$types) {
    dir.create(type);similarity <- object@netP$similarity[[type]]$matrix[[comparisonName]];stopifnot(is.matrix(similarity),nrow(similarity)>=4L,nrow(similarity)==ncol(similarity),all(is.finite(similarity)),identical(rownames(similarity),colnames(similarity)))
    ix <- expand.grid(j=seq_len(ncol(similarity)),i=seq_len(nrow(similarity)));ix <- ix[order(ix$i,ix$j),,drop=FALSE];table(data.frame(row=rownames(similarity)[ix$i],column=colnames(similarity)[ix$j],value=similarity[cbind(ix$i,ix$j)]),file.path(type,'similarity-before.tsv'))
    removed <- if(is.null(r$embedding$pathway_remove))rownames(similarity)[colSums(similarity)==1] else r$embedding$pathway_remove
    stopifnot(all(removed %in% rownames(similarity)));included <- !(rownames(similarity) %in% removed);selected <- similarity[included,included,drop=FALSE]
    table(data.frame(feature=rownames(similarity),column_sum=colSums(similarity),included=included,reason=ifelse(included,'included',if(is.null(r$embedding$pathway_remove))'original-column-sum-equals-one' else 'explicit-pathway-removal')),file.path(type,'feature-dispositions.tsv'))
    table(data.frame(feature=colnames(selected),t(selected),check.names=FALSE),file.path(type,'umap-input.tsv'))
    rngBefore <- if(exists('.Random.seed',envir=.GlobalEnv))get('.Random.seed',envir=.GlobalEnv) else NULL;warningStart <- length(warnings)
    object <- CellChat::netEmbedding(object,type=type,pathway.remove=r$embedding$pathway_remove,umap.method=r$embedding$umap_method,n_neighbors=r$embedding$n_neighbors,min_dist=r$embedding$min_dist,seed.use=as.integer(r$embedding$seed))
    embedded <- object@netP$similarity[[type]]$dr[[comparisonName]];stopifnot(identical(rownames(embedded),colnames(selected)),ncol(embedded)==2L,all(is.finite(embedded)));rngAfterEmbedding <- .Random.seed
    saveRDS(object,file.path(type,'original-after-embedding.rds'))
    object <- CellChat::netClustering(object,type=type,k=r$clustering$k,methods=r$clustering$method,do.plot=r$clustering$plot_eigengap,do.parallel=r$clustering$parallel,nCores=as.integer(r$clustering$workers))
    rngAfterClustering <- .Random.seed;clusters <- object@netP$similarity[[type]]$group[[comparisonName]];stopifnot(length(clusters)==nrow(embedded),all(is.finite(clusters)),all(clusters>=1),all(clusters==as.integer(clusters)))
    future::plan(future::sequential)
    table(data.frame(feature=rownames(embedded),embedded,cluster=as.integer(clusters),check.names=FALSE),file.path(type,'points.tsv'))
    summary <- do.call(rbind,lapply(sort(unique(clusters)),function(k){x <- embedded[clusters==k,,drop=FALSE];center <- colMeans(x);data.frame(cluster=as.integer(k),size=nrow(x),mean_1=center[1],mean_2=center[2],within_ss=sum(rowSums(sweep(x,2,center,'-')^2)))}));table(summary,file.path(type,'cluster-summary.tsv'))
    if(r$clustering$plot_embedding){plot <- CellChat::netVisual_embeddingPairwise(object,type=type);saveRDS(plot,file.path(type,'original-plot.rds'));pdf(file.path(type,'embedding.pdf'),width=8,height=6);print(plot);dev.off()}
    stopifnot(identical(similarity,object@netP$similarity[[type]]$matrix[[comparisonName]]));guard()
    reports[[type]] <- list(points=nrow(embedded),clusters=length(unique(clusters)),comparison_name=comparisonName,neighbors_argument=if(is.null(r$embedding$n_neighbors))ceiling(sqrt(nrow(selected)))+1L else r$embedding$n_neighbors,input_transposed=TRUE,metric='correlation',rng_before=rngBefore,rng_after_embedding=rngAfterEmbedding,rng_after_clustering=rngAfterClustering,warnings_start=warningStart,warnings_end=length(warnings))
  }
  for(slot in setdiff(slotNames(object),'netP'))stopifnot(identical(methods::slot(original,slot),methods::slot(object,slot)))
  for(name in setdiff(names(original@netP),'similarity'))stopifnot(identical(original@netP[[name]],object@netP[[name]]))
  saveRDS(object,'original-network-cellchat.rds');guard();for(n in names(lock$files))stopifnot(digest::digest(file=file.path(sourceRoot,n),algo='sha256')==before[[n]])
  status <- 'COMPLETED_ORIGINAL_CELLCHAT_NETWORK_WORKER_ONLY'
},warning=function(w){warnings[[length(warnings)+1L]] <<- conditionMessage(w);invokeRestart('muffleWarning')}),error=function(e){detail <<- conditionMessage(e)})
try(future::plan(future::sequential),silent=TRUE)
json(list(status=status,detail=detail,warnings=warnings,types=reports,original_parallel_rng_warnings=sum(vapply(warnings,function(w)grepl('UNRELIABLE VALUE',w,fixed=TRUE),logical(1))),analysis_admitted=FALSE,historical_paper_runtime=FALSE),'worker-report.json');writeLines(capture.output(sessionInfo()),'session-info.txt')
quit(status=if(status=='COMPLETED_ORIGINAL_CELLCHAT_NETWORK_WORKER_ONLY')0L else 2L)
