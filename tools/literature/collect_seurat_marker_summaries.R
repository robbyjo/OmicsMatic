# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original fold summaries and controlled pre/postprocessing; NOT Wilcoxon/LR test validation.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==2L)
.libPaths(c(normalizePath('build/literature-review/signac-runtime/reference-setup-1/R-lib'),normalizePath('build/omics-sctransform-r'),.Library),include.site=FALSE)
out<-args[[1]];stopifnot(!dir.exists(out));dir.create(out)
input<-jsonlite::fromJSON(args[[2]],simplifyVector=FALSE);ns<-asNamespace('Seurat')
stopifnot(as.character(packageVersion('Seurat'))=='5.6.0',as.character(packageVersion('SeuratObject'))=='5.4.0',as.character(packageVersion('Matrix'))=='1.7.5')
profiles<-list();source.info<-list()
for(version in c('4.1.0','4.3.0','5.0.0','5.1.0','5.2.1','5.6.0')) {
 path<-if(version=='5.6.0') 'build/literature-review/signac-runtime/differential-source-intake-1/differential_expression.R' else paste0('build/literature-review/signac-runtime/differential-historical-source-intake-1/',version,'.R')
 env<-new.env(parent=ns)
 for(expr in parse(path,keep.source=FALSE))if(is.call(expr)&&identical(expr[[1]],as.name('<-'))&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name('function')))eval(expr,env)
 # Retain literal historical bodies. The explicit private access/dispatch bridge
 # only resolves the current container's defunct slot API and S3 registration.
 env$GetAssayData<-function(object,slot=NULL,layer=NULL,...) {
  stopifnot(is.null(slot)||is.null(layer));SeuratObject::GetAssayData(object,layer=if(is.null(layer))slot else layer,...)
 }
 env$FoldChange<-local({e<-env;function(object,...)e$FoldChange.default(object,...)})
 profiles[[version]]<-env;source.info[[version]]<-list(path=path,historical_whole_package=FALSE,private_slot_to_layer_bridge=version %in% c('4.1.0','4.3.0','5.0.0','5.1.0','5.2.1'))
}
number<-function(x)if(is.nan(x))'NaN' else if(is.na(x))'NA' else if(is.infinite(x))if(x>0)'Inf' else '-Inf' else format(x,digits=17,scientific=FALSE,trim=TRUE)
rows<-function(frame)lapply(seq_len(nrow(frame)),function(i)list(id=rownames(frame)[i],values=lapply(frame[i,,drop=FALSE],function(x)number(x[[1]]))))
results<-list()
for(case in input) {
 env<-profiles[[case$version]];warnings<-character();tested<-character()
 matrix<-do.call(rbind,lapply(case$matrix,function(row)as.numeric(unlist(row))));rownames(matrix)<-paste0('f',seq_len(nrow(matrix)));colnames(matrix)<-paste0('c',seq_len(ncol(matrix)));matrix<-as(matrix,'dgCMatrix')
 assay<-SeuratObject::CreateAssayObject(counts=abs(matrix),min.cells=0,min.features=0)
 assay<-SeuratObject::SetAssayData(assay,layer='data',new.data=matrix)
 assay<-SeuratObject::SetAssayData(assay,layer='scale.data',new.data=as.matrix(matrix))
 a<-paste0('c',unlist(case$group1)+1L);b<-paste0('c',unlist(case$group2)+1L)
 slot<-case$slot;norm<-if(case$normalization=='UNRECORDED')NULL else if(case$normalization=='LOG_NORMALIZE')'LogNormalize' else 'TFIDF'
 capture<-function(f)tryCatch(withCallingHandlers(f(),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')}),error=function(e)structure(list(error=conditionMessage(e)),class='source_error'))
 fc<-capture(function(){
  settings<-list(object=assay,cells.1=a,cells.2=b,slot=slot,pseudocount.use=case$pseudocount,base=case$base)
  if(case$version!='4.1.0')settings['norm.method']<-list(norm)
  do.call(env$FoldChange.Assay,settings)
 })
 if(inherits(fc,'source_error')){results[[length(results)+1L]]<-list(id=case$id,status='original-summary-failure',error=fc$error,warnings=warnings);next}
 p<-vapply(case$p_values,function(x)if(identical(x,'NaN'))NaN else as.numeric(x),numeric(1));names(p)<-rownames(matrix)
 env$PerformDE<-function(object,cells.1,cells.2,features,...) {
  tested<<-features;data.frame(p_val=p[features],row.names=features)
 }
 filtered<-capture(function()env$FindMarkers.default(object=matrix,cells.1=a,cells.2=b,slot=case$testing_slot,fc.results=fc,test.use='LR',min.pct=case$min_pct,min.diff.pct=if(identical(case$min_diff_pct,'-Inf'))-Inf else case$min_diff_pct,logfc.threshold=case$logfc_threshold,only.pos=case$only_pos,min.cells.group=1,verbose=FALSE))
 results[[length(results)+1L]]<-list(id=case$id,status=if(inherits(filtered,'source_error'))'original-filter-failure' else 'completed-source-summary-and-controlled-p-callback',summary=rows(fc),tested=as.list(tested),markers=if(inherits(filtered,'source_error'))list() else rows(filtered),error=if(inherits(filtered,'source_error'))filtered$error else NULL,warnings=warnings,statistical_test_executed=FALSE)
}
rounding<-lapply(c(3L,7L,16L,80L,125L,400L,2000L),function(n)list(n=n,values=as.list(vapply(0:n,function(k)number(round(k/n,3)),character(1)))))
jsonlite::write_json(list(status='original-six-edition-marker-summary-and-controlled-prepostprocessing-not-statistical-test-validation',source_profiles=source.info,cases=results,rounding=rounding,source_statistical_tests_executed=FALSE),file.path(out,'RESULT.json'),auto_unbox=TRUE,pretty=TRUE,null='null',digits=17)
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'));cat('SEURAT_MARKER_SUMMARY_REFERENCE_COMPLETE\n')
