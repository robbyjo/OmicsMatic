# Independent original-spdep controls for the complete synthetic weights-to-test bridge.
args <- commandArgs(trailingOnly=TRUE);stopifnot(length(args)==6L)
out <- args[1];.libPaths(args[2:3],include.site=FALSE);original <- args[5]
stopifnot(as.character(getRversion())=="4.3.3",as.character(packageVersion("spdep"))=="1.4.1")
official <- new.env(parent=asNamespace("spdep"));sys.source(args[6],envir=official,keep.source=FALSE)
stopifnot(identical(body(official$lm.morantest),body(spdep::lm.morantest)),identical(formals(official$lm.morantest),formals(spdep::lm.morantest)))
source <- readRDS(file.path(original,"cases","27-5-principal_graph","input.rds"))
lw <- readRDS(file.path(original,"cases","27-5-principal_graph","original-listw.rds"))
ids <- unlist(source$input$cell_ids);n <- length(ids);stopifnot(n==27L)
index <- seq_len(n);design <- cbind(Intercept=rep(1,n),position=(index-14)/13,nonlinear=sin(index*0.17))
responses <- rbind(synthetic_gene_1=sin(index*0.37)+0.03*index,synthetic_gene_2=cos(index*0.57)-0.01*index,synthetic_gene_3=sin(index*0.11)+cos(index*0.23)^2)
writeLines(c(paste(c("cell_id",colnames(design)),collapse="\t"),vapply(seq_len(n),function(i)paste(c(ids[i],sprintf("%.17g",design[i,])),collapse="\t"),character(1))),file.path(out,"design.tsv"))
writeLines(c(paste(c("gene_id",ids),collapse="\t"),vapply(seq_len(nrow(responses)),function(i)paste(c(rownames(responses)[i],sprintf("%.17g",responses[i,])),collapse="\t"),character(1))),file.path(out,"expression.tsv"))
expected <- list();models <- list();tests <- list()
for(gene in rownames(responses)){
 y <- unname(responses[gene,]);fit <- stats::lm(y~design-1);test <- spdep::lm.morantest(fit,lw,alternative="greater",zero.policy=TRUE)
 expected[[gene]] <- c(moran_I=unname(test$estimate[1]),expectation=unname(test$estimate[2]),variance=unname(test$estimate[3]),z=unname(test$statistic),p_value=test$p.value,log_p_value=stats::pnorm(unname(test$statistic),lower.tail=FALSE,log.p=TRUE));models[[gene]] <- fit;tests[[gene]] <- test
}
q <- stats::p.adjust(vapply(expected,function(row)row[["p_value"]],numeric(1)),method="BH")
for(gene in names(expected))expected[[gene]] <- c(expected[[gene]],q_value=q[[gene]])
saveRDS(list(design=design,responses=responses,models=models,tests=tests,expected=expected,weights=lw),file.path(out,"original-spdep-bridge.rds"))
tokens <- lapply(expected,function(row)as.list(setNames(sprintf("%.17g",row),names(row))))
jsonlite::write_json(list(status="PASS_ORIGINAL_SPDEP_COMPLETE_SYNTHETIC_BRIDGE_REFERENCE",cells=n,genes=nrow(responses),source_case="27-5-principal_graph",spdep=as.character(packageVersion("spdep")),official_function_body_formals_identical=TRUE,expected=tokens,no_paper_analysis=TRUE),file.path(out,"REFERENCE.json"),pretty=TRUE,auto_unbox=TRUE,digits=NA)
writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"));cat("PASS three complete original-spdep bridge tests\n")
