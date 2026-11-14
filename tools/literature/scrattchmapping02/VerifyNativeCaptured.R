# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Re-execute the unchanged original numeric function against every complete RDS.
args <- commandArgs(TRUE)
stopifnot(length(args)==3L)
source.dir <- args[[1L]]; references <- args[[2L]]; destination <- args[[3L]]
if(file.exists(destination))stop("Use a NEW comparison record")
options(warn=1)
if(as.character(getRversion())!="4.5.3" || !requireNamespace("Matrix",quietly=TRUE) ||
   as.character(packageVersion("Matrix"))!="1.7.6") {
  cat("SKIP3: exact R 4.5.3 and Matrix 1.7.6 required; original comparison skipped\n")
  quit(status=3L)
}
original <- new.env(parent=globalenv())
sys.source(file.path(source.dir,"R/HANN_utils.R"),envir=original)
function.record <- readRDS(file.path(references,"original-function.rds"))
same <- function(a,b)identical(a,b,num.eq=FALSE,single.NA=FALSE,attrib.as.set=FALSE,ignore.environment=FALSE)
text <- function(x)if(is.na(x))"!NA"else paste(sprintf("%02x",as.integer(charToRaw(enc2utf8(x)))),collapse="")
stopifnot(same(formals(original$l2norm),function.record$formals),same(body(original$l2norm),function.record$body))
index <- read.delim(file.path(references,"cases.tsv"),stringsAsFactors=FALSE,check.names=FALSE)
matched <- errors <- warnings <- 0L
for(name in index$name){
  expected <- readRDS(file.path(references,name,"complete-original.rds"))
  inputs <- expected$inputs; x <- inputs$X; mode <- inputs$by
  result <- NULL; error <- NULL; conditions <- list()
  value <- tryCatch(withCallingHandlers(withVisible(original$l2norm(x,mode)),
    warning=function(c)conditions[[length(conditions)+1L]]<<-c,
    message=function(c)conditions[[length(conditions)+1L]]<<-c),
    error=function(c){error<<-c;conditions[[length(conditions)+1L]]<<-c;NULL})
  if(is.null(error))result <- value$value
  actual <- list(inputs=inputs,value=result,error=error,conditions=conditions,
                 visibility=if(is.null(value))NULL else value$visible)
  if(!same(actual,expected))stop(paste("Complete original numeric object/condition differs:",name))
  for(kind in c("warning","message")) {
    messages <- vapply(Filter(function(c)inherits(c,kind),conditions),conditionMessage,character(1))
    file <- file.path(references,name,if(kind=="warning")"warnings.tsv"else"messages.tsv")
    stopifnot(identical(unname(vapply(messages,text,character(1))),readLines(file,warn=FALSE)))
  }
  if(!is.null(error))stopifnot(identical(text(conditionMessage(error)),readLines(file.path(references,name,"error.tsv"),warn=FALSE)))
  matched <- matched+1L;errors <- errors+as.integer(!is.null(error))
  warnings <- warnings+sum(vapply(conditions,function(c)inherits(c,"warning"),logical(1)))
}
stopifnot(matched==318L,errors==15L,warnings==52L)
jsonlite::write_json(list(status="PASS_ALL318_COMPLETE_ORIGINAL_L2_RETURNS_INPUTS_ERRORS_AND_CONDITIONS",
  cases=matched,original_errors=errors,warning_messages=warnings,complete_R_condition_equality=TRUE,
  R_version=as.character(getRversion()),Matrix_version=as.character(packageVersion("Matrix")),
  whole_tool_qualified=FALSE,hierarchical_mapping_executed=FALSE,new_family_credit=0L,whole_goal_complete=FALSE),
  destination,pretty=TRUE,auto_unbox=TRUE)
cat("PASS_COMPLETE_ORIGINAL_L2_OBJECTS_AND_CONDITIONS",matched,"\n")
