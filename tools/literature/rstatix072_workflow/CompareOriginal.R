# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE)
stopifnot(length(args)==3L)
reference <- readRDS(file.path(args[1],'complete-original-results.rds'))
actual <- readRDS(file.path(args[2],'complete-original-results.rds'))
stopifnot(length(reference)==248L,identical(names(reference),names(actual)))
rows <- list();returned_rows <- 0L;returned_cells <- 0L
for(i in seq_along(reference)) {
 a <- actual[[i]];r <- reference[[i]]
 stopifnot(identical(a$id,r$id),identical(a$original_call,r$original_call),identical(a$seed_before,r$seed_before),identical(a$seed_after,r$seed_after))
 # Preserve both full original RDS objects. Formula environments refer to
 # distinct execution contexts; compare complete values/attributes/arguments
 # without claiming identical environment pointers or opaque RDS byte parity.
 difference <- all.equal(a$value,r$value,check.environment=FALSE,tolerance=0)
 if(!isTRUE(difference))stop('Complete original returned object differs for ',a$id,': ',paste(difference,collapse='; '))
 stopifnot(length(a$conditions)==length(r$conditions))
 for(j in seq_along(a$conditions)) {
  ac <- a$conditions[[j]];rc <- r$conditions[[j]]
  stopifnot(identical(ac$kind,rc$kind),identical(ac$class,rc$class),identical(ac$message,rc$message),identical(ac$call,rc$call))
 }
 filename <- sprintf('%04d.tsv',i)
 stopifnot(identical(readBin(file.path(args[1],filename),'raw',n=file.info(file.path(args[1],filename))$size),
  readBin(file.path(args[2],filename),'raw',n=file.info(file.path(args[2],filename))$size)))
 returned_rows <- returned_rows+nrow(a$value);returned_cells <- returned_cells+nrow(a$value)*ncol(a$value)
 rows[[i]] <- list(id=a$id,complete_original_object_equal=TRUE,rows=nrow(a$value),columns=ncol(a$value),
  classes=class(a$value),complete_conditions=length(a$conditions),caller_rng_states_equal=TRUE)
}
stopifnot(identical(readLines(file.path(args[1],'complete-index.tsv')),readLines(file.path(args[2],'complete-index.tsv'))))
jsonlite::write_json(list(status='PASS_COMPLETE_ORIGINAL_RSTATIX_RETURNED_OBJECTS_ATTRIBUTES_ARGUMENTS_TABLES_AND_CONDITIONS',
 complete_calls=248L,complete_returned_rows=returned_rows,complete_returned_cells=returned_cells,records=rows,
 formula_execution_environment_pointers_compared=FALSE,opaque_RDS_byte_parity_claimed=FALSE,
 original_returned_objects_modified=FALSE,paper_reproduced=FALSE),args[3],pretty=TRUE,auto_unbox=TRUE,digits=17)
cat('PASS_COMPLETE_ORIGINAL_RSTATIX_RETURNED_OBJECTS calls=248 rows=',returned_rows,' cells=',returned_cells,'\n',sep='')
