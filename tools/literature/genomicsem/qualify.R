# Copyright (C) 2026 JLinAlg contributors; GPL-3.0-or-later.
# Synthetic source/runtime qualification. This is not the end-user Java adapter.
options(warn=1, digits=17)
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==1L)
cfg<-jsonlite::read_json(args[1],simplifyVector=TRUE)
.libPaths(c(cfg$libraries,.Library),include.site=FALSE)
profile<-jsonlite::read_json(cfg$profile,simplifyVector=FALSE)
dest<-normalizePath(cfg$out,winslash='/',mustWork=TRUE)
stopifnot(getRversion()==profile$R$version,R.version$platform==profile$R$platform)
for(p in profile$dependencies)stopifnot(packageDescription(p$package,fields='Version')==p$version)
stopifnot(as.character(packageVersion('lavaan'))==profile$lavaan$runtime_version,
          packageDescription('Matrix',fields='Version')==profile$R$Matrix)
library(GenomicSEM)
stopifnot(as.character(packageVersion('GenomicSEM'))==profile$upstream$package_version)
ns<-asNamespace('GenomicSEM');e<-new.env(parent=ns)
for(f in sort(list.files(file.path(cfg$source,'R'),full.names=TRUE)))sys.source(f,envir=e,keep.source=FALSE)
fn<-ls(e,all.names=TRUE)[vapply(ls(e,all.names=TRUE),function(n)is.function(get(n,e,inherits=FALSE)),logical(1))]
stopifnot(length(fn)==profile$upstream$original_namespace_functions)
for(n in fn)stopifnot(exists(n,ns,inherits=FALSE),
    identical(deparse(formals(get(n,e))),deparse(formals(get(n,ns)))),
    identical(deparse(body(get(n,e))),deparse(body(get(n,ns)))))
writeLines(fn,file.path(dest,'namespace-functions.txt'))
capture.output(sessionInfo(),file=file.path(dest,'session-info.txt'))
conditions<-list();cases<-list();cell_checks<-0L
check<-function(actual,expected,label,atol=1e-8,rtol=1e-6){
 actual<-as.numeric(actual);expected<-as.numeric(expected)
 if(length(actual)!=length(expected)||any(!is.finite(actual))||any(!is.finite(expected))||
    any(abs(actual-expected)>atol+rtol*abs(expected)))stop(paste('Independent numeric check failed:',label))
 cell_checks<<-cell_checks+length(actual)
}
case<-function(name,run,validate){
 cat('\nQUALIFICATION CASE:',name,'\n');dir<-file.path(dest,name);dir.create(dir)
 w<-list();result<-tryCatch(withCallingHandlers(run(),warning=function(v){w[[length(w)+1L]]<<-conditionMessage(v)}),error=function(v)v)
 if(inherits(result,'error')){
   cases[[name]]<<-list(status='FAIL2_NO_QUALIFIED_RESULT',message=conditionMessage(result))
   saveRDS(result,file.path(dir,'original-error.rds'))
 }else{
   saveRDS(result,file.path(dir,'original-result.rds'),version=3)
   capture.output(str(result),file=file.path(dir,'structure.txt'))
   verdict<-tryCatch({validate(result);NULL},error=function(v)conditionMessage(v))
   cases[[name]]<<-if(is.null(verdict))list(status='PASS')else list(status='FAIL2_NO_QUALIFIED_RESULT',message=verdict)
 }
 conditions[[name]]<<-w
 invisible(result)
}
make_cov<-function(k=4){
 t<-paste0('T',seq_len(k));l<-seq(.5,.8,length.out=k);r<-seq(.4,.25,length.out=k)
 S<-tcrossprod(l)+diag(r);dimnames(S)<-list(t,t);m<-k*(k+1L)/2L
 V<-diag(seq(.0001,.00019,length.out=m))+tcrossprod(seq(.001,.003,length.out=m))
 I<-matrix(.05,k,k);diag(I)<-1;dimnames(I)<-list(t,t)
 list(V=V,S=S,I=I,N=rep(50000,m),m=1000000)
}
cov<-make_cov();traits<-colnames(cov$S)
saveRDS(cov,file.path(dest,'synthetic-covstruc.rds'),version=3)
model<-'F1 =~ NA*T1 + T2 + T3 + T4\nF1 ~~ 1*F1'
param_key<-function(d)paste(d$lhs,d$op,d$rhs,sep='|')
# Independent lavaan fit + explicit sandwich algebra; this does not call
# GenomicSEM for the comparator. It is not an independent optimizer.
lav<-lavaan::sem(model,sample.cov=cov$S,estimator='DWLS',se='standard',
 WLS.V=diag(1/diag(cov$V)),sample.nobs=2,optim.dx.tol=.01,ordered=FALSE)
