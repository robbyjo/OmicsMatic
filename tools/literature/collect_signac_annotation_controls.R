# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==3L)
.libPaths(c(normalizePath('build/literature-review/signac-runtime/reference-setup-1/R-lib'),normalizePath('build/omics-sctransform-r'),.Library),include.site=FALSE)
stopifnot(requireNamespace('Signac',quietly=TRUE),as.character(packageVersion('Signac'))=='1.17.1')
input<-jsonlite::read_json(args[[1]],simplifyVector=FALSE);out<-args[[2]]
capture<-function(f){warnings<-character();value<-tryCatch(withCallingHandlers(f(),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')}),error=function(e)list(error=conditionMessage(e)));list(value=value,warnings=warnings)}
kind<-function(x)ifelse(is.nan(x),'NAN',ifelse(is.na(x),'NA',ifelse(is.infinite(x),'POSITIVE_INFINITY','FINITE')))
ranges<-function(rows,seqlengths=NULL){
  result<-GenomicRanges::GRanges(seqnames=vapply(rows,`[[`,character(1),'chromosome'),ranges=IRanges::IRanges(start=vapply(rows,`[[`,numeric(1),'start'),end=vapply(rows,`[[`,numeric(1),'end')),strand=vapply(rows,`[[`,character(1),'strand'))
  if(!is.null(seqlengths)){values<-GenomeInfoDb::seqlengths(result);for(name in names(seqlengths))values[name]<-seqlengths[[name]];GenomeInfoDb::seqlengths(result)<-values}
  result
}
range_result<-function(x){list(chromosomes=as.character(GenomeInfoDb::seqnames(x)),start=GenomicRanges::start(x),end=GenomicRanges::end(x),strand=as.character(GenomicRanges::strand(x)),metadata=as.list(S4Vectors::mcols(x)))}
matrix_result<-function(x){x<-as(x,'dgCMatrix');list(features=rownames(x),cells=colnames(x),p=as.vector(x@p),i=as.vector(x@i),x=as.vector(x@x))}
files<-list()
for(file in input$files){plain<-file.path(out,paste0(file$id,'.tsv'));compressed<-paste0(plain,'.gz');Rsamtools::bgzip(plain,dest=compressed,overwrite=FALSE);Rsamtools::indexTabix(compressed,format='bed');files[[file$id]]<-compressed}
fragments<-function(sources)lapply(sources,function(source){map<-if(is.null(source$map))NULL else setNames(vapply(source$map,`[[`,character(1),'barcode'),vapply(source$map,`[[`,character(1),'cell'));Signac::CreateFragmentObject(files[[source$file]],cells=map,validate.fragments=FALSE,verbose=FALSE)})
object<-function(case,annotation=NULL){cells<-unlist(case$cells);counts<-Matrix::Matrix(matrix(1,nrow=2,ncol=length(cells)),sparse=TRUE);dimnames(counts)<-list(c('chr1-1-2','chr1-3-4'),cells);assay<-Signac::CreateChromatinAssay(counts=counts,ranges=GenomicRanges::GRanges(c('chr1','chr1'),IRanges::IRanges(c(1,3),c(2,4))),fragments=fragments(case$sources),annotation=annotation,min.cells=0,min.features=0);SeuratObject::CreateSeuratObject(counts=assay,assay='peaks')}
tss<-list()
for(case in input$tss_cases){
  result<-capture(function(){
    positions<-ranges(case$positions,case$seqlengths);obj<-object(case)
    chosen<-positions;if(!is.null(case$n))chosen<-chosen[1:min(case$n,length(chosen))];chosen<-chosen[!as.character(GenomeInfoDb::seqnames(chosen))%in%c('chrM','Mt','MT')]
    extensions<-list(upstream=capture(function()range_result(Signac:::Extend(chosen,upstream=case$extension,downstream=-(case$extension-99),from.midpoint=TRUE))),downstream=capture(function()range_result(Signac:::Extend(chosen,upstream=-(case$extension-99),downstream=case$extension,from.midpoint=TRUE))),centers=capture(function()range_result(Signac:::Extend(chosen,upstream=500,downstream=500,from.midpoint=TRUE))),full=capture(function()range_result(Signac:::Extend(chosen,upstream=case$extension,downstream=case$extension,from.midpoint=TRUE))))
    fit<-Signac::TSSEnrichment(obj,tss.positions=positions,n=case$n,fast=case$fast,process_n=case$process_n,region_extension=case$extension,cells=if(is.null(case$selected_cells))NULL else unlist(case$selected_cells),verbose=FALSE)
    metadata<-fit[[]];profile<-if(case$fast)NULL else as.matrix(methods::slot(fit[['peaks']],'positionEnrichment')$TSS)
    raw<-if(case$fast)NULL else Signac:::CreateRegionPileupMatrix(obj,regions=Signac:::Extend(chosen,upstream=case$extension,downstream=case$extension,from.midpoint=TRUE),verbose=FALSE)
    list(cells=rownames(metadata),score=unname(metadata$TSS.enrichment),score_kind=kind(metadata$TSS.enrichment),percentile=unname(metadata$TSS.percentile),percentile_kind=kind(metadata$TSS.percentile),selected_positions=range_result(chosen),extensions=extensions,profile=if(is.null(profile))NULL else list(cells=rownames(profile),positions=colnames(profile),values=unname(profile),kinds=unname(kind(profile))),raw_profile=if(is.null(raw))NULL else list(cells=rownames(raw),values=unname(as.matrix(raw))),attached_maps=lapply(Signac::Fragments(fit[['peaks']]),function(f)Signac::GetFragmentData(f,slot='cells')))
  });tss[[length(tss)+1L]]<-list(id=case$id,result=result);cat('TSS',case$id,'\n')
}
gene_activity<-list()
for(case in input$gene_cases){
  result<-capture(function(){
    annotation<-ranges(case$annotation,case$seqlengths);annotation$gene_id<-vapply(case$annotation,`[[`,character(1),'gene_id');annotation$gene_name<-vapply(case$annotation,function(x)if(is.null(x$gene_name))NA_character_ else x$gene_name,character(1));annotation$gene_biotype<-vapply(case$annotation,`[[`,character(1),'gene_biotype');annotation$type<-rep('transcript',length(annotation));annotation$tx_id<-paste0('tx',seq_along(annotation));obj<-object(case,annotation)
    replaced<-annotation;replaced$gene_name<-ifelse(is.na(replaced$gene_name)|replaced$gene_name=='',replaced$gene_id,replaced$gene_name)
    collapsed<-capture(function()range_result(Signac:::CollapseToLongestTranscript(replaced)));tss_positions<-capture(function()range_result(Signac:::GetTSSPositions(annotation)))
    value<-Signac::GeneActivity(obj,features=if(is.null(case$features))NULL else as.character(unlist(case$features)),biotypes=if(is.null(case$biotypes))NULL else as.character(unlist(case$biotypes)),max.width=case$max_width,extend.upstream=case$upstream,extend.downstream=case$downstream,process_n=case$process_n,gene.id=case$gene_id,verbose=FALSE)
    list(matrix=matrix_result(value),collapsed=collapsed,tss_positions=tss_positions)
  });gene_activity[[length(gene_activity)+1L]]<-list(id=case$id,result=result);cat('GENE',case$id,'\n')
}
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
jsonlite::write_json(list(status='captured-full-original-Signac-TSS-and-gene-activity-controls',package='1.17.1',historical_binaries_executed=FALSE,tss=tss,gene_activity=gene_activity),args[[3]],pretty=TRUE,auto_unbox=TRUE,digits=17,na='string',null='null',matrix='rowmajor')
cat('COMPLETE_ORIGINAL_ANNOTATION_CONTROLS',length(tss),length(gene_activity),'\n')
