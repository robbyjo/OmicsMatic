# Original scripts and exit behavior are unchanged. Save association state only
# after normal return, so rounded exports cannot change conditional gate audits.
unhex <- function(x) rawToChar(as.raw(strtoi(substring(x,seq(1,nchar(x),2),seq(2,nchar(x),2)),16L)))
.libPaths(unique(c(vapply(readLines("libraries.hex",warn=FALSE),unhex,""),.Library)))
if(!requireNamespace("jsonlite",quietly=TRUE))stop("Missing declared JSON transport dependency")
cfg <- jsonlite::read_json("request.json",simplifyVector=FALSE)
(function(control) {
  force(control)
  RNGkind("Mersenne-Twister","Inversion","Rejection")
  set.seed(control$seed)
  source(switch(control$operation,ASSOCIATE="FUSION.assoc_test.R",TRAIN="FUSION.compute_weights.R",POSTPROCESS="FUSION.post_process.R"),local=.GlobalEnv,chdir=FALSE)
  if(identical(control$operation,"ASSOCIATE")) {
    saveRDS(list(association=get("out.tbl",envir=.GlobalEnv,inherits=FALSE),
                 source_options=get("opt",envir=.GlobalEnv,inherits=FALSE),
                 mhc=get("mhc",envir=.GlobalEnv,inherits=FALSE)),"source-association-state.rds",version=3)
  }
})(cfg)
