# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Explicit original ASCAT APIs. No user R code or user-supplied RDS is evaluated.
options(warn=1,bitmapType='cairo',digits=17)
engine_root <- getwd()
stage <- 'namespace';warnings_seen <- list();tables <- list();groups <- list()
number <- function(x)vapply(x,function(z)if(is.nan(z))'NaN'else if(is.na(z))'NA'else if(is.infinite(z))if(z>0)'Inf'else'-Inf'else trimws(formatC(z,digits=17,format='g',decimal.mark='.')),character(1))
scalar_vector <- function(x,mode='character') {
  if(is.null(x))return(NULL)
  if(mode=='numeric')return(vapply(x,function(v)if(is.null(v))NA_real_ else as.numeric(v),numeric(1)))
  if(mode=='logical')return(vapply(x,as.logical,logical(1)))
  vapply(x,as.character,character(1))
}
table_out <- function(x,path,role) {
  if(is.null(x)) {
    stream<-file(path,'wb');close(stream);tables[[path]]<<-list(role=role,rows=0L,columns=list(),source_null=TRUE);return(invisible(NULL))
  }
  x<-as.data.frame(x,check.names=FALSE,stringsAsFactors=FALSE)
  schema<-lapply(seq_along(x),function(i)list(ordinal=i,name=names(x)[[i]],type=if(is.numeric(x[[i]]))'source-number-token'else if(is.logical(x[[i]]))'source-logical'else'source-text'))
  values<-lapply(x,function(col)if(is.numeric(col))number(col)else as.character(col));names(values)<-names(x)
  if(any(vapply(values,function(v)any(grepl('[\t\r\n]',v),na.rm=TRUE),logical(1))))stop('Source table contains tab/newline identifiers; literal TSV cannot represent the complete table')
  write.table(as.data.frame(values,check.names=FALSE),path,sep='\t',quote=FALSE,row.names=FALSE,na='NA')
  tables[[path]]<<-list(role=role,rows=nrow(x),columns=schema,source_null=FALSE)
}
matrix_out <- function(x,path,role) {
  if(is.null(x))return(table_out(NULL,path,role))
  table_out(cbind(data.frame(source_id=rownames(x),check.names=FALSE),as.data.frame(x,check.names=FALSE)),path,role)
}
native_reference <- function(bc,fit,path,gamma,bounds,current) {
  stream<-file(path,'wb');on.exit(close(stream));ints<-function(x)writeBin(as.integer(x),stream,size=4,endian='big');doubles<-function(x)writeBin(as.double(x),stream,size=8,endian='big')
  writeChar('OMICSASCAT1',stream,eos=NULL,useBytes=TRUE);ints(length(bc$samples));population<-list()
  for(index in seq_along(bc$samples)) {
    sample<-bc$samples[[index]];b<-bc$Tumor_BAF_segmented[[index]][,,drop=FALSE];names(b)<-rownames(bc$Tumor_BAF_segmented[[index]])
    r<-bc$Tumor_LogR_segmented[,index];names(r)<-rownames(bc$Tumor_LogR_segmented);r<-r[names(b)]
    auto<-!(bc$SNPpos[names(b),1] %in% bc$sexchromosomes);r<-r[auto];b<-b[auto]
    segments<-get('make_segments',asNamespace('ASCAT'))(r,b)
    params<-list(segments=segments,gamma=gamma)
    if(current)params<-c(params,bounds)
    distance<-do.call(get('create_distance_matrix',asNamespace('ASCAT')),params)
    psi<-if(current)seq(bounds$min_ploidy-.5,bounds$max_ploidy+.5,.05)else seq(1,6,.05)
    rho<-if(current)seq(round(bounds$min_purity,2),round(bounds$max_purity,2),.01)else seq(.1,1.05,.01)
    stopifnot(identical(dim(distance),c(length(psi),length(rho))))
    caps<-0L
    if(sample %in% names(fit$distance_matrix)) {
      model<-fit$distance_matrix[[sample]];stopifnot(identical(dim(model),dim(distance)));changed<-which(model!=distance,arr.ind=TRUE)
      if(nrow(changed)) {stopifnot(all(model[changed]==1e20),all(rho[changed[,2]]>1));caps<-nrow(changed)}
    }
    ints(index);ints(length(r));doubles(r);doubles(b);ints(nrow(segments));doubles(as.vector(t(segments)));ints(length(psi));doubles(psi);ints(length(rho));doubles(rho);doubles(as.vector(t(distance)))
    population[[sample]]<-list(input_loci=length(r),retained_loci=sum(segments[,'length']),segments=nrow(segments),distance_cells=length(distance),original_fitting_fallback_capped_cells=caps)
  }
  population
}
run_group <- function(files,sample_info,id,request,current,root) {
  folder<-file.path('models',id);dir.create(folder,recursive=TRUE);prefix<-paste0(folder,'/');s<-request$settings
  sample_ids<-vapply(sample_info,function(x)x$id,character(1));sex<-vapply(sample_info,function(x)x$sex,character(1))
  params<-list(Tumor_LogR_file=files$tumor_logR,Tumor_BAF_file=files$tumor_BAF,Germline_LogR_file=files$normal_logR,Germline_BAF_file=files$normal_BAF,chrs=scalar_vector(s$chromosomes),gender=sex,sexchromosomes=scalar_vector(s$sex_chromosomes))
  if(current)params<-c(params,list(genomeVersion=s$genome,isTargetedSeq=s$is_targeted))
  stage<<-paste0(id,':load');bc<-do.call(ASCAT::ascat.loadData,params)
  stopifnot(identical(as.character(bc$samples),sample_ids));if(any(grepl('[/\\\t\r\n]',sample_ids))||any(sample_ids %in% c('.','..')))stop('Source sample IDs cannot form safe direct-child original plot names')
  saveRDS(bc,paste0(prefix,'loaded-object.rds'),compress=FALSE,version=2)
  ascat.plotRawData(bc,img.dir=folder,img.prefix='Before_')
  stage<<-paste0(id,':correction')
  if(request$correction$mode=='GC-RT')bc<-ascat.correctLogR(bc,GCcontentfile=request$correction$gc,replictimingfile=request$correction$replication)
  else if(request$correction$mode=='GC')bc<-if(current)ascat.correctLogR(bc,GCcontentfile=request$correction$gc)else ascat.GCcorrect(bc,GCcontentfile=request$correction$gc)
  else stopifnot(request$correction$mode=='none')
  saveRDS(bc,paste0(prefix,'corrected-object.rds'),compress=FALSE,version=2)
  matrix_out(bc$Tumor_LogR,paste0(prefix,'corrected-tumor-logR.tsv'),'complete-original-corrected-tumor-logR')
  if(request$correction$mode!='none')ascat.plotRawData(bc,img.dir=folder,img.prefix='After_')
  stage<<-paste0(id,':germline');gg<-NULL
  if(is.null(files$normal_logR)) {
    stopifnot(!is.null(s$genotype_platform));gg<-ascat.predictGermlineGenotypes(bc,platform=s$genotype_platform,img.dir=folder,img.prefix='Genotypes_');saveRDS(gg,paste0(prefix,'predicted-germline.rds'),compress=FALSE,version=2)
  }
  stage<<-paste0(id,':segmentation');params<-list(ASCATobj=bc,ascat.gg=gg,penalty=s$penalty)
  if(s$segmentation=='aspcf')params<-c(params,list(out.dir=folder,out.prefix=''))
  else {stopifnot(s$segmentation=='asmultipcf',s$joint_germline_policy=='source-first-normal');params<-c(params,list(wsample=scalar_vector(s$sample_weights,'numeric'),selectAlg=s$joint_algorithm,refine=s$joint_refine));if(current)params$out.dir<-folder}
  if(current)params$seed<-as.integer(s$seed)
  bc<-do.call(get(paste0('ascat.',s$segmentation),asNamespace('ASCAT')),params);saveRDS(bc,paste0(prefix,'segmented-object.rds'),compress=FALSE,version=2)
  ascat.plotSegmentedData(bc,img.dir=folder,img.prefix='Segmented_')
  matrix_out(bc$Tumor_LogR_segmented,paste0(prefix,'segmented-tumor-logR.tsv'),'complete-original-segmented-tumor-logR')
  stage<<-paste0(id,':fit');manual_rho<-scalar_vector(s$manual_rho,'numeric');manual_psi<-scalar_vector(s$manual_psi,'numeric')
  if(is.null(manual_rho)){manual_rho<-NA_real_;manual_psi<-NA_real_}
  params<-list(ASCATobj=bc,gamma=s$gamma,pdfPlot=FALSE,y_limit=s$y_limit,circos=NA,rho_manual=manual_rho,psi_manual=manual_psi,img.dir=folder,img.prefix='')
  bounds<-list(min_ploidy=s$min_ploidy,max_ploidy=s$max_ploidy,min_purity=s$min_purity,max_purity=s$max_purity)
  if(current)params<-c(params,bounds,list(write_segments=TRUE))
  fit<-do.call(ascat.runAscat,params);saveRDS(fit,paste0(prefix,'fitted-object.rds'),compress=FALSE,version=2)
  capture.output(str(fit,max.level=2),file=paste0(prefix,'fitted-structure.txt'))
  for(name in c('nA','nB'))matrix_out(fit[[name]],paste0(prefix,'fit-',name,'.tsv'),paste0('complete-original-',name))
  for(name in c('segments','segments_raw'))table_out(fit[[name]],paste0(prefix,'fit-',name,'.tsv'),paste0('complete-original-',name))
  purity<-if(current)fit$purity else fit$aberrantcellfraction
  aligned<-function(x)if(is.null(x))rep(NA_real_,length(sample_ids))else unname(x[match(sample_ids,names(x))])
  summary<-data.frame(sample=sample_ids,sex=sex,source_failed=sample_ids %in% fit$failedarrays,source_nonaberrant=sample_ids %in% fit$nonaberrantarrays,purity=aligned(purity),ploidy=aligned(fit$ploidy),psi=aligned(fit$psi),goodness_of_fit=aligned(fit$goodnessOfFit),check.names=FALSE)
  summary$admitted<-!summary$source_failed & is.finite(summary$purity)&is.finite(summary$ploidy)&is.finite(summary$psi)&is.finite(summary$goodness_of_fit)&summary$goodness_of_fit>=s$minimum_goodness_of_fit
  table_out(summary,paste0(prefix,'sample-summary.tsv'),'every-original-input-sample-and-admission-status')
  stage<<-paste0(id,':QC')
  if(current){qc<-ascat.metrics(bc,fit);saveRDS(qc,paste0(prefix,'source-metrics.rds'),version=2);table_out(cbind(sample=rownames(qc),qc),paste0(prefix,'source-metrics.tsv'),'complete-original-current-metrics')}
  stage<<-paste0(id,':native-components');native<-native_reference(bc,fit,paste0(prefix,'native-components.bin'),s$gamma,bounds,current)
  list(id=id,samples=as.list(sample_ids),loci=nrow(bc$Tumor_LogR),source_failed=as.list(fit$failedarrays),native_reference=native,all_samples_admitted=all(summary$admitted),source_current_metrics_available=current,legacy_metrics_policy=if(current)NULL else'Original2.5.2 source purity/ploidy/psi/goodness and sample states; no fabricated3.2 metrics')
}
run <- function() {
  library(ASCAT);library(jsonlite);profile<-read_json('profile.json',simplifyVector=FALSE);request<-read_json('effective.json',simplifyVector=FALSE);current<-profile$ASCAT=='3.2.0';s<-request$settings;stopifnot(as.character(getRversion())==profile$R,as.character(packageVersion('ASCAT'))==profile$ASCAT)
  set.seed(as.integer(s$seed));root<-getwd();dir.create('models');files<-request$matrices
  if(request$mode=='matrices')groups[['cohort']]<<-run_group(files,request$samples,'cohort',request,current,root)
  else {
    stopifnot(current,request$mode %in% c('hts','targeted'),s$segmentation=='aspcf');dir.create('reference')
    for(reference in request$references){file.copy(reference$alleles,paste0('reference/alleles_chr',reference$chromosome,'.txt'));if(!is.null(reference$loci))file.copy(reference$loci,paste0('reference/loci_chr',reference$chromosome,'.txt'))}
    extra<-paste('-f',s$required_flag,'-F',s$filtered_flag)
    if(!is.null(request$reference_fasta))extra<-paste(extra,'-r',normalizePath(request$reference_fasta,mustWork=TRUE))
    allele_prefix<-'reference/alleles_chr';loci_prefix<-'reference/loci_chr'
    if(request$mode=='targeted') {
      stage<<-'targeted-normal-panel';panel<-request$normal_panel
      worksheet<-data.frame(Patient_ID=vapply(panel,function(x)x$patient_id,character(1)),Normal_ID=vapply(panel,function(x)x$id,character(1)),Normal_file=vapply(panel,function(x)x$hts,character(1)),Gender=vapply(panel,function(x)x$sex,character(1)),stringsAsFactors=FALSE)
      write.table(worksheet,'normal-panel.tsv',sep='\t',quote=FALSE,row.names=FALSE)
      ascat.prepareTargetedSeq(Worksheet='normal-panel.tsv',Workdir='normal-panel',alleles.prefix=allele_prefix,BED_file=request$bed,allelecounter_exe=request$allele_counter,genomeVersion=s$genome,nthreads=s$nthreads,minCounts=s$panel_min_count,is_chr_based=s$is_chr_based,chrom_names=scalar_vector(s$chromosomes),min_base_qual=s$min_base_qual,min_map_qual=s$min_map_qual,additional_allelecounter_flags=extra,plotQC=TRUE)
      allele_prefix<-'normal-panel/alleleData/Cleaned/alleleData_chr';loci_prefix<-'normal-panel/alleleData/Cleaned/loci_chr'
    }
    for(index in seq_along(request$samples)) {
      sample<-request$samples[[index]];id<-sprintf('sample-%06d',index);dir.create(id);stage<<-paste0(id,':allele-counts-and-preparation');previous<-getwd();setwd(id)
      absolute<-function(x)if(is.null(x))NULL else normalizePath(file.path(previous,x),mustWork=TRUE)
      ascat.prepareHTS(tumourseqfile=absolute(sample$tumor),normalseqfile=if(is.null(sample$normal))NA_character_ else absolute(sample$normal),tumourname=sample$id,normalname=paste0(sample$id,'_normal'),allelecounter_exe=request$allele_counter,alleles.prefix=file.path(previous,allele_prefix),loci.prefix=file.path(previous,loci_prefix),gender=sample$sex,genomeVersion=s$genome,nthreads=s$nthreads,tumourLogR_file='tumor-logR.txt',tumourBAF_file='tumor-BAF.txt',normalLogR_file='normal-logR.txt',normalBAF_file='normal-BAF.txt',minCounts=s$min_count,BED_file=if(is.null(request$bed))NA_character_ else absolute(request$bed),probloci_file=if(is.null(request$problem_loci))NA_character_ else absolute(request$problem_loci),chrom_names=scalar_vector(s$chromosomes),min_base_qual=s$min_base_qual,min_map_qual=s$min_map_qual,additional_allelecounter_flags=extra,skip_allele_counting_tumour=FALSE,skip_allele_counting_normal=FALSE,loci_binsize=s$loci_bin_size,seed=as.integer(s$seed))
      setwd(previous);files<-list(tumor_logR=file.path(id,'tumor-logR.txt'),tumor_BAF=file.path(id,'tumor-BAF.txt'),normal_logR=if(is.null(sample$normal))NULL else file.path(id,'normal-logR.txt'),normal_BAF=if(is.null(sample$normal))NULL else file.path(id,'normal-BAF.txt'))
      groups[[id]]<<-run_group(files,list(sample),id,request,current,root)
    }
  }
  stage<<-'complete';all_admitted<-all(vapply(groups,function(g)g$all_samples_admitted,logical(1)))
  write_json(list(status=if(all_admitted)'COMPLETE_ORIGINAL_ASCAT_WORKFLOW_OUTPUTS'else'FAIL2_ORIGINAL_ASCAT_HAS_UNREPORTABLE_SAMPLES',R=profile$R,ASCAT=profile$ASCAT,mode=request$mode,groups=groups,tables=tables,all_samples_admitted=all_admitted,whole_package_admitted=FALSE),'RESULTS.json',auto_unbox=TRUE,pretty=TRUE,null='null',digits=NA)
  all_admitted
}
ok<-tryCatch(withCallingHandlers(run(),warning=function(w){warnings_seen[[length(warnings_seen)+1L]]<<-list(stage=stage,message=conditionMessage(w))}),error=function(e){
  setwd(engine_root)
  if('jsonlite'%in%loadedNamespaces())jsonlite::write_json(list(status='FAIL2_ORIGINAL_ASCAT_PROCESS_EXCEPTION',stage=stage,message=conditionMessage(e),analysis_admitted=FALSE),'SOURCE-FAILURE.json',auto_unbox=TRUE,pretty=TRUE)
  else dput(list(status='FAIL2_ORIGINAL_ASCAT_PROCESS_EXCEPTION',stage=stage,message=conditionMessage(e)),file='SOURCE-FAILURE.R')
  message('FAIL2 ORIGINAL ASCAT ',stage,': ',conditionMessage(e));FALSE
})
if('jsonlite'%in%loadedNamespaces())jsonlite::write_json(warnings_seen,'SOURCE-WARNINGS.json',auto_unbox=TRUE,pretty=TRUE,null='null')
writeLines(capture.output(sessionInfo()),'session.txt');writeLines(readLines('/proc/self/maps'),'R-MAPS.txt')
if(!isTRUE(ok))quit(status=2)
cat('ORIGINAL_ASCAT_COMPLETE_ALL_REQUESTED_GROUPS_AND_SAMPLES\n')
