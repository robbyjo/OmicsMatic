# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Closed calls to an unchanged licensed original sva namespace.
.libPaths(c(readLines('runtime-paths.txt',encoding='UTF-8',warn=FALSE),.Library),include.site=FALSE)
warnings.seen<-messages.seen<-character();objects<-list();guards<-list();runtime.ready<-FALSE
report<-list(status='RUNNING_NO_RESULT_ADMITTED',results_admitted=FALSE)
action<-'SKIP3: dependent analysis skipped; restore the named exact permitted runtime/package/input or retain the skip. FAIL2: no complete result; preserve inputs, original RDS, console, warnings and partials, correct the named cause and retry NEW output. No data, ID, count, nonfinite, version or method repair.'
unavailable<-function(message)stop(structure(list(message=message,call=NULL),class=c('omics_unavailable','error','condition')))
write.utf8<-function(lines,path){con<-file(path,'wb');on.exit(close(con));if(length(lines))writeBin(charToRaw(enc2utf8(paste0(paste(lines,collapse='\n'),'\n'))),con)}
number<-function(x)vapply(x,function(v)if(is.nan(v))'NaN' else if(is.na(v))'NA' else if(is.infinite(v))if(v>0)'Inf' else '-Inf' else sprintf('%.17g',v),'')
matrix.in<-function(path){columns<-length(strsplit(readLines(path,n=1,warn=FALSE),'\t',fixed=TRUE)[[1]])-1L;x<-read.delim(path,row.names=1,check.names=FALSE,quote='',comment.char='',na.strings=character(),colClasses=c('character',rep('numeric',columns)),encoding='UTF-8');as.matrix(x)}
emit.json<-function(value,path)jsonlite::write_json(value,path,auto_unbox=TRUE,pretty=TRUE,null='null',na='string',digits=NA)
fingerprint<-function(packages){paths<-sort(unique(unlist(lapply(packages,function(pkg)list.files(find.package(pkg),recursive=TRUE,full.names=TRUE,all.files=TRUE,no..=TRUE)))));values<-tools::md5sum(paths);as.list(setNames(unname(values),names(values)))}
guard.source<-function(names){
 definitions<-list();ns<-asNamespace('sva')
 for(path in sort(list.files('upstream-source/sva/R',pattern='[.]R$',full.names=TRUE)))for(e in parse(path,keep.source=FALSE)) {
  if(is.call(e)&&length(e)==3L&&as.character(e[[1]])%in%c('<-','=')&&is.symbol(e[[2]]))definitions[[as.character(e[[2]])]]<-e[[3]]
 }
 for(name in names){e<-definitions[[name]];if(is.null(e)||!is.call(e)||!identical(e[[1]],as.name('function')))stop(paste('Original function source absent:',name));ref<-eval(e,ns);fn<-get(name,ns,inherits=FALSE)
  if(!identical(body(fn),body(ref))||!identical(formals(fn),formals(ref)))stop(paste('Original source body/formals differ:',name))
  guards[[name]]<<-list(body=body(fn),formals=formals(fn))
 }
}
guard.after<-function(){for(name in names(guards)){fn<-get(name,asNamespace('sva'),inherits=FALSE);if(!identical(body(fn),guards[[name]]$body)||!identical(formals(fn),guards[[name]]$formals))stop(paste('Original namespace changed:',name))}}
shape<-function(x)list(class=unname(as.list(class(x))),typeof=typeof(x),length=length(x),dim=if(is.null(dim(x)))NULL else as.list(unname(dim(x))),names=if(is.null(names(x)))NULL else as.list(names(x)),dimnames=if(is.null(dimnames(x)))NULL else lapply(dimnames(x),function(y)if(is.null(y))NULL else as.list(y)))
main<-function(){
 if(!requireNamespace('jsonlite',quietly=TRUE))unavailable('jsonlite is unavailable; install the exact declared private runtime')
 profile<-jsonlite::read_json('../source/SOURCE-PROFILE.json',simplifyVector=TRUE)
 if(as.character(getRversion())!=profile$r_version||R.version$platform!=profile$platform)unavailable(paste('Unqualified R version/platform:',getRversion(),R.version$platform))
 for(pkg in names(profile$packages))if(!requireNamespace(pkg,quietly=TRUE)||as.character(utils::packageVersion(pkg))!=profile$packages[[pkg]])unavailable(paste('Missing or unqualified package:',pkg,'require',profile$packages[[pkg]]))
 for(relative in names(profile$qualified_package_files)){
  parts<-strsplit(relative,'/',fixed=TRUE)[[1]];path<-file.path(find.package(parts[1]),paste(parts[-1],collapse='/'));expected<-profile$qualified_package_files[[relative]]
  if(!file.exists(path)||file.info(path)$size!=expected$bytes||unname(tools::md5sum(path))!=expected$md5)stop(paste('Qualified original package file differs:',relative,'restore exact private runtime; NO COMPLETE RESULT'))
 }
 runtime.ready<<-TRUE
 p<-jsonlite::read_json('parameters.json',simplifyVector=TRUE);op<-p$operation
 dir.create('upstream-source');utils::untar('../inputs/sva_source',exdir='upstream-source',tar='internal')
 guard.source(profile$functions)
 package.names<-sort(loadedNamespaces());before<-fingerprint(package.names);emit.json(before,'package-files-before.json')
 report$runtime<<-list(R=R.version.string,platform=R.version$platform,packages=setNames(lapply(package.names,function(x)as.character(utils::packageVersion(x))),package.names),body_formals_guard=profile$functions,source_archive=profile$sva_source)
 dat<-matrix.in('matrix.tsv');mod<-matrix.in('design.tsv');mod0<-if(p$has_null_design)matrix.in('null_design.tsv')else NULL
 if(qr(mod)$rank!=ncol(mod))stop('Full design is rank deficient; no design repair')
 if(!is.null(mod0)&&(qr(mod0)$rank!=ncol(mod0)||ncol(mod0)>ncol(mod)||qr(cbind(mod,mod0))$rank!=ncol(mod)))stop('Null design must be full rank and nested in full design; no projection or repair')
 controls<-if(p$has_controls)as.vector(matrix.in('controls.tsv'))else NULL
 objects$inputs<<-list(dat=dat,mod=mod,mod0=mod0,controls=controls)
 RNGkind('Mersenne-Twister','Inversion','Rejection');set.seed(p$seed);objects$rng_before<<-list(kind=RNGkind(),seed=.Random.seed)
 selection<-seq_len(nrow(dat));for.selection<-if(op=='SVASEQ')log(dat+p$constant)else dat
 if(!is.null(p$vfilter)){ranks<-rank(-matrixStats::rowVars(for.selection));selection<-which(if(op=='SVA')ranks<=p$vfilter else ranks<p$vfilter)}
 write.utf8(c('source_index\tfeature_id',paste(selection,rownames(dat)[selection],sep='\t')),'../results/selection.tsv')
 if(op=='NUM_SV'){
  args<-list(dat=dat,mod=mod,method=p$num_sv_method);if(!is.null(p$permutations))args$B<-p$permutations;if(!is.null(p$vfilter))args$vfilter<-p$vfilter
  value<-do.call(sva::num.sv,args);components<-list(n.sv=value)
 }else {
  args<-list(dat=dat,mod=mod,mod0=mod0,n.sv=if(identical(p$n_sv,'auto'))NULL else p$n_sv,controls=controls,method=p$method)
  if(!is.null(p$iterations))args$B<-p$iterations;if(!is.null(p$vfilter))args$vfilter<-p$vfilter;if(!is.null(p$num_sv_method))args$numSVmethod<-p$num_sv_method
  if(op=='SVASEQ')args$constant<-p$constant
  value<-do.call(if(op=='SVA')sva::sva else sva::svaseq,args);components<-value
 }
 objects$call_arguments<<-args;objects$result<<-value;objects$rng_after<<-list(kind=RNGkind(),seed=.Random.seed)
 saveRDS(objects,'objects.rds',version=3,compress=FALSE)
 guard.after();after<-fingerprint(package.names);emit.json(after,'package-files-after.json');if(!identical(before,after))stop('Original package files changed during execution')
 shapes<-lapply(components,shape);emit.json(shapes,'../results/shapes.json')
 lines<-c('component\tindex\tvalue');for(name in names(components)){x<-components[[name]];values<-if(is.logical(x))as.character(as.vector(x))else number(as.vector(x));if(length(x))lines<-c(lines,paste(name,seq_along(x),values,sep='\t'))}
 write.utf8(lines,'../results/components.tsv')
 for(name in names(components))if(anyNA(components[[name]])||is.numeric(components[[name]])&&any(!is.finite(components[[name]])))stop(paste('Original undefined component retained:',name,'NO COMPLETE RESULT'))
 report$operation<<-op;report$n_sv<<-if(op=='NUM_SV')as.numeric(value)else as.numeric(value$n.sv)
 report$method_requested<<-p$method;report$method_effective<<-if(p$has_controls)'supervised'else p$method
 report$source_rows<<-nrow(dat);report$selected_rows<<-length(selection);report$factor_count_scope<<-if(op!='NUM_SV'&&identical(p$n_sv,'auto'))'Original wrapper passes vfilter again to num.sv; BE uses original B=20'else 'Explicit declared operation and count'
 report$rng<<-list(kind=as.list(RNGkind()),project_seed=p$seed,author_seed_inferred=FALSE)
 report$namespace_unchanged<<-TRUE;report$package_files_unchanged<<-TRUE;report$qualified_package_files_checked<<-length(profile$qualified_package_files)
 report$status<<-'completed-with-warnings';report$results_admitted<<-TRUE
}
code<-tryCatch(withCallingHandlers({main();0L},warning=function(w){warnings.seen<<-c(warnings.seen,conditionMessage(w));invokeRestart('muffleWarning')},message=function(m){messages.seen<<-c(messages.seen,conditionMessage(m));invokeRestart('muffleMessage')}),omics_unavailable=function(e){report$error<<-conditionMessage(e);3L},error=function(e){report$error<<-conditionMessage(e);2L})
if(code!=0L){report$status<-if(code==3L)'SKIP3_DEPENDENCY_UNAVAILABLE'else 'FAIL2_NO_COMPLETE_RESULT';report$results_admitted<-FALSE}
saveRDS(objects,'objects.rds',version=3,compress=FALSE)
report$required_action<-action;report$warnings<-as.list(warnings.seen);report$messages<-as.list(messages.seen)
write.utf8(c('DEVELOPMENT_SCOPE: chosen original SVA3.46.0 workflow; no inferred author recipe.',action,'Original warnings:',warnings.seen,'Original messages:',messages.seen,'Console messages are retained separately in stdout.log.','IRW fixed iterations do not assert numerical convergence; original nonfinite output is never admitted.'),'warnings.txt')
if(requireNamespace('jsonlite',quietly=TRUE))emit.json(report,'report.json')
clean<-function(x)gsub('[\t\r\n]',' ',x)
write.utf8(c('status\tdetail\trequired_action',paste(report$status,clean(if(is.null(report$error))'Original function completed; inspect warnings and scope'else report$error),clean(action),sep='\t')),'worker-status.tsv')
quit(save='no',status=code,runLast=FALSE)
