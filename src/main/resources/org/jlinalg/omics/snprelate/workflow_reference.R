# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original SNPRelate LD/GRM reference collection only. No project analysis admission.
a <- commandArgs(trailingOnly=TRUE)
stopifnot(length(a)==4L)
private_library <- a[1L]; prefix <- a[2L]; out <- a[3L]; transport_path <- a[4L]
stopifnot(nzchar(Sys.setlocale("LC_CTYPE","English_United States.utf8")),isTRUE(l10n_info()[["UTF-8"]]))
.libPaths(c(private_library,file.path(R.home(),"library")),include.site=FALSE)
stopifnot(getRversion()=="4.6.1")
for(p in c("gdsfmt","RhpcBLASctl","SNPRelate")) {
    stopifnot(requireNamespace(p,quietly=TRUE),dirname(find.package(p))==normalizePath(private_library,winslash="/"))
}
stopifnot(utils::packageDescription("SNPRelate",fields="Version")=="1.46.0",
          utils::packageDescription("gdsfmt",fields="Version")=="1.48.2",
          utils::packageDescription("RhpcBLASctl",fields="Version")=="0.23-42")
RhpcBLASctl::blas_set_num_threads(1L)
table <- function(x,path) utils::write.table(x,path,sep="\t",quote=FALSE,row.names=FALSE,col.names=TRUE,na="NA",fileEncoding="UTF-8")
read_input <- function(path) utils::read.table(path,header=FALSE,sep="",colClasses="character",quote="",comment.char="",na.strings=NULL,fileEncoding="UTF-8")
fam <- read_input(paste0(prefix,".fam")); bim <- read_input(paste0(prefix,".bim"))
calls <- utils::read.table(transport_path,header=TRUE,sep="\t",colClasses="integer",quote="",comment.char="",na.strings=NULL)
stopifnot(ncol(fam)==6L,ncol(bim)==6L,nrow(fam)>=2L,nrow(fam)<=100L,nrow(bim)>=1L,nrow(bim)<=1000L,
          nrow(calls)==nrow(bim),ncol(calls)==nrow(fam)+1L,identical(calls[[1L]],seq_len(nrow(bim))))
