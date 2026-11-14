# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Explicit synthetic populations; no author input or design is reconstructed.
args <- commandArgs(TRUE);stopifnot(length(args)==1L,!dir.exists(args[1]))
dir.create(args[1],recursive=TRUE)
set.seed(842719)
data <- data.frame(sample=paste0('sample-α-',seq_len(36L)),
 group=factor(rep(c('control','treated','third'),each=12L),levels=c('control','treated','third')),
 subject=rep(paste0('subject-',seq_len(12L)),3L),
 facet=factor(rep(rep(c('F1','F2'),each=6L),3L)),
 value=rep(c(3,4,5,7,8,10,12,14,17,19,22,25),3L)+rep(c(0,3,7),each=12L)+rnorm(36L,0,.4),
 other=rep(c(2,5,3,8,6,11,10,15,14,21,18,24),3L)+rep(c(0,2,4),each=12L)+rnorm(36L,0,.5),
 stringsAsFactors=FALSE)
two <- droplevels(data[data$group!='third',]);cases <- list()
add <- function(id,kind,code,data,statistical_rows=NULL) {
 stopifnot(is.null(cases[[id]]))
 cases[[id]] <<- list(id=id,kind=kind,call=parse(text=code,keep.source=FALSE)[[1]],data=data,
  seed=190000L+length(cases),expected_statistical_layer_rows=statistical_rows)
}
box <- "ggpubr::ggboxplot(data,x='group',y='value',color='group',palette=c('#2166AC','#B2182B','#4D9221'))"
scatter <- "ggpubr::ggscatter(data,x='value',y='other',color='#2166AC',size=2)"
for(method in c('t.test','wilcox.test'))for(paired in c(FALSE,TRUE))for(alternative in c('two.sided','greater','less')) {
 options <- sprintf("method='%s',paired=%s,alternative='%s',p.adjust.method='holm'",method,paired,alternative)
 add(paste('table',method,paired,alternative,sep='-'),'table',paste0('ggpubr::compare_means(value~group,data=data,',options,')'),two)
 options <- sprintf("method='%s',paired=%s,method.args=list(alternative='%s'),label='p.format'",method,paired,alternative)
 add(paste('annotation',method,paired,alternative,sep='-'),'plot',paste0(box,'+ggpubr::stat_compare_means(',options,')'),two,1L)
}
for(alternative in c('two.sided','greater','less'))add(paste0('table-t-equal-',alternative),'table',
 sprintf("ggpubr::compare_means(value~group,data=data,method='t.test',var.equal=TRUE,alternative='%s')",alternative),two)
