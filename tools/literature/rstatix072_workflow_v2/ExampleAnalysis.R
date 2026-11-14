# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
data <- read.delim(file.path(input_directory,'data.tsv'),check.names=FALSE,stringsAsFactors=FALSE)
data$group <- factor(data$group,levels=c('control','treatment','third'))
two <- droplevels(data[data$group!='third',,drop=FALSE])
results <- list(
 normality=rstatix::shapiro_test(data,value,other),
 variance=rstatix::levene_test(data,value~group),
 omnibus=rstatix::kruskal_test(data,value~group),
 pairwise_wilcoxon=rstatix::wilcox_test(data,value~group,p.adjust.method='BH',exact=FALSE,detailed=TRUE),
 t_test=rstatix::t_test(two,value~group,paired=FALSE,var.equal=FALSE,alternative='two.sided',detailed=TRUE),
 correlation=rstatix::cor_test(data,value,other,method='pearson'),
 holm_adjustment=rstatix::adjust_pvalue(rstatix::t_test(data,value~group,detailed=TRUE),p.col='p',method='holm'))
stopifnot(length(results)==7L,all(vapply(results,function(x)is.data.frame(x)&&nrow(x)>0L,logical(1))))
saveRDS(results,file.path(result_directory,'complete-original-results.rds'),version=3)
index <- data.frame(operation=names(results),rows=vapply(results,nrow,integer(1)),columns=vapply(results,ncol,integer(1)))
write.table(index,file.path(result_directory,'complete-index.tsv'),sep='\t',row.names=FALSE,quote=TRUE)
analysis_complete <- 'ALL_SEVEN_EXPLICIT_EXAMPLE_OPERATIONS_RETURNED'
