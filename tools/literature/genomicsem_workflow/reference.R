options(warn=1,digits=17)
a<-commandArgs(trailingOnly=TRUE);stopifnot(length(a)==4)
runtime<-normalizePath(a[4],winslash='/',mustWork=TRUE)
.libPaths(c(file.path(runtime,'library'),.Library),include.site=FALSE)
library(GenomicSEM)
q<-jsonlite::read_json(a[1],simplifyVector=FALSE);o<-q$options;op<-q$operation
out<-normalizePath(a[2],winslash='/',mustWork=TRUE)
read_matrix<-function(file){d<-read.delim(file,check.names=FALSE,colClasses='character',quote='',comment.char='');m<-sapply(d[-1],as.numeric);rownames(m)<-d[[1]];m}
cov<-list(V=read_matrix(q$inputs$sampling_covariance),S=read_matrix(q$inputs$covariance))
is_assoc<-op%in%c('COMMONFACTOR_GWAS','USER_GWAS','COMMONFACTOR_TSEM','USER_TSEM');tw<-op%in%c('COMMONFACTOR_TSEM','USER_TSEM');user<-op%in%c('USERMODEL','USER_GWAS','USER_TSEM')
if(is_assoc){
 cov$I<-read_matrix(q$inputs$intercepts)
 SNPs<-read.delim(q$inputs$associations,check.names=FALSE,colClasses='character',quote='',comment.char='')
 if(tw){SNPs$HSQ<-as.numeric(SNPs$HSQ);first<-4L}else{SNPs$BP<-as.numeric(SNPs$BP);SNPs$MAF<-as.numeric(SNPs$MAF);first<-7L}
 for(j in seq.int(first,ncol(SNPs)))SNPs[[j]]<-as.numeric(SNPs[[j]])
}
if(user)model<-paste(readLines(q$inputs$model,warn=FALSE,encoding='UTF-8'),collapse='\n')
RNGkind('Mersenne-Twister','Inversion','Rejection');set.seed(as.integer(o$seed))
value<-{if(op=='COMMONFACTOR')commonfactor(cov,estimation=o$estimation)
else if(op=='USERMODEL')usermodel(cov,estimation=o$estimation,model=model,CFIcalc=o$CFIcalc,std.lv=o$std_lv,imp_cov=TRUE,fix_resid=o$fix_resid,toler=o$tolerance,Q_Factor=o$Q_Factor)
else if(!user)commonfactorGWAS(cov,SNPs,estimation=o$estimation,cores=as.integer(o$cores),toler=o$tolerance,SNPSE=o$SNPSE,parallel=o$parallel,GC=o$GC,MPI=FALSE,TWAS=tw,smooth_check=TRUE)
else userGWAS(cov,SNPs,estimation=o$estimation,model=model,printwarn=TRUE,sub=if(length(o$sub))unlist(o$sub)else FALSE,cores=as.integer(o$cores),toler=o$tolerance,SNPSE=o$SNPSE,parallel=o$parallel,GC=o$GC,MPI=FALSE,TWAS=tw,smooth_check=TRUE,std.lv=o$std_lv,fix_measurement=o$fix_measurement,Q_SNP=o$Q_SNP,analytic=FALSE)}
saveRDS(value,file.path(out,'original-reference.rds'),version=3)
adapter<-readRDS(a[3]);comparison<-all.equal(value,adapter,tolerance=0,check.attributes=TRUE)
jsonlite::write_json(list(status=if(identical(value,adapter))'PASS_ORIGINAL_RESULT_IDENTICAL'else'FAIL_ORIGINAL_RESULT_DIFFERS',identical=identical(value,adapter),all_equal_zero_tolerance=comparison,independent_optimizer=FALSE,original_package_called_directly=TRUE),file.path(out,'COMPARISON.json'),auto_unbox=TRUE,pretty=TRUE)
if(!identical(value,adapter))quit(status=2)
