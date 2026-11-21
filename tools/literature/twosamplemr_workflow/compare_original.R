# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent direct namespace calls against captured complete original objects.
args<-commandArgs(TRUE);stopifnot(length(args)==2L)
root<-normalizePath(args[[1]],winslash='/',mustWork=TRUE);out<-args[[2]]
stopifnot(!file.exists(out));dir.create(out,recursive=TRUE)
.libPaths(c(readLines(file.path(root,'worker/runtime-paths.txt'),warn=FALSE),.Library),include.site=FALSE)
stopifnot(as.character(getRversion())=='4.6.1',requireNamespace('TwoSampleMR',quietly=TRUE),requireNamespace('jsonlite',quietly=TRUE))
ns<-asNamespace('TwoSampleMR');description<-utils::packageDescription('TwoSampleMR')
stopifnot(description$Version=='0.7.14',description$RemoteSha=='c08ab8a67b457d8ab87dcb7f6d33d8c8452e4cc6')
report<-jsonlite::fromJSON(file.path(root,'worker/report.json'),simplifyVector=FALSE)
rows<-list()
for(i in seq_along(report$calls)) {
    call<-report$calls[[i]];stopifnot(isTRUE(call$complete));folder<-file.path(root,'worker',call$folder)
    input<-readRDS(file.path(folder,'arguments.rds'));expected<-readRDS(file.path(folder,'original-return.rds'))
    assign('.Random.seed',readRDS(file.path(folder,'rng-before.rds')),envir=.GlobalEnv)
    observed.warnings<-observed.messages<-character()
    actual<-withCallingHandlers(do.call(get(call$label,ns,inherits=FALSE),input),warning=function(w){observed.warnings<<-c(observed.warnings,conditionMessage(w));invokeRestart('muffleWarning')},message=function(m){observed.messages<<-c(observed.messages,conditionMessage(m));invokeRestart('muffleMessage')})
    result.identical<-identical(actual,expected);rng.identical<-identical(.Random.seed,readRDS(file.path(folder,'rng-after.rds')))
    saveRDS(list(actual=actual,warnings=observed.warnings,messages=observed.messages,rng_after=.Random.seed),file.path(out,sprintf('%03d-%s.rds',i,call$label)),version=3)
    rows[[i]]<-list(label=call$label,full_original_return_identical=result.identical,attributes_and_classes_included=TRUE,RNG_endpoint_identical=rng.identical)
    if(!result.identical||!rng.identical) {
        capture.output(all.equal(actual,expected),file=file.path(out,'DIFFERENCE.txt'))
        stop(paste('Complete original return or RNG endpoint differs:',call$label))
    }
}
writeLines(jsonlite::toJSON(list(status='PASS_COMPLETE_UNMODIFIED_NAMESPACE_RETURNS_AND_RNG',calls=rows,namespace_rewritten=FALSE,whole_paper_reproduced=FALSE),auto_unbox=TRUE,pretty=TRUE),file.path(out,'RESULT.json'),useBytes=TRUE)
