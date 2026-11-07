# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
a <- commandArgs(TRUE); out <- a[1]; source <- a[2]
.libPaths(c(a[-(1:2)], .Library), include.site=FALSE)
stopifnot(as.character(getRversion()) == "4.6.1")
suppressPackageStartupMessages(library(fitdistrplus))
stopifnot(as.character(packageVersion("fitdistrplus")) == "1.2.6")
original <- new.env(parent=asNamespace("fitdistrplus"))
for (p in list.files(file.path(source,"R"),pattern="[.]R$",full.names=TRUE)) sys.source(p,envir=original,keep.source=FALSE)
guards <- c("fitdist","mledist","mmedist","manageparam","startargdefault")
guard <- function() for (name in guards) {
  expected <- get(name,original,inherits=FALSE); actual <- get(name,asNamespace("fitdistrplus"),inherits=FALSE)
  stopifnot(identical(formals(expected),formals(actual)),identical(body(expected),body(actual)))
}
guard()
encode <- function(x) as.list(sprintf("%.17g",x))
set.seed(20261107)
cases <- list(pair=c(-0.2,0.3),symmetric=c(-1,-0.5,0,0.5,1),skew=c(-.1,-.01,0,.02,.04,.9),
              repeated=c(0,0,0,0,.1,.2),negative=c(-.9,-.8,-.4,-.4,-.3),offset=1e8+c(-2,-1,0,1,2),
              tiny=c(-2,-1,0,1,2)*1e-100,large=c(-2,-1,0,1,2)*1e100,
              near_constant=1+c(-2,-1,0,1,2)*1e-14,
              constant=c(0,0,0),singleton=0,empty=numeric(),NA_input=c(0,NA_real_,1),NaN_input=c(0,NaN,1),Inf_input=c(0,Inf,1))
for (n in c(3L,4L,5L,10L,30L,100L,1000L)) for (i in 1:4) cases[[paste0("normal-",n,"-",i)]] <- rnorm(n,0.02*i,0.1*i)
reports <- list(); fits <- list()
for (name in names(cases)) {
  x <- cases[[name]]; methods <- list()
  for (method in c("mle","mme")) {
    warnings <- character(); error <- NULL
    fit <- withCallingHandlers(tryCatch(fitdistrplus::fitdist(x,"norm",method=method),error=function(e){error<<-conditionMessage(e);NULL}),warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart("muffleWarning")})
    fits[[paste(name,method,sep="/")]] <- list(data=x,fit=fit,warnings=warnings,error=error)
    methods[[method]] <- if(is.null(fit)) list(returned=FALSE,error=error,warnings=as.list(warnings)) else list(returned=TRUE,estimate=as.list(fit$estimate),loglik=sprintf("%.17g",fit$loglik),aic=sprintf("%.17g",fit$aic),bic=sprintf("%.17g",fit$bic),convergence=fit$convergence,warnings=as.list(warnings))
  }
  finite <- length(x)>1 && all(is.finite(x)); mu <- if(finite)mean(x) else NA_real_; variance <- if(finite)sum((x-mu)^2)/length(x) else NA_real_
  sd <- sqrt(variance); analytical <- finite && is.finite(mu) && is.finite(variance) && variance>0 && is.finite(sd) && sd>0
  analytic <- if(analytical) list(mean=sprintf("%.17g",mu),sd=sprintf("%.17g",sd),variance=sprintf("%.17g",variance),loglik=sprintf("%.17g",sum(dnorm(x,mu,sd,log=TRUE)))) else NULL
  reports[[name]] <- list(id=name,data=encode(x),native_positive_finite_variance_domain=analytical,analytical=analytic,original_methods=methods)
}
probabilities <- list(); q <- c(-1e150,-40,-10,-8,-3,-1,0,1,3,8,10,40,1e150)
for (mu in c(-0.02,0,0.5)) for (sigma in c(1e-10,0.3,1,100)) {
  values <- mu+sigma*q
  for(tail in c("upper","lower","two-sided")) {
    logp <- if(tail=="upper")pnorm(values,mu,sigma,lower.tail=FALSE,log.p=TRUE) else if(tail=="lower")pnorm(values,mu,sigma,lower.tail=TRUE,log.p=TRUE) else pmin(0,log(2)+pnorm(-abs((values-mu)/sigma),log.p=TRUE))
    order <- order(logp); sorted <- pmin(0,log(length(logp))-log(seq_along(logp))+logp[order]); adjusted <- rep(NA_real_,length(logp));adjusted[order]<-rev(cummin(rev(sorted)))
    probabilities[[length(probabilities)+1L]] <- list(mean=sprintf("%.17g",mu),sd=sprintf("%.17g",sigma),tail=tail,values=encode(values),logp=encode(logp),p=encode(exp(logp)),logq=encode(adjusted),q=encode(p.adjust(exp(logp),method="BH")))
  }
}
guard()
saveRDS(fits,file.path(out,"original-fits.rds"));saveRDS(list(cases=cases,probabilities=probabilities),file.path(out,"inputs-and-probabilities.rds"))
versions <- as.list(vapply(c("fitdistrplus","MASS","survival","rlang","jsonlite","stats"),function(p)as.character(packageVersion(p)),character(1)))
profile <- list(R_version=as.character(getRversion()),platform=R.version$platform,versions=versions,guarded_functions=as.list(guards),source_bodies_and_formals_checked_before_and_after=TRUE,original_runtime_installed=FALSE)
jsonlite::write_json(list(profile=profile,fits=reports,probabilities=probabilities),file.path(out,"controls.json"),auto_unbox=TRUE,pretty=TRUE,digits=NA,null="null",na="string")
cat("Original fitdistrplus normal controls captured:",length(cases)*2L,"fits and",length(probabilities)*length(q),"tail/BH values\n")
