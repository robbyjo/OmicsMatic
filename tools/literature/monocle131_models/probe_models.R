# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(trailingOnly=TRUE);stopifnot(length(args)==4L)
out <- args[1];source_root <- args[4];.libPaths(args[2:3],include.site=FALSE)
write_json <- function(x,p)jsonlite::write_json(x,p,pretty=TRUE,auto_unbox=TRUE,digits=NA,na="string",null="null")
stopifnot(as.character(getRversion())=="4.3.3")
suppressPackageStartupMessages(library(monocle3));stopifnot(as.character(getNamespaceVersion("monocle3"))=="1.3.1",normalizePath(find.package("monocle3"))==normalizePath(file.path(args[3],"monocle3")))
RhpcBLASctl::blas_set_num_threads(1L);RhpcBLASctl::omp_set_num_threads(1L);options(mc.cores=1L)
definitions <- list();ns <- asNamespace("monocle3")
for(file in list.files(file.path(source_root,"R"),pattern="[.]R$",full.names=TRUE))for(expr in parse(file,keep.source=FALSE))if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]]) %in% c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))definitions[[as.character(expr[[2]])]] <- expr[[3]]
guards <- c("new_cell_data_set","estimate_size_factors","detect_genes","fit_models","fit_model_helper","extract_model_status_helper","extract_coefficient_helper","coefficient_table","smart_es_apply")
for(name in guards){stopifnot(!is.null(definitions[[name]]),exists(name,ns,inherits=FALSE));original <- eval(definitions[[name]],envir=ns);loaded <- get(name,ns,inherits=FALSE);if(!identical(body(original),body(loaded))||!identical(formals(original),formals(loaded)))stop("Original body/formals mismatch: ",name)}
writeLines(deparse(get("summary.speedglm",asNamespace("speedglm"))),file.path(out,"summary-speedglm.R"))
set.seed(2051);genes <- paste0("G",seq_len(8));cells <- sprintf("C%03d",seq_len(120));Gi <- seq(-2.4,2.4,length.out=120);Sample <- factor(rep(c("A","B","C","D"),30),levels=c("A","B","C","D"));UMI <- 30000+seq_len(120)*37+as.integer(1200*sin(seq_len(120)*1.31))
raw <- sapply(seq_along(cells),function(i)rnbinom(8,size=7,mu=exp(3.5+c(.65,-.65,.3,-.2,.02,0,.12,-.1)*Gi[i]+.1*(as.integer(Sample[i])-1))));dimnames(raw) <- list(genes,cells)
normalized <- log1p(t(t(raw)/colSums(raw))*10000)
cm <- data.frame(Gi=Gi,Sample=Sample,UMI=UMI,row.names=cells);gm <- data.frame(gene_short_name=genes,row.names=genes)
write.table(data.frame(gene_id=genes,raw,check.names=FALSE),file.path(out,"raw.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
write.table(data.frame(gene_id=genes,normalized,check.names=FALSE),file.path(out,"normalized.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
write.table(data.frame(cell_id=cells,cm,check.names=FALSE),file.path(out,"cells.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
write.table(data.frame(gene_id=genes,gm,check.names=FALSE),file.path(out,"genes.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
tokens <- function(x)as.list(vapply(as.numeric(x),function(v)if(is.nan(v))"NaN" else if(is.na(v))"NA" else if(is.infinite(v))if(v>0)"Inf" else "-Inf" else sprintf("%.17g",v),character(1)))
atomic <- function(d)list(rows=nrow(d),columns=setNames(lapply(d,function(x)if(is.numeric(x))tokens(x)else as.list(as.character(x))),names(d)))
dir.create(file.path(out,"cases"));results <- list()
for(label in c("default-normalized-clean","default-normalized-full","quasipoisson-normalized","default-raw","poisson-raw","gaussian-normalized","negbinomial-raw")){
 directory <- file.path(out,"cases",label);dir.create(directory);warnings <- character();detail <- NULL
 result <- tryCatch(withCallingHandlers({
  normalized_case <- grepl("normalized",label);expression <- if(normalized_case)normalized else raw
  cds <- monocle3::new_cell_data_set(Matrix::Matrix(expression,sparse=TRUE),cell_metadata=cm,gene_metadata=gm)
  a <- list(cds=cds,model_formula_str="~Gi + Sample + UMI",cores=1L,verbose=TRUE)
  if(label=="default-normalized-full")a$clean_model <- FALSE
  if(grepl("^(quasipoisson|poisson|gaussian|negbinomial)-",label))a$expression_family <- sub("-.*$","",label)
  fits <- do.call(monocle3::fit_models,a);coefs <- monocle3::coefficient_table(fits)
  simple <- coefs[,!vapply(coefs,is.list,logical(1)),drop=FALSE]
  saveRDS(list(cds=cds,fits=fits),file.path(directory,"models.rds"));loaded <- readRDS(file.path(directory,"models.rds"));again <- monocle3::coefficient_table(loaded$fits);again <- again[,!vapply(again,is.list,logical(1)),drop=FALSE];stopifnot(identical(simple,again),identical(as.matrix(SummarizedExperiment::assay(loaded$cds,"counts")),expression))
  write_json(atomic(simple),file.path(directory,"coefficients.json"));write.table(simple,file.path(directory,"coefficients.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
  diagnostics <- lapply(seq_len(nrow(fits)),function(i){model <- fits$model[[i]];summary <- fits$model_summary[[i]];list(gene_id=fits$gene_id[i],status=fits$status[i],model_class=as.list(class(model)),coefficient_columns=as.list(colnames(summary$coefficients)),df=model$df,df_residual=model$df.residual,summary_df=summary$df,dispersion=summary$dispersion,size_factors=tokens(monocle3::size_factors(cds)))})
  write_json(diagnostics,file.path(directory,"diagnostics.json"));list(genes=nrow(fits),cells=ncol(cds),model_statuses=as.list(fits$status),coefficient_rows=nrow(coefs),roundtrip=TRUE,expression_units=if(normalized_case)"nonnegative-normalized-expression"else"raw-integer-counts",original_arguments=a[names(a)!="cds"])
 },warning=function(w){warnings <<- c(warnings,conditionMessage(w));invokeRestart("muffleWarning")}),error=function(e){detail <<- conditionMessage(e);NULL})
 results[[label]] <- list(status=if(is.null(detail))"ORIGINAL_RETURNED"else"ORIGINAL_ERROR",result=result,cause=detail,warnings=as.list(warnings));write_json(results[[label]],file.path(directory,"source-status.json"));cat(label,results[[label]]$status,"\n")
}
write_json(list(status="OBSERVED_EXACT_ORIGINAL_MONOCLE131_MODEL_CASES_NOT_USER_ANALYSIS",results=results,guarded_functions=as.list(guards),loaded_packages=setNames(lapply(loadedNamespaces(),function(n)as.character(getNamespaceVersion(n))),loadedNamespaces()),paper_reproduced=FALSE,new_tool_credit=0,whole_goal_complete=FALSE),file.path(out,"MODEL-CONTROLS.json"))
writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"))
