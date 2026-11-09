# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Explicit synthetic replicate-zero/size-boundary and constant-score families.
args <- commandArgs(TRUE);out <- args[1];sourceRoot <- args[2];profile <- args[3]
.libPaths(c(args[-seq_len(3L)],.Library),include.site=FALSE);dir.create(out,recursive=TRUE)
stopifnot(profile %in% c('replicate-zero-and-rare','constant-comparison'))
env <- new.env(parent=emptyenv());load(file.path(sourceRoot,'data/CellChatDB.human.rda'),envir=env);db <- env$CellChatDB.human
selected <- do.call(rbind,lapply(c('TGFB','CXCL','FGF','VEGF','SPP1'),function(name) head(db$interaction[db$interaction$pathway_name==name,,drop=FALSE],8L)))
genes <- unique(c(selected$ligand,selected$receptor));complexes <- intersect(genes,rownames(db$complex));genes <- setdiff(genes,complexes)
for(name in complexes)genes <- c(genes,unname(unlist(db$complex[name,,drop=FALSE],use.names=FALSE)))
genes <- unique(genes[nzchar(genes)]);stopifnot('VEGFA' %in% genes)
samples <- paste0(rep(c('control','treated'),each=3L),'-',rep(1:3,2L));groups <- c('sender','receiver','other')
meta <- expand.grid(within_group=1:12,group=groups,sample=samples,KEEP.OUT.ATTRS=FALSE,stringsAsFactors=FALSE)
if(profile=='replicate-zero-and-rare') {
  meta <- rbind(meta,expand.grid(within_group=1:10,group='rare',sample=samples,KEEP.OUT.ATTRS=FALSE,stringsAsFactors=FALSE));groups <- c(groups,'rare')
}
meta$condition <- sub('-[123]$','',meta$sample);meta$cell_id <- paste0('edge-cell-',seq_len(nrow(meta)))
set.seed(81521L);counts <- matrix(0L,nrow=length(genes),ncol=nrow(meta),dimnames=list(genes,meta$cell_id))
for(i in seq_along(genes))for(j in seq_len(nrow(meta))) {
  value <- if(meta$group[j]==c('sender','receiver','other')[(i-1L)%%3L+1L])35 else 1
  counts[i,j] <- if(profile=='constant-comparison')value else rpois(1L,value)+1L
}
if(profile=='replicate-zero-and-rare')counts['VEGFA',meta$sample %in% c('control-3','treated-3')] <- 0L
expression <- CellChat::normalizeData(counts,scale.factor=10000,do.log=TRUE,do.sparse=FALSE)
write.table(data.frame(gene_id=rownames(expression),expression,check.names=FALSE),file.path(out,'expression.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
write.table(data.frame(gene_id=rownames(counts),counts,check.names=FALSE),file.path(out,'counts-normalization-provenance.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
write.table(meta[,c('cell_id','group','sample','condition')],file.path(out,'metadata.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
request <- list(schema_version=1L,source_label='2.2.0-2025-release',expression_units='external-log-normalized-nonnegative',mode='condition-and-sample',expression=normalizePath(file.path(out,'expression.tsv'),winslash='/'),metadata=normalizePath(file.path(out,'metadata.tsv'),winslash='/'),group_order=groups,sample_order=samples,condition_order=c('control','treated'),species='human',database_categories=c('Secreted Signaling','ECM-Receptor','Cell-Cell Contact'),prefilter_min_group_cells=0L,overexpression=list(only_positive=TRUE,thresh_pc=0,thresh_fc=0,thresh_p=0.05,do_fast=TRUE,variable_both=TRUE),inference=list(type='triMean',trim=0.1,population_size=FALSE,nboot=19L,seed=1L,Kh=0.5,n=1),condition_filter=list(min_cells=10L,min_samples=3L,rare_keep=FALSE),sample_filter=list(min_cells=10L,min_samples=1L,rare_keep=FALSE),network=list(pvalue_threshold=0.05),comparison=list(alternative='two.sided',exact=FALSE,continuity_correction=TRUE,strength_difference_threshold=0.25))
jsonlite::write_json(request,file.path(out,'request.json'),auto_unbox=TRUE,pretty=TRUE,digits=NA)
jsonlite::write_json(list(status='PASS_SYNTHETIC_CELLCHAT_EDGE_INPUTS_ONLY',profile=profile,cells=ncol(expression),genes=nrow(expression),biological_accuracy=FALSE),file.path(out,'COMPLETED.json'),auto_unbox=TRUE,pretty=TRUE)
