# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Direct unchanged upstream controls for the native dense-marker component.
args <- commandArgs(TRUE);stopifnot(length(args)==2L)
source <- args[[1L]];out <- args[[2L]]
stopifnot(!file.exists(out));dir.create(out)
library(scrattch.bigcat);library(matrixStats)
stopifnot(as.character(getRversion())=="4.2.3",as.character(packageVersion("scrattch.bigcat"))=="0.0.5",
          as.character(packageVersion("matrixStats"))=="1.3.0")
original <- parse(file.path(source,"R/find_noise_cl.R"),keep.source=FALSE)
definition <- Filter(function(x)is.call(x)&&identical(x[[1L]],as.name("<-"))&&
  identical(x[[2L]],as.name("find_doublet_by_marker")),as.list(original))
stopifnot(length(definition)==1L)
f <- getExportedValue("scrattch.bigcat","find_doublet_by_marker")
stopifnot(identical(formals(f),definition[[1L]][[3L]][[2L]]),identical(body(f),definition[[1L]][[3L]][[3L]]))
cases <- list()
add <- function(m,groups,threshold=3.5,default=FALSE) {
  storage.mode(m) <- "double"
  cases[[sprintf("case-%04d",length(cases)+1L)]] <<- list(matrix=m,groups=groups,threshold=threshold,default=default)
}
set.seed(20261115)
for(nr in c(0L,1L,3L))for(nc in 0:5)for(naming in c("both","neither","columns","rows"))for(kind in 0:5) {
  m <- matrix(rep(c(-0,5,2,8,0,Inf),length.out=nr*nc),nrow=nr,ncol=nc)
  if(naming %in% c("both","rows"))rownames(m) <- if(nr)paste0("g",seq_len(nr))else character()
  if(naming %in% c("both","columns"))colnames(m) <- if(nc)paste0("c",seq_len(nc))else character()
  groups <- switch(as.character(kind),`0`=list(),`1`=list(character()),
    `2`=list(A="g1",B="g1"),`3`=list(A=character(),B="g1",C=c("g1","g1")),
    `4`=list(A=NA_character_,B="g1"),`5`=list(A="missing",B="g1"))
  add(m,groups)
}
for(i in seq_len(300L)) {
  nr <- sample.int(8L,1L);nc <- sample(2:10,1L)
  population <- c(-0,0,1,2,3.5,4,8,-7,1e-300,1e300,Inf,-Inf,NA_real_,NaN)
  m <- matrix(sample(population,nr*nc,replace=TRUE),nrow=nr)
  rownames(m) <- paste0("g",seq_len(nr));colnames(m) <- paste0("cluster ",seq_len(nc))
  if(i%%11L==0L)rownames(m)[[nr]] <- "g1"
  if(i%%13L==0L)colnames(m)[[1L]] <- NA_character_
  if(i%%17L==0L)rownames(m)[[1L]] <- ""
  if(i%%19L==0L)rownames(m)[[nr]] <- NA_character_
  if(i%%23L==0L)dimnames(m) <- list(NULL,NULL)
  groups <- lapply(seq_len(sample(1:6,1L)),function(g)sample(paste0("g",seq_len(nr)),sample(0:4,1L),replace=TRUE))
  names(groups) <- paste0("group ",seq_along(groups))
  if(i%%7L==0L)groups[[1L]] <- c("NA",NA_character_,"", "g1")
  if(i%%29L==0L)groups[1L] <- list(NULL)
  add(m,groups,sample(c(3.5,0,-Inf,Inf,NA_real_,NaN),1L))
}
special.names <- c("Slc32a1","Slc17a7","Slc17a6","Sox10","Opalin","Aqp4","Ly6c1","Slc6a13","Kcnj8","Acta2","C1qc")
m <- matrix(0,nrow=length(special.names),ncol=6L,dimnames=list(special.names,c("neuron","olig","mixed","none","literal NA",NA_character_)))
m[c(1L,4L),3L] <- 5;m[1L,1L] <- 5;m[4L,2L] <- 5;m[1:2,5L] <- Inf;m[4L,6L] <- NA_real_
add(m,NULL,default=TRUE)
for(th in c(3.5,NA_real_,NaN,Inf,-Inf)) {
  m <- matrix(c(5,0,5,0,5,5,5,5,0,1,1,1),nrow=3L,dimnames=list(c("g1","g2","g3"),c("c1","c2","c3","c4")))
  add(m,list(A="g1",B="g2"),th)
  add(m,list(A=character(),B=NULL),th)
  add(m,list(A="g1"),th)
  add(m,list(A=c("g1","g1"),B=c("g2","g3")),th)
}
hex <- function(x)if(is.na(x))"!NA"else paste(sprintf("%02x",as.integer(charToRaw(enc2utf8(x)))),collapse="")
names.file <- function(x,path)writeLines(if(is.null(x))"ABSENT"else c("PRESENT",vapply(x,hex,"")),path,useBytes=TRUE)
numbers.file <- function(x,path) {
  con <- file(path,"wb");on.exit(close(con));writeBin(as.double(x),con,size=8L,endian="little")
}
records <- list();rows <- character();returned.count <- errors <- numbers <- 0L
for(id in names(cases)) {
  case <- cases[[id]];directory <- file.path(out,id);dir.create(directory)
  m <- case$matrix;groups <- case$groups;set.seed(81473L);before <- .Random.seed
  error <- NULL;warnings <- messages <- list();returned <- FALSE
  arguments <- list(cl.means=m)
  if(!case$default)arguments <- c(arguments,list(markers=groups,th=case$threshold))
  value <- tryCatch(withCallingHandlers({
    ans <- do.call(scrattch.bigcat::find_doublet_by_marker,arguments);returned <- TRUE;ans
  },warning=function(c){warnings[[length(warnings)+1L]] <<- c;invokeRestart("muffleWarning")},
    message=function(c){messages[[length(messages)+1L]] <<- c;invokeRestart("muffleMessage")}),
    error=function(c){error <<- c;NULL})
  stopifnot(identical(before,.Random.seed))
  records[[id]] <- list(arguments=arguments,returned=returned,value=value,error=error,warnings=warnings,messages=messages,
                        RNG_before=before,RNG_after=.Random.seed)
  numbers.file(as.vector(t(m)),file.path(directory,"input.bin"))
  numbers.file(case$threshold,file.path(directory,"threshold.bin"))
  names.file(rownames(m),file.path(directory,"input-rows.tsv"));names.file(colnames(m),file.path(directory,"input-columns.tsv"))
  marker.lines <- unlist(lapply(seq_along(groups),function(g)if(length(groups[[g]]))paste(g-1L,vapply(groups[[g]],hex,""),sep="\t")else character()),use.names=FALSE)
  writeLines(as.character(marker.lines),file.path(directory,"markers.tsv"),useBytes=TRUE)
  writeLines(vapply(warnings,function(c)hex(conditionMessage(c)),""),file.path(directory,"warnings.tsv"),useBytes=TRUE)
  writeLines(vapply(messages,function(c)hex(conditionMessage(c)),""),file.path(directory,"messages.tsv"),useBytes=TRUE)
  if(returned) {
    stopifnot(is.matrix(value),typeof(value)=="double")
    numbers.file(as.vector(t(value)),file.path(directory,"output.bin"))
    names.file(rownames(value),file.path(directory,"output-rows.tsv"));names.file(colnames(value),file.path(directory,"output-columns.tsv"))
    returned.count <- returned.count+1L;numbers <- numbers+length(value)
  } else {writeLines(hex(conditionMessage(error)),file.path(directory,"error.tsv"));errors <- errors+1L}
  fields <- c(id,nrow(m),ncol(m),as.integer(!is.null(attr(m,"dimnames",exact=TRUE))),length(groups),as.integer(case$default),
    if(returned)"RETURN"else"ERROR",if(returned)nrow(value)else -1L,if(returned)ncol(value)else -1L,
    if(returned)as.integer(!is.null(attr(value,"dimnames",exact=TRUE)))else -1L,length(warnings),length(messages))
  rows <- c(rows,paste(fields,collapse="\t"))
}
writeLines(c("id\trows\tcolumns\tdimnames\tgroups\tdefault\toutcome\toutput_rows\toutput_columns\toutput_dimnames\twarnings\tmessages",rows),file.path(out,"cases.tsv"))
saveRDS(list(records=records,session=sessionInfo(),original_function=f,source_ast=definition,
  source_commit="bbfe5277de63c65a5342e43f3e1eeb1015755fc7"),file.path(out,"complete-original-reference.rds"),version=3)
jsonlite::write_json(list(status="PASS_COMPLETE_ORIGINAL_DENSE_MARKER_REFERENCE_GENERATED",cases=length(cases),
  complete_returns=returned.count,original_errors=errors,numeric_values=numbers,original_RNG_unchanged=TRUE,
  source_commit="bbfe5277de63c65a5342e43f3e1eeb1015755fc7",R=as.character(getRversion()),
  matrixStats=as.character(packageVersion("matrixStats")),author_commit_established=FALSE,new_family_credit=0,
  whole_tool_qualified=FALSE,whole_paper_reproduced=FALSE),file.path(out,"REFERENCE.json"),pretty=TRUE,auto_unbox=TRUE)
cat("PASS_COMPLETE_ORIGINAL_DENSE_MARKER_REFERENCE_GENERATED",length(cases),returned.count,errors,numbers,"\n")
