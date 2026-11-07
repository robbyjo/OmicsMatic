# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent public-API reference calls; does not source the adapter worker.
p <- commandArgs(TRUE);stopifnot(length(p)==7L,!dir.exists(p[[1]]),dir.create(p[[1]]))
.libPaths(c(p[2:7],.Library),include.site=FALSE)
stopifnot(identical(as.character(packageVersion("DESeq2")),"1.52.0"))
BiocParallel::register(BiocParallel::SerialParam())
num <- function(x)vapply(x,function(z)if(is.nan(z))"NaN" else if(is.na(z))"NA" else if(is.infinite(z))if(z>0)"Inf" else "-Inf" else trimws(formatC(z,digits=17L,format="g")),character(1))
write_table <- function(x,path,ids=rownames(x),first="gene") {
    x <- as.data.frame(x,check.names=FALSE);values <- lapply(x,function(v)if(is.numeric(v))num(v)else if(is.logical(v))ifelse(is.na(v),"NA",ifelse(v,"TRUE","FALSE"))else as.character(v))
    if(!is.null(ids))values <- c(setNames(list(ids),first),values)
    write.table(as.data.frame(values,check.names=FALSE),path,sep="\t",quote=FALSE,row.names=FALSE,na="NA")
}
factor_var <- function(name,levels)list(name=name,kind="factor",levels=levels)
numeric_var <- function(name)list(name=name,kind="numeric")
term <- function(...)list(...)
design <- function(...)list(intercept=TRUE,terms=list(...))
contrast <- function(numerator="treated",denominator="control")list(kind="factor",factor="condition",numerator=numerator,denominator=denominator)
q <- function(id,selector,call,settings=list(),shrink=NULL)list(id=id,selector=selector,call=call,settings=settings,shrink=shrink)
records <- list()
run_case <- function(name,x,meta,variables,model,api_design,fit_args,queries=list(),transforms=list(),extra=list()) {
    folder <- file.path(p[[1]],name);dir.create(folder)
    rownames(x) <- paste0("g",seq_len(nrow(x)));colnames(x) <- paste0("s",seq_len(ncol(x)));rownames(meta) <- colnames(x)
    write_table(x,file.path(folder,"counts.tsv"));write_table(meta,file.path(folder,"samples.tsv"),rownames(meta),"sample")
    fit_spec <- list(test=fit_args$test,fit_type=fit_args$fitType,size_factor_type=if(is.null(extra$sf_type))"ratio" else extra$sf_type,min_replicates_for_replace=if(is.null(fit_args$minReplicatesForReplace))"Inf" else fit_args$minReplicatesForReplace)
    if(!is.null(fit_args$betaPrior))fit_spec$beta_prior <- fit_args$betaPrior
    if(!is.null(fit_args$useT))fit_spec$use_t <- fit_args$useT
    if(!is.null(fit_args$minmu))fit_spec$min_mu <- fit_args$minmu
    request <- list(schema_version=1L,mode="ANALYZE",profile="DESeq2-1.52.0-R4.6.1",counts="counts.tsv",samples="samples.tsv",out="analysis",variables=variables,design=api_design,fit=fit_spec,results=list(),transforms=list(),seed=9221L,save_fitted_object=TRUE)
    if(!is.null(extra$reduced)){request$reduced_design <- extra$reduced_api}
    if(isTRUE(extra$round)){request$units <- "estimated_counts";request$count_preparation <- "R_round"}
    if(!is.null(extra$collapse))request$collapse_by <- extra$collapse
    if(!is.null(extra$keep)){write.table(data.frame(gene=paste0("g",extra$keep)),file.path(folder,"keep.tsv"),sep="\t",quote=FALSE,row.names=FALSE);request$keep_genes <- "keep.tsv"}
    if(!is.null(extra[["sf"]])){write.table(data.frame(sample=colnames(x),size_factor=unname(num(extra[["sf"]]))),file.path(folder,"sf.tsv"),sep="\t",quote=FALSE,row.names=FALSE);request$size_factors <- "sf.tsv"}
    if(!is.null(extra$nf)){dimnames(extra$nf) <- dimnames(x);write_table(extra$nf,file.path(folder,"nf.tsv"));request$normalization_factors <- "nf.tsv"}
    if(!is.null(extra$gm)){write.table(data.frame(gene=rownames(x),geometric_mean=num(extra$gm)),file.path(folder,"gm.tsv"),sep="\t",quote=FALSE,row.names=FALSE);request$geometric_means <- "gm.tsv"}
    if(!is.null(extra$controls)){write.table(data.frame(gene=paste0("g",extra$controls)),file.path(folder,"controls.tsv"),sep="\t",quote=FALSE,row.names=FALSE);request$control_genes <- "controls.tsv"}
    for(query in queries){spec <- c(list(id=query$id,selector=query$selector),query$settings);if(!is.null(query$shrink))spec$shrink <- query$shrink;request$results[[length(request$results)+1L]] <- spec}
    for(t in transforms)request$transforms[[length(request$transforms)+1L]] <- t$spec
    jsonlite::write_json(request,file.path(folder,"request-template.json"),auto_unbox=TRUE,pretty=TRUE,null="null",digits=NA)
    errors <- NULL;warnings_seen <- character();state <- "failed";outputs <- list()
    tryCatch(withCallingHandlers({
        set.seed(9221L)
        prepared <- if(isTRUE(extra$round))round(x)else x
        storage.mode(prepared) <- "integer"
        cd <- S4Vectors::DataFrame(meta)
        for(v in variables)if(v$kind=="factor")cd[[v$name]] <- factor(cd[[v$name]],levels=v$levels)
        dds <- DESeq2::DESeqDataSetFromMatrix(prepared,cd,model)
        if(!is.null(extra$collapse))dds <- DESeq2::collapseReplicates(dds,factor(meta[[extra$collapse]],levels=unique(meta[[extra$collapse]])),run=colnames(x),renameCols=TRUE)
        write_table(DESeq2::counts(dds),file.path(folder,"original-prepared.tsv"))
        if(!is.null(extra$nf))DESeq2::normalizationFactors(dds) <- extra$nf
        else if(!is.null(extra[["sf"]]))DESeq2::sizeFactors(dds) <- extra[["sf"]]
        else {
            a <- list(object=dds,type=fit_spec$size_factor_type,quiet=FALSE)
            if(!is.null(extra$gm))a$geoMeans <- extra$gm
            if(!is.null(extra$controls))a$controlGenes <- extra$controls
            dds <- do.call(DESeq2::estimateSizeFactors,a)
        }
        if(!is.null(extra$keep))dds <- dds[extra$keep,]
        if(fit_args$test!="none") {
            fa <- list(object=dds,quiet=FALSE,parallel=FALSE,minReplicatesForReplace=Inf)
            for(setting in names(fit_args))fa[[setting]] <- fit_args[[setting]]
            if(!is.null(extra$reduced))fa$reduced <- extra$reduced
            dds <- do.call(DESeq2::DESeq,fa)
        }
        write_table(DESeq2::counts(dds,normalized=TRUE),file.path(folder,"original-normalized.tsv"))
        sf <- DESeq2::sizeFactors(dds);if(!is.null(sf))write_table(data.frame(size_factor=sf),file.path(folder,"original-factors.tsv"),colnames(dds),"sample")
        for(query in queries) {
            results <- query$call(dds)
            for(kind in names(results)){path <- paste0("original-",query$id,"-",kind,".tsv");write_table(as.data.frame(results[[kind]]),file.path(folder,path));outputs[[path]] <- list(type="result",id=query$id,kind=kind)}
        }
        for(t in transforms){value <- t$call(dds);path <- paste0("original-transform-",t$spec$id,".tsv");write_table(SummarizedExperiment::assay(value),file.path(folder,path));outputs[[path]] <- list(type="transform",id=t$spec$id)}
        state <- "success"
    },warning=function(w){warnings_seen <<- c(warnings_seen,conditionMessage(w))}),error=function(e){errors <<- conditionMessage(e)})
    record <- list(case=name,status=state,error=errors,warnings=unname(warnings_seen),outputs=outputs)
    jsonlite::write_json(record,file.path(folder,"ORIGINAL.json"),auto_unbox=TRUE,pretty=TRUE,null="null",digits=NA)
    records[[name]] <<- record
    cat(name,state,if(is.null(errors))""else errors,"\n")
}
set.seed(32731L)
x <- DESeq2::counts(DESeq2::makeExampleDESeqDataSet(n=240L,m=12L,betaSD=1))
x[1:50,] <- rpois(50L*12L,.2);x[1,] <- 0L;x[2,] <- 10L;x[2,1] <- 1000000L
meta <- data.frame(donor=rep(paste0("d",1:6),2L),condition=rep(c("control","treated"),each=6L))
vars <- list(factor_var("donor",paste0("d",1:6)),factor_var("condition",c("control","treated")))
queries <- list(
 q("paired",contrast(),function(d)list(base=DESeq2::results(d,contrast=c("condition","treated","control"),alpha=.05)),list(alpha=.05)),
 q("threshold",contrast(),function(d)list(base=DESeq2::results(d,contrast=c("condition","treated","control"),alpha=.05,lfcThreshold=1,altHypothesis="greaterAbs",independentFiltering=FALSE,cooksCutoff=FALSE)),list(alpha=.05,lfc_threshold=1,alternative="greaterAbs",independent_filtering=FALSE,cooks_cutoff=FALSE)),
 q("one_sided",contrast(),function(d)list(base=DESeq2::results(d,contrast=c("condition","treated","control"),alpha=.05,altHypothesis="less",independentFiltering=FALSE,cooksCutoff=FALSE)),list(alpha=.05,alternative="less",independent_filtering=FALSE,cooks_cutoff=FALSE)),
 q("normal",list(kind="coefficient",index=7L),function(d){r <- DESeq2::results(d,name=DESeq2::resultsNames(d)[7L]);list(base=r,shrunken=DESeq2::lfcShrink(d,coef=7L,res=r,type="normal"))},shrink=list(type="normal")),
 q("apeglm",list(kind="coefficient",index=7L),function(d){r <- DESeq2::results(d,name=DESeq2::resultsNames(d)[7L]);list(base=r,shrunken=DESeq2::lfcShrink(d,coef=7L,res=r,type="apeglm"))},shrink=list(type="apeglm")),
 q("ashr",contrast(),function(d){r <- DESeq2::results(d,contrast=c("condition","treated","control"));list(base=r,shrunken=DESeq2::lfcShrink(d,res=r,type="ashr"))},shrink=list(type="ashr")))
