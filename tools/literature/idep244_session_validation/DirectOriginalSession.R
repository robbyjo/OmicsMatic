# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent original literal calls; no Counts.R, Request.py or run.py imports.
p <- commandArgs(TRUE)
stopifnot(length(p)==1L)
.libPaths(readLines("libraries.txt",warn=FALSE),include.site=FALSE)
control <- jsonlite::fromJSON(p[[1]],simplifyVector=FALSE)
stopifnot(as.character(getRversion())=="4.6.1",as.character(packageVersion("shiny"))=="1.14.0")
source("engine/SourceAudit.R",local=TRUE)
literal.calls <- function() {
  locale.result <- Sys.setlocale("LC_ALL","English_United States.utf8")
  stopifnot(nzchar(locale.result),Sys.getlocale("LC_CTYPE")=="English_United States.utf8")
  Sys.setlocale("LC_TIME","C");Sys.setenv(TZ="UTC");mem.maxVSize(4096)
  stopifnot(requireNamespace("BiocManager",quietly=TRUE),as.character(packageVersion("idepGolem"))=="2.4.4",
    as.character(packageVersion("DESeq2"))=="1.52.0")
  idep244.audit(control$source,"original/namespace-before.rds")
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
  expression.path <- paste0("inputs/expression/",control$filename)
  tryCatch(withCallingHandlers({
    phase <- "input_data"
    loaded <- idepGolem::input_data(expression_file=list(datapath=expression.path),experiment_file=NULL,
      go_button=0,demo_data_file=NULL,demo_metadata_file=NULL,max_group_name_length=30L)
    keep("input_data",loaded)
    phase <- "convert_data"
    mapped <- idepGolem::convert_data(converted=NULL,data=loaded$data,
                                     no_id_conversion=TRUE,multiple_map="max_sd")
    keep("convert_data",mapped)
    phase <- "pre_process"
    processed <- idepGolem::pre_process(data=mapped$data,missing_value="geneMedian",data_file_format=1,
      low_filter_fpkm=1,n_min_samples_fpkm=1,log_transform_fpkm=TRUE,log_start_fpkm=1,
      min_counts=0.5,n_min_samples_count=1L,counts_transform=1L,counts_log_start=4L,no_fdr=FALSE)
    keep("pre_process",processed)
    phase <- "limma_value"
    result <- idepGolem::limma_value(data_file_format=1,counts_deg_method=3,
      raw_counts=processed$raw_counts,limma_p_val=0.05,limma_fc=2L,
      select_model_comprions=NULL,sample_info=loaded$sample_info,select_factors_model=NULL,
      select_interactions=NULL,select_block_factors_model=NULL,factor_reference_levels=NULL,
      processed_data=processed$data,counts_log_start=4L,p_vals=processed$p_vals,
      threshold_wald_test=FALSE,independent_filtering=TRUE,descr=processed$descr)
    keep("limma_value",result)
  },warning=function(c)record(c,"warning"),message=function(c)record(c,"message"),
    error=function(c){record(c,"error");failure.calls <<- sys.calls()}),error=function(c)failure <<- c)
  after <- rng()
  idep244.audit(control$source,"original/namespace-after.rds")
  stopifnot(identical(after,rng()))
  saveRDS(list(original_returns=values,original_conditions=conditions,RNG_before=before,RNG_after=after),
    "original/complete-original-returns.rds",version=3,compress=FALSE)
  saveRDS(list(conditions=conditions,error=failure,error_calls=failure.calls),
    "original/complete-conditions.rds",version=3,compress=FALSE)
  writeLines(capture.output(sessionInfo()),"original/SESSION.txt")
  code <- if(is.null(failure)&&is.matrix(values$limma_value$results))0L else 2L
  jsonlite::write_json(list(status="ACTUAL_INDEPENDENT_LITERAL_ORIGINAL_SHINY_CALLS_NOT_ADMISSION",
    exit_code=code,stages=as.list(names(values)),original_error=if(is.null(failure))NULL else
    list(class=as.list(class(failure)),message=conditionMessage(failure),call=deparse(conditionCall(failure))),
    result_dimensions=if(code==0L)dim(values$limma_value$results)else NULL,
    original_algorithms_patched=FALSE,analysis_admitted=FALSE,catalog_credit_added=0),
    "DIRECT.json",auto_unbox=TRUE,pretty=TRUE,null="null")
  code
}
done <- FALSE;acknowledged <- FALSE;execution.code <- 2L
app <- shiny::shinyApp(ui=shiny::fluidPage(),server=function(input,output,session) {
  stopifnot(inherits(session,"ShinySession"),!inherits(session,"MockShinySession"))
  shiny::onSessionEnded(function()if(!acknowledged)shiny::stopApp(2L))
  shiny::observeEvent(input$start_token, {
    shiny::req(identical(input$start_token,control$token))
    stopifnot(!done,identical(shiny::getDefaultReactiveDomain(),session))
    saveRDS(list(session_class=class(session),default_domain_class=class(shiny::getDefaultReactiveDomain()),
      genuine_session=TRUE,token=session$token),"original/session-context.rds",version=3,compress=FALSE)
    execution.code <<- literal.calls()
    done <<- TRUE
    session$sendCustomMessage("omicsmatic_done",list(token=control$token,return_recorded=TRUE))
  },once=TRUE,ignoreNULL=TRUE)
  shiny::observeEvent(input$finish_ack, {
    shiny::req(identical(input$finish_ack,control$token),done)
    acknowledged <<- TRUE
    shiny::stopApp(execution.code)
  },once=TRUE,ignoreNULL=TRUE)
})
result <- shiny::runApp(app,host="127.0.0.1",port=as.integer(control$port),launch.browser=FALSE,quiet=FALSE)
stopifnot(done,acknowledged)
quit(save="no",status=as.integer(result),runLast=FALSE)
