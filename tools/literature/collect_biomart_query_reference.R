# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Exercise the unmodified installed original public getBM against a synthetic protocol server.
args <- commandArgs(TRUE); stopifnot(length(args)>=5L)
home <- args[[1]]; setup <- args[[2]]; version <- args[[3]]; service <- args[[4]]
.libPaths(args[-seq_len(4L)],include.site=FALSE)
profile <- jsonlite::fromJSON(file.path(setup,version,"R-RESULT.json"),simplifyVector=FALSE)
.libPaths(unlist(profile$libraries,use.names=FALSE),include.site=FALSE)
stopifnot(requireNamespace("biomaRt",quietly=TRUE),identical(as.character(packageVersion("biomaRt")),version))
cases <- jsonlite::fromJSON(file.path(home,"cases.json"),simplifyVector=FALSE)
attributes <- data.frame(name=c("gene","symbol","metric"),description=c("Gene ID","Gene symbol","Numeric metric"),fullDescription="",page="feature_page",stringsAsFactors=FALSE)
filters <- data.frame(name=c("gene","secondary","with_flag","numeric_filter","logical_text"),description="Synthetic filter",options="",fullDescription="",filters="",type=c("text","text","boolean_list","text","text"),operation="=",stringsAsFactors=FALSE)
convert <- function(f){
    tokens <- unlist(f$values,use.names=FALSE)
    if(f$type=="CHARACTER")return(as.character(tokens))
    if(f$type=="LOGICAL")return(vapply(tokens,function(x)if(x=="NA")NA else if(x=="TRUE")TRUE else if(x=="FALSE")FALSE else stop("Invalid explicit logical token"),logical(1)))
    stopifnot(f$type=="NUMERIC")
    vapply(tokens,function(x)switch(x,"NA"=NA_real_,"NaN"=NaN,"Inf"=Inf,"Infinity"=Inf,"-Inf"=-Inf,"-Infinity"=-Inf,as.numeric(x)),numeric(1))
}
summaries <- list()
for(case in cases){
    id <- case$id; path <- file.path(home,paste0(version,"-",id));dir.create(path)
    selected <- if(is.null(case$attributes))c("gene","symbol")else unlist(case$attributes,use.names=FALSE)
    dataset <- if(is.null(case$dataset))"synthetic_dataset"else case$dataset
    mart <- methods::new("Mart",biomart="synthetic_mart",host=paste0(service,"/",version,"/",id),dataset=dataset,attributes=attributes,filters=filters)
    fs <- if(length(case$filters))vapply(case$filters,`[[`,character(1),"name")else ""
    values <- if(length(case$filters))lapply(case$filters,convert)else ""
    if(length(case$filters)==1L)values <- values[[1L]]
    component_warnings <- character();component_error <- NULL;component <- NULL
    tryCatch(withCallingHandlers({component <- getFromNamespace(".generateFilterXML","biomaRt")(fs,values,mart)},warning=function(w){component_warnings <<- c(component_warnings,conditionMessage(w));invokeRestart("muffleWarning")}),error=function(e){component_error <<- conditionMessage(e)})
    source_warnings <- character();error <- NULL;result <- NULL
    tryCatch(withCallingHandlers({result <- biomaRt::getBM(attributes=selected,filters=fs,values=values,mart=mart,uniqueRows=case$unique_rows,checkFilters=case$check_filters,bmHeader=FALSE,useCache=FALSE)},warning=function(w){source_warnings <<- c(source_warnings,conditionMessage(w));invokeRestart("muffleWarning")}),error=function(e){error <<- conditionMessage(e)})
    if(!is.null(result)){saveRDS(result,file.path(path,"original-result.rds"));dput(result,file=file.path(path,"original-result.R"))}
    output <- list(id=id,package_version=version,R=R.version.string,status=if(is.null(error))"PASS_ORIGINAL_PUBLIC_GETBM_PROTOCOL_ONLY"else"ORIGINAL_GETBM_ERROR",error=error,source_warnings=unname(source_warnings),component_error=component_error,component_warnings=unname(component_warnings),original_filter_XML=unname(component),returned_columns=if(!is.null(result))colnames(result)else NULL,returned_classes=if(!is.null(result))unname(vapply(result,function(v)paste(class(v),collapse="/"),character(1)))else NULL,returned_rows=if(!is.null(result))nrow(result)else NULL,annotation_provenance="synthetic-protocol-server-only-no-biological-annotation")
    jsonlite::write_json(output,file.path(path,"RESULT.json"),auto_unbox=TRUE,pretty=TRUE,null="null",na="null")
    summaries[[id]] <- output
}
jsonlite::write_json(list(status="COMPLETED_ORIGINAL_PUBLIC_GETBM_PROTOCOL_CONTROLS",version=version,cases=summaries,real_gene_annotation_validated=FALSE),file.path(home,paste0(version,"-COMPLETED.json")),auto_unbox=TRUE,pretty=TRUE,null="null",na="null")
writeLines(capture.output(sessionInfo()),file.path(home,paste0(version,"-session-info.txt")))