transforms <- list(list(spec=list(id="vst_full",method="varianceStabilizingTransformation",blind=FALSE,fit_type="mean"),call=function(d)DESeq2::varianceStabilizingTransformation(d,blind=FALSE,fitType="mean")),list(spec=list(id="rlog",method="rlog",blind=FALSE,fit_type="mean"),call=function(d)DESeq2::rlog(d,blind=FALSE,fitType="mean")),list(spec=list(id="vst_fast",method="vst",blind=TRUE,fit_type="mean",nsub=50L),call=function(d)DESeq2::vst(d,blind=TRUE,fitType="mean",nsub=50L)))
run_case("paired",x,meta,vars,~donor+condition,design(term("donor"),term("condition")),list(test="Wald",fitType="mean"),queries,transforms,extra=list(keep=c(240L,1L,2L,3:199)))
set.seed(31951L);y <- DESeq2::counts(DESeq2::makeExampleDESeqDataSet(n=240L,m=18L,betaSD=.8));y[1,] <- 0L
meta3 <- data.frame(batch=rep(c("b1","b2"),9L),condition=rep(c("control","treated","third"),each=6L))
v3 <- list(factor_var("batch",c("b1","b2")),factor_var("condition",c("control","treated","third")))
lrtq <- list(q("third",contrast("third"),function(d)list(base=DESeq2::results(d,contrast=c("condition","third","control")))),q("treated",contrast(),function(d)list(base=DESeq2::results(d,contrast=c("condition","treated","control")))))
run_case("lrt",y,meta3,v3,~batch+condition,design(term("batch"),term("condition")),list(test="LRT",fitType="mean"),lrtq,extra=list(reduced=~batch,reduced_api=design(term("batch"))))
set.seed(6114L);z <- DESeq2::counts(DESeq2::makeExampleDESeqDataSet(n=240L,m=16L,betaSD=.8))
mi <- data.frame(sex=rep(c("F","M"),each=8L),condition=rep(rep(c("control","treated"),each=4L),2L))
vi <- list(factor_var("sex",c("F","M")),factor_var("condition",c("control","treated")))
iq <- list(q("interaction",list(kind="numeric",weights=c(0,0,1,1)),function(d)list(base=DESeq2::results(d,contrast=c(0,0,1,1)))),q("interaction_shrink",list(kind="coefficient",index=4L),function(d){r <- DESeq2::results(d,name=DESeq2::resultsNames(d)[4L]);list(base=r,shrunken=DESeq2::lfcShrink(d,coef=4L,res=r,type="apeglm"))},shrink=list(type="apeglm")))
run_case("interaction",z,mi,vi,~sex+condition+sex:condition,design(term("sex"),term("condition"),term("sex","condition")),list(test="Wald",fitType="mean"),iq)
pos <- x;for(g in seq_len(nrow(pos)))pos[g,(g-1L)%%ncol(pos)+1L] <- 0L
simple <- list(q("difference",contrast(),function(d)list(base=DESeq2::results(d,contrast=c("condition","treated","control")))))
run_case("poscounts",pos,meta,vars,~donor+condition,design(term("donor"),term("condition")),list(test="Wald",fitType="mean"),simple,extra=list(sf_type="poscounts",controls=c(1:100,3L,3L),gm=rep(1,nrow(pos))))
run_case("supplied_factors",x,meta,vars,~donor+condition,design(term("donor"),term("condition")),list(test="Wald",fitType="parametric"),simple,extra=list(sf=exp(seq(-.3,.3,length.out=ncol(x)))))
nf <- outer(seq(.8,1.2,length.out=nrow(x)),exp(seq(-.2,.2,length.out=ncol(x))))
run_case("normalization_matrix",x,meta,vars,~donor+condition,design(term("donor"),term("condition")),list(test="Wald",fitType="mean"),simple,extra=list(nf=nf,keep=c(240L,1:200)))
set.seed(7301L);c <- DESeq2::counts(DESeq2::makeExampleDESeqDataSet(n=240L,m=6L,betaSD=.7));raw <- c[,rep(1:6,each=2L)]/2
mc <- data.frame(donor=rep(rep(paste0("d",1:3),2L),each=2L),condition=rep(c("control","treated"),each=6L),biological_sample=rep(paste0("bio",1:6),each=2L))
vc <- list(factor_var("donor",paste0("d",1:3)),factor_var("condition",c("control","treated")))
run_case("round_collapse",raw,mc,vc,~donor+condition,design(term("donor"),term("condition")),list(test="Wald",fitType="mean"),simple,extra=list(round=TRUE,collapse="biological_sample"))
run_case("transform_only",x,meta,vars,~donor+condition,design(term("donor"),term("condition")),list(test="none",fitType="mean"),transforms=transforms,extra=list(sf_type="iterate"))
cov <- meta3;cov$sex <- rep(c("F","M","M"),6L);cov$mito <- .05+sin(1:18)/100;cov$ambient <- .1+cos(1:18)/100
vv <- c(v3,list(factor_var("sex",c("F","M")),numeric_var("mito"),numeric_var("ambient")))
run_case("covariates",y,cov,vv,~batch+sex+mito+ambient+condition,design(term("batch"),term("sex"),term("mito"),term("ambient"),term("condition")),list(test="Wald",fitType="mean"),lrtq)
glmq <- list(q("third",list(kind="coefficient",index=4L),function(d)list(base=DESeq2::results(d,name=DESeq2::resultsNames(d)[4L]))),q("treated",list(kind="coefficient",index=3L),function(d)list(base=DESeq2::results(d,name=DESeq2::resultsNames(d)[3L]))))
run_case("glm_lrt",y,meta3,v3,~batch+condition,design(term("batch"),term("condition")),list(test="LRT",fitType="glmGamPoi",minmu=1e-6),glmq,extra=list(reduced=~batch,reduced_api=design(term("batch"))))
betaq <- list(q("map",contrast(),function(d)list(base=DESeq2::results(d,contrast=c("condition","treated","control"),addMLE=TRUE,independentFiltering=FALSE)),list(add_mle=TRUE,independent_filtering=FALSE)))
run_case("beta_prior",x,meta["condition"],list(vars[[2]]),~condition,design(term("condition")),list(test="Wald",fitType="mean",betaPrior=TRUE),betaq)
tq <- list(q("t_test",contrast(),function(d)list(base=DESeq2::results(d,contrast=c("condition","treated","control"),alpha=.07591234567,cooksCutoff=.5,pAdjustMethod="BY")),list(alpha=.07591234567,cooks_cutoff=.5,p_adjust_method="BY")))
run_case("t_wald",x,meta,vars,~donor+condition,design(term("donor"),term("condition")),list(test="Wald",fitType="mean",useT=TRUE,minmu=.2),tq)
outlier <- DESeq2::counts(DESeq2::makeExampleDESeqDataSet(n=240L,m=18L,betaSD=.9));outlier[1,] <- 0L;outlier[2,] <- 10L;outlier[2,1] <- 1000000L
mo <- data.frame(condition=rep(c("control","treated"),each=9L))
run_case("outlier_replacement",outlier,mo,list(vars[[2]]),~condition,design(term("condition")),list(test="Wald",fitType="mean",minReplicatesForReplace=7L),simple)
jsonlite::write_json(records,file.path(p[[1]],"ORIGINAL-SUMMARY.json"),auto_unbox=TRUE,pretty=TRUE,null="null",digits=NA)
writeLines(capture.output(sessionInfo()),file.path(p[[1]],"session-info.txt"))
stopifnot(all(vapply(records,function(r)r$status=="success",logical(1))))
