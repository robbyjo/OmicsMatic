args <- commandArgs(TRUE)
directory <- args[1]
data <- read.delim(file.path(directory,"input.tsv"),check.names=FALSE,na.strings="NA")
output <- list()
for (label in unique(data$case)) {
  item <- data[data$case==label,]
  method <- item$method[1]
  if (item$encoding[1]=="P_VALUE") {
    q <- if(method=="supplied") item$input else stats::p.adjust(item$input,method=method)
    logq <- log(q)
    selected <- !is.na(q) & q<=.05
  } else {
    # Independent log-space extension; direct p.adjust(exp(logP)) loses
    # finite information below binary64 probability underflow.
    x <- item$input
    available <- which(!is.na(x)); order <- available[order(x[available],available)]
    n <- length(order); result <- rep(NA_real_,length(x))
    if(n>0) {
      sorted <- x[order]
      if(method %in% c("BH","BY")) {
        factor <- if(method=="BY") sum(1/seq_len(n)) else 1
        qsort <- rev(cummin(rev(sorted+log(factor*n/seq_len(n)))))
      } else if(method=="holm") qsort <- cummax(sorted+log(n-seq_len(n)+1))
      else if(method=="bonferroni") qsort <- sorted+log(n)
      else qsort <- sorted
      result[order] <- pmin(0,qsort)
    }
    logq <- result; q <- exp(logq)
    selected <- !is.na(logq) & logq<=log(.05)
  }
  formatValue <- function(x) ifelse(is.na(x),"NA",format(x,digits=17,scientific=TRUE,trim=TRUE))
  item$adjusted <- formatValue(q); item$log_adjusted <- formatValue(logq)
  item$selected <- ifelse(selected,"true","false")
  output[[length(output)+1]] <- item
}
result <- do.call(rbind,output)
result$input <- ifelse(is.na(result$input),"NA",format(result$input,digits=17,scientific=TRUE,trim=TRUE))
write.table(result,file.path(directory,"reference.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
writeLines(deparse(stats::p.adjust),file.path(directory,"R-p-adjust-source.R"))
cat(R.version.string,"\n",as.character(packageVersion("stats")),"\n")