for(method in c('anova','kruskal.test')) {
 add(paste0('table-',method),'table',sprintf("ggpubr::compare_means(value~group,data=data,method='%s')",method),data)
 add(paste0('annotation-',method),'plot',paste0(box,sprintf("+ggpubr::stat_compare_means(method='%s',label='p.format')",method)),data,1L)
}
for(method in c('t.test','wilcox.test')) {
 for(ref in c('control','.all.'))add(paste('table-reference',method,ref,sep='-'),'table',
  sprintf("ggpubr::compare_means(value~group,data=data,method='%s',ref.group='%s',p.adjust.method='BH')",method,ref),data)
 add(paste0('table-multiple-response-',method),'table',sprintf("ggpubr::compare_means(c(value,other)~group,data=data,method='%s',p.adjust.method='bonferroni')",method),data)
 add(paste0('table-by-facet-',method),'table',sprintf("ggpubr::compare_means(value~group,data=data,method='%s',group.by='facet',p.adjust.method='BH')",method),data)
 add(paste0('annotation-reference-',method),'plot',paste0(box,sprintf("+ggpubr::stat_compare_means(method='%s',ref.group='control',label='p.signif')",method)),data,2L)
 add(paste0('annotation-comparison-',method),'plot',paste0(box,sprintf("+ggpubr::stat_compare_means(method='%s',comparisons=list(c('control','treated')),label='p.format')",method)),two,3L)
 add(paste0('annotation-all-pairs-',method),'plot',paste0(box,sprintf("+ggpubr::stat_compare_means(method='%s',comparisons=list(c('control','treated'),c('control','third'),c('treated','third')),label='p.signif',step.increase=.12)",method)),data,9L)
 add(paste0('annotation-by-facet-',method),'plot',paste0(box,sprintf("+ggpubr::stat_compare_means(method='%s',label='p.format')+ggplot2::facet_wrap(~facet)",method)),two,2L)
}
for(method in c('pearson','spearman','kendall')) {
 name <- switch(method,pearson='R',spearman='rho',kendall='tau')
 for(alternative in c('two.sided','greater','less'))for(type in c('text','expression')) {
  code <- paste0(scatter,sprintf("+ggpubr::stat_cor(method='%s',alternative='%s',output.type='%s',cor.coef.name='%s',label.x.npc='left',label.y.npc='top')",method,alternative,type,name))
  add(paste('cor',method,alternative,type,sep='-'),'plot',code,two,1L)
 }
 code <- sprintf("ggpubr::ggscatter(data,x='value',y='other',color='group',palette=c('#2166AC','#B2182B'))+ggpubr::stat_cor(mapping=ggplot2::aes(color=group),method='%s',output.type='text')",method)
 add(paste0('cor-grouped-',method),'plot',code,two,2L)
 add(paste0('cor-faceted-',method),'plot',paste0(scatter,sprintf("+ggpubr::stat_cor(method='%s',output.type='text')+ggplot2::facet_wrap(~facet)",method)),two,2L)
 add(paste0('cor-grouped-faceted-',method),'plot',paste0(code,'+ggplot2::facet_wrap(~facet)'),two,4L)
}
add('violin-original','plot',"ggpubr::ggviolin(data,x='group',y='value',fill='group',palette=c('#2166AC','#B2182B','#4D9221'),trim=FALSE)",data)
add('violin-boxplot-facets','plot',"ggpubr::ggviolin(data,x='group',y='value',color='group',palette=c('#2166AC','#B2182B','#4D9221'),add='boxplot',add.params=list(width=.12))+ggplot2::facet_wrap(~facet)",data)
add('violin-log-scale','plot',"ggpubr::ggviolin(data,x='group',y='value',fill='group',palette=c('#2166AC','#B2182B','#4D9221'))+ggplot2::scale_y_log10()",data)
add('violin-horizontal','plot',"ggpubr::ggviolin(data,x='group',y='value',fill='group',palette=c('#2166AC','#B2182B','#4D9221'))+ggplot2::coord_flip()",data)
add('arrange-two-plots','plot',paste0("ggpubr::ggarrange(",box,",ggpubr::ggviolin(data,x='group',y='other',fill='group',palette=c('#2166AC','#B2182B','#4D9221')),ncol=2,nrow=1,labels=c('A','B'))"),data)
saveRDS(cases,file.path(args[1],'cases.rds'),version=3)
manifest <- lapply(cases,function(x)list(id=x$id,kind=x$kind,seed=x$seed,rows=nrow(x$data),original_call=paste(deparse(x$call),collapse='\n'),expected_statistical_layer_rows=x$expected_statistical_layer_rows))
jsonlite::write_json(list(status='EXPLICIT_COMPLETE_SYNTHETIC_GGPUBR_MATRIX_NOT_AUTHOR_DATA',cases=manifest,case_count=length(cases),
 plot_count=sum(vapply(cases,function(x)x$kind=='plot',logical(1))),table_count=sum(vapply(cases,function(x)x$kind=='table',logical(1)))),
 file.path(args[1],'fixture-manifest.json'),pretty=TRUE,auto_unbox=TRUE,na='string')
cat('READY_SYNTHETIC_CASES',length(cases),'\n')
