args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==4L)
.libPaths(args[2:3],include.site=FALSE)
stopifnot(as.character(getRversion())=="4.3.3",as.character(packageVersion("slam"))=="0.1.55")
definitions<-list();ns<-asNamespace("slam")
for(expr in parse(args[4],keep.source=FALSE))if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]]) %in% c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))definitions[[as.character(expr[[2]])]]<-expr[[3]]
name<-"rowapply_simple_triplet_matrix";original<-eval(definitions[[name]],ns);loaded<-get(name,ns,inherits=FALSE)
sha<-function(x,version)digest::digest(serialize(x,NULL,version=version),algo="sha256",serialize=FALSE)
result<-list(R=as.character(getRversion()),platform=R.version$platform,function_name=name,loaded_namespace_path=getNamespaceInfo(ns,"path"),comparisons=list())
for(part in c("body","formals")){
 a<-if(part=="body")body(original)else formals(original);b<-if(part=="body")body(loaded)else formals(loaded)
 result$comparisons[[part]]<-list(identical_default=identical(a,b),identical_strict=identical(a,b,ignore.srcref=FALSE,ignore.bytecode=FALSE),original_type=typeof(a),loaded_type=typeof(b),original_attribute_names=as.list(names(attributes(a))),loaded_attribute_names=as.list(names(attributes(b))),serialization=list())
 for(version in c(2L,3L))result$comparisons[[part]]$serialization[[as.character(version)]]<-list(original_sha256=sha(a,version),loaded_sha256=sha(b,version),raw_identical=identical(serialize(a,NULL,version=version),serialize(b,NULL,version=version)))
}
jsonlite::write_json(result,file.path(args[1],"RESULT.json"),pretty=TRUE,auto_unbox=TRUE,digits=NA)
cat("Saved fingerprint diagnosis; no original source was changed\n")
