# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Run the literal synthetic example populations/settings through original composed plots.
args<-commandArgs(TRUE);stopifnot(length(args)==1L,!dir.exists(args[[1]]));out<-args[[1]];dir.create(out)
.libPaths(c(normalizePath('build/literature-review/signac-runtime/reference-setup-1/R-lib'),normalizePath('build/omics-sctransform-r'),.Library),include.site=FALSE)
stopifnot(as.character(getRversion())=='4.6.1',Sys.setlocale('LC_COLLATE','C')=='C')
capture<-function(f){warnings<-character();value<-tryCatch(withCallingHandlers(f(),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')}),error=function(e)list(error=conditionMessage(e)));list(value=value,warnings=as.list(warnings))}
frame<-function(x)lapply(seq_len(nrow(x)),function(i)as.list(x[i,,drop=FALSE]))
envs<-list();selected<-c('CoveragePlot','SingleCoveragePlot','CoverageTrack','AnnotationPlot','PeakPlot','CombineTracks','theme_browser','reformat_annotations','record_overlapping','split_body','CutMatrix','SingleFileCutMatrix','GetReadsInRegion','TabixOutputToDataFrame','ApplyMatrixByGroup','AverageCounts','CellsPerGroup','GetGroups','FindRegion')
for(v in c('1.9.0','1.14.0','1.17.1')){e<-new.env(parent=asNamespace('Signac'));for(file in c('utilities.R','visualization.R'))for(node in parse(file.path('build/literature-review/signac-runtime/coverage-original-2/original',v,file),keep.source=FALSE))if(is.call(node)&&identical(node[[1]],as.name('<-'))&&is.symbol(node[[2]])&&as.character(node[[2]])%in%selected&&is.call(node[[3]])&&identical(node[[3]][[1]],as.name('function')))eval(node,e);envs[[v]]<-e}
examples<-'examples/signac-coverage';results<-list()
for(name in c('gene','transcript','peak-assay')){
 r<-jsonlite::read_json(file.path(examples,paste0(name,'-request.json')),simplifyVector=FALSE);v<-sub('Signac-','',r$source_profile);e<-envs[[v]];folder<-file.path(out,name);dir.create(folder)
 result<-capture(function(){
  metadata<-read.delim(file.path(examples,r$cell_metadata),check.names=FALSE,quote='',comment.char='',stringsAsFactors=FALSE);stopifnot(identical(names(metadata),c('cell','group','n_count')))
  cells<-metadata$cell;count<-rbind(floor(metadata$n_count/2),metadata$n_count-floor(metadata$n_count/2));dimnames(count)<-list(c('chr1-1-2','chr1-3-4'),cells)
  fragments<-list();for(i in seq_along(r$sources)){
   source<-r$sources[[i]];file<-file.path(folder,paste0('s',i,'.tsv.gz'));Rsamtools::bgzip(file.path(examples,source$fragments),dest=file);Rsamtools::indexTabix(file,format='bed')
   map<-if(is.null(source$map))setNames(cells,cells)else{m<-read.delim(file.path(examples,source$map),check.names=FALSE,quote='',comment.char='',stringsAsFactors=FALSE);setNames(m$barcode,m$cell)}
   fragments[[i]]<-Signac::CreateFragmentObject(path=normalizePath(file,winslash='/'),cells=map,validate.fragments=FALSE,verbose=FALSE)
  }
  a<-read.delim(file.path(examples,r$annotation),check.names=FALSE,quote='',comment.char='',stringsAsFactors=FALSE);annotation<-GenomicRanges::GRanges(a$chromosome,IRanges::IRanges(a$start,a$end),strand=a$strand);annotation$gene_id<-a$gene_name;annotation$gene_name<-a$gene_name;annotation$tx_id<-a$tx_id;annotation$gene_biotype<-a$gene_biotype;annotation$type<-a$type
  assay<-Signac::CreateChromatinAssay(counts=Matrix::Matrix(count,sparse=TRUE),ranges=GenomicRanges::GRanges('chr1',IRanges::IRanges(c(1L,3L),c(2L,4L))),fragments=fragments,annotation=annotation,min.cells=0,min.features=0)
  obj<-SeuratObject::CreateSeuratObject(counts=assay,assay='peaks');obj$group<-metadata$group;stopifnot(identical(as.numeric(obj$nCount_peaks),as.numeric(metadata$n_count)))
  region<-GenomicRanges::GRanges(r$region$chromosome,IRanges::IRanges(r$region$start,r$region$end))
  mode<-if(is.null(r$annotation_mode))'gene' else r$annotation_mode
  plot<-e$CoveragePlot(obj,region=region,assay='peaks',annotation=mode,peaks=FALSE,links=FALSE,group.by='group',window=r$window,downsample.rate=r$downsample_rate,max.downsample=3000)
  data<-plot[[1]][[1]]$data;stopifnot(all(c('position','count','norm.value','coverage','group')%in%names(data)))
  subset<-get('subsetByOverlaps',asNamespace('Signac'))(annotation,region);key<-if(mode=='gene')'gene_name' else 'tx_id';keep<-unique(S4Vectors::mcols(subset)[[key]]);selected<-annotation[S4Vectors::mcols(annotation)[[key]]%in%keep]
  layout<-e$reformat_annotations(selected,r$region$start,r$region$end,collapse_transcript=mode=='gene')
  target<-file.path(folder,'original.png');grDevices::png(target,width=1200,height=650,res=120);tryCatch(print(plot),finally=grDevices::dev.off())
  list(rows=frame(data),annotation=lapply(layout,frame),plot_saved=paste0(name,'/original.png'),n_counts=as.list(metadata$n_count),cells=as.list(cells),source_y_label=plot[[1]][[1]]$labels$y)
 });results[[length(results)+1L]]<-list(example=name,version=v,request=r,result=result)
}
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
jsonlite::write_json(list(status='original-composed-Signac-coverage-literal-example-references',historical_whole_environments=FALSE,results=results),file.path(out,'RESULT.json'),pretty=TRUE,auto_unbox=TRUE,digits=17,na='string',null='null')
stopifnot(all(vapply(results,function(x)is.null(x$result$value$error),logical(1))))
cat('COMPLETE_ORIGINAL_COVERAGE_EXAMPLES',length(results),'\n')
