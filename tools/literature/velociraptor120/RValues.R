# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Preserve complete supported R value trees without JSON coercion of identifiers or NaN/NA.
# This recorder changes no upstream algorithm. Full original RDS is always saved first.
captureRValues <- function(value,directory,max.nodes=1000000L,max.bytes=2147483648) {
 if (!is.character(directory) || length(directory)!=1L || is.na(directory) || file.exists(directory)) stop("Use a NEW R value capture directory")
 if (length(max.nodes)!=1L || is.na(max.nodes) || max.nodes<1 || max.nodes!=floor(max.nodes) || length(max.bytes)!=1L || !is.finite(max.bytes) || max.bytes<1 || max.bytes!=floor(max.bytes)) stop("Explicit positive whole-number capacities required")
 if (!dir.create(directory,recursive=FALSE)) stop("Cannot create original R value capture directory")
 saveRDS(value,file.path(directory,"original.rds"),version=3)
 state <- new.env(parent=emptyenv());state$nodes <- list();state$bytes <- file.info(file.path(directory,"original.rds"))$size
 if (!dir.create(file.path(directory,"records"))) stop("Cannot create R value payload directory")
 capture <- function() {
  if (state$bytes>max.bytes) stop("Original RDS exceeds declared complete-output capacity; retain it and retry NEW paths with an explicit adequate capacity")
  visit <- function(object) {
   id <- length(state$nodes)+1L
   if (id>max.nodes) stop("Complete R value node capacity exceeded; no truncated graph is admitted")
   state$nodes[[id]] <- list(kind="pending")
   type <- typeof(object);s4 <- isS4(object)
   attrs <- attributes(object)
   attr.names <- names(attrs)
   slots <- if(s4) methods::slotNames(object) else character()
   node <- list(id=id,type=type,S4=s4,length=length(object),attribute_names=NULL,attributes=list(),slot_names=NULL,slots=list(),children=list(),payload=NULL)
   if (length(attrs)) {
    node$attribute_names <- visit(attr.names)
    node$attributes <- lapply(seq_along(attrs),function(i) {
     if (attr.names[[i]] %in% slots) return(list(index=i,represented_by_slot=TRUE))
     list(index=i,value=visit(attrs[[i]]),represented_by_slot=FALSE)
    })
   }
   if (s4) {
    node$slot_names <- visit(slots)
    node$slots <- lapply(slots,function(name)visit(methods::slot(object,name)))
   } else if (type=="symbol") {
    node$symbol_name <- visit(as.character(object))
   } else if (type %in% c("list","pairlist","language","expression")) {
    node$element_names <- visit(names(object))
    elements <- object;attributes(elements) <- NULL
    if (type!="list") elements <- as.list(elements)
    node$children <- lapply(seq_along(elements),function(i)visit(elements[[i]]))
   } else if (type=="NULL") {
    stopifnot(length(object)==0L)
   } else if (type %in% c("logical","integer","double","complex","character","raw")) {
    relative <- sprintf("records/%07d.bin",id);path <- file.path(directory,relative)
    connection <- file(path,open="wb");closed <- FALSE
    tryCatch({
     payload <- object;attributes(payload) <- NULL
     if (type=="character") {
      for (i in seq_along(payload)) {
       element <- payload[[i]]
       if (is.na(element)) {
        writeBin(as.raw(0L),connection)
       } else {
        writeBin(as.raw(1L),connection)
        encoding <- charToRaw(Encoding(element));bytes <- charToRaw(element)
        writeBin(as.integer(length(encoding)),connection,size=4L,endian="little");writeBin(encoding,connection)
        writeBin(as.integer(length(bytes)),connection,size=4L,endian="little");writeBin(bytes,connection)
       }
      }
      node$encoding <- "R_character_vector_v1: flag0=NA; flag1=int32le_encoding_length,encoding_bytes,int32le_value_length,R_native_string_bytes"
     } else {
      size <- switch(type,logical=4L,integer=4L,double=8L,complex=16L,raw=1L)
      writeBin(payload,connection,size=size,endian="little")
      node$encoding <- paste0("R_",type,"_",size,"_bytes_little_endian; original_NA_and_NaN_bits_retained")
     }
     close(connection);closed <- TRUE
    },finally={if(!closed)close(connection)})
    size <- file.info(path)$size;state$bytes <- state$bytes+size
    if (state$bytes>max.bytes) stop("Complete R value payload capacity exceeded; retain all original/partial files, no truncation admitted")
    node$payload <- list(path=relative,bytes=size)
   } else {
    stop("Unsupported complete R value type '",type,"'; original RDS retained, do not omit the value or substitute a summary")
   }
   state$nodes[[id]] <- node
   id
  }
  root <- visit(value)
  index <- list(schema="omicsmatic-complete-R-values-v1",status="COMPLETE_SUPPORTED_R_VALUE_TREE",root=root,nodes=state$nodes,node_count=length(state$nodes),original_snapshot="original.rds",original_snapshot_bytes=file.info(file.path(directory,"original.rds"))$size,payload_and_snapshot_bytes=state$bytes,byte_order="little",pointer_identity_or_opaque_RDS_byte_parity_claimed=FALSE)
  jsonlite::write_json(index,file.path(directory,"INDEX.json"),pretty=TRUE,auto_unbox=TRUE,null="null",na="string",digits=17)
  invisible(index)
 }
 tryCatch(capture(),error=function(e) {
  saveRDS(list(error=e,partial_nodes=state$nodes,retained_payload_and_snapshot_bytes=state$bytes),file.path(directory,"CAPTURE-FAILURE.rds"))
  writeLines(c("FAIL2_COMPLETE_R_VALUE_CAPTURE_UNADMITTED",conditionMessage(e),"Preserve original.rds, every available payload and CAPTURE-FAILURE.rds. Resolve the named type/capacity/filesystem cause and retry NEW paths; do not remove values, rewrite identifiers or replace NA/NaN/Inf."),file.path(directory,"WARNINGS_FAILURE.txt"))
  stop(e)
 })
}
