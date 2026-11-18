options(warn=2,digits=17)
set.seed(19870624)
input <- list(); expected <- list(); correlations <- list()
for (id in 1:64) {
 k <- 2L+(id%%7L); n <- sample(1:30,k,replace=TRUE); control <- (id%%k)
 scale <- c(1e-100,1,1e100)[1L+(id%%3L)]
 x <- lapply(seq_len(k),function(i) (rnorm(n[i])+i/3)*scale)
 d <- data.frame(group=rep(0:(k-1L),n),value=unlist(x))
 d$group <- factor(d$group,levels=c(control,(0:(k-1L))[0:(k-1L)!=control]))
 fit <- lm(value~group,data=d,na.action=na.fail)
 means <- tapply(d$value,d$group,mean); v <- vcov(fit); b <- coef(fit)[-1L]
 se <- sqrt(diag(v))[-1L]; treatments <- as.integer(levels(d$group)[-1L])
 input[[id]] <- data.frame(model=id,control=control,group=rep(0:(k-1L),n),value=unlist(x))
 expected[[id]] <- data.frame(model=id,treatment=treatments,control=control,df=df.residual(fit),sd=sigma(fit),effect=unname(b),se=unname(se),t=unname(b/se),raw_p=2*pt(abs(b/se),df.residual(fit),lower.tail=FALSE))
 cr <- cov2cor(v[-1L,-1L,drop=FALSE]); correlations[[id]] <- data.frame(model=id,row=rep(0:(k-2L),each=k-1L),column=rep(0:(k-2L),k-1L),value=as.vector(t(cr)))
}
write.table(do.call(rbind,input),'inputs.tsv',sep='\t',row.names=FALSE,quote=FALSE)
write.table(do.call(rbind,expected),'expected.tsv',sep='\t',row.names=FALSE,quote=FALSE)
write.table(do.call(rbind,correlations),'correlation.tsv',sep='\t',row.names=FALSE,quote=FALSE)
capture.output(sessionInfo(),file='SESSION.txt')
