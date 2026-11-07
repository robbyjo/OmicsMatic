# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Capture original chromosome ordering and grouped range reduction, not peak inference.
args<-commandArgs(TRUE);stopifnot(length(args)==1L,!dir.exists(args[[1]]));out<-args[[1]];dir.create(out)
.libPaths(c(normalizePath('build/literature-review/signac-runtime/reference-setup-1/R-lib'),normalizePath('build/omics-sctransform-r'),.Library),include.site=FALSE)
stopifnot(Sys.setlocale('LC_COLLATE','C')=='C');owner<-asNamespace('Seqinfo');gns<-asNamespace('GenomicRanges')
for(name in c('rankSeqlevels','orderSeqlevels','.hasPrefix','.dropPrefix','.isShortNb','.getNbPart','.getPostNbPart','isRoman','.merge_two_Seqinfo_objects'))if(exists(name,owner,inherits=FALSE))writeLines(deparse(get(name,owner),width.cutoff=500),file.path(out,paste0(name,'.R')))
writeLines(deparse(utils:::.roman2numeric,width.cutoff=500),file.path(out,'R-roman2numeric.R'))
writeLines(deparse(GenomicRanges::makeGRangesFromDataFrame,width.cutoff=500),file.path(out,'makeGRangesFromDataFrame.R'))
constants<-list();for(name in c('.REGEXP0','.REGEXP1'))constants[[name]]<-get(name,owner)
metadata<-packageDescription('Seqinfo');writeLines(capture.output(metadata),file.path(out,'Seqinfo-DESCRIPTION.txt'))
universe<-c('chr1','chr2','chr10','chrX','chrY','chrM','chrMT','chrW','chrZ','chrU','chrI','chrII','chrIX','chrXI','chr01','chr0','chr2A','chr2a','chr2B','chr2b','chr2L','chr2R','chr2_random','chr10_random','chr2.1','chr2.10','chr2.2','CHR1','CHR10','CH1','ch1','1','2','10','I','II','X','Y','M','MT','GL0001.1','GL0002.1','scaffold1','scaffold10','scaffold2','chr999999','chr1000000','chrX_random','chrM_random','chrMT_random','chrUnknown','chrUn','chrUn_KI1','Chr1','chrIV','chrIIII','chrXX','chrXXX','chrVI','chrIVX','chr','CHR','CH','ch','chr000001','chr123456A','chr123456aa','chr12.1','chr12.2')
vectors<-list(universe,c('X','I','II'),c('X','Y','I','II'),c('chrX','chrI','chrII'),c('chrX','chr1','chrI'),character(),c('chrX','chrX','chrI'),c('chr2','chr02','chr002','chr000002'))
vectors[[length(vectors)+1L]]<-c('chr1','chr2',paste0('chr999999tag_',seq_len(3000L)),'chrX','chrY','chrM','scaffold1')
vectors[[length(vectors)+1L]]<-c('CHRMMMMMMMMM',paste0('chr999999tag_',seq_len(2147L)),'chrM','chrMT','1','2')
set.seed(7923);for(i in seq_len(600L))vectors[[length(vectors)+1L]]<-sample(universe,sample(seq_along(universe),1L),replace=FALSE)
ranking<-list()
for(i in seq_along(vectors))for(sex in list(NA,TRUE,FALSE)){
 x<-vectors[[i]];warnings<-character();answer<-withCallingHandlers(Seqinfo::rankSeqlevels(x,X.is.sexchrom=sex),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')})
 ranking[[length(ranking)+1L]]<-list(names=as.list(x),X_is_sexchrom=if(is.na(sex))'auto' else if(sex)'true' else 'false',ranks=as.list(answer),warnings=as.list(warnings))
}
groups<-list(
 list(list(name='B',peaks=list(list('chr2',11,20),list('chr1',101,120))),list(name='A',peaks=list(list('chr1',90,105),list('chr2',21,25),list('chr1',110,130)))),
 list(list(name='first',peaks=list(list('chr1',1,10),list('chr10',3,8))),list(name='second',peaks=list(list('chr2',1,10),list('chrX',2,20)))),
 list(list(name='B',peaks=list(list('chr1',1,10),list('chr1',30,40))),list(name='A',peaks=list(list('chr1',1,10),list('chr1',31,35)))),
 list(list(name='first',peaks=list(list('chrX',1,10),list('chrII',1,10))),list(name='second',peaks=list(list('chrY',1,10),list('chrII',5,15),list('chrX',5,15)))),
 list(list(name='empty',peaks=list()),list(name='A',peaks=list(list('chr1',1,10)))),
 list(list(name='only_empty',peaks=list())),list(),
 list(list(name='first',peaks=list(list('chrX',1,10),list('chrXX',1,10))),list(name='second',peaks=list(list('chrY',1,10),list('chrXX',5,15),list('chrX',5,15)))))
for(i in seq_len(200L)){
 ng<-sample(1:5,1);gs<-list();for(g in seq_len(ng)){np<-sample(0:12,1);ps<-list();if(np>0)for(j in seq_len(np)){start<-sample(1:100,1);ps[[j]]<-list(sample(c('chr1','chr2','chr10','chrX','chrY','chrI','chrII'),1),start,start+sample(1:30,1))};gs[[g]]<-list(name=paste0('group',g),peaks=ps)};groups[[length(groups)+1L]]<-gs
}
combined<-list()
for(gs in groups){
 warnings<-character();grs<-lapply(gs,function(g){frame<-data.frame(chr=vapply(g$peaks,function(p)p[[1]],character(1)),start=vapply(g$peaks,function(p)p[[2]],numeric(1)),end=vapply(g$peaks,function(p)p[[3]],numeric(1)));gr<-GenomicRanges::makeGRangesFromDataFrame(frame);gr$ident<-rep(g$name,length(gr));gr})
 answer<-tryCatch(withCallingHandlers({joined<-Reduce(c,grs);reduced<-GenomicRanges::reduce(joined,with.revmap=TRUE);frame<-as.data.frame(reduced);labels<-vapply(reduced$revmap,function(indices)paste(unique(joined$ident[indices]),collapse=','),character(1));list(seqlevels=as.list(Seqinfo::seqlevels(joined)),rows=lapply(seq_along(reduced),function(i)list(chromosome=as.character(frame$seqnames)[i],start=frame$start[i],end=frame$end[i],groups=labels[i])))},warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')}),error=function(e)list(error=conditionMessage(e)))
 combined[[length(combined)+1L]]<-list(groups=gs,result=answer,warnings=as.list(warnings))
}
stopifnot(combined[[1]]$result$rows[[1]]$start==90L,combined[[1]]$result$rows[[1]]$end==130L)
jsonlite::write_json(list(status='original-Seqinfo-and-GenomicRanges-peak-ordering-references',versions=list(R=as.character(getRversion()),Seqinfo=as.character(packageVersion('Seqinfo')),GenomeInfoDb=as.character(packageVersion('GenomeInfoDb')),GenomicRanges=as.character(packageVersion('GenomicRanges'))),constants=constants,ranking=ranking,combined=combined),file.path(out,'RESULT.json'),auto_unbox=TRUE,pretty=TRUE,digits=17,na='string')
cat('ORIGINAL_PEAK_ORDERING_CONTROLS_COMPLETE\n')
