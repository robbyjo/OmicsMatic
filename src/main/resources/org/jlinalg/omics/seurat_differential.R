# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Closed transport worker. Original guarded Seurat functions perform the tests.
options(stringsAsFactors=FALSE,warn=1,contrasts=c('contr.treatment','contr.poly'))
required.action<-paste('Preserve captures and inspect WARNINGS.md, analysis-status.json, worker report and logs.',
 'Supply the named exact permitted runtime/private packages or skip dependent analysis.',
 'Correct named input/model/source/capacity errors and use NEW output paths. No automatic installation occurs.')
status<-function(code,detail){clean<-function(x)gsub('[\t\r\n]+',' ',x);writeLines(c('status\tdetail\trequired_action',paste(clean(code),clean(detail),clean(required.action),sep='\t')),'worker-status.tsv',useBytes=TRUE)}
warnings<-character();report<-list(status='FAIL2_NO_RESULT_ADMITTED',warnings=list(),statistical_engine='original-Seurat',independent_native_test_replay=FALSE,installed_DLL_source_authentication=FALSE)
number<-function(x)if(is.nan(x))'NaN' else if(is.na(x))'NA' else if(is.infinite(x))if(x>0)'Inf' else '-Inf' else sprintf('%.17g',x)
write.table.fixed<-function(frame,path,header){
 con<-file(path,'wt',encoding='UTF-8');on.exit(close(con));writeLines(paste(header,collapse='\t'),con,useBytes=TRUE)
 if(nrow(frame)>0)for(i in seq_len(nrow(frame)))writeLines(paste(vapply(frame[i,,drop=FALSE],function(v)if(is.numeric(v))number(v[[1]]) else as.character(v[[1]]),character(1)),collapse='\t'),con,useBytes=TRUE)
}
protocol.markers<-function(result,code=NULL){
 marker<-data.frame(contrast=character(),feature=character(),p_value=character(),fold_change=character(),pct_1=character(),pct_2=character(),p_adj=character())
 if(nrow(result)>0) {
  stopifnot(all(c('p_val','pct.1','pct.2','p_val_adj')%in%names(result)));fc.name<-names(result)[grepl('^avg_',names(result))];stopifnot(length(fc.name)==1L)
  marker<-data.frame(contrast=if(!is.null(code))rep(code,nrow(result)) else as.character(result$cluster),feature=if(!is.null(code))rownames(result) else as.character(result$gene),p_value=vapply(result$p_val,number,character(1)),fold_change=vapply(result[[fc.name]],number,character(1)),pct_1=vapply(result$pct.1,number,character(1)),pct_2=vapply(result$pct.2,number,character(1)),p_adj=vapply(result$p_val_adj,number,character(1)))
 }
 marker
}
main<-function(){
 paths<-readLines('runtime-paths.txt',encoding='UTF-8',warn=FALSE);.libPaths(c(normalizePath(paths,mustWork=TRUE),.Library),include.site=FALSE)
 if(!requireNamespace('jsonlite',quietly=TRUE)){status('SKIP3_DEPENDENCY_UNAVAILABLE','Declared private libraries cannot load jsonlite');quit(save='no',status=3L)}
 p<-jsonlite::fromJSON('parameters.json',simplifyVector=FALSE);profile<-jsonlite::fromJSON('../source/reference/SOURCE-PROFILE.json',simplifyVector=FALSE)
 if(as.character(getRversion())!='4.6.1'||p$profile!=profile$profile)stop('Require exact R4.6.1 and admitted explicit Seurat profile')
 for(name in names(profile$required_packages)) {
  if(!requireNamespace(name,quietly=TRUE)){status('SKIP3_DEPENDENCY_UNAVAILABLE',paste('Required package unavailable:',name));quit(save='no',status=3L)}
  if(as.character(packageVersion(name))!=profile$required_packages[[name]])stop(paste('Incompatible package edition:',name,'loaded',as.character(packageVersion(name)),'need',profile$required_packages[[name]]))
 }
 for(name in unlist(profile$absent_optional_packages))if(requireNamespace(name,quietly=TRUE))stop(paste('Optional backend would change the selected base profile:',name,'must be absent from declared libraries'))
 ns<-asNamespace('Seurat');original<-new.env(parent=ns)
 for(expr in parse('../source/reference/differential_expression.R',keep.source=FALSE))if(is.call(expr)&&identical(expr[[1]],as.name('<-'))&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name('function')))eval(expr,original)
 guards<-list()
 for(name in unlist(profile$functions)) {
  a<-get(name,envir=original,inherits=FALSE);b<-get(name,envir=ns,inherits=FALSE)
  if(!identical(body(a),body(b))||!identical(formals(a),formals(b)))stop(paste('Loaded source function body/formals differ:',name));guards[[name]]<-TRUE
 }
 future::plan(future::sequential);RNGkind('Mersenne-Twister','Inversion','Rejection');set.seed(p$seed)
 counts<-as(Matrix::readMM('counts.mtx'),'CsparseMatrix');data<-as(Matrix::readMM('data.mtx'),'CsparseMatrix');stopifnot(inherits(counts,'dgCMatrix'),inherits(data,'dgCMatrix'))
 stopifnot(identical(dim(counts),dim(data)),nrow(counts)==p$features,ncol(counts)==p$cells)
 features<-paste0('f',seq_len(nrow(counts)));cells<-paste0('c',seq_len(ncol(counts)));dimnames(counts)<-dimnames(data)<-list(features,cells)
 assay<-SeuratObject::CreateAssayObject(counts=counts,min.cells=0,min.features=0);assay<-SeuratObject::SetAssayData(assay,layer='data',new.data=data)
 if(isTRUE(p$has_scale_data)) {
  scale<-as(Matrix::readMM('scale-data.mtx'),'CsparseMatrix');stopifnot(inherits(scale,'dgCMatrix'),identical(dim(scale),dim(counts)));dimnames(scale)<-dimnames(counts);assay<-SeuratObject::SetAssayData(assay,layer='scale.data',new.data=as.matrix(scale))
 }
 metadata<-read.delim('metadata.tsv',header=TRUE,check.names=FALSE,colClasses='character',na.strings=NULL,encoding='UTF-8');stopifnot(nrow(metadata)==length(cells),identical(metadata$cell_index,cells))
 rownames(metadata)<-cells;metadata$group_index<-factor(metadata$group_index,levels=unlist(p$group_levels))
 for(v in p$covariates)metadata[[v$name]]<-if(v$kind=='numeric')as.numeric(metadata[[v$name]]) else factor(metadata[[v$name]],levels=unlist(v$levels))
 covariate.names<-vapply(p$covariates,function(v)v$name,character(1));selected<-unlist(p$selected_features)
 expand.group<-function(g)list(code=g$code,numerator=cells[metadata$group_index %in% unlist(g$numerator_groups)],denominator=cells[metadata$group_index %in% unlist(g$denominator_groups)])
 object<-SeuratObject::CreateSeuratObject(counts=assay,assay='prepared',meta.data=metadata);SeuratObject::Idents(object)<-metadata$group_index
 max.cells<-if(identical(p$max_cells_per_group,'Inf'))Inf else p$max_cells_per_group
 norm<-if(p$fc_normalization=='unrecorded')NULL else if(p$fc_normalization=='LogNormalize')'LogNormalize' else 'TFIDF'
 before<-get('.Random.seed',envir=.GlobalEnv);group.capture<-list()
 preview<-file('sampling-rule-preview.tsv','wt',encoding='UTF-8');on.exit(close(preview),add=TRUE);writeLines('contrast\tside\tcell',preview,useBytes=TRUE)
 con<-file('summaries.tsv','wt',encoding='UTF-8');writeLines('contrast\tfeature\tfold_change\tpct_1\tpct_2',con,useBytes=TRUE)
 for(definition in p$groups) {
  g<-expand.group(definition)
  a<-unlist(g$numerator);b<-unlist(g$denominator)
  fc<-Seurat::FoldChange(assay,cells.1=a,cells.2=b,slot=p$fc_slot,pseudocount.use=p$pseudocount,base=p$log_base,norm.method=norm)
  stopifnot(identical(rownames(fc),features))
  for(i in seq_len(nrow(fc)))writeLines(paste(g$code,features[i],number(fc[i,1]),number(fc$pct.1[i]),number(fc$pct.2[i]),sep='\t'),con,useBytes=TRUE)
  # Replay only the guarded source's sampling rule, preserving the real test RNG.
  saved<-get('.Random.seed',envir=.GlobalEnv);set.seed(p$seed);a.test<-a;b.test<-b
  if(length(a)>max.cells)a.test<-sample(a,size=max.cells);if(length(b)>max.cells)b.test<-sample(b,size=max.cells)
  assign('.Random.seed',saved,envir=.GlobalEnv)
  sampled<-length(a)>max.cells||length(b)>max.cells
  if(sampled)for(side in c('numerator','denominator'))for(cell in if(side=='numerator')a.test else b.test)writeLines(paste(g$code,side,cell,sep='\t'),preview,useBytes=TRUE)
  group.capture[[g$code]]<-list(numerator_count=length(a),denominator_count=length(b),source_rule_sampled_numerator_count=length(a.test),source_rule_sampled_denominator_count=length(b.test),sampling_rule_preview_written=sampled)
 }
 close(con);flush(preview)
 group.errors<-list();observed<-character();raw.rows<-0L;transport.error<-NULL;raw.con<-file('raw-group-markers.tsv','wt',encoding='UTF-8');on.exit(close(raw.con),add=TRUE)
 writeLines('contrast\tfeature\tp_value\tfold_change\tpct_1\tpct_2\tp_adj',raw.con,useBytes=TRUE)
 observe.original<-function(...) {
  args<-list(...);code<-if(p$mode=='PAIR')'pair' else as.character(metadata[args$cells.1[1],'group_index'])
  indices<-which(vapply(p$groups,function(g)identical(g$code,code),logical(1)));stopifnot(length(indices)==1L);g<-expand.group(p$groups[[indices]])
  stopifnot(identical(g$numerator,args$cells.1),identical(g$denominator,args$cells.2));observed<<-c(observed,code)
  answer<-tryCatch(do.call(Seurat::FindMarkers,args),error=function(e){group.errors[[code]]<<-conditionMessage(e);stop(e)})
  if(isTRUE(p$save_source_objects))saveRDS(answer,paste0('source-',code,'-markers.rds'))
  table<-protocol.markers(answer,code)
  raw.rows<<-raw.rows+nrow(table)
  if(raw.rows>p$maximum_result_rows){transport.error<<-'Original per-group marker rows exceed maximum_result_rows';stop(transport.error)}
  if(nrow(table)>0)for(i in seq_len(nrow(table)))writeLines(paste(as.character(table[i,]),collapse='\t'),raw.con,useBytes=TRUE)
  answer
 }
 common<-list(test.use=p$test,slot=p$test_slot,fc.slot=p$fc_slot,pseudocount.use=p$pseudocount,base=p$log_base,features=selected,
  min.pct=p$min_pct,min.diff.pct=if(identical(p$min_diff_pct,'-Inf'))-Inf else p$min_diff_pct,logfc.threshold=p$logfc_threshold,
  only.pos=p$only_positive,max.cells.per.ident=max.cells,random.seed=p$seed,min.cells.group=p$min_cells_group,densify=p$densify,verbose=FALSE)
 if(p$mode=='PAIR') {
  g<-expand.group(p$groups[[1]]);common$object<-assay;common$cells.1<-g$numerator;common$cells.2<-g$denominator;common['norm.method']<-list(norm)
  if(length(covariate.names))common$latent.vars<-metadata[c(common$cells.1,common$cells.2),covariate.names,drop=FALSE]
  result<-do.call(observe.original,common)
 } else {
  common$object<-object;common$return.thresh<-p$return_threshold;if(length(covariate.names))common$latent.vars<-covariate.names
  # Observe real per-group calls without modifying the guarded source body.
  # The private binding delegates to the original generic; no test is simulated.
  original.all<-get('FindAllMarkers',envir=ns);binding<-new.env(parent=environment(original.all));binding$FindMarkers<-observe.original
  observed.all<-original.all;environment(observed.all)<-binding
  stopifnot(identical(body(observed.all),body(original.all)),identical(formals(observed.all),formals(original.all)))
  result<-do.call(observed.all,common)
 }
 if(!is.null(transport.error))stop(transport.error)
 stopifnot(identical(observed,vapply(p$groups,function(g)g$code,character(1))),nrow(result)<=p$maximum_result_rows);flush(raw.con)
 if(isTRUE(p$save_source_objects))saveRDS(result,'source-markers.rds')
 raw<-result
 if(ncol(raw)>0){raw<-cbind(source_row=rownames(raw),raw);for(name in names(raw))if(is.numeric(raw[[name]]))raw[[name]]<-vapply(raw[[name]],number,character(1));write.table(raw,'source-markers.tsv',sep='\t',quote=FALSE,col.names=NA,na='NA')}
 else writeLines('source_empty_data_frame','source-markers.tsv')
 marker<-protocol.markers(result,if(p$mode=='PAIR')'pair' else NULL)
 write.table.fixed(marker,'markers.tsv',c('contrast','feature','p_value','fold_change','pct_1','pct_2','p_adj'))
 failures<-group.errors
 report<<-list(status='completed-with-warnings',profile=p$profile,source_guards=guards,versions=as.list(vapply(names(profile$required_packages),function(n)as.character(packageVersion(n)),character(1))),
  test=p$test,mode=p$mode,matrix_filter_slot='data',source_result_columns=as.list(names(result)),source_returned_rows=nrow(result),warnings=as.list(warnings),
  source_contrast_failures=failures,complete_comparison_set=length(failures)==0L,groups=group.capture,RNG_kind=as.list(RNGkind()),RNG_before=as.list(before),RNG_after=as.list(get('.Random.seed',envir=.GlobalEnv)),
  observation_binding='Private FindAllMarkers environment binding delegates each original FindMarkers call; original bodies/formals unchanged; no simulated tests',
  independent_native_statistical_replay=FALSE,installed_DLL_source_authentication=FALSE,scope='Original current Seurat tests on explicitly prepared layers, not historical whole packages or biological validity')
 jsonlite::write_json(report,'worker-report.json',pretty=TRUE,auto_unbox=TRUE,digits=17,na='string');writeLines(capture.output(sessionInfo()),'session-info.txt');status('completed-with-warnings','Original Seurat returned; inspect every source warning and any skipped contrasts')
}
tryCatch(withCallingHandlers(main(),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')}),error=function(e){
 detail<-conditionMessage(e);report$status<-'FAIL2_NO_RESULT_ADMITTED';report$error<-detail;report$warnings<-as.list(warnings)
 if(requireNamespace('jsonlite',quietly=TRUE))try(jsonlite::write_json(report,'worker-report.json',pretty=TRUE,auto_unbox=TRUE,digits=17,na='string'),silent=TRUE)
 status('FAIL2_NO_RESULT_ADMITTED',detail);message('FAIL2: ',detail);quit(save='no',status=2L)
})
