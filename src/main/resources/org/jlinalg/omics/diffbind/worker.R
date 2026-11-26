# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Calls the unmodified DiffBind 3.24.0 API. No input RDS or evaluated user R code.
suppressPackageStartupMessages(library(DiffBind))
suppressPackageStartupMessages(library(jsonlite))
stopifnot(as.character(getRversion()) == '4.6.2',
          as.character(packageVersion('DiffBind')) == '3.24.0',
          as.character(packageVersion('DESeq2')) == '1.54.0',
          as.character(packageVersion('edgeR')) == '5.0.0')
request <- read_json('effective.json', simplifyVector=TRUE)
s <- request$settings
set.seed(as.integer(s$seed))
warnings_seen <- list()
stage <- 'setup'
number <- function(x) vapply(x, function(z)
  if(is.nan(z)) 'NaN' else if(is.na(z)) 'NA' else if(is.infinite(z))
    if(z > 0) 'Inf' else '-Inf' else
    trimws(formatC(z, digits=17, format='g', decimal.mark='.')), character(1))
tables <- list()
table_out <- function(x, path, role) {
  if(is.null(x)) {
    if(path != 'report-selected.tsv') stop('Unexpected NULL complete-source table')
    connection <- file(path,open='wb');close(connection)
    tables[[path]] <<- list(role=role,rows=0L,columns=list(),source_null=TRUE)
    return(invisible(NULL))
  }
  x <- as.data.frame(x, check.names=FALSE)
  columns <- lapply(seq_along(x), function(i) list(ordinal=i, name=names(x)[[i]],
      type=if(is.numeric(x[[i]])) 'source-number-token' else 'source-text'))
  values <- lapply(x, function(col) if(is.numeric(col)) number(col) else as.character(col))
  names(values) <- names(x)
  # check.names=FALSE is essential: original reports have repeated sample names.
  write.table(as.data.frame(values, check.names=FALSE), path, sep='\t', quote=FALSE,
              row.names=FALSE, na='NA')
  tables[[path]] <<- list(role=role, rows=nrow(x), columns=columns, source_null=FALSE)
}
bits <- function(x) vapply(x, function(value)
    paste(rev(format(writeBin(as.double(value), raw(), size=8, endian='little'))), collapse=''), character(1))
