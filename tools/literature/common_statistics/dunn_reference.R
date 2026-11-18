# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent base-R ranks/normal tails/p.adjust plus unchanged original dunn.test functions.
args <- commandArgs(trailingOnly=TRUE)
stopifnot(length(args)==2L, !dir.exists(args[2]))
options(warn=2, digits=17)
dir.create(args[2], recursive=TRUE)
original <- normalizePath(args[1], mustWork=TRUE)
destination <- normalizePath(args[2], mustWork=TRUE)
stopifnot(requireNamespace("rlang",quietly=TRUE))
namespace <- new.env(parent=globalenv())
for (path in sort(list.files(file.path(original,"R"),full.names=TRUE,pattern="[.]R$"))) sys.source(path,envir=namespace)
cases <- list(
  balanced=list(1:5,6:10,11:15),
  tied_unbalanced=list(c(0,0,1,1,2),c(1,2),c(2,2,3,3),c(-1,1,5)),
  two_groups=list(c(1,2,3),c(4,5,6)),
  singleton_constants=list(0,c(0,0,0),c(1,2)),
  equal_mean_ranks=list(c(-3,0,3),c(-2,0,2),c(-1,0,1)),
  extreme_finite=list(c(-1e300,0,1e300),c(-1e299,1e-300),c(1e-300,1e299)),
  underflow=list(rep(0,800),rep(1,800),rep(2,800)),
  subnormal_two_groups=list(rep(0,730),rep(1,730)),
  subnormal_three_groups=list(rep(0,488),rep(1,488),rep(2,488)),
  nearly_all_tied=list(rep(0,999),c(0,1),0),
  signed_zero=list(c(-0,0,0),c(0,1,2),c(2,3)),
  many_groups=lapply(1:9,function(i)seq(i,i+3))
)
set.seed(19870624)
for (i in 1:24) {
  k <- sample(2:7,1)
  cases[[sprintf("random_%02d",i)]] <- lapply(seq_len(k),function(j) sample(-6:10,sample(2:35,1),replace=TRUE))
}
adjust <- function(logp, method) {
  m <- length(logp)
  if (method=="NONE") return(logp)
  if (method %in% c("BONFERRONI","HOLM","BH","BY")) {
    # Standard base-R adjusted probabilities; the independently computed log route
    # below also retains underflowed positive probabilities.
    ordinary <- p.adjust(exp(logp),method=if(method %in% c("BH","BY"))method else tolower(method))
    o <- order(logp); q <- logp[o]
    a <- switch(method,
      BONFERRONI=q+log(m),
      HOLM=cummax(q+log(m+1-seq_len(m))),
      BH=rev(cummin(rev(q+log(m)-log(seq_len(m))))),
      BY=rev(cummin(rev(q+log(m)+log(sum(1/seq_len(m)))-log(seq_len(m)))))
    )
    result <- numeric(m);result[o] <- pmin(0,a)
    stopifnot(max(abs(exp(result)-ordinary))<2e-15)
    return(result)
  }
  # Positive log-tail form of the independently declared Sidak correction.
  sidak <- function(lp,n) {
    p <- exp(lp); result <- log(-expm1(n*log1p(-p)))
    tiny <- lp < log(.Machine$double.xmin);result[tiny] <- lp[tiny]+log(n[tiny]);result[n==1] <- lp[n==1];result[lp==0] <- 0
    result
  }
  if(method=="SIDAK")return(pmin(0,sidak(logp,rep(m,m))))
  stopifnot(method=="HOLM_SIDAK")
  o <- order(logp);result <- numeric(m);result[o] <- pmin(0,cummax(sidak(logp[o],m+1-seq_len(m))));result
}
inputs <- list();expected <- list();groups <- list();raw_original <- list();original_failures <- list();raw_adjustment <- list();index <- 0L;gindex <- 0L;original_index <- 0L;adjustment_index <- 0L
for (id in names(cases)) {
  x <- lapply(cases[[id]],as.numeric);k <- length(x);n <- lengths(x);values <- unlist(x,use.names=FALSE);axis <- rep(seq_len(k),n);N <- length(values)
  ranks <- rank(values,ties.method="average");means <- vapply(seq_len(k),function(i)mean(ranks[axis==i]),0)
  variance <- var(ranks);tie <- variance/(N*(N+1)/12);kw <- kruskal.test(x)
  for (i in seq_len(k)) {
    inputs[[length(inputs)+1L]] <- data.frame(case=id,group=i-1L,values=paste(sprintf("%.17g",x[[i]]),collapse=","))
    gindex <- gindex+1L;groups[[gindex]] <- data.frame(case=id,group=i-1L,n=n[i],mean_rank=means[i],rank_variance=variance,tie_correction=tie,kw_statistic=unname(kw$statistic),kw_p=kw$p.value,log_kw_p=pchisq(kw$statistic,k-1,lower.tail=FALSE,log.p=TRUE))
  }
  pairs <- t(combn(seq_len(k),2));z <- (means[pairs[,2]]-means[pairs[,1]])/sqrt(variance*(1/n[pairs[,1]]+1/n[pairs[,2]]));logp <- pmin(0,log(2)+pnorm(abs(z),lower.tail=FALSE,log.p=TRUE))
  # The original source keeps its own all-pair ordering. Compare through literal returned labels.
  original <- namespace$dunn.test(x=values,g=axis,method="none",kw=FALSE,label=TRUE,table=FALSE,list=FALSE,rmc=TRUE,altp=TRUE,interpret=FALSE)
  expected_labels <- paste(pairs[,2],pairs[,1],sep=" - ");match_original <- match(expected_labels,original$comparisons);stopifnot(!anyNA(match_original))
  stopifnot(max(abs(original$Z[match_original]-z))<2e-11,max(abs(original$altP[match_original]-exp(logp)))<2e-14)
  for (method in c("none","bonferroni","holm","bh","by","sidak","hs")) {
    o <- tryCatch(namespace$dunn.test(x=values,g=axis,method=method,kw=FALSE,label=TRUE,table=FALSE,list=FALSE,rmc=TRUE,altp=TRUE,interpret=FALSE),error=function(e)e)
    if(inherits(o,"error")) {
      stopifnot(k==2L,method %in% c("holm","bh","by","hs"),grepl("incorrect number of dimensions",conditionMessage(o),fixed=TRUE))
      original_failures[[length(original_failures)+1L]] <- data.frame(case=id,method=method,status="ORIGINAL_ERROR_NO_RESULT",cause=conditionMessage(o))
      next
    }
    for(j in seq_along(o$Z)) {original_index<-original_index+1L;raw_original[[original_index]]<-data.frame(case=id,method=method,comparison=o$comparisons[j],z=o$Z[j],p=o$altP[j],original_adjusted=o$altP.adjusted[j])}
  }
  families <- list(all=seq_len(nrow(pairs)),control_first=which(pairs[,1]==1L),control_last=which(pairs[,2]==k))
  if(nrow(pairs)>1L)families$selected<-unique(c(nrow(pairs),1L))
  for (family in names(families)) {
    chosen <- families[[family]];f <- pairs[chosen,,drop=FALSE]
    # The last-control route reverses the original orientation; selected also
    # contains a reversed contrast, explicitly testing caller-chosen order.
    if(family=="control_last")f<-f[,2:1,drop=FALSE]
    if(family=="selected")f[1,]<-rev(f[1,])
    signed_z <- (means[f[,2]]-means[f[,1]])/sqrt(variance*(1/n[f[,1]]+1/n[f[,2]]))
    lp <- pmin(0,log(2)+pnorm(abs(signed_z),lower.tail=FALSE,log.p=TRUE))
    for (method in c("NONE","BONFERRONI","HOLM","BH","BY","SIDAK","HOLM_SIDAK")) {
      a <- adjust(lp,method)
      for (j in seq_along(lp)) {index<-index+1L;expected[[index]]<-data.frame(case=id,family=family,method=method,first=f[j,1]-1L,second=f[j,2]-1L,difference=means[f[j,2]]-means[f[j,1]],standard_error=sqrt(variance*(1/n[f[j,1]]+1/n[f[j,2]])),z=signed_z[j],p=exp(lp[j]),log_p=lp[j],adjusted_p=exp(a[j]),log_adjusted_p=a[j])}
    }
  }
}
write_table <- function(rows,name)write.table(do.call(rbind,rows),file.path(destination,name),sep="\t",quote=FALSE,row.names=FALSE,na="NA")
write_table(inputs,"inputs.tsv");write_table(groups,"groups.tsv");write_table(expected,"expected.tsv");write_table(raw_original,"original-dunn-test.tsv");write_table(original_failures,"original-errors.tsv")
writeLines(c(R.version.string,paste("rlang",as.character(packageVersion("rlang")),normalizePath(find.package("rlang"))),paste("source-DESCRIPTION",read.dcf(file.path(args[1],"DESCRIPTION"))[1,"Version"]),"Original dunn.test.R header literally reports1.4.1; DESCRIPTION reports1.4.2. Neither source is changed.","Raw original adjustment outputs are retained separately; standard cumulative Holm/BH/BY values follow stats::p.adjust."),file.path(destination,"RUNTIME.txt"))
writeLines(sprintf("PASS_INDEPENDENT_DUNN_REFERENCE cases=%d comparison_rows=%d original_rows=%d",length(cases),index,original_index),file.path(destination,"COMPLETED.txt"))
