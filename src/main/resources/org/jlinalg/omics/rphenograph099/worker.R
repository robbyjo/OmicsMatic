# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Executes the unmodified Hao Chen Rphenograph 0.99.1 function; no edition substitution.
args<-commandArgs(TRUE);out<-args[[1]];.libPaths(args[-1]);warnings<-character()
unavailable<-function(message)stop(structure(list(message=message),class=c("rphenograph_unavailable","error","condition")))
save_json<-function(value,name)jsonlite::write_json(value,file.path(out,name),auto_unbox=TRUE,pretty=TRUE,digits=NA,null="null")
canonical<-function(f)paste(c(deparse(formals(f),width.cutoff=500L),deparse(body(f),width.cutoff=500L)),collapse="\n")
graph_state<-function(g)list(directed=igraph::is_directed(g),graph_attributes=igraph::graph_attr(g),vertices=igraph::as_data_frame(g,what="vertices"),edges=igraph::as_data_frame(g,what="edges"))
model_state<-function(fit)list(graph=graph_state(fit[[1]]),community=fit[[2]])
fmt<-function(v){if(is.numeric(v))sprintf("%.17g",v)else as.character(v)}
write_table<-function(columns,name){
 stopifnot(length(unique(lengths(columns)))==1L)
 con<-file(file.path(out,name),"wt",encoding="UTF-8");on.exit(close(con));writeLines(paste(names(columns),collapse="\t"),con)
 if(length(columns[[1]]))writeLines(do.call(paste,c(lapply(columns,fmt),sep="\t")),con)
}
code<-tryCatch(withCallingHandlers({
 if(!requireNamespace("jsonlite",quietly=TRUE))unavailable("Required jsonlite runtime is unavailable")
 profile<-jsonlite::fromJSON(file.path(out,"source-profile.json"),simplifyVector=FALSE)
 if(as.character(getRversion())!=profile$r_version)unavailable("R version differs from the selected exact provider")
 for(name in names(profile$package_versions))if(!requireNamespace(name,quietly=TRUE)||as.character(packageVersion(name))!=profile$package_versions[[name]])unavailable(paste("Required exact namespace unavailable:",name,profile$package_versions[[name]]))
 ns<-asNamespace("Rphenograph");dir.create(file.path(out,"installed-functions"))
 for(name in names(profile$functions)){
  path<-file.path(out,"installed-functions",paste0(name,".txt"));con<-file(path,"wb");writeChar(enc2utf8(canonical(get(name,ns,inherits=FALSE))),con,eos=NULL,useBytes=TRUE);close(con)
  digest<-system2("/usr/bin/sha256sum",shQuote(path),stdout=TRUE);stopifnot(is.null(attr(digest,"status")),strsplit(digest," ",fixed=TRUE)[[1]][1]==profile$functions[[name]])
 }
 request<-jsonlite::fromJSON(file.path(out,"worker-request.json"),simplifyVector=FALSE)
 table<-read.delim(file.path(out,"inputs/matrix.tsv"),check.names=FALSE,quote="",comment.char="",na.strings=character(),colClasses="character",strip.white=FALSE)
 ids<-table[[1]];features<-names(table)[-1];x<-matrix(0,nrow(table),length(features),dimnames=list(ids,features))
 for(j in seq_along(features))x[,j]<-as.numeric(table[[j+1]])
 stopifnot(all(is.finite(x)),!anyDuplicated(ids),!anyDuplicated(features))
 RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(as.integer(request$seed))
 fit<-Rphenograph::Rphenograph(x,k=as.integer(request$k))
 saveRDS(fit,file.path(out,"original-model.rds"));stopifnot(identical(model_state(fit),model_state(readRDS(file.path(out,"original-model.rds")))));saveRDS(model_state(fit),file.path(out,"original-state.rds"))
 # Original RANN ordering and the source's first-column removal are retained literally.
 nn<-get("find_neighbors",ns)(x,k=as.integer(request$k)+1L);used<-nn[,-1];raw<-get("jaccard_coeff",ns)(used)
 saveRDS(list(neighbors_full=nn,neighbors_used=used,raw_coefficients=raw),file.path(out,"complete-components.rds"))
 neighbor_columns<-c(list(cell_id=ids),setNames(lapply(seq_len(ncol(nn)),function(j)nn[,j]),paste0("neighbor_",seq_len(ncol(nn)))))
 write_table(neighbor_columns,"neighbors-full.tsv");write_table(list(from=raw[,1],to=raw[,2],weight=raw[,3]),"coefficients-raw.tsv")
 graph<-fit[[1]];vertex_names<-igraph::V(graph)$name;membership<-unname(igraph::membership(fit[[2]]));indices<-as.numeric(vertex_names)
 stopifnot(length(membership)==length(vertex_names),all(is.finite(indices)),all(indices==floor(indices)),all(indices>=1&indices<=nrow(x)))
 write_table(list(vertex_number=seq_along(indices),vertex_name=vertex_names,source_cell_row=indices,cell_id=ids[indices],membership=membership),"graph-vertices.tsv")
 edges<-igraph::as_data_frame(graph,what="edges");write_table(as.list(edges),"graph-edges.tsv")
 positions<-match(seq_len(nrow(x)),indices);assignments<-rep(NA_real_,nrow(x));assignments[!is.na(positions)]<-membership[positions[!is.na(positions)]]
 write_table(list(cell_id=ids,vertex_number=positions,membership=assignments),"cell-assignments.tsv")
 q<-igraph::modularity(fit[[2]]);reportable<-length(indices)>0&&length(q)==1&&is.finite(q)
 if(length(indices)<nrow(x))warnings<-c(warnings,paste(nrow(x)-length(indices),"input cells have no vertex in the original graph; their assignments remain NA. No vertices were added."))
 if(!reportable)warnings<-c(warnings,"The original function returned an empty graph or nonfinite modularity. Complete original outputs are retained; no usable clustering is admitted.")
 versions<-setNames(lapply(sort(loadedNamespaces()),function(n)as.character(packageVersion(n))),sort(loadedNamespaces()))
 value<-list(schema_version=1,version="0.99.1",source_commit=profile$source_commit,R_version=as.character(getRversion()),package_versions=versions,cell_ids=as.list(ids),feature_ids=as.list(features),k=request$k,seed=request$seed,input_units=request$input_units,input_provenance=request$input_provenance,analysis_population=request$analysis_population,directed=igraph::is_directed(graph),graph_vertices=igraph::vcount(graph),graph_edges=igraph::ecount(graph),modularity=fmt(q),complete_model_roundtrip=TRUE,community_field_names=as.list(names(unclass(fit[[2]]))),reportable_clustering=reportable,warnings=as.list(warnings),paper_package_identity_recovered=FALSE,whole_goal_complete=FALSE)
 save_json(value,"worker-result.json");capture.output(sessionInfo(),file=file.path(out,"sessionInfo.txt"));cat("COMPLETE_ORIGINAL_RPHENOGRAPH099_FUNCTION\n");0L
},warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")}),error=function(e){
 exit<-if(inherits(e,"rphenograph_unavailable"))3L else 2L
 writeLines(conditionMessage(e),file.path(out,"worker-error.txt"),useBytes=TRUE)
 if(requireNamespace("jsonlite",quietly=TRUE))save_json(list(status=if(exit==3L)"SKIP3_REQUIRED_RUNTIME_UNAVAILABLE" else "FAIL2_ORIGINAL_RPHENOGRAPH099_RETAINED",error=conditionMessage(e),warnings=as.list(warnings),result_admitted=FALSE),"worker-failure.json")
 exit
})
writeLines(as.character(code),file.path(out,"worker-exit.txt"));quit(save="no",status=code)
