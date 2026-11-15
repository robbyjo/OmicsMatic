# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Inspect complete original Annoy metadata and graph arrays without rewriting paths.
args <- commandArgs(TRUE);stopifnot(length(args)==2L);out <- args[[1L]];case <- args[[2L]]
r <- readRDS(file.path(case,"reference/complete-result.rds"))
a <- readRDS(file.path(case,"adapter/original/complete-result.rds"))
indices <- list(reference=r$graph$knn.result$index,adapter=a$original_graph$knn.result$index)
slots <- lapply(indices,function(index)setNames(lapply(slotNames(index),function(name)slot(index,name)),slotNames(index)))
saveRDS(slots,file.path(out,"complete-original-index-slots.rds"),version=3)
writeLines(capture.output(str(slots,max.level=4)),file.path(out,"complete-index-slot-inspection.txt"))
plain <- list(
  complete_neighbour_indices=identical(r$graph$knn.result$knn.index,a$original_graph$knn.result$knn.index),
  complete_neighbour_distances=identical(r$graph$knn.result$knn.distance,a$original_graph$knn.result$knn.distance),
  complete_cluster_graph=identical(r$graph$knn.cl.df,a$original_graph$knn.cl.df),
  complete_outlier_ids=identical(r$graph$outlier,a$original_graph$outlier),
  complete_selected_edges=identical(r$selected,a$selected_edges))
path.fields <- lapply(slots,function(fields)Filter(function(value)is.character(value)&&length(value)==1L&&
  startsWith(value,"/"),fields))
jsonlite::write_json(list(status="COMPLETE_GRAPH_ARRAYS_AND_ORIGINAL_INDEX_SLOTS_INSPECTED",
  exact_graph_comparisons=plain,index_slot_names=lapply(slots,names),path_fields=path.fields,
  original_paths_exist=lapply(path.fields,function(fields)lapply(fields,file.exists)),
  full_original_objects_modified=FALSE,live_pointer_persistence_qualified=FALSE,new_family_credit=0),
  file.path(out,"INSPECTION.json"),pretty=TRUE,auto_unbox=TRUE,null="null",na="string")
