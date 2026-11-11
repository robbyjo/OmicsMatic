# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Typed transport to unchanged original GENESIS2.42.0. No install or source repair.
args <- commandArgs(TRUE)
stopifnot(length(args)==4L)
private_library <- normalizePath(args[[1]],winslash="/",mustWork=TRUE)
bundle <- normalizePath(args[[2]],winslash="/",mustWork=TRUE)
out <- normalizePath(args[[3]],winslash="/",mustWork=TRUE)
parameters <- normalizePath(args[[4]],winslash="/",mustWork=TRUE)
.libPaths(c(private_library,file.path(R.home(),"library")),include.site=FALSE)
stopifnot(getRversion()=="4.6.1",.Machine$longdouble.digits==64L)
for(spec in list(c("GENESIS","2.42.0"),c("GWASTools","1.58.0"),c("SNPRelate","1.46.0"),c("gdsfmt","1.48.2"),c("RhpcBLASctl","0.23-42"))) {
    stopifnot(requireNamespace(spec[[1]],quietly=TRUE),dirname(find.package(spec[[1]]))==private_library,
        utils::packageDescription(spec[[1]],fields="Version")==spec[[2]])
}
stopifnot(!is.na(Sys.setlocale("LC_CTYPE","English_United States.utf8")))
RhpcBLASctl::blas_set_num_threads(1L)
read_tsv <- function(path)utils::read.table(path,header=TRUE,sep="\t",quote="",comment.char="",stringsAsFactors=FALSE,na.strings=character(),check.names=FALSE,colClasses="character",fill=TRUE)
write_tsv <- function(value,path)utils::write.table(value,path,sep="\t",quote=FALSE,row.names=FALSE,na="NA")
settings <- read_tsv(parameters);stopifnot(identical(names(settings),c("key","value")),!anyDuplicated(settings$key))
p <- stats::setNames(as.character(settings$value),settings$key)
flag <- function(name){stopifnot(p[[name]] %in% c("true","false"));p[[name]]=="true"}
numeric_choice <- function(name)as.numeric(p[[name]])
optional_names <- function(name)if(p[[name]]=="")NULL else strsplit(p[[name]],",",fixed=TRUE)[[1L]]
transport <- file.path(bundle,"transport")
hex_string <- function(value)paste(sprintf("%02x",as.integer(charToRaw(enc2utf8(value)))),collapse="")
warnings <- character();messages <- character();handles <- list()
on.exit <- NULL
record_conditions <- function(expr)withCallingHandlers(expr,
    warning=function(w){warnings <<- c(warnings,conditionMessage(w));invokeRestart("muffleWarning")},
    message=function(m){messages <<- c(messages,conditionMessage(m));invokeRestart("muffleMessage")})

