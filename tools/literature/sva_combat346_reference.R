# Own synthetic controls for the unchanged upstream sva 3.46.0 ComBat functions.
# No original article text or upstream implementation is redistributed here.
args <- commandArgs(TRUE)
stopifnot(length(args)==3,!dir.exists(args[3]))
source_root <- normalizePath(args[1],mustWork=TRUE)
libraries <- readLines(args[2],warn=FALSE)
stopifnot(all(dir.exists(libraries)))
.libPaths(c(libraries,.libPaths()))
suppressPackageStartupMessages(library(BiocParallel))
suppressPackageStartupMessages(library(matrixStats))
stopifnot(read.dcf(file.path(source_root,"DESCRIPTION"))[1,"Version"]=="3.46.0")
dir.create(args[3]); output <- normalizePath(args[3],mustWork=TRUE)
options(digits=17)
writeLines(capture.output(sessionInfo()),file.path(output,"session.txt"))
e <- new.env(parent=globalenv())
sys.source(file.path(source_root,"R/helper.R"),envir=e)
sys.source(file.path(source_root,"R/ComBat.R"),envir=e)
original <- e$ComBat
observed <- original
capture <- new.env(parent=emptyenv())
e$.omicsmatic_capture_346 <- function(frame) {
  for(name in c("gamma.star","delta.star","var.pooled","stand.mean","keep.rows","zero.rows","mean.only"))
    if(exists(name,envir=frame,inherits=FALSE))assign(name,get(name,envir=frame,inherits=FALSE),envir=capture)
}
prefix <- quote(on.exit(.omicsmatic_capture_346(environment()),add=TRUE))
body(observed) <- as.call(list(as.name("{"),prefix,body(original)))
stopifnot(identical(body(observed)[[3]],body(original)),identical(formals(observed),formals(original)),identical(environment(observed),environment(original)))
writeLines(c("original statistical body is the exact third AST expression of the observer wrapper", "observer only reads existing locals on exit", "every admitted control must also have bitwise-identical stock and observed return matrices"),file.path(output,"observer-contract.txt"))
write_matrix <- function(x,path,id="feature_id") {
  stopifnot(is.matrix(x),!is.null(rownames(x)),!is.null(colnames(x)))
  literal <- matrix(ifelse(is.nan(x),"NaN",ifelse(is.na(x),"NA",sprintf("%.17g",x))),nrow(x),ncol(x),dimnames=dimnames(x))
  z <- data.frame(rownames(x),literal,check.names=FALSE);names(z)[1]<-id
  write.table(z,path,sep="\t",quote=FALSE,row.names=FALSE,na="NA")
}
seed <- 729401L;set.seed(seed)
n <- 24L;m <- 64L
batch <- rep(c("z","a","middle"),c(9,8,7))
phenotype <- rep(c(0,1),length.out=n)
mod <- cbind(intercept=1,phenotype=phenotype)
dat <- matrix(rnorm(m*n),m,n)
for(f in seq_len(m))dat[f,] <- (0.5+f/80)*dat[f,] + (f%%7-3)*phenotype/3 + c(-3,1.5,4)[match(batch,c("z","a","middle"))]*(0.3+f/50)
rownames(dat)<-paste0("g",seq_len(m));colnames(dat)<-paste0("s",seq_len(n));rownames(mod)<-colnames(dat)
missing <- dat;missing[2,3]<-NA_real_;missing[4,14]<-NA_real_
zeros <- dat;zeros[1,batch=="z"]<-0;zeros[2,batch=="middle"]<-8;zeros[3,batch=="a"]<-0.1;zeros[4,batch=="z"]<-seq_len(sum(batch=="z"))*1e-200
missing_constant <- dat;missing_constant[1,batch=="z"]<-2;missing_constant[1,1]<-NA_real_
one_observed <- missing;one_observed[1,2:9]<-NA_real_
two_observed <- missing;two_observed[1,3:9]<-NA_real_
singleton <- batch;singleton[1]<-"single"
underflow <- matrix(rnorm(12L*2400L),12L,2400L)
underflow[,1201:2400] <- underflow[,1201:2400]*1.05+1
rownames(underflow)<-paste0("g",seq_len(nrow(underflow)));colnames(underflow)<-paste0("s",seq_len(ncol(underflow)))
signed <- matrix(0,32,16)
for(f in seq_len(nrow(signed))) {
  a<-sin(seq_len(8)*(0.31+f/41));a<-(a-mean(a))/sd(a)
  b<-cos(seq_len(8)*(0.17+f/53));b<-(b-mean(b))/sd(b)
  signed[f,1:8] <- a*(1+5e-6*f/32)-4-f*.01
  signed[f,9:16] <- b*(1-3e-6*f/32)+4+f*.02
}
rownames(signed)<-paste0("g",seq_len(nrow(signed)));colnames(signed)<-paste0("s",seq_len(ncol(signed)))
cases <- list(
 PARAMETRIC=list(dat=dat,batch=batch,mod=mod),
 NO_COVARIATES=list(dat=dat,batch=batch),
 MEAN_ONLY=list(dat=dat,batch=batch,mod=mod,mean.only=TRUE),
 NONPARAMETRIC=list(dat=dat,batch=batch,mod=mod,par.prior=FALSE),
 NONPARAMETRIC_MEAN=list(dat=dat,batch=batch,mod=mod,par.prior=FALSE,mean.only=TRUE),
 REFERENCE=list(dat=dat,batch=batch,mod=mod,ref.batch="a"),
 NONPARAMETRIC_REFERENCE=list(dat=dat,batch=batch,mod=mod,par.prior=FALSE,ref.batch="middle"),
 MISSING=list(dat=missing,batch=batch,mod=mod),
 MISSING_REFERENCE=list(dat=missing,batch=batch,mod=mod,ref.batch="a"),
 MISSING_NONPARAMETRIC=list(dat=missing,batch=batch,mod=mod,par.prior=FALSE),
 ZERO_VARIANCE=list(dat=zeros,batch=batch,mod=mod),
 MISSING_CONSTANT=list(dat=missing_constant,batch=batch,mod=mod),
 SINGLETON=list(dat=dat,batch=singleton,mod=mod),
 SIGNED_STOPPING=list(dat=signed,batch=rep(c("z","a"),each=8)),
 TWO_OBSERVED_PARAMETRIC=list(dat=two_observed,batch=batch,mod=mod),
 TWO_OBSERVED_NONPARAMETRIC=list(dat=two_observed,batch=batch,mod=mod,par.prior=FALSE),
 MISSING_VARIANCE_PARAMETRIC=list(dat=one_observed,batch=batch,mod=mod),
 MISSING_VARIANCE_NONPARAMETRIC=list(dat=one_observed,batch=batch,mod=mod,par.prior=FALSE),
 NONPARAMETRIC_UNDERFLOW=list(dat=underflow,batch=rep(c("z","a"),each=1200L),par.prior=FALSE),
 CONFOUNDED=list(dat=dat,batch=batch,mod=cbind(intercept=1,confounded=as.numeric(batch=="z"))),
 ALL_CONSTANT=list(dat=dat*0+1,batch=batch),
 SINGLE_FEATURE=list(dat=dat[1,,drop=FALSE],batch=batch)
)
index <- list()
for(name in names(cases)) {
  d<-file.path(output,name);dir.create(d);r<-cases[[name]]
  original_batch <- r$batch;r$batch <- factor(r$batch,levels=unique(r$batch));r$prior.plots<-FALSE;r$BPPARAM<-SerialParam()
  write_matrix(r$dat,file.path(d,"matrix.tsv"))
  write.table(data.frame(sample_id=colnames(r$dat),batch=original_batch),file.path(d,"batch.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
  if(!is.null(r$mod)){rownames(r$mod)<-colnames(r$dat);write_matrix(r$mod,file.path(d,"design.tsv"),"sample_id")}
  settings<-c(parametric=if(is.null(r$par.prior))TRUE else r$par.prior,mean_only=if(is.null(r$mean.only))FALSE else r$mean.only,reference_batch=if(is.null(r$ref.batch))""else r$ref.batch)
  write.table(data.frame(key=names(settings),value=as.character(settings)),file.path(d,"settings.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
  conditions<-character();error<-NULL;stock<-NULL
  error_connection<-file(file.path(d,"stock.stderr.txt"),"wt")
  sink(file.path(d,"stock.stdout.txt"));sink(error_connection,type="message")
  stock <- tryCatch(withCallingHandlers(do.call(original,r),warning=function(w){conditions<<-c(conditions,conditionMessage(w))}),error=function(x){error<<-conditionMessage(x);NULL})
  sink(type="message");close(error_connection);sink()
  if(!is.null(error)){
    writeLines(error,file.path(d,"ORIGINAL_ERROR.txt"));index[[name]]<-data.frame(case=name,status="ORIGINAL_ERROR",features=nrow(r$dat),samples=ncol(r$dat),new_nonfinite=NA_integer_);next
  }
  rm(list=ls(capture),envir=capture)
  error_connection<-file(file.path(d,"observed.stderr.txt"),"wt")
  sink(file.path(d,"observed.stdout.txt"));sink(error_connection,type="message")
  observed_result<-do.call(observed,r);sink(type="message");close(error_connection);sink()
  stopifnot(identical(stock,observed_result))
  write_matrix(stock,file.path(d,"adjusted.tsv"))
  keep<-capture$keep.rows
  gamma<-delta<-matrix(NA_real_,nlevels(r$batch),nrow(r$dat),dimnames=list(levels(r$batch),rownames(r$dat)))
  gamma[,keep]<-capture$gamma.star;delta[,keep]<-capture$delta.star
  write_matrix(gamma,file.path(d,"gamma.tsv"),"batch");write_matrix(delta,file.path(d,"delta.tsv"),"batch")
  write.table(data.frame(feature_id=rownames(r$dat),adjusted=seq_len(nrow(r$dat))%in%keep),file.path(d,"feature-status.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
  saveRDS(list(inputs=r,stock=stock,observed=observed_result,locals=as.list(capture),settings=settings,conditions=conditions),file.path(d,"original.rds"),version=3)
  new_nonfinite<-sum(!is.finite(stock)&!is.na(r$dat))
  index[[name]]<-data.frame(case=name,status=if(new_nonfinite)"ORIGINAL_UNDEFINED_NO_ADMISSION"else"COMPLETE_FINITE_OBSERVED_VALUES",features=nrow(r$dat),samples=ncol(r$dat),new_nonfinite=new_nonfinite)
}
write.table(do.call(rbind,index),file.path(output,"cases.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
writeLines(paste0("source=sva3.46.0;seed=",seed,";observer-stock-bitwise-equality-required"),file.path(output,"COMPLETED.txt"))
