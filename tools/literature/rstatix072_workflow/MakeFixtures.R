# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE)
stopifnot(length(args)==1L,!dir.exists(args[1]))
dir.create(args[1],recursive=TRUE)
cases <- list()
add <- function(id,data,call,seed) {
 stopifnot(!(id %in% names(cases)))
 cases[[id]] <<- list(id=id,data=data,call=call,seed=seed)
}
for(rep in seq_len(4L)) {
 set.seed(72100L+rep)
 data <- data.frame(value=c(rnorm(12),rnorm(12,1.5,1.3),rnorm(12,.5,.8)),
  other=rnorm(36),group=factor(rep(c('Aα','literal NA','group space'),each=12),levels=c('Aα','literal NA','group space')),
  batch=factor(rep(c('batch 1','batch 2'),18)),subject=rep(paste0('subject-',seq_len(12)),3),check.names=FALSE)
 data$other <- .7*data$value+data$other
 two <- droplevels(data[data$group!='group space',,drop=FALSE])
 for(alternative in c('two.sided','less','greater')) {
  for(equal in c(FALSE,TRUE))
   add(paste0('r',rep,'-t-two-',alternative,'-equal-',equal),two,
    substitute(rstatix::t_test(data,value~group,alternative=ALT,var.equal=EQ,detailed=TRUE),list(ALT=alternative,EQ=equal)),1000L+rep)
  add(paste0('r',rep,'-t-paired-',alternative),two,
   substitute(rstatix::t_test(data,value~group,paired=TRUE,alternative=ALT,detailed=TRUE),list(ALT=alternative)),1100L+rep)
  for(paired in c(FALSE,TRUE)) for(exact in c(FALSE,TRUE))
   add(paste0('r',rep,'-wilcox-',alternative,'-paired-',paired,'-exact-',exact),two,
    substitute(rstatix::wilcox_test(data,value~group,paired=PAIR,exact=EX,alternative=ALT,detailed=TRUE),list(PAIR=paired,EX=exact,ALT=alternative)),1200L+rep)
  for(method in c('pearson','spearman','kendall'))
   add(paste0('r',rep,'-cor-',method,'-',alternative),data,
    substitute(rstatix::cor_test(data,value,other,method=METHOD,alternative=ALT),list(METHOD=method,ALT=alternative)),1300L+rep)
 }
 for(adjust in c('holm','BH','bonferroni','BY','none'))
  add(paste0('r',rep,'-t-pairwise-',adjust),data,
   substitute(rstatix::t_test(data,value~group,p.adjust.method=ADJ,detailed=TRUE),list(ADJ=adjust)),1400L+rep)
 for(adjust in c('holm','BH','bonferroni')) for(reference in list(NULL,'.all.','Aα'))
  add(paste0('r',rep,'-wilcox-pairwise-',adjust,'-ref-',if(is.null(reference))'NULL' else reference),data,
   substitute(rstatix::wilcox_test(data,value~group,ref.group=REF,p.adjust.method=ADJ,alternative='greater',exact=FALSE,detailed=TRUE),list(REF=reference,ADJ=adjust)),1500L+rep)
 add(paste0('r',rep,'-shapiro-two-variables'),data,quote(rstatix::shapiro_test(data,value,other)),1600L+rep)
 add(paste0('r',rep,'-shapiro-grouped'),data,quote(rstatix::shapiro_test(dplyr::group_by(data,batch),value,other)),1600L+rep)
 for(center in list(quote(stats::median),quote(base::mean),quote(function(x)mean(x,trim=.1)))) {
  center_name <- if(identical(center,quote(stats::median)))'median' else if(identical(center,quote(base::mean)))'mean' else 'trimmed-mean'
  add(paste0('r',rep,'-levene-',center_name),data,substitute(rstatix::levene_test(data,value~group,center=CENTER),list(CENTER=center)),1700L+rep)
  add(paste0('r',rep,'-levene-grouped-',center_name),data,substitute(rstatix::levene_test(dplyr::group_by(data,batch),value~group,center=CENTER),list(CENTER=center)),1700L+rep)
 }
 add(paste0('r',rep,'-kruskal'),data,quote(rstatix::kruskal_test(data,value~group)),1800L+rep)
 add(paste0('r',rep,'-kruskal-grouped'),data,quote(rstatix::kruskal_test(dplyr::group_by(data,batch),value~group)),1800L+rep)
 for(adjust in c('holm','hochberg','hommel','bonferroni','BH','BY','fdr','none'))
  add(paste0('r',rep,'-adjust-',adjust),data,
   substitute(rstatix::adjust_pvalue(rstatix::t_test(data,value~group,detailed=TRUE),p.col='p',method=ADJ),list(ADJ=adjust)),1900L+rep)
}
stopifnot(length(cases)==248L)
saveRDS(cases,file.path(args[1],'cases.rds'),version=3)
jsonlite::write_json(list(status='EXPLICIT_SYNTHETIC_RSTATIX072_FUNCTION_ARGUMENT_AND_POPULATION_FIXTURES',
 complete_cases=length(cases),ids=names(cases),caller_seeds_explicit=TRUE,
 original_calls=lapply(cases,function(x)paste(deparse(x$call),collapse='\n'))),file.path(args[1],'fixture-manifest.json'),pretty=TRUE,auto_unbox=TRUE)
cat('COMPLETE_RSTATIX072_ORIGINAL_FUNCTION_CASES ',length(cases),'\n',sep='')