# Every original object is retained as RDS. This additional typed ledger records
# primitives/attributes without interpreting R NA as a string or changing values.
emit_object <- function(value,stem) {
    saveRDS(value,file.path(out,paste0(stem,".rds")))
    connection <- file(file.path(out,paste0(stem,"-typed.tsv")),"wt",encoding="UTF-8");on.exit(close(connection))
    writeLines("node\tparent\tslot\tname_utf8_hex\ttype\tlength\telement\tvalue_hex\ttext_utf8_hex\tis_na\tis_nan",connection)
    next_id <- 0L
    walk <- function(x,parent,slot,name,depth) {
        stopifnot(depth<=50L);next_id <<- next_id+1L;id <- next_id;type <- typeof(x)
        line <- function(element="",number="",text="",missing=FALSE,nan=FALSE) {
            writeLines(paste(id,parent,slot,hex_string(name),type,length(x),element,number,text,missing,nan,sep="\t"),connection)
        }
        line()
        if(isS4(x)) {
            for(s in methods::slotNames(x))walk(methods::slot(x,s),id,"S4-slot",s,depth+1L)
        } else if(is.list(x)) {
            labels <- names(x)
            for(i in seq_along(x))walk(x[[i]],id,as.character(i),if(is.null(labels))"" else labels[[i]],depth+1L)
        } else if(is.atomic(x)) {
            for(i in seq_along(x)) {
                if(is.character(x))line(i,text=if(is.na(x[[i]]))"" else hex_string(x[[i]]),missing=is.na(x[[i]]))
                else if(is.complex(x))line(i,text=hex_string(paste(sprintf("%a",Re(x[[i]])),sprintf("%a",Im(x[[i]])),sep=",")),missing=is.na(x[[i]]),nan=is.nan(x[[i]]))
                else if(is.raw(x))line(i,text=as.character(x[[i]]))
                else line(i,number=if(is.na(x[[i]]))if(is.nan(x[[i]]))"NaN" else "NA" else if(is.logical(x))if(x[[i]])"TRUE" else "FALSE" else sprintf("%a",as.double(x[[i]])),missing=is.na(x[[i]]),nan=is.nan(x[[i]]))
            }
        } else if(type %in% c("closure","builtin","special","language","symbol","pairlist","expression")) {
            line(text=hex_string(paste(deparse(x,width.cutoff=500L),collapse="\n")))
        } else if(type=="environment")line(text=hex_string(environmentName(x)))
        else if(!is.null(x))stop("Unsupported original result ledger type: ",type)
        attributes <- attributes(x)
        if(length(attributes))for(key in names(attributes))walk(attributes[[key]],id,"attribute",key,depth+1L)
    }
    walk(value,0L,"root",stem,0L)
}

read_ids <- function(stem)as.character(read_tsv(file.path(transport,paste0(stem,".ids.tsv")))$sample_alias)
read_matrix <- function(stem) {
    ids <- read_ids(stem);values <- read_tsv(file.path(transport,paste0(stem,".values.tsv")))
    result <- matrix(NA_real_,length(ids),length(ids),dimnames=list(ids,ids))
    stopifnot(nrow(values)==length(ids)^2L,!anyDuplicated(paste(values$row,values$column,sep=":")))
    result[cbind(as.integer(values$row),as.integer(values$column))] <- as.numeric(values$value_hex);result
}
emit_matrix <- function(value,stem) {
    original <- as.matrix(value);stopifnot(length(dim(original))==2L)
    row_labels <- rownames(original);column_labels <- colnames(original)
    write_tsv(data.frame(row=seq_len(nrow(original)),row_name_utf8_hex=if(is.null(row_labels))rep("",nrow(original)) else unname(vapply(row_labels,hex_string,character(1L))),label_present=!is.null(row_labels)),file.path(out,paste0(stem,".rows.tsv")))
    write_tsv(data.frame(column=seq_len(ncol(original)),column_name_utf8_hex=if(is.null(column_labels))rep("",ncol(original)) else unname(vapply(column_labels,hex_string,character(1L))),label_present=!is.null(column_labels)),file.path(out,paste0(stem,".columns.tsv")))
    cells <- expand.grid(row=seq_len(nrow(original)),column=seq_len(ncol(original)))
    cells$value_hex <- sprintf("%a",as.vector(original));cells$is_na <- is.na(as.vector(original));cells$is_nan <- is.nan(as.vector(original))
    write_tsv(cells,file.path(out,paste0(stem,".values.tsv")))
}
emit_frame <- function(value,stem) {
    stopifnot(is.data.frame(value));emit_object(value,stem)
    write_tsv(data.frame(column=seq_along(value),name_utf8_hex=vapply(names(value),hex_string,character(1L)),type=vapply(value,typeof,character(1L))),file.path(out,paste0(stem,".schema.tsv")))
    records <- list()
    for(j in seq_along(value)) {
        x <- value[[j]];stopifnot(is.atomic(x),!is.factor(x))
        records[[j]] <- data.frame(row=seq_len(nrow(value)),column=rep.int(j,nrow(value)),value_hex=if(is.numeric(x)||is.logical(x))sprintf("%a",as.double(x)) else rep("",nrow(value)),
            text_utf8_hex=if(is.character(x))unname(vapply(x,function(s)if(is.na(s))"" else hex_string(s),character(1L))) else rep("",nrow(value)),
            is_na=is.na(x),is_nan=if(is.numeric(x))is.nan(x) else rep(FALSE,nrow(value)))
    }
    cells <- if(length(records))do.call(rbind,records) else data.frame(row=integer(),column=integer(),value_hex=character(),text_utf8_hex=character(),is_na=logical(),is_nan=logical())
    write_tsv(cells,file.path(out,paste0(stem,".cells.tsv")))
    write_tsv(data.frame(row=seq_len(nrow(value)),name_utf8_hex=vapply(rownames(value),hex_string,character(1L))),file.path(out,paste0(stem,".rownames.tsv")))
}

