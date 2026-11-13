# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(trailingOnly=TRUE);stopifnot(length(args)>=3L)
out <- args[1];source_root <- args[2];.libPaths(args[-c(1,2)],include.site=FALSE)
stage <- "runtime";warnings_seen <- character();cases <- list()
unavailable <- function(message)stop(structure(list(message=message,call=NULL),class=c("runtime_unavailable","error","condition")))
write_json <- function(x,p)jsonlite::write_json(x,p,pretty=TRUE,auto_unbox=TRUE,digits=NA,na="string",null="null")
token <- function(v)if(is.nan(v))"NaN"else if(is.na(v))"NA"else if(is.infinite(v))if(v>0)"Inf"else"-Inf"else sprintf("%.17g",v)
tokens <- function(x)as.list(vapply(as.numeric(x),token,character(1)))
atomic_table <- function(d,path){
 atomic <- d[,!vapply(d,is.list,logical(1)),drop=FALSE]
 columns <- lapply(names(atomic),function(name){x <- atomic[[name]];numeric <- is.numeric(x);list(name=name,tokens=if(numeric)tokens(x)else as.list(as.character(x)),kinds=as.list(vapply(seq_along(x),function(i)if(numeric&&is.nan(x[i]))"NaN"else if(is.na(x[i]))"NA"else if(numeric&&is.infinite(x[i]))if(x[i]>0)"positive-infinity"else"negative-infinity"else"value",character(1))))})
 write_json(list(rows=nrow(atomic),columns=columns),paste0(path,".json"));write.table(atomic,paste0(path,".tsv"),sep="\t",quote=FALSE,row.names=FALSE,na="NA");atomic
}
read_tsv <- function(name)read.delim(file.path(out,"inputs",name),header=TRUE,sep="\t",quote="",comment.char="",check.names=FALSE,colClasses="character",na.strings=NULL,strip.white=FALSE,row.names=NULL,fileEncoding="UTF-8")
tryCatch(withCallingHandlers({
 if(as.character(getRversion())!="4.3.3"||R.version$platform!="x86_64-conda-linux-gnu")unavailable("Supply the qualified Linux x86_64 R4.3.3 model profile")
 if(!requireNamespace("jsonlite",quietly=TRUE))unavailable("Missing jsonlite in declared private libraries")
 profile <- jsonlite::read_json(file.path(out,"source-profile.json"),simplifyVector=FALSE)
 for(name in names(profile$packages))if(!requireNamespace(name,quietly=TRUE)||as.character(getNamespaceVersion(name))!=profile$packages[[name]])unavailable(paste("Missing/wrong qualified package:",name,profile$packages[[name]]))
 suppressPackageStartupMessages(library(monocle3));if(as.character(getNamespaceVersion("monocle3"))!="1.3.1")unavailable("Original Monocle3 1.3.1 is required; no edition substitution")
 RhpcBLASctl::blas_set_num_threads(1L);RhpcBLASctl::omp_set_num_threads(1L);options(mc.cores=1L)
 stage <- "original namespace/source guards";definitions <- list();ns <- asNamespace("monocle3")
 for(file in list.files(file.path(source_root,"R"),pattern="[.]R$",full.names=TRUE))for(expr in parse(file,keep.source=FALSE))if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]]) %in% c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))definitions[[as.character(expr[[2]])]] <- expr[[3]]
 for(name in unlist(profile$guarded_functions,use.names=FALSE)){stopifnot(!is.null(definitions[[name]]),exists(name,ns,inherits=FALSE));original <- eval(definitions[[name]],envir=ns);loaded <- get(name,ns,inherits=FALSE);if(!identical(body(original),body(loaded))||!identical(formals(original),formals(loaded)))stop("Original function body/formals mismatch: ",name)}
 p <- jsonlite::read_json(file.path(out,"parameters.json"),simplifyVector=FALSE);stage <- "complete input population"
 cells <- read_tsv("cells.tsv");genes <- read_tsv("genes.tsv");stopifnot(identical(names(genes),c("gene_id","gene_short_name")),names(cells)[1]=="cell_id",setequal(names(cells)[-1],names(p$metadata_types)))
 cell_ids <- cells$cell_id;gene_ids <- genes$gene_id;stopifnot(!anyDuplicated(cell_ids),!anyDuplicated(gene_ids),length(cell_ids)>=5L)
 if(p$expression_format=="TSV"){
  d <- read_tsv("expression.data");stopifnot(identical(d$gene_id,gene_ids),identical(names(d)[-1],cell_ids));expression <- as.matrix(d[-1]);storage.mode(expression) <- "double";dimnames(expression) <- list(gene_ids,cell_ids);expression <- Matrix::Matrix(expression,sparse=TRUE)
 }else{expression <- Matrix::readMM(file.path(out,"inputs","expression.data"));stopifnot(identical(dim(expression),as.integer(c(length(gene_ids),length(cell_ids)))));dimnames(expression) <- list(gene_ids,cell_ids)}
 stopifnot(all(is.finite(expression@x)),all(expression@x>=0));if(p$expression_units=="raw-integer-counts")stopifnot(all(expression@x==floor(expression@x)),all(expression@x<=9007199254740991))
 cm <- cells[-1];rownames(cm) <- cell_ids
 for(name in names(p$metadata_types)){
  type <- p$metadata_types[[name]]
  if(type$type=="numeric"){cm[[name]] <- as.numeric(cm[[name]]);stopifnot(all(is.finite(cm[[name]])))}else{levels <- unlist(type$levels,use.names=FALSE);stopifnot(all(cm[[name]] %in% levels));cm[[name]] <- factor(cm[[name]],levels=levels)}
 }
 gm <- data.frame(gene_short_name=genes$gene_short_name,row.names=gene_ids,stringsAsFactors=FALSE)
 dir.create(file.path(out,"analyses"))
 for(a in p$analyses){
  target <- file.path(out,"analyses",a$id);dir.create(target);warnings <- character();messages <- character();failure <- NULL
  result <- tryCatch(withCallingHandlers({
   stage <- paste(a$id,"original new_cell_data_set");set.seed(a$seed);rng_before <- .Random.seed
   cds <- monocle3::new_cell_data_set(expression,cell_metadata=cm,gene_metadata=gm)
   terms <- unlist(a$terms,use.names=FALSE);formula <- paste0("~",if(a$intercept)"1"else"0",if(length(terms))paste0(" + ",paste(terms,collapse=" + "))else"")
   mf <- stats::model.frame(stats::as.formula(formula),data=as.data.frame(SummarizedExperiment::colData(cds)),na.action=stats::na.fail);stopifnot(nrow(mf)==length(cell_ids))
   call <- list(cds=cds,model_formula_str=formula,cores=1L,verbose=TRUE)
   if(a$family!="ORIGINAL_DEFAULT")call$expression_family <- a$family
   if(!is.character(a$clean_model))call$clean_model <- a$clean_model
   stage <- paste(a$id,"original fit_models and coefficient_table")
   fits <- do.call(monocle3::fit_models,call);coefficients <- monocle3::coefficient_table(fits)
   simple_models <- atomic_table(fits,file.path(target,"model-status"));simple_coefficients <- atomic_table(coefficients,file.path(target,"coefficients"))
   saveRDS(list(cds=cds,fits=fits),file.path(target,"models.rds"));saveRDS(list(before=rng_before,after=.Random.seed),file.path(target,"rng.rds"))
   stopifnot(identical(as.character(fits$gene_id),gene_ids),identical(colnames(cds),cell_ids),identical(rownames(cds),gene_ids))
   loaded <- readRDS(file.path(target,"models.rds"));again <- monocle3::coefficient_table(loaded$fits);again <- again[,!vapply(again,is.list,logical(1)),drop=FALSE]
   stopifnot(identical(simple_coefficients,again),identical(loaded$fits$status,fits$status),identical(SummarizedExperiment::assay(loaded$cds,"counts"),SummarizedExperiment::assay(cds,"counts")),identical(SummarizedExperiment::colData(loaded$cds),SummarizedExperiment::colData(cds)))
   write_json(list(cells=as.list(cell_ids),size_factors=tokens(monocle3::size_factors(cds))),file.path(target,"size-factors.json"))
   if(anyNA(fits$status)||any(fits$status!="OK"))stop("Original gene model status is non-OK; complete returned models/coefficients retained, entire request unadmitted")
   rows <- lapply(seq_len(nrow(simple_coefficients)),function(i){
    c <- simple_coefficients[i,,drop=FALSE];at <- match(c$gene_id,fits$gene_id);model <- fits$model[[at]];summary <- fits$model_summary[[at]];names <- colnames(summary$coefficients)
    distribution <- if(identical(names[4],"Pr(>|t|)"))"T"else if(identical(names[4],"Pr(>|z|)"))"NORMAL"else stop("Unqualified original coefficient distribution: ",names[4])
    df <- model[["df",exact=TRUE]];if(is.null(df))df <- model[["df.residual",exact=TRUE]];stopifnot(length(df)==1L,is.finite(df))
    observations <- model[["n",exact=TRUE]];if(is.null(observations))observations <- model[["df.residual",exact=TRUE]]+model[["rank",exact=TRUE]]
    stopifnot(length(observations)==1L,observations==length(cell_ids))
    good <- model[["ngoodobs",exact=TRUE]];if(!is.null(good))stopifnot(good==length(cell_ids))
    list(gene_id=as.character(c$gene_id),component=as.character(c$model_component),term=as.character(c$term),estimate=token(c$estimate),std_err=token(c$std_err),test_val=token(c$test_val),p_value=token(c$p_value),q_value=token(c$q_value),distribution=distribution,residual_df=token(df),observations=observations)
   })
   write_json(list(genes=as.list(gene_ids),rows=rows,adjustment="original stats::p.adjust default Holm, grouped by model_component and term"),file.path(target,"statistics.json"))
   resolved_family <- if(a$family=="ORIGINAL_DEFAULT")formals(monocle3::fit_models)$expression_family else a$family
   resolved_clean <- if(is.character(a$clean_model))formals(monocle3::fit_models)$clean_model else a$clean_model
   write_json(list(formula=formula,expression_family=resolved_family,family_argument_omitted=a$family=="ORIGINAL_DEFAULT",clean_model=resolved_clean,clean_model_argument_omitted=is.character(a$clean_model),cores=1L,verbose=TRUE,expression_units=p$expression_units),file.path(target,"original-arguments.json"))
   list(genes=length(gene_ids),cells=length(cell_ids),rows=nrow(simple_coefficients),model_statuses=as.list(fits$status),returned_model_roundtrip=TRUE)
  },warning=function(w){warnings <<- c(warnings,paste0(stage,": ",conditionMessage(w)));invokeRestart("muffleWarning")},message=function(m){messages <<- c(messages,conditionMessage(m));invokeRestart("muffleMessage")}),error=function(e){failure <<- list(stage=stage,cause=conditionMessage(e),call=paste(deparse(conditionCall(e)),collapse="\n"));NULL})
  entry <- list(id=a$id,status=if(is.null(failure))"ORIGINAL_RETURNED"else"FAIL2_ORIGINAL_MODEL",population=result,error=failure,warnings=as.list(warnings),messages=as.list(messages));cases[[a$id]] <- entry;write_json(entry,file.path(target,"source-status.json"))
 }
 write_json(list(status=if(all(vapply(cases,function(c)c$status=="ORIGINAL_RETURNED",logical(1))))"PASS_ORIGINAL_MONOCLE131_COMPLETE_MODEL_REQUEST"else"FAIL2_ORIGINAL_MONOCLE131_MODEL_REQUEST",cases=cases,guarded_functions=profile$guarded_functions,packages=setNames(lapply(loadedNamespaces(),function(n)as.character(getNamespaceVersion(n))),loadedNamespaces()),expression_units=p$expression_units,source_original_patched=FALSE,whole_goal_complete=FALSE),file.path(out,"worker-result.json"))
 if(any(vapply(cases,function(c)c$status!="ORIGINAL_RETURNED",logical(1))))stop("Original model request contains non-OK analysis; no partial result admitted")
 writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"));writeLines("0",file.path(out,"worker-exit.txt"))
},warning=function(w){warnings_seen <<- c(warnings_seen,conditionMessage(w));invokeRestart("muffleWarning")}),error=function(e){code <- if(inherits(e,"runtime_unavailable"))3L else 2L;writeLines(as.character(code),file.path(out,"worker-exit.txt"));writeLines(c(stage,conditionMessage(e),warnings_seen),file.path(out,"worker-error.txt"));message(stage,": ",conditionMessage(e));quit(save="no",status=code)})
