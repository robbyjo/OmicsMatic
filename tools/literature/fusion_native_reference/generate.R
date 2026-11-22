# Component reference only. Evaluates selected, unchanged original FUSION AST
# expressions, not a transcription or an execution of the complete pipeline.
args <- commandArgs(TRUE)
stopifnot(length(args)==2, dir.exists(args[2]))
original <- normalizePath(args[1],winslash="/",mustWork=TRUE)
destination <- normalizePath(args[2],winslash="/",mustWork=TRUE)
expressions <- parse(original,keep.source=TRUE)
nodes <- list()
walk <- function(x) {
  if (is.call(x)) {
    nodes[[length(nodes)+1L]] <<- x
    for (i in seq_along(x)[-1L]) if(!is.symbol(x[[i]])) walk(x[[i]])
  } else if (is.expression(x) || is.pairlist(x)) {
    for (i in seq_along(x)) if(!identical(x[[i]],quote(expr=))) walk(x[[i]])
  }
}
walk(expressions)
text <- function(x) paste(deparse(x,width.cutoff=500L),collapse=" ")
find <- function(predicate) {
  selected <- Filter(predicate,nodes)
  stopifnot(length(selected)==1L)
  selected[[1L]]
}
assignment <- function(lhs) find(function(x) as.character(x[[1L]])[1L] %in% c("=","<-") && text(x[[2L]])==lhs)
conditional <- function(predicate) find(function(x) identical(x[[1L]],as.name("if")) && predicate(text(x[[2L]]),text(x)))
alleles <- assignment("allele.qc")
zero <- assignment("wgt.matrix[is.na(wgt.matrix)]")
zeroCheck <- conditional(function(condition,whole) condition=="sum(wgt.matrix[, mod.best] != 0) == 0")
top <- conditional(function(condition,whole) startsWith(condition,"substr(") && grepl("colnames\\(cv.performance\\)",condition))
association <- conditional(function(condition,whole) condition=="length(cur.Z) == 0")
ldAssignment <- assignment("cur.LD")
emit <- conditional(function(condition,whole) condition=="!cur.FAIL" && grepl("out.tbl\\$NWGT",whole))
selected <- list(allele_function=alleles,NA_weight_normalization=zero,zero_weight_guard=zeroCheck,
                 top1_selection=top,LD=ldAssignment,imputation_association=association,result_output=emit)
dput(selected,file=file.path(destination,"SELECTED-AST.R"))
writeLines(unlist(lapply(names(selected),function(n) c(paste("#",n),deparse(selected[[n]],width.cutoff=120L),""))),file.path(destination,"SELECTED-EXPRESSIONS.txt"))
eval(alleles)
levels <- c("A","C","G","T","N","a","AT","",NA_character_)
grid <- expand.grid(a1=levels,a2=levels,ref1=levels,ref2=levels,stringsAsFactors=FALSE)
flags <- allele.qc(grid$a1,grid$a2,grid$ref1,grid$ref2)
alleleOutput <- cbind(grid,keep=flags$keep,flip=flags$flip)
write.table(alleleOutput,file=file.path(destination,"alleles.tsv"),sep="\t",quote=FALSE,row.names=FALSE,na="<NA>")

