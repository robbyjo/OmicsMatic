# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original-package reference collection only; no project adapter admission.
arguments <- commandArgs(trailingOnly=TRUE)
stopifnot(length(arguments)==5L)
private_library <- arguments[1L]
prefix <- arguments[2L]
out <- arguments[3L]
transport_path <- arguments[4L]
transport_mode <- arguments[5L]
stopifnot(transport_mode %in% c("both","bed-import","row-index"))
# The startup C locale keeps messages stable but cannot represent UTF-8 IDs on
# Windows. Declare UTF-8 character decoding explicitly; never transliterate IDs.
character_locale <- Sys.setlocale("LC_CTYPE","English_United States.utf8")
stopifnot(nzchar(character_locale),isTRUE(l10n_info()[["UTF-8"]]))
writeLines(c(paste0("LC_CTYPE=",character_locale),"input_encoding=UTF-8"),file.path(out,"character-locale.txt"))
.libPaths(c(private_library, file.path(R.home(), "library")), include.site=FALSE)
stopifnot(getRversion()=="4.6.1")
for (package in c("gdsfmt","RhpcBLASctl","SNPRelate")) {
    stopifnot(requireNamespace(package,quietly=TRUE))
    stopifnot(dirname(find.package(package))==normalizePath(private_library,winslash="/"))
}
stopifnot(utils::packageDescription("SNPRelate",fields="Version")=="1.46.0")
stopifnot(utils::packageDescription("gdsfmt",fields="Version")=="1.48.2")
stopifnot(utils::packageDescription("RhpcBLASctl",fields="Version")=="0.23-42")
RhpcBLASctl::blas_set_num_threads(1L)
fam <- utils::read.table(paste0(prefix,".fam"),header=FALSE,sep="",quote="",comment.char="",colClasses="character",na.strings=NULL,fileEncoding="UTF-8")
bim <- utils::read.table(paste0(prefix,".bim"),header=FALSE,sep="",quote="",comment.char="",colClasses="character",na.strings=NULL,fileEncoding="UTF-8")
stopifnot(ncol(fam)==6L,ncol(bim)==6L,nrow(fam)<=500L,nrow(bim)<=20000L)
write_tsv <- function(x,path) utils::write.table(x,path,sep="\t",quote=FALSE,row.names=FALSE,col.names=TRUE,na="NA",fileEncoding="UTF-8")
analyze <- function(gds, label, layout) {
    folder <- file.path(out,label)
    stopifnot(dir.create(folder))
    read_node <- function(name) gdsfmt::read.gdsn(gdsfmt::index.gdsn(gds,name))
    sample_ids <- read_node("sample.id")
    snp_ids <- read_node("snp.id")
    rs_node <- gdsfmt::index.gdsn(gds,"snp.rs.id",silent=TRUE)
    rs_present <- !is.null(rs_node)
    rs_ids <- if(rs_present) gdsfmt::read.gdsn(rs_node) else rep(NA_character_,length(snp_ids))
    stopifnot(length(sample_ids)==nrow(fam),length(snp_ids)==nrow(bim),length(rs_ids)==nrow(bim))
    write_tsv(data.frame(input_row=seq_along(sample_ids),gds_sample_id=sample_ids,gds_sample_id_na=is.na(sample_ids),
                        storage=typeof(sample_ids),FID=fam[[1L]],IID=fam[[2L]],check.names=FALSE),file.path(folder,"original-sample-map.tsv"))
    write_tsv(data.frame(input_row=seq_along(snp_ids),gds_snp_id=snp_ids,rs_id_node_present=rs_present,
                        rs_id=rs_ids,rs_id_na=is.na(rs_ids),chr=read_node("snp.chromosome"),position=read_node("snp.position"),
                        allele=read_node("snp.allele"),check.names=FALSE),file.path(folder,"original-variant-map.tsv"))
    calls <- SNPRelate::snpgdsGetGeno(gds,snpfirstdim=TRUE,verbose=FALSE)
    stopifnot(identical(dim(calls),c(nrow(bim),nrow(fam))))
    write_tsv(data.frame(input_variant_row=seq_len(nrow(calls)),calls,check.names=FALSE),file.path(folder,"original-genotypes.tsv"))
    rates <- SNPRelate::snpgdsSNPRateFreq(gds)
    stopifnot(all(lengths(rates)==nrow(bim)))
    rate_table <- data.frame(input_variant_row=seq_len(nrow(bim)),rates,check.names=FALSE)
    for(key in names(rates)) rate_table[[paste0(key,"_hex")]] <- sprintf("%a",rates[[key]])
    write_tsv(rate_table,file.path(folder,"original-variant-statistics.tsv"))
    defaults <- formals(SNPRelate::snpgdsIBDKING)
    write_tsv(data.frame(argument=c("autosome.only","remove.monosnp","maf","missing.rate","family.id","num.thread","useMatrix"),
        installed_default=vapply(defaults[c("autosome.only","remove.monosnp","maf","missing.rate","family.id","num.thread","useMatrix")],
        function(x) paste(deparse(x),collapse=" "),character(1L)),check.names=FALSE),file.path(folder,"installed-function-defaults.tsv"))
    write_tsv(data.frame(snp_first_dimension=layout,transport=label),file.path(folder,"transport-specification.tsv"))
    all_options <- list(autosome.only=FALSE,remove.monosnp=FALSE,maf=NaN,missing.rate=NaN)
    plans <- list(
        "default-singletons"=list(family=FALSE,options=list()),
        "all-singletons"=list(family=FALSE,options=all_options),
        "default-families"=list(family=TRUE,options=list()),
        "all-families"=list(family=TRUE,options=all_options),
        "maf-boundary"=list(family=FALSE,options=utils::modifyList(all_options,list(maf=0.125))),
        "missing-boundary"=list(family=FALSE,options=utils::modifyList(all_options,list(missing.rate=0.1))),
        "missing-near-boundary"=list(family=FALSE,options=utils::modifyList(all_options,list(missing.rate=0.09999999999999999))),
        "nonfinite-disabled"=list(family=FALSE,options=utils::modifyList(all_options,list(maf=Inf,missing.rate=-Inf))))
    dispositions <- list()
    for (name in names(plans)) {
        target <- file.path(folder,name)
        stopifnot(dir.create(target))
        plan <- plans[[name]]
        specification <- c(list(gdsobj=gds,type="KING-robust",num.thread=1L,useMatrix=FALSE,verbose=TRUE),plan$options)
        if(plan$family) specification$family.id <- fam[[1L]]
        effective <- function(key) if(key %in% names(plan$options)) plan$options[[key]] else eval(defaults[[key]])
        write_tsv(data.frame(autosome_only=effective("autosome.only"),remove_monomorphic=effective("remove.monosnp"),
                            maf_hex=sprintf("%a",effective("maf")),missing_rate_hex=sprintf("%a",effective("missing.rate")),
                            family_mode=if(plan$family) "original-FID" else "singletons",check.names=FALSE),file.path(target,"original-case-options.tsv"))
        attempt <- tryCatch(list(result=do.call(SNPRelate::snpgdsIBDKING,specification)),error=function(e) list(error=e))
        if(!is.null(attempt$error)) {
            writeLines(conditionMessage(attempt$error),file.path(target,"original-error.txt"),useBytes=TRUE)
            saveRDS(attempt$error,file.path(target,"original-condition.rds"),version=3L)
            dispositions[[name]] <- data.frame(case=name,status="ORIGINAL_ERROR",selected_variants=NA_integer_,matrix_cells=0L)
            next
        }
        result <- attempt$result
        saveRDS(result,file.path(target,"original-result.rds"),version=3L)
        stopifnot(all(c("sample.id","snp.id","kinship","IBS0") %in% names(result)))
        stopifnot(identical(result$sample.id,sample_ids))
        samples <- seq_along(sample_ids)
        variants <- match(result$snp.id,snp_ids)
        stopifnot(!anyNA(variants),!anyDuplicated(variants))
        write_tsv(data.frame(result_row=seq_along(samples),input_row=samples,original_sample_id=result$sample.id,check.names=FALSE),file.path(target,"original-selected-samples.tsv"))
        write_tsv(data.frame(result_row=seq_along(variants),input_row=variants,original_snp_id=result$snp.id,check.names=FALSE),file.path(target,"original-selected-variants.tsv"))
        n <- length(samples)
        stopifnot(identical(dim(result$kinship),c(n,n)),identical(dim(result$IBS0),c(n,n)))
        cells <- expand.grid(row_index=seq_len(n),col_index=seq_len(n))
        k <- as.vector(result$kinship)
        z <- as.vector(result$IBS0)
        write_tsv(data.frame(cells,row_sample_id=result$sample.id[cells$row_index],col_sample_id=result$sample.id[cells$col_index],
                            kinship_hex=sprintf("%a",k),IBS0_hex=sprintf("%a",z),kinship_na=is.na(k),kinship_nan=is.nan(k),
                            IBS0_na=is.na(z),IBS0_nan=is.nan(z),check.names=FALSE),file.path(target,"original-complete-matrices.tsv"))
        dispositions[[name]] <- data.frame(case=name,status="ORIGINAL_SUCCESS",selected_variants=length(variants),matrix_cells=n*n)
        cat("ORIGINAL_REFERENCE_COMPLETED",label,name,"samples",n,"selected_variants",length(variants),"matrix_cells",n*n,"\n")
    }
    write_tsv(do.call(rbind,dispositions),file.path(folder,"original-case-dispositions.tsv"))
}
run_reference <- function() {
    transport <- utils::read.table(transport_path,header=TRUE,sep="\t",quote="",comment.char="",na.strings=NULL,colClasses="integer")
    stopifnot(nrow(transport)==nrow(bim),ncol(transport)==nrow(fam)+1L,identical(transport[[1L]],seq_len(nrow(bim))))
    original_calls <- as.matrix(transport[,-1L,drop=FALSE])
    stopifnot(all(original_calls %in% 0:3))
    methods <- if(transport_mode=="both") c("bed-import","row-index") else transport_mode
    for(method in methods) for(layout in c(TRUE,FALSE)) {
        label <- paste(method,if(layout) "snp-first" else "sample-first",sep="-")
        gds_path <- file.path(out,paste0(label,".gds"))
        if(method=="bed-import") {
            SNPRelate::snpgdsBED2GDS(paste0(prefix,".bed"),paste0(prefix,".fam"),paste0(prefix,".bim"),gds_path,
                family=TRUE,snpfirstdim=layout,compress.annotation="LZMA_RA",compress.geno="",verbose=TRUE)
        } else {
            SNPRelate::snpgdsCreateGeno(gds_path,genmat=if(layout) original_calls else t(original_calls),
                sample.id=seq_len(nrow(fam)),snp.id=seq_len(nrow(bim)),snp.rs.id=bim[[2L]],
                snp.chromosome=as.integer(bim[[1L]]),snp.position=as.integer(bim[[4L]]),snp.allele=paste(bim[[5L]],bim[[6L]],sep="/"),
                snpfirstdim=layout,compress.annotation="LZMA_RA",compress.geno="")
        }
        gds <- SNPRelate::snpgdsOpen(gds_path,readonly=TRUE)
        tryCatch(analyze(gds,label,layout),finally=SNPRelate::snpgdsClose(gds))
    }
}
run_reference()
cat("ORIGINAL_REFERENCE_ONLY_PROJECT_ANALYSIS_NOT_ADMITTED\n")
