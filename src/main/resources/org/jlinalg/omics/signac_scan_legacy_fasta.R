# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Preserve old RegionStats' getSeq(names=GRanges) call on the current FaFile API.
# The original function body and selected genomic ranges remain unchanged.
signac_scan_legacy_fasta<-function(genome){
  if(!methods::is(genome,'FaFile'))stop('Legacy FASTA bridge requires an existing FaFile')
  # FaFile is a reference class. Keep its identity and original generic intact;
  # scope only the old explicitly qualified argument spelling inside this worker.
  namespace<-asNamespace('BSgenome')
  while(!exists('getSeq',envir=namespace,inherits=FALSE)){
    namespace<-parent.env(namespace)
    if(identical(namespace,emptyenv()))stop('Original BSgenome getSeq binding unavailable')
  }
  original<-get('getSeq',namespace,inherits=FALSE)
  replacement<-function(x,names=NULL,...){
    if(methods::is(x,'FaFile')&&!is.null(names)){
      if(!methods::is(names,'GenomicRanges')||'param'%in%names(list(...)))stop('Legacy FASTA bridge requires only explicit unchanged GenomicRanges')
      original(x=x,param=names,...)
    }else if(is.null(names))original(x=x,...) else original(x=x,names=names,...)
  }
  unlockBinding('getSeq',namespace);assign('getSeq',replacement,envir=namespace);lockBinding('getSeq',namespace)
  restore<-function(){unlockBinding('getSeq',namespace);assign('getSeq',original,envir=namespace);lockBinding('getSeq',namespace)}
  list(genome=genome,restore=restore)
}
