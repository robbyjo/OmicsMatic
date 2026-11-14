# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Recompare complete captured returns and RNG objects in the exact original R runtime.
a<-commandArgs(TRUE);stopifnot(length(a)==3L)
.libPaths(c(a[[1L]],.libPaths()));loadNamespace("DoubletFinder");loadNamespace("Seurat")
root<-a[[2L]];out<-a[[3L]];if(file.exists(out))stop("Use a NEW complete-object verification record")
strict<-function(x,y)identical(x,y,num.eq=FALSE,single.NA=FALSE,attrib.as.set=FALSE,ignore.bytecode=FALSE,ignore.environment=FALSE,ignore.srcref=FALSE,extptr.as.ref=TRUE)
checks<-list();objects<-0L;errors<-0L
compare<-function(name,reference,actual) {
 pairs<-list(result=c(file.path(reference,"complete-original-return.rds"),file.path(actual,"complete-result.rds")),rng=c(file.path(reference,"complete-rng-before-after.rds"),file.path(actual,"complete-rng-before-after.rds")))
 answer<-list()
 for(kind in names(pairs)) {
  x<-readRDS(pairs[[kind]][[1L]]);y<-readRDS(pairs[[kind]][[2L]])
  equal<-strict(x,y);answer[[kind]]<-list(equal=equal,class=class(x))
  if(!equal)stop("Complete original ",kind," differs: ",name)
  objects<<-objects+1L
 }
 checks[[name]]<<-answer
}
references<-file.path(root,"original-workflow-references")
for(name in sort(list.dirs(references,recursive=FALSE,full.names=FALSE))) {
 reference<-file.path(references,name);actual<-file.path(root,"qualified-workflow-comparisons",name,"analysis/original")
 status<-jsonlite::fromJSON(file.path(reference,"ORIGINAL-STATUS.json"),simplifyVector=FALSE)
 if(status$original_complete)compare(name,reference,actual) else {
  got<-jsonlite::fromJSON(file.path(actual,"R-status.json"),simplifyVector=FALSE)
  stopifnot(identical(unlist(status$error_class,use.names=FALSE),unlist(got$error$class,use.names=FALSE)),identical(status$error_message,got$error$message),!file.exists(file.path(actual,"complete-result.rds")))
  errors<-errors+1L
 }
}
compare("public-install-end-to-end",file.path(references,"lognorm-default-pN"),file.path(root,"current-public-end-to-end/analysis/original"))
stopifnot(objects==22L,errors==2L)
result<-list(status="PASS_ALL22_COMPLETE_ORIGINAL_R_OBJECTS_AND_TWO_ORIGINAL_ERRORS",complete_R_objects_compared=objects,matching_original_errors=errors,checks=checks,fields_or_cells_omitted=FALSE,closure_environments_ignored=FALSE,numeric_bits_and_NA_payloads_compared=TRUE,opaque_RDS_file_byte_parity_claimed=FALSE,original_condition_identity_claimed=FALSE,models_fit_in_this_verifier=FALSE,convergence_qualified=FALSE,whole_tool_qualified=FALSE)
jsonlite::write_json(result,out,pretty=TRUE,auto_unbox=TRUE,null="null",na="string",digits=17)
cat(result$status,"\n")
