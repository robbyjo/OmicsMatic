# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Internal original-CSV versus scalar-GDS audit; never repairs an upstream node.
library(gdsfmt)
library(SeqArray)
library(readr)
library(jsonlite)
library(digest)

annotation_main <- function(args) {
 if(length(args)!=10L)stop("Require GDS,joinedCSV,newOutput,maxCSVBytes,maxRows,maxColumns,maxCells,maxScalarBytes,maxCanonicalBytes,chunkRows")
 input <- normalizePath(args[1],mustWork=TRUE);csv <- normalizePath(args[2],mustWork=TRUE);output <- args[3]
 whole <- function(i,maximum) {
  if(!grepl("^[1-9][0-9]*$",args[i]))stop("Require positive canonical audit capacity")
  n <- as.numeric(args[i]);if(!is.finite(n)||n>maximum)stop("Unsupported audit capacity")
  n
 }
 max_bytes <- whole(4,2^52);max_rows <- whole(5,.Machine$integer.max)
 max_columns <- whole(6,4096);max_cells <- whole(7,2^52)
 max_scalar <- whole(8,16777216);max_canonical <- whole(9,2^52);chunk <- whole(10,1000000)
 if(file.info(csv)$size>max_bytes)stop("Complete joined CSV byte capacity exceeded")
 if(file.exists(output)||dir.exists(output))stop("Audit output exists;refuse overwrite")
 if(!dir.create(output,recursive=FALSE))stop("Cannot create annotation audit output")
 tryCatch({
  for(role in c("source","expected","stored"))if(!dir.create(file.path(output,role)))stop("Cannot create canonical column directory")
  # A character-only bounded prepass protects the original full read_csv call.
  # Scientific inference below uses the original read_csv defaults unchanged.
  pre_rows <- 0;pre_columns <- NULL
  callback <- SideEffectChunkCallback$new(function(x,pos) {
   if(ncol(x)<2||ncol(x)>max_columns)stop("Complete annotation column capacity exceeded")
   if(is.null(pre_columns))pre_columns <<- names(x)
   if(!identical(pre_columns,names(x)))stop("Joined CSV chunk headers changed")
   pre_rows <<- pre_rows+nrow(x)
   if(pre_rows>max_rows||pre_rows*ncol(x)>max_cells)stop("Complete annotation row/cell capacity exceeded")
   for(value in x)if(any(nchar(value,type="bytes")>max_scalar,na.rm=TRUE))stop("Complete decoded scalar byte capacity exceeded")
   if(nrow(problems(x))!=0L)stop("Joined CSV structural parsing problem")
  })
  read_csv_chunked(csv,callback,chunk_size=min(chunk,max(1,floor(max_cells/max_columns))),col_types=cols(.default=col_character()),na=character(),trim_ws=FALSE,progress=FALSE,show_col_types=FALSE)
  parsed <- read_csv(csv,show_col_types=FALSE,progress=FALSE)
  nr <- nrow(parsed);nc <- ncol(parsed)
  if(nr<1||nr!=pre_rows||nc<2||nc>max_columns||nr>max_rows||nr*nc>max_cells||!identical(names(parsed),pre_columns)||anyDuplicated(names(parsed)))stop("Incomplete or changed complete joined CSV population")
  if(names(parsed)[1]!="VarInfo"||!"variant_vcf" %in% names(parsed)||!is.character(parsed$VarInfo)||anyNA(parsed$VarInfo))stop("Require original VarInfo and variant_vcf columns")
  g <- seqOpen(input,readonly=TRUE);on.exit(seqClose(g),add=TRUE)
  nvar <- prod(as.numeric(objdesp.gdsn(index.gdsn(g,"variant.id"))$dim))
  if(nvar!=nr)stop("Unsafe annotation attachment:CSV and original variant row counts differ")
  for(start in seq.int(1,nr,by=chunk)) {
   indices <- seq.int(start,min(nr,start+chunk-1));seqSetFilter(g,variant.sel=as.integer(indices),verbose=FALSE)
   keys <- paste0(seqGetData(g,"chromosome"),"-",seqGetData(g,"position"),"-",seqGetData(g,"$ref"),"-",seqGetData(g,"$alt"))
   if(!identical(parsed$VarInfo[indices],keys))stop("Unsafe annotation attachment:complete original variant order/keys differ")
  }
  root <- index.gdsn(g,"annotation/info/FunctionalAnnotation/FunctionalAnnotation")
  if(!identical(ls.gdsn(root,include.hidden=TRUE,recursive=FALSE),names(parsed)))stop("Stored annotation columns differ from complete original CSV header/order")
  total <- 0;records <- list();warnings <- list()
  reserve <- function(n) {
   if(!is.finite(n)||n<0||n>max_canonical-total)stop("Complete canonical annotation byte capacity exceeded")
   total <<- total+n
  }
  canonical <- function(value,kind,role,index) {
   relative <- paste0(role,"/c",sprintf("%04d",index),".bin");path <- file.path(output,relative)
   con <- file(paste0(path,".partial"),"wb");on.exit(try(close(con),silent=TRUE))
   for(start in seq.int(1,length(value),by=chunk)) {
    x <- value[seq.int(start,min(length(value),start+chunk-1))]
    if(kind=="UTF8_LENGTH_INT32_LE_NA_NEGATIVE1") {
     for(s in x) {
      if(is.na(s)){reserve(4);writeBin(-1L,con,size=4L,endian="little")} else {
       raw <- charToRaw(enc2utf8(s));if(length(raw)>max_scalar)stop("Complete decoded scalar byte capacity exceeded")
       reserve(4+length(raw));writeBin(as.integer(length(raw)),con,size=4L,endian="little");writeBin(raw,con)
      }
     }
    } else if(kind=="FLOAT64_LE_TAGGED") {
     tags <- as.integer(is.na(x));tags[is.nan(x)] <- 2L
     wire <- matrix(as.raw(0),nrow=9L,ncol=length(x));wire[1,] <- as.raw(tags)
     wire[2:9,] <- matrix(writeBin(as.double(x),raw(),size=8L,endian="little"),nrow=8L)
     reserve(length(wire));writeBin(as.vector(wire),con)
    } else {
     reserve(4*length(x));writeBin(as.integer(x),con,size=4L,endian="little")
    }
   }
   close(con)
   if(!file.rename(paste0(path,".partial"),path))stop("Cannot commit complete canonical column")
   list(path=relative,bytes=unname(file.info(path)$size),sha256=digest(file=path,algo="sha256",serialize=FALSE))
  }
  for(i in seq_len(nc)) {
   name <- names(parsed)[i];source <- parsed[[i]];source_class <- class(source);source_attributes <- attributes(source)
   type <- typeof(source)
   allowed <- (type=="character"&&identical(source_class,"character"))||
              (type=="logical"&&identical(source_class,"logical"))||
              (type=="integer"&&identical(source_class,"integer"))||
              (type=="double"&&(identical(source_class,"numeric")||identical(source_class,"Date")||identical(source_class,c("POSIXct","POSIXt"))||identical(source_class,c("hms","difftime"))))
   if(!allowed)stop("Unsupported original readr scalar class: ",name)
   kind <- switch(type,character="UTF8_LENGTH_INT32_LE_NA_NEGATIVE1",logical="LOGICAL_INT32_LE",integer="INT32_LE",double="FLOAT64_LE_TAGGED")
   expected <- source;attributes(expected) <- NULL
   converted <- if(is.character(expected))sum(is.na(expected)) else 0
   if(is.character(expected))expected[is.na(expected)] <- ""
   node <- index.gdsn(root,name);description <- objdesp.gdsn(node)
   if(!isTRUE(description$is.array)||!identical(as.integer(description$dim),as.integer(nr)))stop("Incomplete stored scalar column dimensions: ",name)
   stored <- read.gdsn(node)
   a <- canonical(source,kind,"source",i);b <- canonical(expected,kind,"expected",i);c <- canonical(stored,kind,"stored",i)
   lost <- !is.null(source_attributes)
   record <- list(index=i,name=name,kind=kind,values=nr,source_type=type,source_class=I(source_class),source_attributes=source_attributes,
                  source_attributes_sha256=digest(source_attributes,algo="sha256",serialize=TRUE),stored_type=typeof(stored),stored_class=I(class(stored)),
                  stored_attributes_sha256=digest(attributes(stored),algo="sha256",serialize=TRUE),source_attributes_lost=lost,
                  source_na=sum(is.na(source)&!is.nan(source)),source_nan=sum(is.nan(source)),stored_na=sum(is.na(stored)&!is.nan(stored)),stored_nan=sum(is.nan(stored)),
                  converted_character_na=converted,source=a,expected=b,stored=c)
   records[[i]] <- record
   if(lost)warnings[[length(warnings)+1L]] <- paste0("SOURCE ATTRIBUTE LOSS: ",name," (",paste(source_class,collapse=","),") is stored as ",paste(class(stored),collapse=","),". Required action: use original class/unit/timezone provenance;do not interpret stored numeric values as preserved date/time classes.")
   write_json(list(status="partial",columns=records,warnings=warnings),file.path(output,"audit.partial.json"),auto_unbox=TRUE,null="null",na="null",digits=NA,pretty=TRUE)
   if(typeof(stored)!=type||!is.null(attributes(stored))||!identical(class(stored),class(expected))||a$bytes<0||b$sha256!=c$sha256||b$bytes!=c$bytes)stop("Original scalar-to-GDS projection/type/missingness differs: ",name)
  }
  if(nrow(problems(parsed))!=0L)stop("Original inferred CSV has parsing problems;do not admit repaired/missing values")
  result <- list(schema="favor-annotation-scalars-v1",rows=nr,columns=records,canonical_bytes=total,
                 joined_csv_sha256=digest(file=csv,algo="sha256",serialize=FALSE),gds_sha256=digest(file=input,algo="sha256",serialize=FALSE),
                 original_frame_rows=nrow(read.gdsn(root)),warnings=warnings,
                 runtime=list(R=R.version.string,readr=as.character(packageVersion("readr")),gdsfmt=as.character(packageVersion("gdsfmt")),SeqArray=as.character(packageVersion("SeqArray")),digest=as.character(packageVersion("digest"))))
  temporary <- file.path(output,"audit.json.partial");write_json(result,temporary,auto_unbox=TRUE,null="null",na="null",digits=NA,pretty=TRUE)
  if(!file.rename(temporary,file.path(output,"audit.json")))stop("Cannot commit complete annotation audit")
 },error=function(e) {
  write_json(list(status="failed",cause=conditionMessage(e),required_action="Retain partial diagnostics;correct the named input,alignment,type,missingness or capacity cause and rerun to a new output. No upstream GDS or script was repaired."),file.path(output,"failure.json"),auto_unbox=TRUE,pretty=TRUE)
  stop(e)
 })
}
if(sys.nframe()==0L)annotation_main(commandArgs(TRUE))
