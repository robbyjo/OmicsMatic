# Original unmodified tximport is an optional reference oracle, never the Java engine.
args <- commandArgs(trailingOnly=TRUE)
.libPaths(c(args[1], .libPaths()))
library(tximport)
stopifnot(as.character(packageVersion('tximport')) == '1.40.0')
cases <- read.delim(args[2], check.names=FALSE, colClasses='character')
flat <- function(x) paste(format(as.vector(t(x)), digits=17, scientific=TRUE, trim=TRUE), collapse=',')
results <- list()
for (i in seq_len(nrow(cases))) {
  case <- cases[i,]
  files <- strsplit(case$files, ';', fixed=TRUE)[[1]]
  names(files) <- strsplit(case$samples, ',', fixed=TRUE)[[1]]
  mapping <- if (case$needs_map == 'true') read.delim(case$mapping, colClasses='character') else NULL
  importer <- function(path) {
    data <- read.delim(path, check.names=FALSE, colClasses='character')
    numeric <- intersect(names(data), c('NumReads','TPM','EffectiveLength','est_counts','tpm','eff_length','expected_count','effective_length','counts','abundance','length'))
    for (name in numeric) data[[name]] <- as.numeric(data[[name]])
    data
  }
  options <- list(files=files,type=if(case$format=='tsv') 'none' else case$format,
    txOut=case$level=='transcript',tx2gene=mapping,countsFromAbundance=case$mode,
    ignoreTxVersion=case$policy=='strip-version',ignoreAfterBar=case$policy=='strip-bar',
    dropInfReps=TRUE,importer=importer)
  if (case$format=='tsv') options <- c(options,list(txIdCol='transcript',countsCol='counts',abundanceCol='abundance',lengthCol='length'))
  result <- do.call(tximport, options)
  results[[i]] <- data.frame(case=case$case,expected_features=paste(rownames(result$counts),collapse=','),
    expected_counts=flat(result$counts),expected_abundance=flat(result$abundance),expected_length=flat(result$length))
}
write.table(do.call(rbind,results),file=args[3],sep='\t',quote=FALSE,row.names=FALSE)
print(sessionInfo())
