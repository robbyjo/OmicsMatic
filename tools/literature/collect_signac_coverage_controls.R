# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original source functions execute unchanged in an explicit current dependency namespace.
args<-commandArgs(TRUE);stopifnot(length(args)==1L,!dir.exists(args[[1]]));out<-args[[1]];dir.create(out)
.libPaths(c(normalizePath('build/literature-review/signac-runtime/reference-setup-1/R-lib'),normalizePath('build/omics-sctransform-r'),.Library),include.site=FALSE)
stopifnot(as.character(getRversion())=='4.6.1',as.character(packageVersion('Signac'))=='1.17.1',Sys.setlocale('LC_COLLATE','C')=='C')
RNGkind('Mersenne-Twister','Inversion','Rejection')
capture<-function(f){warnings<-character();value<-tryCatch(withCallingHandlers(f(),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')}),error=function(e)list(error=conditionMessage(e)));list(value=value,warnings=as.list(warnings))}
vector<-function(x)as.list(unname(x))
frame<-function(x){if(is.null(x))return(NULL);lapply(seq_len(nrow(x)),function(i)as.list(x[i,,drop=FALSE]))}
matrix.result<-function(x)list(cells=vector(rownames(x)),positions=vector(as.numeric(colnames(x))),values=lapply(seq_len(nrow(x)),function(i)vector(as.numeric(x[i,]))))
selected<-c('CoveragePlot','SingleCoveragePlot','CoverageTrack','AnnotationPlot','PeakPlot','CombineTracks','theme_browser','reformat_annotations','record_overlapping','split_body','CutMatrix','SingleFileCutMatrix','GetReadsInRegion','TabixOutputToDataFrame','ApplyMatrixByGroup','AverageCounts','CellsPerGroup','GetGroups','FindRegion')
envs<-list();sources<-list()
for(v in c('1.9.0','1.14.0','1.17.1')){
 e<-new.env(parent=asNamespace('Signac'));folder<-file.path(out,v);dir.create(folder)
 for(file in c('utilities.R','visualization.R')){
  path<-paste0('build/literature-review/signac-runtime/source-intake-1/',v,'/source/Signac/R/',file)
  file.copy(path,file.path(folder,file));expressions<-parse(path,keep.source=FALSE)
  for(node in expressions)if(is.call(node)&&identical(node[[1]],as.name('<-'))&&is.symbol(node[[2]])&&as.character(node[[2]])%in%selected&&is.call(node[[3]])&&identical(node[[3]][[1]],as.name('function')))eval(node,e)
 }
 stopifnot(all(vapply(selected,function(n)exists(n,e,inherits=FALSE),logical(1))))
 for(n in selected)writeLines(deparse(get(n,e),width.cutoff=500),file.path(folder,paste0(n,'.R')))
 envs[[v]]<-e;sources[[v]]<-list(functions=as.list(selected),namespace_parent='Signac1.17.1',historical_whole_environment=FALSE)
}
# Standalone R sample.int controls include sequential group draws after each seed reset.
sampling<-list();for(n in c(1L,2L,3L,7L,16L,21L,100L,301L,3001L,10000L,200000L))for(size in unique(c(0L,1L,min(n,5L),n%/%2L,n))){
 if(size>10000L)next
 set.seed(1234);answer<-lapply(seq_len(3L),function(i)vector(sample.int(n,size=size,replace=FALSE)))
 sampling[[length(sampling)+1L]]<-list(n=n,size=size,seed=1234,draws=answer)
}
# Group aggregation, source rolling sums, fixed-seed sampled order and clipping.
coverage<-list();set.seed(7123)
for(i in seq_len(95L)){
 n<-sample(c(5L,10L,21L,31L,100L,301L),1);nc<-sample(2:7,1);m<-matrix(sample(0:9,n*nc,replace=TRUE),nrow=nc)
 cells<-paste0('c',seq_len(nc));dimnames(m)<-list(cells,seq.int(100L,length.out=n));groups<-setNames(rep(c('B','A','C'),length.out=nc),cells)
 factors<-c(A=100,B=300,C=50);sf<-median(factors);window<-if(i%%5L==0L)NA_integer_ else sample(c(1L,2L,3L,4L,5L,10L,n+1L),1)
 rate<-c(0,0.1,0.5,1)[(i%%4L)+1L];maxn<-c(3L,10L,3000L)[(i%%3L)+1L];ymax<-list(NULL,'q95',0.25,2,'q50')[[i%%5L+1L]]
 input<-list(id=paste0('random-',i),cells=vector(cells),groups=vector(groups),counts=lapply(seq_len(nc),function(j)vector(m[j,])),start=100L,end=100L+n-1L,
             group_scale_factors=as.list(factors),scale_factor=sf,window=if(is.na(window))NULL else window,downsample_rate=rate,maximum_downsample=maxn,ymax=ymax)
 for(v in names(envs)){
  e<-envs[[v]];result<-capture(function(){p<-e$CoverageTrack(cutmat=list(peaks=Matrix::Matrix(m,sparse=TRUE)),region=GenomicRanges::GRanges('chr1',IRanges::IRanges(100L,100L+n-1L)),group.scale.factors=list(factors),scale.factor=list(sf),window=window,ymax=ymax,split.assays=FALSE,assay.scale='common',obj.groups=groups,downsample.rate=rate,max.downsample=maxn);list(rows=frame(p$data),y_limits=vector(p$scales$get_scales('y')$limits),y_label=p$labels$y)})
  coverage[[length(coverage)+1L]]<-list(source_version=v,input=input,result=result)
 }
}
# Exact native normalization populations using complete original object metadata helpers.
scaling<-list();for(groups in list(c('B','A','B','A'),c('B','A','C'),c('A','A'),factor(c('B','A','B'),levels=c('C','B','A')),c('A',NA,'B'))){
 cells<-paste0('c',seq_along(groups));counts<-seq_along(groups)*17;assay<-Matrix::Matrix(rbind(counts,rep(1,length(counts))),sparse=TRUE);dimnames(assay)<-list(c('p1','p2'),cells)
 obj<-SeuratObject::CreateSeuratObject(counts=assay,assay='peaks');obj$group<-groups;names(groups)<-cells
 for(v in names(envs)){
  e<-envs[[v]];r<-capture(function(){reads<-e$AverageCounts(obj,assay='peaks',group.by='group',verbose=FALSE);nc<-e$CellsPerGroup(obj,group.by='group');factors<-suppressWarnings(reads*nc);list(average=as.list(reads),average_names=vector(names(reads)),cell_counts=vector(nc),cell_count_names=vector(names(nc)),factors=vector(factors),factor_names=vector(names(factors)),default_scale_factor=median(factors),object_nCounts=vector(obj$nCount_peaks))})
  scaling[[length(scaling)+1L]]<-list(source_version=v,input=list(cells=vector(cells),groups=vector(as.character(groups)),groups_missing=vector(is.na(groups)),levels=if(is.factor(groups))vector(levels(groups))else NULL,n_counts=vector(obj$nCount_peaks)),result=r)
 }
}
# Original SingleFileCutMatrix on sorted, indexed fragment records, including boundary/support cases.
text<-'chr1\t98\t103\ta\t1\nchr1\t99\t104\tb\t99\nchr1\t100\t105\ta\t1\nchr1\t101\t106\tb\t0\nchr1\t102\t107\ta\t3\nchr1\t102\t107\ta\t3\nchr1\t119\t122\ta\t1\nchr1\t120\t123\tb\t1\nchr2\t10\t20\ta\t1\n'
plain<-file.path(out,'fragments.tsv');con<-file(plain,'wb');writeBin(charToRaw(text),con);close(con);gz<-paste0(plain,'.gz');Rsamtools::bgzip(plain,dest=gz);Rsamtools::indexTabix(gz,format='bed')
cut<-list();for(v in names(envs))for(mapped in c(FALSE,TRUE)){
 e<-envs[[v]];cells<-if(mapped)c('objectB','objectA','absent')else c('b','a','absent');mapping<-setNames(c('b','a','missing'),cells)
 r<-capture(function(){tab<-Rsamtools::TabixFile(gz);open(tab);on.exit(close(tab));matrix.result(e$SingleFileCutMatrix(cellmap=mapping,region=GenomicRanges::GRanges('chr1',IRanges::IRanges(100L,120L)),cells=cells,tabix.file=tab,verbose=FALSE))})
 cut[[length(cut)+1L]]<-list(source_version=v,input=list(fragments=text,cells=vector(cells),map=as.list(mapping),start=100L,end=120L),result=r)
}
# Original annotation layout retains exon/gene selection, source grouping/ordering and arrows.
annotations<-list();set.seed(8431)
for(i in seq_len(45L)){
 ng<-sample(1:8,1);genes<-paste0('gene',seq_len(ng));ann<-data.frame()
 for(g in genes){s<-sample(50:1100,1);for(tx in 1:2)for(exon in 1:2){st<-s+(exon-1L)*sample(30:150,1);ann<-rbind(ann,data.frame(chromosome='chr1',start=st,end=st+sample(5:50,1),strand=sample(c('+','-','*'),1),gene_name=g,tx_id=paste0(g,'_tx',tx),gene_biotype='protein_coding',type='exon'))}}
 gr<-GenomicRanges::GRanges(ann$chromosome,IRanges::IRanges(ann$start,ann$end),strand=ann$strand);S4Vectors::mcols(gr)<-ann[,c('gene_name','tx_id','gene_biotype','type')]
 for(collapse in c(TRUE,FALSE))for(v in names(envs)){
  e<-envs[[v]];r<-capture(function(){a<-gr;region<-GenomicRanges::GRanges('chr1',IRanges::IRanges(100L,1100L));subset<-get('subsetByOverlaps',asNamespace('Signac'))(a,region);key<-if(collapse)'gene_name' else 'tx_id';keep<-unique(S4Vectors::mcols(subset)[[key]]);a<-a[S4Vectors::mcols(a)[[key]]%in%keep];if(!length(a))return(list(empty=TRUE));value<-e$reformat_annotations(a,100L,1100L,collapse);lapply(value,frame)})
  annotations[[length(annotations)+1L]]<-list(source_version=v,input=list(annotation=frame(ann),start=100L,end=1100L,collapse_transcripts=collapse),result=r)
 }
}
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
jsonlite::write_json(list(status='original-source-profile-coverage-annotation-and-R-sampling-controls',sources=sources,
                         versions=list(R=as.character(getRversion()),Signac=as.character(packageVersion('Signac')),RcppRoll=as.character(packageVersion('RcppRoll')),dplyr=as.character(packageVersion('dplyr')),ggplot2=as.character(packageVersion('ggplot2'))),
                         sampling=sampling,coverage=coverage,scaling=scaling,cut=cut,annotations=annotations),file.path(out,'RESULT.json'),pretty=TRUE,auto_unbox=TRUE,digits=17,na='string',null='null')
cat('COMPLETE_ORIGINAL_COVERAGE',length(coverage),length(annotations),length(sampling),'\n')
