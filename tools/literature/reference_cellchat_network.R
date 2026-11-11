# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original object transport checks and original sequential clustering from a
# captured embedding and R RNG state. No bit-reproducible UMAP replay claim.
args <- commandArgs(TRUE);out <- args[1];manifest <- args[2];.libPaths(c(args[-(1:2)],.Library),include.site=FALSE)
library(CellChat);stopifnot(as.character(packageVersion('CellChat'))=='1.1.3',!dir.exists(out),dir.create(out));setwd(out)
input <- jsonlite::read_json(manifest,simplifyVector=TRUE);checks <- list();warnings <- list();refits <- list();status <- 'FAIL2_ORIGINAL_NETWORK_REFERENCE';detail <- ''
equal <- function(name,x,y){test <- all.equal(x,y,tolerance=2e-12,check.attributes=FALSE);if(!isTRUE(test))stop(name,': ',paste(test,collapse='; '));checks[[length(checks)+1L]] <<- name}
tryCatch(withCallingHandlers({
  for(bundle in input$bundles) {
    request <- jsonlite::read_json(file.path(bundle,'effective-analysis.json'),simplifyVector=TRUE);report <- jsonlite::read_json(file.path(bundle,'results','worker-report.json'),simplifyVector=FALSE)
    fitted <- readRDS(file.path(bundle,'results','original-network-cellchat.rds'));tag <- basename(dirname(bundle))
    for(type in request$types) {
      home <- file.path(bundle,'results',type);a <- fitted@netP$similarity[[type]]$dr[['1-2']];group <- fitted@netP$similarity[[type]]$group[['1-2']]
      values <- read.delim(file.path(home,'points.tsv'),check.names=FALSE,stringsAsFactors=FALSE);stopifnot(identical(values$feature,rownames(a)))
      equal(paste(tag,type,'UMAP1'),values$UMAP1,a[,1]);equal(paste(tag,type,'UMAP2'),values$UMAP2,a[,2]);equal(paste(tag,type,'cluster'),values$cluster,as.integer(group))
      if(request$clustering$plot_embedding) {
        originalPlot <- readRDS(file.path(home,'original-plot.rds'));expectedPlot <- CellChat::netVisual_embeddingPairwise(fitted,type=type);equal(paste(tag,type,'original-plot-data'),originalPlot$data,expectedPlot$data)
      }
      if(!request$clustering$parallel) {
        before <- readRDS(file.path(home,'original-after-embedding.rds'));assign('.Random.seed',as.integer(unlist(report$types[[type]]$rng_after_embedding)),envir=.GlobalEnv);future::plan(future::sequential)
        original <- CellChat::netClustering(before,type=type,k=request$clustering$k,methods='kmeans',do.plot=FALSE,do.parallel=FALSE,nCores=request$clustering$workers)
        equal(paste(tag,type,'separate-original-sequential-clustering'),as.integer(group),as.integer(original@netP$similarity[[type]]$group[['1-2']]));saveRDS(original,paste0(tag,'-',type,'-sequential-refit.rds'));refits[[length(refits)+1L]] <- paste(tag,type)
      }
    }
  }
  status <- 'PASS_ORIGINAL_NETWORK_TRANSPORT_AND_CAPTURED_STATE_CLUSTER_REFITS'
},warning=function(w){warnings[[length(warnings)+1L]] <<- conditionMessage(w);invokeRestart('muffleWarning')}),error=function(e){detail <<- conditionMessage(e)})
try(future::plan(future::sequential),silent=TRUE);jsonlite::write_json(list(status=status,detail=detail,checks=checks,sequential_original_refits=refits,warnings=warnings,UMAP_independently_reproduced=FALSE,parallel_clustering_reproduced=FALSE,historical_paper_reproduced=FALSE),file.path(out,'COMPLETED.json'),pretty=TRUE,auto_unbox=TRUE)
quit(status=if(startsWith(status,'PASS_'))0L else 2L)
