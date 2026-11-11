# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent original object, pruning and complete CoveragePlot controls.
args<-commandArgs(TRUE);stopifnot(length(args)==1L,!dir.exists(args[[1]]));out<-args[[1]];dir.create(out)
.libPaths(c(normalizePath('build/literature-review/signac-runtime/reference-setup-1/R-lib'),normalizePath('build/omics-sctransform-r'),.Library),include.site=FALSE)
stopifnot(as.character(getRversion())=='4.6.1',Sys.setlocale('LC_COLLATE','C')=='C')
capture<-function(f){warnings<-character();value<-tryCatch(withCallingHandlers(f(),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')}),error=function(e)list(error=conditionMessage(e)));list(value=value,warnings=as.list(warnings))}
vec<-function(x)as.list(unname(x));frame<-function(x)lapply(seq_len(nrow(x)),function(i)as.list(x[i,,drop=FALSE]))
base<-'build/literature-review/signac-runtime/coverage-original-2/original'
selected<-c('CoveragePlot','SingleCoveragePlot','CoverageTrack','AnnotationPlot','PeakPlot','CombineTracks','theme_browser','reformat_annotations','record_overlapping','split_body','CutMatrix','SingleFileCutMatrix','GetReadsInRegion','TabixOutputToDataFrame','ApplyMatrixByGroup','AverageCounts','CellsPerGroup','GetGroups','FindRegion')
envs<-list();for(v in c('1.9.0','1.14.0','1.17.1')){e<-new.env(parent=asNamespace('Signac'));for(file in c('utilities.R','visualization.R'))for(node in parse(file.path(base,v,file),keep.source=FALSE))if(is.call(node)&&identical(node[[1]],as.name('<-'))&&is.symbol(node[[2]])&&as.character(node[[2]])%in%selected&&is.call(node[[3]])&&identical(node[[3]][[1]],as.name('function')))eval(node,e);envs[[v]]<-e}
cells<-c('objectB','objectA','absent');mapping<-setNames(c('b','a','missing'),cells)
counts<-Matrix::Matrix(matrix(c(35,65,20,180,50,300),nrow=2),sparse=TRUE);dimnames(counts)<-list(c('chr1-100-110','chr1-115-125'),cells)
ann<-GenomicRanges::GRanges('chr1',IRanges::IRanges(c(101L,111L),c(108L,119L)),strand='+');ann$gene_id<-'g1';ann$gene_name<-'GeneA';ann$tx_id<-'tx1';ann$gene_biotype<-'protein_coding';ann$type<-'exon'
fragment<-Signac::CreateFragmentObject(path=normalizePath(file.path(base,'fragments.tsv.gz'),winslash='/'),cells=mapping,validate.fragments=FALSE,verbose=FALSE)
make.object<-function(frags=list(fragment)){
 assay<-Signac::CreateChromatinAssay(counts=counts,ranges=GenomicRanges::GRanges('chr1',IRanges::IRanges(c(100L,115L),c(110L,125L))),fragments=frags,annotation=ann,min.cells=0,min.features=0)
 obj<-SeuratObject::CreateSeuratObject(counts=assay,assay='peaks');obj$group<-c('B','A','B');obj
}
obj<-make.object();region<-GenomicRanges::GRanges('chr1',IRanges::IRanges(100L,120L));full<-list();scaling<-list()
for(v in names(envs)){
 e<-envs[[v]];groups<-c('B','A','B');names(groups)<-cells
 for(levels in list(NULL,c('C','B','A'))){object<-obj;if(!is.null(levels))object$group<-factor(object$group,levels=levels)
  r<-capture(function(){means<-e$AverageCounts(object,assay='peaks',group.by='group',verbose=FALSE);nc<-e$CellsPerGroup(object,group.by='group');factors<-suppressWarnings(means*nc);list(average=vec(means),average_names=vec(names(means)),cells=vec(nc),names=vec(names(nc)),factors=vec(factors),scale_factor=median(factors))})
  scaling[[length(scaling)+1L]]<-list(version=v,cells=vec(cells),groups=vec(groups),n_counts=vec(obj$nCount_peaks),levels=if(is.null(levels))NULL else as.list(levels),result=r)
 }
 r<-capture(function(){cut<-e$CutMatrix(obj,region,assay='peaks',cells=cells,verbose=FALSE);colnames(cut)<-GenomicRanges::start(region):GenomicRanges::end(region);reads<-e$AverageCounts(obj,assay='peaks',group.by='group',verbose=FALSE);nc<-e$CellsPerGroup(obj,group.by='group');factors<-suppressWarnings(reads*nc);sf<-median(factors);gr<-e$GetGroups(obj,group.by='group',idents=NULL)
  track<-e$CoverageTrack(list(peaks=cut),region,list(factors),list(sf),assay.scale='common',obj.groups=gr,ymax=NULL,downsample.rate=1,window=1,max.downsample=3000)
  plot<-e$CoveragePlot(obj,region=region,assay='peaks',annotation=TRUE,peaks=FALSE,links=FALSE,group.by='group',window=1,downsample.rate=1,max.downsample=3000)
  # CoveragePlot wraps one SingleCoveragePlot, which itself wraps coverage + annotation.
  complete.data<-plot[[1]][[1]]$data;stopifnot(identical(complete.data,track$data))
  target<-file.path(out,paste0(v,'-original.png'));grDevices::png(target,width=1200,height=650,res=120);tryCatch(print(plot),finally=grDevices::dev.off())
  list(cut=lapply(seq_len(nrow(cut)),function(i)vec(as.numeric(cut[i,]))),rows=frame(complete.data),source_y_label=track$labels$y,source_n_counts=vec(obj$nCount_peaks),plot_saved=basename(target),rows_from_complete_plot=TRUE,direct_track_rows_identical=TRUE)
 });full[[length(full)+1L]]<-list(version=v,result=r)
}
# A missing chromosome empties region persistently for subsequent sources in original CutMatrix.
plain<-file.path(out,'chr2.tsv');writeLines('chr2\t10\t20\ta\t1',plain);Rsamtools::bgzip(plain,paste0(plain,'.gz'));Rsamtools::indexTabix(paste0(plain,'.gz'),format='bed')
missing<-Signac::CreateFragmentObject(path=normalizePath(paste0(plain,'.gz'),winslash='/'),cells=mapping,validate.fragments=FALSE,verbose=FALSE)
pruning<-list();for(v in names(envs))for(order in list(c('present','missing','present'),c('missing','present'))){
 object<-make.object(lapply(order,function(x)if(x=='present')fragment else missing));e<-envs[[v]];r<-capture(function(){x<-e$CutMatrix(object,region,assay='peaks',cells=cells,verbose=FALSE);if(is.null(x))return(list(null=TRUE));list(cut=lapply(seq_len(nrow(x)),function(i)vec(as.numeric(x[i,]))))});pruning[[length(pruning)+1L]]<-list(version=v,order=as.list(order),result=r)
}
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
jsonlite::write_json(list(status='original-complete-coverage-object-and-pruning-controls',cells=vec(cells),map=as.list(mapping),groups=as.list(c('B','A','B')),fragments=readChar(file.path(base,'fragments.tsv'),nchars=file.info(file.path(base,'fragments.tsv'))$size),region=list(chromosome='chr1',start=100,end=120),annotation=frame(as.data.frame(ann)),scaling=scaling,full=full,pruning=pruning,historical_whole_environments=FALSE),file.path(out,'RESULT.json'),pretty=TRUE,auto_unbox=TRUE,digits=17,na='string',null='null')
cat('COMPLETE_ORIGINAL_COVERAGE_EDGES',length(full),length(scaling),length(pruning),'\n')
