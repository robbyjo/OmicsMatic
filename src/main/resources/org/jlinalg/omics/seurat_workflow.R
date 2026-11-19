# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Closed original Seurat RNA worker. No caller-supplied R is evaluated.
options(stringsAsFactors=FALSE,warn=1,contrasts=c('contr.treatment','contr.poly'))
required.action<-paste('Preserve inputs, partials and logs; inspect WARNINGS.md and worker-report.json.',
 'Supply exact permitted dependencies or leave the dependent analysis skipped.',
 'Correct named input/model/source/capacity failures and retry into a NEW output directory.')
status<-function(code,detail){clean<-function(x)gsub('[\t\r\n]+',' ',x);writeLines(c('status\tdetail\trequired_action',paste(clean(code),clean(detail),clean(required.action),sep='\t')),'worker-status.tsv',useBytes=TRUE)}
stage<-'preflight';warnings<-list();stages<-list();latest.object<-NULL
report<-list(status='FAIL2_NO_RESULT_ADMITTED',stage=stage,installed_DLL_source_authentication=FALSE)
unavailable<-function(detail){status('SKIP3_DEPENDENCY_UNAVAILABLE',detail);message('SKIP3: ',detail);quit(save='no',status=3L)}
number<-function(x)sprintf('%.17g',x)
write.frame<-function(frame,path){
 con<-file(path,'wt',encoding='UTF-8');on.exit(close(con));writeLines(paste(names(frame),collapse='\t'),con,useBytes=TRUE)
 for(i in seq_len(nrow(frame)))writeLines(paste(vapply(frame[i,,drop=FALSE],function(v)if(is.numeric(v))number(v[[1]]) else as.character(v[[1]]),character(1)),collapse='\t'),con,useBytes=TRUE)
}
write.embedding<-function(matrix,path){stopifnot(all(is.finite(matrix)));write.frame(data.frame(id=rownames(matrix),matrix,check.names=FALSE),path)}
write.graph<-function(graph,path){
 g<-as(graph,'dgCMatrix');x<-data.frame(i=g@i+1L,j=rep.int(seq_len(ncol(g)),diff(g@p)),x=g@x);x<-x[order(x$i,x$j),,drop=FALSE]
 stopifnot(all(is.finite(x$x)));write.frame(data.frame(row=paste0('c',x$i),column=paste0('c',x$j),weight=x$x),path)
}
main<-function(){
 paths<-readLines('runtime-paths.txt',encoding='UTF-8',warn=FALSE);.libPaths(c(normalizePath(paths,mustWork=TRUE),.Library),include.site=FALSE)
 if(!requireNamespace('jsonlite',quietly=TRUE))unavailable('Declared libraries cannot load jsonlite 2.0.0')
 p<-jsonlite::fromJSON('parameters.json',simplifyVector=FALSE);profile<-jsonlite::fromJSON('../source/reference/SOURCE-PROFILE.json',simplifyVector=FALSE)
 if(as.character(getRversion())!='4.6.1')unavailable('Require exact R 4.6.1 for this source-bound profile')
 stopifnot(identical(p$profile,profile$profile));required<-profile$required_packages
 if(p$normalization=='SCT-v2')required<-c(required,profile$sct_packages)
 if(isTRUE(p$umap$enabled))required<-c(required,profile$umap_packages)
 if(isTRUE(p$tsne$enabled))required<-c(required,profile$tsne_packages)
 if(p$clustering$algorithm==4L&&p$clustering$leiden_backend=='leidenbase')required<-c(required,list(leidenbase='0.1.36'))
 versions<-list()
 for(name in names(required)){
  if(!requireNamespace(name,quietly=TRUE))unavailable(paste('Required package unavailable:',name,'need',required[[name]]))
  version<-as.character(packageVersion(name));if(version!=required[[name]])unavailable(paste('Incompatible package:',name,'loaded',version,'need',required[[name]]));versions[[name]]<-version
 }
 ns<-asNamespace('Seurat');original<-new.env(parent=ns)
 for(file in unlist(profile$R_sources))for(expr in parse(file.path('../source/reference',file),keep.source=FALSE))if(is.call(expr)&&identical(expr[[1]],as.name('<-'))&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name('function')))eval(expr,original)
 guards<-list()
 for(name in unlist(profile$functions)){
  a<-get(name,envir=original,inherits=FALSE);b<-get(name,envir=ns,inherits=FALSE)
  if(!identical(body(a),body(b))||!identical(formals(a),formals(b)))stop(paste('Loaded source body/formals differ:',name));guards[[name]]<-TRUE
 }
 options(Seurat.nthreads=1L,Seurat.object.assay.version='v5');future::plan(future::sequential);RNGkind('Mersenne-Twister','Inversion','Rejection');set.seed(p$seed)
 counts<-as(Matrix::readMM('counts.mtx'),'CsparseMatrix');stopifnot(inherits(counts,'dgCMatrix'),identical(dim(counts),as.integer(c(p$n_features,p$n_cells))))
 features<-paste0('f',seq_len(nrow(counts)));cells<-paste0('c',seq_len(ncol(counts)));dimnames(counts)<-list(features,cells)
 metadata<-read.delim('metadata.tsv',check.names=FALSE,colClasses='character',na.strings=NULL,encoding='UTF-8');stopifnot(identical(metadata$cell,cells));rownames(metadata)<-cells;metadata$cell<-NULL
 for(v in p$regressors)metadata[[v$name]]<-if(v$kind=='numeric')as.numeric(metadata[[v$name]]) else factor(metadata[[v$name]],levels=unlist(v$levels))
 regress<-vapply(p$regressors,function(v)v$name,character(1));if(!length(regress))regress<-NULL
 stage<<-'construct';obj<-SeuratObject::CreateSeuratObject(counts=counts,meta.data=metadata,min.cells=0,min.features=0,assay='RNA',project='OmicsMaticCapturedRNA');latest.object<<-obj
 stopifnot(identical(rownames(obj),features),identical(colnames(obj),cells));stages[[stage]]<<-TRUE
 stage<<-'normalization'
 if(p$normalization=='LogNormalize'){
  obj<-Seurat::NormalizeData(obj,normalization.method='LogNormalize',scale.factor=p$scale_factor,verbose=FALSE);latest.object<<-obj;stages[[stage]]<<-TRUE
  stage<<-'variable_features';obj<-Seurat::FindVariableFeatures(obj,selection.method='vst',nfeatures=p$variable_features,loess.span=.3,clip.max='auto',verbose=FALSE);latest.object<<-obj;stages[[stage]]<<-TRUE
  stage<<-'scaling';obj<-Seurat::ScaleData(obj,features=SeuratObject::VariableFeatures(obj),vars.to.regress=regress,do.center=p$scale_center,do.scale=p$scale_scale,scale.max=p$scale_max,verbose=FALSE)
 }else{
  obj<-Seurat::SCTransform(obj,assay='RNA',new.assay.name='SCT',vst.flavor='v2',method='glmGamPoi_offset',ncells=p$sct_cells,min_cells=p$sct_min_cells,variable.features.n=p$variable_features,return.only.var.genes=FALSE,do.correct.umi=TRUE,vars.to.regress=regress,seed.use=p$seed,verbose=FALSE)
 }
 latest.object<<-obj;stages[[stage]]<<-TRUE
 stage<<-'pca';gram<-list(attempted=FALSE,returned=FALSE,error=NULL);original.gram<-get('EigenGramPCA',envir=ns,inherits=FALSE)
 observe.gram<-function(...){gram$attempted<<-TRUE;tryCatch({value<-original.gram(...);gram$returned<<-!is.null(value);value},error=function(e){gram$error<<-conditionMessage(e);stop(e)})}
 # A temporary, process-local observer delegates identical arguments/results.
 # Original RunPCA body/formals and original compiled function remain unchanged.
 unlockBinding('EigenGramPCA',ns);assign('EigenGramPCA',observe.gram,envir=ns);lockBinding('EigenGramPCA',ns)
 restore.gram<-function(){unlockBinding('EigenGramPCA',ns);assign('EigenGramPCA',original.gram,envir=ns);lockBinding('EigenGramPCA',ns)}
 extra<-switch(p$pca$solver,exact=list(approx=FALSE),irlba=list(approx=TRUE,tol=p$pca$tolerance,maxit=p$pca$max_iterations),source_default=list(approx=TRUE))
 obj<-tryCatch(do.call(Seurat::RunPCA,c(list(object=obj,npcs=p$pca$components,weight.by.var=p$pca$weight_by_variance,seed.use=p$seed,verbose=FALSE),extra)),finally=restore.gram())
 actual.solver<-if(p$pca$solver=='exact')'prcomp' else if(isTRUE(gram$returned))'EigenGramPCA' else if(isTRUE(gram$attempted))'irlba-after-source-Gram-fallback' else 'irlba'
 if(isTRUE(gram$attempted)&&!isTRUE(gram$returned))warnings[[length(warnings)+1L]]<<-list(stage=stage,message=paste('SOURCE_GRAM_FALLBACK: original RunPCA used IRLBA after Gram failure:',gram$error))
 latest.object<<-obj;stages[[stage]]<<-TRUE;dimensions<-as.integer(unlist(p$neighbors$dimensions));stopifnot(max(dimensions)<=ncol(SeuratObject::Embeddings(obj,'pca')))
 stage<<-'neighbors';extra<-if(p$neighbors$method=='annoy')list(n.trees=p$neighbors$trees,annoy.metric=p$neighbors$metric) else list(nn.eps=p$neighbors$epsilon)
 obj<-do.call(Seurat::FindNeighbors,c(list(object=obj,reduction='pca',dims=dimensions,k.param=p$neighbors$k,nn.method=p$neighbors$method,prune.SNN=p$neighbors$prune,graph.name=c('wf_nn','wf_snn'),verbose=FALSE),extra));latest.object<<-obj;stages[[stage]]<<-TRUE
 stage<<-'clustering';extra<-if(p$clustering$algorithm==4L)list(leiden_method=p$clustering$leiden_backend,leiden_objective_function=p$clustering$objective) else list(n.start=p$clustering$starts)
 obj<-do.call(Seurat::FindClusters,c(list(object=obj,graph.name='wf_snn',resolution=unlist(p$clustering$resolutions),algorithm=p$clustering$algorithm,n.iter=p$clustering$iterations,random.seed=p$seed,group.singletons=p$clustering$group_singletons,verbose=FALSE),extra));latest.object<<-obj;stages[[stage]]<<-TRUE
 if(isTRUE(p$umap$enabled)){
  stage<<-'umap';obj<-Seurat::RunUMAP(obj,reduction='pca',dims=dimensions,umap.method='uwot',n.neighbors=p$umap$neighbors,n.components=2L,metric=p$umap$metric,n.epochs=p$umap$epochs,min.dist=p$umap$min_distance,spread=p$umap$spread,seed.use=p$seed,uwot.sgd=FALSE,return.model=FALSE,verbose=FALSE);latest.object<<-obj;stages[[stage]]<<-TRUE
 }
 if(isTRUE(p$tsne$enabled)){
  stage<<-'tsne';obj<-Seurat::RunTSNE(obj,reduction='pca',dims=dimensions,seed.use=p$seed,tsne.method='Rtsne',perplexity=p$tsne$perplexity,max_iter=p$tsne$iterations,theta=p$tsne$theta,num_threads=1L,verbose=FALSE);latest.object<<-obj;stages[[stage]]<<-TRUE
 }
 stage<<-'export';saveRDS(obj,'ORIGINAL-SEURAT.rds');stopifnot(identical(colnames(obj),cells))
 active<-SeuratObject::DefaultAssay(obj);data<-SeuratObject::LayerData(obj,assay=active,layer='data');scale<-SeuratObject::LayerData(obj,assay=active,layer='scale.data');loadings<-SeuratObject::Loadings(obj[['pca']]);sdev<-SeuratObject::Stdev(obj[['pca']])
 stopifnot(all(is.finite(data)),all(is.finite(scale)),all(is.finite(loadings)),all(is.finite(sdev)),length(scale)<=p$maximum_dense_entries)
 Matrix::writeMM(as(data,'dgCMatrix'),'data.mtx');writeLines(rownames(data),'data-features.txt');Matrix::writeMM(as(scale,'dgCMatrix'),'scale.mtx');writeLines(rownames(scale),'scale-features.txt')
 write.embedding(t(scale[rownames(loadings),,drop=FALSE]),'pca-input.tsv');write.embedding(loadings,'pca-loadings.tsv');write.frame(data.frame(index=seq_along(sdev),sd=sdev),'pca-sdev.tsv')
 for(reduction in c('pca',if(isTRUE(p$umap$enabled))'umap',if(isTRUE(p$tsne$enabled))'tsne'))write.embedding(SeuratObject::Embeddings(obj,reduction),paste0(reduction,'.tsv'))
 write.graph(obj[['wf_nn']],'nn.tsv');write.graph(obj[['wf_snn']],'snn.tsv')
 columns<-grep('^wf_snn_res[.]',names(obj[[]]),value=TRUE);stopifnot(length(columns)==length(p$clustering$resolutions));clusters<-obj[[]][,columns,drop=FALSE]
 write.frame(data.frame(cell=rownames(clusters),lapply(clusters,as.character),check.names=FALSE),'clusters.tsv')
 write.frame(data.frame(source_column=columns,resolution=unlist(p$clustering$resolutions)),'cluster-resolution-map.tsv')
 write.frame(data.frame(feature=features,in_active_assay=features%in%rownames(obj[[active]]),variable=features%in%SeuratObject::VariableFeatures(obj),scaled=features%in%rownames(scale),pca=features%in%rownames(loadings)),'feature-disposition.tsv')
 stages[[stage]]<<-TRUE
 report<<-list(status='completed-with-warnings',profile=p$profile,stage=stage,completed_stages=stages,source_guards=guards,versions=versions,warnings=warnings,n_input_features=p$n_features,n_cells=p$n_cells,active_assay=active,active_features=nrow(obj[[active]]),variable_features=length(SeuratObject::VariableFeatures(obj)),scaled_features=nrow(scale),pca_features=nrow(loadings),pca_components=ncol(loadings),pca_spectrum_length=length(sdev),pca_actual_solver=actual.solver,gram_observation=gram,cluster_columns=as.list(columns),RNG_kind=as.list(RNGkind()),installed_DLL_source_authentication=FALSE,independent_native_model_refit=FALSE,observation_scope='Process-local EigenGramPCA binding delegates original function without changing RunPCA bodies/formals; restored after PCA')
 jsonlite::write_json(report,'worker-report.json',pretty=TRUE,auto_unbox=TRUE,digits=17,na='string');writeLines(capture.output(sessionInfo()),'session-info.txt');status('completed-with-warnings','Original RNA workflow completed; review source filters and all warnings')
}
tryCatch(withCallingHandlers(main(),warning=function(w){warnings[[length(warnings)+1L]]<<-list(stage=stage,message=conditionMessage(w));invokeRestart('muffleWarning')}),error=function(e){
 detail<-conditionMessage(e);report$status<-'FAIL2_NO_RESULT_ADMITTED';report$stage<-stage;report$error<-detail;report$completed_stages<-stages;report$warnings<-warnings
 if(!is.null(latest.object))try(saveRDS(latest.object,'PARTIAL-SOURCE-OBJECT-NOT-ADMITTED.rds'),silent=TRUE)
 if(requireNamespace('jsonlite',quietly=TRUE))try(jsonlite::write_json(report,'worker-report.json',pretty=TRUE,auto_unbox=TRUE,digits=17,na='string'),silent=TRUE)
 status('FAIL2_NO_RESULT_ADMITTED',detail);message('FAIL2: ',detail);quit(save='no',status=2L)
})
