# Artificial prepared prediction models; no people or biological estimates.
args <- commandArgs(TRUE)
stopifnot(length(args)==1L)
snps <- read.table(paste0(args[1],"/ref.1.bim"),stringsAsFactors=FALSE)
set.seed(9346)
for(i in 1:3) {
  wgt.matrix <- cbind(enet=sin(seq_len(nrow(snps))+i)/5,
                      top1=seq_len(nrow(snps))/2,blup=cos(seq_len(nrow(snps)))/5)
  rownames(wgt.matrix) <- snps[,2]
  cv.performance <- matrix(c(.2,.0001,.1,.01,.15,.001),nrow=2,
                           dimnames=list(c("rsq","pval"),c("enet","top1","blup")))
  hsq <- .4;hsq.pv <- .001;N.tot <- 64
  save(wgt.matrix,snps,cv.performance,hsq,hsq.pv,N.tot,
       file=paste0(args[1],"/weights/g",i,".wgt.RDat"))
}
write.table(data.frame(WGT=paste0("g",1:3,".wgt.RDat"),
                      ID=paste0("SYNTH_GENE_",1:3),CHR=1,P0=100000,P1=111000,N=64),
            file=paste0(args[1],"/weights.pos"),sep="\t",quote=FALSE,row.names=FALSE)
