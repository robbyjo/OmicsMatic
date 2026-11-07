# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==3L)
.libPaths(c(normalizePath("build/literature-review/signac-runtime/reference-setup-1/R-lib"),normalizePath("build/omics-sctransform-r"),.Library),include.site=FALSE)
stopifnot(as.character(packageVersion("Signac"))=="1.17.1")
input<-jsonlite::read_json(args[[1]],simplifyVector=FALSE);out<-args[[2]]
capture<-function(f){warnings<-character();result<-tryCatch(withCallingHandlers(f(),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")}),error=function(e)list(error=conditionMessage(e)));list(value=result,warnings=warnings)}
matrix_result<-function(x){original_class<-class(x);x<-as(x,"dgCMatrix");list(features=rownames(x),cells=colnames(x),p=as.vector(x@p),i=as.vector(x@i),x=as.vector(x@x),original_class=original_class)}
files<-list()
for(file in input$files){
  plain<-file.path(out,paste0(file$id,".tsv"));compressed<-paste0(plain,".gz")
  Rsamtools::bgzip(plain,dest=compressed,overwrite=FALSE);Rsamtools::indexTabix(compressed,format="bed")
  files[[file$id]]<-compressed
}
counts<-list()
for(case in input$count_cases){
  paths<-unname(unlist(files[unlist(case$files)]));whitelist<-if(is.null(case$cells))NULL else unlist(case$cells)
  result<-capture(function()Signac::CountFragments(as.list(paths),cells=whitelist,max_lines=if(case$lines==0)NULL else case$lines,verbose=FALSE))
  counts[[length(counts)+1L]]<-list(id=case$id,result=result)
}
quantification<-list();nucleosome<-list()
for(case in input$matrix_cases){
  fragments<-lapply(case$sources,function(source){
    cells<-if(is.null(source$map))NULL else setNames(vapply(source$map,`[[`,character(1),"barcode"),vapply(source$map,`[[`,character(1),"cell"))
    Signac::CreateFragmentObject(files[[source$file]],cells=cells,validate.fragments=FALSE,verbose=FALSE)
  })
  features<-GenomicRanges::GRanges(seqnames=vapply(case$regions,`[[`,character(1),"chromosome"),ranges=IRanges::IRanges(start=vapply(case$regions,`[[`,numeric(1),"start"),end=vapply(case$regions,`[[`,numeric(1),"end")))
  cells<-if(is.null(case$cells))NULL else unlist(case$cells)
  result<-capture(function()matrix_result(Signac::FeatureMatrix(fragments,features,cells=cells,keep_all_features=case$keep,process_n=case$chunk,verbose=FALSE)))
  quantification[[length(quantification)+1L]]<-list(id=case$id,result=result)
  if(!is.null(case$object_cells))for(n in case$nucleosome_lines){
    object_cells<-unlist(case$object_cells);placeholder<-Matrix::Matrix(matrix(1,nrow=2,ncol=length(object_cells)),sparse=TRUE);dimnames(placeholder)<-list(c("chr1-1-2","chr1-3-4"),object_cells)
    assay<-Signac::CreateChromatinAssay(counts=placeholder,ranges=GenomicRanges::GRanges(c("chr1","chr1"),IRanges::IRanges(c(1,3),c(2,4))),fragments=fragments,min.cells=0,min.features=0)
    object<-SeuratObject::CreateSeuratObject(counts=assay,assay="peaks")
    qc<-capture(function(){fit<-Signac::NucleosomeSignal(object,n=if(n==0)NULL else n,verbose=FALSE);data<-fit[[]][,c("nucleosome_signal","nucleosome_percentile"),drop=FALSE];kind<-function(x)ifelse(is.nan(x),"NAN",ifelse(is.na(x),"NA",ifelse(is.infinite(x),"POSITIVE_INFINITY","FINITE")));list(cells=rownames(data),ratio=unname(data$nucleosome_signal),ratio_kind=kind(data$nucleosome_signal),percentile=unname(data$nucleosome_percentile),percentile_kind=kind(data$nucleosome_percentile),attached_maps=lapply(Signac::Fragments(fit[["peaks"]]),function(f)Signac::GetFragmentData(f,slot="cells")))})
    nucleosome[[length(nucleosome)+1L]]<-list(id=case$id,lines=n,result=qc)
  }
}
rounding<-do.call(rbind,lapply(1:200,function(n)data.frame(n=n,rank=seq_len(n),input=seq_len(n)/n,rounded=round(seq_len(n)/n,digits=2))))
write.table(rounding,file.path(out,"rounding.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"))
jsonlite::write_json(list(status="captured-full-original-Signac-fragment-and-nucleosome-controls",package="1.17.1",historical_binaries_executed=FALSE,counts=counts,quantification=quantification,nucleosome=nucleosome),args[[3]],pretty=TRUE,auto_unbox=TRUE,digits=17,na="string",null="null")
cat("COMPLETE_ORIGINAL_FRAGMENT_CONTROLS",length(counts),length(quantification),length(nucleosome),"\n")
