# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# No downloads, arbitrary R expressions, user profiles or implicit package editions.
args<-commandArgs(trailingOnly=TRUE)
if(length(args)!=2L||args[[1]]!="--request-file")stop("Require typed UTF-8 worker request file")
args<-readLines(args[[2]],encoding="UTF-8",warn=FALSE)
if(length(args)!=13L)stop("Invalid typed Signac LSI protocol")
input<-args[[1]];feature_file<-args[[2]];cell_file<-args[[3]];out<-args[[4]]
if(!dir.exists(out))stop("Java-owned worker output directory is missing")
status<-"failed";detail<-"";action<-"Inspect worker logs and retained inputs. Correct the named cause and rerun into a NEW directory. No successful analysis is published after a failure."
warnings<-character()
clean<-function(x)gsub("[\t\r\n]"," ",x)
write_status<-function(){writeLines(c("status\tdetail\trequired_action",paste(clean(status),clean(detail),clean(action),sep="\t")),file.path(out,"worker-status.tsv"))}
result<-tryCatch(withCallingHandlers({
  lib<-normalizePath(args[[5]],winslash="/",mustWork=FALSE);base<-normalizePath(args[[6]],winslash="/",mustWork=FALSE)
  if(!dir.exists(lib)||!dir.exists(base)) {
    status<-"skipped-dependency-unavailable";action<-"Skip dependent LSI. Supply both existing private reference and read-only base libraries, with exact required packages and working DLLs; use permitted setup in NEW paths or leave analysis skipped."
    stop("Explicit private reference/base library directory is unavailable")
  }
  .libPaths(c(lib,base,.Library),include.site=FALSE)
  expected<-c(Signac="1.17.1",Matrix="1.7.5",SeuratObject="5.4.0",RSpectra="0.16.2",irlba="2.4.1")
  for(package in names(expected)) {
    if(!requireNamespace(package,quietly=TRUE)||as.character(packageVersion(package))!=expected[[package]]) {
      status<-"skipped-dependency-unavailable"
      action<-paste("Skip dependent LSI. In a permitted environment supply exact",paste(names(expected),expected,collapse=", "),"with working DLLs in private libraries. Use tools/literature/setup_signac_reference.py --out NEW_PATH --allow-install where installation is permitted, preserve prior libraries, and validate the returned runtime. Otherwise keep this analysis skipped.")
      stop(paste("Required reference package unavailable or wrong edition:",package,expected[[package]]))
    }
  }
  profile<-args[[7]];version<-sub("-source-irlba$","",profile)
  if(!profile %in% c("1.17.1-package-rspectra",paste0(c("1.6.0","1.7.0","1.9.0","1.14.0"),"-source-irlba")))stop("Unknown explicit Signac LSI profile")
  n<-as.integer(args[[8]]);scale<-identical(args[[9]],"true");clip<-if(args[[10]]=="none")NULL else as.numeric(args[[10]])
  tol<-as.numeric(args[[11]]);seed<-as.integer(args[[12]]);work<-as.integer(args[[13]])
  x<-as(Matrix::readMM(input),"CsparseMatrix")
  features<-readLines(feature_file,encoding="UTF-8",warn=FALSE);cells<-readLines(cell_file,encoding="UTF-8",warn=FALSE)
  stopifnot(nrow(x)==length(features),ncol(x)==length(cells),!anyDuplicated(features),!anyDuplicated(cells),all(is.finite(x@x)))
  dimnames(x)<-list(features,cells)
  RNGkind("Mersenne-Twister","Inversion","Rejection");set.seed(seed)
  if(profile=="1.17.1-package-rspectra") {
    source<-file.path("source","signac-lsi","RunSVD-1.17.1.R")
    env<-new.env(parent=asNamespace("Signac"));sys.source(source,env,keep.source=FALSE)
    stopifnot(identical(body(env$RunSVD.default),body(get("RunSVD.default",asNamespace("Signac")))))
    fit<-Signac::RunSVD(x,assay="peaks",n=n,scale.embeddings=scale,scale.max=clip,tol=tol,pca=FALSE,verbose=FALSE)
  } else {
    source<-file.path("source","signac-lsi",paste0("RunSVD-",version,".R"))
    env<-new.env(parent=asNamespace("Signac"));env$irlba<-irlba::irlba;sys.source(source,env,keep.source=FALSE)
    fit<-env$RunSVD.default(x,assay="peaks",n=n,scale.embeddings=scale,scale.max=clip,tol=tol,irlba.work=work,verbose=FALSE)
    warnings<-c(warnings,"HISTORICAL_FUNCTION_SOURCE_ONLY: unmodified archived Signac function uses current pinned dependencies; this is not a historical whole-package installation or complete paper reproduction.")
  }
  write_matrix<-function(x,path){
    # Text connections under Windows LC_CTYPE=C can escape intact UTF-8 IDs as
    # <U+XXXX>. Write explicit UTF-8 bytes instead; never repair escaped labels.
    con<-file(path,open="wb");on.exit(close(con))
    emit<-function(value){writeBin(charToRaw(enc2utf8(value)),con);writeBin(as.raw(10L),con)}
    emit(paste(c("id",colnames(x)),collapse="\t"))
    for(i in seq_len(nrow(x)))emit(paste(c(rownames(x)[[i]],sprintf("%.17g",x[i,])),collapse="\t"))
  }
  embeddings<-SeuratObject::Embeddings(fit);loadings<-SeuratObject::Loadings(fit)
  u<-fit@misc$u;v<-fit@misc$v;d<-fit@misc$d
  dimnames(u)<-dimnames(embeddings);dimnames(v)<-dimnames(loadings)
  stopifnot(ncol(u)==ncol(v),length(d)==ncol(u))
  write_matrix(u,file.path(out,"raw-u.tsv"));write_matrix(v,file.path(out,"loadings.tsv"));write_matrix(embeddings,file.path(out,"embeddings.tsv"))
  writeLines(c("component\tsingular_value\tstdev",vapply(seq_along(d),function(i)paste(i,sprintf("%.17g",d[[i]]),sprintf("%.17g",fit@stdev[[i]]),sep="\t"),character(1))),file.path(out,"singular-values.tsv"))
  saveRDS(fit,file.path(out,"fit.rds"),version=3)
  settings<-c(profile=profile,full_package=as.character(packageVersion("Signac")),solver=if(profile=="1.17.1-package-rspectra")"RSpectra::svds" else "irlba::irlba",requested_components=n,
              effective_components=ncol(u),scale_embeddings=scale,clip=if(is.null(clip))"none" else clip,tolerance_argument=tol,seed=seed,irlba_work_argument=work,
              historical_packages_installed="false",standard_deviation_denominator_features=max(1,nrow(x)-1))
  # 1.6/1.7 source accepts tol but does not forward it; report actual source behavior.
  settings<-c(settings,tolerance_forwarded=if(version %in% c("1.6.0","1.7.0"))"false (irlba default)" else "true")
  writeLines(c("setting\tvalue",paste(names(settings),settings,sep="\t")),file.path(out,"effective-settings.tsv"))
  ip<-installed.packages(lib.loc=.libPaths());write.table(ip[,c("Package","Version","LibPath","Built"),drop=FALSE],file.path(out,"installed-packages.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
  writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"))
  status<-if(length(warnings))"completed-with-warnings" else "completed";detail<-"Original SVD completed; Java must still validate sparse residuals, identities, finite output and postprocessing."
  TRUE
},warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")}),error=function(e){detail<<-conditionMessage(e);FALSE})
writeLines(c("warning",clean(warnings)),file.path(out,"worker-warnings.tsv"));write_status()
cat("SIGNAC_WORKER_STATUS",status,"\n",detail,"\n")
quit(status=if(result)0L else if(status=="skipped-dependency-unavailable")3L else 2L)
