# Base-R references for the exact stream consumed by the MR-PRESSO profile.
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args<-commandArgs(TRUE);stopifnot(length(args)==1L,dir.exists(args[1]))
seeds<-c(0L,1L,19L,2147483647L,-1L,-2147483647L)
for(kind in c('Rejection','Rounding'))for(seed in seeds){
  suppressWarnings(RNGkind('Mersenne-Twister','Inversion',kind));set.seed(seed)
  path<-file.path(args[1],paste0(tolower(kind),'-',seed,'.tsv'));stopifnot(!file.exists(path))
  stream<-file(path,open='wt',encoding='UTF-8')
  emit<-function(...)writeLines(paste(c(...),collapse='\t'),stream)
  state<-function()emit('STATE',length(.Random.seed),as.character(.Random.seed))
  emit('PROFILE',R.version.string,kind,as.character(seed));state()
  values<-runif(625);emit('UNIFORM',length(values),sprintf('%.17g',values));state()
  values<-rnorm(625);emit('NORMAL',length(values),sprintf('%.17g',values));state()
  for(pair in list(c(.3,0),c(Inf,.1),c(-Inf,.1),c(NaN,.1),c(.3,-1),c(.3,Inf),c(.3,.12))){
    value<-suppressWarnings(rnorm(1,pair[1],pair[2]));emit('SCALAR',sprintf('%.17g',c(pair,value)));state()
  }
  for(plan in list(c(0L,0L),c(1L,1L),c(2L,1L),c(2L,2L),c(7L,7L),c(16L,5L),c(17L,17L),c(257L,257L),c(65536L,4L),c(65537L,4L),c(1000000L,8L))){
    values<-sample.int(plan[1],plan[2],replace=FALSE)-1L;emit('SAMPLE',plan,values);state()
  }
  close(stream)
}
writeLines(c(R.version.string,R.home(),paste(RNGkind(),collapse='/')),file.path(args[1],'RUNTIME.txt'))
