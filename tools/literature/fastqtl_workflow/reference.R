# Independent nominal linear-model checks on the synthetic, complete-covariate fixture.
# R lm uses double arithmetic; FastQTL uses float vectors and prints six significant digits.
args <- commandArgs(trailingOnly=TRUE)
stopifnot(length(args)==3L)
fixture_root <- args[1]; capture_root <- args[2]; output <- args[3]
stopifnot(!file.exists(output)); dir.create(output)
results <- list()
for (kind in c("GT","DS")) {
  base <- file.path(fixture_root,kind)
  v <- read.delim(gzfile(file.path(base,"genotypes.vcf.gz")),skip=4,check.names=FALSE,comment.char="",quote="")
  p <- read.delim(gzfile(file.path(base,"phenotypes.bed.gz")),check.names=FALSE,comment.char="",quote="")
  c <- read.delim(file.path(base,"covariates.txt"),check.names=FALSE,comment.char="",quote="")
  sample_ids <- names(p)[-(1:4)]
  stopifnot(setequal(sample_ids,names(v)[-(1:9)]),setequal(sample_ids,names(c)[-1]))
  covariates <- t(as.matrix(c[,sample_ids,drop=FALSE]));storage.mode(covariates)<-"double"
  actual <- read.delim(file.path(capture_root,paste0(tolower(kind),"-nominal-capture"),"worker","result.txt"),header=FALSE)
  for (i in seq_len(nrow(actual))) {
    row <- actual[i,]; gi <- match(row[[2]],v[[3]]); pi <- match(row[[1]],p[[4]])
    stopifnot(!is.na(gi),!is.na(pi),row[[3]]==v[[2]][gi]-(p[[2]][pi]+1))
    tokens <- as.character(v[gi,sample_ids])
    if(kind=="DS") x <- vapply(strsplit(tokens,":",fixed=TRUE),function(z) if(z[2]==".") NA_real_ else as.double(z[2]),0.0)
    else x <- vapply(strsplit(gsub("|","/",tokens,fixed=TRUE),"/",fixed=TRUE),function(z) if(any(z==".")) NA_real_ else sum(as.double(z)),0.0)
    observed <- x[!is.na(x)]; rounded <- floor(observed+.5); alt <- sum(rounded); ref <- 2*length(rounded)-alt
    stopifnot(row[[4]]==sum(if(ref>=alt) rounded>0 else rounded<2),row[[5]]==min(alt,ref),abs(row[[6]]-min(alt,ref)/(2*length(rounded)))<5e-6)
    y <- as.double(p[pi,sample_ids]);x[is.na(x)]<-mean(x,na.rm=TRUE);y[is.na(y)]<-mean(y,na.rm=TRUE)
    model <- summary(lm(y~x+covariates))$coefficients["x",]
    expected <- c(model[4],model[1],model[2]);observed_output <- as.numeric(row[7:9]);relative <- abs(observed_output-expected)/pmax(abs(expected),1e-300)
    stopifnot(all(is.finite(expected)),all(abs(observed_output-expected)<=1e-12+5e-5*abs(expected)))
    results[[length(results)+1L]] <- data.frame(kind=kind,phenotype=row[[1]],variant=row[[2]],statistic=c("P","slope","SE"),source=observed_output,R_lm=expected,relative_error=relative)
  }
}
all <- do.call(rbind,results);write.table(all,file.path(output,"comparisons.tsv"),sep="\t",row.names=FALSE,quote=FALSE)
writeLines(c(paste("PASS",nrow(all),"independent nominal statistics"),paste("Maximum relative error",format(max(all$relative_error),digits=17)),R.version.string),file.path(output,"RESULT.txt"))
cat(readLines(file.path(output,"RESULT.txt")),sep="\n")
