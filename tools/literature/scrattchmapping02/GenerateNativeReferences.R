# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Execute the unchanged source function; retain full R values and conditions.
args <- commandArgs(TRUE)
stopifnot(length(args) == 3L)
source.dir <- args[[1L]]; project <- args[[2L]]; out <- args[[3L]]
if (file.exists(out)) stop("Use a NEW reference output")
dir.create(out)
options(warn=1)
expressions <- parse(file.path(source.dir, "R/HANN_utils.R"))
stopifnot(all(vapply(expressions, function(x) is.call(x) &&
  as.character(x[[1L]]) %in% c("<-", "=") && is.call(x[[3L]]) &&
  identical(x[[3L]][[1L]], as.name("function")), logical(1))))
original <- new.env(parent=globalenv())
sys.source(file.path(source.dir, "R/HANN_utils.R"), envir=original)
saveRDS(list(formals=formals(original$l2norm), body=body(original$l2norm)),
        file.path(out, "original-function.rds"), version=3)
source(file.path(project, "tools/literature/velociraptor120/RValues.R"))
text <- function(x) if (is.na(x)) "!NA" else
  paste(sprintf("%02x", as.integer(charToRaw(enc2utf8(x)))), collapse="")
binary <- function(x, path) {
  con <- file(path, "wb"); on.exit(close(con))
  writeBin(as.double(t(x)), con, size=8L, endian="little")
}
names.file <- function(x, path) writeLines(if(is.null(x)) "ABSENT" else
  c("PRESENT", vapply(x, text, character(1))), path, useBytes=TRUE)
matrices <- list(
  ordinary=matrix(c(3,4,1,2,0.3,0.4), nrow=3, byrow=TRUE),
  zero_row_and_small=matrix(c(0,0,0.3,0.4,3,4),nrow=3,byrow=TRUE),
  zero_column_and_small=matrix(c(0,0.3,0,0.4),nrow=2,byrow=TRUE),
  signed_zero=matrix(c(-0,0,0,-0),nrow=2),
  missing_no_zero=matrix(c(NA_real_,1,2,3),nrow=2),
  nan_no_zero=matrix(c(NaN,1,2,3),nrow=2),
  missing_zero_row=matrix(c(NA_real_,1,0,0),nrow=2,byrow=TRUE),
  nan_zero_column=matrix(c(NaN,0,1,0),nrow=2,byrow=TRUE),
  infinities=matrix(c(Inf,-Inf,1,2),nrow=2),
  infinity_zero=matrix(c(Inf,0,1,0),nrow=2,byrow=TRUE),
  overflow=matrix(c(1e308,-1e308,1,2),nrow=2),
  underflow=matrix(c(1e-200,-1e-200,0,0),nrow=2),
  tiny_nonzero=matrix(c(1e-150,2e-150,3e-150,4e-150),nrow=2),
  empty=matrix(numeric(),nrow=0,ncol=0),
  empty_rows=matrix(numeric(),nrow=0,ncol=3),
  empty_columns=matrix(numeric(),nrow=3,ncol=0),
  scalar_zero=matrix(0,1,1), scalar_negative_zero=matrix(-0,1,1),
  scalar_positive=matrix(0.25,1,1), scalar_negative=matrix(-0.25,1,1),
  scalar_NA=matrix(NA_real_,1,1), scalar_NaN=matrix(NaN,1,1),
  scalar_Inf=matrix(Inf,1,1), scalar_minus_Inf=matrix(-Inf,1,1))
