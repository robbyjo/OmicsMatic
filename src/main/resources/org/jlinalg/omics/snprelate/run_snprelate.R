# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Typed original SNPRelate execution. Scientific admission belongs to the Java audit.
args <- commandArgs(trailingOnly=TRUE);stopifnot(length(args)==4L)
library_path <- args[1L]; prefix <- args[2L]; work <- args[3L]; parameter_path <- args[4L]
stopifnot(nzchar(Sys.setlocale("LC_CTYPE","English_United States.utf8")),isTRUE(l10n_info()[["UTF-8"]]))
.libPaths(c(library_path,file.path(R.home(),"library")),include.site=FALSE)
stopifnot(getRversion()=="4.6.1")
for(package in c("gdsfmt","RhpcBLASctl","SNPRelate"))stopifnot(requireNamespace(package,quietly=TRUE),
    dirname(find.package(package))==normalizePath(library_path,winslash="/"))
versions <- c(SNPRelate="1.46.0",gdsfmt="1.48.2",RhpcBLASctl="0.23-42")
for(package in names(versions))stopifnot(utils::packageDescription(package,fields="Version")==versions[[package]])
RhpcBLASctl::blas_set_num_threads(1L)
write_tsv <- function(x,name) utils::write.table(x,file.path(work,name),sep="\t",quote=FALSE,row.names=FALSE,col.names=TRUE,na="NA",fileEncoding="UTF-8")
read_tsv <- function(path,header=TRUE,sep="\t",classes="character") utils::read.table(path,header=header,sep=sep,colClasses=classes,
    quote="",comment.char="",na.strings=NULL,fileEncoding="UTF-8",check.names=FALSE)
options <- read_tsv(parameter_path)
expected <- c("mode","layout","autosomes_only","remove_monomorphic","maf","missing_rate","threads","method","start","seed","window_bp","window_variants","ld_threshold","family_mode")
if(options$value[match("mode",options$parameter)]=="pca")expected <- c(expected,"pca_algorithm","pca_eigen_count","pca_bayesian","pca_eigen_method","pca_aux_dim","pca_iterations","pca_need_genmat")
stopifnot(identical(names(options),c("parameter","value")),identical(options$parameter,expected))
p <- stats::setNames(options$value,options$parameter)
boolean <- function(name) {stopifnot(p[[name]] %in% c("true","false"));p[[name]]=="true"}
autosomes <- boolean("autosomes_only");remove_mono <- boolean("remove_monomorphic")
maf <- as.numeric(p[["maf"]]);missing <- as.numeric(p[["missing_rate"]]);snp_first <- p[["layout"]]=="snp-first"
stopifnot(p[["threads"]]=="1",p[["mode"]] %in% c("king-robust","ld-pruning","grm","pca"),p[["layout"]] %in% c("snp-first","sample-first"))
write_tsv(data.frame(package=names(versions),version=unname(versions)),"original-package-versions.tsv")
effective <- options
effective$value[effective$parameter=="maf"] <- sprintf("%a",maf)
effective$value[effective$parameter=="missing_rate"] <- sprintf("%a",missing)
effective$value[effective$parameter=="ld_threshold"] <- sprintf("%a",as.numeric(p[["ld_threshold"]]))
write_tsv(effective,"effective-parameters.tsv")
fam <- read_tsv(paste0(prefix,".fam"),header=FALSE,sep="")
bim <- read_tsv(paste0(prefix,".bim"),header=FALSE,sep="")
transport <- read_tsv(file.path(dirname(prefix),"native-calls.tsv"),classes="integer")
stopifnot(ncol(fam)==6L,ncol(bim)==6L,nrow(transport)==nrow(bim),ncol(transport)==nrow(fam)+1L,
    identical(transport[[1L]],seq_len(nrow(bim))))
calls <- as.matrix(transport[,-1L,drop=FALSE]);dimnames(calls) <- NULL;stopifnot(all(calls %in% 0:3))
gds_path <- file.path(work,"row-index.gds")
SNPRelate::snpgdsCreateGeno(gds_path,genmat=if(snp_first) calls else t(calls),sample.id=seq_len(nrow(fam)),snp.id=seq_len(nrow(bim)),
    snp.rs.id=bim[[2L]],snp.chromosome=as.integer(bim[[1L]]),snp.position=as.integer(bim[[4L]]),snp.allele=paste(bim[[5L]],bim[[6L]],sep="/"),
    snpfirstdim=snp_first,compress.annotation="LZMA_RA",compress.geno="")
