# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Calls original package functions unchanged. HTTP diagnostics observe raw bytes.
args <- commandArgs(TRUE); stopifnot(length(args)>=3L)
home <- args[[1L]]; phase <- args[[2L]]
.libPaths(args[-seq_len(2L)],include.site=FALSE)
request <- jsonlite::fromJSON(file.path(home,"worker-request.json"),simplifyVector=FALSE)
version <- request$edition
events <- list(); messages_seen <- character(); warnings_seen <- character(); trace_bytes <- 0; trace_error <- ""; context <- phase
dir.create(file.path(home,"http"))
trace <- function(type,data){
    if(!type%in%0:4)return(invisible(NULL))
    if(!is.raw(data)){trace_error <<- "Non-raw Curl diagnostic data";return(invisible(NULL))}
    trace_bytes <<- trace_bytes+length(data)
    if(trace_bytes>request$maximum_http_bytes){trace_error <<- "Complete raw HTTP diagnostic capacity exceeded";return(invisible(NULL))}
    name <- sprintf("%06d-type%d.bin",length(events)+1L,type)
    writeBin(data,file.path(home,"http",name))
    events[[length(events)+1L]] <<- list(index=length(events)+1L,type=type,file=paste0("http/",name),bytes=length(data),context=context)
    invisible(NULL)
}
config <- list(verbose=TRUE,debugfunction=trace,accept_encoding="identity",followlocation=FALSE,timeout=request$http_timeout_seconds,connecttimeout=min(15L,request$http_timeout_seconds))
status <- "FAIL2_ORIGINAL_WORKER_FAILED"; detail <- ""; metadata <- NULL; cases <- list(); operation <- "initialization"
convert <- function(f){
    tokens <- unlist(f$values,use.names=FALSE)
    if(f$type=="CHARACTER")return(as.character(tokens))
    if(f$type=="LOGICAL")return(vapply(tokens,function(x)if(x=="NA")NA else if(x=="TRUE")TRUE else if(x=="FALSE")FALSE else stop("Invalid explicit logical token"),logical(1)))
    stopifnot(f$type=="NUMERIC")
    vapply(tokens,function(x)switch(x,"NA"=NA_real_,"NaN"=NaN,"Inf"=Inf,"+Inf"=Inf,"Infinity"=Inf,"+Infinity"=Inf,"-Inf"=-Inf,"-Infinity"=-Inf,as.numeric(x)),numeric(1))
}
typed <- function(frame){
    columns <- lapply(seq_along(frame),function(i){
        x <- frame[[i]]; values <- lapply(seq_along(x),function(j){
            value <- x[[j]]
            state <- if(is.numeric(value)&&is.nan(value))"NaN" else if(is.na(value))"NA" else if(is.numeric(value)&&is.infinite(value))if(value>0)"+Inf"else"-Inf" else "value"
            text <- if(state!="value")state else if(is.double(value))sprintf("%.17g",value)else as.character(value)
            list(state=state,text=text)
        })
        list(index=i,name=names(frame)[[i]],class=as.list(class(x)),storage_type=typeof(x),values=values)
    })
    list(rows=nrow(frame),row_names=as.list(rownames(frame)),columns=columns)
}
tryCatch(withCallingHandlers({
    stopifnot(identical(R.version.string,"R version 4.6.1 (2026-06-24 ucrt)"),requireNamespace("biomaRt",quietly=TRUE),identical(as.character(packageVersion("biomaRt")),version))
    # A source guard supplements complete installed-file hashes; no namespace assignment.
    source <- file.path(home,"source",version)
    expressions <- unlist(lapply(list.files(source,pattern="\\.R$",full.names=TRUE),function(p)as.list(parse(file=p))),recursive=FALSE)
    for(name in c("getBM",".listMarts","useDataset",".setResultColNames",".splitValues",".createFilterXMLchunk",".generateFilterXML",".processResults",".fetchHTMLresults",".submitQueryXML",".createHash")){
        definitions <- Filter(function(e)is.call(e)&&length(e)==3L&&as.character(e[[1L]])%in%c("<-","=")&&identical(e[[2L]],as.name(name)),expressions)
        stopifnot(length(definitions)==1L)
        expected <- eval(definitions[[1L]][[3L]],new.env(parent=baseenv()));actual <- getFromNamespace(name,"biomaRt")
        stopifnot(identical(body(expected),body(actual),ignore.srcref=TRUE),identical(formals(expected),formals(actual),ignore.srcref=TRUE))
    }
    if(phase=="metadata"){
        operation <- "service-metadata"
        e <- request$endpoint
        registry <- as.data.frame(getFromNamespace(".listMarts","biomaRt")(host=e$base_url,path="/biomart/martservice",port=e$port,includeHosts=TRUE,http_config=config,ensemblRedirect=FALSE,verbose=FALSE))
        write.table(registry,file.path(home,"original-registry.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
        selected <- registry[registry$biomart==e$mart,,drop=FALSE]
        stopifnot(nrow(selected)==1L,identical(selected$version[[1L]],e$registry_version),identical(selected$database[[1L]],e$database),identical(selected$vschema[[1L]],e$virtual_schema),identical(selected$path[[1L]],"/biomart/martservice"))
        mart <- methods::new("Mart",biomart=e$mart,host=paste0(e$base_url,":",e$port,"/biomart/martservice?redirect=no"),vschema=e$virtual_schema,http_config=config)
        mart <- biomaRt::useDataset(e$dataset,mart=mart)
        datasets <- biomaRt::listDatasets(mart);selected_dataset <- datasets[datasets$dataset==e$dataset,,drop=FALSE]
        stopifnot(nrow(selected_dataset)==1L,identical(selected_dataset$version[[1L]],e$assembly))
        write.table(datasets,file.path(home,"original-datasets.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
        attributes <- biomaRt::listAttributes(mart);filters <- biomaRt::listFilters(mart,what=c("name","description","options","fullDescription","filters","type","operation"))
        write.table(attributes,file.path(home,"original-attributes.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
        write.table(filters,file.path(home,"original-filters.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
        metadata <- list(endpoint=e,host=methods::slot(mart,"host"),attributes=as.list(attributes$name),filters=lapply(seq_len(nrow(filters)),function(i)list(name=filters$name[[i]],type=filters$type[[i]])))
        # Do not serialize the trace callback's mutable environment into the Mart.
        methods::slot(mart,"http_config") <- list()
        saveRDS(mart,file.path(home,"mart.rds"));jsonlite::write_json(metadata,file.path(home,"metadata.json"),auto_unbox=TRUE,pretty=TRUE)
    } else {
        stopifnot(phase=="queries")
        mart <- readRDS(file.path(home,"mart.rds"));methods::slot(mart,"http_config") <- config
        for(case in request$queries){
            context <- case$id;directory <- file.path(home,case$id);dir.create(directory)
            first <- length(messages_seen);first_warning <- length(warnings_seen)
            filters <- if(length(case$filters))vapply(case$filters,`[[`,character(1),"name")else ""
            values <- if(length(case$filters))lapply(case$filters,convert)else ""
            if(length(case$filters)==1L)values <- values[[1L]]
            component_warnings <- character()
            chunks <- withCallingHandlers(getFromNamespace(".generateFilterXML","biomaRt")(filters,values,mart),warning=function(w){component_warnings <<- c(component_warnings,conditionMessage(w));invokeRestart("muffleWarning")})
            result <- biomaRt::getBM(attributes=unlist(case$attributes,use.names=FALSE),filters=filters,values=values,mart=mart,uniqueRows=case$unique_rows,checkFilters=case$check_filters,bmHeader=FALSE,useCache=FALSE,verbose=TRUE)
            saveRDS(result,file.path(directory,"original-result.rds"));dput(result,file=file.path(directory,"original-result.R"))
            write.table(result,file.path(directory,"original-result.tsv"),sep="\t",quote=TRUE,row.names=TRUE,na="NA")
            jsonlite::write_json(typed(result),file.path(directory,"typed-result.json"),auto_unbox=TRUE,pretty=TRUE,null="null",na="null")
            # Original getBM calls base message from the biomaRt package domain.
            # gettext in that domain can escape Unicode under C locale; wire XML is separate.
            console <- lapply(request$expected_native_queries[[case$id]],function(xml){value <- NULL;withCallingHandlers(message(xml,domain="R-biomaRt"),message=function(m){value <<- conditionMessage(m);invokeRestart("muffleMessage")});value})
            report <- list(id=case$id,rows=nrow(result),columns=as.list(names(result)),messages=as.list(tail(messages_seen,length(messages_seen)-first)),warnings=as.list(tail(warnings_seen,length(warnings_seen)-first_warning)),original_filter_chunks=as.list(unname(chunks)),original_filter_warnings=as.list(component_warnings),native_console_model=console)
            jsonlite::write_json(report,file.path(directory,"original-report.json"),auto_unbox=TRUE,pretty=TRUE,null="null",na="null");cases[[length(cases)+1L]] <- report
        }
    }
    stopifnot(!nzchar(trace_error))
    status <- "PASS_ORIGINAL_OPERATION_ONLY"
},warning=function(w){warnings_seen <<- c(warnings_seen,conditionMessage(w));invokeRestart("muffleWarning")},message=function(m){messages_seen <<- c(messages_seen,conditionMessage(m));invokeRestart("muffleMessage")}),error=function(e){detail <<- conditionMessage(e);if(operation=="service-metadata"&&!nzchar(trace_error))status <<- "SKIP3_EXACT_SERVICE_UNAVAILABLE"})
packages <- lapply(sort(loadedNamespaces()),function(n)list(name=n,path=normalizePath(if(n=="base")find.package(n)else getNamespaceInfo(n,"path"),winslash="/")))
dlls <- lapply(getLoadedDLLs(),function(d)list(name=d[["name"]],path=d[["path"]]))
jsonlite::write_json(list(status=status,error=detail,trace_error=trace_error,phase=phase,edition=version,R=R.version.string,events=events,messages=as.list(messages_seen),warnings=as.list(warnings_seen),cases=cases,packages=packages,DLLs=unname(dlls),namespace_patched=FALSE),file.path(home,"worker-report.json"),auto_unbox=TRUE,pretty=TRUE,null="null",na="null")
writeLines(capture.output(sessionInfo()),file.path(home,"session-info.txt"))
quit(status=if(status=="PASS_ORIGINAL_OPERATION_ONLY")0L else if(status=="SKIP3_EXACT_SERVICE_UNAVAILABLE")3L else 2L)
