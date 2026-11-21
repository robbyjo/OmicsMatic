# Fault-inject instrumentation only; original statistical functions stay unchanged.
args<-commandArgs(TRUE);stopifnot(length(args)==2L)
worker<-normalizePath(args[1],winslash='/',mustWork=TRUE);reference<-readRDS(args[2])
code.expressions<-parse(worker,keep.source=FALSE);at<-which(vapply(code.expressions,function(e)is.call(e)&&identical(e[[1]],as.name('<-'))&&identical(e[[2]],as.name('code')),TRUE));stopifnot(length(at)==1L)
for(e in code.expressions[seq_len(at-1L)])eval(e,envir=.GlobalEnv)
stopifnot(requireNamespace('jsonlite',quietly=TRUE),requireNamespace('limma',quietly=TRUE),requireNamespace('statmod',quietly=TRUE))
before<-lapply(c('glmgam.fit','mixedModel2Fit'),function(name){f<-get(name,asNamespace('statmod'),inherits=FALSE);list(body=body(f),formals=formals(f))})
names(before)<-c('glmgam.fit','mixedModel2Fit')
fault.injected<-FALSE;original.recorder<-record.internal
record.internal<-function(label,value) {
 if(!fault.injected){close(journal);fault.injected<<-TRUE}
 original.recorder(label,value)
}
for(e in code.expressions[at:length(code.expressions)]) {
 if(is.call(e)&&identical(e[[1]],as.name('quit')))break
 eval(e,envir=.GlobalEnv)
}
stopifnot(fault.injected,code==2L,!report$results_admitted,report$status=='FAIL2_NO_COMPLETE_RESULT',length(report$finalization_failures)>0L,file.exists('objects.rds'))
stopifnot(grepl('Original model journal write failed',report$error,fixed=TRUE))
retained<-readRDS('objects.rds');pick<-function(x,label){which<-grep(paste0('-',label,'$'),names(x));stopifnot(length(which)==1L);x[[which]]}
comparisons<-vapply(c('lmFit-original','contrasts-fit-original','moderated-original'),function(label)identical(pick(retained,label),pick(reference,label)),TRUE);stopifnot(all(comparisons))
fallbacks<-grep('-journal-fallback-',names(retained),fixed=TRUE);stopifnot(length(fallbacks)>0L)
for(name in names(before)){f<-get(name,asNamespace('statmod'),inherits=FALSE);stopifnot(identical(body(f),before[[name]]$body),identical(formals(f),before[[name]]$formals))}
jsonlite::write_json(list(status='PASS_FULL_R_WORKER_REFUSES_JOURNAL_FAILURE_AND_RETAINS_EXACT_ORIGINAL_FITS',fault_injection_scope='Instrumented recorder closes its journal; original statistical bodies and namespaces remain unchanged',worker_status=report$status,results_admitted=FALSE,original_complete_fits_identical=as.list(comparisons),journal_fallback_snapshots=length(fallbacks),finalization_failures=report$finalization_failures),path='../FAULT-CONTROL.json',pretty=TRUE,auto_unbox=TRUE,null='null')
