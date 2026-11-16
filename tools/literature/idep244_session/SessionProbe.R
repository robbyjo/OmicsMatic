# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original iDEP loader in an actual unmodified Shiny session. Development probe.
args <- commandArgs(TRUE)
stopifnot(length(args)==1L)
.libPaths(readLines("libraries.txt",warn=FALSE),include.site=FALSE)
control <- jsonlite::fromJSON(args[[1]],simplifyVector=FALSE)
stopifnot(as.character(getRversion())=="4.6.1",
          as.character(packageVersion("shiny"))=="1.14.0",
          as.character(packageVersion("idepGolem"))=="2.4.4")
locale.result <- Sys.setlocale("LC_ALL","English_United States.utf8")
stopifnot(nzchar(locale.result),all(vapply(c("LC_COLLATE","LC_CTYPE","LC_MONETARY","LC_TIME"),
    Sys.getlocale,character(1))=="English_United States.utf8"))
stopifnot(identical(Sys.setlocale("LC_TIME","C"),"C"))
Sys.setenv(TZ="UTC")
source(control$audit,local=TRUE)
idep244.audit(control$source,"namespace-before.rds")
done <- FALSE;acknowledged <- FALSE;original <- NULL;failure <- NULL;returned <- FALSE
conditions <- list();phase <- "SESSION_SETUP";rng.before <- NULL;rng.after <- NULL
rng <- function()list(kind=RNGkind(),seed=get(".Random.seed",envir=globalenv(),inherits=FALSE))
record <- function(c,kind) {
  conditions[[length(conditions)+1L]] <<- list(kind=kind,phase=phase,condition=c,
    class=class(c),message=conditionMessage(c),call=conditionCall(c))
}
app <- shiny::shinyApp(ui=shiny::fluidPage(),server=function(input,output,session) {
  stopifnot(inherits(session,"ShinySession"),!inherits(session,"MockShinySession"))
  shiny::onSessionEnded(function()if(!acknowledged)shiny::stopApp(2L))
  shiny::observeEvent(input$start_token, {
    shiny::req(identical(input$start_token,control$token))
    if(done)stop("Duplicate original invocation refused")
    saveRDS(list(session_class=class(session),default_domain_class=class(shiny::getDefaultReactiveDomain()),
      genuine_session=identical(shiny::getDefaultReactiveDomain(),session),
      token=session$token),"session-context.rds",version=3,compress=FALSE)
    RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(42L)
    rng.before <<- rng();phase <<- "input_data"
    tryCatch(withCallingHandlers({
      original <<- idepGolem::input_data(expression_file=list(datapath="inputs/counts.csv"),
        experiment_file=NULL,go_button=0,demo_data_file=NULL,demo_metadata_file=NULL,
        max_group_name_length=20)
      returned <<- TRUE
    },warning=function(c)record(c,"warning"),message=function(c)record(c,"message"),
      error=function(c)record(c,"error")),error=function(c)failure <<- c)
    rng.after <<- rng()
    saveRDS(list(original_return=original,original_call_returned=returned,conditions=conditions,error=failure,
      RNG_before=rng.before,RNG_after=rng.after),"complete-loader-return.rds",version=3,compress=FALSE)
    idep244.audit(control$source,"namespace-after.rds")
    stopifnot(identical(rng.after,rng()))
    done <<- TRUE
    session$sendCustomMessage("omicsmatic_done",list(token=control$token,return_recorded=TRUE))
  },once=TRUE,ignoreNULL=TRUE)
  shiny::observeEvent(input$finish_ack, {
    shiny::req(identical(input$finish_ack,control$token),done)
    acknowledged <<- TRUE
    saveRDS(list(acknowledged=acknowledged,done=done),"session-acknowledgement.rds",version=3,compress=FALSE)
    shiny::stopApp(0L)
  },once=TRUE,ignoreNULL=TRUE)
})
app.result <- shiny::runApp(app,host="127.0.0.1",port=as.integer(control$port),
  launch.browser=FALSE,quiet=FALSE,test.mode=FALSE)
code <- if(identical(app.result,0L)&&done&&acknowledged&&is.null(failure))0L else 2L
jsonlite::write_json(list(status=if(code==0L)"PASS_GENUINE_SHINY_LOADER_PROBE_NOT_ANALYSIS" else
  "FAIL2_GENUINE_SHINY_LOADER_PROBE",exit_code=code,done=done,acknowledged=acknowledged,
  error=if(is.null(failure))NULL else conditionMessage(failure),
  original_call_returned=returned,original_return_is_NULL=returned&&is.null(original),original_dimensions=dim(original$data),
  original_sample_names=if(is.null(original))NULL else as.list(colnames(original$data)),
  original_condition_count=length(conditions),catalog_credit_added=0),
  "R-status.json",auto_unbox=TRUE,pretty=TRUE,null="null",digits=17)
writeLines(capture.output(sessionInfo()),"SESSION.txt")
quit(save="no",status=code,runLast=FALSE)
