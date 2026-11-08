# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Execute the complete original CLI in its own environment; never replace a
# namespace function, parser, optimizer, sink or original program statement.
.omics_root<-Sys.getenv("OMICS_LEAFCUTTER_SOURCE")
.omics_library<-Sys.getenv("OMICS_LEAFCUTTER_LIBRARY")
.omics_home<-Sys.getenv("OMICS_LEAFCUTTER_CASE")
stopifnot(nzchar(.omics_root),nzchar(.omics_library),nzchar(.omics_home))
library(jsonlite)
.libPaths(c(.omics_library,.libPaths()))
library(leafcutter)
.omics_namespace<-asNamespace("leafcutter")
stopifnot(normalizePath(getNamespaceInfo(.omics_namespace,"path"))==normalizePath(file.path(.omics_library,"leafcutter")))
.omics_checked<-character()
for(.omics_path in list.files(file.path(.omics_root,"original/leafcutter/R"),pattern="\\.R$",full.names=TRUE))for(.omics_expr in parse(.omics_path,keep.source=FALSE)) {
  if(is.call(.omics_expr)&&length(.omics_expr)>=3&&(identical(.omics_expr[[1]],as.name("<-"))||identical(.omics_expr[[1]],as.name("=")))&&is.symbol(.omics_expr[[2]])&&is.call(.omics_expr[[3]])&&identical(.omics_expr[[3]][[1]],as.name("function"))) {
    .omics_name<-as.character(.omics_expr[[2]]);.omics_expected<-eval(.omics_expr[[3]],envir=.omics_namespace);.omics_actual<-get(.omics_name,envir=.omics_namespace,inherits=FALSE)
    stopifnot(identical(paste(deparse(formals(.omics_expected)),collapse="\n"),paste(deparse(formals(.omics_actual)),collapse="\n")),identical(paste(deparse(body(.omics_expected)),collapse="\n"),paste(deparse(body(.omics_actual)),collapse="\n")))
    .omics_checked<-c(.omics_checked,.omics_name)
  }
}
stopifnot(length(.omics_checked)==24,as.character(packageVersion("leafcutter"))=="0.2.9")
.omics_probe<-c(pi,1e-300,1e300,1+.Machine$double.eps)
stopifnot(identical(.omics_probe,fromJSON(toJSON(.omics_probe,digits=I(17)))))
.omics_save<-function(value,name)write_json(value,file.path(.omics_home,name),auto_unbox=TRUE,pretty=TRUE,digits=I(17),null="null",na="string",matrix="rowmajor")
.omics_counter<-0L
.omics_condition<-function(condition,type) {
  .omics_counter<<-.omics_counter+1L
  .omics_save(list(type=type,message=conditionMessage(condition),class=class(condition),pid=Sys.getpid(),ordinal=.omics_counter),paste0("condition-",Sys.getpid(),"-",.omics_counter,".json"))
  # Continue the original condition handling; do not muffle/replace warnings.
}
.omics_environment<-new.env(parent=.GlobalEnv)
.omics_error<-NULL
tryCatch(withCallingHandlers(sys.source(file.path(.omics_root,"original/scripts/leafcutter_ds.R"),envir=.omics_environment),warning=function(w).omics_condition(w,"warning"),message=function(m).omics_condition(m,"message")),error=function(e){.omics_error<<-e})
if(!is.null(.omics_error)) {
  if(exists("results",envir=.omics_environment,inherits=FALSE))saveRDS(.omics_environment$results,file.path(.omics_home,"original-results-provisional.rds"))
  .omics_available<-intersect(c("counts","meta","group_names","numeric_x","confounders","opt"),ls(.omics_environment,all.names=TRUE))
  if(length(.omics_available))saveRDS(mget(.omics_available,envir=.omics_environment,inherits=FALSE),file.path(.omics_home,"original-input-state-provisional.rds"))
  .omics_save(list(status="FAIL2_ORIGINAL_DIFFERENTIAL_PROGRAM",message=conditionMessage(.omics_error),class=class(.omics_error)),"source-error.json")
  stop(.omics_error)
}
.omics_results<-.omics_environment$results
saveRDS(.omics_results,file.path(.omics_home,"original-results.rds"))
saveRDS(list(counts=.omics_environment$counts,metadata=.omics_environment$meta,groups=.omics_environment$group_names,numeric_groups=.omics_environment$numeric_x,confounders=.omics_environment$confounders,options=.omics_environment$opt),file.path(.omics_home,"original-input-state.rds"))
.omics_counts<-.omics_environment$counts
.omics_x<-.omics_environment$numeric_x
.omics_confounders<-.omics_environment$confounders
.omics_options<-.omics_environment$opt
.omics_encoding<-list()
if(ncol(.omics_environment$meta)>2)for(.omics_column in names(.omics_environment$meta)[3:ncol(.omics_environment$meta)]) {
  .omics_values<-.omics_environment$meta[[.omics_column]]
  if(is.numeric(.omics_values)) {
    .omics_scaled<-scale(.omics_values)
    .omics_encoding[[.omics_column]]<-list(type="numeric",center=as.numeric(attr(.omics_scaled,"scaled:center")),scale=as.numeric(attr(.omics_scaled,"scaled:scale")))
  } else if(is.logical(.omics_values)) {
    .omics_encoding[[.omics_column]]<-list(type="logical",levels=c(FALSE,TRUE))
  } else .omics_encoding[[.omics_column]]<-list(type="factor",levels=levels(factor(.omics_values)))
}
.omics_cluster_ids<-paste(get_intron_meta(rownames(.omics_counts))$chr,get_intron_meta(rownames(.omics_counts))$clu,sep=":")
.omics_exported<-list()
.omics_filters<-c("Too many introns in cluster","<=1 junction in cluster","<=1 sample with coverage>0","<=1 sample with coverage>min_coverage","<2 introns used in >=min_samples_per_intron samples","Not enough valid samples")
for(.omics_cluster in names(.omics_results)) {
  .omics_fit<-.omics_results[[.omics_cluster]]
  if(is.character(.omics_fit)) {
    .omics_exported[[.omics_cluster]]<-list(status=.omics_fit)
    if(!(.omics_fit %in% .omics_filters))stop("Original cluster returned a failed/timeout disposition: ",.omics_cluster," ",.omics_fit)
    next
  }
  if(inherits(.omics_fit,"error"))stop("Original cluster returned an error: ",.omics_cluster," ",conditionMessage(.omics_fit))
  stopifnot(.omics_fit$fit_null$return_code==0,.omics_fit$fit_full$return_code==0,is.finite(.omics_fit$fit_null$value),is.finite(.omics_fit$fit_full$value),is.finite(.omics_fit$loglr),is.finite(.omics_fit$lrtp),.omics_fit$lrtp>=0,.omics_fit$lrtp<=1)
  .omics_members<-which(.omics_cluster_ids==.omics_cluster)
  .omics_y<-t(.omics_counts[.omics_members,,drop=FALSE]);.omics_totals<-rowSums(.omics_y);.omics_sample_mask<-.omics_totals>0
  .omics_y<-.omics_y[.omics_sample_mask,,drop=FALSE];.omics_intron_mask<-colSums(.omics_y>0)>=.omics_options$min_samples_per_intron;.omics_y<-.omics_y[,.omics_intron_mask,drop=FALSE]
  .omics_full<-cbind(1,.omics_x[.omics_sample_mask]);.omics_null<-.omics_full[,1,drop=FALSE]
  if(!is.null(.omics_confounders)) {
    .omics_ch<-.omics_confounders[.omics_sample_mask,,drop=FALSE];.omics_ch<-.omics_ch[,apply(.omics_ch,2,sd)>0,drop=FALSE]
    .omics_full<-cbind(.omics_full,.omics_ch);.omics_null<-cbind(.omics_null,.omics_ch)
  }
  .omics_exported[[.omics_cluster]]<-list(status="Success",samples=colnames(.omics_counts)[.omics_sample_mask],introns=colnames(.omics_y),sample_mask=.omics_sample_mask,intron_mask=.omics_intron_mask,counts=unname(.omics_y),full=unname(.omics_full),null=unname(.omics_null),fit=.omics_fit)
}
.omics_effects<-leaf_cutter_effect_sizes(.omics_results)
.omics_table<-cluster_results_table(.omics_results)
stopifnot(all(is.finite(.omics_effects$logef)),all(is.finite(.omics_effects$baseline)),all(is.finite(.omics_effects$perturbed)),all(is.finite(.omics_effects$deltapsi)))
.omics_save(list(status="PASS_COMPLETE_ORIGINAL_DIFFERENTIAL_PROGRAM_ONLY",samples=colnames(.omics_counts),introns=rownames(.omics_counts),counts=unname(as.matrix(.omics_counts)),metadata=.omics_environment$meta,metadata_classes=lapply(.omics_environment$meta,class),metadata_encoding=.omics_encoding,group_names=.omics_environment$group_names,numeric_groups=.omics_x,confounders=if(is.null(.omics_confounders))NULL else unname(.omics_confounders),results=.omics_exported,raw_cluster_table=.omics_table,raw_effects=.omics_effects,reported_cluster_table=.omics_environment$cluster_table,reported_gene_missing=if("genes" %in% names(.omics_environment$cluster_table))is.na(.omics_environment$cluster_table$genes)else NULL,reported_effects=.omics_environment$effect_size_table,namespace_functions_checked=.omics_checked,original_namespace_patched=FALSE,numeric_export_significant_digits=17L,warning_scope="All signaled R warnings/messages are captured by standard calling handlers. Original debug=FALSE may suppress other optimizer console diagnostics. Discarded null-refit return codes are not reported by the original API."),"source-report.json")
