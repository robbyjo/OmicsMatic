# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==3L)
.libPaths(c(normalizePath('build/literature-review/signac-runtime/reference-setup-1/R-lib'),normalizePath('build/omics-sctransform-r'),.Library),include.site=FALSE)
stopifnot(requireNamespace('Signac',quietly=TRUE),as.character(packageVersion('Signac'))=='1.17.1',!requireNamespace('wrswoR',quietly=TRUE))
input<-jsonlite::read_json(args[[1]],simplifyVector=FALSE);resource<-'src/main/resources/org/jlinalg/omics/signac-motifs';profiles<-jsonlite::read_json(file.path(resource,'SOURCE-PROFILES.json'),simplifyVector=FALSE)
source('src/main/resources/org/jlinalg/omics/signac_motif_legacy_access.R')
capture<-function(f){warnings<-character();value<-tryCatch(withCallingHandlers(f(),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')}),error=function(e)list(error=conditionMessage(e)));list(value=value,warnings=warnings)}
envs<-list();for(record in profiles$records){env<-new.env(parent=asNamespace('Signac'));for(fun in record$functions)sys.source(file.path(resource,fun$resource),env,keep.source=FALSE);if(record$profile=='1.17.1-package')for(name in ls(env))stopifnot(identical(body(env[[name]]),body(get(name,asNamespace('Signac')))));if(isTRUE(input$legacy_access_bridge)&&record$profile!='1.17.1-package')env$GetAssayData<-signac_motif_legacy_get_assay_data;envs[[record$profile]]<-env}
ids<-function(values,names)if(length(values)==0L)character() else vapply(values,function(v)if(is.numeric(v))names[[as.integer(v)+1L]] else v,character(1))
results<-list()
for(profile in input$profiles)for(case in input$cases){
 before<-NULL;after<-NULL;object<-NULL;meta<-NULL;features<-NULL;background<-NULL;env<-envs[[profile]]
 result<-capture(function(){
  d<-input$datasets[[case$dataset]];peaks<-GenomicRanges::GRanges(vapply(d$peaks,`[[`,character(1),'chromosome'),IRanges::IRanges(vapply(d$peaks,`[[`,numeric(1),'start'),vapply(d$peaks,`[[`,numeric(1),'end')));peak.names<-paste0(as.character(GenomeInfoDb::seqnames(peaks)),'-',GenomicRanges::start(peaks),'-',GenomicRanges::end(peaks));counts<-as(Matrix::Matrix(do.call(rbind,lapply(d$counts,unlist)),sparse=TRUE),'dgCMatrix');dimnames(counts)<-list(peak.names,unlist(d$cells))
  motif.ids<-unlist(d$motifs);if(length(motif.ids)==0L)motif.ids<-character();matrix<-if(length(motif.ids))as(Matrix::Matrix(do.call(rbind,lapply(d$matrix,unlist)),sparse=TRUE),'dgCMatrix') else new('dgCMatrix',Dim=as.integer(c(length(peak.names),0L)),p=0L);dimnames(matrix)<-list(peak.names,motif.ids)
  pwm<-setNames(lapply(motif.ids,function(id)matrix(1,4,5,dimnames=list(c('A','C','G','T'),NULL))),motif.ids);motif<-Signac::CreateMotifObject(data=matrix,pwm=pwm,motif.names=setNames(as.list(unlist(d$motif_names)),motif.ids))
  assay<-Signac::CreateChromatinAssay(counts=counts,ranges=peaks,motifs=motif,min.cells=0,min.features=0);meta<<-data.frame(GC.percent=vapply(d$metadata,function(v)if(is.null(v$gc))NA_real_ else v$gc,numeric(1)),sequence.length=vapply(d$metadata,`[[`,numeric(1),'length'),count=Matrix::rowSums(counts),row.names=peak.names);assay<-SeuratObject::AddMetaData(assay,meta);object<<-SeuratObject::CreateSeuratObject(counts=assay,assay='peaks')
  features<<-ids(case$query,peak.names);background<<-if(is.null(case$background))NULL else if(is.numeric(case$background))case$background else ids(case$background,peak.names);RNGkind('Mersenne-Twister','Inversion','Rejection');set.seed(case$seed);before<<-.Random.seed;fun<-if(profile=='1.17.1-package')Signac::FindMotifs else env$FindMotifs
  value<-fun(object=object,features=features,background=background,assay='peaks',verbose=FALSE,p.adjust.method=case$adjustment,features.match=unlist(case$features_match));after<<-.Random.seed;list(rows=value,seed_before=before,seed_after=after,effective_motif_names=Signac::GetMotifData(object,assay='peaks',slot='motif.names'))
 })
 row<-list(id=case$id,profile=profile,result=result)
 if(!'error'%in%names(result$value)){
  replay<-capture(function(){
   selected<-features;bg<-background;if(is.null(bg))bg<-rownames(object)
   .Random.seed<<-before
   if(is.numeric(bg)){
    choose<-meta[setdiff(rownames(meta),selected),,drop=FALSE]
    if(profile!='1.6.0-source'&&length(setdiff(selected,rownames(meta)))>0L)selected<-intersect(selected,rownames(meta))
    query<-meta[selected,,drop=FALSE];bg<-env$MatchRegionStats(meta.feature=choose,query.feature=query,regions=selected,n=bg,verbose=FALSE,features.match=unlist(case$features_match))
   }
   stopifnot(identical(after,.Random.seed));list(effective_query=selected,effective_background=bg,replay_after=.Random.seed)
  })
  if('error'%in%names(replay$value))row$replay_error<-replay else row$replay<-replay
 }
 results[[length(results)+1L]]<-row;cat(profile,case$id,'\n')
}
writeLines(capture.output(sessionInfo()),file.path(args[[3]],'session-info.txt'));jsonlite::write_json(list(status='captured-original-motif-enrichment-controls',results=results),args[[2]],pretty=TRUE,auto_unbox=TRUE,digits=17,na='string',null='null');cat('COMPLETE_ORIGINAL_MOTIF_CONTROLS',length(results),'\n')
