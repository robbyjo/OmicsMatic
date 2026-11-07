# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Guarded original FASTA/PFM -> AddMotifs/RegionStats -> FindMotifs worker.
# Native Java input/reference/position/statistical auditing is still required.
args<-commandArgs(trailingOnly=TRUE)
if(length(args)!=2L||args[[1]]!='--request-file')stop('Require --request-file UTF8_PROTOCOL')
protocol<-readLines(args[[2]],encoding='UTF-8',warn=FALSE)
if(length(protocol)!=4L)stop('Invalid motif worker protocol')
status<-'failed';detail<-'';warnings<-character()
action<-'Read WARNINGS.md, worker logs, captured FASTA/PFMs/counts/query, source profile and effective settings. Preserve this attempt. Correct the named runtime/input/reference/background/capacity cause and use NEW output paths. Do not silently change reference, motifs, sampler, selections or thresholds.'
clean<-function(value)gsub('[\t\r\n]',' ',as.character(value))
number<-function(value){if(is.na(value))return(if(is.nan(value))'NaN' else 'NA');if(is.infinite(value))return(if(value>0)'Inf' else '-Inf');formatC(value,format='g',digits=17)}
write_tsv<-function(path,header,rows){connection<-file(path,'wb');on.exit(close(connection));writeBin(charToRaw(enc2utf8(paste0(paste(header,collapse='\t'),'\n'))),connection);for(row in rows){stopifnot(length(row)==length(header));if(any(grepl('[\t\r\n]',row)))stop('Unsafe typed output field');writeBin(charToRaw(enc2utf8(paste0(paste(row,collapse='\t'),'\n'))),connection)}}
read_tsv<-function(path){
 lines<-readLines(path,encoding='UTF-8',warn=FALSE);if(!length(lines))stop('Empty typed motif TSV')
 fields<-function(line){value<-strsplit(paste0(line,'\t__omicsmatic_end__'),'\t',fixed=TRUE)[[1L]];value[-length(value)]}
 header<-fields(lines[[1L]]);rows<-lapply(lines[-1L],fields);if(anyDuplicated(header)||any(header=='')||any(lengths(rows)!=length(header)))stop('Invalid typed motif TSV shape')
 values<-lapply(seq_along(header),function(i)vapply(rows,`[[`,character(1),i));names(values)<-header;as.data.frame(values,stringsAsFactors=FALSE,check.names=FALSE)
}
required<-function(message){status<<-'skipped-dependency-unavailable';action<<-'SKIP3: skip dependent motif scanning/enrichment. Supply exact R4.6.1, Signac1.17.1, motifmatchr1.34.0, TFBSTools1.50.0, BSgenome1.80.0 and the pinned Matrix/SeuratObject/genomic dependencies in explicit permitted private/base libraries, with working native DLLs and wrswoR absent for the base-sample.int profile. Install/provide them only where permitted into NEW paths; otherwise retain the skip. Do not substitute paper versions silently.';stop(message)}
ok<-tryCatch(withCallingHandlers({
 .libPaths(c(protocol[[1L]],protocol[[2L]],protocol[[3L]],.Library),include.site=FALSE)
 if(!identical(paste(R.version$major,R.version$minor,sep='.'),'4.6.1'))required('Exact R4.6.1 is required')
 expected<-c(Signac='1.17.1',motifmatchr='1.34.0',TFBSTools='1.50.0',BSgenome='1.80.0',Biostrings='2.80.2',Rsamtools='2.28.0',Matrix='1.7.5',SeuratObject='5.4.0',GenomicRanges='1.64.0',GenomeInfoDb='1.48.0',jsonlite='2.0.0')
 for(package in names(expected))if(!requireNamespace(package,quietly=TRUE)||as.character(packageVersion(package))!=expected[[package]])required(paste('Missing/wrong required package',package,expected[[package]]))
 if(requireNamespace('wrswoR',quietly=TRUE))required('Optional wrswoR changes this sampling profile; use a separate runtime with it absent')
 if(requireNamespace('BiocParallel',quietly=TRUE))BiocParallel::register(BiocParallel::SerialParam())
 p<-jsonlite::read_json(protocol[[4L]],simplifyVector=FALSE);if(!p$profile%in%c('1.6.0-source','1.9.0-source','1.14.0-source','1.17.1-package'))stop('Unsupported source profile')
 source('source/signac_pfm_construction.R');guard_signac_pfm_constructor();source('source/signac_motif_legacy_access.R');source('source/signac_scan_legacy_fasta.R');metadata<-jsonlite::read_json('source/signac-scan/SOURCE-PROFILES.json',simplifyVector=FALSE)
 env<-new.env(parent=asNamespace('Signac'));record<-metadata$records[[which(vapply(metadata$records,function(x)x$profile==p$profile,logical(1)))]]
 for(f in record$functions)sys.source(file.path('source/signac-scan',f$resource),env,keep.source=FALSE)
 if(p$profile=='1.17.1-package')for(name in ls(env))if(!identical(body(env[[name]]),body(get(name,asNamespace('Signac')))))required(paste('Installed Signac function differs from retained original source:',name))
 if(p$profile!='1.17.1-package')env$GetAssayData<-signac_motif_legacy_get_assay_data
 peak.ids<-readLines(p$features,encoding='UTF-8',warn=FALSE);cells<-readLines(p$cells,encoding='UTF-8',warn=FALSE);counts<-as(Matrix::readMM(p$counts),'dgCMatrix');dimnames(counts)<-list(peak.ids,cells)
 if(anyDuplicated(peak.ids)||anyDuplicated(cells)||any(!is.finite(counts@x))||any(counts@x<0)||any(counts@x!=floor(counts@x)))stop('Invalid normalized count exchange')
 ranges<-read_tsv(p$peaks);if(!identical(names(ranges),c('chromosome','start','end')))stop('Invalid peak range columns');peaks<-GenomicRanges::GRanges(ranges$chromosome,IRanges::IRanges(as.integer(ranges$start),as.integer(ranges$end)));names(peaks)<-peak.ids
 if(!identical(peak.ids,paste0(as.character(GenomeInfoDb::seqnames(peaks)),'-',GenomicRanges::start(peaks),'-',GenomicRanges::end(peaks))))stop('Canonical peak range identities differ')
 typed<-read_tsv(p$pfm);if(!identical(names(typed),c('motif_id','motif_name','position','A','C','G','T','bg_A','bg_C','bg_G','bg_T')))stop('Invalid typed PFM columns')
 motif.ids<-unique(typed$motif_id);if(!length(motif.ids)||anyDuplicated(motif.ids))stop('No unique PFM models')
 pfm<-do.call(TFBSTools::PFMatrixList,lapply(motif.ids,function(id){rows<-typed[typed$motif_id==id,,drop=FALSE];if(!identical(as.integer(rows$position),seq_len(nrow(rows)))||length(unique(rows$motif_name))!=1L)stop('PFM positions/names differ');matrix<-t(as.matrix(data.frame(lapply(rows[,c('A','C','G','T'),drop=FALSE],as.numeric))));rownames(matrix)<-c('A','C','G','T');background<-vapply(rows[1L,c('bg_A','bg_C','bg_G','bg_T'),drop=FALSE],function(x)as.numeric(x[[1L]]),numeric(1));names(background)<-c('A','C','G','T');TFBSTools::PFMatrix(ID=id,name=rows$motif_name[[1L]],strand='+',bg=background,profileMatrix=matrix)}));names(pfm)<-motif.ids
 pfm.capture<-capture_signac_effective_pfms(pfm,typed,'effective-pfms.tsv')
 if(file.exists(paste0(p$fasta,'.fai')))stop('Generated reference index already exists; preserve attempt and use NEW output')
 Rsamtools::indexFa(p$fasta);genome<-Rsamtools::FaFile(p$fasta);originalGetSeq<-BSgenome::getSeq;bridge<-NULL
 perform<-function(){
  if(p$profile=='1.6.0-source'){bridge<-signac_scan_legacy_fasta(genome);on.exit(bridge$restore(),add=TRUE)}
  motif<-if(p$profile=='1.17.1-package')Signac::AddMotifs(peaks,genome=genome,pfm=pfm,verbose=FALSE) else env$AddMotifs.default(peaks,genome=genome,pfm=pfm,verbose=FALSE)
  stats<-if(p$profile=='1.17.1-package')Signac::RegionStats(peaks,genome=genome,verbose=FALSE) else env$RegionStats.default(peaks,genome=genome,verbose=FALSE)
  list(motif=motif,stats=as.data.frame(stats))
 }
 scanned<-perform();if(!identical(BSgenome::getSeq,originalGetSeq))stop('Historical FASTA access binding was not restored')
 motif<-scanned$motif;stats<-scanned$stats;rownames(stats)<-peak.ids;stats$count<-Matrix::rowSums(counts)
 motif.matrix<-Signac::GetMotifData(motif,slot='data');if(!identical(rownames(motif.matrix),peak.ids)||!identical(colnames(motif.matrix),motif.ids))stop('Source motif matrix row/column identities changed')
 labels<-Signac::GetMotifData(motif,slot='motif.names');positions<-Signac::GetMotifData(motif,slot='positions');if(sum(vapply(positions,length,integer(1)))>p$maximum_hits)stop('Original motif positions exceed explicit capture capacity')
 Matrix::writeMM(motif.matrix,'motif-presence.mtx');write_tsv('motif-labels.tsv',c('motif_id','motif_name'),lapply(seq_along(motif.ids),function(i)c(motif.ids[[i]],labels[[motif.ids[[i]]]])))
 write_tsv('sequence-stats.tsv',c('peak_id',names(stats)),lapply(seq_len(nrow(stats)),function(i)c(peak.ids[[i]],vapply(stats[i,,drop=FALSE],number,character(1)))))
 position.rows<-list();for(id in names(positions)){x<-positions[[id]];for(i in seq_along(x))position.rows[[length(position.rows)+1L]]<-c(id,as.character(GenomeInfoDb::seqnames(x)[i]),GenomicRanges::start(x)[i],GenomicRanges::end(x)[i],as.character(GenomicRanges::strand(x)[i]),number(x$score[i]))}
 write_tsv('motif-positions.tsv',c('motif_id','chromosome','start','end','strand','score'),position.rows)
 assay<-Signac::CreateChromatinAssay(counts=counts,ranges=peaks,motifs=motif,min.cells=0,min.features=0);assay<-SeuratObject::AddMetaData(assay,stats);object<-SeuratObject::CreateSeuratObject(counts=assay,assay='peaks');effective.counts<-SeuratObject::GetAssayData(object,assay='peaks',layer='counts')
 if(!identical(dimnames(effective.counts),dimnames(counts))||!identical(effective.counts@p,counts@p)||!identical(effective.counts@i,counts@i)||!identical(effective.counts@x,counts@x))stop('Seurat changed count identities/order/sparse storage; no implicit repair admitted')
 query<-readLines(p$query,encoding='UTF-8',warn=FALSE);background<-switch(p$background_kind,matched=as.numeric(p$background_n),all=NULL,explicit=readLines(p$background_file,encoding='UTF-8',warn=FALSE),stop('Invalid background kind'))
 RNGkind('Mersenne-Twister','Inversion','Rejection');set.seed(p$seed);before<-.Random.seed;fun<-if(p$profile=='1.17.1-package')Signac::FindMotifs else env$FindMotifs
 enrichment<-fun(object,features=query,background=background,assay='peaks',verbose=FALSE,p.adjust.method=p$p_adjust_method,features.match=unlist(p$features_match));after<-.Random.seed
 effective.query<-query;selected.background<-background;if(is.null(selected.background))selected.background<-peak.ids
 .Random.seed<-before
 if(p$background_kind=='matched'){
  choose<-stats[setdiff(rownames(stats),query),,drop=FALSE];if(p$profile!='1.6.0-source'&&length(setdiff(query,rownames(stats)))>0L)effective.query<-intersect(query,rownames(stats));selected.background<-env$MatchRegionStats(meta.feature=choose,query.feature=stats[effective.query,,drop=FALSE],regions=effective.query,n=p$background_n,verbose=FALSE,features.match=unlist(p$features_match))
 }
 replay.after<-.Random.seed;if(!identical(after,replay.after))stop('Original full-run and sampled-background replay RNG states differ')
 write_tsv('effective-query.tsv','peak_id',lapply(effective.query,identity));write_tsv('effective-background.tsv','peak_id',lapply(selected.background,identity))
 for(entry in list(list('rng-before.tsv',before),list('rng-after.tsv',after),list('rng-replay-after.tsv',replay.after)))write_tsv(entry[[1L]],c('index','value'),lapply(seq_along(entry[[2L]]),function(i)c(i,entry[[2L]][[i]])))
 write_tsv('enrichment-original.tsv',names(enrichment),lapply(seq_len(nrow(enrichment)),function(i)vapply(enrichment[i,,drop=FALSE],function(value)if(is.numeric(value))number(value) else as.character(value),character(1))))
 settings<-c(profile=p$profile,motifmatchr='1.34.0-package',TFBSTools=as.character(packageVersion('TFBSTools')),R_version=R.version.string,sampling='base-R-sample.int',wrswoR_present='false',rng_kind=paste(RNGkind(),collapse=','),seed=number(p$seed),background_kind=p$background_kind,background_n=if(is.null(p$background_n))'none' else number(p$background_n),features_match=paste(unlist(p$features_match),collapse=','),p_adjust_method=p$p_adjust_method,fixed_AddMotifs_scan_defaults='original-unmodified-defaults',historical_whole_packages_executed='false',qualified_getSeq_restored='true',full_and_replay_RNG_states_identical='true',complete_runtime_DLL_host_closure='false',Java_must_validate='true')
 write_tsv('effective-settings.tsv',c('key','value'),Map(c,names(settings),unname(settings)));saveRDS(object,'fit.rds');write.table(installed.packages(lib.loc=.libPaths())[,c('Package','Version','LibPath','Built'),drop=FALSE],'installed-packages.tsv',sep='\t',quote=TRUE,row.names=FALSE);writeLines(capture.output(sessionInfo()),'session-info.txt')
 warnings<-c(warnings,'UPSTREAM_MOTIFMATCHR_PROFILE: scanning uses motifmatchr1.34.0 with original AddMotifs default arguments and declared PFMs. No historical motifmatchr package or independent MOODS calibration is claimed.','SOURCE_INFERENCE: FindMotifs uses the background alone as its hypergeometric population. Review NaN/Inf and nominal/adjusted probabilities; enrichment is not TF activity or regulatory causality.')
 if(p$profile!='1.17.1-package')warnings<-c(warnings,'HISTORICAL_SOURCE_ONLY: unchanged historical Signac functions use current pinned dependencies and explicit metadata/FASTA API bridges. No historical whole-package binaries are claimed.')
 status<-'completed-with-warnings';detail<-'Original scanning, sequence statistics, motif enrichment and seeded background replay completed; independent Java validation is still required.';TRUE
},warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')}),error=function(e){detail<<-conditionMessage(e);FALSE})
write_tsv('worker-warnings.tsv','warning',lapply(unique(warnings),function(x)clean(x)));write_tsv('worker-status.tsv',c('status','detail','required_action'),list(c(status,clean(detail),clean(action))));cat('SIGNAC_MOTIF_SCAN_WORKER_STATUS',status,'\n',detail,'\n');quit(status=if(ok)0L else if(status=='skipped-dependency-unavailable')3L else 2L)
