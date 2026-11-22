# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Retain full-object comparison errors, then inspect complete source data and layers.
args <- commandArgs(TRUE);stopifnot(length(args)==2L);out <- args[[1L]];case <- args[[2L]]
r <- readRDS(file.path(case,"reference/complete-result.rds"))
a <- readRDS(file.path(case,"adapter/original/complete-result.rds"))
differences <- list()
compare <- function(label,x,y) {
  result <- tryCatch(all.equal(x,y,tolerance=0),error=function(c)
    list(comparison_error=c,class=class(c),message=conditionMessage(c)))
  differences[[label]] <<- list(identical=identical(x,y),complete_all_equal_return=result)
  saveRDS(differences,file.path(out,"complete-comparison-progress.rds"),version=3)
  cat(label,if(isTRUE(result))"EQUAL"else"DIFFERENCE_OR_COMPARISON_ERROR","\n")
}
writeLines(capture.output(str(r$graph,max.level=3)),file.path(out,"complete-graph-structure.txt"))
compare("complete_original_graph",r$graph,a$original_graph)
compare("complete_selected_edges",r$selected,a$selected_edges)
compare("complete_original_plot",r$plot,a$original_plot)
rp <- r$plot;ap <- a$original_plot
saveRDS(list(reference_names=names(rp),adapter_names=names(ap)),file.path(out,"complete-plot-member-names.rds"),version=3)
writeLines(capture.output(str(rp,max.level=2)),file.path(out,"complete-plot-structure.txt"))
for(name in names(rp)) {
  compare(paste0("plot_member/",name),rp[[name]],ap[[name]])
  if(inherits(rp[[name]],"ggplot")) {
    rb <- ggplot2::ggplot_build(rp[[name]]);ab <- ggplot2::ggplot_build(ap[[name]])
    saveRDS(list(reference=rb,adapter=ab),file.path(out,paste0("complete-build-",name,".rds")),version=3)
    compare(paste0("complete_layer_data/",name),rb$data,ab$data)
    compare(paste0("complete_panel_parameters/",name),rb$layout$panel_params,ab$layout$panel_params)
  }
}
summary <- lapply(differences,function(item) {
  result <- item$complete_all_equal_return
  if(is.list(result)&&inherits(result$comparison_error,"condition"))
    item$complete_all_equal_return <- list(class=result$class,message=result$message,
      complete_condition_record="complete-comparison-progress.rds")
  item
})
jsonlite::write_json(list(status="FULL_GRAPH_PLOT_DIFFERENCES_AND_COMPLETE_BUILDS_RETAINED",comparisons=summary,
  complete_condition_records="complete-comparison-progress.rds",
  complete_original_objects_pruned=FALSE,full_plot_object_identity_claimed=FALSE,new_family_credit=0),
  file.path(out,"INSPECTION.json"),pretty=TRUE,auto_unbox=TRUE,null="null",na="string",digits=17)
