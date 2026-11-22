# Invocation-only wrapper. Original scripts and their exit behavior are unchanged.
unhex <- function(x) rawToChar(as.raw(strtoi(substring(x,seq(1,nchar(x),2),seq(2,nchar(x),2)),16L)))
.libPaths(unique(c(vapply(readLines("libraries.hex",warn=FALSE),unhex,""),.Library)))
if(!requireNamespace("jsonlite",quietly=TRUE))stop("Missing declared JSON transport dependency")
cfg <- jsonlite::read_json("request.json",simplifyVector=FALSE)
RNGkind("Mersenne-Twister","Inversion","Rejection")
set.seed(cfg$seed)
source(switch(cfg$operation,ASSOCIATE="FUSION.assoc_test.R",TRAIN="FUSION.compute_weights.R",POSTPROCESS="FUSION.post_process.R"),local=.GlobalEnv,chdir=FALSE)
