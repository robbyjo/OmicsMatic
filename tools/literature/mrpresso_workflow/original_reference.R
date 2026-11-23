# Original public-API reference generator. No MR-PRESSO function is patched.
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args<-commandArgs(TRUE);stopifnot(length(args)==3L)
.libPaths(c(args[1],.Library));out<-args[2];sourcefile<-args[3]
stopifnot(dir.exists(out),length(list.files(out,all.files=TRUE,no..=TRUE))==0L)
reference<-new.env(parent=baseenv());sys.source(sourcefile,envir=reference,keep.source=FALSE)
stopifnot(identical(deparse(body(MRPRESSO::mr_presso)),deparse(body(reference$mr_presso))))
stopifnot(identical(formals(MRPRESSO::mr_presso),formals(reference$mr_presso)))

# A small typed JSON writer avoids an extra package and preserves original
# storage classes, data-frame row labels, NA/NaN/Inf and character P-value bounds.
js<-function(s){
  v<-utf8ToInt(enc2utf8(s));paste0('"',paste(vapply(v,function(k){
    if(k==34L)return('\\"');if(k==92L)return('\\\\')
    if(k<32L)return(sprintf('\\u%04x',k));intToUtf8(k)
  },character(1)),collapse=''),'"')
}
scalar<-function(x){
  if(is.character(x)){if(is.na(x))return('{"special":"NA_character_"}');return(js(x))}
  if(is.nan(x))return('{"special":"NaN"}')
  if(is.na(x))return(paste0('{"special":',js(paste0('NA_',typeof(x),'_')),'}'))
  if(is.infinite(x))return(paste0('{"special":',js(if(x>0)'Inf' else '-Inf'),'}'))
  if(is.logical(x))return(if(x)'true' else 'false')
  sprintf('%.17g',x)
}
array<-function(values)paste0('[',paste(values,collapse=','),']')
encode<-function(x){
  if(is.null(x))return('null')
  if(is.data.frame(x)){
    columns<-vapply(seq_along(x),function(i)paste0('{"name":',js(names(x)[i]),',"data":',encode(x[[i]]),'}'),character(1))
    return(paste0('{"kind":"data.frame","row_names":',array(vapply(row.names(x),js,character(1))),',"columns":',array(columns),'}'))
  }
  if(is.list(x))return(paste0('{"kind":"list","names":',if(is.null(names(x)))'null' else array(vapply(names(x),js,character(1))),',"values":',array(vapply(x,encode,character(1))),'}'))
  stopifnot(is.atomic(x),!is.factor(x))
  paste0('{"kind":',js(typeof(x)),',"values":',array(vapply(seq_along(x),function(i)scalar(x[i]),character(1))),
         ',"names":',if(is.null(names(x)))'null' else array(vapply(names(x),js,character(1))),
         ',"dim":',if(is.null(dim(x)))'null' else array(as.character(dim(x))),'}')
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
add<-function(name,data=base(),outlier=TRUE,distortion=TRUE,B=63L,alpha=.05,seed=19L,exposures=grep('^X[0-9]+$',names(data),value=TRUE)){
  stopifnot(!name%in%names(cases));cases[[name]]<<-list(data=data,arguments=list(BetaOutcome='Y',BetaExposure=exposures,
    SdOutcome='SY',SdExposure=sub('^X','SX',exposures),OUTLIERtest=outlier,DISTORTIONtest=distortion,
    SignifThreshold=alpha,NbDistribution=B,seed=seed))
}
clean<-base();strong<-clean;strong$Y[c(3,10)]<-strong$Y[c(3,10)]+c(1.1,-.7)
for(o in c(FALSE,TRUE))for(d in c(FALSE,TRUE))add(paste0('clean-',as.integer(o),as.integer(d)),clean,o,d)
for(o in c(FALSE,TRUE))for(d in c(FALSE,TRUE))add(paste0('outlier-',as.integer(o),as.integer(d)),strong,o,d)
add('outlier-stable-precision',strong,B=257L)
add('original-default-B',strong,B=1000L)
add('alpha-zero',strong,alpha=0)
add('all-outliers-alpha-one',strong,alpha=1)
missing<-strong;missing$Y[2]<-NA_real_;missing$SX1[7]<-NA_real_;add('complete-case-labels',missing)
flipped<-strong;flipped[seq(2,12,2),c('Y','X1')]<- -flipped[seq(2,12,2),c('Y','X1')];add('sign-folding',flipped)
zero<-strong;zero$X1[2]<-0;add('zero-first-exposure',zero)
zero<-strong;zero$SX1<-0;add('zero-exposure-standard-error',zero)
bad<-strong;bad$SX1[2]<- -.03;add('negative-exposure-standard-error',bad)
bad<-strong;bad$SY[2]<-0;add('zero-outcome-standard-error',bad)
add('insufficient-instruments',base(3))
add('insufficient-distributions',strong,B=12L)
multi<-base(k=2);multi$Y[c(3,10)]<-multi$Y[c(3,10)]+c(1.1,-.7)
add('multivariable-two',multi,B=127L)
add('multivariable-two-even-B',multi,B=128L)
add('multivariable-reversed-exposures',multi,B=127L,exposures=c('X2','X1'))
multi3<-base(n=15,k=3);multi3$Y[c(3,10)]<-multi3$Y[c(3,10)]+c(1.1,-.7)
add('multivariable-three',multi3,B=127L)
aliased<-multi;aliased$X2<-aliased$X1;add('aliased-exposures',aliased)

progress<-list()
for(name in names(cases)){
  target<-file.path(out,name);dir.create(target);item<-cases[[name]]
  RNGkind('Mersenne-Twister','Inversion','Rejection');set.seed(731L)
  before<-.Random.seed;warnings<-list();error<-NULL
  value<-withCallingHandlers(tryCatch(do.call(MRPRESSO::mr_presso,c(item$arguments,list(data=item$data))),error=function(e){error<<-conditionMessage(e);NULL}),
    warning=function(w){warnings[[length(warnings)+1L]]<<-list(message=conditionMessage(w),classes=class(w));invokeRestart('muffleWarning')})
  result<-list(case=name,input=item$data,arguments=item$arguments,returned=is.null(error),result=value,warnings=warnings,error=error,
    rng_kind=RNGkind(),rng_before=before,rng_after=.Random.seed,original_function_patched=FALSE)
  saveRDS(result,file.path(target,'original.rds'),version=3,compress=FALSE);writejson(result,file.path(target,'original.json'))
  progress[[length(progress)+1L]]<-list(case=name,returned=is.null(error),warning_count=length(warnings),error=error)
  writejson(progress,file.path(out,'PROGRESS.json'));cat(name,if(is.null(error))'RETURNED' else 'ORIGINAL_ERROR','\n');flush.console()
}
writejson(list(status='ORIGINAL_PUBLIC_API_REFERENCE_COMPLETE',cases=progress,original_function_patched=FALSE),file.path(out,'RESULT.json'))
