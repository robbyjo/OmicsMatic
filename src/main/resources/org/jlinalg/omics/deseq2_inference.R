# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Typed original-runtime worker. No user R expressions, installs or input RDS objects.
libraries <- readLines("runtime-paths.txt",encoding="UTF-8",warn=FALSE)
.libPaths(c(libraries,.Library),include.site=FALSE)
warnings_seen <- list();messages_seen <- character();runtime_ready <- FALSE
state <- "SKIP3_DEPENDENCY_UNAVAILABLE";detail <- "";source_guard <- character()
action <- "Read worker logs, source profile, package versions and WARNINGS.md. Supply R4.6.1, original DESeq2 1.52.0 and each requested optional package in declared private libraries only where permitted. If unavailable or installation is forbidden, skip the dependent request. Preserve this attempt and use NEW output paths after correction."
number <- function(x) vapply(x,function(z){
    if(is.nan(z))"NaN" else if(is.na(z))"NA" else if(is.infinite(z))
        if(z>0)"Inf" else "-Inf" else trimws(formatC(z,digits=17L,format="g",decimal.mark="."))
},character(1))
table_out <- function(x,path,first=NULL,first_name=NULL) {
    x <- as.data.frame(x,check.names=FALSE)
    values <- lapply(x,function(col)if(is.numeric(col))number(col) else if(is.logical(col))ifelse(is.na(col),"NA",ifelse(col,"TRUE","FALSE")) else as.character(col))
    if(!is.null(first)) {values <- c(setNames(list(as.character(first)),first_name),values)}
    frame <- as.data.frame(values,check.names=FALSE,stringsAsFactors=FALSE)
    write.table(frame,path,sep="\t",quote=FALSE,row.names=FALSE,col.names=TRUE,na="NA",fileEncoding="UTF-8")
}
indexed <- function(x,path,first_name="gene_index")table_out(x,path,rownames(x),first_name)
formula_for <- function(d) {
    terms <- vapply(d$terms,function(t)paste(unlist(t),collapse=":"),character(1))
    if(length(terms)==0L)as.formula(if(isTRUE(d$intercept))"~1" else "~0")
    else reformulate(terms,intercept=isTRUE(d$intercept))
}
select_args <- function(s,dds) {
    if(s$kind=="factor")list(contrast=c(s$factor,s$numerator_code,s$denominator_code))
    else if(s$kind=="coefficient") {
        names <- DESeq2::resultsNames(dds)
        if(s$index<1L||s$index>length(names))stop("Coefficient selector outside original resultsNames order")
        list(name=names[[s$index]])
    } else list(contrast=as.numeric(unlist(s$weights)))
}
tryCatch(withCallingHandlers({
    if(!requireNamespace("jsonlite",quietly=TRUE))stop("Required jsonlite package is unavailable")
    if(getRversion()!=package_version("4.6.1"))stop("Reference profile requires exact R4.6.1; no edition was substituted")
    if(!requireNamespace("DESeq2",quietly=TRUE)||as.character(packageVersion("DESeq2"))!="1.52.0")stop("Original DESeq2 1.52.0 is unavailable or cannot load")
    p <- jsonlite::fromJSON("parameters.json",simplifyVector=FALSE)
    fit <- p$fit
    required <- character()
    if(fit$fit_type=="glmGamPoi"||any(vapply(p$transforms,function(t)t$fit_type=="glmGamPoi",logical(1))))required <- c(required,"glmGamPoi")
    for(result in p$results)if(!is.null(result$shrink)&&result$shrink$type %in% c("apeglm","ashr"))required <- c(required,result$shrink$type)
    for(pkg in unique(required))if(!requireNamespace(pkg,quietly=TRUE))stop(paste("Requested optional runtime unavailable:",pkg))
    # Check loaded function bodies and formal arguments against the retained official sources.
    definitions <- list()
    for(file in list.files("../source/reference/R",pattern="[.]R$",full.names=TRUE)) {
        for(expression in parse(file,keep.source=FALSE)) {
            if(is.call(expression)&&length(expression)==3L&&identical(expression[[1]],as.name("<-"))&&
               is.symbol(expression[[2]])&&is.call(expression[[3]])&&identical(expression[[3]][[1]],as.name("function")))
                definitions[[as.character(expression[[2]])]] <- expression[[3]]
        }
    }
    guard_names <- c("DESeqDataSetFromMatrix","DESeq","estimateSizeFactorsForMatrix","estimateSizeFactors.DESeqDataSet",
                     "estimateDispersionsGeneEst","estimateDispersionsFit","estimateDispersionsMAP",
                     "nbinomWaldTest","nbinomLRT","results","resultsNames","lfcShrink","rlog",
                     "vst","varianceStabilizingTransformation","collapseReplicates")
    ns <- asNamespace("DESeq2")
    for(name in guard_names) {
        if(is.null(definitions[[name]])||!exists(name,envir=ns,inherits=FALSE))stop(paste("Required original source function unavailable:",name))
        ref <- eval(definitions[[name]],envir=ns);installed <- get(name,envir=ns,inherits=FALSE)
        if(!identical(body(ref),body(installed))||!identical(formals(ref),formals(installed)))stop(paste("Loaded function body/formals differ from original source:",name))
        source_guard <- c(source_guard,name)
    }
    BiocParallel::register(BiocParallel::SerialParam())
    runtime_ready <- TRUE
    RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(p$seed)
    write.table(data.frame(index=seq_along(.Random.seed),value=.Random.seed),"rng-before.tsv",sep="\t",quote=FALSE,row.names=FALSE)
    x <- read.delim("counts.tsv",row.names=1L,check.names=FALSE,colClasses=c("character",rep("numeric",p$samples)))
    x <- as.matrix(x)
    if(p$count_preparation=="R_round")x <- round(x)
    if(any(!is.finite(x))||any(x<0)||any(x>2147483647)||any(x!=round(x)))stop("Prepared counts violate the raw integer DESeqDataSet contract")
    storage.mode(x) <- "integer"
    meta <- read.delim("samples.tsv",row.names=1L,check.names=FALSE,colClasses="character")
    stopifnot(nrow(x)==p$genes,ncol(x)==p$samples,identical(colnames(x),rownames(meta)))
    cd <- S4Vectors::DataFrame(row.names=colnames(x))
    for(variable in p$variables) {
        name <- variable$name
        if(variable$kind=="factor")cd[[name]] <- factor(meta[[name]],levels=as.character(unlist(variable$levels)))
        else cd[[name]] <- as.numeric(meta[[name]])
    }
    design <- formula_for(p$design)
    dds <- DESeq2::DESeqDataSetFromMatrix(countData=x,colData=cd,design=design)
    if(isTRUE(p$collapse)) {
        groups <- factor(meta$collapse_group,levels=paste0("c",seq_len(p$analysis_samples)))
        dds <- DESeq2::collapseReplicates(dds,groupby=groups,run=colnames(dds),renameCols=TRUE)
    }
    indexed(DESeq2::counts(dds),"counts-prepared.tsv")
    if(isTRUE(p$has_normalization_factors)) {
        nf <- as.matrix(read.delim("normalization-factors.tsv",row.names=1L,check.names=FALSE))
        stopifnot(identical(dimnames(nf),dimnames(dds)))
        DESeq2::normalizationFactors(dds) <- nf
    } else if(isTRUE(p$has_size_factors)) {
        values <- read.delim("size-factors.tsv",check.names=FALSE)
        stopifnot(identical(values$sample_index,colnames(dds)))
        DESeq2::sizeFactors(dds) <- values$value
    } else {
        sf_args <- list(object=dds,type=fit$size_factor_type,quiet=FALSE)
        if(isTRUE(p$has_geometric_means))sf_args$geoMeans <- read.delim("geometric-means.tsv")$value
        if(isTRUE(p$has_control_genes))sf_args$controlGenes <- as.integer(unlist(p$control_genes))
        dds <- do.call(DESeq2::estimateSizeFactors,sf_args)
    }
    # Estimate on the complete supplied population before the caller's explicit feature selection.
    dds <- dds[as.integer(unlist(p$selected_gene_indices)),]
    mm <- model.matrix(DESeq2::design(dds),data=as.data.frame(SummarizedExperiment::colData(dds)))
    if(ncol(mm)>p$maximum_model_columns)stop("Declared maximum_model_columns exceeded")
    indexed(mm,"model-matrix.tsv",first_name="sample_index")
    if(fit$test!="none") {
        de_args <- list(object=dds,test=fit$test,fitType=fit$fit_type,sfType=fit$size_factor_type,
                        betaPrior=fit$beta_prior,quiet=FALSE,minReplicatesForReplace=if(identical(fit$min_replicates_for_replace,"Inf"))Inf else fit$min_replicates_for_replace,
                        useT=fit$use_t,minmu=fit$min_mu,parallel=FALSE)
        if(fit$test=="LRT")de_args$reduced <- formula_for(p$reduced_design)
        dds <- do.call(DESeq2::DESeq,de_args)
    }
    coefficients <- DESeq2::resultsNames(dds)
    table_out(data.frame(index=seq_along(coefficients),source_coefficient=coefficients),"coefficients.tsv")
    sf <- DESeq2::sizeFactors(dds)
    table_out(data.frame(size_factor=if(is.null(sf))rep(NA_real_,ncol(dds))else sf),"size-factors-original.tsv",colnames(dds),"sample_index")
    if(!is.null(DESeq2::normalizationFactors(dds)))indexed(DESeq2::normalizationFactors(dds),"normalization-factors-original.tsv")
    indexed(DESeq2::counts(dds,normalized=TRUE),"normalized-counts-original.tsv")
    metrics <- as.data.frame(S4Vectors::mcols(dds))
    numeric_columns <- vapply(metrics,function(v)is.numeric(v)||is.logical(v),logical(1))
    if(any(numeric_columns))indexed(metrics[,numeric_columns,drop=FALSE],"gene-estimates.tsv")
    else table_out(data.frame(admitted_gene=rep(TRUE,nrow(dds))),"gene-estimates.tsv",rownames(dds),"gene_index")
    dir.create("results");dir.create("transforms")
    result_settings <- list()
    for(spec in p$results) {
        ra <- c(list(object=dds),select_args(spec$selector,dds),list(lfcThreshold=spec$lfc_threshold,
             altHypothesis=spec$alternative,independentFiltering=spec$independent_filtering,alpha=spec$alpha,
             pAdjustMethod=spec$p_adjust_method,addMLE=spec$add_mle,test=spec$test,parallel=FALSE))
        if(!identical(spec$cooks_cutoff,"source-default"))ra$cooksCutoff <- if(identical(spec$cooks_cutoff,"Inf"))Inf else spec$cooks_cutoff
        res <- do.call(DESeq2::results,ra)
        indexed(as.data.frame(res),file.path("results",paste0(spec$id,".tsv")))
        writeLines(capture.output(str(S4Vectors::metadata(res))),file.path("results",paste0(spec$id,"-metadata.txt")))
        descriptions <- as.data.frame(S4Vectors::mcols(res))
        table_out(descriptions,file.path("results",paste0(spec$id,"-columns.tsv")),rownames(descriptions),"column")
        if(!is.null(spec$shrink)) {
            sh <- spec$shrink;sa <- list(dds=dds,res=res,type=sh$type,lfcThreshold=sh$lfc_threshold,svalue=sh$svalue,quiet=FALSE,parallel=FALSE)
            if(sh$type!="ashr") {
                if(spec$selector$kind=="coefficient")sa$coef <- spec$selector$index
                else sa$contrast <- select_args(spec$selector,dds)$contrast
            }
            shrunk <- do.call(DESeq2::lfcShrink,sa)
            indexed(as.data.frame(shrunk),file.path("results",paste0(spec$id,"-shrunken.tsv")))
            writeLines(capture.output(str(DESeq2::priorInfo(shrunk))),file.path("results",paste0(spec$id,"-shrink-prior.txt")))
        }
        result_settings[[spec$id]] <- spec
    }
    for(spec in p$transforms) {
        args <- list(object=dds,blind=spec$blind,fitType=spec$fit_type)
        if(spec$method=="vst")args$nsub <- spec$nsub
        if(!is.null(spec$beta_prior_variance))args$betaPriorVar <- spec$beta_prior_variance
        transformed <- do.call(getExportedValue("DESeq2",spec$method),args)
        indexed(SummarizedExperiment::assay(transformed),file.path("transforms",paste0(spec$id,".tsv")))
    }
    if(isTRUE(p$save_fitted_object))saveRDS(dds,"fitted-dds.rds",compress=TRUE)
    write.table(data.frame(index=seq_along(.Random.seed),value=.Random.seed),"rng-after.tsv",sep="\t",quote=FALSE,row.names=FALSE)
    effective <- list(profile="DESeq2-1.52.0-R4.6.1",R=as.character(getRversion()),DESeq2=as.character(packageVersion("DESeq2")),
        genes=nrow(dds),samples=ncol(dds),design=paste(deparse(DESeq2::design(dds)),collapse=" "),
        fit=fit,results=result_settings,transforms=p$transforms,source_body_formals_guards=source_guard,
        normalization_prepared_before_explicit_gene_selection=TRUE,
        size_factors_estimated=!isTRUE(p$has_size_factors)&&!isTRUE(p$has_normalization_factors),seed=p$seed,
        actual_dispersion_fit_type=attr(DESeq2::dispersionFunction(dds),"fitType"),
        historical_whole_packages_executed=FALSE,installed_DLL_source_authentication=FALSE)
    jsonlite::write_json(effective,"effective-settings.json",auto_unbox=TRUE,pretty=TRUE,null="null",digits=NA)
    state <- "completed-with-warnings"
},warning=function(w){warnings_seen[[length(warnings_seen)+1L]] <<- list(class=class(w),message=conditionMessage(w))},
message=function(m){messages_seen <<- c(messages_seen,conditionMessage(m))}),
error=function(e){detail <<- conditionMessage(e);if(runtime_ready)state <<- "FAIL2_SOURCE_OR_INPUT_ERROR"})
writeLines(capture.output(sessionInfo()),"session-info.txt")
if(requireNamespace("jsonlite",quietly=TRUE)) {
    loaded <- sort(loadedNamespaces())
    packages <- lapply(loaded,function(pkg)list(package=pkg,version=as.character(packageVersion(pkg)),path=find.package(pkg)))
    jsonlite::write_json(list(status=state,detail=detail,warnings=warnings_seen,messages=unname(messages_seen),
        required_action=action,loaded_packages=packages,source_body_formals_guards=source_guard),
        "worker-report.json",auto_unbox=TRUE,pretty=TRUE,null="null",digits=NA)
}
safe <- function(text)gsub("[\t\r\n]"," ",text)
writeLines(c("status\tdetail\trequired_action",paste(state,safe(detail),safe(action),sep="\t")),"worker-status.tsv")
cat("STATUS:",state,"\nDETAIL:",detail,"\n")
quit(status=if(state=="completed-with-warnings")0L else if(startsWith(state,"SKIP3"))3L else 2L)
