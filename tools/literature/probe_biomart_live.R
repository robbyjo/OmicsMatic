# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original public useMart/getBM against explicitly selected public Ensembl hosts.
args <- commandArgs(TRUE);stopifnot(length(args)>=6L)
home <- args[[1]];version <- args[[2]];host <- args[[3]];expected_assembly <- args[[4]]
.libPaths(args[-seq_len(4L)],include.site=FALSE)
stopifnot(requireNamespace("biomaRt",quietly=TRUE),identical(as.character(packageVersion("biomaRt")),version))
options(timeout=45)
status <- "SKIP3_LIVE_BIOMART_ANNOTATION_UNAVAILABLE";detail <- "";warnings_seen <- character();queries <- list();metadata <- list()
tryCatch(withCallingHandlers({
    marts <- biomaRt::listMarts(host=host)
    write.table(marts,file.path(home,"original-marts.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
    mart <- biomaRt::useMart(biomart="ENSEMBL_MART_ENSEMBL",dataset="hsapiens_gene_ensembl",host=host)
    datasets <- biomaRt::listDatasets(mart)
    write.table(datasets,file.path(home,"original-datasets.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
    selected <- datasets[datasets$dataset=="hsapiens_gene_ensembl",,drop=FALSE]
    stopifnot(nrow(selected)==1L,identical(selected$version[[1L]],expected_assembly))
    write.table(biomaRt::listAttributes(mart,what=c("name","description","fullDescription","page")),file.path(home,"original-attributes.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
    write.table(biomaRt::listFilters(mart,what=c("name","description","options","fullDescription","filters","type","operation")),file.path(home,"original-filters.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
    metadata <- list(requested_host=host,resolved_mart_host=methods::slot(mart,"host"),biomart=methods::slot(mart,"biomart"),dataset=methods::slot(mart,"dataset"),virtual_schema=methods::slot(mart,"vschema"),verified_dataset_assembly=selected$version[[1L]],mart_versions=marts$version)
    definitions <- list(identifiers=list(attributes=c("ensembl_gene_id","hgnc_symbol","gene_biotype","chromosome_name","start_position","end_position"),ids=c("ENSG00000141510","ENSG00000139618")),transcripts=list(attributes=c("ensembl_transcript_id","ensembl_gene_id","hgnc_symbol"),ids=c("ENSG00000141510","ENSG00000139618")),GO=list(attributes=c("ensembl_gene_id","go_id","name_1006","namespace_1003"),ids="ENSG00000141510"))
    for(name in names(definitions)){
        definition <- definitions[[name]]
        result <- biomaRt::getBM(attributes=definition$attributes,filters="ensembl_gene_id",values=definition$ids,mart=mart,uniqueRows=TRUE,bmHeader=FALSE,useCache=FALSE)
        saveRDS(result,file.path(home,paste0(name,".rds")));dput(result,file=file.path(home,paste0(name,".R")))
        write.table(result,file.path(home,paste0(name,".tsv")),sep="\t",quote=TRUE,row.names=FALSE,na="NA")
        stopifnot(nrow(result)>0L,all(result$ensembl_gene_id%in%definition$ids))
        if(name=="identifiers")stopifnot(setequal(result$ensembl_gene_id,definition$ids))
        queries[[name]] <- list(attributes=definition$attributes,input_public_gene_ids=definition$ids,returned_rows=nrow(result),returned_columns=colnames(result),returned_classes=unname(vapply(result,function(v)paste(class(v),collapse="/"),character(1))))
    }
    status <- "PASS_ORIGINAL_LIVE_BIOMART_PUBLIC_GENE_ANNOTATION_ONLY"
},warning=function(w){warnings_seen <<- c(warnings_seen,conditionMessage(w))}),error=function(e){detail <<- conditionMessage(e)})
jsonlite::write_json(list(status=status,error=detail,version=version,R=R.version.string,metadata=metadata,completed_queries=queries,warnings=unname(warnings_seen),public_gene_controls_only=TRUE,paper_reproduced=FALSE,Java_annotation_client_validated=FALSE),file.path(home,"COMPLETED.json"),auto_unbox=TRUE,pretty=TRUE,null="null",na="null")
writeLines(capture.output(sessionInfo()),file.path(home,"session-info.txt"))
quit(status=if(status=="PASS_ORIGINAL_LIVE_BIOMART_PUBLIC_GENE_ANNOTATION_ONLY")0L else 3L)
