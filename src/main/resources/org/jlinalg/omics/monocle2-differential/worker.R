# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original Monocle2 differential tests only; closed typed inputs, no installs or input RDS.
runtime_ready <- FALSE;status <- "SKIP3_DEPENDENCY_UNAVAILABLE";detail <- "";global_error <- NULL
warnings_seen <- character();messages_seen <- character();cases <- list();guards <- character();stage_name <- "runtime"
action <- "Preserve this attempt. Read WARNINGS.md, worker-report.json, each source-status.json and stdout/stderr. Supply exact Linux R4.2.1/Monocle2.26.0/private dependencies only where permitted; otherwise skip dependent analysis. No installation or edition substitution occurs. FAIL2 admits no batch result, including earlier completed cases. Correct the named response/family/model/runtime/capacity cause into NEW paths; never drop genes, repair IDs, impute, replace nonfinite values or interpret failed-gene p/q=1 as a valid null."
.libPaths(c(readLines("runtime-paths.txt",encoding="UTF-8",warn=FALSE),.Library),include.site=FALSE)
write_json <- function(x,path)jsonlite::write_json(x,path,pretty=TRUE,auto_unbox=TRUE,na="string",null="null",digits=NA)
kind <- function(x)if(is.numeric(x)&&is.nan(x))"NaN" else if(is.na(x))"NA" else if(is.numeric(x)&&is.infinite(x)){if(x>0)"positive-infinity" else "negative-infinity"} else "value"
token <- function(x){k<-kind(x);if(k=="NaN")"NaN" else if(k=="NA")"NA" else if(k=="positive-infinity")"Inf" else if(k=="negative-infinity")"-Inf" else if(is.double(x))sprintf("%.17g",x) else as.character(x)}
atomic_table <- function(frame,path,row_ids=FALSE) {
    saveRDS(frame,paste0(path,".rds"));original_columns <- lapply(frame,function(x)list(class=as.list(class(x)),type=typeof(x),atomic_export=is.atomic(x)&&is.null(dim(x))))
    keep <- vapply(frame,function(x)is.atomic(x)&&is.null(dim(x)),logical(1));omitted <- names(frame)[!keep];frame <- as.data.frame(frame[,keep,drop=FALSE],check.names=FALSE)
    if(row_ids){stopifnot(!"source_row_name" %in% names(frame));frame <- cbind(data.frame(source_row_name=rownames(frame),stringsAsFactors=FALSE),frame)}
    columns <- lapply(seq_along(frame),function(i){x<-frame[[i]];list(name=names(frame)[i],class=as.list(class(x)),type=typeof(x),tokens=as.list(vapply(seq_along(x),function(j)token(x[j]),character(1))),kinds=as.list(vapply(seq_along(x),function(j)kind(x[j]),character(1))))})
    encoded <- setNames(lapply(columns,function(x)unlist(x$tokens,use.names=FALSE)),names(frame));write.table(as.data.frame(encoded,check.names=FALSE),paste0(path,".tsv"),sep="\t",quote=TRUE,qmethod="double",row.names=FALSE,fileEncoding="UTF-8")
    write_json(list(rows=nrow(frame),columns=columns,original_columns=original_columns,omitted_from_TSV=as.list(omitted),complete_original_RDS=basename(paste0(path,".rds"))),paste0(path,".json"))
}
read_literal <- function(path){rows<-lapply(readLines(path,encoding="UTF-8",warn=FALSE),strsplit,split="\t",fixed=TRUE);rows<-lapply(rows,`[[`,1L);h<-rows[[1]];rows<-rows[-1L];columns<-lapply(seq_along(h),function(j)vapply(rows,function(r)r[[j]],character(1)));names(columns)<-h;as.data.frame(columns,check.names=FALSE,stringsAsFactors=FALSE)}
formula_text <- function(spec){terms<-vapply(spec$terms,function(t)if(is.null(t$spline_df))t$column else paste0("sm.ns(",t$column,", df=",as.integer(t$spline_df),")"),character(1));paste0("~",paste(c(if(spec$intercept)"1" else "0",terms),collapse="+"))}
tryCatch(withCallingHandlers({
    if(!requireNamespace("jsonlite",quietly=TRUE))stop("Required jsonlite unavailable")
    p <- jsonlite::fromJSON("parameters.json",simplifyVector=FALSE);profile <- jsonlite::fromJSON("../source/SOURCE-PROFILE.json",simplifyVector=FALSE)
    Sys.setlocale("LC_CTYPE","C.UTF-8");if(!isTRUE(l10n_info()[["UTF-8"]])||Sys.getlocale("LC_COLLATE")!="C"||Sys.getlocale("LC_NUMERIC")!="C")stop("Required UTF-8 character / C numeric and collation locale unavailable")
    if(p$version!=profile$version)stop("Requested exact Monocle2 edition unavailable: ",p$version,"; this profile is ",profile$version," and no fallback occurs")
    if(as.character(getRversion())!=profile$r_version||R.version$platform!=profile$platform)stop("Exact R/platform profile unavailable: ",profile$r_version," / ",profile$platform)
    for(name in names(profile$required_packages))if(!requireNamespace(name,quietly=TRUE)||as.character(package_version(getNamespaceVersion(name)))!=profile$required_packages[[name]]||as.character(packageVersion(name))!=profile$required_packages[[name]])stop("Exact dependency unavailable: ",name," ",profile$required_packages[[name]])
    suppressPackageStartupMessages(library(monocle));ns <- asNamespace("monocle");definitions <- list()
    for(file in list.files("../source/2.26.0/R",pattern="[.]R$",full.names=TRUE))for(expr in parse(file,keep.source=FALSE)){
        if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]]) %in% c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))definitions[[as.character(expr[[2]])]] <- expr[[3]]
    }
    for(name in unlist(profile$guarded_functions,use.names=FALSE)){
        if(is.null(definitions[[name]])||!exists(name,ns,inherits=FALSE))stop("Original source function unavailable: ",name)
        source_fn <- eval(definitions[[name]],envir=ns);loaded_fn <- get(name,ns,inherits=FALSE)
        if(!identical(body(source_fn),body(loaded_fn))||!identical(formals(source_fn),formals(loaded_fn)))stop("Original function body/formals mismatch: ",name)
        guards <- c(guards,name)
    }
    RhpcBLASctl::blas_set_num_threads(1L);RhpcBLASctl::omp_set_num_threads(1L);runtime_ready <- TRUE;stage_name <- "input"
    cm <- read_literal("../inputs/cell-metadata.tsv");gm <- read_literal("../inputs/gene-metadata.tsv");cell_ids <- cm[[1]];gene_ids <- gm[[1]]
    rownames(cm)<-cell_ids;cm<-cm[,-1,drop=FALSE];rownames(gm)<-gene_ids;gm<-gm[,-1,drop=FALSE]
    for(name in names(p$metadata_types)){spec<-p$metadata_types[[name]];cm[[name]]<-if(spec$type=="numeric")as.numeric(cm[[name]]) else factor(cm[[name]],levels=unlist(spec$levels,use.names=FALSE));stopifnot(!anyNA(cm[[name]]))}
    if(p$matrix_format=="dense-tsv"){
        raw <- read_literal("../inputs/expression");stopifnot(identical(raw[[1]],gene_ids),identical(names(raw)[-1],cell_ids));values <- matrix(as.numeric(as.matrix(raw[,-1,drop=FALSE])),nrow=length(gene_ids),dimnames=list(gene_ids,cell_ids));expression <- methods::as(values,"dgCMatrix")
    }else{
        expression <- methods::as(Matrix::readMM("../inputs/expression"),"dgCMatrix");stopifnot(identical(dim(expression),c(length(gene_ids),length(cell_ids))));dimnames(expression)<-list(gene_ids,cell_ids)
    }
    stopifnot(all(is.finite(expression@x)));saveRDS(list(expression=expression,cell_metadata=cm,gene_metadata=gm),"input-transport.rds")
    write_json(list(gene_ids=as.list(gene_ids),cell_ids=as.list(cell_ids),genes=nrow(expression),cells=ncol(expression),matrix_format=p$matrix_format,expression_units=p$expression_units),"input-ledger.json")
    dir.create("cases")
    for(a in p$analyses){
        target<-file.path("cases",a$id);dir.create(target);case_warnings<-character();case_messages<-character();failure<-NULL;stage_name<-"construct"
        returned<-tryCatch(withCallingHandlers({
            set.seed(as.integer(a$seed));write_json(list(seed=a$seed,RNGkind=as.list(RNGkind())),file.path(target,"rng.json"))
            factory <- get(a$family,asNamespace("VGAM"));factory_args <- if(is.null(a$size))list() else list(size=a$size);family <- do.call(factory,factory_args)
            cds<-monocle::newCellDataSet(expression,phenoData=methods::new("AnnotatedDataFrame",data=cm),featureData=methods::new("AnnotatedDataFrame",data=gm),lowerDetectionLimit=a$lower_detection_limit,expressionFamily=family)
            stopifnot(identical(rownames(cds),gene_ids),identical(colnames(cds),cell_ids),isTRUE(all.equal(Biobase::exprs(cds),expression,tolerance=0)))
            saveRDS(cds,file.path(target,"constructed-cds.rds"))
            if(a$estimate_size_factors){stage_name<-"size-factors";cds<-BiocGenerics::estimateSizeFactors(cds);saveRDS(cds,file.path(target,"size-factors-cds.rds"))}
            if(a$dispersion$estimate){
                stage_name<-"dispersions";d<-a$dispersion;cds<-BiocGenerics::estimateDispersions(cds,modelFormulaStr=formula_text(d$model),relative_expr=d$relative_expr,min_cells_detected=as.integer(d$min_cells_detected),remove_outliers=d$remove_outliers,cores=1L)
                saveRDS(cds,file.path(target,"dispersions-cds.rds"));atomic_table(monocle::dispersionTable(cds),file.path(target,"dispersion-table"))
            }
            stage_name<-"save-reload";saveRDS(cds,file.path(target,"cds.rds"));reloaded<-readRDS(file.path(target,"cds.rds"))
            stopifnot(identical(rownames(reloaded),gene_ids),identical(colnames(reloaded),cell_ids),isTRUE(all.equal(Biobase::exprs(reloaded),expression,tolerance=0)),identical(Biobase::pData(reloaded),Biobase::pData(cds)),identical(Biobase::fData(reloaded),Biobase::fData(cds)),identical(BiocGenerics::sizeFactors(reloaded),BiocGenerics::sizeFactors(cds)))
            if(a$dispersion$estimate)stopifnot(identical(monocle::dispersionTable(reloaded),monocle::dispersionTable(cds)))
            atomic_table(data.frame(cell_id=cell_ids,size_factor=BiocGenerics::sizeFactors(cds)),file.path(target,"cells"))
            full<-formula_text(a$full_model);reduced<-formula_text(a$reduced_model);stage_name<-"differentialGeneTest"
            tests<-monocle::differentialGeneTest(cds,fullModelFormulaStr=full,reducedModelFormulaStr=reduced,relative_expr=a$relative_expr,cores=1L,verbose=TRUE)
            atomic_table(tests,file.path(target,"tests"),row_ids=TRUE);stopifnot(identical(rownames(tests),gene_ids))
            write_json(list(family=a$family,requested_factory_arguments=factory_args,source_factory_formals=paste(deparse(formals(factory)),collapse="\n"),full_formula=full,reduced_formula=reduced,relative_expr=a$relative_expr,estimate_size_factors=a$estimate_size_factors,dispersion=a$dispersion,cores=1L,verbose=TRUE,lower_detection_limit=a$lower_detection_limit,source_response_rule="NB: optional x/Size_Factor then round; uninormal/binomialff: unchanged response; gene dispersion hint follows original helper",q_adjustment="Original BH only across status OK genes; failed original rows retain pval/qval 1 and FAIL status",internal_VGAM_models_returned_by_original_function=FALSE),file.path(target,"original-arguments.json"))
            if(anyNA(tests$status)||any(tests$status!="OK"))stop("Original non-OK gene statuses; failed-row p/q=1 are not valid null results. All raw tests retained; entire atomic batch unadmitted")
            list(genes=length(gene_ids),cells=length(cell_ids),test_rows=nrow(tests),save_reload_identical=TRUE)
        },warning=function(w){case_warnings<<-c(case_warnings,paste0(stage_name,": ",conditionMessage(w)));invokeRestart("muffleWarning")},message=function(m){case_messages<<-c(case_messages,paste0(stage_name,": ",conditionMessage(m)));invokeRestart("muffleMessage")}),error=function(e){failure<<-list(stage=stage_name,class=as.list(class(e)),message=conditionMessage(e),call=paste(deparse(conditionCall(e)),collapse="\n"));NULL})
        entry<-list(id=a$id,status=if(is.null(failure))"original-returned" else "FAIL2_ORIGINAL_ERROR",original_error=failure,warnings=as.list(case_warnings),messages=as.list(case_messages),population=returned,admission_authority="Java analysis-status.json; source return alone is not admission")
        write_json(entry,file.path(target,"source-status.json"));cases[[length(cases)+1L]]<-entry;write_json(list(status="INCOMPLETE_NO_RESULT_ADMITTED",cases=cases),"worker-progress.json")
    }
    if(any(vapply(cases,function(x)x$status!="original-returned",logical(1))))stop("One or more original cases failed; no atomic batch result admitted")
    status<-"completed-with-warnings"
},warning=function(w){warnings_seen<<-c(warnings_seen,paste0(stage_name,": ",conditionMessage(w)));invokeRestart("muffleWarning")},message=function(m){messages_seen<<-c(messages_seen,conditionMessage(m));invokeRestart("muffleMessage")}),error=function(e){detail<<-conditionMessage(e);global_error<<-list(class=as.list(class(e)),message=conditionMessage(e),call=paste(deparse(conditionCall(e)),collapse="\n"));if(runtime_ready)status<<-"FAIL2_ORIGINAL_ERROR"})
writeLines(capture.output(sessionInfo()),"session-info.txt")
if(requireNamespace("jsonlite",quietly=TRUE))write_json(list(status=status,detail=detail,original_error=global_error,warnings=as.list(warnings_seen),messages=as.list(messages_seen),cases=cases,guarded_functions=as.list(guards),locale=Sys.getlocale(),scope="Original Monocle2 2.26.0 declared response/family/nested-model differential tests; no paper-version inference, Monocle3 substitution or biological certification"),"worker-report.json")
clean<-function(x)gsub("[\t\r\n]"," ",x);writeLines(c("status\tdetail\trequired_action",paste(status,clean(detail),clean(action),sep="\t")),"worker-status.tsv")
quit(status=if(status=="completed-with-warnings")0L else if(runtime_ready)2L else 3L)