projection_out <- function(x, path) {
  result <- data.frame(id=as.character(x[,1]), p_bits=bits(x[,2]),
                       padj_bits=bits(x[,3]), fold_bits=bits(x[,4]), check.names=FALSE)
  write.table(result, path, sep='\t', quote=FALSE, row.names=FALSE)
}
withCallingHandlers({
  metadata <- read.csv('inputs/samples.csv', stringsAsFactors=FALSE, check.names=FALSE)
  for(column in c('bamReads','bamControl','Peaks')) if(column %in% names(metadata))
    metadata[[column]] <- file.path('inputs',metadata[[column]])
  method <- if(s$engine == 'DESeq2') DBA_DESEQ2 else if(s$engine == 'edgeR') DBA_EDGER else stop('Unknown engine')
  config <- data.frame(AnalysisMethod=method, th=s$threshold, DataType=DBA_DATA_FRAME,
      RunParallel=FALSE, minQCth=s$mapq, fragmentSize=s$fragment_size,
      bCorPlot=FALSE, reportInit='diffbind', bUsePval=s$use_pvalue, design=TRUE,
      doBlacklist=FALSE, doGreylist=FALSE)
  stage <- 'construct'
  cat('SOURCE_STAGE',stage,'\n')
  object <- dba(sampleSheet=metadata, config=config, peakCaller='bed',
                bRemoveM=FALSE, bRemoveRandom=FALSE)
  stage <- 'count'
  cat('SOURCE_STAGE',stage,'\n')
  object <- dba.count(object, minOverlap=s$min_overlap, score=DBA_SCORE_READS,
      fragmentSize=s$fragment_size, summits=if(s$summits == -1) FALSE else s$summits,
      filter=s$count_filter, bRemoveDuplicates=s$remove_duplicates,
      bScaleControl=s$scale_controls, bSubControl=s$subtract_controls,
      mapQCth=s$mapq, minCount=s$min_count, bLog=FALSE,
      bUseSummarizeOverlaps=TRUE, bParallel=FALSE)
  table_out(dba.peakset(object,bRetrieve=TRUE,numCols=ncol(object$binding),
            DataType=DBA_DATA_FRAME),'counted-peaks.tsv','complete-source-counted-binding')
  saveRDS(object,'counted-object.rds',compress='xz',version=3)
  stage <- 'normalize'
  cat('SOURCE_STAGE',stage,'\n')
  normalization <- switch(s$normalization, LIB=DBA_NORM_LIB, RLE=DBA_NORM_RLE,
                           TMM=DBA_NORM_TMM, stop('Unknown normalization'))
  library_size <- switch(s$library_size, FULL=DBA_LIBSIZE_FULL,
                          PEAKREADS=DBA_LIBSIZE_PEAKREADS, stop('Unknown library size'))
  object <- dba.normalize(object,method=method,normalize=normalization,
      library=library_size,background=FALSE,spikein=FALSE,offsets=FALSE,libFun=mean)
  writeLines(capture.output(dput(object$norm)),'normalization.R.txt')
  stage <- 'contrast'
  cat('SOURCE_STAGE',stage,'\n')
  # The Java reader constructs this formula from an allowlisted ordered factor list.
  design <- paste0('~',paste(s$design,collapse='+'))
  object <- dba.contrast(object,design=design,
      contrast=c(s$contrast_factor,s$numerator,s$denominator),minMembers=s$min_members)
  stage <- 'analyze'
  cat('SOURCE_STAGE',stage,'\n')
  object <- dba.analyze(object,method=method,bBlacklist=FALSE,bGreylist=FALSE,
                       bReduceObjects=FALSE,bParallel=FALSE)
  saveRDS(object,'model-object.rds',compress='xz',version=3)
  stage <- 'reports'
  cat('SOURCE_STAGE',stage,'\n')
  for(item in list(list(name='report-all.tsv',threshold=1,fold=0),
                   list(name='report-selected.tsv',threshold=s$threshold,fold=s$test_fold))) {
    report <- dba.report(object,contrast=1,method=method,th=item$threshold,
        bUsePval=s$use_pvalue,fold=item$fold,bNormalized=TRUE,bFlip=FALSE,precision=0,
        bCounts=TRUE,bCalled=TRUE,bCalledDetail=TRUE,DataType=DBA_DATA_FRAME)
    table_out(report,item$name,'complete-source-report-including-repeated-headers')
  }
  if(s$engine == 'DESeq2') {
    # Independent upstream results feed the Java projection audit. The expected
    # rows come from the COMPLETE original namespace function on this fitted model.
    dds <- dba.analyze(object,bRetrieveAnalysis=DBA_DESEQ2)
    if(any(!S4Vectors::mcols(dds)$betaConv,na.rm=TRUE)) stop('DESeq2 contains nonconverged fitted rows')
    contrast <- object$contrasts[[1]]
    for(shrink in c(FALSE,TRUE)) for(lfc in unique(c(0,s$test_fold))) {
      de <- DESeq2::results(dds,contrast=contrast$contrast,lfcThreshold=lfc)
      if(shrink) de <- suppressMessages(DESeq2::lfcShrink(dds,contrast=contrast$contrast,
                                      lfcThreshold=lfc,res=de,type='ashr'))
      tag <- paste0('projection-',if(shrink)'shrink'else'unshrunk','-',number(lfc))
      input <- cbind(as.numeric(rownames(de)),de$pvalue,de$padj,de$log2FoldChange)
      expected <- get('pv.DESeq2ContrastResults',asNamespace('DiffBind'))(object,contrast,shrink=shrink,lfc=lfc)$de
      projection_out(input,paste0(tag,'-input.tsv'))
      projection_out(expected,paste0(tag,'-expected.tsv'))
    }
    table_out(cbind(id=rownames(dds),as.data.frame(DESeq2::results(dds,
        contrast=contrast$contrast,lfcThreshold=0))),'DESeq2-results-all.tsv','complete-unshrunk-DESeq2-results')
  }
  stage <- 'plots'
  cat('SOURCE_STAGE',stage,'\n')
  png('PCA.png',width=960,height=720,res=100,type='cairo')
  pca <- dba.plotPCA(object,attributes=DBA_CONDITION,label=DBA_ID,components=1:2)
  if(inherits(pca,'ggplot')) print(pca)
  dev.off()
  png('heatmap.png',width=960,height=720,res=100,type='cairo')
  heatmap <- dba.plotHeatmap(object,attributes=DBA_ID,ColAttributes=DBA_CONDITION,
      RowAttributes=DBA_CONDITION,maxSites=nrow(object$binding),correlations=TRUE)
  dev.off()
  stopifnot(is.matrix(heatmap),nrow(heatmap)==nrow(metadata),
            ncol(heatmap)==nrow(metadata),!anyDuplicated(rownames(heatmap)),
            setequal(rownames(heatmap),metadata$SampleID),
            setequal(colnames(heatmap),metadata$SampleID),all(is.finite(heatmap)))
  table_out(cbind(sample=rownames(heatmap),as.data.frame(heatmap,check.names=FALSE)),
            'heatmap-correlations.tsv','complete-source-sample-correlation-matrix')
  write_json(list(status='COMPLETE_ORIGINAL_DIFFBIND_WORKFLOW_PROCESS_OUTPUTS',
      peaks=nrow(object$binding),samples=ncol(object$class),engine=s$engine,
      design=design,contrast=contrast <- c(s$contrast_factor,s$numerator,s$denominator),
      tables=tables,report_fold_changes_test=TRUE,whole_package_admitted=FALSE),
      'RESULTS.json',auto_unbox=TRUE,pretty=TRUE,null='null')
  write_json(warnings_seen,'SOURCE-WARNINGS.json',auto_unbox=TRUE,pretty=TRUE,null='null')
  writeLines(capture.output(sessionInfo()),'session.txt')
  writeLines(readLines('/proc/self/maps'),'R-MAPS.txt')
  cat('SOURCE_COMPLETE',s$engine,'peaks',nrow(object$binding),'samples',ncol(object$class),'\n')
},warning=function(w) {
  warnings_seen[[length(warnings_seen)+1L]] <<- list(stage=stage,message=conditionMessage(w))
})
