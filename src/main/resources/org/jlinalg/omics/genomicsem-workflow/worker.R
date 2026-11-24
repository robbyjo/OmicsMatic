# Copyright (C) 2026 JLinAlg contributors; GPL-3.0-or-later.
# Original package calls only. The Java owner checks inputs, namespace/runtime
# bytes and outputs; no dependency installation or numerical repair occurs here.
options(warn=1,digits=17)
a<-commandArgs(trailingOnly=TRUE);stopifnot(length(a)==1)
cfg<-jsonlite::read_json(a[1],simplifyVector=FALSE)
root<-normalizePath(cfg$root,winslash='/',mustWork=TRUE);home<-normalizePath(cfg$runtime_home,winslash='/',mustWork=TRUE)
lib<-file.path(home,'library');.libPaths(c(lib,.Library),include.site=FALSE)
req<-cfg$request;o<-req$options;op<-req$operation
write_json<-function(x,name){p<-file.path(root,'worker',name);tmp<-paste0(p,'.partial');jsonlite::write_json(x,tmp,auto_unbox=TRUE,pretty=TRUE,null='null',digits=17);stopifnot(file.rename(tmp,p))}
hex<-function(x)paste(sprintf('%02x',as.integer(charToRaw(enc2utf8(x)))),collapse='')
canonical<-function(f)paste0(paste(c(deparse(formals(f),width.cutoff=500L),deparse(body(f),width.cutoff=500L)),collapse='\n'),'\n')
snapshot<-function(name){
 con<-file(file.path(root,'worker',name),'wt',encoding='UTF-8');on.exit(close(con))
 writeLines('namespace\tname_hex\tcanonical_hex',con)
 for(pkg in c('GenomicSEM','lavaan')){
  ns<-asNamespace(pkg)
  for(n in sort(ls(ns,all.names=TRUE))){f<-get(n,ns,inherits=FALSE);if(is.function(f))writeLines(paste(pkg,hex(n),hex(canonical(f)),sep='\t'),con)}
 }
}
original_calls<-0L;conditions<-list()
outcome<-tryCatch({
 profile<-jsonlite::read_json(file.path(root,'source','PROFILE.json'),simplifyVector=FALSE)
 if(as.character(getRversion())!=profile$R$version||R.version$platform!=profile$R$platform)stop('UNAVAILABLE: require R4.6.1 Windows x86_64')
 for(p in profile$dependencies)if(packageDescription(p$package,fields='Version')!=p$version)stop(paste('UNAVAILABLE: require package',p$package,p$version))
 if(packageDescription('Matrix',fields='Version')!=profile$R$Matrix||as.character(packageVersion('lavaan'))!=profile$lavaan$runtime_version)stop('UNAVAILABLE: require Matrix1.7-5 and lavaan0.6-19')
 library(GenomicSEM)
 if(as.character(packageVersion('GenomicSEM'))!=profile$upstream$package_version)stop('UNAVAILABLE: require pinned original GenomicSEM0.0.5')
 ns<-asNamespace('GenomicSEM');reference<-new.env(parent=ns)
 for(f in sort(list.files(file.path(home,'source/R'),full.names=TRUE)))sys.source(f,envir=reference,keep.source=FALSE)
 funcs<-ls(reference,all.names=TRUE)[vapply(ls(reference,all.names=TRUE),function(n)is.function(get(n,reference,inherits=FALSE)),logical(1))]
 stopifnot(length(funcs)==44)
 for(n in funcs)stopifnot(exists(n,ns,inherits=FALSE),identical(deparse(formals(get(n,reference))),deparse(formals(get(n,ns)))),identical(deparse(body(get(n,reference))),deparse(body(get(n,ns)))))
 snapshot('namespace-before.tsv')
 dlls<-vapply(getLoadedDLLs(),function(d)d[['path']],character(1));dlls<-dlls[nzchar(dlls)]
 write_json(list(nonce=cfg$nonce,R_home=normalizePath(R.home(),winslash='/',mustWork=TRUE),R_version=as.character(getRversion()),platform=R.version$platform,DLLs=unname(dlls),namespaces=sort(loadedNamespaces()),namespace_functions=44),'runtime-probe.json')
 write_json(list(nonce=cfg$nonce,status='READY_BEFORE_FIRST_ORIGINAL_FIT'),'READY.json')
 deadline<-Sys.time()+300
 while(!file.exists(file.path(root,'worker','ACK.json'))){if(Sys.time()>deadline)stop('Java runtime/input fingerprint handshake was not acknowledged');Sys.sleep(.025)}
 ack<-jsonlite::read_json(file.path(root,'worker','ACK.json'));stopifnot(identical(ack$nonce,cfg$nonce))
 read_data<-function(role){p<-file.path(root,'inputs',role);utils::read.table(p,header=TRUE,sep='\t',quote='',comment.char='',check.names=FALSE,stringsAsFactors=FALSE,colClasses='character',na.strings=character(0))}
 read_matrix<-function(role){d<-read_data(role);names<-colnames(d)[-1];m<-matrix(as.numeric(as.matrix(d[,-1,drop=FALSE])),nrow=nrow(d));dimnames(m)<-list(d[[1]],names);m}
 S<-read_matrix('S.tsv');V<-read_matrix('V.tsv');cov<-list(V=V,S=S)
 eigenS<-min(eigen(S,symmetric=TRUE,only.values=TRUE)$values);eigenV<-min(eigen(V,symmetric=TRUE,only.values=TRUE)$values)
 write_json(list(S_min_eigenvalue=eigenS,V_min_eigenvalue=eigenV,original_inputs_changed=FALSE,upstream_smoothing_policy=o$smoothing_policy),'data-audit.json')
 if(o$smoothing_policy=='FAIL'&&(eigenS<=0||eigenV<=0))stop('Prepared S/V would require original upstream smoothing;policy FAIL leaves the original matrices unmodified and admits no fit')
 association<-grepl('GWAS$|TSEM$',op);tw<-grepl('TSEM$',op);user<-grepl('^USER',op)
 if(association){
  cov$I<-read_matrix('I.tsv');d<-read_data('associations.tsv');metadata<-if(tw)3L else 6L
  if(tw)d$HSQ<-as.numeric(d$HSQ)else{d$BP<-as.numeric(d$BP);d$MAF<-as.numeric(d$MAF)}
  for(j in seq.int(metadata+1L,ncol(d)))d[[j]]<-as.numeric(d[[j]])
 }
 if(user)model<-paste(readLines(file.path(root,'inputs/model.txt'),warn=FALSE,encoding='UTF-8'),collapse='\n')
 paths<-character(0)
 if(association){
  predictor<-if(tw)'Gene'else'SNP'
  if(user){
   parsed<-withCallingHandlers(lavaan::lavParseModelString(model,as.data.frame.=TRUE),warning=function(w){conditions[[length(conditions)+1L]]<<-list(message=conditionMessage(w),call=paste(deparse(conditionCall(w)),collapse='\n'))})
   model_paths<-unique(paste0(parsed$lhs[parsed$op=='~'&parsed$rhs==predictor],'~',predictor))
   if(length(o$sub)){paths<-gsub('\\s+','',unlist(o$sub));stopifnot(all(paths%in%model_paths))}else paths<-model_paths
  }else paths<-paste0('F1~',predictor)
  if(!length(paths))stop('The original model parser found no requested SNP/Gene association paths')
 }
 write_json(list(requested_paths=as.list(paths),model_parser=if(user&&association)'original lavaan::lavParseModelString'else'original common-factor specification'),'model-paths.json')
 RNGkind('Mersenne-Twister','Inversion','Rejection');set.seed(as.integer(o$seed))
 original_calls<-1L
 result<-withCallingHandlers({
  if(op=='COMMONFACTOR')GenomicSEM::commonfactor(cov,estimation=o$estimation)
  else if(op=='USERMODEL')GenomicSEM::usermodel(cov,estimation=o$estimation,model=model,CFIcalc=o$CFIcalc,std.lv=o$std_lv,imp_cov=TRUE,fix_resid=o$fix_resid,toler=o$tolerance,Q_Factor=o$Q_Factor)
  else if(!user)GenomicSEM::commonfactorGWAS(covstruc=cov,SNPs=d,estimation=o$estimation,cores=as.integer(o$cores),toler=o$tolerance,SNPSE=o$SNPSE,parallel=o$parallel,GC=o$GC,MPI=FALSE,TWAS=tw,smooth_check=TRUE)
  else GenomicSEM::userGWAS(covstruc=cov,SNPs=d,estimation=o$estimation,model=model,printwarn=TRUE,sub=if(length(o$sub)==0)FALSE else unlist(o$sub),cores=as.integer(o$cores),toler=o$tolerance,SNPSE=o$SNPSE,parallel=o$parallel,GC=o$GC,MPI=FALSE,smooth_check=TRUE,TWAS=tw,std.lv=o$std_lv,fix_measurement=o$fix_measurement,Q_SNP=o$Q_SNP,analytic=FALSE)
 },warning=function(w){conditions[[length(conditions)+1L]]<<-list(message=conditionMessage(w),call=paste(deparse(conditionCall(w)),collapse='\n'))})
 saveRDS(result,file.path(root,'worker','original-result.rds'),version=3)
 snapshot('namespace-after.tsv')
 table_dir<-file.path(root,'worker','tables');dir.create(table_dir);tables<-list();total_rows<-0L
 kind<-function(v){if(is.factor(v))return('character');if(typeof(v)%in%c('double','integer','logical','character'))return(typeof(v));stop(paste('Unsupported original output column type:',typeof(v)))}
 cell<-function(v,i){x<-v[i];if(is.factor(v))x<-as.character(x);if(is.numeric(x)&&is.nan(x))return('Q');if(is.na(x))return('N');if(is.numeric(x)&&is.infinite(x))return(if(x>0)'P'else'M');if(is.character(x))return(paste0('S:',hex(x)));if(is.logical(x))return(if(x)'B:TRUE'else'B:FALSE');if(is.integer(x))return(paste0('I:',as.character(x)));paste0('D:',sprintf('%.17g',x))}
 export<-function(groups,path,grouped){
  prototype<-as.data.frame(groups[[1]],check.names=FALSE,stringsAsFactors=FALSE);cn<-colnames(prototype);stopifnot(!anyDuplicated(cn))
  types<-vapply(prototype,kind,character(1));id<-length(tables)+1L;filename<-sprintf('table-%06d.tsv',id);con<-file(file.path(table_dir,filename),'wt',encoding='UTF-8');on.exit(close(con))
  writeLines(paste(c('group','group_name_hex','row','row_name_hex',vapply(cn,hex,character(1))),collapse='\t'),con)
  rows<-0L
  for(g in seq_along(groups)){
   d<-as.data.frame(groups[[g]],check.names=FALSE,stringsAsFactors=FALSE);stopifnot(identical(colnames(d),cn),identical(vapply(d,kind,character(1)),types))
   name<-if(is.null(names(groups)))''else names(groups)[g];if(is.na(name))name<-''
   if(nrow(d)>0)for(i in seq_len(nrow(d))){
    rows<-rows+1L;total_rows<<-total_rows+1L;if(total_rows>req$limits$maximum_result_rows)stop('Original result row capacity exceeded')
    fields<-vapply(d,function(v)cell(v,i),character(1));writeLines(paste(c(g,hex(name),i,hex(rownames(d)[i]),fields),collapse='\t'),con)
   }
  }
  tables[[id]]<<-list(id=id,path=path,file=paste0('worker/tables/',filename),rows=rows,groups=length(groups),grouped_original_list=grouped,columns=lapply(seq_along(cn),function(j)list(name=cn[j],kind=unname(types[j]),R_class=class(prototype[[j]]))))
 }
 visit<-function(x,path='result',depth=0L){
  if(depth>16L||length(tables)>=1000L)stop('Original output structure exceeds prepared-profile capacity')
  if(is.data.frame(x)||is.matrix(x)){export(list(x),path,FALSE);return(invisible(NULL))}
  if(!is.list(x)||length(x)==0L)stop('Original result has no supported complete table structure')
  if(all(vapply(x,is.data.frame,logical(1)))&&all(vapply(x,function(d)identical(colnames(d),colnames(x[[1]]))&&identical(vapply(d,kind,character(1)),vapply(x[[1]],kind,character(1))),logical(1)))){export(x,path,TRUE);return(invisible(NULL))}
  for(j in seq_along(x)){n<-if(is.null(names(x))||!nzchar(names(x)[j]))as.character(j)else names(x)[j];visit(x[[j]],paste0(path,'/',n),depth+1L)}
 }
 visit(result)
 write_json(list(tables=tables,total_rows=total_rows,original_RDS_interpreted_by_Java=FALSE),'table-index.json')
 write_json(list(conditions=conditions,original_function_calls=original_calls),'conditions.json')
 list(status='ORIGINAL_CALL_RETURNED_REVIEW_REQUIRED',original_function_calls=original_calls,returned_original_result=TRUE)
},error=function(e){
 try(saveRDS(e,file.path(root,'worker','original-error.rds'),version=3),silent=TRUE)
 list(status=if(startsWith(conditionMessage(e),'UNAVAILABLE:')&&original_calls==0L)'SKIP3_DEPENDENT_ANALYSIS_SKIPPED'else'FAIL2_NO_COMPLETE_RESULT',message=conditionMessage(e),original_function_calls=original_calls,returned_original_result=FALSE)
})
write_json(outcome,'worker-status.json')
if(outcome$status=='SKIP3_DEPENDENT_ANALYSIS_SKIPPED')quit(status=3)
if(outcome$status!='ORIGINAL_CALL_RETURNED_REVIEW_REQUIRED')quit(status=2)