matrices$literal_names <- matrices$ordinary
dimnames(matrices$literal_names) <- list(c("NA", NA_character_, "\u03b2"), c("", "NA"))
matrices$duplicate_names <- matrices$ordinary
dimnames(matrices$duplicate_names) <- list(c("x", "x", ""), c("\u03b1", "\u03b1"))
set.seed(20261114)
for (i in seq_len(80L)) {
  nr <- sample(1:30,1); nc <- sample(1:30,1)
  x <- matrix(rnorm(nr*nc)*10^sample(-100:100,1), nrow=nr)
  if(i %% 3L == 0L) x[sample(length(x), max(1L,length(x)%/%5L))] <- 0
  if(i %% 7L == 0L) x[1,] <- 0
  if(i %% 11L == 0L) x[,1] <- 0
  if(i %% 5L == 0L) dimnames(x) <- list(paste0("row",seq_len(nr)),paste0("column",seq_len(nc)))
  matrices[[paste0("random_",i)]] <- x
}
cases <- list()
for (name in names(matrices)) for (mode in c("column", "row", "Column")) {
  # Ordinal mode suffixes remain unique on case-insensitive Windows filesystems.
  x <- matrices[[name]]; path <- file.path(out,paste0(name,"_mode",match(mode,c("column","row","Column"))))
  if(file.exists(path)) stop("Refuse to overwrite a reference case")
  dir.create(path)
  inputs <- list(X=x,by=mode); result <- NULL; error <- NULL; conditions <- list()
  value <- tryCatch(withCallingHandlers(withVisible(original$l2norm(x,mode)),
    warning=function(c) conditions[[length(conditions)+1L]] <<- c,
    message=function(c) conditions[[length(conditions)+1L]] <<- c),
    error=function(c) {error <<- c; conditions[[length(conditions)+1L]] <<- c; NULL})
  if(is.null(error)) result <- value$value
  saveRDS(list(inputs=inputs,value=result,error=error,conditions=conditions,
               visibility=if(is.null(value))NULL else value$visible),
          file.path(path,"complete-original.rds"),version=3)
  captureRValues(list(inputs=inputs,result=result),file.path(path,"complete-R-values"))
  binary(x,file.path(path,"input.bin"))
  names.file(rownames(x),file.path(path,"row-names.tsv"))
  names.file(colnames(x),file.path(path,"column-names.tsv"))
  warnings <- vapply(Filter(function(c)inherits(c,"warning"),conditions),conditionMessage,character(1))
  messages <- vapply(Filter(function(c)inherits(c,"message"),conditions),conditionMessage,character(1))
  writeLines(vapply(warnings,text,character(1)),file.path(path,"warnings.tsv"),useBytes=TRUE)
  writeLines(vapply(messages,text,character(1)),file.path(path,"messages.tsv"),useBytes=TRUE)
  if(is.null(error)) {
    stopifnot(is.matrix(result),identical(dim(result),dim(x)),identical(dimnames(result),dimnames(x)))
    binary(result,file.path(path,"result.bin"))
  } else writeLines(text(conditionMessage(error)),file.path(path,"error.tsv"),useBytes=TRUE)
  cases[[length(cases)+1L]] <- data.frame(name=basename(path),rows=nrow(x),columns=ncol(x),
      by=text(mode),status=if(is.null(error))"PASS"else"ERROR",warnings=length(warnings),messages=length(messages))
}
index <- do.call(rbind,cases)
write.table(index,file.path(out,"cases.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
saveRDS(list(cases=index,session=sessionInfo(),RNGkind=RNGkind(),final_seed=.Random.seed),
        file.path(out,"complete-generator-record.rds"),version=3)
jsonlite::write_json(list(status="COMPLETE_ORIGINAL_L2_COMPONENT_REFERENCES_NOT_HIERARCHICAL_MAPPING",
    cases=nrow(index),successful=sum(index$status=="PASS"),original_errors=sum(index$status=="ERROR"),
    R_version=as.character(getRversion()),Matrix_version=as.character(packageVersion("Matrix")),
    warning_messages=sum(index$warnings),messages=sum(index$messages),
    finite_tolerance="1e-14 * max(abs(original), 1e-300); zeros and nonfinite classifications checked separately",
    complete_R_conditions_retained=TRUE,portable_R_condition_identity_claimed=FALSE,
    whole_tool_qualified=FALSE,new_family_credit=0L,whole_goal_complete=FALSE),
    file.path(out,"COMPLETED.json"),pretty=TRUE,auto_unbox=TRUE)
cat("COMPLETE_ORIGINAL_L2_REFERENCES",nrow(index),"\n")
