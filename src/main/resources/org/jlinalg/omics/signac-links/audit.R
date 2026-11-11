# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Adapter audit of literal upstream methods; does not modify the full package or archived function bodies.
audit_link_peaks <- function(object,env,case,gene.coords) {
  meta<-SeuratObject::GetAssayData(object[['peaks']],layer='meta.features')
  counts<-SeuratObject::GetAssayData(object[['peaks']],layer='counts')
  if(!'count'%in%colnames(meta)){info<-env$FindTopFeatures.default(counts,verbose=FALSE);meta<-cbind(meta,info[rownames(meta),,drop=FALSE])}
  expression<-SeuratObject::GetAssayData(object[['RNA']],layer='data');positive.peaks<-Matrix::rowSums(counts>0);positive.genes<-Matrix::rowSums(expression>0)
  peak.keep<-positive.peaks>case$min_cells;gene.keep<-positive.genes>case$min_cells
  if(!is.null(case$genes_use))gene.keep<-intersect(names(gene.keep[gene.keep]),unlist(case$genes_use))
  peak.data<-counts[peak.keep,];expression<-expression[gene.keep,,drop=FALSE];genes<-rownames(expression)
  coordinates<-if(case$gene_id)gene.coords[gene.coords$gene_id%in%genes] else gene.coords[gene.coords$gene_name%in%genes]
  if(case$gene_id)coordinates$gene_name<-coordinates$gene_id
  peaks<-GenomicRanges::granges(object[['peaks']])[peak.keep];distance<-env$DistanceToTSS(peaks,coordinates,distance=case$distance)
  if(!is.null(case$min_distance)&&case$min_distance>0)distance<-distance-env$DistanceToTSS(peaks,coordinates,distance=case$min_distance)
  peak.data<-Matrix::t(peak.data);method<-if(case$method=='pearson')env$corSparse else env$SparseSpearmanCor;all.peaks<-rownames(counts)[peak.keep];pairs<-list();cis<-list();features.match<-c('GC.percent','count','sequence.length')
  for(i in seq_len(ncol(distance))){
    gene<-colnames(distance)[[i]];near<-as.logical(distance[,i]);cis[[length(cis)+1L]]<-list(gene=gene,peaks=all.peaks[near])
    if(sum(near)<2)next
    gene.expression<-Matrix::t(expression[gene,,drop=FALSE]);coefficients<-method(peak.data[,near,drop=FALSE],gene.expression);rownames(coefficients)<-all.peaks[near];coefficients<-coefficients[abs(coefficients)>case$score_cutoff,,drop=FALSE]
    if(nrow(coefficients)==0)next
    chromosome<-as.character(GenomeInfoDb::seqnames(coordinates[i]));prefix<-paste0('^',chromosome,if(case$profile=='1.17.1-package')'-' else '')
    pool<-all.peaks[!grepl(prefix,all.peaks)];query<-meta[rownames(coefficients),,drop=FALSE]
    background<-lapply(seq_len(nrow(query)),function(j)env$MatchRegionStats(meta.feature=meta[pool,,drop=FALSE],query.feature=query[j,,drop=FALSE],features.match=features.match,n=case$n_sample,verbose=FALSE))
    bg.data<-peak.data[,unlist(background),drop=FALSE];bg.correlation<-method(bg.data,gene.expression)
    for(j in seq_len(nrow(coefficients))){
      values<-as.vector(bg.correlation[(((j-1)*case$n_sample)+1):(j*case$n_sample),]);z<-(coefficients[j]-mean(values))/stats::sd(values);p<-stats::pnorm(-abs(z))
      pairs[[length(pairs)+1L]]<-list(gene=gene,peak=rownames(coefficients)[[j]],coefficient=unname(coefficients[j]),background=unname(background[[j]]),background_coefficients=values,zscore=unname(z),pvalue=unname(p),retained=unname(p<case$pvalue_cutoff),pool=unname(pool))
    }
  }
  list(genes=colnames(distance),peaks=all.peaks,peak_positive=unname(positive.peaks),gene_positive=unname(positive.genes),cis=cis,pairs=pairs,seed_after=.Random.seed)
}
