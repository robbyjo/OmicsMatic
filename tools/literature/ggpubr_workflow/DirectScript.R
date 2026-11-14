# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent unchanged-original execution: capture conditions without muffling.
args <- commandArgs(TRUE);stopifnot(length(args)==5L,!dir.exists(args[4]))
provider <- jsonlite::fromJSON(file.path(args[1],'READY.json'));.libPaths(c(provider$library,provider$base_library))
suppressPackageStartupMessages(library(ggpubr,lib.loc=provider$library))
stopifnot(as.character(getRversion())=='4.3.3',as.character(packageVersion('ggpubr'))==provider$version)
dir.create(args[4],recursive=TRUE);conditions <- list();scope <- new.env(parent=globalenv())
scope$input_directory <- normalizePath(args[2],mustWork=TRUE);scope$result_directory <- normalizePath(args[4],mustWork=TRUE)
withCallingHandlers(sys.source(args[3],envir=scope,keep.source=TRUE),
 warning=function(w)conditions[[length(conditions)+1L]] <<- list(object=w,kind='warning',class=class(w),message=conditionMessage(w),call=conditionCall(w)),
 message=function(m)conditions[[length(conditions)+1L]] <<- list(object=m,kind='message',class=class(m),message=conditionMessage(m),call=conditionCall(m)))
stopifnot(identical(scope$analysis_complete,args[5]))
saveRDS(conditions,file.path(args[4],'direct-complete-conditions.rds'),version=3)
jsonlite::write_json(list(status='ORIGINAL_R_SCRIPT_RETURNED_WITH_EXACT_MARKER_NOT_INDEPENDENT_SCIENTIFIC_ADMISSION',
 version=provider$version,warning_count=sum(vapply(conditions,function(c)c$kind=='warning',logical(1))),
 messages=lapply(conditions,function(c)c[c('kind','class','message')])),file.path(args[4],'direct-status.json'),pretty=TRUE,auto_unbox=TRUE)
writeLines(capture.output(sessionInfo()),file.path(args[4],'direct-session-info.txt'))
cat('ORIGINAL_R_SCRIPT_RETURNED',provider$version,'\n')
