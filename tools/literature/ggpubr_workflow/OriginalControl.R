# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# The exact caller script runs in the adapter and independent original R.
cases <- readRDS(file.path(input_directory,'cases.rds'))
stopifnot(length(cases)>0L,length(unique(names(cases)))==length(cases))
# Construction/layout helpers may open the original default PDF device. Keep
# that complete file within the declared results instead of an implicit cwd.
options(device=function(...)grDevices::pdf(file=file.path(result_directory,'construction-device.pdf'),...))
for(name in c('objects','portable','tables','plots'))dir.create(file.path(result_directory,name))
complete <- list();index <- list()
axis_surface <- function(axis) {
 list(breaks=axis$breaks,minor_breaks=axis$minor_breaks,limits=axis$limits,continuous_range=axis$continuous_range,
  labels=axis$get_labels(),aesthetics=axis$aesthetics,position=axis$position)
}
run_one <- function(case) {
 # Only the case data lives in the plot/formula execution environment.
 env <- list2env(list(data=case$data),parent=globalenv());set.seed(case$seed)
 seed_before <- .Random.seed;conditions <- list()
 capture <- function(c,kind)conditions[[length(conditions)+1L]] <<- list(kind=kind,object=c,class=class(c),message=conditionMessage(c),call=conditionCall(c))
 result <- withCallingHandlers({
  value <- eval(case$call,envir=env)
  if(case$kind=='table') {
   stopifnot(is.data.frame(value),nrow(value)>0L)
   write.table(value,file.path(result_directory,'tables',paste0(case$id,'.tsv')),sep='\t',row.names=FALSE,quote=TRUE,na='NA')
   list(original_value=value,portable=list(value=value),rows=nrow(value),columns=ncol(value))
  } else {
   built <- ggplot2::ggplot_build(value)
   if(!is.null(case$expected_statistical_layer_rows)) {
    actual <- nrow(built$data[[length(built$data)]])
    if(!identical(actual,case$expected_statistical_layer_rows))stop('Complete original statistical layer population differs for ',case$id,': ',actual,' expected ',case$expected_statistical_layer_rows)
   }
   grob <- ggplot2::ggplot_gtable(built)
   portable <- list(input_data=value$data,layer_data=built$data,layout=built$layout$layout,
    panels=lapply(built$layout$panel_params,function(p)list(x_range=p$x.range,y_range=p$y.range,x=axis_surface(p$x),y=axis_surface(p$y))),
    labels=built$plot$labels,grob_layout=grob$layout,grob_widths=as.character(grob$widths),grob_heights=as.character(grob$heights))
   seed_after_build <- .Random.seed
   svglite::svglite(file.path(result_directory,'plots',paste0(case$id,'.svg')),width=8,height=5,bg='white',pointsize=12)
   tryCatch(print(value),finally=grDevices::dev.off());seed_after_svg <- .Random.seed
   ragg::agg_png(file.path(result_directory,'plots',paste0(case$id,'.png')),width=8,height=5,units='in',res=120,background='white',pointsize=12)
   tryCatch(print(value),finally=grDevices::dev.off())
   list(original_value=value,original_built=built,original_grob=grob,portable=portable,
    seed_after_build=seed_after_build,seed_after_svg=seed_after_svg,rows=sum(vapply(built$data,nrow,integer(1))),columns=sum(vapply(built$data,ncol,integer(1))))
  }
 },warning=function(w)capture(w,'warning'),message=function(m)capture(m,'message'))
 result$conditions <- conditions;result$seed_before <- seed_before;result$seed_after <- .Random.seed
 result$id <- case$id;result$kind <- case$kind;result$original_call <- case$call
 result
}
for(i in seq_along(cases)) {
 case <- cases[[i]];record <- run_one(case)
 saveRDS(record,file.path(result_directory,'objects',paste0(case$id,'.rds')),version=3)
 # Portable surfaces retain all layer rows/columns, labels and numeric panels.
 # Full original plot, built and grob objects are retained above without edits.
 display <- record[c('id','kind','original_call','portable','seed_before','seed_after','seed_after_build','seed_after_svg')]
 display$conditions <- lapply(record$conditions,function(x)x[c('kind','class','message','call')])
 saveRDS(display,file.path(result_directory,'portable',paste0(case$id,'.rds')),version=3)
 complete[[case$id]] <- display
 index[[i]] <- data.frame(ordinal=i,id=case$id,kind=case$kind,complete_rows=record$rows,complete_columns=record$columns,
  warnings=sum(vapply(record$conditions,function(x)x$kind=='warning',logical(1))),messages=sum(vapply(record$conditions,function(x)x$kind=='message',logical(1))),stringsAsFactors=FALSE)
}
saveRDS(complete,file.path(result_directory,'complete-portable.rds'),version=3)
write.table(do.call(rbind,index),file.path(result_directory,'complete-index.tsv'),sep='\t',row.names=FALSE,quote=TRUE)
stopifnot(length(complete)==length(cases))
if(grDevices::dev.cur()>1L)grDevices::dev.off()
analysis_complete <- 'ALL_DECLARED_ORIGINAL_GGPUBR_PLOTS_AND_TABLES_RETURNED'
