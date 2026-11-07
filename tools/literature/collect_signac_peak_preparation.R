# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original C++ splitting and unmodified retained R parsing/combination branches.
args<-commandArgs(TRUE);stopifnot(length(args)==1L,!dir.exists(args[[1]]));out<-args[[1]];dir.create(out)
.libPaths(c(normalizePath('build/literature-review/signac-runtime/reference-setup-1/R-lib'),normalizePath('build/omics-sctransform-r'),.Library),include.site=FALSE)
stopifnot(as.character(getRversion())=='4.6.1',as.character(packageVersion('Signac'))=='1.17.1')
hex<-function(path)paste(sprintf('%02x',as.integer(readBin(path,'raw',n=file.info(path)$size))),collapse='')
write.raw<-function(path,text){con<-file(path,'wb');on.exit(close(con));writeBin(charToRaw(enc2utf8(text)),con)}
split.cases<-list()
run.split<-function(name,texts,cells,groups,maps=NULL){
 folder<-file.path(out,name);dir.create(folder);split<-file.path(folder,'split');dir.create(split)
 source.names<-gsub('/','_',gsub(' ','_',groups,fixed=TRUE),fixed=TRUE);unique.names<-unique(source.names);inputs<-list();warnings<-character()
 if(is.null(maps))maps<-lapply(texts,function(x)setNames(cells,cells))
 result<-tryCatch(withCallingHandlers({
  for(i in seq_along(texts)){
   path<-file.path(folder,paste0('input',i,'.tsv'));write.raw(path,texts[[i]])
   mapping<-maps[[i]];keep<-cells%in%names(mapping);raw.cells<-unname(mapping[cells[keep]])
   code<-Signac:::splitFragments(fragments=path,cells=raw.cells,idents=source.names[keep],unique_idents=unique.names,outdir=paste0(normalizePath(split,winslash='/'),'/'),suffix='',buffer_length=256L,append=TRUE,verbose=FALSE)
   stopifnot(code==0L);inputs[[i]]<-list(text=texts[[i]],map=lapply(seq_along(mapping),function(j)list(cell=names(mapping)[j],barcode=unname(mapping[j]))))
  };TRUE
 },warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')}),error=function(e)list(error=conditionMessage(e)))
 split.cases[[name]]<<-list(status=if(isTRUE(result))'original-completed' else 'original-failed',error=if(is.list(result))result$error else NULL,inputs=inputs,cells=lapply(seq_along(cells),function(i)list(cell=cells[i],group=groups[i])),source_names=as.list(unique.names),expected_hex=as.list(setNames(vapply(unique.names,function(g)hex(file.path(split,paste0(g,'.bed'))),character(1)),unique.names)),warnings=as.list(warnings))
}
base<-'# original header\nchr1\t10\t40\ta\t1\nchr1\t20\t50\tb\t100\nchr1\t20\t50\tb\t100\nchr1\t30\t80\tx\t1\n'
run.split('basic',list(base),c('b','a','unobserved'),c('B','A','C'))
run.split('mapped',list(base),c('objectB','objectA'),c('B','A'),list(c(objectB='b',objectA='a')))
run.split('multiple_sources',list(base,'chr2\t5\t18\ta\t7\nchr2\t8\t20\tb\t0\n'),c('b','a'),c('B','A'))
run.split('name_collision',list(base),c('a','b'),c('grp A','grp/A'))
run.split('six_columns',list('chr1\t0\t20\ta\t3\t+\nchr1\t30\t70\tb\t9\t-\n'),c('a','b'),c('A','B'))
run.split('crlf',list('chr1\t0\t20\ta\t3\r\nchr1\t30\t70\tb\t9\r\n'),c('a','b'),c('A','B'))
run.split('no_final_newline',list('chr1\t0\t20\ta\t3','chr2\t30\t70\ta\t9\n'),c('a','b'),c('A','B'))

source.file<-'build/literature-review/signac-runtime/source-intake-1/1.9.0/source/Signac/R/peaks.R'
env<-new.env(parent=asNamespace('Signac'));for(e in parse(source.file,keep.source=FALSE))if(is.call(e)&&identical(e[[1]],as.name('<-'))&&is.call(e[[3]])&&identical(e[[3]][[1]],as.name('function')))eval(e,env)
find.block<-function(e){if(!is.call(e))return(NULL);if(identical(e[[1]],as.name('if'))&&identical(e[[2]],as.name('combine.peaks')))return(e[[3]]);for(v in as.list(e)[-1]){r<-find.block(v);if(!is.null(r))return(r)};NULL}
block<-find.block(body(env$CallPeaks.Seurat));stopifnot(!is.null(block));combine.cases<-list()
run.combine<-function(name,groups){
 grlist<-lapply(groups,function(g){frame<-data.frame(chr=vapply(g$peaks,function(p)p[[1]],character(1)),start=vapply(g$peaks,function(p)p[[2]],numeric(1)),end=vapply(g$peaks,function(p)p[[3]],numeric(1)));gr<-GenomicRanges::makeGRangesFromDataFrame(frame);gr$ident<-rep(g$name,length(gr));gr})
 local<-new.env(parent=env);local$grlist<-grlist;result<-tryCatch({eval(block,local);as.data.frame(local$gr)},error=function(e)list(error=conditionMessage(e)))
 combine.cases[[name]]<<-list(groups=groups,status=if(is.data.frame(result))'original-completed' else 'original-failed',result=if(is.data.frame(result))lapply(seq_len(nrow(result)),function(i)as.list(result[i,c('seqnames','start','end','peak_called_in')])) else result)
}
run.combine('revmap_order',list(list(name='B',peaks=list(list('chr2',11,20),list('chr1',101,120))),list(name='A',peaks=list(list('chr1',90,105),list('chr2',21,25),list('chr1',110,130)))))
run.combine('nested_ties',list(list(name='Z',peaks=list(list('chr1',30,60),list('chr1',10,40))),list(name='A',peaks=list(list('chr1',20,35),list('chr1',80,90)))))
run.combine('empty_group',list(list(name='empty',peaks=list()),list(name='A',peaks=list(list('chr1',1,10)))))
run.combine('all_empty',list(list(name='empty',peaks=list())))
run.combine('no_returned_groups',list())

parse.cases<-list();macs.called<-list()
run.parse<-function(name,text,broad=FALSE,format='BED'){
 folder<-file.path(out,paste0('parse-',name));dir.create(folder);actual.command<-NULL
 env$system<-function(command,...){actual.command<<-command;write.raw(file.path(folder,paste0('fixture_peaks.',if(broad)'broadPeak' else 'narrowPeak')),text);0L}
 result<-tryCatch(env$CallPeaks.default(object=c('/input/a.fragments.gz','/input/b.fragments.gz'),macs2.path='/private/macs2',outdir=normalizePath(folder,winslash='/'),broad=broad,format=format,name='fixture',cleanup=FALSE,verbose=FALSE),error=function(e)list(error=conditionMessage(e)))
 good<-inherits(result,'GRanges');parse.cases[[name]]<<-list(text=text,broad=broad,format=format,status=if(good)'original-completed' else 'original-failed',command=actual.command,result=if(good)lapply(seq_along(result),function(i)as.list(as.data.frame(result)[i,,drop=FALSE])) else result)
}
run.parse('narrow','chr1\t0\t100\tfixture_peak_1\t35\t.\t4.25\t8.5\t3.5\t20\nchr2\t100\t200\tfixture_peak_2\t2000\t.\t12.75\tInf\t200\t0\n')
run.parse('broad','chr1\t10\t90\tfixture_peak_1\t70\t.\t8\t9\t7\n',TRUE)
run.parse('bedpe','chr1\t10\t90\tfixture_peak_1\t70\t.\t8\t9\t7\t5\n',FALSE,'BEDPE')
run.parse('empty','')
stopifnot(all(vapply(split.cases,function(c)c$status=='original-completed',logical(1))),parse.cases$empty$status=='original-completed',length(parse.cases$empty$result)==0L)
jsonlite::write_json(list(status='original-Signac-split-and-retained-parse-reduce-controls-not-MACS-fits',split=split.cases,combine=combine.cases,parse=parse.cases,scope='C++1.17.1 splitting; unchanged1.9.0 R parser/command and combine.peaks block with controlled supplied outputs. No statistical peak calling or whole historical runtime is claimed.'),file.path(out,'RESULT.json'),auto_unbox=TRUE,pretty=TRUE,digits=17,na='string')
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'));cat('ORIGINAL_SIGNAC_PEAK_PREPARATION_CONTROLS_COMPLETE\n')
