# Independently call unchanged original namespaces; never source the adapter worker.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==4L)
.libPaths(args[2:3],include.site=FALSE)
stopifnot(as.character(getRversion())=="4.3.3",as.character(packageVersion("monocle3"))=="1.0.0",as.character(packageVersion("RANN"))=="2.6.2",as.character(packageVersion("spdep"))=="1.4.1")
plan<-jsonlite::read_json(args[4],simplifyVector=FALSE);stopifnot(length(plan$cases)==66L)
signatures<-function(){result<-list();for(package in c("monocle3","RANN","spdep","slam")){names<-switch(package,monocle3=c("calculateLW","jaccard_coeff","new_cell_data_set"),RANN="nn2",spdep=c("nb2listw","card"),slam="rowapply_simple_triplet_matrix");result[[package]]<-lapply(setNames(names,names),function(name){fun<-get(name,asNamespace(package),inherits=FALSE);list(body=digest::digest(serialize(body(fun),NULL,version=2),algo="sha256",serialize=FALSE),formals=digest::digest(serialize(formals(fun),NULL,version=2),algo="sha256",serialize=FALSE))})};result}
before<-signatures();results<-list();nearest_returns<-weight_returns<-original_errors<-RNG_returns<-0L
for(case in plan$cases){
 root<-case$capture;request<-jsonlite::read_json(file.path(root,"effective-request.json"),simplifyVector=FALSE)
 cds<-readRDS(file.path(root,"inputs/source-cds.rds"));slot<-request$reduction_slot;coordinates<-SingleCellExperiment::reducedDims(cds)[[slot]];n<-ncol(cds)
 RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(as.integer(request$seed));rng<-list(initial=.Random.seed)
 nearest<-RANN::nn2(coordinates,coordinates,min(request$k+1L,n),searchtype="standard");rng$after_nn<-.Random.seed
 stopifnot(identical(nearest,readRDS(file.path(root,"worker/original-nearest.rds"))));nearest_returns<-nearest_returns+1L
 error<-NULL;returned<-NA
 if(request$engine=="original"){
  rng$before_calculate<-.Random.seed
  error<-tryCatch({
   lw<-get("calculateLW",asNamespace("monocle3"))(cds,k=as.integer(request$k),neighbor_graph=request$neighbor_graph,reduction_method=slot,verbose=FALSE)
   rng$after_calculate<-.Random.seed
   stopifnot(identical(lw,readRDS(file.path(root,"worker/original-listw.rds"))))
   NULL
  },error=identity)
  returned<-is.null(error)
  stopifnot(identical(returned,case$expected_success))
  if(returned)weight_returns<-weight_returns+1L else{
   original<-readRDS(file.path(root,"worker/original-error.rds"))
   stopifnot(identical(as.list(class(error)),original$class),identical(conditionMessage(error),original$message),identical(as.list(deparse(conditionCall(error))),original$call))
   original_errors<-original_errors+1L
  }
 }
 if(is.null(error))rng$final<-.Random.seed
 stopifnot(identical(rng,readRDS(file.path(root,"worker/RNG.rds"))))
 RNG_returns<-RNG_returns+1L
 results[[case$name]]<-list(complete_nearest_return_identical=TRUE,original_calculateLW_executed=request$engine=="original",complete_listw_identical=if(request$engine=="original")returned else NULL,original_error_class_message_call_identical=if(request$engine=="original")!returned else NULL,complete_RNG_return_identical=TRUE)
 cat("PASS independent original",case$name,"\n");flush.console()
}
stopifnot(identical(before,signatures()),nearest_returns==66L,weight_returns==25L,original_errors==8L,RNG_returns==66L)
result<-list(status="PASS_INDEPENDENT_ORIGINAL66_NEAREST25_LISTW8_ERRORS66_RNG",complete_original_nearest_returns=nearest_returns,complete_original_listw_returns=weight_returns,original_errors_class_message_call_identical=original_errors,complete_RNG_returns=RNG_returns,source_function_signatures_unchanged=TRUE,source_signatures=before,cases=results,adapter_worker_sourced=FALSE,new_catalog_credit=0L,whole_goal_complete=FALSE)
jsonlite::write_json(result,file.path(args[1],"RESULT.json"),pretty=TRUE,auto_unbox=TRUE,digits=NA,na="null",null="null")