delta<-lavaan::lavInspect(lav,'delta');W<-lavaan::lavInspect(lav,'WLS.V')
bread<-solve(t(delta)%*%W%*%delta)
sandwich<-bread%*%t(delta)%*%W%*%cov$V%*%W%*%delta%*%bread
pt<-lavaan::parameterTable(lav);pt<-pt[pt$free>0,];pt<-pt[order(pt$free),]
independent<-data.frame(lhs=pt$lhs,op=pt$op,rhs=pt$rhs,estimate=pt$est,SE=sqrt(diag(sandwich)))
write.table(independent,file.path(dest,'independent-lavaan-sandwich.tsv'),sep='\t',quote=TRUE,row.names=FALSE)
validate_common<-function(r,estimation){
 d<-r$results;ix<-match(param_key(independent),param_key(d));stopifnot(!anyNA(ix))
 check(d$Unstandardized_Estimate[ix],independent$estimate,'common-factor estimates')
 if(estimation=='DWLS')check(d$Unstandardized_SE[ix],independent$SE,'full sampling covariance sandwich SE')
 stopifnot(as.numeric(r$modelfit$df)==2,all(is.finite(as.numeric(r$modelfit[1,]))))
}
case('commonfactor-DWLS',function()commonfactor(cov,estimation='DWLS'),function(r)validate_common(r,'DWLS'))
case('commonfactor-ML',function()commonfactor(cov,estimation='ML'),function(r)validate_common(r,'ML'))
validate_user<-function(r){
 d<-r$results;ix<-match(param_key(independent),param_key(d));stopifnot(!anyNA(ix))
 check(d$Unstand_Est[ix],independent$estimate,'usermodel estimates')
 check(d$Unstand_SE[ix],independent$SE,'usermodel full sampling covariance SE')
 check(r$resid_cov[[1]],cov$S,'implied covariance',atol=1e-7)
}
case('usermodel-DWLS',function()usermodel(cov,estimation='DWLS',model=model,std.lv=FALSE,CFIcalc=TRUE,imp_cov=TRUE,fix_resid=FALSE),validate_user)
case('usermodel-ML',function()usermodel(cov,estimation='ML',model=model,std.lv=FALSE,CFIcalc=TRUE,imp_cov=TRUE,fix_resid=FALSE),function(r)check(r$resid_cov[[1]],cov$S,'ML implied covariance',atol=1e-7))
cov3<-make_cov(3);reg<-solve(cov3$S[1:2,1:2],cov3$S[1:2,3])
case('conditional-regression',function()usermodel(cov3,estimation='DWLS',model='T3 ~ T1 + T2',CFIcalc=FALSE,imp_cov=TRUE,fix_resid=FALSE),function(r){
 d<-r$results;ix<-which(d$lhs=='T3'&d$op=='~');check(d$Unstand_Est[ix],reg,'joint subtype-style regression',atol=1e-7)
})
cov2<-make_cov(2)
cholesky<-'EA =~ 1*T1 + NA*T2\nF =~ 1*T2\nT1 ~~ 0*T1\nT2 ~~ 0*T2\nEA ~~ 0*F'
case('two-trait-Cholesky',function()usermodel(cov2,estimation='DWLS',model=cholesky,CFIcalc=FALSE,imp_cov=TRUE,fix_resid=FALSE),function(r){
 check(r$resid_cov[[1]],cov2$S,'Cholesky implied covariance',atol=1e-6)
 stopifnot(as.numeric(r$modelfit$df)==0)
})
cov6<-make_cov(6);L<-matrix(0,6,2);L[1:3,1]<-c(.5,.6,.7);L[4:6,2]<-c(.5,.6,.7)
cov6$S<-L%*%matrix(c(1,.3,.3,.8),2)%*%t(L)+diag(.3,6);dimnames(cov6$S)<-list(paste0('T',1:6),paste0('T',1:6))
hierarchical<-'F1 =~ T1 + T2 + T3\nF2 =~ T4 + T5 + T6\nH =~ 1*F1 + 1*F2\nF1 ~~ 0*F2'
case('higher-order-SEM',function()usermodel(cov6,estimation='DWLS',model=hierarchical,CFIcalc=TRUE,imp_cov=TRUE,fix_resid=FALSE),function(r)check(r$resid_cov[[1]],cov6$S,'higher-order implied covariance',atol=1e-6))
snps<-data.frame(SNP=c('rsSynthetic1','rsSynthetic2'),CHR=c(1,1),BP=c(1001,1002),MAF=c(.2,.3),A1=c('A','C'),A2=c('G','T'))
for(j in seq_along(traits))snps[[paste0('beta.',traits[j])]]<-c(.01,-.02)*seq(.5,.8,length.out=4)[j]
for(j in seq_along(traits))snps[[paste0('se.',traits[j])]]<-c(.003,.004)
write.table(snps,file.path(dest,'synthetic-snps.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
twas<-data.frame(Gene=c('SyntheticGene1','SyntheticGene2'),Panel=c('SyntheticPanel','SyntheticPanel'),HSQ=c(.3,.4))
twas<-cbind(twas,snps[,7:ncol(snps)]);write.table(twas,file.path(dest,'synthetic-TWAS.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
validate_cfGWAS<-function(d,tw=FALSE){
 stopifnot(nrow(d)==2,all(d$fail==0))
 check(d$est,c(.005,-.010),'planted common-factor association',atol=1e-7)
 check(d$Z_Estimate,d$est/d$se_c,'commonfactor GWAS Z',atol=1e-9)
 check(d$Pval_Estimate,2*pnorm(abs(d$Z_Estimate),lower.tail=FALSE),'commonfactor GWAS P',atol=1e-14)
 check(d$Q_df,c(3,3),'commonfactor Q df',atol=0,rtol=0)
 check(d$Q_pval,pchisq(d$Q,df=d$Q_df,lower.tail=FALSE),'commonfactor Q P',atol=1e-12)
 if(tw)stopifnot(identical(d$Gene,twas$Gene),all(d$rhs=='Gene'))else stopifnot(identical(d$SNP,snps$SNP),identical(as.numeric(d$BP),snps$BP),identical(d$A1,snps$A1),identical(d$A2,snps$A2))
}
validate_userGWAS<-function(r,tw=FALSE){
 stopifnot(length(r)==2)
 for(i in 1:2){
  d<-r[[i]];assoc<-d[d$op=='~'&d$rhs==if(tw)'Gene' else 'SNP',,drop=FALSE]
  stopifnot(nrow(assoc)==1,all(d$error==0),all(is.finite(as.numeric(unlist(assoc[,c('est','SE','Z_Estimate','Pval_Estimate')])))),assoc$SE>0)
  check(assoc$est,c(.005,-.010)[i],'planted userGWAS association',atol=1e-7)
  check(assoc$Z_Estimate,assoc$est/assoc$SE,'userGWAS Z',atol=1e-9)
  check(assoc$Pval_Estimate,2*pnorm(abs(assoc$Z_Estimate),lower.tail=FALSE),'userGWAS P',atol=1e-14)
  if(tw)stopifnot(all(d$Gene==twas$Gene[i]),all(d$Panel==twas$Panel[i]))else stopifnot(all(d$SNP==snps$SNP[i]),all(d$CHR==snps$CHR[i]),all(d$BP==snps$BP[i]),all(d$A1==snps$A1[i]),all(d$A2==snps$A2[i]))
 }
}
for(tw in c(FALSE,TRUE)){
 x<-if(tw)twas else snps;label<-if(tw)'TWAS'else'GWAS'
 case(paste0('commonfactor-',label),function()commonfactorGWAS(cov,x,estimation='DWLS',cores=1,parallel=FALSE,GC='standard',TWAS=tw,smooth_check=TRUE),function(r)validate_cfGWAS(r,tw))
 case(paste0('user-',label),function()userGWAS(cov,x,estimation='DWLS',model=paste0('F =~ T1 + T2 + T3 + T4\nF ~ ',if(tw)'Gene'else'SNP'),cores=1,parallel=FALSE,GC='standard',TWAS=tw,fix_measurement=TRUE,Q_SNP=TRUE,smooth_check=TRUE),function(r)validate_userGWAS(r,tw))
}
case('commonfactor-GWAS-parallel2',function()commonfactorGWAS(cov,snps,estimation='DWLS',cores=2,parallel=TRUE,GC='standard',smooth_check=TRUE),validate_cfGWAS)
case('user-GWAS-parallel2',function()userGWAS(cov,snps,estimation='DWLS',model='F =~ T1 + T2 + T3 + T4\nF ~ SNP',cores=2,parallel=TRUE,GC='standard',fix_measurement=TRUE,Q_SNP=TRUE,smooth_check=TRUE),validate_userGWAS)
passed<-all(vapply(cases,function(c)identical(c$status,'PASS'),logical(1)))
summary<-list(status=if(passed)'PASS_DECLARED_SYNTHETIC_UPSTREAM_CASES'else'FAIL2_RUNTIME_CASES_UNQUALIFIED',profile=profile$profile,original_namespace_functions=length(fn),cases=cases,numeric_cells_checked=cell_checks,conditions=conditions,catalog_admitted=FALSE,new_family_credit=0,new_review_credit=0,whole_goal_complete=FALSE)
jsonlite::write_json(summary,file.path(dest,'RESULT.json'),auto_unbox=TRUE,pretty=TRUE,null='null',digits=17)
if(!passed)quit(status=2)
