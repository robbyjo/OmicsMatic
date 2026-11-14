# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Re-render the complete original graph RDS in a fresh R process, without fitting.
args <- commandArgs(TRUE);stopifnot(length(args)==4L)
library <- args[[1L]];graph.path <- args[[2L]];render.path <- args[[3L]];out <- args[[4L]]
.libPaths(c(library,.libPaths()));loadNamespace('velociraptor',lib.loc=library)
for (name in c('ggplot2','patchwork','metR'))loadNamespace(name)
graph <- readRDS(graph.path);render <- jsonlite::fromJSON(render.path,simplifyVector=FALSE)
stopifnot(inherits(graph,c('ggplot','patchwork','ggplot2::ggplot')))
conditions <- list();failure <- NULL
tryCatch(withCallingHandlers({
 set.seed(as.integer(render$seed))
 grDevices::svg(file.path(out,'figure.svg'),width=render$width_inches,height=render$height_inches,bg=render$background)
 tryCatch(print(graph),finally=grDevices::dev.off())
 set.seed(as.integer(render$seed))
 grDevices::png(file.path(out,'figure.png'),width=render$width_inches,height=render$height_inches,units='in',res=render$png_dpi,type='cairo',bg=render$background)
 tryCatch(print(graph),finally=grDevices::dev.off())
},warning=function(w)conditions[[length(conditions)+1L]] <<- list(kind='warning',object=w,calls=sys.calls()),message=function(m)conditions[[length(conditions)+1L]] <<- list(kind='message',object=m,calls=sys.calls())),error=function(e){failure <<- e;conditions[[length(conditions)+1L]] <<- list(kind='error',object=e,calls=sys.calls())})
saveRDS(list(conditions=conditions,error=failure,graph.class=class(graph),session=sessionInfo()),file.path(out,'COMPLETE-GRAPH-REPLAY-DIAGNOSTICS.rds'),version=3)
jsonlite::write_json(list(status=if(is.null(failure))'COMPLETE_ORIGINAL_GRAPH_RDS_REPLAY'else'FAIL2_ORIGINAL_GRAPH_RDS_REPLAY',graph.class=class(graph),python.initialized=reticulate::py_available(initialize=FALSE),raw.console.diagnostic.counts.complete=FALSE,graph.environment.pointer.or.opaque.RDS.byte.parity.claimed=FALSE),file.path(out,'REPLAY-STATUS.json'),pretty=TRUE,auto_unbox=TRUE)
if (!is.null(failure)) {print(failure);quit(save='no',status=2L,runLast=FALSE)}
