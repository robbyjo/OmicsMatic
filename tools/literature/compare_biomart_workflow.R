# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent direct public getBM calls, followed by complete dataframe/RDS/typed-state comparisons.
args <- commandArgs(TRUE);stopifnot(length(args)>=4L)
request_path <- args[[1L]];home <- args[[2L]]
.libPaths(args[-seq_len(2L)],include.site=FALSE)
r <- jsonlite::fromJSON(request_path,simplifyVector=FALSE);e <- r$endpoint
stopifnot(requireNamespace("biomaRt",quietly=TRUE),identical(as.character(packageVersion("biomaRt")),r$edition))
# Original .listMarts flattens its options with c(); a diagnostic callback keeps
# this a typed list rather than coercing numeric Curl options to character.
config <- list(followlocation=FALSE,timeout=60L,connecttimeout=15L,accept_encoding="identity",debugfunction=function(type,data)invisible(NULL))
registry <- as.data.frame(getFromNamespace(".listMarts","biomaRt")(host=e$base_url,path="/biomart/martservice",port=e$port,includeHosts=TRUE,http_config=config,ensemblRedirect=FALSE))
selected <- registry[registry$biomart==e$mart,,drop=FALSE]
stopifnot(nrow(selected)==1L,selected$version[[1L]]==e$registry_version,selected$database[[1L]]==e$database,selected$vschema[[1L]]==e$virtual_schema)
mart <- methods::new("Mart",biomart=e$mart,host=paste0(e$base_url,":",e$port,"/biomart/martservice?redirect=no"),vschema=e$virtual_schema,http_config=config)
mart <- biomaRt::useDataset(e$dataset,mart)
datasets <- biomaRt::listDatasets(mart);stopifnot(datasets$version[datasets$dataset==e$dataset]==e$assembly)
convert <- function(f){
    x <- unlist(f$values,use.names=FALSE)
    switch(f$type,CHARACTER=as.character(x),NUMERIC=as.numeric(x),LOGICAL=as.logical(x),stop("Unknown explicit input type"))
}
reports <- list()
for(q in r$queries){
    filters <- if(length(q$filters))vapply(q$filters,`[[`,character(1),"name")else ""
    values <- if(length(q$filters))lapply(q$filters,convert)else ""
    if(length(q$filters)==1L)values <- values[[1L]]
    expected <- biomaRt::getBM(attributes=unlist(q$attributes,use.names=FALSE),filters=filters,values=values,mart=mart,uniqueRows=q$unique_rows,checkFilters=q$check_filters,useCache=FALSE,bmHeader=FALSE)
    directory <- file.path(home,q$id);dir.create(directory)
    saveRDS(expected,file.path(directory,"independent-original.rds"));dput(expected,file=file.path(directory,"independent-original.R"))
    actual <- readRDS(file.path(r$out,"results",q$id,"original-result.rds"))
    stopifnot(identical(expected,actual))
    typed <- jsonlite::fromJSON(file.path(r$out,"results",q$id,"typed-result.json"),simplifyVector=FALSE)
    stopifnot(typed$rows==nrow(expected),identical(as.character(unlist(typed$row_names,use.names=FALSE)),rownames(expected)),length(typed$columns)==ncol(expected))
    for(i in seq_along(expected)){
        x <- expected[[i]];col <- typed$columns[[i]];stopifnot(col$index==i,col$name==names(expected)[[i]],identical(unlist(col$class,use.names=FALSE),class(x)),col$storage_type==typeof(x),length(col$values)==length(x))
        for(j in seq_along(x)){
            value <- col$values[[j]]
            if(is.numeric(x)&&is.nan(x[[j]]))stopifnot(value$state=="NaN",value$text=="NaN")
            else if(is.na(x[[j]]))stopifnot(value$state=="NA",value$text=="NA")
            else if(is.numeric(x)&&is.infinite(x[[j]]))stopifnot(value$state==if(x[[j]]>0)"+Inf"else"-Inf")
            else {stopifnot(value$state=="value");if(is.double(x))stopifnot(identical(as.double(value$text),x[[j]]))else stopifnot(identical(value$text,as.character(x[[j]])))}
        }
    }
    reports[[length(reports)+1L]] <- list(id=q$id,complete_original_dataframe_identical=TRUE,typed_states_complete=TRUE,rows=nrow(expected),columns=as.list(names(expected)),classes=as.list(vapply(expected,typeof,character(1))))
}
jsonlite::write_json(list(status="PASS_COMPLETE_INDEPENDENT_ORIGINAL_DATAFRAMES_AND_TYPED_STATES",edition=r$edition,cases=reports),file.path(home,"COMPLETED.json"),auto_unbox=TRUE,pretty=TRUE,null="null")
writeLines(capture.output(sessionInfo()),file.path(home,"session-info.txt"))
