# Extended original public-API witnesses. The package function is never patched.
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args<-commandArgs(TRUE);stopifnot(length(args)==3L)
.libPaths(c(args[1],.Library));out<-args[2];sourcefile<-args[3]
stopifnot(dir.exists(out),length(list.files(out,all.files=TRUE,no..=TRUE))==0L)
reference<-new.env(parent=baseenv());sys.source(sourcefile,envir=reference,keep.source=FALSE)
stopifnot(identical(deparse(body(MRPRESSO::mr_presso)),deparse(body(reference$mr_presso))))
stopifnot(identical(formals(MRPRESSO::mr_presso),formals(reference$mr_presso)))
js<-function(s){v<-utf8ToInt(enc2utf8(s));paste0('"',paste(vapply(v,function(k){
 if(k==34L)return('\\"');if(k==92L)return('\\\\');if(k<32L)return(sprintf('\\u%04x',k));intToUtf8(k)
},character(1)),collapse=''),'"')}
scalar<-function(x){
 if(is.character(x)){if(is.na(x))return('{"special":"NA_character_"}');return(js(x))}
 if(is.nan(x))return('{"special":"NaN"}')
 if(is.na(x))return(paste0('{"special":',js(paste0('NA_',typeof(x),'_')),'}'))
 if(is.infinite(x))return(paste0('{"special":',js(if(x>0)'Inf' else '-Inf'),'}'))
 if(is.logical(x))return(if(x)'true' else 'false');sprintf('%.17g',x)
}
array<-function(x)paste0('[',paste(x,collapse=','),']')
encode<-function(x){
 if(is.null(x))return('null')
 if(is.data.frame(x)){
  columns<-vapply(seq_along(x),function(i)paste0('{"name":',js(names(x)[i]),',"data":',encode(x[[i]]),'}'),character(1))
  return(paste0('{"kind":"data.frame","row_names":',array(vapply(row.names(x),js,character(1))),',"columns":',array(columns),'}'))
 }
 if(is.list(x))return(paste0('{"kind":"list","names":',if(is.null(names(x)))'null' else array(vapply(names(x),js,character(1))),',"values":',array(vapply(x,encode,character(1))),'}'))
 stopifnot(is.atomic(x),!is.factor(x))
 paste0('{"kind":',js(typeof(x)),',"values":',array(vapply(seq_along(x),function(i)scalar(x[i]),character(1))),
  ',"names":',if(is.null(names(x)))'null' else array(vapply(names(x),js,character(1))),',"dim":',if(is.null(dim(x)))'null' else array(as.character(dim(x))),'}')
}
writejson<-function(x,path)writeLines(encode(x),path,useBytes=TRUE)
writeLines(capture.output(sessionInfo()),file.path(out,'SESSION.txt'))
writeLines(deparse(body(MRPRESSO::mr_presso)),file.path(out,'ORIGINAL-FUNCTION-BODY.txt'))
writeLines(c(R.version.string,find.package('MRPRESSO'),as.character(packageVersion('MRPRESSO'))),file.path(out,'RUNTIME.txt'))
base<-function(n=12L,k=1L){
 i<-seq_len(n);d<-data.frame(Y=rep(0,n),SY=.04+(i%%3)*.007)
 for(j in seq_len(k)){
  d[[paste0('X',j)]]<-.4+sin(i*(.71+j*.43))*.25+cos(i*(.17+j*.11))*.08
  d[[paste0('SX',j)]]<-.025+(i%%(j+2))*.004
  d$Y<-d$Y+d[[paste0('X',j)]]*(if(j%%2) .31/j else -.23/j)
 }
 d$Y<-d$Y+sin(i*1.73)*.013;row.names(d)<-sprintf('variant-%04d',i);d
}
cases<-list()
add<-function(name,data,outlier=TRUE,distortion=TRUE,B=257L,alpha=.05,seed=19L,sample='Rejection',omit=character(),advance=FALSE){
 exposures<-grep('^X[0-9]+$',names(data),value=TRUE)
 arguments<-list(BetaOutcome='Y',BetaExposure=exposures,SdOutcome='SY',SdExposure=sub('^X','SX',exposures),
  OUTLIERtest=outlier,DISTORTIONtest=distortion,SignifThreshold=alpha,NbDistribution=B,seed=seed)
 arguments[intersect(names(arguments),omit)]<-NULL
 stopifnot(!name%in%names(cases));cases[[name]]<<-list(data=data,arguments=arguments,sample=sample,advance=advance,omitted=omit)
}
clean<-base();strong<-clean;strong$Y[c(3,10)]<-strong$Y[c(3,10)]+c(1.1,-.7)
multi<-base(k=2);multi$Y[c(3,10)]<-multi$Y[c(3,10)]+c(1.1,-.7)
add('rounding-univ-257',strong,sample='Rounding')
add('rounding-mvmr-127',multi,B=127L,sample='Rounding')
add('rounding-mvmr-128',multi,B=128L,sample='Rounding')
add('continued-univ-state',strong,seed=NULL,advance=TRUE)
add('continued-mvmr-state',multi,B=127L,seed=NULL,advance=TRUE)
add('continued-rounding-state',multi,B=127L,seed=NULL,sample='Rounding',advance=TRUE)
defaults<-c('OUTLIERtest','DISTORTIONtest','SignifThreshold','NbDistribution','seed')
add('omitted-all-defaults-clean',clean,omit=defaults,advance=TRUE)
add('omitted-all-defaults-outlier',strong,omit=defaults,advance=TRUE)
add('omitted-B-with-tests',strong,omit='NbDistribution')
tiny<-strong;tiny$Y<-tiny$Y*1e-20;tiny$SY<-tiny$SY*1e-20;add('tiny-outcome-univ',tiny)
tiny<-multi;tiny$Y<-tiny$Y*1e-20;tiny$SY<-tiny$SY*1e-20;add('tiny-outcome-mvmr',tiny)
tiny<-strong;tiny$X1<-tiny$X1*1e-20;tiny$SX1<-tiny$SX1*1e-20;add('tiny-exposure-univ',tiny)
large<-strong;large$Y<-large$Y*1e20;large$SY<-large$SY*1e20;add('large-outcome-univ',large)
perfect<-clean;perfect$Y<-.31*perfect$X1;add('essentially-perfect-raw',perfect)
zero<-clean;zero$Y<-0;zero$Y[3]<-1.1
zero$Y[10]<- -1.1*(zero$X1[3]/zero$SY[3]^2)/(zero$X1[10]/zero$SY[10]^2)
add('zero-corrected-inference',zero)
balanced<-base(k=2);rows<-c(3L,7L,10L);a<-balanced$X1[rows]/balanced$SY[rows]^2;b<-balanced$X2[rows]/balanced$SY[rows]^2
delta<-c(a[2]*b[3]-a[3]*b[2],a[3]*b[1]-a[1]*b[3],a[1]*b[2]-a[2]*b[1]);delta<-delta/max(abs(delta))*1.1
balanced$Y[rows]<-balanced$Y[rows]+delta
add('balanced-mvmr-distortion-257',balanced)
add('balanced-mvmr-distortion-258',balanced,B=258L)
special<-multi;special$X1[2]<-0;special$SX2[5]<-NaN;special$Y[8]<-NA_real_;add('mvmr-zero-first-and-missing',special)
ids<-strong;row.names(ids)<-c('NA','NaN','0001',paste0('rs',4:11),intToUtf8(c(114L,115L,945L)));add('literal-unicode-identifiers',ids)
fixed<-multi;fixed$SX2<-0;add('mvmr-zero-one-exposure-sd',fixed)
progress<-list()
for(name in names(cases)){
 item<-cases[[name]];target<-file.path(out,name);stopifnot(dir.create(target))
 suppressWarnings(RNGkind('Mersenne-Twister','Inversion',item$sample));set.seed(731L)
 if(item$advance){invisible(rnorm(13));invisible(runif(7))}
 before<-.Random.seed;warnings<-list();error<-NULL
 value<-withCallingHandlers(tryCatch(do.call(MRPRESSO::mr_presso,c(item$arguments,list(data=item$data))),error=function(e){error<<-conditionMessage(e);NULL}),
  warning=function(w){warnings[[length(warnings)+1L]]<<-list(message=conditionMessage(w),classes=class(w));invokeRestart('muffleWarning')})
 result<-list(case=name,input=item$data,arguments=item$arguments,omitted_arguments=item$omitted,returned=is.null(error),result=value,warnings=warnings,error=error,
  rng_kind=RNGkind(),rng_before=before,rng_after=.Random.seed,original_function_patched=FALSE)
 saveRDS(result,file.path(target,'original.rds'),version=3,compress=FALSE);writejson(result,file.path(target,'original.json'))
 progress[[length(progress)+1L]]<-list(case=name,returned=is.null(error),warning_count=length(warnings),error=error)
 writejson(progress,file.path(out,'PROGRESS.json'));cat(name,if(is.null(error))'RETURNED' else 'ORIGINAL_ERROR','\n');flush.console()
}
writejson(list(status='EXTENDED_ORIGINAL_PUBLIC_API_REFERENCE_COMPLETE',cases=progress,original_function_patched=FALSE),file.path(out,'RESULT.json'))
