# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Internal read-only audit helper. No dependency installation or source repair.
library(gdsfmt)
library(SeqArray)
library(jsonlite)
library(digest)

main <- function(args) {
 if(length(args)!=10L)stop("Require GDS,outdir,mode,blockVariants,maxSamples,maxVariants,maxBlockValues,maxNodeValues,maxCanonicalBytes,maxNodes")
 input <- normalizePath(args[1],mustWork=TRUE)
 output <- args[2]
 mode <- args[3]
 if(!mode %in% c("VARIANTS_ONLY","DIPLOID_GT"))stop("Unknown explicit scientific mode")
 number <- function(i,maximum) {
  if(!grepl("^[1-9][0-9]*$",args[i]))stop("Require positive canonical capacity")
  value <- as.numeric(args[i]);if(!is.finite(value)||value>maximum)stop("Unsupported capacity")
  value
 }
 block <- number(4,1000000)
 max_samples <- number(5,1000000)
 max_variants <- number(6,.Machine$integer.max)
 max_block_values <- number(7,.Machine$integer.max)
 max_node_values <- number(8,2^52)
 max_bytes <- number(9,2^52)
 max_nodes <- number(10,1000000)
 if(file.exists(output)||dir.exists(output))stop("Audit output already exists;refuse overwrite")
 if(!dir.create(output,recursive=FALSE))stop("Cannot create audit output")
 canonical <- file.path(output,"canonical-values")
 if(!dir.create(canonical))stop("Cannot create canonical value diagnostics")
 g <- seqOpen(input,readonly=TRUE)
 on.exit(seqClose(g),add=TRUE)
 nodes <- ls.gdsn(g,recursive=TRUE,include.dirs=TRUE,include.hidden=TRUE)
 if(length(nodes)>max_nodes||anyDuplicated(nodes))stop("Invalid or oversized complete GDS node population")
 metadata <- lapply(nodes,function(name) {
  node <- index.gdsn(g,name);d <- objdesp.gdsn(node)
  if(!isTRUE(d$good))stop("Invalid GDS node: ",name)
  population <- if(isTRUE(d$is.array))prod(as.numeric(d$dim)) else 0
  if(!is.finite(population)||population>max_node_values)stop("Complete GDS node value capacity exceeded: ",name)
  list(storage=d$storage,trait=d$trait,type=as.character(d$type),is_array=d$is.array,
       is_sparse=d$is.sparse,dimensions=I(as.integer(d$dim)),encoder=d$encoder,
       compression=d$compress,hidden=d$hidden,
       parameter_sha256=digest(d$param,algo="sha256",serialize=TRUE),
       attributes_sha256=digest(get.attr.gdsn(node),algo="sha256",serialize=TRUE),
       values=population,
       data_sha256=if(isTRUE(d$is.array))unname(digest.gdsn(node,algo="sha256",action="none")[[1]]) else NULL)
 })
 names(metadata) <- nodes
 samples <- seqGetData(g,"sample.id")
 variant_node <- objdesp.gdsn(index.gdsn(g,"variant.id"))
 n <- prod(as.numeric(variant_node$dim));ns <- length(samples)
 if(n<1||n>max_variants||n!=floor(n)||ns>max_samples)stop("Unsupported complete sample/variant population")
 if(mode=="VARIANTS_ONLY") {
  if(ns!=0L||any(c("genotype/data","phase/data") %in% nodes))stop("Variant-only GDS must not contain samples or genotype/phase arrays")
 } else {
  if(ns<1||!all(c("genotype/data","phase/data") %in% nodes))stop("Diploid GDS requires actual sample,genotype and phase populations")
  if(2*ns>max_block_values)stop("One complete genotype variant exceeds block capacity")
  block <- min(block,floor(max_block_values/(2*ns)))
 }
 roles <- c("sample.id","variant.id","chromosome","position","$ref","$alt","annotation/id")
 if(mode=="DIPLOID_GT")roles <- c(roles,"genotype","phase")
 integer_roles <- c("variant.id","position","genotype","phase")
 files <- setNames(file.path(canonical,sprintf("%02d.bin",seq_along(roles))),roles)
 connections <- lapply(files,function(name)file(name,"wb"))
 on.exit(for(con in connections)try(close(con),silent=TRUE),add=TRUE)
 counts <- setNames(rep(0,length(roles)),roles)
 total_bytes <- 0
 reserve <- function(bytes) {
  if(!is.finite(bytes)||bytes<0||bytes>max_bytes-total_bytes)stop("Complete canonical audit byte capacity exceeded")
  total_bytes <<- total_bytes+bytes
 }
 integers <- function(role,values) {
  if(!is.integer(values))stop("Unexpected noninteger canonical values: ",role)
  reserve(4*length(values));writeBin(values,connections[[role]],size=4L,endian="little")
  counts[[role]] <<- counts[[role]]+length(values)
 }
 strings <- function(role,values) {
  if(!is.character(values)||anyNA(values))stop("Missing or noncharacter identity values: ",role)
  for(value in values) {
   bytes <- charToRaw(enc2utf8(value));size <- length(bytes)
   if(size>.Machine$integer.max)stop("UTF8 identity exceeds int32 length capacity")
   reserve(4+size);writeBin(as.integer(size),connections[[role]],size=4L,endian="little")
   writeBin(bytes,connections[[role]])
  }
  counts[[role]] <<- counts[[role]]+length(values)
 }
 strings("sample.id",samples)
 for(start in seq.int(1,n,by=block)) {
  end <- min(n,start+block-1);indices <- seq.int(start,end)
  seqSetFilter(g,variant.sel=as.integer(indices),verbose=FALSE)
  integers("variant.id",seqGetData(g,"variant.id"));integers("position",seqGetData(g,"position"))
  for(role in c("chromosome","$ref","$alt","annotation/id"))strings(role,seqGetData(g,role))
  if(mode=="DIPLOID_GT") {
   gt <- seqGetData(g,"genotype",.useraw=FALSE,.padNA=TRUE)
   phase <- seqGetData(g,"phase",.useraw=FALSE,.padNA=TRUE)
   if(!identical(as.integer(dim(gt)),as.integer(c(2,ns,length(indices))))||
      !identical(as.integer(dim(phase)),as.integer(c(ns,length(indices)))))stop("Unexpected complete GT/phase dimensions")
   if(!is.integer(gt)||any(!is.na(gt)&!gt %in% c(0L,1L)))stop("Unsupported genotype allele values")
   if(!is.integer(phase)||anyNA(phase)||any(!phase %in% c(0L,1L)))stop("Unsupported phase values")
   integers("genotype",as.vector(gt));integers("phase",as.vector(phase))
  }
 }
 for(con in connections)close(con)
 connections <- list()
 digests <- lapply(roles,function(role)list(kind=if(role %in% integer_roles)"INT32_LE" else "UTF8_LENGTH_INT32_LE",values=unname(counts[[role]]),sha256=digest(file=files[[role]],algo="sha256",serialize=FALSE)))
 names(digests) <- roles
 result <- list(schema="favor-gds-values-v1",mode=mode,variants=n,samples=ns,
                digests=digests,canonical_bytes=total_bytes,
                root_attributes_sha256=digest(get.attr.gdsn(g$root),algo="sha256",serialize=TRUE),
                all_nodes_including_hidden=metadata,
                runtime=list(R=R.version.string,gdsfmt=as.character(packageVersion("gdsfmt")),SeqArray=as.character(packageVersion("SeqArray")),digest=as.character(packageVersion("digest"))))
 temporary <- file.path(output,"audit.json.partial")
 write_json(result,temporary,auto_unbox=TRUE,null="null",na="null",digits=NA,pretty=TRUE)
 if(!file.rename(temporary,file.path(output,"audit.json")))stop("Cannot commit complete GDS audit")
}
if(sys.nframe()==0L)main(commandArgs(TRUE))
