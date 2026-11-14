# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Every comparison, population and plotting operation here is explicit.
grDevices::pdf(file.path(result_directory,'construction-device.pdf'))
construction_device <- grDevices::dev.cur()
data <- read.delim(file.path(input_directory,'data.tsv'),sep='\t',quote='',check.names=FALSE,na.strings=character(),
 colClasses=c('character','character','numeric','numeric'),fileEncoding='UTF-8')
stopifnot(nrow(data)==24L,!anyDuplicated(data$sample),all(is.finite(data$value)),all(is.finite(data$other)),
 identical(sort(unique(data$group)),c('control','treatment')))
data$group <- factor(data$group,levels=c('control','treatment'));stopifnot(all(table(data$group)==12L))
statistics <- ggpubr::compare_means(value~group,data=data,method='t.test',paired=FALSE,var.equal=FALSE,alternative='two.sided',p.adjust.method='holm')
stopifnot(nrow(statistics)==1L)
write.table(statistics,file.path(result_directory,'complete-statistics.tsv'),sep='\t',row.names=FALSE,quote=TRUE)
calls <- list(
 boxplot=quote(ggpubr::ggboxplot(data,x='group',y='value',color='group',palette=c('#2166AC','#B2182B'))+
  ggpubr::stat_compare_means(method='t.test',paired=FALSE,method.args=list(var.equal=FALSE,alternative='two.sided'),label='p.format')),
 scatter=quote(ggpubr::ggscatter(data,x='value',y='other',color='#2166AC')+ggpubr::stat_cor(method='pearson',alternative='two.sided',output.type='text')),
 violin=quote(ggpubr::ggviolin(data,x='group',y='value',fill='group',palette=c('#2166AC','#B2182B'),add='boxplot',add.params=list(width=.12))))
objects <- list();layers <- list()
for(name in names(calls)) {
 env <- list2env(list(data=data),parent=globalenv());set.seed(741L)
 plot <- eval(calls[[name]],envir=env);built <- ggplot2::ggplot_build(plot)
 if(name %in% c('boxplot','scatter'))stopifnot(nrow(built$data[[length(built$data)]])==1L)
 stopifnot(all(vapply(built$data,nrow,integer(1))>0L))
 grob <- ggplot2::ggplot_gtable(built)
 objects[[name]] <- list(plot=plot,built=built,grob=grob,original_call=calls[[name]])
 layers[[name]] <- built$data
 svglite::svglite(file.path(result_directory,paste0(name,'.svg')),width=8,height=5,bg='white',pointsize=12)
 tryCatch(print(plot),finally=grDevices::dev.off())
 ragg::agg_png(file.path(result_directory,paste0(name,'.png')),width=8,height=5,units='in',res=120,background='white',pointsize=12)
 tryCatch(print(plot),finally=grDevices::dev.off())
}
saveRDS(list(data=data,statistics=statistics,plots=objects),file.path(result_directory,'complete-original-objects.rds'),version=3)
saveRDS(layers,file.path(result_directory,'complete-layer-data.rds'),version=3)
grDevices::dev.off(construction_device)
analysis_complete <- 'ALL_THREE_ORIGINAL_GGPUBR_EXAMPLE_PLOTS_AND_STATISTICS_RETURNED'
