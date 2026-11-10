# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original SNPRelate PCA references, not project analysis admission.
a <- commandArgs(trailingOnly=TRUE);stopifnot(length(a)==4L)
library_path <- a[1L];prefix <- a[2L];out <- a[3L];transport_path <- a[4L]
stopifnot(nzchar(Sys.setlocale("LC_CTYPE","English_United States.utf8")),isTRUE(l10n_info()[["UTF-8"]]))
.libPaths(c(library_path,file.path(R.home(),"library")),include.site=FALSE);stopifnot(getRversion()=="4.6.1")
for(package in c("gdsfmt","RhpcBLASctl","SNPRelate"))stopifnot(requireNamespace(package,quietly=TRUE),dirname(find.package(package))==normalizePath(library_path,winslash="/"))
stopifnot(utils::packageDescription("SNPRelate",fields="Version")=="1.46.0",utils::packageDescription("gdsfmt",fields="Version")=="1.48.2",
          utils::packageDescription("RhpcBLASctl",fields="Version")=="0.23-42")
RhpcBLASctl::blas_set_num_threads(1L)
read_input <- function(path) utils::read.table(path,header=FALSE,sep="",colClasses="character",quote="",comment.char="",na.strings=NULL,fileEncoding="UTF-8")
table <- function(x,path) utils::write.table(x,path,sep="\t",quote=FALSE,row.names=FALSE,col.names=TRUE,na="NA",fileEncoding="UTF-8")
fam <- read_input(paste0(prefix,".fam"));bim <- read_input(paste0(prefix,".bim"))
data <- utils::read.table(transport_path,header=TRUE,sep="\t",colClasses="integer",quote="",comment.char="",na.strings=NULL)
stopifnot(nrow(fam)>=4L,nrow(fam)<=100L,nrow(bim)<=1000L,nrow(data)==nrow(bim),ncol(data)==nrow(fam)+1L)
calls <- as.matrix(data[,-1L,drop=FALSE]);dimnames(calls) <- NULL;stopifnot(all(calls %in% 0:3))
table(data.frame(input_row=seq_len(nrow(fam)),gds_sample_id=seq_len(nrow(fam)),FID=fam[[1L]],IID=fam[[2L]]),file.path(out,"original-sample-map.tsv"))
table(data.frame(input_row=seq_len(nrow(bim)),gds_snp_id=seq_len(nrow(bim)),original_snp_id=bim[[2L]]),file.path(out,"original-variant-map.tsv"))
dput(formals(SNPRelate::snpgdsPCA),file=file.path(out,"installed-PCA-formals.R"))
dispositions <- list()
for(snp_first in c(TRUE,FALSE)) {
    label <- if(snp_first) "snp-first" else "sample-first";folder <- file.path(out,label);stopifnot(dir.create(folder))
    gds_path <- file.path(folder,"row-index.gds")
    SNPRelate::snpgdsCreateGeno(gds_path,genmat=if(snp_first) calls else t(calls),sample.id=seq_len(nrow(fam)),snp.id=seq_len(nrow(bim)),
        snp.rs.id=bim[[2L]],snp.chromosome=as.integer(bim[[1L]]),snp.position=as.integer(bim[[4L]]),snp.allele=paste(bim[[5L]],bim[[6L]],sep="/"),snpfirstdim=snp_first,
        compress.annotation="LZMA_RA",compress.geno="")
    gds <- SNPRelate::snpgdsOpen(gds_path,readonly=TRUE)
    tryCatch({
        stopifnot(identical(SNPRelate::snpgdsGetGeno(gds,snpfirstdim=TRUE,verbose=FALSE),replace(calls,calls==3L,NA_integer_)))
        plans <- list()
        for(bayesian in c(FALSE,TRUE))for(solver in c("DSPEVX","DSPEV"))for(k in c(3L,0L)) {
            name <- paste("exact",bayesian,solver,k,sep="-")
            plans[[name]] <- list(algorithm="exact",eigen.cnt=k,bayesian=bayesian,eigen.method=solver,need.genmat=TRUE,genmat.only=FALSE,aux.dim=6L,iter.num=3L)
        }
        for(iter in c(1L,3L)) {
            name <- paste0("randomized-FALSE-DSPEVX-3-iter",iter)
            plans[[name]] <- list(algorithm="randomized",eigen.cnt=3L,bayesian=FALSE,eigen.method="DSPEVX",need.genmat=FALSE,genmat.only=FALSE,aux.dim=6L,iter.num=iter)
        }
        for(name in names(plans)) {
            target <- file.path(folder,name);stopifnot(dir.create(target));parameters <- c(list(autosome.only=TRUE,remove.monosnp=TRUE,maf=NaN,missing.rate=0.01,num.thread=1L,verbose=TRUE),plans[[name]])
            dput(parameters,file=file.path(target,"explicit-parameters.R"));saveRDS(parameters,file.path(target,"explicit-parameters.rds"),version=3L)
            RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(20261110L);before <- .Random.seed
            saveRDS(before,file.path(target,"rng-before.rds"),version=3L)
            warnings <- character()
            attempt <- tryCatch(withCallingHandlers(list(result=do.call(SNPRelate::snpgdsPCA,c(list(gdsobj=gds),parameters))),
                warning=function(w) warnings <<- c(warnings,conditionMessage(w))),error=function(e) list(error=e))
            writeLines(warnings,file.path(target,"original-warnings.txt"),useBytes=TRUE)
            saveRDS(.Random.seed,file.path(target,"rng-after.rds"),version=3L)
            if(!is.null(attempt$error)) {
                writeLines(conditionMessage(attempt$error),file.path(target,"original-error.txt"),useBytes=TRUE)
                saveRDS(attempt$error,file.path(target,"original-condition.rds"),version=3L)
                dispositions[[paste(label,name,sep="-")]] <- data.frame(case=name,layout=label,status="ORIGINAL_ERROR",selected_variants=NA_integer_,eigenvalues=0L,eigenvector_cells=0L)
                next
            }
            result <- attempt$result;saveRDS(result,file.path(target,"original-result.rds"),version=3L)
            stopifnot(identical(result$sample.id,seq_len(nrow(fam))),all(result$snp.id %in% seq_len(nrow(bim))))
            table(data.frame(result_row=seq_along(result$snp.id),input_variant_row=result$snp.id,original_snp_id=bim[[2L]][result$snp.id]),file.path(target,"original-selected-variants.tsv"))
            table(data.frame(index=seq_along(result$eigenval),eigenvalue_hex=sprintf("%a",result$eigenval),varprop_hex=sprintf("%a",result$varprop)),file.path(target,"original-eigenvalues.tsv"))
            e <- result$eigenvect;cells <- expand.grid(sample_row=seq_len(nrow(e)),component=seq_len(ncol(e)))
            table(data.frame(cells,value_hex=sprintf("%a",as.vector(e)),value_na=is.na(as.vector(e)),value_nan=is.nan(as.vector(e))),file.path(target,"original-eigenvectors.tsv"))
            table(data.frame(trace_xtx_hex=sprintf("%a",result$TraceXTX),reported_bayesian=result$Bayesian,genmat_present=!is.null(result$genmat),
                eigenvalues=length(result$eigenval),eigenvector_columns=ncol(e),rng_changed=!identical(before,.Random.seed)),file.path(target,"original-PCA-summary.tsv"))
            if(!is.null(result$genmat)) {
                g <- result$genmat;stopifnot(identical(dim(g),c(nrow(fam),nrow(fam))))
                cells <- expand.grid(row_index=seq_len(nrow(g)),col_index=seq_len(ncol(g)))
                table(data.frame(cells,value_hex=sprintf("%a",as.vector(g))),file.path(target,"original-genotype-matrix.tsv"))
            }
            dispositions[[paste(label,name,sep="-")]] <- data.frame(case=name,layout=label,status="ORIGINAL_SUCCESS",selected_variants=length(result$snp.id),
                eigenvalues=length(result$eigenval),eigenvector_cells=length(result$eigenvect))
        }
    },finally=SNPRelate::snpgdsClose(gds))
}
table(do.call(rbind,dispositions),file.path(out,"original-case-dispositions.tsv"))
cat("ORIGINAL_PCA_REFERENCE_ONLY_NO_PROJECT_ANALYSIS_ADMITTED\n")
