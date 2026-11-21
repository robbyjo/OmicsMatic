# Independent pairwise complete-object comparator. Never source the adapter worker.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==5L)
out<-args[1];.libPaths(args[2:3],include.site=FALSE);plan<-jsonlite::read_json(args[4],simplifyVector=FALSE);source_root<-args[5]
stopifnot(as.character(getRversion())=="4.3.3",as.character(packageVersion("monocle3"))=="1.0.0")
ns<-asNamespace("monocle3");definitions<-list()
for(file in list.files(file.path(source_root,"R"),pattern="[.]R$",full.names=TRUE))for(expr in parse(file,keep.source=FALSE))if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]])%in%c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))definitions[[as.character(expr[[2]])]]<-expr[[3]]
signatures<-function(){result<-list();for(name in unlist(plan$guarded_functions,use.names=FALSE)){original<-eval(definitions[[name]],ns);loaded<-get(name,ns,inherits=FALSE);stopifnot(identical(body(original),body(loaded),ignore.srcref=FALSE,ignore.bytecode=FALSE),identical(formals(original),formals(loaded),ignore.srcref=FALSE,ignore.bytecode=FALSE));result[[name]]<-list(body_sha256=digest::digest(serialize(body(loaded),NULL,version=2),algo="sha256",serialize=FALSE),formals_sha256=digest::digest(serialize(formals(loaded),NULL,version=2),algo="sha256",serialize=FALSE))};result}
before<-signatures();stopifnot(identical(before,plan$reference_source_signatures))

same_attributes<-function(left,right,path,depth){
 if(!identical(names(left),names(right))||length(left)!=length(right))stop(paste(path,"complete attribute names differ"),call.=FALSE)
 for(i in seq_along(left))same(left[[i]],right[[i]],paste0(path,"@",names(left)[i]),depth+1L)
 invisible(TRUE)
}

same<-function(left,right,path="object",depth=0L){
 fail<-function(reason)stop(paste(path,reason),call.=FALSE)
 if(depth>=128L)fail("comparison nesting exceeds128")
 if(!identical(typeof(left),typeof(right))||!identical(isS4(left),isS4(right)))fail("type/S4 differs")
 if(is.null(left)){if(!is.null(right))fail("NULL differs");return(invisible(TRUE))}
 # igraph caches contain private environments. Compare every exposed graph,
 # vertex and edge attribute and direction, preserving row/column order/types.
 if(inherits(left,"igraph")||inherits(right,"igraph")){
  if(!inherits(left,"igraph")||!inherits(right,"igraph"))fail("igraph class differs")
  same(class(left),class(right),paste0(path,"@class"),depth+1L)
  same(igraph::is_directed(left),igraph::is_directed(right),paste0(path,"/directed"),depth+1L)
  same(igraph::graph_attr(left),igraph::graph_attr(right),paste0(path,"/graph_attributes"),depth+1L)
  same(igraph::as_data_frame(left,what="both"),igraph::as_data_frame(right,what="both"),paste0(path,"/vertex_edge_data"),depth+1L)
  return(invisible(TRUE))
 }
 if(typeof(left)%in%c("environment","closure","builtin","special","externalptr","weakref","language","symbol","pairlist"))fail("unsupported non-self-contained object; nothing ignored")
 la<-attributes(left);ra<-attributes(right)
 if(isS4(left)){
  slots<-slotNames(left);if(!identical(slots,slotNames(right)))fail("slot names differ")
  for(name in slots)same(methods::slot(left,name),methods::slot(right,name),paste0(path,"@",name),depth+1L)
  same_attributes(la[!names(la)%in%slots],ra[!names(ra)%in%slots],paste0(path,"/non_slot_attributes"),depth+1L)
 }else if(is.list(left)){
  l<-unclass(left);r<-unclass(right);if(length(l)!=length(r))fail("complete list length differs")
  for(i in seq_along(l))same(l[[i]],r[[i]],paste0(path,"/",if(is.null(names(l)))i else names(l)[i]),depth+1L)
  same_attributes(la,ra,paste0(path,"/attributes"),depth+1L)
 }else{
  attributes(left)<-attributes(right)<-NULL
  if(!identical(left,right,num.eq=FALSE,single.NA=FALSE,ignore.srcref=FALSE,ignore.bytecode=FALSE))fail("complete atomic values differ")
  same_attributes(la,ra,paste0(path,"/attributes"),depth+1L)
 }
 invisible(TRUE)
}

results<-list();learned<-output<-ordering<-rng_count<-errors<-policy<-0L
for(case in plan$cases){
 expected<-jsonlite::read_json(file.path(case$reference,"RESULT.json"),simplifyVector=FALSE);capture<-case$capture;worker<-file.path(capture,"worker");checks<-list()
 if(file.exists(file.path(case$reference,"learned-cds.rds"))){same(readRDS(file.path(case$reference,"learned-cds.rds")),readRDS(file.path(worker,"learned-cds.rds")),paste0(case$name,"/learned_cds"));checks$complete_learned_CDS_identical<-TRUE;learned<-learned+1L}
 if(file.exists(file.path(case$reference,"output-cds.rds"))){same(readRDS(file.path(case$reference,"output-cds.rds")),readRDS(file.path(worker,"output-cds.rds")),paste0(case$name,"/output_cds"));checks$complete_output_CDS_identical<-TRUE;output<-output+1L}
 if(file.exists(file.path(case$reference,"original-ordering.rds"))){same(readRDS(file.path(case$reference,"original-ordering.rds")),readRDS(file.path(worker,"original-ordering.rds")),paste0(case$name,"/ordering"));checks$complete_original_ordering_identical<-TRUE;ordering<-ordering+1L}
 same(readRDS(file.path(case$reference,"RNG.rds")),readRDS(file.path(worker,"RNG.rds")),paste0(case$name,"/RNG"));checks$complete_RNG_identical<-TRUE;rng_count<-rng_count+1L
 if(!is.null(expected$source_error)){
  original<-readRDS(file.path(case$reference,"original-error.rds"));actual<-readRDS(file.path(worker,"original-error.rds"))
  same(original,actual[c("class","message","call")],paste0(case$name,"/original_error"));checks$complete_original_error_class_message_call_identical<-TRUE;errors<-errors+1L
 }
 if(isTRUE(expected$declared_convergence_policy_refused)){
  error<-readRDS(file.path(worker,"original-error.rds"));stopifnot(error$stage=="original learn_graph",grepl("declared objective convergence",error$message,fixed=TRUE),!file.exists(file.path(capture,"COMPLETED.json")));checks$declared_convergence_policy_refused<-TRUE;policy<-policy+1L
 }
 results[[case$name]]<-checks;cat(case$name,"complete original objects match\n");flush.console()
}
stopifnot(identical(before,signatures()))
result<-list(status="PASS_COMPLETE_ORIGINAL_GRAPH_MODELS_ORDERING_ERRORS_AND_RNG",cases=results,complete_learned_CDS=learned,complete_output_CDS=output,complete_ordering_returns=ordering,complete_RNG_returns=rng_count,complete_original_errors=errors,convergence_policy_refusals=policy,source_function_signatures_unchanged=TRUE,adapter_worker_sourced=FALSE,ignored_model_state="igraph private cache representation only",whole_paper_reproduced=FALSE)
jsonlite::write_json(result,file.path(out,"RESULT.json"),pretty=TRUE,auto_unbox=TRUE,digits=NA,na="string",null="null")
