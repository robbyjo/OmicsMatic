# Independent original namespace calls; never source or evaluate the adapter worker.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==5L)
out<-args[1];.libPaths(args[2:3],include.site=FALSE);source_root<-args[4];plan<-jsonlite::read_json(args[5],simplifyVector=FALSE)
stopifnot(as.character(getRversion())=="4.3.3",as.character(packageVersion("monocle3"))=="1.0.0")
for(name in names(plan$packages))stopifnot(requireNamespace(name,quietly=TRUE),as.character(getNamespaceVersion(name))==plan$packages[[name]])
RhpcBLASctl::blas_set_num_threads(1L);RhpcBLASctl::omp_set_num_threads(1L);options(mc.cores=1L)
definitions<-list();ns<-asNamespace("monocle3")
for(file in list.files(file.path(source_root,"R"),pattern="[.]R$",full.names=TRUE))for(expr in parse(file,keep.source=FALSE))if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]])%in%c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))definitions[[as.character(expr[[2]])]]<-expr[[3]]
signatures<-function(){result<-list();for(name in unlist(plan$guarded_functions,use.names=FALSE)){original<-eval(definitions[[name]],ns);loaded<-get(name,ns,inherits=FALSE);stopifnot(identical(body(original),body(loaded),ignore.srcref=FALSE,ignore.bytecode=FALSE),identical(formals(original),formals(loaded),ignore.srcref=FALSE,ignore.bytecode=FALSE));result[[name]]<-list(body_sha256=digest::digest(serialize(body(loaded),NULL,version=2),algo="sha256",serialize=FALSE),formals_sha256=digest::digest(serialize(formals(loaded),NULL,version=2),algo="sha256",serialize=FALSE))};result}
before<-signatures();results<-list()
write_json<-function(value,path)jsonlite::write_json(value,path,pretty=TRUE,auto_unbox=TRUE,digits=NA,na="string",null="null")
for(case in plan$cases){
 directory<-file.path(out,case$name);dir.create(directory,recursive=FALSE);cds<-readRDS(case$source_cds);p<-case$parameters;warnings<-messages<-character();rng<-list();source_error<-NULL;policy_refused<-FALSE;graph_returned<-p$operation=="ORDER";order_returned<-FALSE
 RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(as.integer(p$seed));rng$initial<-.Random.seed
 source_error<-tryCatch(withCallingHandlers({
  if(p$operation!="ORDER"){
   control<-p$learn_graph_control;if(length(control)==0L)control<-list()
   cds<-monocle3::learn_graph(cds,use_partition=p$use_partition,close_loop=p$close_loop,learn_graph_control=control,verbose=TRUE);rng$after_graph<-.Random.seed;graph_returned<-TRUE;saveRDS(cds,file.path(directory,"learned-cds.rds"))
   eps<-if(is.null(control$eps))1e-5 else control$eps;objectives<-cds@principal_graph_aux[["UMAP"]]$objective_vals
   converged<-length(objectives)>0L&&all(vapply(objectives,function(x){n<-length(x);n>=2L&&all(is.finite(x))&&is.finite(abs(x[n-1L]-x[n])/abs(x[n-1L]))&&abs(x[n-1L]-x[n])/abs(x[n-1L])<eps},logical(1)))&&!any(grepl("Warning: kmeans returned ifault",messages,fixed=TRUE))
   policy_refused<-!converged&&p$convergence_policy=="require"
  }
  if(!policy_refused&&p$operation!="LEARN"){
   if(!is.null(p$root_pr_nodes))cds<-monocle3::order_cells(cds,root_pr_nodes=unlist(p$root_pr_nodes,use.names=FALSE),verbose=TRUE)else cds<-monocle3::order_cells(cds,root_cells=unlist(p$root_cells,use.names=FALSE),verbose=TRUE)
   rng$after_order<-.Random.seed;order_returned<-TRUE;roots<-cds@principal_graph_aux[["UMAP"]]$root_pr_nodes
   ordering<-get("extract_general_graph_ordering",ns)(cds,roots,verbose=FALSE,reduction_method="UMAP");saveRDS(ordering,file.path(directory,"original-ordering.rds"))
  }
  NULL
 },warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")},message=function(m){messages<<-c(messages,conditionMessage(m));invokeRestart("muffleMessage")}),error=identity)
 if(is.null(source_error)&&!policy_refused){rng$final<-.Random.seed;saveRDS(cds,file.path(directory,"output-cds.rds"))}else{rng$seed_exists_at_error<-exists(".Random.seed",envir=.GlobalEnv,inherits=FALSE);if(rng$seed_exists_at_error)rng$at_error<-.Random.seed}
 saveRDS(rng,file.path(directory,"RNG.rds"))
 if(!is.null(source_error)){record<-list(class=as.list(class(source_error)),message=conditionMessage(source_error),call=as.list(deparse(conditionCall(source_error))));saveRDS(record,file.path(directory,"original-error.rds"))}else record<-NULL
 result<-list(expected_cli_exit=if(is.null(source_error)&&!policy_refused)0L else 2L,source_graph_returned=graph_returned,source_order_returned=order_returned,source_error=record,declared_convergence_policy_refused=policy_refused,operation=p$operation,parameters=p,source_cds=case$source_cds,warnings=as.list(warnings),messages=as.list(messages))
 write_json(result,file.path(directory,"RESULT.json"));results[[case$name]]<-result;cat(case$name,"expected CLI",result$expected_cli_exit,"\n");flush.console()
}
stopifnot(identical(before,signatures()))
write_json(list(status="PASS_UNCHANGED_ORIGINAL100_GRAPH_REFERENCE_CASES",cases=results,source_signatures_unchanged=TRUE,source_function_signatures=before,adapter_worker_sourced=FALSE,whole_paper_reproduced=FALSE),file.path(out,"RESULT.json"))
