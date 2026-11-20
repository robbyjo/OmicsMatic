# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independently reproduce the rejected FIRST coefficient-2 null fit with the
# stock exported API. Never loads the adapter worker or changes original models.
# Arguments: FAILED_CAPTURE PREVIOUS_UNGUARDED_CAPTURE NEW_OUT
args<-commandArgs(TRUE);stopifnot(length(args)==3L,!file.exists(args[3]))
dir.create(args[3],recursive=TRUE);out<-normalizePath(args[3],winslash='/',mustWork=TRUE)
bootstrap<-Sys.getenv('OMICSMATIC_EDGER_REFERENCE_LIBRARIES');stopifnot(nzchar(bootstrap))
.libPaths(c(readLines(bootstrap),.Library),include.site=FALSE);stopifnot(requireNamespace('jsonlite',quietly=TRUE))
bindings<-jsonlite::fromJSON(file.path(args[1],'worker/runtime-bindings.json'))
.libPaths(c(bindings$libraries,.Library),include.site=FALSE);stopifnot(requireNamespace('edgeR',quietly=TRUE))
actual<-readRDS(file.path(args[1],'worker/objects.rds'))
previous<-readRDS(file.path(args[2],'worker/objects.rds'))
fit<-actual$fit;stopifnot(!is.null(fit),ncol(fit$design)==4L)
null<-edgeR::glmFit(fit$counts,design=fit$design[,-2,drop=FALSE],offset=fit$offset,weights=fit$weights,dispersion=fit$dispersion,prior.count=0)
observed<-actual$internal_fits[[length(actual$internal_fits)]]
stopifnot(identical(observed,null),any(null$iter>250L),!any(null$failed),identical(actual$fit,previous$fit))
stock.test<-edgeR::glmLRT(fit,coef=2L)
stopifnot(identical(stock.test,previous$tests[[1L]]),all(is.finite(as.matrix(stock.test$table))))
state<-jsonlite::fromJSON(file.path(args[1],'analysis-status.json'));stopifnot(identical(state$results_admitted,FALSE),grepl('FAIL2',state$status))
saveRDS(list(independent_null=null,independent_stock_test=stock.test,maximum_iterations=max(null$iter),failed_flags=null$failed),file.path(out,'independent-original-exhaustion.rds'),version=3)
writeLines(capture.output(sessionInfo()),file.path(out,'SESSION.txt'))
jsonlite::write_json(list(status='PASS_REAL_ORIGINAL_NULL_FIT_EXHAUSTION_REPRODUCED_AND_REFUSED',edgeR=as.character(packageVersion('edgeR')),
 original_null_identical=TRUE,original_full_fit_identical=TRUE,previous_stock_test_identical=TRUE,original_stock_test_table_finite=TRUE,
 maximum_iterations=max(null$iter),source_iteration_limit=250L,failed_flags_reported=sum(null$failed),
 exhausted_features=as.list(rownames(null)[null$iter>250L]),current_result_admitted=FALSE,
 previous_complete_capture_scope='Superseded for admission by observed internal iteration-exhaustion checks; numeric source comparisons remain exact historical evidence',
 source_worker_loaded=FALSE,stock_namespace_modified=FALSE),file.path(out,'COMPLETED.json'),auto_unbox=TRUE,pretty=TRUE)
cat('PASS genuine source null-fit exhaustion:',as.character(packageVersion('edgeR')),max(null$iter),'iterations with failed=FALSE; rejected\n')
