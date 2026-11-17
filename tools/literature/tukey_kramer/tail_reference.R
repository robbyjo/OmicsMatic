# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent positive-integral checks using R's QUADPACK integration, pnorm
# and pchisq. Original ptukey values are retained beside the independent result.
args <- commandArgs(trailingOnly=TRUE)
if (length(args)!=1L || file.exists(args[[1L]])) stop("Require NEW output")
dir.create(args[[1L]], recursive=TRUE)
out <- normalizePath(args[[1L]], winslash="/")
options(digits=17, OutDec=".")
cases <- data.frame(groups=c(2,2,3,3,4,5,8,12,2,3), df=c(4,1022,4,12,4,30,1000,40,2,2),
                    q=c(sqrt(3)*1e6,2500,1e6,30,1e6,12,15,15,1e155,1e155))
cache <- new.env(parent=emptyenv())
log_density <- function(r,k) {
    if (r<=0) return(-Inf)
    if (k==2) return(-.5*log(pi)-r*r/4)
    if (k==3) return(log(3)-.5*log(pi)-r*r/4+log(2*pnorm(r/sqrt(6))-1))
    key <- paste(k, sprintf("%.17g",r))
    if (exists(key,envir=cache,inherits=FALSE)) return(get(key,envir=cache))
    peak <- 2*pnorm(r/2)-1
    inner <- function(u) {
        low <- u-r/2; high <- u+r/2
        interval <- ifelse(low>=0, pnorm(low,lower.tail=FALSE)-pnorm(high,lower.tail=FALSE),pnorm(high)-pnorm(low))
        exp(-u*u+(k-2)*(log(interval)-log(peak)))
    }
    value <- integrate(inner,0,10,rel.tol=2e-11,abs.tol=0,subdivisions=1000L,stop.on.error=TRUE)$value
    result <- log(k*(k-1)/(2*pi))-r*r/4+log(2)+(k-2)*log(peak)+log(value)
    assign(key,result,envir=cache);result
}
rows <- c("groups\tdf\tq\tpositive_tail\tlegacy_ptukey\trelative_quadrature_error")
for (i in seq_len(nrow(cases))) {
    k <- cases$groups[i]; df <- cases$df[i]; q <- cases$q[i]
    if (k==2) {
        value <- 2*pt(q/sqrt(2),df,lower.tail=FALSE); error <- 0
    } else {
        integrand <- function(r) vapply(r,function(x) exp(log_density(x,k)+pchisq(df*(x/q)^2,df,log.p=TRUE)),numeric(1))
        fit <- integrate(integrand,0,60,rel.tol=2e-10,abs.tol=0,subdivisions=1000L,stop.on.error=TRUE)
        value <- fit$value;error <- fit$abs.error/value
        stopifnot(is.finite(value),value>0,error<2e-8)
        if (k==3 && q==1e6) {
            asymptote <- 2*(12+27*sqrt(3)/pi)/q^4
            stopifnot(abs(value/asymptote-1)<1e-8)
        }
        if (k==3 && q==1e155) {
            # Form the quotient without overflowing q^2.
            asymptote <- ((2+3*sqrt(3)/pi)/q)/q
            stopifnot(abs(value/asymptote-1)<1e-7)
        }
    }
    row <- c(k,df,q,value,ptukey(q,k,df,lower.tail=FALSE),error)
    rows <- c(rows,paste(sprintf("%.17g",row),collapse="\t"))
}
writeLines(rows,file.path(out,"expected.tsv"),useBytes=TRUE)
capture.output(sessionInfo(),file=file.path(out,"SESSION.txt"))
writeLines("PASS_INDEPENDENT_POSITIVE_TAIL_INTEGRALS_AND_EXACT_TWO_GROUP_IDENTITY",file.path(out,"COMPLETED.txt"))
