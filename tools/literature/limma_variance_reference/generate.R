# Original-package controls for the native limma variance component.
# SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE)
stopifnot(length(args) == 6L)
.libPaths(c(readLines(args[1], warn=FALSE), .libPaths()))
stopifnot(as.character(getRversion()) == "4.6.1",
          as.character(packageVersion("limma")) == "3.68.5",
          as.character(packageVersion("statmod")) == "1.5.2")
out <- args[6]
stopifnot(dir.exists(out), !file.exists(file.path(out,"original-results.rds")))
source.env <- new.env(parent=asNamespace("limma"))
sys.source(file.path(args[2],"R/fitFDist.R"), envir=source.env, keep.source=FALSE)
sys.source(file.path(args[2],"R/squeezeVar.R"), envir=source.env, keep.source=FALSE)
statmod.env <- new.env(parent=asNamespace("statmod"))
sys.source(file.path(args[3],"R/digamma.R"), envir=statmod.env, keep.source=FALSE)
for(name in c("fitFDist","trigammaInverse","squeezeVar",".squeezeVar")) {
  original <- get(name, envir=asNamespace("limma"), inherits=FALSE)
  src <- get(name, envir=source.env, inherits=FALSE)
  stopifnot(identical(body(original),body(src)),identical(formals(original),formals(src)))
  dput(list(formals=formals(original),body=body(original)),file=file.path(out,paste0(name,".R")))
}
stopifnot(identical(body(statmod::logmdigamma),body(statmod.env$logmdigamma)),
          identical(formals(statmod::logmdigamma),formals(statmod.env$logmdigamma)))
dput(list(formals=formals(statmod::logmdigamma),body=body(statmod::logmdigamma)),
     file=file.path(out,"logmdigamma.R"))
capture.output(sessionInfo(),file=file.path(out,"session.txt"))
input <- read.delim(args[4],sep="\t",quote="",comment.char="",check.names=FALSE,
                    colClasses="character",na.strings=NULL)
vector <- function(text) if(identical(text,"EMPTY")) numeric(0) else
  as.numeric(strsplit(text,";",fixed=TRUE)[[1]])
encode <- function(x) {
  if(length(x)==0L)return("EMPTY")
  paste(vapply(x,function(v) {
    if(is.nan(v))return("NaN")
    if(is.na(v))return("NA")
    if(is.infinite(v))return(if(v>0)"Inf" else "-Inf")
    format(v,digits=17,scientific=TRUE,trim=TRUE)
  },character(1)),collapse=";")
}
records <- base::vector("list",nrow(input))
table <- base::vector("list",nrow(input))
for(i in seq_len(nrow(input))) {
  row <- input[i,,drop=FALSE]; x <- vector(row$x); df <- vector(row$df)
  warnings <- character(0); error <- NULL
  result <- tryCatch(withCallingHandlers(switch(row$operation,
    fit=limma::fitFDist(x,df1=df,covariate=NULL),
    squeeze=limma::squeezeVar(x,df=df,covariate=NULL,robust=FALSE,legacy=TRUE),
    posterior=limma:::.squeezeVar(var=x,df=df,var.prior=vector(row$prior),df.prior=vector(row$prior_df)),
    inverse=limma:::trigammaInverse(x),
    logmdigamma=statmod::logmdigamma(x),stop("Unknown operation")),
    warning=function(w){warnings <<- c(warnings,conditionMessage(w));invokeRestart("muffleWarning")}),
    error=function(e){error <<- conditionMessage(e);NULL})
  records[[i]] <- list(input=row,result=result,warnings=warnings,error=error)
  scale <- df2 <- values <- "EMPTY"
  if(is.null(error)) {
    if(row$operation=="fit") {scale <- encode(result$scale);df2 <- encode(result$df2)}
    else if(row$operation=="squeeze") {
      scale <- encode(result$var.prior);df2 <- encode(result$df.prior);values <- encode(result$var.post)
    } else values <- encode(result)
  }
  table[[i]] <- data.frame(case=row$case,operation=row$operation,status=if(is.null(error))"RETURNED" else "ERROR",
    scale=scale,df2=df2,values=values,warnings=paste(warnings,collapse=" | "),
    error=if(is.null(error))"" else error,check.names=FALSE)
}
saveRDS(records,file.path(out,"original-results.rds"),version=3,compress=FALSE)
write.table(do.call(rbind,table),file=args[5],sep="\t",quote=FALSE,row.names=FALSE,na="NA")
writeLines("PASS_ORIGINAL_NAMESPACE_SOURCE_IDENTITIES_AND_CONTROLS",file.path(out,"COMPLETED.txt"))