emit_model <- function(model,stem) {
    emit_object(model,paste0(stem,"null-model"));emit_frame(model$fixef,paste0(stem,"fixed-effects"));emit_frame(model$fit,paste0(stem,"sample-fits"))
    emit_matrix(model$model.matrix,paste0(stem,"design"));emit_matrix(model$betaCov,paste0(stem,"beta-covariance"))
    write_tsv(data.frame(converged=model$converged,effective_family=model$model$family$family,effective_mixed=model$model$family$mixedmodel,
        zero_flags=if(is.null(model$zeroFLAG))"NULL" else paste(as.character(model$zeroFLAG),collapse=",")),file.path(out,paste0(stem,"model-status.tsv")))
    components <- model$varComp;labels <- names(components)
    write_tsv(data.frame(component=seq_along(components),name_utf8_hex=if(is.null(labels))rep("",length(components)) else unname(vapply(labels,hex_string,character(1L))),
        name_present=rep(!is.null(labels),length(components)),value_hex=sprintf("%a",components),is_na=is.na(components),is_nan=is.nan(components)),file.path(out,paste0(stem,"variance-components.tsv")))
}

emit_normalisation <- function(stage1,covariance) {
    residuals <- stage1$fit$resid.marginal;n <- length(residuals);groups <- stage1$group.idx
    by_group <- p[["normalisation"]]=="by.group"
    indices <- if(by_group)groups else list(`__all__`=seq_len(n))
    stopifnot(length(indices)>0L)
    ranks <- scores <- variances <- scales <- normalized <- numeric(n);labels <- character(n)
    for(i in seq_along(indices)) {
        selected <- indices[[i]];original <- residuals[selected]
        variance <- if(p[["rescale"]]=="none")1 else if(p[["rescale"]]=="residSD")stats::var(original) else GENESIS:::.averageGroupVar(stage1$varComp,covariance,groups[i])
        unit_scores <- GENESIS:::.rankNorm(original)
        ranks[selected] <- rank(original);scores[selected] <- unit_scores;variances[selected] <- variance;scales[selected] <- sqrt(variance);normalized[selected] <- unit_scores*sqrt(variance);labels[selected] <- names(indices)[i]
    }
    write_tsv(data.frame(model_row=seq_len(n),sample_alias=rownames(stage1$model.matrix),group_utf8_hex=unname(vapply(labels,hex_string,character(1L))),
        residual_hex=sprintf("%a",residuals),average_rank_hex=sprintf("%a",ranks),unit_score_hex=sprintf("%a",scores),variance_hex=sprintf("%a",variances),
        scale_hex=sprintf("%a",scales),normalised_outcome_hex=sprintf("%a",normalized)),file.path(out,"original-normalisation.tsv"))
}

