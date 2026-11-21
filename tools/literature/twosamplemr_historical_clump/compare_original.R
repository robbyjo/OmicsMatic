# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Fresh unmodified namespace calls; original complete objects and RNG are retained.
args<-commandArgs(TRUE);stopifnot(length(args)==2L)
root<-normalizePath(args[[1]],winslash='/',mustWork=TRUE);out<-args[[2]];stopifnot(!file.exists(out));dir.create(out,recursive=TRUE)
out<-normalizePath(out,winslash='/',mustWork=TRUE)
.libPaths(c(readLines(file.path(root,'worker/runtime-paths.txt'),warn=FALSE),.Library),include.site=FALSE)
stopifnot(as.character(getRversion())=='4.6.1',requireNamespace('TwoSampleMR',quietly=TRUE),requireNamespace('ieugwasr',quietly=TRUE),requireNamespace('jsonlite',quietly=TRUE))
two<-utils::packageDescription('TwoSampleMR');ld<-utils::packageDescription('ieugwasr')
stopifnot(two$Version=='0.6.8',ld$Version=='1.2.1',ld$RemoteSha=='ce5d0c07c40f1f9cdafff40bb6a644a3913c9ad6',requireNamespace('digest',quietly=TRUE))
reference<-jsonlite::fromJSON(file.path(root,'source/clump-reference/TWOSAMPLEMR-0.6.8-PROFILE.json'),simplifyVector=FALSE)
ns<-asNamespace('TwoSampleMR');ld.ns<-asNamespace('ieugwasr')
fingerprint<-function(fun)digest::digest(serialize(list(formals=formals(fun),body=body(fun)),NULL,version=3),serialize=FALSE,algo='sha256')
guard<-function() {
    stopifnot(length(reference$guards)==149L,reference$commit=='806703f0693cdd1c01cbde1d8ec51b3ab0ae9d14')
    for(name in names(reference$guards))stopifnot(identical(fingerprint(get(name,ns,inherits=FALSE)),reference$guards[[name]]$sha256))
    code<-parse(file.path(root,'source/clump-reference/ieugwasr-ld_clump.R'),keep.source=FALSE)
    count<-0L
    for(e in code)if(is.call(e)&&length(e)==3L&&identical(e[[1]],as.name('<-'))&&is.symbol(e[[2]])&&is.call(e[[3]])&&identical(e[[3]][[1]],as.name('function'))) {
        name<-as.character(e[[2]]);actual<-get(name,ld.ns,inherits=FALSE);expected<-eval(e[[3]],envir=ld.ns);stopifnot(identical(formals(actual),formals(expected)),identical(body(actual),body(expected)));count<-count+1L
    }
    stopifnot(count==7L)
}
guard()

report<-jsonlite::fromJSON(file.path(root,'worker/report.json'),simplifyVector=FALSE);setwd(file.path(root,'worker'))
capture<-function(fun,input) {
    warnings<-messages<-character();value<-error<-NULL
    value<-withCallingHandlers(tryCatch(do.call(fun,input),error=function(e){error<<-e;NULL}),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')},message=function(m){messages<<-c(messages,conditionMessage(m));invokeRestart('muffleMessage')})
    list(value=value,error=error,warnings=warnings,messages=messages,rng_after=.Random.seed)
}
write.result<-function(value)writeLines(jsonlite::toJSON(value,auto_unbox=TRUE,pretty=TRUE),file.path(out,'RESULT.json'),useBytes=TRUE)
if(report$status!='completed-with-warnings') {
    top<-report$calls[[1]];folder<-file.path(root,'worker',top$folder);input<-readRDS(file.path(folder,'arguments.rds'));expected<-readRDS(file.path(folder,'original-error.rds'))
    assign('.Random.seed',readRDS(file.path(folder,'rng-before.rds')),envir=.GlobalEnv)
    actual<-capture(get('clump_data',asNamespace('TwoSampleMR')),input);guard();saveRDS(actual,file.path(out,'original-error-comparison.rds'),version=3)
    stopifnot(!is.null(actual$error),identical(class(actual$error),class(expected)),identical(actual$rng_after,readRDS(file.path(folder,'rng-after.rds'))))
    header<-function(e)strsplit(conditionMessage(e),'\nEnd of plink log:\n',fixed=TRUE)[[1]][1]
    stopifnot(identical(header(actual$error),header(expected)))
    write.result(list(status='PASS_ORIGINAL_RAISED_ERROR_CLASS_STATIC_HEADER_AND_RNG',full_raw_errors_retained=TRUE,full_error_identity_claimed=FALSE,volatile_PLINK_log_paths_times_seeds_not_compared=TRUE,original_bindings_rewritten=FALSE));quit(save='no',status=0,runLast=FALSE)
}
entries<-c(lapply(report$calls,function(call)c(call,list(package='TwoSampleMR'))),lapply(report$ld_calls,function(call)c(call,list(label=call$name,package='ieugwasr'))))
rows<-list()
for(i in seq_along(entries)) {
    call<-entries[[i]];stopifnot(isTRUE(call$complete));folder<-file.path(root,'worker',call$folder)
    input<-readRDS(file.path(folder,'arguments.rds'));expected<-readRDS(file.path(folder,'original-return.rds'));tmp.rebound<-FALSE
    if('tmpdir'%in%names(input)){input$tmpdir<-tempdir();tmp.rebound<-TRUE}
    assign('.Random.seed',readRDS(file.path(folder,'rng-before.rds')),envir=.GlobalEnv)
    actual<-capture(get(call$label,asNamespace(call$package),inherits=FALSE),input);guard()
    identical.return<-is.null(actual$error)&&identical(actual$value,expected);identical.rng<-identical(actual$rng_after,readRDS(file.path(folder,'rng-after.rds')))
    saveRDS(actual,file.path(out,sprintf('%03d-%s.rds',i,call$label)),version=3)
    rows[[i]]<-list(package=call$package,label=call$label,full_original_return_identical=identical.return,attributes_and_classes_included=TRUE,RNG_endpoint_identical=identical.rng,non_scientific_temporary_directory_rebound=tmp.rebound,original_bindings_rewritten=FALSE)
    if(!identical.return||!identical.rng){capture.output(all.equal(actual$value,expected),file=file.path(out,sprintf('%03d-DIFFERENCE.txt',i)));write.result(list(status='FAIL_ORIGINAL_RETURN_OR_RNG_DIFFERENCE',calls=rows));stop(paste('Original return/RNG differs:',call$label))}
}
write.result(list(status='PASS_COMPLETE_UNMODIFIED_CLUMP_RETURNS_AND_RNG',calls=rows,original_bindings_rewritten=FALSE,whole_paper_reproduced=FALSE,TwoSampleMR_source_functions_guarded=149L,ieugwasr_source_functions_guarded=7L,dependency_era_reproduced=FALSE))
