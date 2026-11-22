args<-commandArgs(TRUE)
d<-read.delim(file.path(args[1],"source-counts.tsv"),check.names=FALSE)
fits<-list()
for(add in c(0,1))for(i in seq_len(nrow(d))) {
 r<-d[i,];ct<-matrix(c(r$a,r$b,r$c,r$d)+add,2,byrow=TRUE);f<-fisher.test(ct,conf.level=.95)
 m<-sum(ct[,1]);n<-sum(ct[,2]);k<-sum(ct[1,]);x<-ct[1,1];support<-max(0,k-n):min(k,m)
 ld<-dhyper(support,m,n,k,log=TRUE);weights<-exp(ld-max(ld));prob<-weights/sum(weights)
 take<-if(prob[x-min(support)+1]==0)ld<=ld[x-min(support)+1]+log1p(1e-7) else prob<=prob[x-min(support)+1]*(1+1e-7)
 chosen<-ld[take];lp<-if(all(take))0 else min(0,max(chosen)+log(sum(exp(chosen-max(chosen))))-max(ld)-log(sum(weights)))
 fits[[paste(r$sheet,r$source_row,add,sep=":")]]<-c(p=f$p.value,lp=lp)
}
fi<-list();input<-list();reference<-list()
for(sheet in unique(d$sheet))for(add in c(0,1))for(encoding in c("P_VALUE","NATURAL_LOG"))for(method in c("NONE","BH","BY","HOLM","BONFERRONI")) {
 family<-paste(sheet,add,encoding,method,sep="-");rows<-d[d$sheet==sheet,];ids<-as.character(rows$source_row)
 p<-sapply(ids,function(id)fits[[paste(sheet,id,add,sep=":")]]["p"]);lp<-sapply(ids,function(id)fits[[paste(sheet,id,add,sep=":")]]["lp"])
 p<-c(p,NA_real_);lp<-c(lp,NA_real_);ids<-c(ids,"missing");N<-length(ids)
 fi[[length(fi)+1]]<-data.frame(family=family,correction=method,encoding=encoding,pseudocount=add,confidence=.95)
 input[[length(input)+1]]<-data.frame(family=family,test_id=ids,trait=c(as.character(rows$trait),"declared-missing"),context=c(as.character(rows$context),"missing"),a=c(rows$a,NA),b=c(rows$b,NA),c=c(rows$c,NA),d=c(rows$d,NA))
 if(encoding=="P_VALUE") {
   q<-stats::p.adjust(p,method=switch(method,NONE="none",HOLM="holm",BONFERRONI="bonferroni",method));lq<-log(q);selected<-!is.na(q)&q<=.05
 } else {
   at<-which(!is.na(lp));order<-at[order(lp[at],at)];n<-length(order);v<-lp[order]
   factor<-if(method=="BY")sum(1/seq_len(n)) else 1
   adjusted<-switch(method,NONE=v,BONFERRONI=v+log(n),HOLM=cummax(v+log(n-seq_len(n)+1)),BH=rev(cummin(rev(v+log(n/seq_len(n))))),BY=rev(cummin(rev(v+log(factor*n/seq_len(n))))))
   lq<-rep(NA_real_,N);lq[order]<-pmin(0,adjusted);q<-exp(lq);selected<-!is.na(lq)&lq<=log(.05)
 }
 reference[[length(reference)+1]]<-data.frame(family=family,test_id=ids,p=p,log_p=lp,adjusted=q,log_adjusted=lq,selected=ifelse(is.na(p),"NA",ifelse(selected,"true","false")))
}
families<-do.call(rbind,fi);tests<-do.call(rbind,input);result<-do.call(rbind,reference)
for(name in c("p","log_p","adjusted","log_adjusted"))result[[name]]<-ifelse(is.na(result[[name]]),"NA",format(result[[name]],digits=17,scientific=TRUE,trim=TRUE))
write.table(families,file.path(args[1],"families.tsv"),sep="\t",quote=FALSE,row.names=FALSE,na="NA")
write.table(tests,file.path(args[1],"input.tsv"),sep="\t",quote=FALSE,row.names=FALSE,na="NA")
write.table(result,file.path(args[1],"reference.tsv"),sep="\t",quote=FALSE,row.names=FALSE,na="NA")
cat(R.version.string,"\n",as.character(packageVersion("stats")),"\n")
