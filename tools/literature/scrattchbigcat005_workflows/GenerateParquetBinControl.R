# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Nonzero entries across both reported bin boundaries; synthetic data only.
args <- commandArgs(TRUE);stopifnot(length(args)==1L)
out <- args[[1L]];stopifnot(!file.exists(out));dir.create(out,recursive=TRUE)
library(Matrix);dir.create(file.path(out,"assets"));dir.create(file.path(out,"requests"))
genes <- sprintf("partition-gene-%03d",1:502);cells <- sprintf("partition-cell-%05d",1:50002)
active.rows <- c(1L,2L,3L,500L,501L,502L);active.cols <- c(1L,25000L,50000L,50001L,50002L)
values <- 2^seq_along(active.rows)
mat <- sparseMatrix(i=rep(active.rows,length(active.cols)),j=rep(active.cols,each=length(active.rows)),
  x=rep(values,length(active.cols)),dims=c(length(genes),length(cells)),dimnames=list(genes,cells))
centroids <- matrix(0,nrow=length(genes),ncol=3L,dimnames=list(genes,c("type-A","type-B","type-C")))
centroids[1:3,] <- diag(values[1:3])
centroids[active.rows[4:6],] <- values[4:6]
path <- function(relative)structure(list(relative=relative),class="omicsmatic_bigcat_path_v1")
ref <- structure(list(id="expression",path=character()),class="omicsmatic_bigcat_reference_v1")
set.seed(1729L)
request <- list(schema_version=1L,profile="prepublication-2023",operation="map_cells_knn_big",
  arguments=list(big.dat=ref,cl.dat=centroids,select.cells=cells[active.cols],method="Annoy.Cosine",block.size=2L,mc.cores=1L),
  rng=list(kind=RNGkind(),seed=.Random.seed),globals=list(),
  preparation=list(expression=list(constructor="convert_big.dat_parquet",
    arguments=list(mat=mat,dir=path("expression"),col.bin.size=50000L,row.bin.size=500L,logNormal=TRUE,mc.cores=1L))),
  post_selection=NULL,arrow_cpu_count=1L)
name <- "mapping-nonzero-four-partitions"
saveRDS(request,file.path(out,"requests",paste0(name,".rds")),version=3)
saveRDS(list(mat=mat,centroids=centroids,active_rows=active.rows,active_cols=active.cols),
  file.path(out,"complete-control-inputs.rds"),version=3)
jsonlite::write_json(list(status="SYNTHETIC_NONZERO_FOUR_PARTITION_REQUEST_NOT_QUALIFICATION",
  cases=setNames(list(list(profile=request$profile,operation=request$operation,
    role="Original 50000-cell/500-gene bins with nonzero entries in all four partitions",
    request=paste0("requests/",name,".rds"))),name),author_data=FALSE,new_family_credit=0),
  file.path(out,"CONTROLS.json"),pretty=TRUE,auto_unbox=TRUE)
