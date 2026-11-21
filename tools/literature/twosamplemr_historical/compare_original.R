# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Fresh direct calls; no observation worker or namespace rewriting is loaded.
args<-commandArgs(TRUE);stopifnot(length(args)==2L)
root<-normalizePath(args[[1]],winslash='/',mustWork=TRUE);out<-args[[2]]
stopifnot(!file.exists(out));dir.create(out,recursive=TRUE)
.libPaths(c(readLines(file.path(root,'worker/runtime-paths.txt'),warn=FALSE),.Library),include.site=FALSE)
stopifnot(as.character(getRversion())=='4.6.1',.Platform$OS.type=='windows',requireNamespace('jsonlite',quietly=TRUE),requireNamespace('digest',quietly=TRUE),requireNamespace('TwoSampleMR',quietly=TRUE))
reference<-jsonlite::fromJSON(file.path(root,'source/historical-profile.json'),simplifyVector=FALSE)
ns<-asNamespace('TwoSampleMR');description<-utils::packageDescription('TwoSampleMR');stopifnot(description$Version==reference$version)
fingerprint<-function(fun)digest::digest(serialize(list(formals=formals(fun),body=body(fun)),NULL,version=3),serialize=FALSE,algo='sha256')
guard<-function()for(name in names(reference$guards))stopifnot(identical(fingerprint(get(name,ns,inherits=FALSE)),reference$guards[[name]]$sha256))
guard();report<-jsonlite::fromJSON(file.path(root,'worker/report.json'),simplifyVector=FALSE);rows<-list();returns<-errors<-0L
for(i in seq_along(report$calls)) {
    call<-report$calls[[i]];folder<-file.path(root,'worker',call$folder);input<-readRDS(file.path(folder,'arguments.rds'))
    assign('.Random.seed',readRDS(file.path(folder,'rng-before.rds')),envir=.GlobalEnv);warnings<-messages<-character();error<-NULL
    actual<-tryCatch(withCallingHandlers(do.call(get(call$label,ns,inherits=FALSE),input),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')},message=function(m){messages<<-c(messages,conditionMessage(m));invokeRestart('muffleMessage')}),error=function(e){error<<-e;NULL})
    rng.identical<-identical(.Random.seed,readRDS(file.path(folder,'rng-after.rds')))
    if(isTRUE(call$complete)) {
        expected<-readRDS(file.path(folder,'original-return.rds'));same<-is.null(error)&&identical(actual,expected);returns<-returns+1L
        rows[[i]]<-list(label=call$label,full_original_return_identical=same,attributes_and_classes_included=TRUE,RNG_endpoint_identical=rng.identical)
        if(!same){capture.output(all.equal(actual,expected),file=file.path(out,'DIFFERENCE.txt'));stop(paste('Complete original return differs:',call$label))}
    }else {
        expected<-readRDS(file.path(folder,'original-error.rds'));same<-!is.null(error)&&identical(class(error),class(expected))&&identical(conditionMessage(error),conditionMessage(expected));errors<-errors+1L
        rows[[i]]<-list(label=call$label,error_class_and_message_identical=same,RNG_endpoint_identical=rng.identical,full_raised_conditions_retained=TRUE)
        if(!same)stop(paste('Original raised-error class/message differs:',call$label))
    }
    saveRDS(list(actual=actual,error=error,warnings=warnings,messages=messages,rng_after=.Random.seed),file.path(out,sprintf('%03d-%s.rds',i,call$label)),version=3)
    if(!rng.identical)stop(paste('Original RNG endpoint differs:',call$label));guard()
}
writeLines(jsonlite::toJSON(list(status='PASS_COMPLETE_TAGGED_NAMESPACE_RETURNS_ERRORS_AND_RNG',calls=rows,full_original_returns=returns,original_errors=errors,source_functions_guarded=length(reference$guards),namespace_rewritten=FALSE,paper_execution_commit_established=FALSE,dependency_era_reproduced=FALSE),auto_unbox=TRUE,pretty=TRUE),file.path(out,'RESULT.json'),useBytes=TRUE)
