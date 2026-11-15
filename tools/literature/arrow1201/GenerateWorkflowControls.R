# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Synthetic tabular layouts are project fixtures, not recovered author schemas.
args <- commandArgs(TRUE);stopifnot(length(args)==1L)
root <- args[[1L]];stopifnot(!file.exists(root));dir.create(root)
assets <- file.path(root,"assets");dir.create(assets)
requests <- file.path(root,"requests");dir.create(requests)
arrow::set_cpu_count(1L)
df <- data.frame(gene_id=c("001","g.1","G1","G1","gene \u03b1",NA," trailing "),
  cell_id=c("c1","c1","c/2","c3","c4","c5","c6"),
  value=c(0,-0,1.25,NA_real_,NaN,Inf,-Inf),
  count=c(0L,1L,2L,NA_integer_,0L,3L,4L),
  flag=c(TRUE,FALSE,NA,TRUE,FALSE,NA,TRUE),check.names=FALSE)
df[["int64"]] <- bit64::as.integer64(c("9007199254740993","9223372036854775806",NA,"-9007199254740993","0","1","2"))
df[["date"]] <- as.Date("2020-01-01")+c(0,1,NA,3,4,5,6)
df[["time UTC"]] <- as.POSIXct("2020-01-01",tz="UTC")+c(0,1,NA,3,4,5,6)
df[["category"]] <- factor(c("b","a",NA,"a","b","a","b"),levels=c("b","a","unused"))
df[["nested"]] <- list(c(1L,2L),integer(),NA_integer_,3L,c(4L,5L),0L,8L)
small <- file.path(assets,"expression-0.parquet")
other <- file.path(assets,"expression-1.parquet")
empty <- file.path(assets,"empty.parquet")
arrow::write_parquet(df,small)
arrow::write_parquet(df[c(7L,2L,2L),,drop=FALSE],other)
arrow::write_parquet(df[FALSE,,drop=FALSE],empty)
writeBin(charToRaw("not a parquet file\n"),file.path(assets,"broken.parquet"))
hive <- file.path(assets,"deg");dir.create(hive)
deg <- data.frame(row_id=1:8,gene_id=c("001","G1","G1","gene \u03b1",NA,"g.1"," trailing ","Z"),
  fg_cluster=c("C1","C1","C1","C2","C2","C2","C1","C1"),
  bg_cluster=c("C2","C2","C3","C1","C3","C1","C2","C2"),
  log2FC=c(2,-3,0,Inf,NA_real_,NaN,-Inf,1),adj_p=c(0.01,0.02,0.8,NA_real_,NaN,0.001,0.049,0.2),
  fg_bin=c(0L,0L,0L,1L,1L,1L,0L,0L),bg_bin=c(1L,1L,2L,0L,2L,0L,1L,1L))
arrow::write_dataset(deg,hive,format="parquet",partitioning=c("fg_bin","bg_bin"),hive_style=TRUE)
base <- function(operation)list(schema_version=1L,operation=operation,cpu_count=1L)
read <- function(chunks) c(base("read-chunks"),list(chunks=chunks))
query <- function(steps=list(),sources=hive,unify=TRUE) c(base("dataset-query"),
  list(arguments=list(sources=sources,format="parquet",unify_schemas=unify),
       steps=steps,collect_arguments=list(as_data_frame=TRUE)))
step <- function(verb,...)list(verb=verb,arguments=list(...))
save <- function(name,request)saveRDS(request,file.path(requests,paste0(name,".rds")),version=3)
save("read-complete",read(list(main=list(file=small,as_data_frame=TRUE))))
save("read-projection",read(list(main=list(file=small,col_select=c("gene_id","value","int64"),as_data_frame=TRUE))))
save("read-ordered-chunks",read(list(first=list(file=other,as_data_frame=TRUE),second=list(file=small,as_data_frame=TRUE))))
save("read-empty",read(list(empty=list(file=empty,as_data_frame=TRUE))))
save("query-complete",query(list(step("arrange",quote(row_id)))))
save("query-pair",query(list(step("filter",quote(fg_cluster=="C1"),quote(bg_cluster=="C2"),quote(adj_p<0.05)),
  step("arrange",quote(row_id)),step("select",quote(gene_id),quote(log2FC),quote(adj_p),quote(fg_cluster),quote(bg_cluster),quote(row_id)))))
save("query-partition",query(list(step("filter",quote(fg_bin==1L),quote(bg_bin==0L)),step("arrange",quote(row_id)))))
save("query-empty",query(list(step("filter",quote(fg_cluster=="absent")),step("arrange",quote(row_id)))))
files <- list.files(hive,pattern="[.]parquet$",recursive=TRUE,full.names=TRUE)
save("query-explicit-files",query(list(step("arrange",quote(row_id))),sources=rev(files)))
save("read-broken",read(list(main=list(file=file.path(assets,"broken.parquet"),as_data_frame=TRUE))))
save("query-missing-column",query(list(step("select",quote(no_such_gene_column)))))
save("query-unsupported",query(list(step("filter",quote(stats::median(log2FC)>1)))))
# A real 50,000 x 500 tabular file exercises the reported chunk dimensions.
# The wide sparse-zero layout here is a fixture choice, not the paper's unknown encoding.
wide <- as.data.frame(setNames(replicate(500L,integer(50000L),simplify=FALSE),sprintf("gene%03d",1:500)))
for(j in seq_len(500L))wide[[j]][c(1L,50000L)] <- c(j,2L*j)
large <- file.path(assets,"dimensions-50000x500.parquet")
arrow::write_parquet(wide,large)
save("read-reported-dimensions",read(list(chunk=list(file=large,as_data_frame=TRUE))))
saveRDS(list(data_frame_before_arrow=df,deg_before_arrow=deg,fixture_layout_is_author_layout=FALSE),
  file.path(root,"fixture-originals.rds"),version=3)
jsonlite::write_json(list(status="GENERATED_ORIGINAL_ARROW1201_PROJECT_FIXTURES",
  cases=sort(sub("[.]rds$","",list.files(requests))),reported_dimension_fixture=c(50000L,500L),
  author_parquet_schema_recovered=FALSE),file.path(root,"GENERATED.json"),pretty=TRUE,auto_unbox=TRUE)
