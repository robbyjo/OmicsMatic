# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Owned observer. The original installed namespace is never rewritten.
action <- 'FAIL2: NO COMPLETE RESULT. Retain inputs, full original returns/errors, partials and logs; correct the named input/design, numerical, integrity, capacity or process cause and retry NEW output. SKIP3: restore the exact permitted named prerequisite or retain the dependent-analysis skip. Downloads/private installs remain allowed where permitted. No version, population, unit, threshold, identifier or estimator substitution.'
warnings.seen <- messages.seen <- character(); calls <- list(); tables <- list(); guards <- list()
ready <- FALSE; json.ready <- FALSE; current.call <- NULL
report <- list(status='RUNNING_NO_RESULT_ADMITTED',results_admitted=FALSE)
unavailable <- function(message)stop(structure(list(message=message,call=NULL),class=c('omics_unavailable','error','condition')))
write.utf8 <- function(lines,path) {con<-file(path,'wb');on.exit(close(con));if(length(lines))writeBin(charToRaw(enc2utf8(paste0(paste(lines,collapse='\n'),'\n'))),con)}
json <- function(value,path)write.utf8(jsonlite::toJSON(value,auto_unbox=TRUE,null='null',na='string',digits=17,pretty=TRUE,force=TRUE),path)
number <- function(x)vapply(x,function(v)if(is.nan(v))'NaN' else if(is.na(v))'NA' else if(is.infinite(v))if(v>0)'Inf' else '-Inf' else sprintf('%.17g',v),'')
table.out <- function(value,label) {
    if(is.matrix(value))value<-as.data.frame(value,check.names=FALSE,stringsAsFactors=FALSE)
    stopifnot(is.data.frame(value));path<-paste0(label,'.tsv')
    columns<-lapply(value,function(x)if(is.numeric(x))number(x) else ifelse(is.na(x),'NA',as.character(x)))
    if(any(grepl('[\t\r\n]',names(columns)))||any(vapply(columns,function(x)any(grepl('[\t\r\n]',x)),TRUE)))stop('Original output contains TSV controls; original RDS retained, export refused')
    lines<-if(nrow(value))do.call(paste,c(columns,sep='\t')) else character()
    write.utf8(c(paste(names(columns),collapse='\t'),lines),path)
    tables[[path]]<<-list(rows=nrow(value),columns=as.list(names(value)),types=unname(as.list(vapply(value,function(x)typeof(x),''))),row_names=as.list(rownames(value)))
    invisible(value)
}
input <- function(path) {
    x<-read.delim(path,check.names=FALSE,quote='',comment.char='',colClasses='character',na.strings='NA',encoding='UTF-8')
    for(name in names(x)) {
        if(grepl('^(beta|se|eaf|pval|samplesize|ncase|ncontrol|pos)[.](exposure|outcome)$',name))x[[name]]<-as.numeric(x[[name]])
        else if(name%in%c('mr_keep','mr_keep.exposure','mr_keep.outcome','remove','palindromic','ambiguous','proxy.outcome'))x[[name]]<-as.logical(x[[name]])
    };x
}
invoke <- function(label,fun,args) {
    at<-length(calls)+1L;folder<-sprintf('objects/%03d-%s',at,label);dir.create(folder)
    saveRDS(args,paste0(folder,'/arguments.rds'),version=3);saveRDS(.Random.seed,paste0(folder,'/rng-before.rds'),version=3)
    calls[[at]]<<-list(label=label,folder=folder,complete=FALSE);current.call<<-at
    value<-tryCatch(do.call(fun,args),error=function(error) {
        saveRDS(error,paste0(folder,'/original-error.rds'),version=3)
        saveRDS(.Random.seed,paste0(folder,'/rng-after.rds'),version=3)
        calls[[at]]$error<<-list(message=conditionMessage(error),class=as.list(class(error)),call=paste(deparse(conditionCall(error)),collapse='\n'))
        stop(error)
    })
    saveRDS(value,paste0(folder,'/original-return.rds'),version=3);saveRDS(.Random.seed,paste0(folder,'/rng-after.rds'),version=3)
    calls[[at]]$complete<<-TRUE;calls[[at]]$return_class<<-as.list(class(value));current.call<<-NULL
    value
}
ns <- NULL
source.guard <- function() {
    source.zip<-'../source/reference/source.zip';entries<-utils::unzip(source.zip,list=TRUE)$Name;definitions<-list()
    for(file in entries[grepl('^R/.*[.]R$',entries)]) {
        con<-unz(source.zip,file,open='r');code<-tryCatch(parse(con,keep.source=FALSE,encoding='UTF-8'),finally=close(con))
        for(e in code)if(is.call(e)&&length(e)==3L&&(identical(e[[1]],as.name('<-'))||identical(e[[1]],as.name('=')))&&is.symbol(e[[2]])&&is.call(e[[3]])&&identical(e[[3]][[1]],as.name('function')))definitions[[as.character(e[[2]])]]<-e[[3]]
    }
    if(length(definitions)<100L)stop('Original source function population unexpectedly small')
    for(name in names(definitions)) {
        if(!exists(name,ns,inherits=FALSE))stop(paste('Original namespace function missing:',name))
        original<-get(name,ns,inherits=FALSE);reference<-eval(definitions[[name]],envir=ns)
        if(!identical(body(original),body(reference))||!identical(formals(original),formals(reference)))stop(paste('Original namespace source body/formals differ:',name))
        guards[[name]]<<-list(body=body(original),formals=formals(original))
    }
}
guard.after <- function()for(name in names(guards)) {
    original<-get(name,ns,inherits=FALSE)
    if(!identical(body(original),guards[[name]]$body)||!identical(formals(original),guards[[name]]$formals))stop(paste('Original namespace changed during analysis:',name))
}
main <- function() {
    .libPaths(c(readLines('runtime-paths.txt',encoding='UTF-8',warn=FALSE),.Library),include.site=FALSE)
    if(as.character(getRversion())!='4.6.1')unavailable(paste('Exact R4.6.1 profile required; found',getRversion()))
    if(!requireNamespace('jsonlite',quietly=TRUE))unavailable('jsonlite is unavailable in the explicitly declared libraries')
    json.ready<<-TRUE;r<-jsonlite::fromJSON('../effective-request.json',simplifyVector=FALSE)
    if(!requireNamespace('TwoSampleMR',quietly=TRUE))unavailable('Original TwoSampleMR namespace or a required dependency cannot load in the declared libraries')
    d<-utils::packageDescription('TwoSampleMR')
    if(d$Version!='0.7.14'||is.null(d$RemoteSha)||d$RemoteSha!='c08ab8a67b457d8ab87dcb7f6d33d8c8452e4cc6')unavailable('The named exact TwoSampleMR0.7.14 c08ab8a6 profile is not supplied; adjacent editions are not substituted')
    ns<<-asNamespace('TwoSampleMR');source.guard()
    if('mr_raps'%in%unlist(r$methods)&&!requireNamespace('mr.raps',quietly=TRUE))unavailable('Requested mr_raps needs the separately permitted mr.raps package; install/qualify it or retain the skip')
    saveRDS(list(R=R.version,session=sessionInfo(),description=d,external=extSoftVersion()),'runtime-provenance.rds',version=3)
    capture.output(sessionInfo(),file='session.txt');json(list(R=as.character(getRversion()),package=d$Package,version=d$Version,commit=d$RemoteSha,source_functions_guarded=length(guards),namespace_rewritten=FALSE),'runtime-profile.json')
    RNGkind('Mersenne-Twister','Inversion','Rejection');set.seed(r$seed);saveRDS(list(kind=RNGkind(),state=.Random.seed),'rng-initial.rds',version=3)
    dir.create('objects');ready<<-TRUE
    op<-r$operation
    if(op=='MR')dat<-input('../inputs/harmonised') else {
        exposure<-input('../inputs/exposure');outcome<-input('../inputs/outcome')
        if(op=='MV_HARMONISE') {
            value<-invoke('mv_harmonise_data',ns$mv_harmonise_data,list(exposure_dat=exposure,outcome_dat=outcome,harmonise_strictness=r$harmonise_action))
            report$mv_members<<-lapply(value,function(x)list(type=typeof(x),length=length(x),dimensions=as.list(dim(x))))
            for(name in names(value)) {
                x<-value[[name]]
                if(is.null(x))warnings.seen<<-c(warnings.seen,paste('Original multivariable member',name,'is NULL; it remains absent, with no imputed values'))
                else if(is.data.frame(x)||is.matrix(x))table.out(x,paste0('mv-',name))
                else if(is.atomic(x))table.out(data.frame(value=x,check.names=FALSE),paste0('mv-',name))
                else stop(paste('Unexpected original multivariable return member:',name))
            }
            if(!length(value$outcome_beta))stop('Original multivariable preparation has no complete instruments; original return retained')
            report$original_population<<-list(mv_instruments=length(value$outcome_beta));return(invisible(NULL))
        }
        dat<-invoke('harmonise_data',ns$harmonise_data,list(exposure_dat=exposure,outcome_dat=outcome,action=r$harmonise_action))
        table.out(dat,'harmonised');if(is.data.frame(attr(dat,'log')))table.out(attr(dat,'log'),'harmonisation-log')
        if(!nrow(dat))stop('No overlapping instruments after original harmonization; original empty return retained')
    }
    report$original_population<<-list(rows=nrow(dat),kept=sum(dat$mr_keep%in%TRUE),excluded=sum(!dat$mr_keep%in%TRUE))
    if(op%in%c('MR','HARMONISE_AND_MR')) {
        keep<-dat$mr_keep%in%TRUE
        if(any(!is.finite(as.matrix(dat[keep,c('beta.exposure','beta.outcome','se.exposure','se.outcome'),drop=FALSE]))))stop('Kept original MR rows contain nonfinite effects/SEs; no omission/imputation')
        methods<-unlist(r$methods);parameters<-r$parameters
        method.list<-ns$mr_method_list();table.out(method.list,'method-list')
        value<-invoke('mr',ns$mr,list(dat=dat,parameters=parameters,method_list=methods));table.out(value,'mr')
        groups<-unique(dat[,c('id.exposure','id.outcome'),drop=FALSE]);population<-list()
        for(i in seq_len(nrow(groups)))for(method in methods) {
            selected<-dat$id.exposure==groups$id.exposure[i]&dat$id.outcome==groups$id.outcome[i]&dat$mr_keep%in%TRUE
            n<-sum(selected);display<-method.list$name[match(method,method.list$obj)]
            present<-nrow(value)>0L&&any(value$id.exposure==groups$id.exposure[i]&value$id.outcome==groups$id.outcome[i]&value$method==display)
            state<-if(present)'ESTIMATE_RETURNED' else if(n==0L)'NO_RETAINED_INSTRUMENTS' else if(n>1L&&length(methods)>1L&&method=='mr_wald_ratio')'SOURCE_MULTISNP_WALD_OMITTED' else 'SOURCE_NO_ESTIMATE'
            population[[length(population)+1L]]<-data.frame(id.exposure=groups$id.exposure[i],id.outcome=groups$id.outcome[i],method=method,retained_instruments=n,status=state,estimate_admitted=present)
            if(!present)warnings.seen<<-c(warnings.seen,paste(state,groups$id.exposure[i],groups$id.outcome[i],method,'has no admitted estimate; inspect original warnings and retained inputs before choosing a NEW analysis'))
        }
        table.out(do.call(rbind,population),'method-population')
        if(!nrow(value))stop('Original mr returned no estimable method rows; empty original return retained')
        if(any(!is.finite(value$b))||any(!is.finite(value$pval))||any(value$pval<0|value$pval>1))stop('Original MR core fields unavailable/nonfinite; no complete result')
        sign.name<-method.list$name[method.list$obj=='mr_sign'];se.required<-!value$method%in%sign.name
        if(any(!is.finite(value$se[se.required]))||any(value$se[se.required]<0))stop('Original MR standard errors unavailable/nonfinite; raw statistics retained')
        for(sensitivity in unlist(r$sensitivity)) {
            if(sensitivity=='HETEROGENEITY') {
                selected<-methods[methods%in%method.list$obj[method.list$heterogeneity_test]]
                if(!length(selected)){warnings.seen<<-c(warnings.seen,'No selected estimator supplies a source heterogeneity test; no result fabricated');next}
                x<-invoke('mr_heterogeneity',ns$mr_heterogeneity,list(dat=dat,parameters=parameters,method_list=selected))
            }else if(sensitivity=='PLEIOTROPY')x<-invoke('mr_pleiotropy_test',ns$mr_pleiotropy_test,list(dat=dat))
            else if(sensitivity=='SINGLE_SNP')x<-invoke('mr_singlesnp',ns$mr_singlesnp,list(dat=dat,parameters=parameters))
            else x<-invoke('mr_leaveoneout',ns$mr_leaveoneout,list(dat=dat,parameters=parameters))
            if(is.null(x)||!nrow(x))warnings.seen<<-c(warnings.seen,paste('Original',sensitivity,'returned no applicable rows; no sensitivity result fabricated'))
            else table.out(x,paste0('sensitivity-',sensitivity))
        }
        warnings.seen<<-c(warnings.seen,'Original mr omits inapplicable/all-NA methods and skips multi-SNP Wald when multiple methods are selected. Read the saved method list, mr rows and population record; missing rows are not estimates. Source sensitivity function defaults are preserved in the guarded definitions and captured arguments.')
    }
}
code<-0L;failure<-NULL
tryCatch(withCallingHandlers(main(),warning=function(w){warnings.seen<<-c(warnings.seen,conditionMessage(w));invokeRestart('muffleWarning')},message=function(m){messages.seen<<-c(messages.seen,conditionMessage(m));invokeRestart('muffleMessage')}),error=function(error) {
    code<<-if(inherits(error,'omics_unavailable'))3L else 2L;failure<<-error
    saveRDS(error,'worker-error.rds',version=3)
})
if(ready)tryCatch(guard.after(),error=function(error){code<<-2L;failure<<-error;saveRDS(error,'guard-after-error.rds',version=3)})
status<-if(code==0L)'completed-with-warnings' else if(code==3L)'SKIP3_DEPENDENCY_UNAVAILABLE' else 'FAIL2_NO_COMPLETE_RESULT'
report$status<-status;report$results_admitted<-code==0L;report$required_action<-action;report$calls<-calls;report$tables<-tables
report$original_namespace_rewritten<-FALSE;report$source_functions_guarded<-length(guards)
if(!is.null(failure))report$error<-conditionMessage(failure)
saveRDS(list(report=report,warnings=warnings.seen,messages=messages.seen,guards=guards),'observer-final.rds',version=3)
write.utf8(c(action,'',warnings.seen),'warnings.txt');write.utf8(messages.seen,'messages.txt')
if(json.ready)json(report,'report.json')
detail<-if(is.null(failure))'Original returns retained; review warnings and source population selection' else gsub('[\t\r\n]',' ',conditionMessage(failure))
write.utf8(c('status\tdetail\trequired_action',paste(status,detail,action,sep='\t')),'worker-status.tsv')
quit(save='no',status=code,runLast=FALSE)
