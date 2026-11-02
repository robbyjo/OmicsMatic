# Independent base-R calculation of the PUBLISHED FUMA expression method.
# This is not an original FUMA preprocessing script or historical GTEx resource.
args <- commandArgs(trailingOnly=TRUE)
stopifnot(length(args)==1L, !file.exists(args[1]))
dir.create(args[1], recursive=FALSE)
destination <- normalizePath(args[1], winslash="/", mustWork=TRUE)
options(digits=17)
set.seed(723019)
writeLines(R.version.string, file.path(destination,"R-version.txt"))
writeLines(capture.output(sessionInfo()), file.path(destination,"sessionInfo.txt"))
fmt <- function(x) sprintf("%.17g", x)
table <- function(x,path) write.table(x,path,sep="\t",quote=FALSE,row.names=FALSE,col.names=TRUE,na="NA")
for(n in c(3L,4L,5L,63L,64L,65L,129L,200L)) for(subset in c(FALSE,TRUE)) {
    name <- paste0(if(subset)"subset-" else "complete-",n)
    folder <- file.path(destination,name);dir.create(folder)
    selected <- if(subset && n>4L) which(seq_len(n) %% 3L != 0L) else seq_len(n)
    groups <- paste0("L",(seq_along(selected)-1L) %% min(3L,length(selected)))
    labels <- rev(unique(groups))
    ids <- paste0("s",seq_len(n))
    genes <- paste0("ENSG",seq_len(12L),".7")
    raw <- matrix(rexp(12L*n, rate=.04), nrow=12L)
    raw[1,] <- 0
    raw[2,] <- 1
    raw[3,] <- 1+.Machine$double.eps
    raw[4,] <- 1-.Machine$double.eps/2
    raw[5,] <- 50
    raw[6,] <- 100
    raw[7,] <- .Machine$double.xmax
    raw[8,] <- .Machine$double.xmin
    raw[9,] <- rep(c(0,3,7,50,51), length.out=n)
    raw[10,] <- rep(c(0,2), length.out=n)
    source <- data.frame(gene=genes, stringsAsFactors=FALSE)
    for(i in seq_len(n)) source[[ids[i]]] <- fmt(raw[,i])
    table(source,file.path(folder,"input.tsv"))
    sample <- data.frame(sample=ids[selected],label=groups,stringsAsFactors=FALSE)
    table(sample[rev(seq_len(nrow(sample))),,drop=FALSE],file.path(folder,"samples.tsv"))
    table(data.frame(label=labels),file.path(folder,"labels.tsv"))
    result <- list()
    for(i in seq_len(nrow(raw))) {
        values <- raw[i,selected]
        transformed <- log2(pmin(values,50)+1)
        center <- mean(transformed)
        centered <- transformed-center
        means <- vapply(labels,function(g)mean(values[groups==g]),numeric(1))
        logs <- vapply(labels,function(g)mean(transformed[groups==g]),numeric(1))
        norms <- vapply(labels,function(g)mean(centered[groups==g]),numeric(1))
        row <- c(gene=genes[i],disposition=if(any(means>1))"RETAINED" else "NOT_EXPRESSED",capped=as.character(sum(values>50)),sample_center=fmt(center),label_average=fmt(mean(logs)))
        for(j in seq_along(labels)) row <- c(row,setNames(fmt(means[j]),paste0("raw:",labels[j])))
        for(j in seq_along(labels)) row <- c(row,setNames(fmt(logs[j]),paste0("log2:",labels[j])))
        for(j in seq_along(labels)) row <- c(row,setNames(fmt(norms[j]),paste0("norm:",labels[j])))
        result[[i]] <- row
    }
    table(as.data.frame(do.call(rbind,result),stringsAsFactors=FALSE),file.path(folder,"expected.tsv"))
}
writeLines(c("Independent base-R implementation of the documented FUMA method.",
    "No original FUMA preprocessing runtime or historical resource equivalence claim.",
    "16 complete/subset cases;12 genes per case; explicit ordered labels and sample selections.",
    "Raw means BEFORE cap; cap50; log2(x+1); selected-sample center; per-label averages; unweighted Average."),
    file.path(destination,"SCOPE.txt"))
cat("Completed independent published-method controls:16 cases,192 gene rows\n")