genotypes <- as.matrix(calls[,-1L,drop=FALSE]);stopifnot(all(genotypes %in% 0:3))
# Transport headers are row indices, not source GDS dimnames; retain identity in
# explicit maps and compare the complete matrix's values and dimensions.
dimnames(genotypes) <- NULL
table(data.frame(input_row=seq_len(nrow(fam)),gds_sample_id=seq_len(nrow(fam)),FID=fam[[1L]],IID=fam[[2L]]),file.path(out,"original-sample-map.tsv"))
table(data.frame(input_row=seq_len(nrow(bim)),gds_snp_id=seq_len(nrow(bim)),original_snp_id=bim[[2L]],chr=bim[[1L]],position=bim[[4L]],allele1=bim[[5L]],allele2=bim[[6L]]),file.path(out,"original-variant-map.tsv"))
for(name in c("snpgdsLDpruning","snpgdsLDpair","snpgdsLDMat","snpgdsGRM")) {
    fn <- getExportedValue("SNPRelate",name)
    dput(formals(fn),file=file.path(out,paste0(name,"-installed-formals.R")))
}
dispositions <- list()
capture <- function(label,folder,parameters,operation) {
    stopifnot(dir.create(folder));saveRDS(parameters,file.path(folder,"explicit-parameters.rds"),version=3L)
    dput(parameters,file=file.path(folder,"explicit-parameters.R"))
    warnings <- character()
    attempt <- tryCatch(withCallingHandlers(list(result=operation()),warning=function(w) warnings <<- c(warnings,conditionMessage(w))),error=function(e) list(error=e))
    writeLines(warnings,file.path(folder,"original-warnings.txt"),useBytes=TRUE)
    if(!is.null(attempt$error)) {
        writeLines(conditionMessage(attempt$error),file.path(folder,"original-error.txt"),useBytes=TRUE)
        saveRDS(attempt$error,file.path(folder,"original-condition.rds"),version=3L)
        dispositions[[label]] <<- data.frame(case=label,status="ORIGINAL_ERROR",selected_variants=NA_integer_,output_rows=0L,warnings=length(warnings))
        return(NULL)
    }
    attempt$result
}
for(layout in c(TRUE,FALSE)) {
    label <- if(layout) "snp-first" else "sample-first"
    folder <- file.path(out,label);stopifnot(dir.create(folder))
    gds_path <- file.path(folder,"row-index.gds")
    SNPRelate::snpgdsCreateGeno(gds_path,genmat=if(layout) genotypes else t(genotypes),sample.id=seq_len(nrow(fam)),
        snp.id=seq_len(nrow(bim)),snp.rs.id=bim[[2L]],snp.chromosome=as.integer(bim[[1L]]),snp.position=as.integer(bim[[4L]]),
        snp.allele=paste(bim[[5L]],bim[[6L]],sep="/"),snpfirstdim=layout,compress.annotation="LZMA_RA",compress.geno="")
    gds <- SNPRelate::snpgdsOpen(gds_path,readonly=TRUE)
    tryCatch({
        stopifnot(identical(SNPRelate::snpgdsGetGeno(gds,snpfirstdim=TRUE,verbose=FALSE),replace(genotypes,genotypes==3L,NA_integer_)))
        for(method in c("composite","r","dprime","corr")) for(start in c("random.f500","random","first","last")) {
            key <- paste(label,"ld",method,start,sep="-");target <- file.path(folder,paste("ld",method,start,sep="-"))
            parameters <- list(autosome.only=TRUE,remove.monosnp=TRUE,maf=0.005,missing.rate=0.01,method=method,
                slide.max.bp=500000L,slide.max.n=NA_integer_,ld.threshold=0.2,start.pos=start,num.thread=1L,autosave=NULL,verbose=TRUE)
            result <- capture(key,target,parameters,function() {
                RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(20261110L)
                before <- .Random.seed;saveRDS(before,file.path(target,"rng-before.rds"),version=3L)
                first <- do.call(SNPRelate::snpgdsLDpruning,c(list(gdsobj=gds),parameters))
                after <- .Random.seed;saveRDS(after,file.path(target,"rng-after.rds"),version=3L)
                RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(20261110L)
                second <- do.call(SNPRelate::snpgdsLDpruning,c(list(gdsobj=gds),parameters))
                saveRDS(second,file.path(target,"determinism-repeat.rds"),version=3L)
                list(first=first,second=second,after_first=after,after_second=.Random.seed)
            })
            if(!is.null(result)) {
                stopifnot(identical(result$first,result$second),identical(result$after_first,result$after_second))
                result <- result$first
                saveRDS(result,file.path(target,"original-result.rds"),version=3L)
                ids <- as.integer(unlist(result,use.names=FALSE));chromosomes <- as.character(rep(names(result),lengths(result)))
                stopifnot(!anyNA(ids),!anyDuplicated(ids),all(ids %in% seq_len(nrow(bim))))
                table(data.frame(result_row=seq_along(ids),chromosome=chromosomes,input_variant_row=ids,original_snp_id=bim[[2L]][ids]),file.path(target,"original-selected-variants.tsv"))
                dispositions[[key]] <- data.frame(case=key,status="ORIGINAL_SUCCESS",selected_variants=length(ids),output_rows=length(ids),warnings=length(readLines(file.path(target,"original-warnings.txt"))))
            }
        }
        for(method in c("GCTA","Eigenstrat","EIGMIX","Weighted","Corr","IndivBeta")) {
            key <- paste(label,"grm",method,sep="-");target <- file.path(folder,paste0("grm-",method))
            parameters <- list(autosome.only=TRUE,remove.monosnp=TRUE,maf=NaN,missing.rate=0.01,method=method,
                num.thread=1L,useMatrix=FALSE,out.fn=NULL,with.id=TRUE,verbose=TRUE)
            result <- capture(key,target,parameters,function() do.call(SNPRelate::snpgdsGRM,c(list(gdsobj=gds),parameters)))
            if(!is.null(result)) {
                saveRDS(result,file.path(target,"original-result.rds"),version=3L)
                stopifnot(identical(result$sample.id,seq_len(nrow(fam))),all(result$snp.id %in% seq_len(nrow(bim))),
                    identical(dim(result$grm),c(nrow(fam),nrow(fam))))
                table(data.frame(result_row=seq_along(result$snp.id),input_variant_row=result$snp.id,original_snp_id=bim[[2L]][result$snp.id]),file.path(target,"original-selected-variants.tsv"))
                cells <- expand.grid(row_index=seq_len(nrow(fam)),col_index=seq_len(nrow(fam)))
                values <- as.vector(result$grm)
                table(data.frame(cells,value_hex=sprintf("%a",values),value_na=is.na(values),value_nan=is.nan(values)),file.path(target,"original-complete-matrix.tsv"))
                if(!is.null(result$avg_val))table(data.frame(avg_val_hex=sprintf("%a",result$avg_val)),file.path(target,"original-average.tsv"))
                dispositions[[key]] <- data.frame(case=key,status="ORIGINAL_SUCCESS",selected_variants=length(result$snp.id),output_rows=length(values),warnings=length(readLines(file.path(target,"original-warnings.txt"))))
            }
        }
    },finally=SNPRelate::snpgdsClose(gds))
}
table(do.call(rbind,dispositions),file.path(out,"original-case-dispositions.tsv"))
cat("ORIGINAL_LD_GRM_REFERENCE_ONLY_PROJECT_ANALYSIS_NOT_ADMITTED\n")
