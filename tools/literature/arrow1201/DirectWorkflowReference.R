# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Independent literal original API calls; never invokes the adapter translator.
local({
  args <- commandArgs(TRUE);stopifnot(length(args)==3L)
  case <- args[[1L]];assets <- args[[2L]];out <- args[[3L]]
  stopifnot(!file.exists(out));dir.create(out)
  invisible(loadNamespace("arrow"));invisible(loadNamespace("dplyr"))
  arrow::set_cpu_count(1L)
  rng <- function()list(kind=RNGkind(),seed_exists=exists(".Random.seed",globalenv(),inherits=FALSE),
    seed=if(exists(".Random.seed",globalenv(),inherits=FALSE))get(".Random.seed",globalenv())else NULL)
  before <- rng();value <- NULL;error <- NULL;conditions <- list()
  record <- function(c,kind)conditions[[length(conditions)+1L]] <<- list(kind=kind,phase="ORIGINAL_ANALYSIS",
    condition=c,class=class(c),message=conditionMessage(c),call=conditionCall(c))
  small <- file.path(assets,"expression-0.parquet");other <- file.path(assets,"expression-1.parquet")
  hive <- file.path(assets,"deg")
  tryCatch(withCallingHandlers({
    if(case=="read-complete")value <- list(main=arrow::read_parquet(small,as_data_frame=TRUE))
    else if(case=="read-projection")value <- list(main=arrow::read_parquet(small,col_select=c("gene_id","value","int64"),as_data_frame=TRUE))
    else if(case=="read-ordered-chunks")value <- list(first=arrow::read_parquet(other,as_data_frame=TRUE),second=arrow::read_parquet(small,as_data_frame=TRUE))
    else if(case=="read-empty")value <- list(empty=arrow::read_parquet(file.path(assets,"empty.parquet"),as_data_frame=TRUE))
    else if(case=="read-reported-dimensions")value <- list(chunk=arrow::read_parquet(file.path(assets,"dimensions-50000x500.parquet"),as_data_frame=TRUE))
    else if(case=="read-broken")value <- list(main=do.call(arrow::read_parquet,list(file=file.path(assets,"broken.parquet"),as_data_frame=TRUE)))
    else {
      if(case=="query-explicit-files")ds <- arrow::open_dataset(rev(list.files(hive,pattern="[.]parquet$",recursive=TRUE,full.names=TRUE)),format="parquet",unify_schemas=TRUE)
      else ds <- arrow::open_dataset(hive,format="parquet",unify_schemas=TRUE)
      if(case=="query-pair") {
        ds <- dplyr::filter(ds,fg_cluster=="C1",bg_cluster=="C2",adj_p<0.05)
        ds <- dplyr::arrange(ds,row_id)
        ds <- dplyr::select(ds,gene_id,log2FC,adj_p,fg_cluster,bg_cluster,row_id)
      } else if(case=="query-partition") {
        ds <- dplyr::filter(ds,fg_bin==1L,bg_bin==0L)
        ds <- dplyr::arrange(ds,row_id)
      } else if(case=="query-empty") {
        ds <- dplyr::filter(ds,fg_cluster=="absent")
        ds <- dplyr::arrange(ds,row_id)
      } else if(case=="query-missing-column")ds <- do.call(dplyr::select,list(.data=ds,quote(no_such_gene_column)))
      else if(case=="query-unsupported")ds <- do.call(dplyr::filter,list(.data=ds,quote(stats::median(log2FC)>1)))
      else if(case %in% c("query-complete","query-explicit-files"))ds <- dplyr::arrange(ds,row_id)
      else stop("Unknown independent reference case")
      value <- dplyr::collect(ds,as_data_frame=TRUE)
    }
  },warning=function(c)record(c,"warning"),message=function(c)record(c,"message"),
    error=function(c)saveRDS(list(error=c,calls=sys.calls()),file.path(out,"complete-original-failure-and-calls.rds"),version=3)),
    error=function(c){error <<- c;record(c,"error")})
  if(is.null(error))saveRDS(value,file.path(out,"complete-result.rds"),version=3)
  saveRDS(list(before=before,after=rng()),file.path(out,"complete-rng-before-after.rds"),version=3)
  saveRDS(list(phase=if(is.null(error))"COMPLETE"else"ORIGINAL_ANALYSIS",conditions=conditions,error=error),
    file.path(out,"complete-conditions.rds"),version=3)
  writeLines(capture.output(sessionInfo()),file.path(out,"original-session-info.txt"))
  jsonlite::write_json(list(case=case,returned=is.null(error),error_class=if(!is.null(error))class(error),
    error_message=if(!is.null(error))conditionMessage(error)),file.path(out,"REFERENCE.json"),pretty=TRUE,auto_unbox=TRUE,null="null")
})
