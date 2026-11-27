# Own independent complete-SVD controls, distinct from the original partial PCA solvers.
args<-commandArgs(TRUE);stopifnot(length(args)==2)
root<-args[1];out<-args[2];stopifnot(!file.exists(out));dir.create(out)
records<-list()
for(name in c('wide','tall')) {
  g<-as.matrix(read.table(file.path(root,name,'dosages.tsv'),header=FALSE,na.strings='NA'))
  for(tag in c('v1.2.6','v2.0')) {
    legacy<-tag=='v1.2.6';x<-g
    for(j in seq_len(nrow(x))) {
      mean<-mean(x[j,],na.rm=TRUE);stopifnot(is.finite(mean));x[j,is.na(x[j,])]<-mean
      p<-mean/2;sd<-sqrt((if(legacy)1 else 2)*p*(1-p))
      if(sd>1e-9)x[j,]<-(x[j,]-mean)/sd else if(!legacy)x[j,]<-0
    }
    x<-t(x);n<-nrow(x);m<-ncol(x);k<-20
    fit<-svd(x,nu=k,nv=k);divisor<-if(legacy)n-1 else m;ev<-fit$d[seq_len(k)]^2/divisor;trace<-sum(x*x)/divisor
    folder<-file.path(root,name,tag)
    val<-scan(file.path(folder,'eigenvalues.txt'),quiet=TRUE);pve<-scan(file.path(folder,'pve.txt'),quiet=TRUE)
    mat<-function(file)if(legacy)as.matrix(read.table(file.path(folder,file),header=FALSE))else as.matrix(read.delim(file.path(folder,file),check.names=FALSE)[,-c(1,2)])
    u<-mat('eigenvectors.txt');v<-mat('loadings.txt');pc<-mat('pcs.txt');stopifnot(all(dim(u)==c(n,k)),all(dim(v)==c(m,k)))
    align<-sign(colSums(u*fit$u));stopifnot(all(align!=0));uu<-sweep(fit$u,2,align,'*');vv<-sweep(fit$v,2,align,'*')
    multiplier<-if(legacy)if(n>m)fit$d[seq_len(k)]else ev else sqrt(ev)
    expectedPc<-sweep(uu,2,multiplier,'*')
    relative<-function(a,b)max(abs(a-b)/pmax(1,abs(b)))
    errors<-c(eigenvalues=relative(val,ev),pve=relative(pve,ev/trace),eigenvectors=relative(u,uu),loadings=relative(v,vv),pc_scores=relative(pc,expectedPc))
    stopifnot(all(is.finite(errors)),max(errors)<if(legacy)2e-5 else 1e-7)
    records[[length(records)+1]]<-data.frame(case=name,edition=tag,quantity=names(errors),maximum_error=as.numeric(errors),complete_svd=TRUE,top_k_subspace_compared=TRUE)
  }
}
write.table(do.call(rbind,records),file.path(out,'complete-svd-comparison.tsv'),sep='\t',quote=FALSE,row.names=FALSE)
writeLines(c(R.version.string,capture.output(sessionInfo())),file.path(out,'R-session.txt'))
