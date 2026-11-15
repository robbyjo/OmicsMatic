# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Exercise the actual comparator on unchanged and deliberately altered saved objects.
args <- commandArgs(TRUE);stopifnot(length(args)==5L)
helper <- args[[1L]];reference <- args[[2L]];error.reference <- args[[3L]]
missing.reference <- args[[4L]];out <- args[[5L]]
stopifnot(!file.exists(out));dir.create(out)
rscript <- file.path(R.home("bin"),"Rscript")
records <- list()
check <- function(name,ref,change=NULL,error=FALSE) {
  path <- file.path(out,name);dir.create(path)
  for(file in list.files(ref,full.names=TRUE))if(!dir.exists(file))
    stopifnot(file.copy(file,file.path(path,basename(file))))
  if(error)stopifnot(file.copy(file.path(path,"complete-rng-before-after.rds"),
                             file.path(path,"failed-analysis-rng-before-after.rds")))
  if(!is.null(change))change(path)
  code <- system2(rscript,c("--vanilla",shQuote(helper),shQuote(ref),shQuote(path),
    shQuote(file.path(path,"COMPARISON.json"))),stdout=file.path(path,"compare.stdout"),stderr=file.path(path,"compare.stderr"))
  expected <- if(is.null(change))0L else 1L
  records[[name]] <<- list(exit_code=code,expected_exit_code=expected)
  stopifnot(identical(as.integer(code),expected))
}
value.change <- function(fun)function(path) {
  file <- file.path(path,"complete-result.rds");value <- readRDS(file)
  value <- fun(value);saveRDS(value,file,version=3)
}
condition.change <- function(fun)function(path) {
  file <- file.path(path,"complete-conditions.rds");value <- readRDS(file)
  value$error <- fun(value$error);saveRDS(value,file,version=3)
}
check("unchanged-return",reference)
check("signed-zero-payload",reference,value.change(function(v){v[[1L]]$value[[1L]] <- -0;v}))
check("NA-versus-NaN",reference,value.change(function(v){v[[1L]]$value[[4L]] <- NaN;v}))
check("integer64-precision",reference,value.change(function(v){v[[1L]]$int64[[1L]] <- bit64::as.integer64("9007199254740992");v}))
check("duplicate-identifier",reference,value.change(function(v){v[[1L]]$gene_id[[4L]] <- "changed-G1";v}))
check("field-order",reference,value.change(function(v){v[[1L]] <- v[[1L]][rev(seq_along(v[[1L]]))];v}))
check("numeric-value",reference,value.change(function(v){v[[1L]]$value[[3L]] <- 1.250001;v}))
check("factor-level",reference,value.change(function(v){levels(v[[1L]]$category)[[1L]] <- "changed-category";v}))
check("row-order",reference,value.change(function(v){v[[1L]] <- v[[1L]][c(2L,1L,3:7),,drop=FALSE];v}))
check("RNG-snapshot",reference,function(path){
  file <- file.path(path,"complete-rng-before-after.rds");v <- readRDS(file)
  v$after$seed_exists <- !v$after$seed_exists;saveRDS(v,file,version=3)
})
check("unchanged-error",error.reference,error=TRUE)
check("error-class",error.reference,condition.change(function(e){class(e) <- c("changed-error-class",class(e));e}),error=TRUE)
check("error-message",error.reference,condition.change(function(e){e$message <- "changed original error message";e}),error=TRUE)
check("error-call",missing.reference,condition.change(function(e){e$call <- quote(changed_original_call());e}),error=TRUE)
jsonlite::write_json(list(status="PASS_ACTUAL_ARROW1201_COMPARATOR_NEGATIVE_CONTROLS",
  unchanged_controls=2L,tampered_rejections=12L,cases=records,original_objects_modified=FALSE),
  file.path(out,"COMPLETED.json"),pretty=TRUE,auto_unbox=TRUE)
