# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Source this file in the R session holding the study's exact PFMatrixList.
# No download, installation, pseudocount or model normalization is performed.
write_signac_pfms<-function(pfm,path,id_source=c('list_names','matrix_id')){
  id_source<-match.arg(id_source)
  if(!requireNamespace('TFBSTools',quietly=TRUE))stop('SKIP3: TFBSTools is unavailable. Provide/install it only where permitted; otherwise skip PFM export and dependent motif analysis.')
  if(!methods::is(pfm,'PFMatrixList'))stop('Require PFMatrixList counts; PWMatrix log-odds models are a different input contract')
  models<-as.list(pfm);if(!length(models)||length(models)>100000L)stop('Require 1..100000 complete PFM models')
  ids<-if(id_source=='list_names')names(pfm) else vapply(models,function(x)methods::slot(x,'ID'),character(1))
  if(is.null(ids)||length(ids)!=length(models)||anyNA(ids)||any(ids=='')||anyDuplicated(ids))stop('Require explicit unique IDs. Preserve existing list names, or consciously choose id_source="matrix_id" and record that mapping; no fallback/deduplication is inferred.')
  valid_text<-function(x)length(x)==1L&&!is.na(x)&&nzchar(trimws(x))&&!grepl('[\t\r\n]',x)&&nchar(x,type='bytes')<=4094L
  if(!all(vapply(ids,valid_text,logical(1))))stop('PFM IDs are outside the typed UTF-8 TSV identity contract')
  number<-function(x)trimws(formatC(x,format='g',digits=17,decimal.mark='.'))
  parts<-vector('list',length(models)+1L);parts[[1L]]<-'motif_id\tmotif_name\tposition\tA\tC\tG\tT\tbg_A\tbg_C\tbg_G\tbg_T';total<-0L;bytes<-nchar(parts[[1L]],type='bytes')+1
  for(i in seq_along(models)){
    model<-models[[i]];name<-methods::slot(model,'name');matrix<-methods::slot(model,'profileMatrix');background<-methods::slot(model,'bg')
    if(!valid_text(name)||!setequal(rownames(matrix),c('A','C','G','T'))||ncol(matrix)<1L||ncol(matrix)>512L)stop('Invalid PFM label/alphabet/width; no relabeling or position truncation was applied')
    matrix<-matrix[c('A','C','G','T'),,drop=FALSE];background<-background[c('A','C','G','T')]
    if(length(background)!=4L||any(!is.finite(background))||any(background<=0)||abs(sum(background)-1)>2e-12||any(!is.finite(matrix))||any(matrix<0)||any(matrix>1e12)||any(colSums(matrix)<=0))stop('Invalid declared PFM counts/background; no normalization or pseudocounts were applied')
    total<-total+ncol(matrix);if(total>2000000L)stop('Combined PFM positions exceed the typed export capacity')
    parts[[i+1L]]<-vapply(seq_len(ncol(matrix)),function(position)paste(c(ids[[i]],name,position,vapply(matrix[,position],number,character(1)),vapply(background,number,character(1))),collapse='\t'),character(1))
    sizes<-nchar(enc2utf8(parts[[i+1L]]),type='bytes');if(any(sizes>16384L))stop('PFM TSV line exceeds the native input capacity')
    bytes<-bytes+sum(sizes+1);if(bytes>256000000)stop('PFM TSV exceeds the native 256 MB input capacity; no partial output was written')
  }
  path<-path.expand(path);if(file.exists(path)||dir.exists(path))stop('Preserve existing output and choose a NEW PFM TSV path')
  if(!dir.exists(dirname(path)))stop('Create/select an existing permitted output directory')
  connection<-file(path,'wb');on.exit(close(connection));for(lines in parts)writeBin(charToRaw(enc2utf8(paste0(paste(lines,collapse='\n'),'\n'))),connection)
  invisible(list(path=path,motifs=length(models),positions=total,id_source=id_source,counts_and_background_unchanged=TRUE,analysis_performed=FALSE))
}
