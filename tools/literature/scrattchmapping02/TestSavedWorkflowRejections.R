# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Invoke the actual saved-workflow comparator against deliberately altered copies.
args <- commandArgs(TRUE);stopifnot(length(args)==4L)
positive.controls <- args[[1L]];error.controls <- args[[2L]];comparator <- args[[3L]];out <- args[[4L]]
stopifnot(dir.exists(out),!length(list.files(out,all.files=TRUE,no..=TRUE)))
cases <- list()
add <- function(name,base,change)cases[[name]] <<- list(base=base,change=change)
add("unchanged_return","depth_3",NULL)
add("unchanged_opaque_error","no_cells",NULL)
add("numeric_return_changed","depth_3",function(folder) {
 p <- file.path(folder,"complete-result.rds");x <- readRDS(p)
 x$best.map.df$avg.dist[[1L]] <- x$best.map.df$avg.dist[[1L]]+.Machine$double.eps
 saveRDS(x,p,version=3)
})
add("return_fields_reordered","depth_3",function(folder) {
 p <- file.path(folder,"complete-result.rds");x <- readRDS(p);saveRDS(rev(x),p,version=3)
})
add("RNG_seed_changed","depth_3",function(folder) {
 p <- file.path(folder,"complete-rng-before-after.rds");x <- readRDS(p)
 x$after$seed[[2L]] <- x$after$seed[[2L]]+1L;saveRDS(x,p,version=3)
})
add("missing_value_changed_to_NaN","depth_1",function(folder) {
 p <- file.path(folder,"complete-result.rds");x <- readRDS(p)
 stopifnot(is.na(x$best.map.df$avg.path.cor[[1L]]),!is.nan(x$best.map.df$avg.path.cor[[1L]]))
 x$best.map.df$avg.path.cor[[1L]] <- NaN;saveRDS(x,p,version=3)
})
for(field in c("message","attribute","call","closure_body","closure_environment"))local({
 selected <- field
 add(paste0("error_",selected,"_changed"),"no_cells",function(folder) {
  p <- file.path(folder,"complete-conditions.rds");x <- readRDS(p);e <- x$error
  if(selected=="message")e$message <- paste0(e$message," [ALTERED COPY]")
  if(selected=="attribute")attr(e,"altered") <- TRUE
  if(selected=="call")e$call <- quote(altered_call())
  if(selected=="closure_body") {f<-e$call[[1L]];body(f)<-quote(stop("altered copy"));e$call[[1L]]<-f}
  if(selected=="closure_environment") {
   f<-e$call[[1L]];v<-new.env(parent=parent.env(environment(f)))
   v$addr<-get("addr",environment(f));v$added_binding<-"altered copy";environment(f)<-v;e$call[[1L]]<-f
  }
  x$error <- e
  for(i in seq_along(x$conditions))if(identical(x$conditions[[i]]$kind,"error"))x$conditions[[i]]$condition <- e
  saveRDS(x,p,version=3)
 })
})
results <- list()
for(name in names(cases)) {
 entry <- cases[[name]];directory <- file.path(out,name);dir.create(directory)
 controls <- if(entry$base=="no_cells")error.controls else positive.controls
 actual <- file.path(directory,"deliberately-copied-actual");dir.create(actual)
 original <- file.path(controls,entry$base,"adapter/original")
 for(file in list.files(original,pattern="[.]rds$",full.names=TRUE))stopifnot(file.copy(file,actual))
 if(!is.null(entry$change))entry$change(actual)
 command <- c("--vanilla",shQuote(comparator),shQuote(file.path(controls,entry$base,"original-reference")),
              shQuote(actual),shQuote(file.path(directory,"COMPARISON.json")))
 code <- system2(file.path(R.home("bin"),"Rscript"),command,
  stdout=file.path(directory,"comparison.stdout"),stderr=file.path(directory,"comparison.stderr"))
 expected <- if(is.null(entry$change))0L else 2L
 stopifnot(code==expected)
 report <- jsonlite::fromJSON(file.path(directory,"COMPARISON.json"),simplifyVector=FALSE)
 results[[name]] <- list(actual_exit_code=code,expected_exit_code=expected,status=report$status)
}
jsonlite::write_json(list(status="PASS_ACTUAL_SAVED_WORKFLOW_COMPARATOR_REJECTION_CONTROLS",
 cases=results,controls=length(results),original_records_modified=FALSE,new_family_credit=0L,whole_goal_complete=FALSE),
 file.path(out,"COMPLETED.json"),pretty=TRUE,auto_unbox=TRUE)
cat("PASS_ACTUAL_SAVED_WORKFLOW_COMPARATOR_REJECTION_CONTROLS",length(results),"\n")
