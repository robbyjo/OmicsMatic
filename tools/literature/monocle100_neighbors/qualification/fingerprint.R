# Fingerprints of selected original SLAM source; no GPL2-only source is redistributed.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==4L)
.libPaths(args[2:3],include.site=FALSE)
stopifnot(as.character(getRversion())=="4.3.3",as.character(packageVersion("slam"))=="0.1.55")
definitions<-list();ns<-asNamespace("slam")
for(expr in parse(args[4],keep.source=FALSE))if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]]) %in% c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))definitions[[as.character(expr[[2]])]]<-expr[[3]]
name<-"rowapply_simple_triplet_matrix";original<-eval(definitions[[name]],ns);loaded<-get(name,ns,inherits=FALSE)
stopifnot(identical(body(original),body(loaded),ignore.srcref=FALSE,ignore.bytecode=FALSE),identical(formals(original),formals(loaded),ignore.srcref=FALSE,ignore.bytecode=FALSE))
# Version3 includes the session native-encoding header. Version2 is explicit,
# remains source-object-sensitive, and matches this ASCII function across locales.
sha<-function(x)digest::digest(serialize(x,NULL,version=2),algo="sha256",serialize=FALSE)
expected<-list(body_sha256=sha(body(original)),formals_sha256=sha(formals(original)),serialization_version=2L,original_body_formals_identical=TRUE)
matches<-function(fun)identical(sha(body(fun)),expected$body_sha256)&&identical(sha(formals(fun)),expected$formals_sha256)
body_changed<-loaded;body(body_changed)<-substitute({ORIGINAL;NULL},list(ORIGINAL=body(loaded)))
argument_changed<-loaded;formals(argument_changed)<-c(formals(loaded),alist(omicsmatic_guard_changed=TRUE))
stopifnot(matches(original),matches(loaded),!matches(body_changed),!matches(argument_changed))
jsonlite::write_json(list(slam=setNames(list(expected),name)),file.path(args[1],"FUNCTION-FINGERPRINTS.json"),pretty=TRUE,auto_unbox=TRUE,digits=NA)
jsonlite::write_json(list(status="PASS_ORIGINAL_SOURCE_IDENTITY_AND_CHANGED_BODY_FORMALS_REFUSALS",locale=Sys.getlocale("LC_CTYPE"),original_loaded_strict_identity=TRUE,serialization_version=2L,original_accepted=matches(original),loaded_accepted=matches(loaded),changed_body_rejected=!matches(body_changed),changed_formals_rejected=!matches(argument_changed)),file.path(args[1],"CONTROLS.json"),pretty=TRUE,auto_unbox=TRUE,digits=NA)
cat("PASS strict original-source comparison and changed body/formals refusals\n")
