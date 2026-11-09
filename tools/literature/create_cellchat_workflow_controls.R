# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Synthetic controls using explicit original DB genes and original normalization.
args <- commandArgs(TRUE);out <- args[1];sourceRoot <- args[2]
.libPaths(c(args[-seq_len(2L)],.Library),include.site=FALSE)
dir.create(out,recursive=TRUE)
env <- new.env(parent=emptyenv());load(file.path(sourceRoot,'data/CellChatDB.human.rda'),envir=env);db <- env$CellChatDB.human
pathways <- c('TGFB','CXCL','FGF','VEGF','SPP1')
selected <- do.call(rbind,lapply(pathways,function(name) head(db$interaction[db$interaction$pathway_name==name,,drop=FALSE],8L)))
genes <- unique(c(selected$ligand,selected$receptor))
complexes <- intersect(genes,rownames(db$complex));genes <- setdiff(genes,complexes)
for(name in complexes) genes <- c(genes,unname(unlist(db$complex[name,,drop=FALSE],use.names=FALSE)))
genes <- unique(genes[nzchar(genes)]);stopifnot(length(genes)>=20L,!anyDuplicated(genes))
group <- c('sender','receiver','other');samples <- paste0(rep(c('control','treated'),each=3L),'-',rep(seq_len(3L),2L))
meta <- expand.grid(within_group=seq_len(12L),group=group,sample=samples,KEEP.OUT.ATTRS=FALSE,stringsAsFactors=FALSE)
meta$condition <- sub('-[123]$','',meta$sample);meta$cell_id <- paste0('cell-',seq_len(nrow(meta)))
set.seed(81520L)
counts <- matrix(0L,nrow=length(genes),ncol=nrow(meta),dimnames=list(genes,meta$cell_id))
for(i in seq_along(genes)) for(j in seq_len(nrow(meta))) {
  high <- group[(i-1L)%%3L+1L]
  value <- if(meta$group[j]==high) 35 else 1
  if(meta$condition[j]=='treated' && i%%4L==0L) value <- value*1.7
  counts[i,j] <- rpois(1L,value)+1L
}
expression <- CellChat::normalizeData(counts,scale.factor=10000,do.log=TRUE,do.sparse=FALSE)
write.table(data.frame(gene_id=rownames(expression),expression,check.names=FALSE),file.path(out,'expression.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
write.table(data.frame(gene_id=rownames(counts),counts,check.names=FALSE),file.path(out,'counts-normalization-provenance.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
write.table(meta[,c('cell_id','group','sample','condition')],file.path(out,'metadata.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
write.table(selected,file.path(out,'fixture-database-pair-selection.tsv'),sep='\t',quote=TRUE,row.names=FALSE)
request <- list(schema_version=1L,source_label='2.2.0-2025-release',expression_units='external-log-normalized-nonnegative',mode='condition-and-sample',
  expression=normalizePath(file.path(out,'expression.tsv'),winslash='/'),metadata=normalizePath(file.path(out,'metadata.tsv'),winslash='/'),group_order=group,sample_order=samples,condition_order=c('control','treated'),species='human',database_categories=c('Secreted Signaling','ECM-Receptor','Cell-Cell Contact'),prefilter_min_group_cells=0L,
  overexpression=list(only_positive=TRUE,thresh_pc=0,thresh_fc=0,thresh_p=0.05,do_fast=TRUE,variable_both=TRUE),
  inference=list(type='triMean',trim=0.1,population_size=FALSE,nboot=19L,seed=1L,Kh=0.5,n=1),
  condition_filter=list(min_cells=10L,min_samples=3L,rare_keep=FALSE),sample_filter=list(min_cells=10L,min_samples=1L,rare_keep=FALSE),
  network=list(pvalue_threshold=0.05),comparison=list(alternative='two.sided',exact=FALSE,continuity_correction=TRUE,strength_difference_threshold=0.25))
jsonlite::write_json(request,file.path(out,'request.json'),auto_unbox=TRUE,pretty=TRUE,digits=NA)
jsonlite::write_json(list(status='PASS_SYNTHETIC_SOURCE_DB_AND_ORIGINAL_LOG_NORMALIZATION_CONTROLS_ONLY',genes=nrow(expression),cells=ncol(expression),groups=3L,conditions=2L,biological_samples=6L,cells_per_group_per_sample=12L,normalization='CellChat::normalizeData(scale.factor10000,do.logTRUE,do.sparseFALSE)',biological_accuracy=FALSE),file.path(out,'COMPLETED.json'),auto_unbox=TRUE,pretty=TRUE)
