# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Direct original Monocle2 scientific controls; no user analysis or Java adapter admission.
a <- commandArgs(TRUE);out <- a[1];original_source <- a[2];.libPaths(c(a[-(1:2)],.Library),include.site=FALSE)
warnings_seen <- list();messages_seen <- list();cases <- list();status <- "FAIL2_ORIGINAL_MONOCLE2_PROBE";detail <- "";stage <- "load";loaded <- FALSE
write_json <- function(x,path)jsonlite::write_json(x,path,pretty=TRUE,auto_unbox=TRUE,na="string",null="null",digits=NA)
tryCatch(withCallingHandlers({
    suppressPackageStartupMessages(library(monocle));loaded <- TRUE
    stopifnot(identical(as.character(packageVersion("monocle")),"2.26.0"))
    stopifnot(identical(as.character(getRversion()),"4.2.1"),identical(as.character(packageVersion("dplyr")),"1.1.4"))
    definitions <- list();ns <- asNamespace("monocle");guards <- character()
    for(file in list.files(file.path(original_source,"R"),pattern="[.]R$",full.names=TRUE))for(expr in parse(file,keep.source=FALSE)) {
        if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]]) %in% c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))definitions[[as.character(expr[[2]])]] <- expr[[3]]
    }
    for(name in c("newCellDataSet","differentialGeneTest","diff_test_helper","compareModels","estimateSizeFactorsForMatrix","estimateDispersionsForCellDataSet","smartEsApply","calculate_NB_dispersion_hint")) {
        if(is.null(definitions[[name]])||!exists(name,ns,inherits=FALSE))stop("Original source function unavailable: ",name)
        original <- eval(definitions[[name]],envir=ns);loaded <- get(name,ns,inherits=FALSE)
        if(!identical(body(original),body(loaded))||!identical(formals(original),formals(loaded)))stop("Original function body/formals mismatch: ",name)
        guards <- c(guards,name)
    }
    write_json(list(original_version=as.character(packageVersion("monocle")),R=as.character(getRversion()),dplyr=as.character(packageVersion("dplyr")),guarded_functions=as.list(guards)),file.path(out,"source-guards.json"))
    RhpcBLASctl::blas_set_num_threads(1L);RhpcBLASctl::omp_set_num_threads(1L);set.seed(20261107)
    cells <- sprintf("cell-%03d",1:200);genes <- sprintf("gene-%03d",1:120)
    progress <- seq(0,1,length.out=200);condition <- factor(rep(c("control","case"),100),levels=c("control","case"))
    sex <- factor(rep(c("female","female","male","male"),50));batch <- factor(rep(c("a","b","c","d"),50))
    cm <- data.frame(progress=progress,condition=condition,sex=sex,batch=batch,row.names=cells)
    mu <- outer(exp(seq(log(2),log(256),length.out=120)),progress,function(g,t)g*exp(0.5*t))
    mu[,condition=="case"] <- mu[,condition=="case"]*1.5
    counts <- matrix(rnbinom(length(mu),size=8,mu=as.vector(mu)),nrow=120,dimnames=list(genes,cells))
    gm <- data.frame(gene_short_name=genes,row.names=genes)
    raw <- monocle::newCellDataSet(methods::as(counts,"dgCMatrix"),phenoData=methods::new("AnnotatedDataFrame",data=cm),
        featureData=methods::new("AnnotatedDataFrame",data=gm),lowerDetectionLimit=0.1,expressionFamily=VGAM::negbinomial.size())
    stopifnot(identical(unname(dim(raw)),dim(counts)),identical(rownames(raw),genes),identical(colnames(raw),cells))
    saveRDS(list(counts=counts,cell_metadata=cm,gene_metadata=gm,cds=raw,seed=20261107L),file.path(out,"input.rds"))
    stage <- "size-factors";cds <- BiocGenerics::estimateSizeFactors(raw);saveRDS(cds,file.path(out,"size-factors-cds.rds"))
    stage <- "dispersions";cds <- BiocGenerics::estimateDispersions(cds,modelFormulaStr="~1",relative_expr=TRUE,min_cells_detected=1L,remove_outliers=TRUE)
    saveRDS(cds,file.path(out,"dispersions-cds.rds"));saveRDS(monocle::dispersionTable(cds),file.path(out,"dispersion-table.rds"))
    write.table(data.frame(cell_id=cells,size_factor=monocle::sizeFactors(cds)),file.path(out,"size-factors.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
    configurations <- list(
        list(id="condition-adjusted-relative",full="~condition+progress+sex",reduced="~progress+sex",relative=TRUE),
        list(id="condition-adjusted-unscaled",full="~condition+progress+sex",reduced="~progress+sex",relative=FALSE),
        list(id="progress-spline",full="~sm.ns(progress, df=3)+batch",reduced="~batch",relative=TRUE))
    for(config in configurations) {
        stage <- config$id;target <- file.path(out,config$id);dir.create(target);failure <- NULL
        value <- tryCatch(monocle::differentialGeneTest(cds,fullModelFormulaStr=config$full,reducedModelFormulaStr=config$reduced,
            relative_expr=config$relative,cores=1L,verbose=TRUE),error=function(e){failure <<- list(message=conditionMessage(e),call=paste(deparse(conditionCall(e)),collapse="\n"));NULL})
        if(!is.null(value)) {
            saveRDS(value,file.path(target,"original-tests.rds"));stopifnot(identical(rownames(value),genes))
            write.table(data.frame(gene_id=rownames(value),value,check.names=FALSE),file.path(target,"original-tests.tsv"),sep="\t",quote=TRUE,row.names=FALSE,na="NA")
            # Inspect the untouched per-gene helper as a distinct source control.
            all_values <- new.env(parent=asNamespace("monocle"));for(name in names(cm))assign(name,cm[[name]],all_values)
            assign("Size_Factor",monocle::sizeFactors(cds),all_values)
            helper <- get("diff_test_helper",asNamespace("monocle"));environment(helper) <- all_values
            one <- helper(counts[1,],fullModelFormulaStr=config$full,reducedModelFormulaStr=config$reduced,expressionFamily=cds@expressionFamily,
                relative_expr=config$relative,weights=NULL,disp_func=cds@dispFitInfo[["blind"]]$disp_func,verbose=TRUE)
            saveRDS(one,file.path(target,"original-single-gene-helper.rds"));stopifnot(isTRUE(all.equal(one$pval[[1]],value$pval[[1]],tolerance=1e-13)))
        }
        report <- list(id=config$id,arguments=config,original_error=failure,returned=!is.null(value),rows=if(is.null(value))0L else nrow(value),
            statuses=if(is.null(value))list() else as.list(table(value$status)),all_status_OK=!is.null(value)&&!anyNA(value$status)&&all(value$status=="OK"),
            q_adjustment="Original BH only across status OK genes; source failed rows retain pval/qval 1 and FAIL status")
        write_json(report,file.path(target,"RESULT.json"));cases[[length(cases)+1L]] <- report
    }
    if(any(!vapply(cases,function(x)x$all_status_OK,logical(1))))stop("One or more direct original Monocle2 controls failed; no fitting profile admitted")
    status <- "COMPLETED_ORIGINAL_MONOCLE2_DIFFERENTIAL_READINESS_PROBE"
},warning=function(w){warnings_seen[[length(warnings_seen)+1L]] <<- list(stage=stage,message=conditionMessage(w));invokeRestart("muffleWarning")},
  message=function(m){messages_seen[[length(messages_seen)+1L]] <<- list(stage=stage,message=conditionMessage(m));invokeRestart("muffleMessage")} ),
    error=function(e){detail <<- conditionMessage(e)})
writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"))
write_json(list(status=status,stage=stage,detail=detail,warnings=warnings_seen,messages=messages_seen,cases=cases,exact_Monocle2="2.26.0",
    original_package_loaded=loaded,synthetic_only=TRUE,Java_adapter_validated=FALSE,implementation_credit=FALSE,whole_paper_reproduced=FALSE),file.path(out,"RESULT.json"))
quit(status=if(status=="COMPLETED_ORIGINAL_MONOCLE2_DIFFERENTIAL_READINESS_PROBE")0L else 2L)
