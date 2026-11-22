args <- commandArgs(trailingOnly=TRUE);stopifnot(length(args)==2L,getRversion()=="4.6.1")
.libPaths(c(args[[1L]],file.path(R.home(),"library")),include.site=FALSE);stopifnot(as.character(packageVersion("SKAT"))=="2.2.5")
out <- args[[2L]];stopifnot(!dir.exists(out));dir.create(out,recursive=TRUE)
set.seed(20261122);n <- 120L;G <- matrix(0,nrow=n,ncol=8L)
for(j in seq_len(ncol(G)))G[sample.int(n,5L+j*2L),j] <- sample(c(1,2),5L+j*2L,replace=TRUE,prob=c(.9,.1))
G[,1] <- 0;cov <- seq_len(n)/n;continuous <- sin(seq_len(n)/3)+.2*cov+.5*G[,4];binary <- as.numeric(seq_len(n)%%5==0)
colnames(G) <- paste0("artificial-variant-",seq_len(ncol(G)));rownames(G) <- paste0("artificial-sample-",seq_len(n))
saveRDS(list(G=G,covariate=cov,continuous=continuous,binary=binary),file.path(out,"ARTIFICIAL-INPUTS.rds"))
rows <- list()
for(trait in c("C","D"))for(method in c("davies","optimal.adj"))for(adjust in c(FALSE,TRUE)) {
  id <- paste(trait,method,adjust,sep="-");seen <- character();err <- NULL;null <- NULL
  set.seed(20261122)
  value <- tryCatch(withCallingHandlers({Y <- if(trait=="C")continuous else binary;null <- SKAT::SKAT_Null_Model(Y~cov,out_type=trait,Adjustment=adjust);SKAT::SKAT(G,null,method=method,kernel="linear.weighted",weights.beta=c(1,25),is_check_genotype=TRUE)},warning=function(w){seen <<- c(seen,conditionMessage(w));invokeRestart("muffleWarning")}),error=function(e){err <<- conditionMessage(e);NULL})
  saveRDS(list(null=null,value=value,warnings=seen,error=err),file.path(out,paste0(id,".rds")))
  rows[[length(rows)+1L]] <- data.frame(id=id,status=if(is.null(err))"returned" else "error",p=if(is.null(err))format(value$p.value,digits=17) else "",error=if(is.null(err))"" else err,warnings=paste(seen,collapse=" | "),stringsAsFactors=FALSE)
}
write.table(do.call(rbind,rows),file.path(out,"RESULTS.tsv"),sep="\t",quote=TRUE,row.names=FALSE);capture.output(sessionInfo(),file=file.path(out,"SESSION.txt"));cat("Eight original SKAT2.2.5 artificial fit attempts retained;not an execution adapter or paper reproduction\n")
