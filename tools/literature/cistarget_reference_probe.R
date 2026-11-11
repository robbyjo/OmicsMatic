# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Execute unmodified function definitions selected from original GPL R source.
# No RcisTarget installation, S4/Feather/annotation wrapper or historical runtime claim.
args <- commandArgs(trailingOnly=TRUE)
input <- scan(args[1],what=character(),quiet=TRUE)
sourceRoot <- args[2]
out <- args[3]
dir.create(out)
original <- new.env(parent=globalenv())
loadDefinitions <- function(file,names) {
  statements <- parse(file=file,keep.source=TRUE)
  found <- character()
  for (statement in statements) {
    if (is.call(statement) && as.character(statement[[1]]) == '<-' &&
        is.symbol(statement[[2]]) && as.character(statement[[2]]) %in% names) {
      eval(statement,envir=original)
      found <- c(found,as.character(statement[[2]]))
    }
  }
  stopifnot(setequal(found,names))
  found
}
selected <- c(loadDefinitions(file.path(sourceRoot,'R/01_calcAUC.R'),'.auc'),
  loadDefinitions(file.path(sourceRoot,'R/02_addMotifAnnotation.R'),'.calcNES'),
  loadDefinitions(file.path(sourceRoot,'R/priv_calcErn.R'),c('.calcRCC.oneRanking','.calcRCC','.calcEnr_iCisTarget')),
  loadDefinitions(file.path(sourceRoot,'R/03_addSignificantGenes.R'),'.getSignificantGenes'))
writeLines(selected,file.path(out,'original-functions.txt'))
at <- 1L
nextToken <- function() {value <- input[at];at <<- at+1L;value}
writeMatrix <- function(value,file) write.table(value,file=file,sep='\t',quote=FALSE,row.names=FALSE,col.names=FALSE,na='NaN')
completed <- character()
while(at<=length(input)) {
  id <- nextToken();profile <- nextToken();total <- as.integer(nextToken());m <- as.integer(nextToken());n <- as.integer(nextToken())
  parameter <- as.numeric(nextToken());bound <- as.integer(nextToken())
  weights <- as.numeric(input[at:(at+n-1L)]);at <- at+n
  ranks <- matrix(as.numeric(input[at:(at+m*n-1L)]),nrow=m,byrow=TRUE);at <- at+m*n
  if(profile!='RCISTARGET_1_29_0_ICISTARGET') next
  home <- file.path(out,id);dir.create(home)
  motifNames <- paste0('motif_',seq_len(m)-1L);geneNames <- paste0('feature_',seq_len(n)-1L)
  rownames(ranks) <- motifNames;colnames(ranks) <- geneNames;oldRanks <- ranks
  auc <- apply(ranks,1,original$.auc,round(parameter),round(parameter)*n)
  nes <- original$.calcNES(auc)
  curves <- original$.calcRCC(ranks,bound,1)
  avg <- apply(curves,1,mean);dev <- apply(curves,1,sd);boundary <- avg+2*dev
  leading <- original$.calcEnr_iCisTarget(ranks,bound,motifNames,FALSE,1,50)
  frame <- data.frame(motif=motifNames,ranks,check.names=FALSE)
  targets <- original$.getSignificantGenes(geneNames,frame,motifNames,method='iCisTarget',maxRank=bound,plotCurve=FALSE,genesFormat='geneList',nCores=1,digits=17,nMean=50)
  stopifnot(identical(ranks,oldRanks))
  stats <- data.frame(motif=seq_len(m)-1L,auc=unname(auc),nes=unname(nes),leading_rank=unname(leading['x',]),leading_recovery=unname(leading['y',]),
    leading_excess=vapply(seq_len(m),function(i) leading['y',i]-boundary[leading['x',i]],numeric(1)))
  options(digits=17)
  write.table(stats,file.path(home,'metrics.tsv'),sep='\t',quote=FALSE,row.names=FALSE,na='NaN')
  writeMatrix(t(curves),file.path(home,'curves.tsv'))
  write.table(data.frame(rank=seq_len(bound-1L),mean=avg,sd=dev,mean_plus_two_sd=boundary),file.path(home,'background.tsv'),sep='\t',quote=FALSE,row.names=FALSE,na='NaN')
  write.table(data.frame(rounded_auc_count=round(parameter),source_auc_cutoff=round(parameter),mean_auc=mean(auc),sd_auc=sd(auc)),file.path(home,'parameters.tsv'),sep='\t',quote=FALSE,row.names=FALSE,na='NaN')
  targetRows <- data.frame(motif=integer(),source_order=integer(),feature=integer(),rank=numeric(),weight=numeric())
  for(i in seq_len(m)) {
    ids <- targets$enrichedGenes[[motifNames[i]]]
    indices <- match(ids,geneNames)
    if(length(indices)>0) targetRows <- rbind(targetRows,data.frame(motif=i-1L,source_order=seq_along(indices)-1L,feature=indices-1L,rank=ranks[i,indices],weight=weights[indices]))
  }
  write.table(targetRows,file.path(home,'targets.tsv'),sep='\t',quote=FALSE,row.names=FALSE,na='NaN')
  completed <- c(completed,id);cat('PASS original RcisTarget',id,'\n')
}
stopifnot(length(completed)==32L)
writeLines(c('PASS_32_UNCHANGED_RCISTARGET_FUNCTION_BOUNDARY_CONTROLS_ONLY',completed),file.path(out,'COMPLETED.txt'))
cat(R.version.string,'\n')