failure <- NULL
tryCatch(record_conditions({
    versions <- c("GENESIS","GWASTools","SNPRelate","gdsfmt","RhpcBLASctl","BiocParallel","Matrix","igraph")
    write_tsv(data.frame(package=versions,version=vapply(versions,function(name)utils::packageDescription(name,fields="Version"),character(1L))),file.path(out,"original-package-versions.tsv"))
    write_tsv(settings,file.path(out,"original-effective-parameters.tsv"))
    sample_map <- read_tsv(file.path(transport,"sample-map.tsv"));all_ids <- as.character(sample_map$sample_alias)
    selected <- if(flag("has_selection"))read_ids("selection") else NULL
    if(flag("has_genotypes")) {
        fam <- utils::read.table(file.path(bundle,"inputs/cohort.fam"),header=FALSE,colClasses="character",quote="",comment.char="",na.strings=character())
        bim <- utils::read.table(file.path(bundle,"inputs/cohort.bim"),header=FALSE,colClasses="character",quote="",comment.char="",na.strings=character())
        calls <- as.matrix(read_tsv(file.path(bundle,"inputs/native-calls.tsv"))[,-1,drop=FALSE]);storage.mode(calls) <- "integer"
        stopifnot(ncol(calls)==nrow(fam),nrow(calls)==nrow(bim))
        gds_file <- file.path(out,"original-transport.gds")
        SNPRelate::snpgdsCreateGeno(gds_file,genmat=if(p[["layout"]]=="snp-first")calls else t(calls),sample.id=as.integer(all_ids),
            snp.id=seq_len(nrow(bim)),snp.rs.id=bim[[2L]],snp.chromosome=as.integer(bim[[1L]]),snp.position=as.integer(bim[[4L]]),
            snp.allele=paste(bim[[5L]],bim[[6L]],sep="/"),snpfirstdim=p[["layout"]]=="snp-first")
        # A separate original readonly genotype view proves lossless transport.
        original_gds <- gdsfmt::openfn.gds(gds_file,readonly=TRUE)
        original_calls <- SNPRelate::snpgdsGetGeno(original_gds,sample.id=as.integer(all_ids),snp.id=seq_len(nrow(bim)),snpfirstdim=TRUE,verbose=FALSE)
        write_tsv(data.frame(input_variant_row=seq_len(nrow(original_calls)),original_calls,check.names=FALSE),file.path(out,"original-complete-genotypes.tsv"))
        gdsfmt::closefn.gds(original_gds)
    }
    if(p[["mode"]]=="pcair") {
        gds <- if(p[["gds_handle"]]=="gds.class")gdsfmt::openfn.gds(gds_file,readonly=TRUE) else SNPRelate::snpgdsOpen(gds_file,readonly=TRUE)
        handles[[length(handles)+1L]] <- list(object=gds,type=p[["gds_handle"]])
        kinship <- if(flag("has_kinship"))read_matrix("kinship") else NULL
        divergence <- if(flag("has_divergence"))read_matrix("divergence") else NULL
        forced <- if(flag("has_forcing"))read_ids("forcing") else NULL
        set.seed(as.integer(p[["seed"]]));saveRDS(.Random.seed,file.path(out,"original-RNG-before.rds"))
        result <- GENESIS::pcair(gds,kinobj=kinship,divobj=divergence,kin.thresh=numeric_choice("kinship_threshold"),div.thresh=numeric_choice("divergence_threshold"),
            unrel.set=forced,sample.include=selected,snp.include=seq_len(nrow(bim)),num.cores=1L,verbose=FALSE,
            eigen.cnt=as.integer(p[["eigen_count"]]),algorithm=p[["algorithm"]],eigen.method=p[["eigen_method"]],bayesian=flag("bayesian"),
            aux.dim=as.integer(p[["aux_dim"]]),iter.num=as.integer(p[["iterations"]]),autosome.only=TRUE,remove.monosnp=flag("remove_monomorphic"),
            maf=numeric_choice("maf"),missing.rate=numeric_choice("missing_rate"),need.genmat=FALSE)
        saveRDS(.Random.seed,file.path(out,"original-RNG-after.rds"));emit_object(result,"original-result")
        stopifnot(is.matrix(result$vectors),!is.null(result$sample.id),!is.null(result$nsamp),nrow(result$vectors)==result$nsamp,
            identical(rownames(result$vectors),as.character(result$sample.id)))
        emit_matrix(result$vectors,"original-PC-vectors")
        write_tsv(data.frame(component=seq_along(result$values),value_hex=sprintf("%a",result$values),is_na=is.na(result$values),is_nan=is.nan(result$values)),file.path(out,"original-PC-eigenvalues.tsv"))
        write_tsv(data.frame(component=seq_along(result$varprop),value_hex=sprintf("%a",result$varprop),is_na=is.na(result$varprop),is_nan=is.nan(result$varprop)),file.path(out,"original-PC-variance-proportions.tsv"))
        write_tsv(rbind(data.frame(set=rep("related",length(result$rels)),source_order=seq_along(result$rels),sample_alias=as.character(result$rels)),
            data.frame(set=rep("unrelated",length(result$unrels)),source_order=seq_along(result$unrels),sample_alias=as.character(result$unrels))),file.path(out,"original-PC-partition.tsv"))
        write_tsv(data.frame(method=result$method,samples=result$nsamp,variants=result$nsnps,kinship_threshold_hex=sprintf("%a",result$kin.thresh),divergence_threshold_hex=sprintf("%a",result$div.thresh)),file.path(out,"original-PC-summary.tsv"))
        # Read-only source QC selection on the actual unrelated PCA population.
        qc <- SNPRelate:::.InitFile2(cmd=NULL,gdsobj=gds,sample.id=result$unrels,snp.id=seq_len(nrow(bim)),autosome.only=TRUE,remove.monosnp=flag("remove_monomorphic"),
            maf=numeric_choice("maf"),missing.rate=numeric_choice("missing_rate"),verbose=FALSE)
        write_tsv(data.frame(result_row=seq_along(qc$snp.id),input_variant_row=qc$snp.id),file.path(out,"original-PC-QC-variants.tsv"))
    } else {
        if(flag("has_genotypes")) {
            reader <- GWASTools::GdsGenotypeReader(gds_file,genotypeDim=if(p[["layout"]]=="snp-first")"snp,scan" else "scan,snp",allow.fork=FALSE)
            handles[[length(handles)+1L]] <- list(object=reader,type="GWASTools-reader")
            sex <- ifelse(fam[[5L]]=="1","M",ifelse(fam[[5L]]=="2","F",NA_character_))
            scans <- GWASTools::ScanAnnotationDataFrame(data.frame(scanID=as.integer(all_ids),sex=sex))
            snps <- GWASTools::SnpAnnotationDataFrame(data.frame(snpID=seq_len(nrow(bim)),chromosome=as.integer(bim[[1L]]),position=as.integer(bim[[4L]])))
            geno <- GWASTools::GenotypeData(reader,snpAnnot=snps,scanAnnot=scans)
            iterator <- function()GWASTools::GenotypeBlockIterator(geno,snpBlock=as.integer(p[["snp_block_size"]]),snpInclude=seq_len(nrow(bim)))
        }
        if(p[["mode"]]=="pcrelate") {
            pcs <- read_tsv(file.path(transport,"pcs.tsv"));rownames(pcs) <- as.character(pcs$sample_alias)
            pcs <- as.matrix(pcs[,optional_names("pc_columns"),drop=FALSE]);storage.mode(pcs) <- "double"
            training <- if(flag("has_training"))read_ids("training") else NULL
            set.seed(as.integer(p[["seed"]]));saveRDS(.Random.seed,file.path(out,"original-RNG-before.rds"))
            result <- GENESIS::pcrelate(iterator(),pcs=pcs,scale=p[["scale"]],ibd.probs=flag("ibd_probabilities"),sample.include=selected,training.set=training,
                sample.block.size=as.integer(p[["sample_block_size"]]),maf.thresh=numeric_choice("maf_threshold"),maf.bound.method=p[["maf_bound_method"]],
                small.samp.correct=flag("small_sample_correct"),BPPARAM=BiocParallel::SerialParam(),verbose=FALSE)
            saveRDS(.Random.seed,file.path(out,"original-RNG-after.rds"));emit_object(result,"original-result")
            emit_frame(result$kinSelf,"original-kinship-self");emit_frame(result$kinBtwn,"original-kinship-between")
            if(flag("covariance_output")) {
                threshold <- if(p[["covariance_threshold"]]=="NULL")NULL else numeric_choice("covariance_threshold")
                covariance <- GENESIS::pcrelateToMatrix(result,sample.include=if(is.null(selected))all_ids else selected,thresh=threshold,scaleKin=numeric_choice("scale_kin"),verbose=FALSE)
                emit_object(covariance,"original-derived-covariance");emit_matrix(covariance,"original-derived-covariance")
            }
        } else {
            traits <- read_tsv(file.path(transport,"phenotypes.tsv"));rownames(traits) <- as.character(traits$sample_alias);traits$sample_alias <- NULL
            for(column in unique(c(p[["outcome"]],optional_names("covariates"))))traits[[column]] <- as.numeric(traits[[column]])
            covariances <- optional_names("covariance_names")
            covariance <- if(is.null(covariances))NULL else stats::setNames(lapply(covariances,function(name)read_matrix(paste0("covariance-",name))),covariances)
            starts <- if(p[["start"]]=="NULL")NULL else as.numeric(strsplit(p[["start"]],",",fixed=TRUE)[[1L]])
            fit_original <- function(two_stage)GENESIS::fitNullModel(traits,outcome=p[["outcome"]],covars=optional_names("covariates"),cov.mat=covariance,
                group.var=if(p[["group_variable"]]=="NULL")NULL else p[["group_variable"]],family=p[["family"]],two.stage=two_stage,norm.option=p[["normalisation"]],rescale=p[["rescale"]],
                start=starts,AIREML.tol=numeric_choice("tolerance"),max.iter=as.integer(p[["max_iterations"]]),EM.iter=as.integer(p[["EM_iterations"]]),drop.zeros=flag("drop_zeros"),return.small=FALSE,verbose=FALSE)
            # The original two-stage API does not retain its first-stage fit.
            # This separate deterministic control fit uses the same declared
            # settings and is recorded in full, before the unchanged two-stage call.
            if(flag("two_stage")) { stage1 <- fit_original(FALSE);emit_model(stage1,"original-stage1-") }
            model <- fit_original(flag("two_stage"));emit_model(model,"original-")
            if(flag("two_stage"))emit_normalisation(stage1,covariance)
            if(p[["mode"]]=="association") {
                stopifnot(isTRUE(model$converged))
                original_selection <- GENESIS:::.sampleIndexNullModel(iterator(),model)
                write_tsv(data.frame(model_row=seq_along(original_selection),input_sample_row=original_selection,
                    source_scan_id=as.character(GWASTools::getScanID(geno)[original_selection]),model_sample_alias=rownames(model$model.matrix)),file.path(out,"original-association-sample-selection.tsv"))
                result <- GENESIS::assocTestSingle(iterator(),null.model=model,test=p[["test"]],recalc.pval.thresh=numeric_choice("recalc_p_threshold"),
                    GxE=if(p[["interaction"]]=="NULL")NULL else p[["interaction"]],geno.coding=p[["coding"]],male.diploid=flag("male_diploid"),BPPARAM=BiocParallel::SerialParam(),verbose=FALSE)
                emit_frame(result,"original-association")
                effective_test <- if(p[["interaction"]]!="NULL")"Wald.GxE" else if(p[["test"]]=="Score.SPA" && model$model$family$family!="binomial")"Score" else if(p[["test"]]=="CMP" && !model$model$family$mixedmodel)"BinomiRare" else p[["test"]]
                score_control <- p[["test"]]!="Score" || p[["interaction"]]!="NULL"
                if(score_control) {
                    ordinary_score <- GENESIS::assocTestSingle(iterator(),null.model=model,test="Score",recalc.pval.thresh=numeric_choice("recalc_p_threshold"),
                        GxE=NULL,geno.coding=p[["coding"]],male.diploid=flag("male_diploid"),BPPARAM=BiocParallel::SerialParam(),verbose=FALSE)
                    emit_frame(ordinary_score,"original-score-control")
                }
                if(effective_test=="BinomiRare") {
                    probabilities <- if(model$model$family$mixedmodel)GENESIS:::expit(model$fit$linear.predictor) else model$fit$fitted.values
                    model_ids <- rownames(model$model.matrix)
                    write_tsv(data.frame(model_row=seq_along(model_ids),sample_alias=model_ids,probability_hex=sprintf("%a",probabilities)),file.path(out,"original-association-probabilities.tsv"))
                    pmf_records <- list()
                    # A separate original poibin call on explicitly reconstructed
                    # derived test inputs. Original hard calls remain unchanged.
                    for(i in seq_len(nrow(result))) {
                        variant <- as.integer(result$variant.id[i]);g <- calls[variant,match(model_ids,all_ids)];g[g==3L] <- NA_real_
                        if(p[["coding"]]=="dominant")g[g==2L & !is.na(g)] <- 1
                        if(p[["coding"]]=="recessive") { g[g==1L & !is.na(g)] <- 0;g[g==2L & !is.na(g)] <- 1 }
                        g[is.na(g)] <- 2*result$freq[i];carriers <- which(g>0)
                        source_score_p <- ordinary_score$Score.pval[match(variant,ordinary_score$variant.id)]
                        if(source_score_p<=numeric_choice("recalc_p_threshold")) {
                            mass <- poibin::dpoibin(seq.int(0L,length(carriers)),probabilities[carriers])
                            pmf_records[[length(pmf_records)+1L]] <- data.frame(input_variant_row=variant,successes=seq.int(0L,length(carriers)),probability_hex=sprintf("%a",mass))
                        }
                    }
                    pmfs <- if(length(pmf_records))do.call(rbind,pmf_records) else data.frame(input_variant_row=integer(),successes=integer(),probability_hex=character())
                    write_tsv(pmfs,file.path(out,"original-poibin-control-pmfs.tsv"))
                }
                write_tsv(data.frame(requested_test=p[["test"]],effective_test=effective_test,variants=nrow(result),interaction=p[["interaction"]],score_control_executed=score_control),file.path(out,"original-association-status.tsv"))
            }
        }
    }
}),error=function(error){failure <<- conditionMessage(error);writeLines(c(conditionMessage(error),paste(deparse(conditionCall(error)),collapse="\n")),file.path(out,"original-error.txt"),useBytes=TRUE)})
for(handle in rev(handles)) {
    tryCatch(if(handle$type=="GWASTools-reader")GWASTools::close(handle$object) else if(handle$type=="gds.class")gdsfmt::closefn.gds(handle$object) else SNPRelate::snpgdsClose(handle$object),
        error=function(error){
            if(is.null(failure))failure <<- conditionMessage(error)
            writeLines(c(conditionMessage(error),paste(deparse(conditionCall(error)),collapse="\n")),file.path(out,"original-close-error.txt"),useBytes=TRUE)
        })
}
writeLines(warnings,file.path(out,"original-warnings.txt"),useBytes=TRUE)
writeLines(messages,file.path(out,"original-messages.txt"),useBytes=TRUE)
optional_packages <- c("SPAtest","poibin","COMPoissonReg")
write_tsv(data.frame(package=optional_packages,installed=vapply(optional_packages,function(name)file.exists(file.path(private_library,name,"DESCRIPTION")),logical(1L)),
    version=vapply(optional_packages,function(name)if(file.exists(file.path(private_library,name,"DESCRIPTION")))utils::packageDescription(name,lib.loc=private_library,fields="Version") else "UNAVAILABLE",character(1L)),
    namespace_loaded=vapply(optional_packages,isNamespaceLoaded,logical(1L))),file.path(out,"original-optional-package-versions.tsv"))
if(!is.null(failure))quit(save="no",status=2L)
cat("ORIGINAL_GENESIS_EXPLICIT_WORKFLOW_COMPLETE\n")
