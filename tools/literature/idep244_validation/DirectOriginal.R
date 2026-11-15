# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent literal original calls for the declared synthetic controls.
# This program does not source/import Worker.R, run.py or request Schema.py.
p <- commandArgs(TRUE)
stopifnot(length(p)==5L)
cases <- p[[1]];source.root <- p[[2]];libraries <- p[[3]];audit <- p[[4]];case <- p[[5]]
stopifnot(case %in% c("disabled","mapped","designed","original-rank-error","original-no-counts"))
.libPaths(readLines(libraries,warn=FALSE),include.site=FALSE)
locale.result <- Sys.setlocale("LC_ALL","English_United States.utf8")
stopifnot(nzchar(locale.result),Sys.getlocale("LC_CTYPE")=="English_United States.utf8")
Sys.setlocale("LC_TIME","C");Sys.setenv(TZ="UTC");mem.maxVSize(4096)
requireNamespace("jsonlite",quietly=TRUE)
requireNamespace("BiocManager",quietly=TRUE)
source(audit,local=TRUE)
dir.create("original/stages",recursive=TRUE)
dir.create("inputs/expression",recursive=TRUE)
name <- if(case=="original-no-counts")"all-zero.csv"else"counts.csv"
stopifnot(file.copy(file.path(cases,name),file.path("inputs/expression",name)))
expression.path <- paste0("inputs/expression/",name)
experiment <- NULL
if (case %in% c("designed","original-rank-error")) {
  dir.create("inputs/design")
  stopifnot(file.copy(file.path(cases,"design.csv"),"inputs/design/design.csv"))
  experiment <- list(datapath="inputs/design/design.csv")
}
if (case=="mapped") {
  reference <- file.path(cases,"synthetic-reference")
  for (n in list.files(reference,recursive=TRUE,all.files=TRUE,no..=TRUE,include.dirs=FALSE)) {
    dest <- file.path("inputs/reference",n)
    dir.create(dirname(dest),recursive=TRUE,showWarnings=FALSE)
    stopifnot(file.copy(file.path(reference,n),dest))
  }
}
idep244.audit(source.root,"original/namespace-before.rds")
RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(24405L)
rng <- function()list(kind=RNGkind(),seed_exists=exists(".Random.seed",globalenv(),inherits=FALSE),
                     seed=get(".Random.seed",globalenv(),inherits=FALSE))
before <- rng()
saveRDS(list(RNG_before=before,locale=Sys.getlocale(),TZ=Sys.getenv("TZ"),
  vector_heap_mib=mem.maxVSize(),search_path=search(),loaded_namespaces=sort(loadedNamespaces())),
  "original/caller-context.rds",version=3,compress=FALSE)
conditions <- list();values <- list();phase <- NULL;failure <- NULL;failure.calls <- NULL
keep <- function(name,value) {
  values[name] <<- list(value)
  saveRDS(value,file.path("original/stages",paste0(name,".rds")),version=3,compress=FALSE)
  invisible(value)
}
record <- function(c,kind)conditions[[length(conditions)+1L]] <<- list(
  kind=kind,phase=phase,condition=c,class=class(c),message=conditionMessage(c),call=conditionCall(c))
tryCatch(withCallingHandlers({
  phase <- "input_data"
  loaded <- idepGolem::input_data(
    expression_file=list(datapath=expression.path),experiment_file=experiment,
    go_button=0,demo_data_file=NULL,demo_metadata_file=NULL,max_group_name_length=30L)
  keep("input_data",loaded)
  converted <- NULL
  if (case=="mapped") {
    DATAPATH <- "inputs/reference/"
    org_info_file <- "inputs/reference/demo/orgInfo.db"
    phase <- "get_idep_data"
    data <- idepGolem::get_idep_data(datapath=DATAPATH)
    keep("get_idep_data",data)
    phase <- "convert_id"
    converted <- idepGolem::convert_id(rownames(loaded$data),idep_data=data,
                                      select_org="244",max_sample_ids=100L)
    keep("convert_id",converted)
  }
  phase <- "convert_data"
  mapped <- idepGolem::convert_data(converted=converted,data=loaded$data,
                                   no_id_conversion=case!="mapped",multiple_map="max_sd")
  keep("convert_data",mapped)
  phase <- "pre_process"
  processed <- idepGolem::pre_process(data=mapped$data,missing_value="geneMedian",data_file_format=1,
    low_filter_fpkm=1,n_min_samples_fpkm=1,log_transform_fpkm=TRUE,log_start_fpkm=1,
    min_counts=0.5,n_min_samples_count=1L,counts_transform=1L,counts_log_start=4L,no_fdr=FALSE)
  keep("pre_process",processed)
  if (!is.null(processed$raw_counts)) {
    phase <- "limma_value"
    designed <- case %in% c("designed","original-rank-error")
    result <- idepGolem::limma_value(data_file_format=1,counts_deg_method=3,
      raw_counts=processed$raw_counts,limma_p_val=0.05,limma_fc=2L,
      select_model_comprions=if(designed)c("Condition: B vs. A","Condition: C vs. A")else NULL,
      sample_info=loaded$sample_info,select_factors_model=if(designed)"Condition"else NULL,
      select_interactions=NULL,select_block_factors_model=if(case=="original-rank-error")"Batch"else NULL,
      factor_reference_levels=if(designed)"Condition:A"else NULL,processed_data=processed$data,
      counts_log_start=4L,p_vals=processed$p_vals,threshold_wald_test=FALSE,
      independent_filtering=TRUE,descr=processed$descr)
    keep("limma_value",result)
  }
},warning=function(c)record(c,"warning"),message=function(c)record(c,"message"),
  error=function(c){record(c,"error");failure.calls <<- sys.calls()}),
error=function(c)failure <<- c)
after <- rng()
idep244.audit(source.root,"original/namespace-after.rds")
saveRDS(list(original_returns=values,original_conditions=conditions,RNG_before=before,RNG_after=after),
  "original/complete-original-returns.rds",version=3,compress=FALSE)
saveRDS(list(conditions=conditions,error=failure,error_calls=failure.calls),
  "original/complete-conditions.rds",version=3,compress=FALSE)
writeLines(capture.output(sessionInfo()),"original/SESSION.txt")
jsonlite::write_json(list(status="CAPTURED_ACTUAL_INDEPENDENT_ORIGINAL_IDEP244_CALLS_NOT_ADAPTER_ADMISSION",
  case=case,stages=as.list(names(values)),original_error=if(is.null(failure))NULL else
    list(class=as.list(class(failure)),message=conditionMessage(failure),call=deparse(conditionCall(failure))),
  return_class=if("limma_value"%in%names(values))as.list(class(values$limma_value))else NULL,
  result_dimensions=if(is.list(values$limma_value))dim(values$limma_value$results)else NULL,
  independent_literals_not_generated_from_adapter_calls=TRUE,source_algorithms_patched=FALSE,
  analysis_admitted=FALSE,catalog_credit_added=0),
  "DIRECT.json",auto_unbox=TRUE,pretty=TRUE,null="null")
