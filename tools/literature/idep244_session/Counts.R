# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Same original API calls as the preserved v1 count worker, inside a real session.
idep244.session.counts <- function(control,request) {
  phase <- "READINESS";failure <- NULL;calls <- NULL;returns <- list()
  conditions <- list();rng.before <- NULL;rng.after <- NULL;started <- FALSE
  returned <- FALSE;recorded <- FALSE;adapter.warnings <- character()
  rng <- function()list(kind=RNGkind(),
    seed_exists=exists(".Random.seed",envir=globalenv(),inherits=FALSE),
    seed=if(exists(".Random.seed",envir=globalenv(),inherits=FALSE))
      get(".Random.seed",envir=globalenv(),inherits=FALSE)else NULL)
  record <- function(condition,kind) {
    conditions[[length(conditions)+1L]] <<- list(kind=kind,phase=phase,
      condition=condition,class=class(condition),message=conditionMessage(condition),
      call=conditionCall(condition))
  }
  stage <- function(name,fun) {
    phase <<- name
    value <- fun()
    returns[name] <<- list(value) # Keep original NULL returns as named list entries.
    saveRDS(value,file.path("original/stages",paste0(name,".rds")),version=3,compress=FALSE)
    value
  }
  vec <- function(x)if(is.null(x))NULL else unlist(x,use.names=FALSE)
  tryCatch(withCallingHandlers({
    if (!identical(as.character(getRversion()),"4.6.1"))
      stop("Original R4.6.1 profile required")
    locale.result <- Sys.setlocale("LC_ALL",request$locale)
    if (!nzchar(locale.result)||!all(vapply(c("LC_COLLATE","LC_CTYPE","LC_MONETARY","LC_TIME"),
        Sys.getlocale,character(1))==request$locale))
      stop("Explicit Windows UTF-8 caller locale is unavailable")
    if (!identical(Sys.setlocale("LC_TIME","C"),"C"))stop("C timestamp locale is unavailable")
    Sys.setenv(TZ="UTC")
    heap <- mem.maxVSize(request$limits$vector_heap_mib)
    if (!is.finite(heap)||heap!=request$limits$vector_heap_mib)
      stop("Requested R vector-heap limit cannot be enforced")
    source("engine/legacy/SourceAudit.R",local=TRUE)
    stopifnot(as.character(packageVersion("idepGolem"))=="2.4.4",
              as.character(packageVersion("DESeq2"))=="1.52.0",
              requireNamespace("BiocManager",quietly=TRUE))
    idep244.audit(control$source,"original/namespace-before.rds")
    phase <- "CALLER_RNG"
    do.call(RNGkind,as.list(vec(request$rng$kind)))
    set.seed(as.integer(request$rng$seed))
    rng.before <- rng()
    saveRDS(list(RNG_before=rng.before,locale=Sys.getlocale(),TZ=Sys.getenv("TZ"),
      vector_heap_mib=heap,search_path=search(),loaded_namespaces=sort(loadedNamespaces())),
      "original/caller-context.rds",version=3,compress=FALSE)
    started <- TRUE
    loaded <- stage("input_data",function()idepGolem::input_data(
      expression_file=list(datapath=request$expression),
      experiment_file=if(is.null(request$design))NULL else list(datapath=request$design),
      go_button=0,demo_data_file=NULL,demo_metadata_file=NULL,
      max_group_name_length=request$load$max_group_name_length))
    if (!is.list(loaded)||is.null(loaded$data))
      stop("Original input_data returned no expression matrix; original return retained")
    converted <- NULL
    if (identical(request$mapping$mode,"database")) {
      phase <- "REFERENCE_READINESS"
      DATAPATH <- paste0(request$mapping$reference_dir,"/")
      org_info_file <- paste0(DATAPATH,"demo/orgInfo.db")
      # These are the original app's caller globals, not replacements of functions.
      assign("DATAPATH",DATAPATH,envir=globalenv())
      assign("org_info_file",org_info_file,envir=globalenv())
      if (!file.exists(org_info_file)||!file.exists(paste0(DATAPATH,"demo/demo_data_info.csv")))
        stop("Captured reference disappeared; refusing the original automatic-download path")
      idep.data <- stage("get_idep_data",function()idepGolem::get_idep_data(datapath=DATAPATH))
      ix <- which(idep.data$org_info$id==request$mapping$organism_id)
      if (length(ix)!=1L || !file.exists(paste0(DATAPATH,"db/",idep.data$org_info[ix,"file"])))
        stop("Captured selected organism database is unavailable")
      converted <- stage("convert_id",function()idepGolem::convert_id(
        query=rownames(loaded$data),idep_data=idep.data,
        select_org=request$mapping$organism_id,max_sample_ids=request$mapping$max_sample_ids))
      if (is.null(converted))
        adapter.warnings <- c(adapter.warnings,
          "Original convert_id returned NULL. Original convert_data therefore retains unconverted IDs; no successful mapping is inferred.")
    }
    mapped <- stage("convert_data",function()idepGolem::convert_data(
      converted=converted,data=loaded$data,
      no_id_conversion=identical(request$mapping$mode,"disabled"),
      multiple_map=request$mapping$multiple_map))
    processed <- stage("pre_process",function()idepGolem::pre_process(
      data=mapped$data,missing_value=request$preprocess$missing_value,data_file_format=1,
      low_filter_fpkm=1,n_min_samples_fpkm=1,log_transform_fpkm=TRUE,log_start_fpkm=1,
      min_counts=request$preprocess$min_counts,n_min_samples_count=request$preprocess$n_min_samples_count,
      counts_transform=request$preprocess$counts_transform,
      counts_log_start=request$preprocess$counts_log_start,no_fdr=FALSE))
    if (!is.list(processed)||is.null(processed$raw_counts))
      stop("Original pre_process returned no usable count matrix; complete return retained")
    result <- stage("limma_value",function()idepGolem::limma_value(
      data_file_format=1,counts_deg_method=3,raw_counts=processed$raw_counts,
      limma_p_val=request$analysis$fdr,limma_fc=request$analysis$min_fold_change,
      select_model_comprions=vec(request$analysis$comparisons),sample_info=loaded$sample_info,
      select_factors_model=vec(request$analysis$factors),select_interactions=vec(request$analysis$interactions),
      select_block_factors_model=vec(request$analysis$block_factors),
      factor_reference_levels=vec(request$analysis$reference_levels),
      processed_data=processed$data,counts_log_start=request$preprocess$counts_log_start,
      p_vals=processed$p_vals,threshold_wald_test=request$analysis$threshold_wald_test,
      independent_filtering=request$analysis$independent_filtering,descr=processed$descr))
    returned <- TRUE
    if (!is.list(result)||is.null(result$results)||!is.matrix(result$results)||
        nrow(result$results)==0L||ncol(result$results)==0L)
      stop("Original limma_value returned an error/no complete DEG matrix; original value retained")
    rng.after <- rng()
    phase <- "POST_SOURCE_AUDIT"
    idep244.audit(control$source,"original/namespace-after.rds")
    stopifnot(identical(rng.after,rng()))
    phase <- "ORIGINAL_RETURN_RECORDING"
    saveRDS(list(original_returns=returns,original_conditions=conditions,
      RNG_before=rng.before,RNG_after=rng.after),
      "original/complete-original-returns.rds",version=3,compress=FALSE)
    recorded <- TRUE
    if (grepl("Only the first",result$exp_type,fixed=TRUE))
      adapter.warnings <- c(adapter.warnings,
        paste("Original comparison truncation retained:",result$exp_type))
    phase <- "COMPLETE"
  },warning=function(c)record(c,"warning"),message=function(c)record(c,"message"),
  error=function(c) {
    record(c,"error");calls <<- sys.calls()
  }),error=function(c)failure <<- c)
  if (!is.null(failure)) {
    rng.after <- rng()
    saveRDS(list(original_returns=returns,original_conditions=conditions,error=failure,
      error_calls=calls,RNG_before=rng.before,RNG_after=rng.after),
      "original/complete-failed-returns.rds",version=3,compress=FALSE)
    # A native error must not bypass the post-call namespace audit. Preserve the
    # original error/partials first, then check unchanged original function bodies.
    idep244.audit(control$source,"original/namespace-after.rds")
    stopifnot(identical(rng.after,rng()))
  }
  saveRDS(list(conditions=conditions,error=failure,error_calls=calls),
    "original/complete-conditions.rds",version=3,compress=FALSE)
  writeLines(capture.output(sessionInfo()),"original/SESSION.txt")
  code <- if(is.null(failure)&&returned&&recorded)0L else 2L
  status <- list(status=if(code==0L)"PASS_ORIGINAL_IDEP244_COMPLETE_COUNT_RETURN_RECORDED" else
    "FAIL2_ORIGINAL_IDEP244_NO_COMPLETE_RESULT",phase=phase,exit_code=code,
    original_calls_started=started,original_dispatcher_returned=returned,
    complete_original_return_recorded=recorded,
    original_stages=as.list(unname(names(returns))),original_condition_count=length(conditions),
    captured_R_condition_messages=lapply(conditions,function(x)
      list(kind=x$kind,phase=x$phase,message=x$message)),
    raw_native_diagnostic_counts_complete=FALSE,
    original_input_dimensions=if("input_data"%in%names(returns))dim(returns$input_data$data)else NULL,
    result_dimensions=if(code==0L)dim(result$results)else NULL,
    comparisons=if(code==0L)as.list(unname(result$comparisons))else NULL,
    exp_type=if(code==0L)result$exp_type else NULL,
    adapter_warnings=as.list(unname(adapter.warnings)),
    error=if(is.null(failure))NULL else list(class=class(failure),message=conditionMessage(failure)),
    vector_heap_mib=mem.maxVSize(),network_isolated=FALSE,
    generated_original_R_script_executed=FALSE,final_adapter_admission_established=FALSE,
    catalog_credit_added=0,whole_tool_qualified=FALSE,paper_reproduced=FALSE)
  jsonlite::write_json(status,"original/R-status.json",pretty=TRUE,auto_unbox=TRUE,
    null="null",na="string",digits=17)
  cat(status$status,"\n")
  list(exit_code=code,status=status)
}
