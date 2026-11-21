fitFDist <- function(x,df1,covariate=NULL)
#	Moment estimation of the parameters of a scaled F-distribution.
#	The numerator degrees of freedom is given, the scale factor and denominator df is to be estimated.
#	Gordon Smyth and Belinda Phipson
#	Created 8 Sept 2002.  Last revised 22 Jul 2024.
{
#	Check x
	n <- length(x)
	if(n == 0L) return(list(scale=NA,df2=NA))
	if(n == 1L) return(list(scale=x,df2=0))

#	Check df1
	ok <- is.finite(df1) & df1 > 1e-15
	if(length(df1)==1L) {
		if(!ok) {
			return(list(scale=NA,df2=NA))
		} else {
			ok <- rep_len(TRUE,n)
		}
	} else {
		if(length(df1) != n) stop("x and df1 have different lengths")
	}

#	Check covariate
	if(is.null(covariate)) {
		splinedf <- 1L
	} else {
		if(length(covariate) != n) stop("x and covariate must be of same length")
		if(anyNA(covariate)) stop("NA covariate values not allowed")
		isfin <- is.finite(covariate)
		if(!all(isfin)) {
			if(any(isfin)) {
				r <- range(covariate[isfin])
				covariate[covariate == -Inf] <- r[1]-1
				covariate[covariate == Inf] <- r[2]+1
			} else {
				covariate <- sign(covariate)
			}
		}
	}

#	Remove missing or infinite or negative values and zero degrees of freedom
	ok <- ok & is.finite(x) & (x > -1e-15)
	nok <- sum(ok)
	if(nok==1L) return(list(scale=x[ok],df2=0))
	notallok <- (nok < n)
	if(notallok) {
		x <- x[ok]
		if(length(df1)>1L) df1 <- df1[ok]
		if(!is.null(covariate)) {
			covariate.notok <- covariate[!ok]
			covariate <- covariate[ok]
		}
	}

#	Set df for spline trend
	if(!is.null(covariate)) {
#		splinedf <- min(4L,nok-1L,length(unique(covariate)))
		splinedf <- 1L + (nok >= 3L) + (nok >= 6L) + (nok >= 30L)
		splinedf <- min(splinedf, length(unique(covariate)))
#		If covariate takes only one unique value or insufficient
#		observations, recall with NULL covariate
		if(splinedf < 2L) {
			out <- Recall(x=x,df1=df1)
			out$scale <- rep_len(out$scale,n)
			return(out)
		}
	}

#	Avoid exactly zero values
	x <- pmax(x,0)
	m <- median(x)
	if(m==0) {
		warning("More than half of residual variances are exactly zero: eBayes unreliable")
		m <- 1
	} else {
		if(any(x==0)) warning("Zero sample variances detected, have been offset away from zero",call.=FALSE)
	}
	x <- pmax(x, 1e-5 * m)

#	Better to work on with log(F)
	z <- log(x)
	e <- z+logmdigamma(df1/2)

	if(is.null(covariate)) {
		emean <- mean(e)
		evar <- sum((e-emean)^2)/(nok-1L)
	} else {
		if(!requireNamespace("splines",quietly=TRUE)) stop("splines package required but is not available")
		design <- try(splines::ns(covariate,df=splinedf,intercept=TRUE),silent=TRUE)
		if(is(design,"try-error")) stop("Problem with covariate")
		fit <- lm.fit(design,e)
		if(notallok) {
			design2 <- predict(design,newx=covariate.notok)
			emean <- rep_len(0,n)
			emean[ok] <- fit$fitted.values
			emean[!ok] <- design2 %*% fit$coefficients
		} else {
			emean <- fit$fitted.values
		}
		evar <- mean(fit$effects[-(1:fit$rank)]^2)
	}

#	Estimate scale and df2
	evar <- evar - mean(trigamma(df1/2))
	if(evar > 0) {
		df2 <- 2*trigammaInverse(evar)
		s20 <- exp(emean-logmdigamma(df2/2))
	} else {
		df2 <- Inf
		if(is.null(covariate))
#			Use simple pooled variance, which is MLE of the scale in this case.
#			Versions of limma before Jan 2017 returned the limiting
#			value of the evar>0 estimate, which is larger.
			s20 <- mean(x)
		else
			s20 <- exp(emean)
	}

	list(scale=s20,df2=df2)
}


trigammaInverse <- function(x) {
#	Solve trigamma(y) = x for y
#	Gordon Smyth
#	8 Sept 2002.  Last revised 12 March 2004.

#	Non-numeric or zero length input
	if(!is.numeric(x)) stop("Non-numeric argument to mathematical function")
	if(length(x)==0) return(numeric(0))

#	Treat out-of-range values as special cases
	omit <- is.na(x)
	if(any(omit)) {
		y <- x
		if(any(!omit)) y[!omit] <- Recall(x[!omit])
		return(y)
	}
	omit <- (x < 0)
	if(any(omit)) {
		y <- x
		y[omit] <- NaN
		warning("NaNs produced")
		if(any(!omit)) y[!omit] <- Recall(x[!omit])
		return(y)
	}
	omit <- (x > 1e7)
	if(any(omit)) {
		y <- x
		y[omit] <- 1/sqrt(x[omit])
		if(any(!omit)) y[!omit] <- Recall(x[!omit])
		return(y)
	}
	omit <- (x < 1e-6)
	if(any(omit)) {
		y <- x
		y[omit] <- 1/x[omit]
		if(any(!omit)) y[!omit] <- Recall(x[!omit])
		return(y)
	}

#	Newton's method
#	1/trigamma(y) is convex, nearly linear and strictly > y-0.5,
#	so iteration to solve 1/x = 1/trigamma is monotonically convergent
	y <- 0.5+1/x
	iter <- 0
	repeat {
		iter <- iter+1
		tri <- trigamma(y)
		dif <- tri*(1-tri/x)/psigamma(y,deriv=2)
		y <- y+dif
		if(max(-dif/y) < 1e-8) break
		if(iter > 50) {
			warning("Iteration limit exceeded")
			break
		}
	}
	y
}
