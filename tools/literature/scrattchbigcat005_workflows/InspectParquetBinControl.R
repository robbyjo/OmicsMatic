# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent table reconstruction and normalized-Euclidean distance calculations.
args <- commandArgs(TRUE);stopifnot(length(args)==3L)
library(Matrix)
out <- args[[1L]];case <- args[[2L]];inputs <- readRDS(file.path(args[[3L]],"complete-control-inputs.rds"))
library(arrow)
prepared <- readRDS(file.path(case,"adapter/original/complete-original-preparation.rds"))
descriptor <- prepared$objects$expression
stopifnot(identical(descriptor$row_id,rownames(inputs$mat)),identical(descriptor$col_id,colnames(inputs$mat)))
original.workspace <- jsonlite::fromJSON(file.path(case,"adapter/control.json"))$workspace
stopifnot(startsWith(descriptor$parquet.dir,paste0(original.workspace,"/")))
relative <- substring(descriptor$parquet.dir,nchar(original.workspace)+2L)
stopifnot(!".." %in% strsplit(relative,"/",fixed=TRUE)[[1L]])
# Read the captured workspace copy without modifying the original descriptor.
physical.parquet <- file.path(case,"workspace",relative)
files <- list.files(physical.parquet,pattern="[.]parquet$",recursive=TRUE,full.names=TRUE)
partition <- paste(basename(dirname(dirname(files))),basename(dirname(files)),sep="/")
expected.partitions <- paste0("col_bin=",rep(1:2,each=2L),"/row_bin=",rep(1:2,2L))
stopifnot(length(files)==4L,setequal(partition,expected.partitions))
tables <- setNames(lapply(files,function(path)as.data.frame(arrow::read_parquet(path))),partition)
entries <- do.call(rbind,tables)
stopifnot(nrow(entries)==30L,!anyDuplicated(paste(entries$i,entries$j)))
expected <- as.matrix(inputs$mat[,inputs$active_cols,drop=FALSE]);observed <- expected*0
columns <- match(entries$j+1L,inputs$active_cols);stopifnot(!anyNA(columns))
observed[cbind(entries$i+1L,columns)] <- entries$x
stopifnot(identical(expected,observed))
mapping <- readRDS(file.path(case,"adapter/original/complete-result.rds"))
stopifnot(setequal(as.character(mapping$sample_id),colnames(expected)),!anyDuplicated(mapping$sample_id),
  all(c("sample_id","cl","dist") %in% names(mapping)))
normalize <- function(mat)sweep(mat,2L,sqrt(colSums(mat^2)),"/")
query <- normalize(expected);reference <- normalize(inputs$centroids)
distances <- sapply(seq_len(ncol(query)),function(j)sqrt(colSums((reference-query[,j])^2)))
dimnames(distances) <- list(colnames(reference),colnames(query))
best <- apply(distances,2L,which.min);labels <- rownames(distances)[best]
names(labels) <- colnames(query)
predicted <- labels[as.character(mapping$sample_id)]
stopifnot(identical(unname(predicted),as.character(mapping$cl)))
cpu <- distances[cbind(match(mapping$cl,rownames(distances)),match(mapping$sample_id,colnames(distances)))]
maximum <- max(abs(cpu-mapping$dist));stopifnot(maximum<1e-6)
saveRDS(list(input=inputs,all_original_parquet_tables=tables,reconstructed=observed,
  complete_mapping=mapping,independent_distances=distances,expected_labels=labels),
  file.path(out,"complete-independent-partition-and-distance-check.rds"),version=3)
jsonlite::write_json(list(status="PASS_FOUR_NONZERO_ORIGINAL_PARTITIONS_AND_INDEPENDENT_MAPPING",
  dimensions=dim(inputs$mat),bin_cells=50000L,bin_genes=500L,partitions=partition,nonzero_entries=nrow(entries),
  complete_query_matrix_identical=TRUE,complete_cell_and_gene_ID_vectors_identical=TRUE,
  mapped_cells=nrow(mapping),maximum_Annoy_float_distance_difference=maximum,
  independent_CPU_tolerance=1e-6,original_distances_modified=FALSE,
  original_metric="Euclidean distance of L2-normalized vectors",new_family_credit=0),
  file.path(out,"INSPECTION.json"),pretty=TRUE,auto_unbox=TRUE,digits=17)
