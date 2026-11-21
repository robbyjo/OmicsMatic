# Extract complete owned synthetic CDS objects; no original data or labels are changed.
args<-commandArgs(trailingOnly=TRUE);stopifnot(length(args)==4L)
out<-args[1];.libPaths(args[2:3],include.site=FALSE);old<-args[4]
suppressPackageStartupMessages(library(monocle3));stopifnot(as.character(packageVersion("monocle3"))=="1.0.0")
records<-list()
for(label in sort(list.dirs(file.path(old,"cases"),recursive=FALSE,full.names=FALSE))){
 input<-readRDS(file.path(old,"cases",label,"input.rds"));stopifnot(inherits(input$cds,"cell_data_set"));methods::validObject(input$cds)
 target<-file.path(out,label);dir.create(target);saveRDS(input$cds,file.path(target,"cds.rds"));check<-readRDS(file.path(target,"cds.rds"))
 stopifnot(identical(SingleCellExperiment::counts(input$cds),SingleCellExperiment::counts(check)),identical(SummarizedExperiment::colData(input$cds),SummarizedExperiment::colData(check)),identical(SummarizedExperiment::rowData(input$cds),SummarizedExperiment::rowData(check)),identical(SingleCellExperiment::reducedDims(input$cds),SingleCellExperiment::reducedDims(check)))
 records[[label]]<-list(k=input$input$k,neighbor_graph=input$input$mode,reduction_slot="UMAP",cells=ncol(input$cds),genes=nrow(input$cds),source=file.path(old,"cases",label,"input.rds"))
}
stopifnot(length(records)==33L)
jsonlite::write_json(list(status="PASS33_COMPLETE_SYNTHETIC_CDS_INPUTS_EXTRACTED",records=records),file.path(out,"RESULT.json"),pretty=TRUE,auto_unbox=TRUE,digits=NA)
cat("PASS extracted33 complete original synthetic CDS objects\n")
