# Original-runtime comparison of observational error paths, without statistical rewrites.
args<-commandArgs(TRUE);stopifnot(length(args)==4L)
worker<-normalizePath(args[1],winslash='/',mustWork=TRUE);libraries<-readLines(args[2],warn=FALSE);out<-normalizePath(args[3],winslash='/',mustWork=TRUE);expected<-args[4]
setwd(out);writeLines(libraries,'runtime-paths.txt',useBytes=TRUE)
expressions<-parse(worker,keep.source=FALSE)
for(e in expressions) {
 if(is.call(e)&&identical(e[[1]],as.name('<-'))&&identical(e[[2]],as.name('main')))break
 eval(e,envir=.GlobalEnv)
}
stopifnot(requireNamespace('jsonlite',quietly=TRUE),requireNamespace('statmod',quietly=TRUE))
original<-get('glmgam.fit',asNamespace('statmod'),inherits=FALSE);before.body<-body(original);before.formals<-formals(original)
specs<-list(incomplete_iteration=list(X=matrix(0,4L,2L),y=rep(1,4L),coef.start=c(1,1)),negative_input=list(X=cbind(1,1:4),y=c(-1,1,2,3)),empty_return=list(X=matrix(numeric(),0L,0L),y=numeric()),zero_return=list(X=cbind(1,1:4),y=rep(0,4L)),converged=list(X=cbind(1,1:6),y=c(1,2,1.8,3,2.5,4)),journal_failure_during_original_error=list(X=matrix(0,4L,2L),y=rep(1,4L),coef.start=c(1,1)),journal_failure_after_original_success=list(X=cbind(1,1:6),y=c(1,2,1.8,3,2.5,4)))
results<-list()
capture<-function(fn,a)tryCatch(list(value=do.call(fn,a),error=NULL),error=function(e)list(value=NULL,error=conditionMessage(e)))
for(name in names(specs)) {
 dir.create(name);setwd(file.path(out,name));internal.failures<-character();internal.count<-0L;gamma.count<-0L;internal.context<-list(feature_index=1L,feature_id='literal_\u00e9')
 objects<-list();object.count<-0L;dir.create('objects');writeLines('index\tlabel\tstart\tend','internal-model-index.tsv');writeLines('fit_index\tfeature_index\titerations\tmaximum_iterations\ttolerance\tscore_step\trelative_deviance\tdamping_ratio\tconverged\tscore1\tscore2\tstep1\tstep2','convergence.tsv')
 journal<-file('internal-model-journal.bin',open='wb');closed<-startsWith(name,'journal_failure_');if(closed)close(journal)
 copy<-original;env<-new.env(parent=asNamespace('statmod'));env$.omics_observe<-gamma.observer
 prefix<-quote(on.exit(.omics_observe(environment()),add=TRUE));body(copy)<-as.call(list(as.name('{'),prefix,body(original)));environment(copy)<-env
 stopifnot(identical(body(copy)[[3]],body(original)),identical(formals(copy),formals(original)))
 stock<-capture(original,specs[[name]]);observed<-capture(copy,specs[[name]])
 saveRDS(stock,'original.rds',version=3);saveRDS(observed,'observed.rds',version=3)
 if(!closed)close(journal);saveRDS(objects,'fallback-objects.rds',version=3)
 same<-identical(stock,observed)
 results[[name]]<-list(source_error=stock$error,observed_error=observed$error,complete_return_identical=same,observer_failures=as.list(internal.failures),iterative_observations=gamma.count,journal_closed_for_control=closed)
 if(expected=='fixed') {
  stopifnot(same)
  if(name=='incomplete_iteration'||closed)stopifnot(length(internal.failures)>0L)
 } else if(expected=='legacy'&&name=='incomplete_iteration')stopifnot(!same,!identical(stock$error,observed$error))
 setwd(out)
}

mixed.original<-get('mixedModel2Fit',asNamespace('statmod'),inherits=FALSE);mixed.body<-body(mixed.original);mixed.formals<-formals(mixed.original)
mixed.args<-list(X=matrix(1,6L,1L),Z=model.matrix(~0+factor(rep(1:3,each=2))),only.varcomp=TRUE)
for(kind in c('error','success')) {
 name<-paste0('mixed_',kind,'_closed_journal');dir.create(name);setwd(file.path(out,name));objects<-list();object.count<-0L;dir.create('objects')
 internal.failures<-character();internal.count<-0L;gamma.count<-0L;internal.context<-list()
 writeLines('index\tlabel\tstart\tend','internal-model-index.tsv');writeLines('fit_index\tfeature_index\titerations\tmaximum_iterations\ttolerance\tscore_step\trelative_deviance\tdamping_ratio\tconverged\tscore1\tscore2\tstep1\tstep2','convergence.tsv')
 journal<-file('internal-model-journal.bin',open='wb');close(journal)
 a<-c(list(y=if(kind=='error')rep(.Machine$double.xmax/2,6) else c(1,1.4,3,3.2,5,5.1)),mixed.args)
 stock<-capture(mixed.original,a)
 wrapper<-function(...) {i<-1L;M<-matrix(0,1L,6L,dimnames=list('literal_gene',NULL));observed.mixed(...)}
 observed<-capture(wrapper,a);same<-identical(stock,observed)
 saveRDS(stock,'original.rds',version=3);saveRDS(observed,'observed.rds',version=3);saveRDS(objects,'fallback-objects.rds',version=3)
 results[[name]]<-list(source_error=stock$error,observed_error=observed$error,complete_return_identical=same,observer_failures=as.list(internal.failures),fallback_snapshots=object.count,journal_closed_for_control=TRUE)
 if(expected=='fixed')stopifnot(same,length(internal.failures)>0L,object.count>0L)
 if(expected=='legacy-mixed')stopifnot(!same)
 if(kind=='error')stopifnot(!is.null(stock$error)) else stopifnot(is.null(stock$error))
 setwd(out)
}
stopifnot(identical(body(get('mixedModel2Fit',asNamespace('statmod'))),mixed.body),identical(formals(get('mixedModel2Fit',asNamespace('statmod'))),mixed.formals))
stopifnot(identical(body(get('glmgam.fit',asNamespace('statmod'))),before.body),identical(formals(get('glmgam.fit',asNamespace('statmod'))),before.formals))
jsonlite::write_json(list(status=if(expected=='fixed')'PASS_ORIGINAL_ERRORS_RETURNS_AND_NAMESPACE_PRESERVED' else 'REPRODUCED_LEGACY_OBSERVER_MASKING',R=as.character(getRversion()),statmod=as.character(packageVersion('statmod')),cases=results),file.path(out,'RESULTS.json'),auto_unbox=TRUE,pretty=TRUE,null='null')
