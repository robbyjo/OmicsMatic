# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent original R pipeline: readMM -> Signac normalization/selection -> SVD.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==3L)
.libPaths(c(normalizePath("build/literature-review/signac-runtime/reference-setup-1/R-lib"),normalizePath("build/omics-sctransform-r"),.Library),include.site=FALSE)
requests<-jsonlite::read_json(args[[1]],simplifyVector=FALSE)
stopifnot(as.character(packageVersion("Signac"))=="1.17.1")
results<-list()
for(request in requests){
  warnings<-character()
  result<-withCallingHandlers({
    x<-as(Matrix::readMM(request$counts),"CsparseMatrix");rownames(x)<-readLines(request$features);colnames(x)<-readLines(request$cells)
    normalized<-Signac::RunTFIDF(x,method=request$tfidf_method,scale.factor=request$tfidf_scale,verbose=FALSE)
    assay<-SeuratObject::CreateAssayObject(counts=x,min.cells=0,min.features=0)
    cutoff<-if(request$cutoff_kind=="count")request$cutoff else paste0("q",request$cutoff)
    assay<-Signac::FindTopFeatures(assay,min.cutoff=cutoff,verbose=FALSE)
    selected<-SeuratObject::VariableFeatures(assay);normalized<-normalized[selected,,drop=FALSE]
    RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(request$seed)
    common<-list(object=normalized,assay="peaks",n=request$components,scale.embeddings=request$scale_embeddings,scale.max=request$clip,tol=request$tolerance,verbose=FALSE)
    if(request$profile=="1.17.1-package-rspectra")fit<-do.call(Signac::RunSVD,c(common,list(pca=FALSE))) else {
      version<-sub("-source-irlba$","",request$profile);path<-file.path(args[[3]],version,"source/Signac/R/dimension_reduction.R")
      expressions<-parse(path,keep.source=FALSE);selected_expression<-vapply(expressions,function(e)is.call(e)&&identical(e[[1]],as.name("<-"))&&identical(e[[2]],as.name("RunSVD.default")),logical(1));stopifnot(sum(selected_expression)==1L)
      env<-new.env(parent=asNamespace("Signac"));env$irlba<-irlba::irlba;eval(expressions[[which(selected_expression)]],env)
      # Omit work when not explicitly requested: this independently checks the
      # source's lazy default after n is clamped to the number of cells minus1.
      fit<-do.call(env$RunSVD.default,common)
    }
    list(id=request$id,selected=selected,cells=colnames(normalized),p=as.vector(normalized@p),i=as.vector(normalized@i),x=as.vector(normalized@x),
         u=unname(fit@misc$u),v=unname(fit@misc$v),embeddings=unname(SeuratObject::Embeddings(fit)),singular_values=as.vector(fit@misc$d),stdev=as.vector(fit@stdev))
  },warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")})
  result$warnings<-warnings;results[[length(results)+1L]]<-result
}
jsonlite::write_json(list(status="passed-independent-original-R-counts-to-LSI-controls",full_original_package="1.17.1",historical_packages_installed=FALSE,results=results),args[[2]],auto_unbox=TRUE,pretty=TRUE,digits=17,na="string",null="null")
cat("COMPLETE_INDEPENDENT_SIGNAC_LSI_CONTROLS",length(results),"\n")
