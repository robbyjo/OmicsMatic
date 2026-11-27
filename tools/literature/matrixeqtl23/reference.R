# Owned synthetic-control harness. Load an unchanged privately retained CRAN source.
args <- commandArgs(TRUE)
stopifnot(length(args)==2)
source(args[1], local=.GlobalEnv)
root <- args[2]
options(digits=17)
readmat <- function(name) as.matrix(read.delim(file.path(root,paste0(name,'.tsv')),row.names=1,check.names=FALSE))
s <- readmat('snps'); g <- readmat('genes'); c <- readmat('covariates')
if (file.exists(file.path(root,'error_covariance.tsv'))) ec <- readmat('error_covariance') else ec <- numeric()
sp <- read.delim(file.path(root,'snp_positions.tsv'),check.names=FALSE)[,1:3]
gp <- read.delim(file.path(root,'gene_positions.tsv'),check.names=FALSE)
settings <- read.delim(file.path(root,'settings.tsv'),check.names=FALSE)
sd <- SlicedData$new(); sd$CreateFromMatrix(s); sd$ResliceCombined(settings$block)
gd <- SlicedData$new(); gd$CreateFromMatrix(g); gd$ResliceCombined(settings$block)
cd <- SlicedData$new(); cd$CreateFromMatrix(c)
r <- Matrix_eQTL_main(sd,gd,cd,file.path(root,'original-all-output.tsv'),
    settings$threshold,modelLINEAR,ec,FALSE,file.path(root,'original-cis-output.tsv'),
    settings$cis_threshold,sp,gp,settings$distance,FALSE,TRUE,FALSE)
for (name in intersect(c('all','cis','trans'),names(r))) {
  x <- r[[name]]
  if(!is.null(x$eqtls)) write.table(x$eqtls,file.path(root,paste0('original-',name,'.tsv')),sep='\t',row.names=FALSE,quote=FALSE,na='NA')
  write.table(data.frame(ntests=x$ntests),file.path(root,paste0('original-',name,'-count.tsv')),sep='\t',row.names=FALSE,quote=FALSE)
  for (type in c('snps','gene')) if(!is.null(x[[paste0('min.pv.',type)]]))
    write.table(data.frame(id=names(x[[paste0('min.pv.',type)]]),p=x[[paste0('min.pv.',type)]]),file.path(root,paste0('original-',name,'-minimum-',type,'.tsv')),sep='\t',row.names=FALSE,quote=FALSE)
}
# Independent ordinary/full GLS fit, with source imputation and source zero-row cases
# excluded from SE qualification because those coefficients are unidentifiable.
imp <- function(x) {for(i in seq_len(nrow(x))) {m<-mean(x[i,],na.rm=TRUE);if(is.na(m))m<-0;x[i,is.na(x[i,])]<-m};x}
s<-imp(s);g<-imp(g);c<-imp(c)
if(length(ec)>0) {e<-eigen(ec,symmetric=TRUE);w<-e$vectors%*%diag(1/sqrt(e$values))%*%t(e$vectors)} else w<-diag(ncol(s))
cv<-t(rbind(1,c))
rows<-list()
for(i in seq_len(nrow(s))) for(j in seq_len(nrow(g))) {
  design<-w%*%cbind(cv,s[i,]); y<-as.vector(w%*%g[j,]);fit<-lm.fit(design,y)
  if(fit$rank!=ncol(design)) next
  df<-length(y)-fit$rank;v<-sum(fit$residuals^2)/df
  rr<-qr.R(fit$qr);se<-sqrt(diag(chol2inv(rr))*v)
  rows[[length(rows)+1]]<-data.frame(snp=rownames(s)[i],gene=rownames(g)[j],beta=tail(fit$coefficients,1),SE=tail(se,1),df=df)
}
write.table(do.call(rbind,rows),file.path(root,'independent-lm.tsv'),sep='\t',row.names=FALSE,quote=FALSE,na='NA')
writeLines(c(R.version.string,capture.output(sessionInfo())),file.path(root,'R-session.txt'))
