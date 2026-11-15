# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# An actual original Shiny session supplies the original notification context.
# All scientific work remains in unchanged iDEP/DESeq2 namespace functions.
args <- commandArgs(TRUE)
stopifnot(length(args)==1L)
writeLines(as.character(Sys.getpid()),"worker-rterm-pid.txt")
.libPaths(readLines("libraries.txt",warn=FALSE),include.site=FALSE)
control <- jsonlite::fromJSON(args[[1]],simplifyVector=FALSE)
.libPaths(unlist(control$libraries,use.names=FALSE),include.site=FALSE)
request <- jsonlite::fromJSON("effective-request.json",simplifyVector=FALSE)
stopifnot(identical(request$profile,"original-idep244-shiny-r461-windows"),
          identical(request$schema,2L),as.character(packageVersion("shiny"))=="1.14.0")
source("engine/Counts.R",local=TRUE)
done <- FALSE;acknowledged <- FALSE;genuine <- FALSE;execution <- NULL
app <- shiny::shinyApp(ui=shiny::fluidPage(),server=function(input,output,session) {
  stopifnot(inherits(session,"ShinySession"),!inherits(session,"MockShinySession"))
  shiny::onSessionEnded(function()if(!acknowledged)shiny::stopApp(2L))
  shiny::observeEvent(input$start_token, {
    shiny::req(identical(input$start_token,control$token))
    if(done)stop("Duplicate original count invocation refused")
    genuine <<- identical(shiny::getDefaultReactiveDomain(),session)
    stopifnot(genuine)
    saveRDS(list(session_class=class(session),
      default_domain_class=class(shiny::getDefaultReactiveDomain()),genuine_session=genuine,
      token=session$token),"original/session-context.rds",version=3,compress=FALSE)
    execution <<- idep244.session.counts(control,request)
    done <<- TRUE
    session$sendCustomMessage("omicsmatic_done",list(token=control$token,return_recorded=TRUE))
  },once=TRUE,ignoreNULL=TRUE)
  shiny::observeEvent(input$finish_ack, {
    shiny::req(identical(input$finish_ack,control$token),done)
    acknowledged <<- TRUE
    saveRDS(list(acknowledged=TRUE,done=TRUE,token=control$token),
      "original/session-acknowledgement.rds",version=3,compress=FALSE)
    shiny::stopApp(as.integer(execution$exit_code))
  },once=TRUE,ignoreNULL=TRUE)
})
app.result <- shiny::runApp(app,host="127.0.0.1",port=as.integer(control$port),
  launch.browser=FALSE,quiet=FALSE,test.mode=FALSE)
code <- if(done&&acknowledged&&genuine&&identical(app.result,0L)&&execution$exit_code==0L)0L else 2L
jsonlite::write_json(list(status="ORIGINAL_SHINY_SESSION_RECEIPT_NOT_FINAL_ADMISSION",
  acknowledged=acknowledged,done=done,genuine_session=genuine,token=control$token,
  exit_code=code,full_GUI_qualified=FALSE),"SESSION-COMPLETED.json",
  auto_unbox=TRUE,pretty=TRUE,null="null",digits=17)
quit(save="no",status=code,runLast=FALSE)
