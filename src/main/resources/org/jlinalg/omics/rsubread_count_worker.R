# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Closed typed argument wire. No user R expression, source patch or install is accepted.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==3L)
source(file.path(args[1L],"rsubread_runtime_guard.R"),local=TRUE)
fun<-tryCatch(rsubread_runtime_guard(args[2L],file.path(args[1L],"reference"),getwd()),error=function(e){
    writeLines(c(class(e),conditionMessage(e)),"original-error.txt");writeLines("SKIP3_ORIGINAL_RSUBREAD_RUNTIME_UNAVAILABLE","worker-status.txt");quit(save="no",status=3L)})
writeLines("PASS_ORIGINAL_RSUBREAD_RUNTIME_GUARDS","worker-status.txt")
tryCatch({
    rows<-strsplit(readLines(args[3L],warn=TRUE),"\t",fixed=TRUE)
    allowed<-c("files","annot.ext","isGTFAnnotationFile","GTF.featureType","GTF.attrType","GTF.attrType.extra","genome","tmpDir","isPairedEnd","countReadPairs","strandSpecific",
        "useMetaFeatures","allowMultiOverlap","countMultiMappingReads","fraction","ignoreDup","juncCounts","isLongRead","requireBothEndsMapped","checkFragLength","countChimericFragments","autosort","primaryOnly","splitOnly","nonSplitOnly","largestOverlap","minOverlap","minMQS","minFragLength","maxFragLength","nthreads","fracOverlap","fracOverlapFeature")
    arguments<-list()
    for(row in rows) {
        stopifnot(length(row)>=3L,row[1L]%in%allowed,!row[1L]%in%names(arguments))
        tokens<-if(length(row)>2L)row[-(1:2)]else character()
        value<-switch(row[2L],STRING={stopifnot(all(startsWith(tokens,"S")));vapply(substring(tokens,2L),rsubread_decode_path,"",USE.NAMES=FALSE)},
            BOOL={stopifnot(all(tokens%in%c("0","1")));tokens=="1"},
            INT={stopifnot(all(grepl("^[0-9]+$",tokens)));as.integer(tokens)},
            DOUBLE={value<-as.double(tokens);stopifnot(all(is.finite(value)));value},
            NULL={stopifnot(identical(tokens,"NULL"));NULL},stop("Unknown argument type"))
        arguments[row[1L]]<-list(value)
    }
    stopifnot(setequal(names(arguments),allowed),identical(normalizePath(arguments$tmpDir),normalizePath(getwd())),all(is.finite(arguments$strandSpecific)),
              length(arguments$files)>0L,length(arguments$isPairedEnd)==length(arguments$files),length(arguments$countReadPairs)==length(arguments$files),
              length(arguments$strandSpecific)==length(arguments$files),!arguments$isLongRead)
    saveRDS(arguments,"arguments.rds",version=3L);dput(arguments,file="arguments.R")
    source(file.path(args[1L],"rsubread_result_wire.R"),local=TRUE)
    result<-withCallingHandlers(do.call(fun,arguments),warning=function(w)cat("ORIGINAL_WARNING:",conditionMessage(w),"\n",file=stderr()))
    saveRDS(result,"complete-original-result.rds",version=3L);dput(result,file="complete-original-result.R")
    rsubread_write_result(result,"values")
    for(member in c("counts","annotation","stat","counts_junction"))if(!is.null(result[[member]]))
        write.table(result[[member]],paste0(member,".tsv"),sep="\t",quote=TRUE,row.names=TRUE,col.names=NA,na="NA")
    writeLines("COMPLETE_ORIGINAL_RSUBREAD_COUNT_LIST","analysis-status.txt")
},error=function(e){writeLines(c(class(e),conditionMessage(e),capture.output(sys.calls())),"original-error.txt");writeLines("FAIL2_ORIGINAL_COUNTING_NOT_ADMITTED","analysis-status.txt");quit(save="no",status=2L)})
