# Canonical fingerprints retain original-function identity without redistributing GPL2-only source.
args <- commandArgs(trailingOnly=TRUE);stopifnot(length(args)==4L)
.libPaths(args[2:3],include.site=FALSE);stopifnot(as.character(getRversion())=="4.3.3",as.character(packageVersion("slam"))=="0.1.55")
definitions <- list();ns <- asNamespace("slam")
for(expr in parse(args[4],keep.source=FALSE))if(is.call(expr)&&length(expr)==3L&&is.symbol(expr[[1]])&&as.character(expr[[1]]) %in% c("<-","=")&&is.symbol(expr[[2]])&&is.call(expr[[3]])&&identical(expr[[3]][[1]],as.name("function")))definitions[[as.character(expr[[2]])]] <- expr[[3]]
name <- "rowapply_simple_triplet_matrix";original <- eval(definitions[[name]],ns);loaded <- get(name,ns,inherits=FALSE)
stopifnot(identical(body(original),body(loaded)),identical(formals(original),formals(loaded)))
sha <- function(x)digest::digest(serialize(x,NULL,version=3),algo="sha256",serialize=FALSE)
result <- list(slam=setNames(list(list(body_sha256=sha(body(original)),formals_sha256=sha(formals(original)),serialization_version=3L,original_body_formals_identical=TRUE)),name))
jsonlite::write_json(result,file.path(args[1],"FUNCTION-FINGERPRINTS.json"),pretty=TRUE,auto_unbox=TRUE,digits=NA)
cat("PASS original SLAM body/formals and canonical serialization3 fingerprints\n")
