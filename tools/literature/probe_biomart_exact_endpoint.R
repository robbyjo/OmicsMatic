# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original Mart constructor/useDataset with exact registry metadata; caller diagnostics do not patch source functions.
args <- commandArgs(TRUE);stopifnot(length(args)>=6L)
home <- args[[1]];version <- args[[2]];host <- args[[3]];assembly <- args[[4]]
.libPaths(args[-seq_len(4L)],include.site=FALSE)
stopifnot(requireNamespace("biomaRt",quietly=TRUE),identical(as.character(packageVersion("biomaRt")),version))
events <- list();messages <- character();warnings_seen <- character();trace_bytes <- 0;trace_error <- "";dir.create(file.path(home,"http"))
trace <- function(type,data){
    if(!type%in%0:4)return(invisible(NULL))
    if(!is.raw(data)){trace_error <<- "Curl diagnostic data was not raw";return(invisible(NULL))}
    trace_bytes <<- trace_bytes+length(data)
    if(trace_bytes>33554432){trace_error <<- "Complete diagnostic capacity exceeded";return(invisible(NULL))}
    name <- sprintf("%06d-type%d.bin",length(events)+1L,type)
    writeBin(data,file.path(home,"http",name))
    events[[length(events)+1L]] <<- list(index=length(events)+1L,type=type,file=paste0("http/",name),bytes=length(data))
    invisible(NULL)
}
config <- list(verbose=TRUE,debugfunction=trace,accept_encoding="identity",followlocation=FALSE,timeout=45L,connecttimeout=15L)
status <- "SKIP3_EXACT_BIOMART_ENDPOINT_UNAVAILABLE";detail <- "";metadata <- list();result <- NULL
tryCatch(withCallingHandlers({
    registry <- as.data.frame(getFromNamespace(".listMarts","biomaRt")(host=host,path="/biomart/martservice",port=443,includeHosts=TRUE,http_config=config,ensemblRedirect=FALSE,verbose=FALSE))
    write.table(registry,file.path(home,"original-registry.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
    selected_mart <- registry[registry$biomart=="ENSEMBL_MART_ENSEMBL",,drop=FALSE]
    stopifnot(nrow(selected_mart)==1L,selected_mart$path[[1L]]=="/biomart/martservice")
    mart <- methods::new("Mart",biomart="ENSEMBL_MART_ENSEMBL",host=paste0(host,":443/biomart/martservice?redirect=no"),vschema=selected_mart$vschema[[1L]],http_config=config)
    mart <- biomaRt::useDataset(dataset="hsapiens_gene_ensembl",mart=mart)
    datasets <- biomaRt::listDatasets(mart)
    selected <- datasets[datasets$dataset=="hsapiens_gene_ensembl",,drop=FALSE]
    stopifnot(nrow(selected)==1L,identical(selected$version[[1L]],assembly))
    metadata <- list(host=methods::slot(mart,"host"),mart=methods::slot(mart,"biomart"),dataset=methods::slot(mart,"dataset"),virtual_schema=methods::slot(mart,"vschema"),assembly=selected$version[[1L]],original_Mart_constructor_and_useDataset_profile=TRUE,HTTP_redirects_disabled=TRUE)
    result <- biomaRt::getBM(attributes=c("ensembl_gene_id","hgnc_symbol","gene_biotype","chromosome_name","start_position","end_position"),filters="ensembl_gene_id",values=c("ENSG00000141510","ENSG00000139618"),mart=mart,uniqueRows=TRUE,bmHeader=FALSE,useCache=FALSE,verbose=TRUE)
    stopifnot(!nzchar(trace_error),nrow(result)>0L,setequal(result$ensembl_gene_id,c("ENSG00000141510","ENSG00000139618")))
    saveRDS(result,file.path(home,"original-result.rds"));dput(result,file=file.path(home,"original-result.R"))
    write.table(result,file.path(home,"original-result.tsv"),sep="\t",quote=TRUE,row.names=FALSE,na="NA")
    status <- "PASS_ORIGINAL_EXACT_ENDPOINT_QUERY_AND_UNMODIFIED_CURL_TRACE"
},warning=function(w){warnings_seen <<- c(warnings_seen,conditionMessage(w))},message=function(m){messages <<- c(messages,conditionMessage(m))}),error=function(e){detail <<- conditionMessage(e)})
jsonlite::write_json(list(status=status,error=detail,trace_error=trace_error,version=version,R=R.version.string,metadata=metadata,events=events,messages=unname(messages),warnings=unname(warnings_seen),returned_rows=if(!is.null(result))nrow(result)else NULL,source_namespace_monkey_patched=FALSE,annotation_client_admitted=FALSE),file.path(home,"COMPLETED.json"),auto_unbox=TRUE,pretty=TRUE,null="null",na="null")
writeLines(capture.output(sessionInfo()),file.path(home,"session-info.txt"))
quit(status=if(status=="PASS_ORIGINAL_EXACT_ENDPOINT_QUERY_AND_UNMODIFIED_CURL_TRACE")0L else 3L)
