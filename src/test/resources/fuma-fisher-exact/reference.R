args<-commandArgs(TRUE)
d<-read.delim(file.path(args[1],"input.tsv"),check.names=FALSE)
out<-lapply(seq_len(nrow(d)),function(i) {
  r<-d[i,];counts<-matrix(c(r$a,r$b,r$c,r$d)+r$pseudocount,2,byrow=TRUE)
  fit<-fisher.test(counts,alternative="two.sided",conf.level=r$confidence)
  m<-sum(counts[,1]);n<-sum(counts[,2]);k<-sum(counts[1,]);x<-counts[1,1]
  support<-max(0,k-n):min(k,m);ld<-dhyper(support,m,n,k,log=TRUE)
  # Independent finite-log extension; the original fisher.test reports probability only.
  weights<-exp(ld-max(ld));prob<-weights/sum(weights)
  selected<-if(prob[x-min(support)+1]==0) ld<=ld[x-min(support)+1]+log1p(1e-7) else prob<=prob[x-min(support)+1]*(1+1e-7)
  chosen<-ld[selected];logP<-if(all(selected)) 0 else min(0,max(chosen)+log(sum(exp(chosen-max(chosen))))-max(ld)-log(sum(weights)))
  data.frame(case=r$case,p=fit$p.value,log_p=logP,OR=unname(fit$estimate),lower=fit$conf.int[1],upper=fit$conf.int[2])
})
result<-do.call(rbind,out)
for(name in names(result)[-1])result[[name]]<-ifelse(is.na(result[[name]]),"NA",format(result[[name]],digits=17,scientific=TRUE,trim=TRUE))
write.table(result,file.path(args[1],"reference.tsv"),sep="\t",quote=FALSE,row.names=FALSE,na="NA")
writeLines(deparse(stats::fisher.test),file.path(args[1],"R-fisher-test-source.R"))
cat(R.version.string,"\n",as.character(packageVersion("stats")),"\n")
