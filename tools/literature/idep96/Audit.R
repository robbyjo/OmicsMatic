# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Static parsing only: do not evaluate source expressions, load app packages or start Shiny.
args <- commandArgs(trailingOnly=TRUE)
if(length(args)!=2L)stop('Audit.R CAPTURED_SOURCE NEW_RECORD_DIRECTORY')
sourceRoot <- args[[1]]; out <- args[[2]]
if(!dir.create(out))stop('Audit output must be new')
dir.create(file.path(out,'expressions'))
beforeSeed <- if(exists('.Random.seed',envir=.GlobalEnv,inherits=FALSE))get('.Random.seed',envir=.GlobalEnv) else NULL
bindings <- list(); calls <- list(); inputs <- list()
flat <- function(x)paste(deparse(x,width.cutoff=500L,control='keepNA'),collapse='\n')
headName <- function(x)if(is.call(x))flat(x[[1]]) else ''
missingBox <- unname(as.list(quote(f(,)))[2])
selected <- c('iDEPversion','maxSamples','maxSamplesDefault','maxComparisons','maxFactors',
 'kurtosis.log','kurtosis.warning','readData','readSampleInfo','sampleInfo','convertedData',
 'convertedCounts','geneInfo','allGeneInfo','DEG','degs','modelFactors','referenceLevels','datapath','quotes')
effects <- c('library','require','requireNamespace','source','download.file','install.packages',
 'BiocManager::install','system','system2','dbConnect','dbGetQuery','set.seed','DESeq','results','DESeqDataSetFromMatrix',
 'DEG.DESeq2','DEG.limma','cpm','DGEList','estimateSizeFactors','counts','rlog','vst','readLines','read.csv','read.table','load','eval','parse')
inputTypes <- c('numericInput','sliderInput','checkboxInput','radioButtons','selectInput','selectizeInput','textInput')
literal <- function(x)is.atomic(x)&&length(x)==1L&&!is.na(x)
walk <- function(node,file,path,scope='global') {
 if(is.expression(node)||is.pairlist(node)) {
  boxes <- as.list(node)
  for(k in seq_along(boxes))if(!identical(unname(boxes[k]),missingBox))walk(boxes[[k]],file,paste0(path,'.',k),scope)
  return(invisible(NULL))
 }
 if(!is.call(node))return(invisible(NULL))
 op <- headName(node); parts <- as.list(node)
 if(op%in%c('<-','=','<<-')&&length(node)==3L) {
  lhs <- flat(node[[2]]); rhs <- node[[3]]; rh <- headName(rhs)
  if(rh%in%c('function','reactive','eventReactive')||lhs%in%selected||(scope=='global'&&literal(rhs))) {
   record <- sprintf('expressions/%06d.R',length(bindings)+1L)
   writeLines(flat(rhs),file.path(out,record),useBytes=TRUE)
   known <- literal(rhs)
   bindings[[length(bindings)+1L]] <<- data.frame(file=file,path=path,scope=scope,lhs=lhs,
    kind=if(rh=='function')'function' else if(rh%in%c('reactive','eventReactive'))rh else if(known)'literal' else 'expression',
    rhs_head=rh,literal_known=known,literal_value=if(known)as.character(rhs) else '',
    formals_expression=if(rh=='function')flat(rhs[[2]]) else '',expression_record=record,stringsAsFactors=FALSE)
  }
  if(rh=='function') {
   walk(rhs[[2]],file,paste0(path,'.3.2'),paste0(scope,'/',lhs,'/formals'))
   walk(rhs[[3]],file,paste0(path,'.3.3'),paste0(scope,'/',lhs))
   return(invisible(NULL))
  }
 }
 if(op%in%effects)calls[[length(calls)+1L]] <<- data.frame(file=file,path=path,scope=scope,head=op,expression=flat(node),stringsAsFactors=FALSE)
 if(op%in%inputTypes) {
  a <- parts[-1]; an <- names(a)
  index <- if(!is.null(an)&&'inputId'%in%an)which(an=='inputId')[[1]] else 1L
  id <- if(length(a)>=index&&literal(a[[index]])&&is.character(a[[index]]))a[[index]] else ''
  get <- function(key)if(!is.null(an)&&key%in%an)flat(a[[which(an==key)[[1]]]]) else ''
  inputs[[length(inputs)+1L]] <<- data.frame(file=file,path=path,type=op,id=id,
   value_expression=get('value'),selected_expression=get('selected'),choices_expression=get('choices'),
   complete_call=flat(node),stringsAsFactors=FALSE)
 }
 if(op=='function') {
  walk(node[[2]],file,paste0(path,'.2'),paste0(scope,'/anonymous@',path,'/formals'))
  walk(node[[3]],file,paste0(path,'.3'),paste0(scope,'/anonymous@',path));return(invisible(NULL))
 }
 for(k in seq_along(parts)[-1])if(!identical(unname(parts[k]),missingBox))walk(parts[[k]],file,paste0(path,'.',k),scope)
 invisible(NULL)
}
for(name in c('server.R','iDEP_core_functions.R','ui.R')) {
 code <- parse(file.path(sourceRoot,'shinyapps/idep96',name),keep.source=TRUE,encoding='UTF-8')
 walk(code,name,'root')
}
saveTable <- function(rows,name,columns) {
 data <- if(length(rows))do.call(rbind,rows) else as.data.frame(setNames(rep(list(character()),length(columns)),columns))
 write.table(data,file.path(out,name),sep='\t',quote=TRUE,qmethod='double',row.names=FALSE,col.names=TRUE,fileEncoding='UTF-8')
}
saveTable(bindings,'bindings.tsv',c('file','path','scope','lhs','kind','rhs_head','literal_known','literal_value','formals_expression','expression_record'))
saveTable(calls,'calls.tsv',c('file','path','scope','head','expression'))
saveTable(inputs,'inputs.tsv',c('file','path','type','id','value_expression','selected_expression','choices_expression','complete_call'))
afterSeed <- if(exists('.Random.seed',envir=.GlobalEnv,inherits=FALSE))get('.Random.seed',envir=.GlobalEnv) else NULL
if(!identical(beforeSeed,afterSeed))stop('Static parsing changed RNG state')
writeLines(c(R.version.string,capture.output(sessionInfo())),file.path(out,'SESSION.txt'),useBytes=TRUE)
writeLines(c('PASS_PARSE_ONLY_NO_ORIGINAL_EXPRESSION_EVALUATION',paste('bindings',length(bindings)),
 paste('selected_calls',length(calls)),paste('UI_inputs',length(inputs)),'RNG_unchanged TRUE'),file.path(out,'RETURN.txt'))
