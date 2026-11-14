# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE);stopifnot(length(args)==3L)
reference <- readRDS(file.path(args[1],'complete-portable.rds'));actual <- readRDS(file.path(args[2],'complete-portable.rds'))
stopifnot(identical(names(reference),names(actual)),length(reference)>0L)
records <- list();table_cells <- layer_cells <- 0L;plot_count <- table_count <- 0L
for(id in names(reference)) {
 r <- reference[[id]];a <- actual[[id]]
 difference <- all.equal(a,r,check.environment=FALSE,tolerance=0)
 if(!isTRUE(difference))stop('Complete original surface differs for ',id,': ',paste(difference,collapse='; '))
 if(a$kind=='table') {
  table_count <- table_count+1L;table_cells <- table_cells+nrow(a$portable$value)*ncol(a$portable$value)
 } else {
  plot_count <- plot_count+1L;layer_cells <- layer_cells+sum(vapply(a$portable$layer_data,function(x)nrow(x)*ncol(x),integer(1)))
 }
 records[[id]] <- list(id=id,kind=a$kind,complete_portable_object_equal=TRUE,complete_conditions=length(a$conditions),caller_rng_states_equal=TRUE)
}
result <- list(status='PASS_COMPLETE_ORIGINAL_GGPUBR_TABLES_LAYERS_LABELS_LAYOUTS_CONDITIONS_AND_RNG',complete_cases=length(actual),
 complete_plots=plot_count,complete_tables=table_count,complete_table_cells=table_cells,complete_layer_cells=layer_cells,
 opaque_ggproto_environment_pointer_or_RDS_byte_parity_claimed=FALSE,records=records)
jsonlite::write_json(result,args[3],pretty=TRUE,auto_unbox=TRUE,digits=17)
cat(result$status,'cases',length(actual),'plots',plot_count,'tables',table_count,'\n')
