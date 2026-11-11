# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-2.0-or-later
# Read preserved original GLM objects; do not refit or replace their estimates.
a<-commandArgs(TRUE);out<-a[1];controls<-a[2];version<-a[3]
.libPaths(c(a[-(1:3)],.Library),include.site=FALSE)
suppressPackageStartupMessages({library(Matrix);library(SoupX)})
stopifnot(as.character(getRversion())=="4.6.1",packageDescription("SoupX")$Version==version)
rows<-list()
for(id in c("manual-automatic-mask-cell-only","manual-automatic-mask-supplied-clusters","manual-supplied-mask")){
 value<-readRDS(file.path(controls,id,"original-return.rds"));fit<-value$channel$fit
 stopifnot(is(fit,"glm"),fit$family$family=="poisson",fit$family$link=="log",length(coef(fit))==1L,all(is.finite(fit$offset)),all(is.finite(fit$y)))
 observed<-sum(fit$y);exposure<-sum(exp(fit$offset));analytical<-observed/exposure;original<-exp(coef(fit))[[1]]
 rows[[id]]<-list(observed=sprintf("%.17g",observed),exposure=sprintf("%.17g",exposure),analytical_rho=sprintf("%.17g",analytical),original_rho=sprintf("%.17g",original),original_minus_analytical=sprintf("%.17g",original-analytical),original_score=sprintf("%.17g",observed-original*exposure),original_iter=fit$iter,original_converged=fit$converged,original_glm_control=fit$control,complete_y=as.list(sprintf("%.17g",fit$y)),complete_log_exposure=as.list(sprintf("%.17g",fit$offset)),original_object_not_modified=TRUE)
}
jsonlite::write_json(list(version=version,R_version=as.character(getRversion()),status="diagnosed-preserved-original-global-Poisson-objects-without-refitting",cases=rows),file.path(out,"diagnosis.json"),auto_unbox=TRUE,pretty=TRUE,digits=NA)
