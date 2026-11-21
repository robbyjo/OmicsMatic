# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Owned clumping observer. The TwoSampleMR namespace stays unchanged. Two ieugwasr functions receive audited observation prefixes; original bodies/formals are retained and restored.
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
    value<-tryCatch(withCallingHandlers(do.call(fun,args),error=.omics_source_error),error=function(error) {
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
reference <- NULL
fingerprint <- function(fun)digest::digest(serialize(list(formals=formals(fun),body=body(fun)),NULL,version=3),serialize=FALSE,algo='sha256')
source.guard <- function() {
    reference<<-jsonlite::fromJSON('../source/clump-reference/TWOSAMPLEMR-0.6.8-PROFILE.json',simplifyVector=FALSE)
    if(reference$version!='0.6.8'||reference$commit!='806703f0693cdd1c01cbde1d8ec51b3ab0ae9d14'||length(reference$guards)!=149L)stop('Different original historical source reference')
    for(name in names(reference$guards)) {
        if(!exists(name,ns,inherits=FALSE)||!identical(fingerprint(get(name,ns,inherits=FALSE)),reference$guards[[name]]$sha256))stop(paste('Original installed body/formals differ from tagged source:',name))
        guards[[name]]<<-reference$guards[[name]]$sha256
    }
}
guard.after <- function()for(name in names(guards))if(!identical(fingerprint(get(name,ns,inherits=FALSE)),guards[[name]]))stop(paste('Original namespace changed during analysis:',name))

.omics_ld_calls <- list();.omics_frames<-list();.omics_errors<-list();.omics_observer_failures<-list();.omics_observing<-FALSE
.omics_original<-list();.omics_prefixed<-list();.omics_ld_guards<-list();.omics_ld_ns<-NULL;.omics_tmp<-NULL
.omics_bindings_restored<-FALSE
.omics_try <- function(stage,expr) {
    previous<-.omics_observing;.omics_observing<<-TRUE;on.exit({.omics_observing<<-previous})
    tryCatch(withCallingHandlers(force(expr),warning=function(w){invokeRestart('muffleWarning')}),error=function(e){.omics_observer_failures[[length(.omics_observer_failures)+1L]]<<-list(stage=stage,message=conditionMessage(e),class=as.list(class(e)));invisible(NULL)})
}
.omics_values <- function(frame,names) {
    result<-list();for(name in names)if(exists(name,frame,inherits=FALSE))result[name]<-list(get(name,frame,inherits=FALSE));result
}
.omics_enter <- function(name,frame) {
    token<-length(.omics_ld_calls)+1L;folder<-sprintf('ld-calls/%03d-%s',token,name)
    .omics_ld_calls[[token]]<<-list(name=name,folder=folder,complete=FALSE,artifact_snapshots=list())
    .omics_frames[[token]]<<-frame
    .omics_try(paste(name,'entry'),{
        if(!dir.create(folder,recursive=TRUE))stop('Could not create original-call capture directory')
        args<-.omics_values(frame,c('dat','clump_kb','clump_r2','clump_p','pop','bfile','plink_bin','tmpdir','threads','memory'))
        saveRDS(args,file.path(folder,'arguments.rds'),version=3);saveRDS(.Random.seed,file.path(folder,'rng-before.rds'),version=3)
    });token
}
.omics_snapshot <- function(token,frame,stage) {
    if(!exists('fn',frame,inherits=FALSE))return(invisible(NULL))
    fn<-get('fn',frame,inherits=FALSE)
    prefix<-file.path(normalizePath(dirname(fn),winslash='/',mustWork=TRUE),basename(fn))
    if(!startsWith(prefix,paste0(.omics_tmp,'/')))stop('Original cleanup prefix lies outside the owned R temporary directory')
    files<-Sys.glob(paste0(fn,'*'));if(!length(files))return(invisible(NULL))
    index<-length(.omics_ld_calls[[token]]$artifact_snapshots)+1L
    destination<-file.path(.omics_ld_calls[[token]]$folder,sprintf('artifacts-%03d',index));if(!dir.create(destination))stop('Could not create artifact snapshot directory')
    rows<-list()
    for(file in files) {
        path<-normalizePath(file,winslash='/',mustWork=TRUE)
        if(!startsWith(path,paste0(.omics_tmp,'/'))||!startsWith(path,prefix)||dir.exists(path))stop('Original temporary artifact has an unsafe path or type')
        suffix<-substring(path,nchar(prefix)+1L);name<-if(!nzchar(suffix))'association-input.txt' else paste0('plink',suffix)
        if(grepl('[/\\]',name))stop('Unsafe original artifact name')
        before<-unname(tools::md5sum(path));target<-file.path(destination,name)
        if(!file.copy(path,target,overwrite=FALSE,copy.mode=TRUE,copy.date=TRUE))stop('Could not retain original artifact before cleanup')
        after<-unname(tools::md5sum(path));saved<-unname(tools::md5sum(target))
        if(!identical(before,after)||!identical(before,saved))stop('Original artifact changed during capture')
        rows[[length(rows)+1L]]<-list(original=path,suffix=suffix,saved=target,bytes=unname(file.info(target)$size),source_md5_before=before,source_md5_after=after,saved_md5=saved)
    }
    .omics_ld_calls[[token]]$artifact_snapshots[[index]]<<-list(stage=stage,original_prefix=prefix,files=rows)
}
.omics_unlink <- function(x,recursive=FALSE,force=FALSE,expand=TRUE) {
    frame<-parent.frame();token<-get('.omics_clump_token',frame,inherits=FALSE);fn<-get('fn',frame,inherits=FALSE)
    prefix<-file.path(normalizePath(dirname(fn),winslash='/',mustWork=TRUE),basename(fn))
    if(!identical(as.character(x),paste0(fn,'*'))||recursive||!expand||!startsWith(prefix,paste0(.omics_tmp,'/')))stop('Refusing unexpected/outside-owned original cleanup; no files deleted')
    .omics_try('capture-before-original-unlink',.omics_snapshot(token,frame,'before-original-unlink'))
    base::unlink(x,recursive=recursive,force=force,expand=expand)
}
.omics_leave <- function(token,frame,value) {
    .omics_try('capture-original-exit',{
        .omics_snapshot(token,frame,'original-exit')
        folder<-.omics_ld_calls[[token]]$folder
        locals<-.omics_values(frame,c('dat','ids','i','x','res','resources','shell','fn','fun2','status','log'))
        saveRDS(locals,file.path(folder,'locals.rds'),version=3);saveRDS(.Random.seed,file.path(folder,'rng-after.rds'),version=3)
        error<-.omics_errors[[as.character(token)]]
        if(!is.null(error)){saveRDS(error,file.path(folder,'original-error.rds'),version=3);.omics_ld_calls[[token]]$error<<-list(class=as.list(class(error)),message=conditionMessage(error))}
        else {saveRDS(value,file.path(folder,'original-return.rds'),version=3);.omics_ld_calls[[token]]$complete<<-TRUE;.omics_ld_calls[[token]]$return_class<<-as.list(class(value))}
        if(exists('status',frame,inherits=FALSE)).omics_ld_calls[[token]]$plink_exit_status<<-get('status',frame,inherits=FALSE)
    })
}
.omics_source_error <- function(error) {
    if(.omics_observing)return(invisible(NULL))
    for(token in seq_along(.omics_frames))if(!isTRUE(.omics_ld_calls[[token]]$complete)&&any(vapply(sys.frames(),function(frame)identical(frame,.omics_frames[[token]]),TRUE))).omics_errors[[as.character(token)]]<<-error
}
.omics_guard_ld <- function() {
    code<-parse('../source/clump-reference/ieugwasr-ld_clump.R',keep.source=FALSE)
    for(e in code)if(is.call(e)&&length(e)==3L&&identical(e[[1]],as.name('<-'))&&is.symbol(e[[2]])&&is.call(e[[3]])&&identical(e[[3]][[1]],as.name('function'))) {
        name<-as.character(e[[2]]);original<-get(name,.omics_ld_ns,inherits=FALSE);reference<-eval(e[[3]],envir=.omics_ld_ns)
        if(!identical(body(original),body(reference))||!identical(formals(original),formals(reference)))stop(paste('Original ieugwasr source differs:',name))
        .omics_ld_guards[[name]]<<-list(body=body(original),formals=formals(original))
    }
    expected<-c('ld_clump','ld_clump_api','ld_clump_local','warn_tied_pval','plink_resource_flags','random_string','ld_reflookup')
    if(length(.omics_ld_guards)!=7L||!setequal(names(.omics_ld_guards),expected))stop('Unexpected ieugwasr local-clumping source-function population')
}
.omics_install <- function()for(name in c('ld_clump','ld_clump_local')) {
    original<-get(name,.omics_ld_ns,inherits=FALSE);observed<-original
    prefix<-bquote({
        .omics_clump_token<-base::get('.omics_enter',envir=.GlobalEnv)(.(name),environment())
        on.exit({.omics_return_value<-base::returnValue(default=NULL);base::get('.omics_leave',envir=.GlobalEnv)(.omics_clump_token,environment(),.omics_return_value)},add=TRUE)
        if(.(name=='ld_clump_local'))unlink<-base::get('.omics_unlink',envir=.GlobalEnv)
        .(body(original))
    })
    body(observed)<-prefix
    if(!identical(body(observed)[[5]],body(original))||!identical(formals(observed),formals(original))||!identical(environment(observed),environment(original)))stop('Observation-prefix AST inverse/formals/environment differs')
    .omics_original[[name]]<<-original;.omics_prefixed[[name]]<<-observed
    unlockBinding(name,.omics_ld_ns);assign(name,observed,.omics_ld_ns);lockBinding(name,.omics_ld_ns)
}
.omics_restore <- function() {
    for(name in names(.omics_original)) {
        current<-get(name,.omics_ld_ns,inherits=FALSE)
        if((!identical(body(current),body(.omics_prefixed[[name]]))&&!identical(body(current),body(.omics_original[[name]])))||!identical(formals(current),formals(.omics_original[[name]]))).omics_observer_failures[[length(.omics_observer_failures)+1L]]<<-list(stage='restore',message=paste('Observed binding changed:',name))
        unlockBinding(name,.omics_ld_ns);assign(name,.omics_original[[name]],.omics_ld_ns);lockBinding(name,.omics_ld_ns)
    }
    for(name in names(.omics_ld_guards)) {
        current<-get(name,.omics_ld_ns,inherits=FALSE);reference<-.omics_ld_guards[[name]]
        if(!identical(body(current),reference$body)||!identical(formals(current),reference$formals))stop(paste('Original ieugwasr binding not restored:',name))
    }
    .omics_bindings_restored<<-TRUE
}
main <- function() {
    .libPaths(c(readLines('runtime-paths.txt',encoding='UTF-8',warn=FALSE),.Library),include.site=FALSE)
    if(as.character(getRversion())!='4.6.1')unavailable('Exact R4.6.1 is required')
    if(.Platform$OS.type!='windows'||.Machine$sizeof.pointer!=8L)unavailable('This exact original profile is qualified only on 64-bit Windows R4.6.1; qualify a separate host profile before execution')
    if(!requireNamespace('jsonlite',quietly=TRUE))unavailable('jsonlite is unavailable in the declared libraries')
    json.ready<<-TRUE;r<-jsonlite::fromJSON('../effective-request.json',simplifyVector=FALSE)
    if(!requireNamespace('TwoSampleMR',quietly=TRUE)||!requireNamespace('ieugwasr',quietly=TRUE))unavailable('Exact TwoSampleMR/ieugwasr namespaces are unavailable')
    desc<-utils::packageDescription('TwoSampleMR');ld.desc<-utils::packageDescription('ieugwasr')
    if(desc$Version!='0.6.8')unavailable('Supply exact tagged TwoSampleMR0.6.8 806703f0; adjacent editions are not substituted')
    if(!requireNamespace('digest',quietly=TRUE))unavailable('digest is required for exact original tagged function body/formal hashes')
    if(ld.desc$Version!='1.2.1'||is.null(ld.desc$RemoteSha)||ld.desc$RemoteSha!='ce5d0c07c40f1f9cdafff40bb6a644a3913c9ad6')unavailable('Supply exact ieugwasr1.2.1 ce5d0c07')
    ns<<-asNamespace('TwoSampleMR');.omics_ld_ns<<-asNamespace('ieugwasr');source.guard();.omics_guard_ld()
    .omics_tmp<<-normalizePath(tempdir(),winslash='/',mustWork=TRUE)
    owned<-normalizePath('owned-tmp',winslash='/',mustWork=TRUE)
    if(!startsWith(.omics_tmp,paste0(owned,'/')))stop('R temporary directory must be inside worker/owned-tmp')
    saveRDS(list(R=R.version,session=sessionInfo(),TwoSampleMR=desc,ieugwasr=ld.desc,external=extSoftVersion()),'runtime-provenance.rds',version=3)
    capture.output(sessionInfo(),file='session.txt')
    json(list(R=as.character(getRversion()),TwoSampleMR=desc$Version,TwoSampleMR_commit=reference$commit,TwoSampleMR_commit_binding='Tagged source archive plus all149 original function hashes; not installed RemoteSha or proven paper execution commit',dependency_era_reproduced=FALSE,ieugwasr=ld.desc$Version,ieugwasr_commit=ld.desc$RemoteSha,TwoSampleMR_source_functions_guarded=length(guards),ieugwasr_source_functions_guarded=length(.omics_ld_guards),namespace_observation='Two ieugwasr binding prefixes; original statement AST inverse, formals and namespace environment checked; bindings restored after call; TwoSampleMR namespace not rewritten'),'runtime-profile.json')
    RNGkind('Mersenne-Twister','Inversion','Rejection');set.seed(r$seed);dir.create('objects');dir.create('ld-calls');ready<<-TRUE
    data<-input('../inputs/instruments');binding<-jsonlite::fromJSON('plink-binding.json',simplifyVector=FALSE)
    value<-tryCatch({.omics_install();invoke('clump_data',ns$clump_data,list(dat=data,clump_kb=r$clump_kb,clump_r2=r$clump_r2,clump_p1=r$clump_p1,pop=r$pop,bfile=normalizePath('../inputs/reference',winslash='/',mustWork=FALSE),plink_bin=binding$executable))},finally=.omics_restore())
    table.out(value,'clumped')
    for(token in seq_along(.omics_ld_calls)) {
        call<-.omics_ld_calls[[token]];folder<-call$folder;args<-readRDS(file.path(folder,'arguments.rds'));locals<-readRDS(file.path(folder,'locals.rds'));returned<-readRDS(file.path(folder,'original-return.rds'))
        input.path<-file.path(folder,'input');table.out(args$dat,input.path);.omics_ld_calls[[token]]$input_export<<-paste0(input.path,'.tsv')
        .omics_ld_calls[[token]]$original_return_rows<<-nrow(returned);.omics_ld_calls[[token]]$original_return_columns<<-as.list(names(returned))
        if(ncol(returned)>0L){return.path<-file.path(folder,'returned');table.out(returned,return.path);.omics_ld_calls[[token]]$return_export<<-paste0(return.path,'.tsv')}
        .omics_ld_calls[[token]]$scientific_options<<-args[intersect(names(args),c('clump_kb','clump_r2','clump_p'))]
        if(call$name=='ld_clump_local') {
            .omics_ld_calls[[token]]$group_id<<-as.character(unique(args$dat$id));.omics_ld_calls[[token]]$plink_command<<-locals$fun2;.omics_ld_calls[[token]]$plink_resource_flags<<-locals$resources
            parsed<-locals[['res',exact=TRUE]]
            .omics_ld_calls[[token]]$source_parsed_index_keys<<-as.list(if(is.null(parsed))character() else as.character(parsed[['SNP']]))
            .omics_ld_calls[[token]]$source_parsed_index_type<<-if(is.null(parsed))'no-clumped-file' else typeof(parsed[['SNP']])
        }
    }
    batch<-Filter(function(x)x$name=='ld_clump',.omics_ld_calls)
    if(length(batch)!=1L)stop('Expected one original ieugwasr grouped call')
    args<-readRDS(file.path(batch[[1]]$folder,'arguments.rds'));effective<-args$dat
    if(nrow(effective)!=nrow(data)||!identical(as.character(effective$rsid),as.character(data$SNP)))stop('Original grouped input row lineage differs')
    kept<-seq_len(nrow(data))%in%as.integer(rownames(value));sizes<-table(effective$id)
    ledger<-data.frame(input_row=seq_len(nrow(data)),SNP=as.character(data$SNP),source_group=as.character(effective$id),source_pval=effective$pval,source_group_rows=as.integer(sizes[as.character(effective$id)]),kept=kept,stringsAsFactors=FALSE)
    ledger$source_rule<-ifelse(ledger$source_group_rows==1,'SOURCE_SINGLE_VARIANT_BYPASS',ifelse(kept,'SOURCE_RETURNED','SOURCE_NOT_RETURNED'))
    table.out(ledger,'decisions');report$selection_complete<<-TRUE;report$no_statistical_estimate_produced<<-TRUE
    report$population<<-list(input_rows=nrow(data),retained_rows=nrow(value),excluded_rows=nrow(data)-nrow(value),groups=length(sizes),single_variant_groups=sum(sizes==1))
    if(any(sizes==1))warnings.seen<<-c(warnings.seen,'SOURCE_SINGLE_VARIANT_BYPASS: original ieugwasr retains a single-row group without calling PLINK, even if absent from the reference or above clump_p1; no LD/reference/significance claim follows.')
    if(!nrow(value))warnings.seen<<-c(warnings.seen,'COMPLETE_EMPTY_SELECTION: original clumping returned zero variants; no downstream MR estimate is available. Preserve this filtering outcome; do not substitute instruments.')
    status<-vapply(Filter(function(x)x$name=='ld_clump_local',.omics_ld_calls),function(x)if(is.null(x$plink_exit_status))NA_integer_ else as.integer(x$plink_exit_status),0L)
    if(anyNA(status)||any(status!=0L))stop('Original PLINK process failure; full original return and available artifacts retained, no selection admitted')
    if(length(.omics_observer_failures))stop('Observation/capture failed; retain original returns and partials, no selection admitted')
}
code<-0L;failure<-NULL
tryCatch(withCallingHandlers(main(),warning=function(w){warnings.seen<<-c(warnings.seen,conditionMessage(w));invokeRestart('muffleWarning')},message=function(m){messages.seen<<-c(messages.seen,conditionMessage(m));invokeRestart('muffleMessage')}),error=function(error){code<<-if(inherits(error,'omics_unavailable'))3L else 2L;failure<<-error;saveRDS(error,'worker-error.rds',version=3)})
if(ready)tryCatch({guard.after();for(name in names(.omics_ld_guards)){current<-get(name,.omics_ld_ns,inherits=FALSE);reference<-.omics_ld_guards[[name]];if(!identical(body(current),reference$body)||!identical(formals(current),reference$formals))stop('Original ieugwasr binding differs after cleanup')}},error=function(error){code<<-2L;failure<<-error;saveRDS(error,'guard-after-error.rds',version=3)})
status<-if(code==0L)'completed-with-warnings' else if(code==3L)'SKIP3_DEPENDENCY_UNAVAILABLE' else 'FAIL2_NO_COMPLETE_RESULT'
report$status<-status;report$results_admitted<-code==0L;report$required_action<-action;report$calls<-calls;report$tables<-tables;report$ld_calls<-.omics_ld_calls;report$observation_failures<-.omics_observer_failures
report$TwoSampleMR_namespace_rewritten<-FALSE;report$TwoSampleMR_source_functions_guarded<-length(guards);report$ieugwasr_source_functions_guarded<-length(.omics_ld_guards);report$ieugwasr_bindings_restored<-.omics_bindings_restored
if(!is.null(failure))report$error<-conditionMessage(failure)
saveRDS(list(report=report,warnings=warnings.seen,messages=messages.seen,guards=guards,ieugwasr_guards=.omics_ld_guards),'observer-final.rds',version=3)
write.utf8(c(action,'',warnings.seen),'warnings.txt');write.utf8(messages.seen,'messages.txt');if(json.ready)json(report,'report.json')
detail<-if(is.null(failure))'Original selection retained; inspect source defaults, bypasses and complete-empty-selection warnings' else gsub('[\t\r\n]',' ',conditionMessage(failure))
write.utf8(c('status\tdetail\trequired_action',paste(status,detail,action,sep='\t')),'worker-status.tsv')
quit(save='no',status=code,runLast=FALSE)
