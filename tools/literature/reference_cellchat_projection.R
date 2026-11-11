# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Original installed CellChat projectData controls; no function replacement.
args <- commandArgs(TRUE)
stopifnot(length(args)>=4L)
out <- args[1]; sourceRoot <- args[2]
.libPaths(c(args[-(1:2)],.Library),include.site=FALSE)
stopifnot(requireNamespace('CellChat',quietly=TRUE),identical(as.character(packageVersion('CellChat')),'1.1.3'))
warnings_seen <- list();status <- 'FAIL2_ORIGINAL_CELLCHAT_PROJECTION_REFERENCE';detail <- ''
writeJson <- function(x,path) jsonlite::write_json(x,path,auto_unbox=TRUE,pretty=TRUE,digits=NA,na='string')
readMatrix <- function(path) {
  # Mark UTF-8 bytes directly. fileEncoding would transcode via the C locale and
  # can silently truncate a Unicode identifier before the original function.
  d <- read.delim(path,sep='\t',quote='',comment.char='',check.names=FALSE,colClasses='character',na.strings=NULL,encoding='UTF-8')
  matrix(as.numeric(as.matrix(d[,-1,drop=FALSE])),nrow=nrow(d),dimnames=list(d[[1]],names(d)[-1]))
}
tryCatch(withCallingHandlers({
  definitions <- new.env(parent=baseenv())
  for(file in list.files(file.path(sourceRoot,'R'),pattern='\\.R$',full.names=TRUE)) for(statement in parse(file=file,keep.source=FALSE)) {
    if(is.call(statement) && identical(statement[[1]],as.name('<-')) && is.symbol(statement[[2]]) && is.call(statement[[3]]) && identical(statement[[3]][[1]],as.name('function'))) eval(statement,envir=definitions)
  }
  required <- c('projectData','projectAndRecombine','projectOnNetwork','randomWalkBySolve','l1NormalizeColumns','l1NormalizeRows','projectFromNetworkRecombine')
  form <- function(f) list(formals=deparse(formals(f),width.cutoff=500L),body=deparse(body(f),width.cutoff=500L))
  guard <- function() for(name in required) stopifnot(identical(form(get(name,envir=definitions)),form(get(name,envir=asNamespace('CellChat')))))
  guard();writeJson(setNames(lapply(required,function(n)form(get(n,envir=asNamespace('CellChat')))),required),file.path(out,'original-function-forms.json'))
  cases <- jsonlite::read_json(file.path(out,'cases.json'),simplifyVector=FALSE)
  results <- list()
  for(case in cases) {
    home <- file.path(out,case$id);expression <- readMatrix(file.path(home,'expression.tsv'));adjacency <- readMatrix(file.path(home,'ppi.tsv'))
    original <- methods::new('CellChat',data.signaling=expression)
    result <- CellChat::projectData(original,adjacency,alpha=case$alpha,normalizeAdjMatrix=case$normalization)
    expected <- as.matrix(result@data.project)
    stopifnot(identical(dimnames(expression),dimnames(expected)),all(is.finite(expected)))
    # write.table can escape Unicode names to <U+....> under LC_CTYPE=C.
    # Numeric cells are finite; write complete literal UTF-8 rows as raw bytes.
    lines <- c(paste(c('gene_id',colnames(expected)),collapse='\t'),vapply(seq_len(nrow(expected)),function(i) paste(c(rownames(expected)[i],formatC(expected[i,],digits=17,format='g')),collapse='\t'),character(1)))
    writeLines(enc2utf8(lines),file.path(home,'original-projected.tsv'),useBytes=TRUE)
    saveRDS(result,file.path(home,'original-result.rds'));results[[case$id]] <- list(genes=nrow(expected),cells=ncol(expected),complete=TRUE)
  }
  # Exact original endpoint/zero-row/zero-column refusals are retained too.
  e <- matrix(c(1,2,3,4),2,dimnames=list(c('A','B'),c('c1','c2')))
  object <- methods::new('CellChat',data.signaling=e);a <- matrix(c(1,1,1,1),2,dimnames=list(c('A','B'),c('A','B')))
  failures <- list(alpha0=list(alpha=0,adjacency=a),alpha1=list(alpha=1,adjacency=a),zero_row=list(alpha=.5,adjacency=matrix(c(0,1,0,1),2,dimnames=dimnames(a))),zero_column=list(alpha=.5,adjacency=matrix(c(0,0,1,1),2,dimnames=dimnames(a))))
  errors <- lapply(failures,function(f) tryCatch({CellChat::projectData(object,f$adjacency,alpha=f$alpha);stop('Unexpected success')},error=function(e)conditionMessage(e)))
  stopifnot(all(vapply(errors,function(e)!identical(e,'Unexpected success'),logical(1))))
  writeJson(errors,file.path(out,'original-refusals.json'));guard()
  status <- 'PASS_ORIGINAL_CELLCHAT_1_1_3_PROJECTDATA_CONTROLS_ONLY'
  writeJson(results,file.path(out,'original-cases.json'))
},warning=function(w){warnings_seen[[length(warnings_seen)+1L]] <<- conditionMessage(w)}),error=function(e){detail <<- conditionMessage(e)})
writeLines(capture.output(sessionInfo()),file.path(out,'session-info.txt'))
writeJson(list(status=status,detail=detail,warnings=warnings_seen,exact_version=as.character(packageVersion('CellChat')),full_communication_workflow=FALSE),file.path(out,'R-RESULT.json'))
quit(status=if(startsWith(status,'PASS_'))0L else 2L)
