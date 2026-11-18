# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Fixed worker: original multcomp inference, never user-supplied R expressions.
options(warn=2, digits=17)
startup <- character()
finish <- function(code, message) {
    writeLines(message, "original-error.txt", useBytes=TRUE)
    available <- c("d","model","hypothesis","result","critical","intervals","coverage","initial.rng",".Random.seed")
    available <- available[vapply(available,exists,envir=.GlobalEnv,inherits=FALSE,FUN.VALUE=logical(1L))]
    tryCatch(saveRDS(list(error=message,partial_objects=mget(available,envir=.GlobalEnv),session=sessionInfo()),
        "partial-original-fit.rds",version=3),error=function(recording) message("Partial original R fit recording failed: ",conditionMessage(recording)))
    writeLines(if(code==3L) "SKIP3_DUNNETT_RUNTIME_UNAVAILABLE" else "FAIL2_DUNNETT_ORIGINAL_INFERENCE", "worker-status.txt")
    quit(save="no", status=code)
}
tryCatch({
    paths <- readLines("runtime-paths.txt", encoding="UTF-8", warn=FALSE)
    if(length(paths)!=2L) stop("Incomplete declared runtime paths")
    .libPaths(c(paths[2L], .Library), include.site=FALSE)
    norm <- function(x) normalizePath(x, winslash="/", mustWork=TRUE)
    if(norm(R.home())!=norm(paths[1L])) finish(3L,"R_HOME differs from the declared runtime")
    required <- c("multcomp","mvtnorm","sandwich","TH.data","zoo","survival","MASS","codetools")
    if(any(vapply(required, function(p) length(find.package(p,quiet=TRUE))==0L, FUN.VALUE=logical(1L))))
        finish(3L,"A required original R package cannot be loaded; inspect stderr and install permitted dependencies")
    # No personal/site libraries participate. All code resides in the two captured provider trees.
    if(packageDescription("multcomp")$Version!="1.4-29")
        finish(3L,"This profile requires the original multcomp 1.4-29 source release")
    settings <- readLines("settings.txt", warn=FALSE)
    if(length(settings)!=7L) stop("Incomplete fixed-worker settings")
    control <- as.integer(settings[1L]); confidence <- as.double(settings[2L])
    seed <- as.integer(settings[3L]); maxpts <- as.integer(settings[4L])
    tolerance <- as.double(settings[5L]); ptol <- as.double(settings[6L]); policy <- settings[7L]
    withCallingHandlers(suppressPackageStartupMessages(library(multcomp)), warning=function(w) {
        message <- conditionMessage(w)
        startup <<- c(startup,message)
        writeLines(startup,"STARTUP-WARNINGS.txt",useBytes=TRUE)
        if(policy=="RECORD_BUILD_WARNINGS" && grepl("^package .* was built under R version [0-9.]+$",message)) {
            message("RECORDED R BUILD WARNING: ",message)
            invokeRestart("muffleWarning")
        }
    })
    writeLines(startup,"STARTUP-WARNINGS.txt",useBytes=TRUE)
    d <- read.delim("prepared-observations.tsv", header=TRUE, sep="\t", quote="", comment.char="", check.names=FALSE)
    if(!identical(names(d),c("group","value")) || anyNA(d) || any(!is.finite(d$value))) stop("Incomplete original model input")
    levels <- unique(d$group); levels <- c(control,levels[levels!=control])
    d$group <- factor(paste0("g",d$group), levels=paste0("g",levels))
    set.seed(seed); initial.rng <- .Random.seed
    model <- lm(value~group,data=d,na.action=na.fail)
    hypothesis <- glht(model,linfct=mcp(group="Dunnett"),alternative="two.sided")
    algorithm <- mvtnorm::GenzBretz(maxpts=maxpts,abseps=tolerance,releps=0)
    result <- summary(hypothesis,test=adjusted("single-step",algorithm=algorithm))
    p.error <- attr(result$test$pvalues,"error")
    if(length(p.error)!=1L || !is.finite(p.error) || p.error>tolerance) stop("Original adjusted-p integration did not meet the declared tolerance")
    quantile.function <- adjusted_calpha(algorithm=algorithm,ptol=ptol,maxiter=500L)
    critical <- quantile.function(hypothesis,confidence)
    intervals <- confint(hypothesis,level=confidence,calpha=critical)
    correlation <- cov2cor(vcov(hypothesis)); k <- nrow(correlation)
    coverage <- mvtnorm::pmvt(lower=rep(-as.double(critical),k),upper=rep(as.double(critical),k),
        df=hypothesis$df,corr=correlation,algorithm=algorithm)
    if(!(attr(coverage,"msg")=="Normal Completion" || grepl("^univariate",attr(coverage,"msg")))) stop("Independent original interval coverage integration failed")
    coverage.error <- attr(coverage,"error")
    if(length(coverage.error)!=1L || !is.finite(coverage.error) || coverage.error>tolerance ||
       abs(as.double(coverage)-confidence)>ptol+2*tolerance) stop("Original interval coverage check failed its declared probability tolerance")
    treatments <- levels[-1L]
    rows <- data.frame(treatment=treatments,estimate=as.double(coef(hypothesis)),
        se=sqrt(diag(vcov(hypothesis))),t=as.double(result$test$tstat),
        adjusted_p=as.double(result$test$pvalues),lower=intervals$confint[,"lwr"],upper=intervals$confint[,"upr"])
    write.table(rows,"original-comparisons.tsv",sep="\t",row.names=FALSE,quote=FALSE)
    write.table(data.frame(index=levels,n=as.integer(table(d$group)),mean=as.double(tapply(d$value,d$group,mean))),
        "original-groups.tsv",sep="\t",row.names=FALSE,quote=FALSE)
    write.table(unname(correlation),"original-correlation.tsv",sep="\t",row.names=FALSE,col.names=FALSE,quote=FALSE)
    metrics <- c(df=df.residual(model),residual_sd=sigma(model),critical=as.double(critical),p_error=p.error,
        coverage=as.double(coverage),coverage_error=coverage.error,startup_warnings=length(startup))
    write.table(data.frame(metric=names(metrics),value=as.double(metrics)),"original-diagnostics.tsv",sep="\t",row.names=FALSE,quote=FALSE)
    runtime <- lapply(required,function(name) list(package=name,version=packageDescription(name)$Version,path=find.package(name)))
    names(runtime) <- required
    saveRDS(list(data=d,model=model,hypothesis=hypothesis,summary=result,intervals=intervals,
        critical=critical,coverage=coverage,initial_rng=initial.rng,final_rng=.Random.seed,
        session=sessionInfo(),packages=runtime,startup_warnings=startup),"original-fit.rds",version=3)
    capture.output(sessionInfo(),file="SESSION.txt")
    writeLines("PASS_ORIGINAL_DUNNETT_REQUIRES_JAVA_AUDIT","worker-status.txt")
}, error=function(e) finish(2L,conditionMessage(e)))
