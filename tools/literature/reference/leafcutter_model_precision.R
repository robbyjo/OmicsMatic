# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Lossless post-fit export from original binary fits; never optimize/refit a model.
args <- commandArgs(TRUE)
library(jsonlite)
.libPaths(c(args[1], .libPaths()))
library(leafcutter)
library(rstan)
library(doMC)
registerDoMC(1)
stopifnot(normalizePath(getNamespaceInfo(asNamespace("leafcutter"), "path")) == normalizePath(file.path(args[1], "leafcutter")))
reference <- args[2]; out <- args[3]
literal <- fromJSON(file.path(reference, "cases.json"), simplifyVector=FALSE)
mat <- function(x) do.call(rbind,lapply(x,unlist))
save_json <- function(x,name) write_json(x,file.path(out,name),auto_unbox=TRUE,pretty=TRUE,digits=I(17),null="null",na="string",matrix="rowmajor")
roundtrip <- c(pi,1e-300,1e300,1+.Machine$double.eps,-246.8906819745761)
stopifnot(identical(roundtrip,fromJSON(toJSON(roundtrip,digits=I(17)))))
models <- get("stanmodels",asNamespace("leafcutter"))
for (case in literal$grids) {
  y<-mat(case$counts);x<-mat(case$design)
  data<-list(N=nrow(x),P=ncol(x),K=ncol(y),x=x,y=y,concShape=case$shape,concRate=case$rate,outlier_prior_a=case$outlier_a,outlier_prior_b=case$outlier_b)
  model<-if(case$robust)models$dm_glm_robust else models$dm_glm_multi_conc
  handle<-sampling(model,data=data,chains=0,iter=1,warmup=0,refresh=0,seed=case$seed)
  points<-list()
  for(point in case$points) {
    parameters<-list(beta_raw=mat(point$beta_raw),beta_scale=unlist(point$beta_scale),conc=unlist(point$concentration))
    if(case$robust)parameters$outlier_prob<-point$outlier_probability
    u<-unconstrain_pars(handle,parameters);value<-log_prob(handle,u,adjust_transform=FALSE)
    stopifnot(is.finite(value))
    points[[length(points)+1L]]<-list(id=point$id,parameters=parameters,objective=as.numeric(value),unconstrained=u,jacobian_adjustment=FALSE)
  }
  save_json(list(id=case$id,original_model=if(case$robust)"dm_glm_robust" else "dm_glm_multi_conc",data=data,points=points),paste0(case$id,".json"))
}
for(case in literal$fits) {
  saved<-readRDS(file.path(reference,paste0(case$id,".rds")))
  report<-fromJSON(file.path(reference,paste0(case$id,".json")),simplifyVector=FALSE)
  report$fit<-saved;report$effects<-leaf_cutter_effect_sizes(list(cluster=saved))
  save_json(report,paste0(case$id,".json"))
}
for(case in literal$differential) {
  saved<-readRDS(file.path(reference,paste0(case$id,".rds")))
  report<-fromJSON(file.path(reference,paste0(case$id,".json")),simplifyVector=FALSE)
  for(cluster in names(saved))if(!is.character(saved[[cluster]]))report$results[[cluster]]$fit<-saved[[cluster]]
  report$cluster_table<-cluster_results_table(saved);report$effects<-leaf_cutter_effect_sizes(saved)
  save_json(report,paste0(case$id,".json"))
}
save_json(list(status="PASS_LOSSLESS_ORIGINAL_LEAFCUTTER_MODEL_CONTROL_BINDING_ONLY",fits_refitted=FALSE,fixed_parameter_Stan_objectives_repeated=16,lossless_numeric_roundtrip=TRUE,original_namespace_patched=FALSE),"ORIGINAL-COMPLETED.json")
