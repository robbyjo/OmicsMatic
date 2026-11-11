# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original Cicero/Monocle3 calls only; typed inputs, no installs, no input RDS or executable formulae.
runtime_ready<-FALSE;status<-"SKIP3_DEPENDENCY_UNAVAILABLE";detail<-"";current_stage<-"runtime";warnings_seen<-character();messages_seen<-character();cases<-list();guards<-list();guard_baseline<-list()
action<-"Preserve this attempt. Read WARNINGS.md, analysis-status.json, worker-report.json, source-status.json and original stdout/stderr. Provision the exact permitted Windows Java/R4.6.1/private Cicero-M3 profile only where installation is allowed; otherwise skip dependent analysis. Analysis installs nothing and substitutes no version. FAIL2 admits no atomic batch result. Correct the named input/source/model/policy/capacity cause into NEW paths; do not drop peaks/cells, repair IDs, impute values or convert unresolved NA coaccessibilities into zero or valid absence."
.libPaths(c(readLines("runtime-paths.txt",encoding="UTF-8",warn=FALSE),.Library),include.site=FALSE)
write_json<-function(x,path)jsonlite::write_json(x,path,pretty=TRUE,auto_unbox=TRUE,na="string",null="null",digits=NA)
kind<-function(x)if(is.numeric(x)&&is.nan(x))"NaN" else if(is.na(x))"NA" else if(is.numeric(x)&&is.infinite(x)){if(x>0)"positive-infinity" else "negative-infinity"} else "value"
token<-function(x){k<-kind(x);if(k=="NaN")"NaN" else if(k=="NA")"NA" else if(k=="positive-infinity")"Inf" else if(k=="negative-infinity")"-Inf" else if(is.double(x))sprintf("%.17g",x) else as.character(x)}
atomic_table<-function(frame,path){
 saveRDS(frame,paste0(path,".rds"))
 if(nrow(frame)>p$transport_chunk_entries){
  parts<-list();destination<-file(paste0(path,".tsv"),open="wt",encoding="UTF-8");on.exit(close(destination),add=TRUE)
  for(first in seq.int(1L,nrow(frame),by=p$transport_chunk_entries)){
   last<-min(nrow(frame),first+p$transport_chunk_entries-1L);part<-paste0(path,sprintf(".part-%06d",length(parts)+1L));atomic_table(frame[first:last,,drop=FALSE],part);lines<-readLines(paste0(part,".tsv"),encoding="UTF-8",warn=FALSE);if(length(parts))lines<-lines[-1L];writeLines(lines,destination,useBytes=TRUE);parts[[length(parts)+1L]]<-basename(paste0(part,".json"))
  };write_json(list(rows=nrow(frame),parts=parts,complete_original_RDS=basename(paste0(path,".rds"))),paste0(path,".json"));return(invisible(NULL))
 }
 stopifnot(is.data.frame(frame),all(vapply(frame,function(x)is.atomic(x)&&is.null(dim(x)),logical(1))))
 columns<-lapply(seq_along(frame),function(i){x<-frame[[i]];list(name=names(frame)[i],class=as.list(class(x)),type=typeof(x),levels=if(is.factor(x))as.list(levels(x)) else NULL,tokens=as.list(vapply(seq_along(x),function(j)token(x[j]),character(1))),kinds=as.list(vapply(seq_along(x),function(j)kind(x[j]),character(1))))})
 encoded<-setNames(lapply(columns,function(x)unlist(x$tokens,use.names=FALSE)),names(frame));write.table(as.data.frame(encoded,check.names=FALSE),paste0(path,".tsv"),sep="\t",quote=TRUE,qmethod="double",row.names=FALSE,fileEncoding="UTF-8");write_json(list(rows=nrow(frame),columns=columns),paste0(path,".json"))
}
read_literal<-function(path){rows<-lapply(readLines(path,encoding="UTF-8",warn=FALSE),strsplit,split="\t",fixed=TRUE);rows<-lapply(rows,`[[`,1L);h<-rows[[1]];rows<-rows[-1L];cols<-lapply(seq_along(h),function(j)vapply(rows,function(r)r[[j]],character(1)));names(cols)<-h;as.data.frame(cols,check.names=FALSE,stringsAsFactors=FALSE)}
sparse_transport<-function(x,maximum){
 x<-methods::as(x,"dgCMatrix");stopifnot(all(is.finite(x@x)),length(x@x)<=maximum);t<-summary(x)
 list(row_ids=as.list(rownames(x)),column_ids=as.list(colnames(x)),entries=lapply(seq_len(nrow(t)),function(i)list(row=as.integer(t$i[i]),column=as.integer(t$j[i]),value=token(t$x[i]))))
}
write_sparse<-function(x,path,maximum){
 x<-methods::as(x,"dgCMatrix");stopifnot(all(is.finite(x@x)),length(x@x)<=maximum)
 if(length(x@x)<=p$transport_chunk_entries){write_json(sparse_transport(x,maximum),path);return(invisible(NULL))}
 t<-summary(x);parts<-list()
 for(first in seq.int(1L,nrow(t),by=p$transport_chunk_entries)){
  last<-min(nrow(t),first+p$transport_chunk_entries-1L);part<-paste0(sub("[.]json$","",path),sprintf(".part-%06d.json",length(parts)+1L));write_json(list(entries=lapply(seq.int(first,last),function(i)list(row=as.integer(t$i[i]),column=as.integer(t$j[i]),value=token(t$x[i])))),part);parts[[length(parts)+1L]]<-basename(part)
 };write_json(list(row_ids=as.list(rownames(x)),column_ids=as.list(colnames(x)),entry_count=nrow(t),entry_parts=parts),path)
}
 dense_transport<-function(x){stopifnot(is.matrix(x),all(is.finite(x)));list(row_ids=as.list(rownames(x)),column_ids=as.list(colnames(x)),values=lapply(seq_len(nrow(x)),function(i)as.list(unname(vapply(x[i,],token,character(1)))))) }