formatValues <- function(v) paste(vapply(v,function(x) {
  if(is.nan(x)) "NaN" else if(is.na(x)) "NA" else if(is.infinite(x)) if(x>0) "Infinity" else "-Infinity" else sprintf("%.17g",x)
},""),collapse=",")
cases <- list()
add <- function(id,g,z,w,maximum=0.5,minimum=0.7,top1=FALSE) {
  cases[[length(cases)+1L]] <<- list(id=id,genos=scale(g),z=z,weights=w,maximum=maximum,minimum=minimum,top1=top1)
}
set.seed(9346)
for(i in 1:120) {
  n <- 2L + i %% 11L
  latent <- rnorm(32)
  genotypes <- matrix(rnorm(32*n),32,n) + (0.1 + (i%%8)/2)*latent
  z <- rnorm(n,sd=2+(i%%5))
  weights <- rnorm(n)
  if(i%%3==0L) z[seq_len(max(1L,n%/%3L))] <- NA_real_
  if(i%%7==0L) weights[n] <- NA_real_
  add(paste0("generated-",i),genotypes,z,weights,minimum=c(0,0.3,0.7,0.95)[1L+i%%4L],top1=i%%9L==0L)
}
orthogonal <- cbind(c(-1,-1,1,1),c(-1,1,-1,1))
correlated <- cbind(c(0,0,1,1,2,2),c(0,1,1,1,1,2),c(0,0,0,1,2,2),c(2,1,1,1,0,0))
add("zero-weights",correlated,c(1,2,3,4),c(0,0,0,0))
add("all-weight-NA",correlated,c(1,2,3,4),rep(NA_real_,4))
add("all-z-missing",correlated,rep(NA_real_,4),c(1,2,3,4))
add("too-many-missing",correlated,c(1,NA,NA,NA),c(1,2,3,4))
add("missing-exact-boundary",correlated,c(1,2,NA,NA),c(1,2,3,4),minimum=0)
add("low-quality",orthogonal,c(1,NA),c(1,2))
add("zero-r2-allowed-source-NaN",orthogonal,c(1,NA),c(1,2),minimum=0)
add("zero-weight-NaN-still-propagates",orthogonal,c(1,NA),c(1,0),minimum=0)
add("top1-first-squared-tie",correlated,c(1,2,3,4),c(2,-2,1,0),top1=TRUE)
add("top1-negative-largest",correlated,c(1,2,3,4),c(2,-3,1,0),top1=TRUE)
add("zero-score",correlated,rep(0,4),c(1,2,3,4))
add("extreme-source-P-zero",correlated,rep(1000,4),c(1,2,3,4))
add("singular-LD-positive-variance",cbind(c(0,1,1,2),c(0,1,1,2)),c(2,3),c(1,1))
add("singular-LD-zero-variance",cbind(c(0,1,1,2),c(0,1,1,2)),c(2,3),c(1,-1))
add("singular-LD-imputation",cbind(c(0,1,1,2),c(0,1,1,2)),c(2,NA),c(1,1))
add("one-marker",matrix(c(0,1,1,2),ncol=1),2,1)
records <- list()
for(case in cases) {
  environment <- new.env(parent=globalenv())
  environment$cur.genos <- case$genos
  environment$cur.Z <- case$z
  environment$wgt.matrix <- matrix(case$weights,ncol=1,dimnames=list(paste0("s",seq_along(case$weights)),if(case$top1) "top1" else "enet"))
  environment$cv.performance <- matrix(c(0.1,0.001),nrow=2,dimnames=list(c("rsq","pval"),colnames(environment$wgt.matrix)))
  environment$mod.best <- 1L;environment$w <- 1L
  environment$opt <- list(max_impute=case$maximum,min_r2pred=case$minimum,perm=0)
  environment$wgtlist <- data.frame(ID=case$id)
  environment$out.tbl <- data.frame(NSNP=NA_real_,NWGT=NA_real_,TWAS.Z=NA_real_,TWAS.P=NA_real_)
  environment$cur.FAIL <- FALSE
  error <- NULL
  transcript <- capture.output(tryCatch({
    eval(ldAssignment,environment)
    eval(zero,environment)
    eval(zeroCheck,environment)
    eval(top,environment)
    eval(association,environment)
    eval(emit,environment)
  },error=function(e) error <<- conditionMessage(e)))
  getValue <- function(name,default=NA_real_) if(exists(name,envir=environment,inherits=FALSE)) get(name,envir=environment) else default
  score <- environment$out.tbl$TWAS.Z
  p <- environment$out.tbl$TWAS.P
  status <- if(!is.null(error)) "ERROR" else if(environment$cur.FAIL || !is.finite(score) || !is.finite(p)) "NO_RESULT" else "COMPLETE"
  quality <- getValue("all.r2pred",rep(1,length(case$z)))
  records[[length(records)+1L]] <- c(id=case$id,n=length(case$z),maximum=case$maximum,minimum=case$minimum,top1=case$top1,
    z_in=formatValues(case$z),weights_in=formatValues(case$weights),ld=formatValues(as.vector(t(getValue("cur.LD")))),
    source_status=status,cur_fail=environment$cur.FAIL,z=formatValues(score),p=formatValues(p),
    logp=if(status=="COMPLETE") formatValues(log(2)+pnorm(abs(score),lower.tail=FALSE,log.p=TRUE)) else "NA",
    variance=formatValues(getValue("cur.twasr2pred")),completed_z=formatValues(environment$cur.Z),
    imputation_r2=formatValues(quality),effective_weights=formatValues(environment$wgt.matrix),
    zeroed_positions=paste(which(is.na(case$weights))-1L,collapse=","),
    diagnostic=gsub("[\t\r\n]+"," ",paste(c(transcript,error),collapse=" | ")))
}
output <- as.data.frame(do.call(rbind,records),stringsAsFactors=FALSE)
write.table(output,file=file.path(destination,"associations.tsv"),sep="\t",quote=FALSE,row.names=FALSE,na="NA")
writeLines(capture.output(sessionInfo()),file.path(destination,"SESSION.txt"))
writeLines(c(paste("allele_cases",nrow(grid)),paste("association_cases",nrow(output)),
             paste(names(table(output$source_status)),as.vector(table(output$source_status)))),file.path(destination,"COUNTS.txt"))
