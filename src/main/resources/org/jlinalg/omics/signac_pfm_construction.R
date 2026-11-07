# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Capture actual upstream PFMatrix slots; do not alter models passed to motifmatchr.
guard_signac_pfm_constructor<-function(){
  original<-get('PFMatrix',asNamespace('TFBSTools'))
  expected<-quote({
    mode(profileMatrix)<-'integer'
    new('PFMatrix',ID=ID,name=name,matrixClass=matrixClass,strand=strand,bg=bg,tags=tags,profileMatrix=profileMatrix)
  })
  if(!identical(body(original),expected))stop('TFBSTools PFMatrix constructor body differs from the retained 1.50.0 integer-construction profile')
  invisible(TRUE)
}

capture_signac_effective_pfms<-function(pfm,typed,path){
  rows<-vector('list',nrow(typed));changed<-0L;positions<-0L;cursor<-0L
  for(id in names(pfm)){
    model<-pfm[[id]];matrix<-methods::slot(model,'profileMatrix');background<-methods::slot(model,'bg');declared<-typed[typed$motif_id==id,,drop=FALSE]
    if(!identical(rownames(matrix),c('A','C','G','T'))||typeof(matrix)!='integer'||ncol(matrix)!=nrow(declared))stop('Effective upstream PFM structure differs')
    original<-t(as.matrix(data.frame(lapply(declared[,c('A','C','G','T'),drop=FALSE],as.numeric))));changed<-changed+sum(original!=matrix);positions<-positions+ncol(matrix)
    for(position in seq_len(ncol(matrix))){cursor<-cursor+1L;rows[[cursor]]<-c(id,methods::slot(model,'name'),position,vapply(matrix[,position],function(v)trimws(number(v)),character(1)),vapply(background[c('A','C','G','T')],function(v)trimws(number(v)),character(1)))}
  }
  write_tsv(path,c('motif_id','motif_name','position','A','C','G','T','bg_A','bg_C','bg_G','bg_T'),rows)
  list(positions=positions,changed_counts=changed)
}