tryCatch(withCallingHandlers({
 if(!requireNamespace("jsonlite",quietly=TRUE))stop("Required jsonlite unavailable")
 p<-jsonlite::fromJSON("parameters.json",simplifyVector=FALSE);profile<-jsonlite::fromJSON("../source/SOURCE-PROFILE.json",simplifyVector=FALSE)
 if(p$version!=profile$version)stop("Requested exact Cicero edition unavailable: ",p$version,"; no fallback")
 if(as.character(getRversion())!=profile$r_version||R.version$platform!=profile$platform)stop("Exact R/platform profile unavailable")
 Sys.setlocale("LC_CTYPE","English_United States.utf8")
 if(!isTRUE(l10n_info()[["UTF-8"]])||Sys.getlocale("LC_COLLATE")!="C"||Sys.getlocale("LC_NUMERIC")!="C")stop("Required UTF-8 character/C collation and numeric locale unavailable")
 for(name in names(profile$required_packages))if(!requireNamespace(name,quietly=TRUE)||as.character(package_version(getNamespaceVersion(name)))!=profile$required_packages[[name]]||as.character(packageVersion(name))!=profile$required_packages[[name]])stop("Exact dependency unavailable: ",name," ",profile$required_packages[[name]])
 suppressPackageStartupMessages(library(cicero))
 for(package in names(profile$guarded_functions)){
  ns<-asNamespace(package);definitions<-list()
  for(file in list.files(file.path("../source/1.3.9",package,"R"),pattern="[.]R$",full.names=TRUE))for(expr in parse(file,keep.source=FALSE)){
   if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]]) %in% c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))definitions[[as.character(expr[[2]])]]<-expr[[3]]
  }
  guard_baseline[[package]]<-list()
  for(name in unlist(profile$guarded_functions[[package]],use.names=FALSE)){
   if(is.null(definitions[[name]])||!exists(name,ns,inherits=FALSE))stop("Original source function unavailable: ",package,"::",name)
   source_fn<-eval(definitions[[name]],envir=ns);loaded_fn<-get(name,ns,inherits=FALSE)
   if(!identical(body(source_fn),body(loaded_fn))||!identical(formals(source_fn),formals(loaded_fn)))stop("Original source body/formals mismatch: ",package,"::",name)
   guard_baseline[[package]][[name]]<-list(body=body(loaded_fn),formals=formals(loaded_fn))
  };guards[[package]]<-profile$guarded_functions[[package]]
 }
 RhpcBLASctl::blas_set_num_threads(1L);RhpcBLASctl::omp_set_num_threads(1L);runtime_ready<-TRUE;current_stage<-"input"
 cells<-read_literal("../inputs/cells.tsv");peaks<-read_literal("../inputs/peaks.tsv");coordinates<-read_literal("../inputs/embedding.tsv");genome<-read_literal("../inputs/genome.tsv");cell_ids<-cells[[1]];peak_ids<-peaks[[1]]
 embedding<-matrix(as.numeric(as.matrix(coordinates[,-1,drop=FALSE])),nrow=length(cell_ids),dimnames=list(cell_ids,names(coordinates)[-1]));genome[[2]]<-as.numeric(genome[[2]])
 if(p$matrix_format=="dense-tsv"){
  raw<-read_literal("../inputs/counts");stopifnot(identical(raw[[1]],peak_ids),identical(names(raw)[-1],cell_ids));expression<-methods::as(matrix(as.numeric(as.matrix(raw[,-1,drop=FALSE])),nrow=length(peak_ids),dimnames=list(peak_ids,cell_ids)),"dgCMatrix")
 }else{expression<-methods::as(Matrix::readMM("../inputs/counts"),"dgCMatrix");stopifnot(identical(dim(expression),c(length(peak_ids),length(cell_ids))));dimnames(expression)<-list(peak_ids,cell_ids)}
 stopifnot(all(is.finite(expression@x)));t<-summary(expression);t<-t[t$x!=0,,drop=FALSE];zero_peaks<-which(Matrix::rowSums(expression)==0);if(length(zero_peaks))t<-rbind(t,data.frame(i=zero_peaks,j=1L,x=0));triplets<-data.frame(peak=peak_ids[t$i],cell=cell_ids[t$j],count=as.numeric(t$x),stringsAsFactors=FALSE)
 cds<-cicero::make_atac_cds(triplets,binarize=p$binarize)
 for(name in unlist(p$numeric_metadata,use.names=FALSE))SummarizedExperiment::colData(cds)[[name]]<-as.numeric(cells[[name]])[match(colnames(cds),cell_ids)]
 stopifnot(setequal(rownames(cds),peak_ids),setequal(colnames(cds),cell_ids));saveRDS(cds,"input-atac-cds.rds");saveRDS(list(raw_expression=expression,prepared_coordinates=embedding,declared_cells=cells,declared_peaks=peaks,declared_genome=genome),"input-transport.rds")
 write_sparse(SummarizedExperiment::assay(cds,"counts"),"constructed-counts.json",p$maximum_intermediate_entries)
 atomic_table(as.data.frame(SummarizedExperiment::rowData(cds)),"constructed-peaks");atomic_table(data.frame(cell_id=colnames(cds)),"constructed-cells")
 write_json(list(input_peak_ids=as.list(peak_ids),input_cell_ids=as.list(cell_ids),source_peak_ids=as.list(rownames(cds)),source_cell_ids=as.list(colnames(cds)),coordinate_system=p$coordinate_system,coordinate_conversion=FALSE,binarize=p$binarize,zero_peak_dimension_anchors=as.list(peak_ids[zero_peaks]),source_ordering="Original make_atac_cds factor/C collation and chromosome/start ordering; exact mapping audited by Java"),"input-ledger.json")
 dir.create("cases")
 for(a in p$analyses){
  target<-file.path("cases",a$id);dir.create(target);case_warnings<-character();case_messages<-character();failure<-NULL;current_stage<-"aggregation"
  population<-tryCatch(withCallingHandlers({
   set.seed(as.integer(a$aggregation_seed));write_json(list(aggregation_seed=a$aggregation_seed,RNGkind=as.list(RNGkind())),file.path(target,"rng.json"))
   summary_stats<-unlist(a$summary_stats,use.names=FALSE);if(!length(summary_stats))summary_stats<-NULL
   aggregated<-cicero::make_cicero_cds(cds,embedding,k=as.integer(a$k),summary_stats=summary_stats,size_factor_normalize=a$size_factor_normalize,silent=FALSE,return_agg_info=TRUE)
   saveRDS(aggregated,file.path(target,"aggregation-return.rds"));ccds<-aggregated[[1]];saveRDS(ccds,file.path(target,"cicero-cds.rds"));atomic_table(aggregated[[2]],file.path(target,"aggregation-map"));atomic_table(as.data.frame(SummarizedExperiment::colData(ccds)),file.path(target,"aggregate-metadata"));write_sparse(SummarizedExperiment::assay(ccds,"counts"),file.path(target,"aggregate-counts.json"),p$maximum_intermediate_entries)
   m<-a$model;d<-a$distance_parameter;parameters<-NULL
   if(d$mode=="estimate"){
    current_stage<-"distance_parameter";set.seed(as.integer(d$seed));parameters<-cicero::estimate_distance_parameter(ccds,window=m$window,maxit=as.integer(d$maxit),s=m$power,sample_num=as.integer(d$sample_num),distance_constraint=d$distance_constraint,distance_parameter_convergence=d$convergence,max_elements=as.integer(m$max_elements),genomic_coords=genome,max_sample_windows=as.integer(d$max_sample_windows));saveRDS(parameters,file.path(target,"distance-parameters.rds"));value<-mean(unlist(parameters));write_json(list(parameters=as.list(unlist(parameters)),returned_samples=length(parameters),mean=value),file.path(target,"distance-parameters.json"))
   }else value<-d$value
   stopifnot(length(value)==1L,is.finite(value),value>=0)
   current_stage<-"models";ns<-asNamespace("cicero");preflight<-get("generate_windows",ns)(m$window,genome);stopifnot(length(preflight)<=p$maximum_windows);preflight_entries<-0
   for(i in seq_along(preflight)){part<-get("get_genomic_range",ns)(preflight,ccds,i);if(nrow(part)>1&&nrow(part)<=m$max_elements)preflight_entries<-preflight_entries+4*nrow(part)^2;stopifnot(preflight_entries<=p$maximum_model_entries)}
   models<-cicero::generate_cicero_models(ccds,distance_parameter=value,s=m$power,window=m$window,max_elements=as.integer(m$max_elements),genomic_coords=genome);saveRDS(models,file.path(target,"models.rds"))
   ns<-asNamespace("cicero");windows<-get("generate_windows",ns)(m$window,genome);stopifnot(length(windows)==length(models),length(windows)<=p$maximum_windows);exported<-vector("list",length(models));entries<-0
   for(i in seq_along(models)){
    part<-get("get_genomic_range",ns)(windows,ccds,i);model<-models[[i]];w<-list(id=names(models)[i],chromosome=as.character(GenomicRanges::seqnames(windows[i])),start=as.numeric(GenomicRanges::start(windows[i])),end=as.numeric(GenomicRanges::end(windows[i])),peak_ids=as.list(rownames(part)))
    if(is.list(model)){
     entries<-entries+4*nrow(part)^2;stopifnot(entries<=p$maximum_model_entries);vals<-as.matrix(SummarizedExperiment::assay(part,"counts"));covariance<-stats::cov(t(vals));diag(covariance)<-diag(covariance)+1e-4;distance_matrix<-get("calc_dist_matrix",ns)(part);rho<-get("get_rho_mat",ns)(distance_matrix,value,m$power);dimnames(rho)<-dimnames(covariance)
     w$status<-"original-model";w$matrix_file<-sprintf("window-%06d.json",i);write_json(list(covariance=dense_transport(covariance),penalty=dense_transport(rho),w=dense_transport(model$w),wi=dense_transport(model$wi),errflag=model$errflag,niter=model$niter,del=model$del),file.path(target,w$matrix_file))
    }else if(is.character(model)&&length(model)==1L){w$status<-"source-skipped";w$reason<-model}else stop("Unexpected original model/window result; raw models retained")
    exported[[i]]<-w
   }
   write_json(list(distance_parameter=value,windows=exported,matrix_entries=entries),file.path(target,"models.json"))
   current_stage<-"connections";connections<-cicero::assemble_connections(models,silent=FALSE);saveRDS(connections,file.path(target,"source-connections-before-cap.rds"));if(nrow(connections)>p$maximum_connection_rows)stop("Original connection population exceeds maximum_connection_rows; raw RDS retained");atomic_table(connections,file.path(target,"connections"))
   if(any(is.nan(connections$coaccess))||any(is.infinite(connections$coaccess)))stop("Original nonfinite non-NA connection scores; raw values retained")
   if(any(vapply(exported,function(x)x$status=="source-skipped",logical(1)))&&a$skipped_windows_policy=="require-all-modeled")stop("Declared policy rejects original skipped windows; all dispositions retained")
   if(anyNA(connections$coaccess)&&a$unresolved_connections_policy=="fail-on-NA")stop("Declared policy rejects unresolved original NA connections; no NA-to-zero substitution")
   if(d$mode=="estimate"){
    if(d$require_full_sample&&length(parameters)!=d$sample_num)stop("Declared policy requires all requested distance samples; partial original sampling retained")
    if(!d$allow_maxit_warning&&any(grepl("maximum iterations hit",case_warnings,fixed=TRUE)))stop("Declared policy rejects original distance-calibration maxit warning")
   }
   current_stage<-"save_reload";restored<-readRDS(file.path(target,"cicero-cds.rds"));stopifnot(identical(SummarizedExperiment::assay(restored,"counts"),SummarizedExperiment::assay(ccds,"counts")),identical(rownames(restored),rownames(ccds)),identical(colnames(restored),colnames(ccds)),identical(readRDS(file.path(target,"connections.rds")),connections))
   write_json(list(analysis=a,binarize=p$binarize,window_boundary_rule="Either raw start or end strictly inside source window; no general-overlap substitution or endpoint conversion",source_ordering="make_atac_cds original reordering retained with exact ledger",solver="Original glasso defaults; no independent refit claimed",glasso_formals=profile$glasso_formals,cov2cor_body=profile$cov2cor_body),file.path(target,"original-arguments.json"))
   list(peaks=nrow(ccds),input_cells=ncol(cds),aggregates=ncol(ccds),windows=length(models),connections=nrow(connections),unresolved_NA=sum(is.na(connections$coaccess)),save_reload_identical=TRUE)
  },warning=function(w){case_warnings<<-c(case_warnings,paste0(current_stage,": ",conditionMessage(w)));invokeRestart("muffleWarning")},message=function(m){case_messages<<-c(case_messages,paste0(current_stage,": ",conditionMessage(m)));invokeRestart("muffleMessage")}),error=function(e){failure<<-list(stage=current_stage,class=as.list(class(e)),message=conditionMessage(e),call=paste(deparse(conditionCall(e)),collapse="\n"));NULL})
  entry<-list(id=a$id,status=if(is.null(failure))"original-returned" else "FAIL2_ORIGINAL_ERROR",population=population,warnings=as.list(case_warnings),messages=as.list(case_messages),original_error=failure,admission_authority="Java analysis-status.json; original return alone is not admission");write_json(entry,file.path(target,"source-status.json"));cases[[length(cases)+1L]]<-entry;write_json(list(status="INCOMPLETE_NO_RESULT_ADMITTED",cases=cases),"worker-progress.json")
 }
 if(any(vapply(cases,function(x)x$status!="original-returned",logical(1))))stop("One or more original cases failed; no atomic batch result admitted")
 for(package in names(guard_baseline))for(name in names(guard_baseline[[package]])){f<-get(name,asNamespace(package),inherits=FALSE);g<-guard_baseline[[package]][[name]];if(!identical(body(f),g$body)||!identical(formals(f),g$formals))stop("Loaded original function changed during analysis: ",package,"::",name)}
 status<-"completed-with-warnings"
},warning=function(w){warnings_seen<<-c(warnings_seen,paste0(current_stage,": ",conditionMessage(w)));invokeRestart("muffleWarning")},message=function(m){messages_seen<<-c(messages_seen,conditionMessage(m));invokeRestart("muffleMessage")}),error=function(e){detail<<-conditionMessage(e);if(runtime_ready)status<<-"FAIL2_ORIGINAL_ERROR"})
writeLines(capture.output(sessionInfo()),"session-info.txt");if(requireNamespace("jsonlite",quietly=TRUE))write_json(list(status=status,detail=detail,warnings=as.list(warnings_seen),messages=as.list(messages_seen),cases=cases,guarded_functions=guards,locale=Sys.getlocale(),analysis_installs_software=FALSE),"worker-report.json")
clean<-function(x)gsub("[\t\r\n]"," ",x);writeLines(c("status\tdetail\trequired_action",paste(status,clean(detail),clean(action),sep="\t")),"worker-status.tsv");quit(status=if(status=="completed-with-warnings")0L else if(runtime_ready)2L else 3L)
