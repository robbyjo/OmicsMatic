args<-commandArgs(TRUE); stopifnot(length(args)==3L)
roots<-c(windows=normalizePath(args[1],winslash="/",mustWork=TRUE),linux=normalizePath(args[2],winslash="/",mustWork=TRUE)); out<-args[3]; stopifnot(!file.exists(out)); dir.create(out)
inputs<-file.path(roots[["windows"]],"inputs")
ph<-read.table(file.path(inputs,"phenotypes.tsv"),header=TRUE,stringsAsFactors=FALSE,check.names=FALSE)
cv<-read.table(file.path(inputs,"covariates.tsv"),header=TRUE,stringsAsFactors=FALSE,check.names=FALSE)
stopifnot(ph$Y[7]==-9, is.na(cv$AGE[11]))
nag<-function(m) (1-exp((deviance(m)-m$null.deviance)/nobs(m)))/(1-exp(-m$null.deviance/nobs(m)))
close6<-function(value,reference){b<-if(reference==0)0 else .5*10^(floor(log10(abs(reference)))-5);is.finite(value)&&is.finite(reference)&&abs(value-reference)<=b+5e-8*max(1,abs(reference))}
rows<-list(); index<-0L
for(platform in c("windows","linux"))for(kind in c("linear","binary","linear-standardized","binary-control-standardized")){
 folder<-file.path(roots[[platform]],kind,"worker"); scores<-read.table(file.path(folder,"original.all_score"),header=TRUE,stringsAsFactors=FALSE,check.names=FALSE)
 original<-read.delim(file.path(folder,"original.prsice"),header=TRUE,check.names=FALSE); binary<-startsWith(kind,"binary"); covariates<-kind!="binary-control-standardized"
 for(i in seq_len(nrow(original))){
  threshold<-original$Threshold[i]; column<-which(vapply(names(scores),function(n)startsWith(n,"Pt_")&&isTRUE(all.equal(as.numeric(sub("^Pt_","",n)),threshold,tolerance=1e-10)),logical(1)))
  stopifnot(length(column)==1L); d<-merge(merge(scores[,c(1,2,column)],ph,by=c("FID","IID"),sort=FALSE),cv,by=c("FID","IID"),sort=FALSE);names(d)[3]<-"SCORE"
  d$OUTCOME<-if(binary)ifelse(d$CASE==-9,NA_real_,d$CASE-1)else d$Y
  wanted<-c("OUTCOME","SCORE",if(covariates)c("AGE","BATCH"));d<-d[complete.cases(d[,wanted,drop=FALSE]),];d$BATCH<-factor(d$BATCH)
  f1<-if(covariates)OUTCOME~SCORE+AGE+BATCH else OUTCOME~SCORE; f0<-if(covariates)OUTCOME~AGE+BATCH else OUTCOME~1
  if(binary){m1<-glm(f1,data=d,family=binomial());m0<-glm(f0,data=d,family=binomial());stopifnot(m1$converged,m0$converged);co<-coef(summary(m1))["SCORE",]; b<-co[1];se<-co[2];sourcep<-pchisq((b/se)^2,df=1,lower.tail=FALSE);standardp<-co[4];full<-nag(m1);null<-nag(m0)}else{
    m1<-lm(f1,data=d);m0<-lm(f0,data=d);co<-coef(summary(m1))["SCORE",];b<-co[1];se<-co[2];sourcep<-2*pt(abs(b/se),df=nobs(m1),lower.tail=FALSE);standardp<-co[4];full<-summary(m1)$r.squared;null<-summary(m0)$r.squared
  }
  observed<-c(b,se,sourcep,full-null); reference<-unlist(original[i,c("Coefficient","Standard.Error","P","R2")],use.names=FALSE)
  ok<-mapply(close6,observed,reference); index<-index+1L;rows[[index]]<-data.frame(platform=platform,kind=kind,threshold=threshold,N=nobs(m1),rank=length(coef(m1)),source_beta=reference[1],R_beta=b,source_SE=reference[2],R_SE=se,source_P=reference[3],R_source_convention_P=sourcep,R_conventional_P=standardp,source_delta_R2=reference[4],R_delta_R2=full-null,R_full_R2=full,R_null_R2=null,R_summary_PRS_R2=1-(1-full)/(1-null),passed=all(ok))
 }
}
result<-do.call(rbind,rows); write.csv(result,file.path(out,"RESULTS.csv"),row.names=FALSE,quote=TRUE)
summary_checks<-0L
for(platform in c("windows","linux"))for(kind in c("linear","binary","linear-standardized","binary-control-standardized")){
  summary<-read.delim(file.path(roots[[platform]],kind,"worker","original.summary"),header=TRUE,check.names=FALSE)
  stopifnot(nrow(summary)==1L)
  chosen<-result[result$platform==platform & result$kind==kind & abs(result$threshold-summary$Threshold[1])<1e-12,]
  stopifnot(nrow(chosen)==1L)
  stopifnot(close6(chosen$R_full_R2,summary$Full.R2[1]),close6(chosen$R_null_R2,summary$Null.R2[1]),close6(chosen$R_summary_PRS_R2,summary$PRS.R2[1]))
  summary_checks<-summary_checks+1L
}
stopifnot(summary_checks==8L)
writeLines(capture.output(sessionInfo()),file.path(out,"R-SESSION.txt")); stopifnot(nrow(result)==40L,all(result$passed))
writeLines(c("PASS_INDEPENDENT_R_FITS_40_ORIGINAL_ROWS","Quantitative -9 was retained as observed, matching source.","Quantitative original coefficient P uses t degrees of freedom N, rather than residual degrees of freedom.","Per-threshold R2 is full-minus-null; summary PRS.R2 uses 1-(1-full)/(1-null).","R convergence was checked for these fixtures; original C++ GLM convergence is not exposed."),file.path(out,"COMPLETED.txt"));cat("PASS 40 original fit rows independently checked with base R\n")
