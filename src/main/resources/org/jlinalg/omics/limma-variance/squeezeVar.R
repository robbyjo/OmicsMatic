#	EMPIRICAL BAYES SQUEEZING OF VARIANCES

squeezeVar <- function(var, df, covariate=NULL, span=NULL, robust=FALSE, winsor.tail.p=c(0.05,0.1), legacy=NULL)
#	Empirical Bayes posterior variances
#	Gordon Smyth
#	Created 2 March 2004.  Last modified 12 July 2025.
{
	n <- length(var)

#	No observations
	if(identical(n,0L)) stop("var is empty")

#	The code will run for n=2 but there is no theoretical advantage from empirical Bayes with less than 3 observations
	if(n < 3L) return(list(var.post=var,var.prior=var,df.prior=0))

#	When df==0, guard against missing or infinite values in var
	if(length(df)>1L) var[df==0] <- 0

#	span is only implemented for new hyperparameter function
    if(!is.null(span)) legacy <- FALSE

#	Choose legacy or new hyperparameter method depending whether df are unequal
	if(is.null(legacy)) {
		dfp <- df[df>0]
		legacy <- identical(min(dfp),max(dfp))
	}

#	Estimate hyperparameters
	if(legacy) {
		if(robust) {
			fit <- fitFDistRobustly(var, df1=df, covariate=covariate, winsor.tail.p=winsor.tail.p)
			df.prior <- fit$df2.shrunk
		} else {
			fit <- fitFDist(var, df1=df, covariate=covariate)
			df.prior <- fit$df2
		}
	} else {
		fit <- fitFDistUnequalDF1(var, df1=df, covariate=covariate, span=span, robust=robust)
		df.prior <- fit$df2.shrunk
		if(is.null(df.prior)) df.prior <- fit$df2
	}
	if(anyNA(df.prior)) stop("Could not estimate prior df")

#	Posterior variances
	var.post <- .squeezeVar(var=var, df=df, var.prior=fit$scale, df.prior=df.prior)

	list(df.prior=df.prior,var.prior=fit$scale,var.post=var.post)
}

.squeezeVar <- function(var, df, var.prior, df.prior)
#	Squeeze posterior variances given hyperparameters
#	NAs not allowed in df.prior
#	Gordon Smyth
#	Created 5 May 2016. Last modified 1 August 2024.
{
#	If df.prior are all finite, use canonical formula
	m <- max(df.prior)
	if(is.finite(m)) return( (df*var + df.prior*var.prior) / (df+df.prior) )

#	Set var.post to var.prior of length n
	n <- length(var)
	if(identical(length(var.prior),n)) {
		var.post <- var.prior
	} else {
		var.post <- rep_len(var.prior, length.out=n)
	}

#	Check if df.prior all Inf
	m <- min(df.prior)
	if(m > 1e100) return(var.post)

#	Only some df.prior are finite
	i <- which(is.finite(df.prior))
	if(length(df)>1L) df <- df[i]
	df.prior <- df.prior[i]
	var.post[i] <- (df*var[i] + df.prior*var.post[i]) / (df+df.prior)

	var.post
}
