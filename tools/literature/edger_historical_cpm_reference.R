# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Actual selected historical package calls; no patched source or Java calculations.
args<-commandArgs(TRUE);stopifnot(length(args)==7);out<-args[3];version<-args[6];provider<-args[7];stopifnot(provider%in%c("package","original-kernel"),!file.exists(out));dir.create(out,recursive=TRUE)
.libPaths(c(args[4],readLines(args[5])),include.site=FALSE)
stopifnot(as.character(getRversion())=="4.6.1")
source<-file.path(args[2],version,"source/edgeR");e<-new.env(parent=globalenv())
for(n in c("makeCompressedMatrix.R","cpm.R","rpkm.R"))sys.source(file.path(source,"R",n),envir=e)
if(provider=="package"){
    stopifnot(requireNamespace("edgeR",quietly=TRUE),as.character(packageVersion("edgeR"))==version)
    for(n in c("cpm.default","rpkm.default","makeCompressedMatrix",".compressPrior"))stopifnot(identical(formals(get(n,e)),formals(get(n,asNamespace("edgeR")))),identical(body(get(n,e)),body(get(n,asNamespace("edgeR")))))
    cpm_function<-edgeR::cpm;rpkm_function<-edgeR::rpkm
}else{
    stopifnot(requireNamespace("Rcpp",quietly=TRUE),!"edgeR"%in%loadedNamespaces())
    dll<-dyn.load(args[4]);e$.cxx_calculate_cpm_raw<-getNativeSymbolInfo("calculate_cpm_raw",PACKAGE=dll)$address
    e$.cxx_calculate_cpm_log<-getNativeSymbolInfo("calculate_cpm_log",PACKAGE=dll)$address
    attach(e,name=paste0("original_edgeR_matrix_helpers_",version),pos=2)
    cpm_function<-e$cpm.default;rpkm_function<-e$rpkm.default
}
original<-readRDS(args[1]);number<-function(x)vapply(x,function(v)if(is.nan(v))"NaN" else if(is.na(v))"NA" else if(is.infinite(v))if(v>0)"Inf" else "-Inf" else sprintf("%.17g",v),"")
capture<-function(f){warnings<-messages<-character();value<-withCallingHandlers(f(),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")},message=function(m){messages<<-c(messages,conditionMessage(m));invokeRestart("muffleMessage")});list(value=value,warnings=warnings,messages=messages)}
cases<-returns<-list();k<-0L
for(id in names(original$datasets)){
    d<-original$datasets[[id]];ls<-if(is.null(d$libraries))colSums(d$matrix) else d$libraries
    if(any(ls<=0))next
    for(log in c(FALSE,TRUE))for(prior in c(0,2,.5))for(op in c("CPM","RPKM")){
        lengths<-seq_len(nrow(d$matrix))*100+500
        r<-capture(function()if(op=="CPM")cpm_function(d$matrix,lib.size=ls,log=log,prior.count=prior) else rpkm_function(d$matrix,gene.length=lengths,lib.size=ls,log=log,prior.count=prior))
        k<-k+1L;returns[[k]]<-r;cases[[k]]<-list(name=paste(id,op,log,prior,sep="/"),dataset=id,operation=op,log=log,prior=prior,libraries=unname(number(ls)),gene_lengths=lengths,
            values=lapply(seq_len(nrow(r$value)),function(i)unname(number(r$value[i,]))),warnings=as.list(r$warnings),messages=as.list(r$messages))
    }
}
encoded<-lapply(original$datasets,function(d)list(counts=lapply(seq_len(nrow(d$matrix)),function(i)unname(number(d$matrix[i,]))),samples=ncol(d$matrix),libraries=if(is.null(d$libraries))NULL else unname(number(d$libraries))))
jsonlite::write_json(list(schema_version=1,producer_version=version,provider=provider,datasets=encoded,transforms=cases,historical_author_host_reproduced=FALSE),file.path(out,"reference.json"),auto_unbox=TRUE,null="null",digits=NA)
saveRDS(list(inputs=original$datasets,returns=returns,reference=cases),file.path(out,"original-returns.rds"),version=3)
writeLines(capture.output(sessionInfo()),file.path(out,"SESSION.txt"))
jsonlite::write_json(list(status="PASS_ACTUAL_ORIGINAL_HISTORICAL_EDGER_CPM_RPKM_RETURNS",version=version,provider=provider,stock_package_namespace_loaded="edgeR"%in%loadedNamespaces(),transforms=length(cases),original_source_modified=FALSE,author_host_reproduced=FALSE,inference_run=FALSE),file.path(out,"COMPLETED.json"),auto_unbox=TRUE,pretty=TRUE)
cat("PASS actual original edgeR",version,"CPM/RPKM:",length(cases),"returns\n")