gds <- SNPRelate::snpgdsOpen(gds_path,readonly=TRUE)
perform <- function() {
    read_node <- function(name) gdsfmt::read.gdsn(gdsfmt::index.gdsn(gds,name))
    write_tsv(data.frame(input_row=seq_len(nrow(fam)),gds_sample_id=read_node("sample.id"),FID=fam[[1L]],IID=fam[[2L]]),"original-sample-map.tsv")
    write_tsv(data.frame(input_row=seq_len(nrow(bim)),gds_snp_id=read_node("snp.id"),original_snp_id=read_node("snp.rs.id"),
        chr=read_node("snp.chromosome"),position=read_node("snp.position"),allele=read_node("snp.allele")),"original-variant-map.tsv")
    geno <- SNPRelate::snpgdsGetGeno(gds,snpfirstdim=TRUE,verbose=FALSE)
    stopifnot(identical(geno,replace(calls,calls==3L,NA_integer_)))
    write_tsv(data.frame(input_variant_row=seq_len(nrow(geno)),geno,check.names=FALSE),"original-genotypes.tsv")
    rates <- SNPRelate::snpgdsSNPRateFreq(gds)
    write_tsv(data.frame(input_variant_row=seq_len(nrow(bim)),AF_hex=sprintf("%a",rates$AlleleFreq),MAF_hex=sprintf("%a",rates$MinorFreq),
        missing_hex=sprintf("%a",rates$MissingRate)),"original-variant-statistics.tsv")
    qc_options <- list(gdsobj=gds,sample.id=NULL,snp.id=NULL,autosome.only=autosomes,remove.monosnp=remove_mono,maf=maf,missing.rate=missing,num.thread=1L,verbose=TRUE)
    qc <- do.call(get(".InitFile2",envir=asNamespace("SNPRelate")),c(list(cmd="Explicit adapter QC census:"),qc_options))
    write_tsv(data.frame(result_row=seq_along(qc$snp.id),input_variant_row=qc$snp.id,original_snp_id=bim[[2L]][qc$snp.id]),"original-qc-population.tsv")
    warnings <- character()
    original <- function(fn,opts) {
        attempt <- tryCatch(withCallingHandlers(list(result=do.call(fn,opts)),warning=function(w) warnings <<- c(warnings,conditionMessage(w))),
            error=function(e) list(error=e))
        writeLines(warnings,file.path(work,"original-warnings.txt"),useBytes=TRUE)
        if(!is.null(attempt$error)) {
            writeLines(conditionMessage(attempt$error),file.path(work,"original-error.txt"),useBytes=TRUE)
            saveRDS(attempt$error,file.path(work,"original-condition.rds"),version=3L)
            stop(attempt$error)
        }
        attempt$result
    }
    base <- qc_options
    if(p[["mode"]]=="ld-pruning") {
        stopifnot(p[["method"]] %in% c("composite","r","dprime","corr"),p[["start"]] %in% c("random.f500","random","first","last"))
        max_variants <- if(p[["window_variants"]]=="NA") NA_integer_ else as.integer(p[["window_variants"]])
        RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(as.integer(p[["seed"]]))
        saveRDS(.Random.seed,file.path(work,"rng-before.rds"),version=3L)
        result <- original(SNPRelate::snpgdsLDpruning,c(base,list(method=p[["method"]],start.pos=p[["start"]],
            slide.max.bp=as.integer(p[["window_bp"]]),slide.max.n=max_variants,ld.threshold=as.numeric(p[["ld_threshold"]]),autosave=NULL)))
        saveRDS(.Random.seed,file.path(work,"rng-after.rds"),version=3L)
        saveRDS(result,file.path(work,"original-result.rds"),version=3L)
        selected <- as.integer(unlist(result,use.names=FALSE))
        write_tsv(data.frame(result_row=seq_along(selected),input_variant_row=selected,original_snp_id=bim[[2L]][selected],
            chromosome=as.character(rep(names(result),lengths(result)))),"original-selected-variants.tsv")
        # Supplementary source LD values for retained pairs inside the exact declared
        # windows. No dense all-genome LD matrix is created.
        edge_path <- file.path(work,"original-retained-LD-edges.tsv")
        con <- file(edge_path,open="wt",encoding="UTF-8");on.exit(close(con),add=TRUE)
        writeLines("first_variant_row\tsecond_variant_row\tld_hex\tpA_A_hex\tpA_B_hex\tpB_A_hex\tpB_B_hex",con)
        chr <- as.integer(bim[[1L]]);bp <- as.integer(bim[[4L]]);max_bp <- as.integer(p[["window_bp"]])
        max_n <- if(is.na(max_variants)) .Machine$integer.max else max_variants
        qc_positions <- integer(nrow(bim))
        for(ch in unique(chr[qc$snp.id])) {
            these <- qc$snp.id[chr[qc$snp.id]==ch];qc_positions[these] <- seq_along(these)
            retained <- selected[chr[selected]==ch];lo <- 1L
            if(length(retained)>=2L)for(i in 2:length(retained)) {
                first <- retained[i]
                while(lo<i && (bp[first]-bp[retained[lo]]>max_bp || qc_positions[first]-qc_positions[retained[lo]]>max_n))lo <- lo+1L
                if(lo<i)for(j in lo:(i-1L)) {
                    second <- retained[j]
                    ld <- original(SNPRelate::snpgdsLDpair,list(snp1=calls[first,],snp2=calls[second,],method=p[["method"]]))
                    fields <- c(as.character(first),as.character(second),sprintf("%a",ld[["ld"]]),
                        vapply(c("pA_A","pA_B","pB_A","pB_B"),function(key) if(key %in% names(ld)) sprintf("%a",ld[[key]]) else "ABSENT",character(1L)))
                    writeLines(paste(fields,collapse="\t"),con)
                }
            }
        }
    } else if(p[["mode"]]=="pca") {
        RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(as.integer(p[["seed"]]));before <- .Random.seed
        saveRDS(before,file.path(work,"rng-before.rds"),version=3L)
        extra <- list(algorithm=p[["pca_algorithm"]],eigen.cnt=as.integer(p[["pca_eigen_count"]]),bayesian=boolean("pca_bayesian"),
            eigen.method=p[["pca_eigen_method"]],aux.dim=as.integer(p[["pca_aux_dim"]]),iter.num=as.integer(p[["pca_iterations"]]),need.genmat=boolean("pca_need_genmat"),genmat.only=FALSE)
        result <- original(SNPRelate::snpgdsPCA,c(base,extra));saveRDS(.Random.seed,file.path(work,"rng-after.rds"),version=3L)
        saveRDS(result,file.path(work,"original-result.rds"),version=3L);stopifnot(identical(result$sample.id,seq_len(nrow(fam))))
        write_tsv(data.frame(result_row=seq_along(result$snp.id),input_variant_row=result$snp.id,original_snp_id=bim[[2L]][result$snp.id]),"original-selected-variants.tsv")
        write_tsv(data.frame(index=seq_along(result$eigenval),eigenvalue_hex=sprintf("%a",result$eigenval),varprop_hex=sprintf("%a",result$varprop)),"original-eigenvalues.tsv")
        e <- result$eigenvect;cells <- expand.grid(sample_row=seq_len(nrow(e)),component=seq_len(ncol(e)))
        write_tsv(data.frame(cells,value_hex=sprintf("%a",as.vector(e)),value_na=is.na(as.vector(e)),value_nan=is.nan(as.vector(e))),"original-eigenvectors.tsv")
        write_tsv(data.frame(trace_xtx_hex=sprintf("%a",result$TraceXTX),reported_bayesian=result$Bayesian,genmat_present=!is.null(result$genmat),
            eigenvalues=length(result$eigenval),eigenvector_columns=ncol(e),rng_changed=!identical(before,.Random.seed)),"original-PCA-summary.tsv")
        if(!is.null(result$genmat)) {
            g <- result$genmat;cells <- expand.grid(row_index=seq_len(nrow(g)),col_index=seq_len(ncol(g)))
            write_tsv(data.frame(cells,value_hex=sprintf("%a",as.vector(g))),"original-genotype-matrix.tsv")
        }
    } else {
        if(p[["mode"]]=="king-robust") {
            stopifnot(p[["family_mode"]] %in% c("singletons","original-fid"))
            extra <- list(type="KING-robust",useMatrix=FALSE)
            if(p[["family_mode"]]=="original-fid")extra$family.id <- fam[[1L]]
            result <- original(SNPRelate::snpgdsIBDKING,c(base,extra));matrix <- result$kinship;ibs0 <- result$IBS0
        } else {
            stopifnot(p[["method"]] %in% c("GCTA","Eigenstrat","EIGMIX","Weighted","Corr","IndivBeta"))
            result <- original(SNPRelate::snpgdsGRM,c(base,list(method=p[["method"]],useMatrix=FALSE,with.id=TRUE,out.fn=NULL)))
            matrix <- result$grm;ibs0 <- NULL
            write_tsv(data.frame(requested=p[["method"]],reported=result$method),"original-GRM-method.tsv")
            if(!is.null(result$avg_val))write_tsv(data.frame(avg_val_hex=sprintf("%a",result$avg_val)),"original-average.tsv")
        }
        saveRDS(result,file.path(work,"original-result.rds"),version=3L)
        stopifnot(identical(result$sample.id,seq_len(nrow(fam))))
        write_tsv(data.frame(result_row=seq_along(result$snp.id),input_variant_row=result$snp.id,original_snp_id=bim[[2L]][result$snp.id]),"original-selected-variants.tsv")
        n <- nrow(fam);stopifnot(identical(dim(matrix),c(n,n)))
        cells <- expand.grid(row_index=seq_len(n),col_index=seq_len(n));values <- as.vector(matrix)
        cells$value_hex <- sprintf("%a",values);cells$value_na <- is.na(values);cells$value_nan <- is.nan(values)
        if(!is.null(ibs0)) {cells$IBS0_hex <- sprintf("%a",as.vector(ibs0));cells$IBS0_na <- is.na(as.vector(ibs0));cells$IBS0_nan <- is.nan(as.vector(ibs0))}
        write_tsv(cells,"original-complete-matrix.tsv")
    }
    cat("ORIGINAL_SNPRELATE_EXPLICIT_WORKFLOW_COMPLETE\n")
}
tryCatch(perform(),finally=SNPRelate::snpgdsClose(gds))
