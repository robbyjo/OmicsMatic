# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Diagnose the retained full-object difference; do not alter original R objects.
args <- commandArgs(TRUE);stopifnot(length(args)==2L);out <- args[[1L]];case <- args[[2L]]
r <- readRDS(file.path(case,"reference/complete-result.rds"))
a <- readRDS(file.path(case,"adapter/original/complete-result.rds"))
whole <- all.equal(r,a,tolerance=0)
definition <- list(reference=r$fbm$.refClassDef,adapter=a$fbm$.refClassDef)
saveRDS(definition,file.path(out,"complete-original-FBM-class-definitions.rds"),version=3)
classes <- all.equal(definition$reference,definition$adapter,tolerance=0)
writeLines(capture.output(str(definition,max.level=4)),file.path(out,"complete-class-definition-inspection.txt"))
nr <- r$fbm$nrow;nc <- r$fbm$ncol
stopifnot(identical(nr,a$fbm$nrow),identical(nc,a$fbm$ncol),identical(r$fbm$type,a$fbm$type))
paths <- lapply(c("reference-workspace","workspace"),function(directory) {
  path <- list.files(file.path(case,directory),pattern="[.]bk$",full.names=TRUE,recursive=TRUE)
  stopifnot(length(path)==1L,file.info(path)$size==nr*nc*4L);path
})
raw <- lapply(paths,function(path)readBin(path,"raw",n=file.info(path)$size))
values <- lapply(paths,function(path)matrix(readBin(path,numeric(),n=nr*nc,size=4L,endian="little"),nrow=nr))
saveRDS(list(reference=values[[1L]],adapter=values[[2L]],source_type=r$fbm$type,
  rows=nr,columns=nc,paths=paths),file.path(out,"complete-independent-FBM-payloads.rds"),version=3)
jsonlite::write_json(list(status="FULL_OBJECT_DIFFERENCE_RETAINED_FBM_PAYLOADS_INSPECTED",
  complete_object_comparison=whole,class_definition_comparison=classes,rows=nr,columns=nc,type=r$fbm$type,
  complete_raw_float_payload_identical=identical(raw[[1L]],raw[[2L]]),
  complete_decoded_matrices_identical=identical(values[[1L]],values[[2L]]),
  full_original_objects_modified=FALSE,live_pointer_persistence_qualified=FALSE,
  new_family_credit=0),file.path(out,"INSPECTION.json"),pretty=TRUE,auto_unbox=TRUE,null="null",na="string")
