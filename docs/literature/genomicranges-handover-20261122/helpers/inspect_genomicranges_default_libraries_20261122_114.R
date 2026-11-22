args<-commandArgs(TRUE)
dir.create(args[[1]],recursive=TRUE,showWarnings=FALSE)
cat("R_HOME",R.home(),"\n")
cat("R_VERSION",R.version.string,"\n")
for(lib in .libPaths()){
 cat("LIBRARY",normalizePath(lib,winslash="/",mustWork=TRUE),"\n")
 for(pkg in c("GenomicRanges","IRanges","S4Vectors","GenomeInfoDb","GenomeInfoDbData","XVector","BiocGenerics","Seqinfo")){
  if(file.exists(file.path(lib,pkg,"DESCRIPTION"))){
   d<-read.dcf(file.path(lib,pkg,"DESCRIPTION"))
   cat("PACKAGE",pkg,d[[1,"Version"]],file.path(lib,pkg),"\n")
  }
 }
}
saveRDS(list(R=R.version,libraries=.libPaths(),session=sessionInfo()),file.path(args[[1]],"inspection.rds"))
