# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Execute the unchanged original matrix definition, not a replacement algorithm.
args <- commandArgs(TRUE)
stopifnot(length(args) == 2L)
invisible(Sys.setlocale("LC_ALL", "C"))
original <- normalizePath(args[1], mustWork=TRUE)
out <- args[2]
stopifnot(!file.exists(out)); dir.create(out, recursive=TRUE)
env <- new.env(parent=baseenv())
# These originals use base and stats only. No S4 classes or package bindings are mocked.
env$sd <- stats::sd
sys.source(file.path(original, "R", "geomMean.R"), envir=env)
sys.source(file.path(original, "R", "stabMeasureM.R"), envir=env)
expr <- parse(file.path(original, "R", "selectHKs.R"), keep.source=TRUE)
first <- expr[[1]]
stopifnot(identical(as.character(first[[1]]), "setMethod"),
          identical(first[[2]], "selectHKs"), identical(first[[3]], "matrix"))
definition <- first[[which(names(first) == "definition")]]
stopifnot(identical(definition[[1]], as.name("function")))
select.original <- eval(definition, envir=env)
saveRDS(list(select_matrix=select.original, stabMeasureM=env$stabMeasureM,
             geomMean=env$geomMean), file.path(out, "PRIVATE-original-functions.rds"), version=3)

number <- function(x) ifelse(is.nan(x), "NaN", sprintf("%.17g", x))
table <- function(file, header, columns) {
  writeLines(c(paste(header, collapse="\t"),
    apply(as.matrix(columns), 1, function(row) paste(row, collapse="\t"))), file, useBytes=TRUE)
}
mat <- function(file, x) {
  table(file, c("sample_id", paste0("G", seq_len(ncol(x)))),
    cbind(paste0("S",seq_len(nrow(x))), matrix(number(x), nrow=nrow(x))))
}
cases <- list(
  analytic=list(x=matrix(c(0,0,0, 0,1,2, 0,2,4),3,3,byrow=TRUE),scale="LOG2_QUANTITY"),
  constant=list(x=matrix(rep(c(-4,0,3,9,11),each=8),8,5),scale="LOG2_QUANTITY"),
  shared_sample_shift=list(x=outer(seq_len(9), c(-2,0,2,4), "+"),scale="LOG2_QUANTITY"),
  two_references=list(x=matrix(c(-4,2, -1,5, 2,8, 7,12),4,2,byrow=TRUE),scale="LOG2_QUANTITY")
)
set.seed(713409L)
for(i in seq_len(36)) {
  n <- c(2L,3L,8L,19L,50L,100L)[(i-1L)%%6L+1L]
  g <- c(3L,4L,6L,9L,13L,20L)[(i-1L)%/%6L+1L]
  x <- matrix(rnorm(n*g), n, g)
  x <- sweep(x, 2, runif(g,0.1,5), "*")
  x <- sweep(x, 2, runif(g,-10,10), "+")
  cases[[paste0("seeded_",sprintf("%02d",i))]] <- list(x=x,scale="LOG2_QUANTITY")
}
for(i in seq_len(4)) {
  x <- matrix(exp(rnorm(80L,sd=3)),20L,4L)
  cases[[paste0("quantity_",i)]] <- list(x=x,scale="QUANTITY")
}
cases$large_quantity <- list(x=matrix(c(1e290,2e290,4e290,3e290,
  1e291,3e291,4e291,2e291, 1e292,2e292,3e292,4e292),3,4,byrow=TRUE),scale="QUANTITY")
for (id in names(cases)) {
  d <- file.path(out,id);dir.create(d)
  input <- cases[[id]]$x; scale <- cases[[id]]$scale
  x <- if(scale=="QUANTITY") log2(input) else input
  symbols <- paste0("G",seq_len(ncol(x)))
  colnames(x) <- symbols
  mat(file.path(d,"input.tsv"),input)
  writeLines(paste0("scale=",scale),file.path(d,"options.properties"))
  conditions <- list()
  result <- if(ncol(x)>2) withCallingHandlers(select.original(x,Symbols=symbols,
    method="geNorm",minNrHKs=2,log=TRUE,trace=FALSE,na.rm=FALSE),
    warning=function(w) conditions[[length(conditions)+1L]] <<- w,
    message=function(m) conditions[[length(conditions)+1L]] <<- m) else
    list(ranking=setNames(symbols,c("1","1")), variation=numeric(),
         meanM=setNames(mean(env$stabMeasureM(x,log=TRUE,na.rm=FALSE)),"2"))
  saveRDS(list(result=result,conditions=conditions,seed=.Random.seed,input=input,
    log2_input=x,scale=scale),file.path(d,"PRIVATE-original-result.rds"),version=3)
  table(file.path(d,"ranking.tsv"),c("rank","gene"),cbind(names(result$ranking),result$ranking))
  if(length(result$variation)) table(file.path(d,"variation.tsv"),c("comparison","V"),
    cbind(names(result$variation),number(result$variation))) else
    writeLines("comparison\tV",file.path(d,"variation.tsv"))
  table(file.path(d,"meanM.tsv"),c("genes","mean_M"),cbind(names(result$meanM),number(result$meanM)))
  active <- seq_len(ncol(x));trace <- NULL
  for(k in ncol(x):2L) {
    m <- env$stabMeasureM(x[,active,drop=FALSE],log=TRUE,na.rm=FALSE)
    trace <- rbind(trace,cbind(k,active,number(m)))
    if(k>2L) active <- active[-which.max(m)]
  }
  table(file.path(d,"stability.tsv"),c("genes","column_1based","M"),trace)
  best <- match(result$ranking[1:2],symbols)
  nf <- rowMeans(x[,best,drop=FALSE])
  table(file.path(d,"normalization.tsv"),c("sample_id","log2_factor"),
    cbind(paste0("S",seq_len(nrow(x))),number(nf)))
  if(scale=="QUANTITY") {
    literal <- withCallingHandlers(select.original(input,Symbols=symbols,
      method="geNorm",minNrHKs=2,log=FALSE,trace=FALSE,na.rm=FALSE),
      warning=function(w) conditions[[length(conditions)+1L]] <<- w)
    saveRDS(list(result=literal,conditions=conditions),file.path(d,"PRIVATE-literal-quantity-branch.rds"),version=3)
    writeLines(c("The Java profile compares against the unchanged log=TRUE matrix method.",
      "The original log=FALSE result is retained separately; it is not silently repaired.",
      paste0("original_literal_quantity_nonfinite_variation=",any(!is.finite(literal$variation)))),
      file.path(d,"literal-quantity-branch.txt"))
  }
}
saveRDS(sessionInfo(),file.path(out,"PRIVATE-session-info.rds"),version=3)
writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"))
writeLines(names(cases),file.path(out,"case-index.txt"))
cat("PASS_ORIGINAL_MATRIX_METHOD_FIXTURES ",length(cases),"\n",sep="")
