# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==2L)
.libPaths(c(normalizePath('build/literature-review/signac-runtime/reference-setup-1/R-lib'),normalizePath('build/omics-sctransform-r'),.Library),include.site=FALSE)
stopifnot(requireNamespace('Signac',quietly=TRUE),as.character(packageVersion('Signac'))=='1.17.1');example<-args[[1]];out<-args[[2]]
capture<-function(f){warnings<-character();value<-tryCatch(withCallingHandlers(f(),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')}),error=function(e)list(error=conditionMessage(e)));list(value=value,warnings=warnings)}
kind<-function(x)ifelse(is.nan(x),'NAN',ifelse(is.na(x),'NA',ifelse(is.infinite(x),'POSITIVE_INFINITY','FINITE')))
annotation<-read.delim(file.path(example,'annotation.tsv'),check.names=FALSE,stringsAsFactors=FALSE,fileEncoding='UTF-8',na.strings=character());gr<-GenomicRanges::GRanges(annotation$chromosome,IRanges::IRanges(annotation$start,annotation$end),strand=annotation$strand);gr$gene_id<-annotation$gene_id;gr$gene_name<-annotation$gene_name;gr$gene_biotype<-annotation$gene_biotype;gr$type<-rep('transcript',nrow(annotation));gr$tx_id<-paste0('tx',seq_len(nrow(annotation)))
fragment<-file.path(out,'reference-fragments.tsv.gz');Rsamtools::bgzip(file.path(example,'fragments.tsv'),dest=fragment,overwrite=FALSE);Rsamtools::indexTabix(fragment,format='bed');frags<-list(Signac::CreateFragmentObject(fragment,validate.fragments=FALSE,verbose=FALSE))
object<-function(cells){counts<-Matrix::Matrix(matrix(1,nrow=2,ncol=length(cells)),sparse=TRUE);dimnames(counts)<-list(c('chr1-1-2','chr1-3-4'),cells);assay<-Signac::CreateChromatinAssay(counts=counts,ranges=GenomicRanges::GRanges(c('chr1','chr1'),IRanges::IRanges(c(1,3),c(2,4))),annotation=gr,fragments=frags,min.cells=0,min.features=0);SeuratObject::CreateSeuratObject(counts=assay,assay='peaks')}
result<-list()
for(name in c('fast','profile','derived','gene')){
  request<-jsonlite::read_json(file.path(example,paste0(name,'-request.json')),simplifyVector=FALSE);cells<-readLines(file.path(example,request$object_cells_file),encoding='UTF-8');obj<-object(cells)
  result[[name]]<-capture(function(){
    if(name=='gene'){x<-Signac::GeneActivity(obj,extend.upstream=request$upstream,extend.downstream=request$downstream,biotypes=unlist(request$biotypes),max.width=request$max_width,process_n=request$process_n,gene.id=request$gene_ids,verbose=FALSE);x<-as(x,'dgCMatrix');return(list(matrix=list(features=rownames(x),cells=colnames(x),p=as.vector(x@p),i=as.vector(x@i),x=as.vector(x@x))))}
    positions<-NULL
    if(!is.null(request$positions)){p<-read.delim(file.path(example,request$positions),check.names=FALSE,stringsAsFactors=FALSE);positions<-GenomicRanges::GRanges(p$chromosome,IRanges::IRanges(p$start,p$end),strand=p$strand)}
    fit<-Signac::TSSEnrichment(obj,tss.positions=positions,fast=request$mode=='TSS_FAST',process_n=request$process_n,region_extension=request$region_extension,verbose=FALSE);metadata<-fit[[]];profile<-if(name=='profile')as.matrix(methods::slot(fit[['peaks']],'positionEnrichment')$TSS) else NULL;raw<-if(name=='profile')Signac:::CreateRegionPileupMatrix(obj,regions=Signac:::Extend(positions[!as.character(GenomeInfoDb::seqnames(positions))%in%c('chrM','Mt','MT')],upstream=request$region_extension,downstream=request$region_extension,from.midpoint=TRUE),verbose=FALSE) else NULL
    list(cells=rownames(metadata),score=unname(metadata$TSS.enrichment),score_kind=kind(metadata$TSS.enrichment),percentile=unname(metadata$TSS.percentile),percentile_kind=kind(metadata$TSS.percentile),profile=if(is.null(profile))NULL else list(cells=rownames(profile),positions=colnames(profile),values=unname(profile)),raw_profile=if(is.null(raw))NULL else list(values=unname(as.matrix(raw))))
  })
}
stopifnot(all(vapply(result,function(r)is.null(r$value$error),logical(1))))
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'));jsonlite::write_json(list(status='fresh-full-original-Signac-checked-annotation-examples',package='1.17.1',results=result),file.path(out,'original.json'),pretty=TRUE,auto_unbox=TRUE,digits=17,na='string',null='null',matrix='rowmajor');cat('COMPLETE_ORIGINAL_CHECKED_ANNOTATION_EXAMPLES 4\n')
