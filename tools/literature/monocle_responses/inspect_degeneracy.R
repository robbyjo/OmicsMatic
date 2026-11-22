# Inspect complete saved original objects; do not refit or replace a response.
args<-commandArgs(TRUE);stopifnot(length(args)==2L,as.character(getRversion())=="4.3.3")
out<-args[1];saved<-readRDS(args[2]);source<-saved$models_and_results[["greater 4"]]
stopifnot(!is.null(source),source$model$rank==2L,nrow(saved$responses)==4L)
y<-saved$responses[4,];X<-saved$design
error<-max(abs(y-(-4+(48/7)*X[,2])))
response_norm<-sqrt(sum(y^2));residual_norm<-sqrt(sum(source$model$residuals^2))
ratio<-residual_norm/response_norm;threshold<-64*.Machine$double.eps*source$model$rank
stopifnot(error<=4e-15,is.finite(ratio),ratio<threshold,all(is.finite(c(source$result$estimate,source$result$statistic,source$result$p.value))))
jsonlite::write_json(list(status="PASS_RETAINED_ORIGINAL_NUMERICALLY_DEGENERATE_RESPONSE_DIAGNOSIS",
    case="asis-plain",gene="gene-4",cells=length(y),rank=source$model$rank,
    response_relation="y=-4+(48/7)*supplied_trend",maximum_binary64_relation_error=error,
    original_R_response_norm=response_norm,original_R_residual_norm=residual_norm,
    original_R_residual_relative_norm=ratio,native_relative_resolution_threshold=threshold,
    original_R_finite_estimates=as.list(unname(source$result$estimate)),
    original_R_z=unname(source$result$statistic),original_R_p_value=source$result$p.value,
    original_R_refitted=FALSE,original_input_modified=FALSE,
    action="Retain original finite inference as source evidence. Native testing refuses the entire affected gene family for all alternatives; preparation itself remains admissible."),
    file.path(out,"RESULT.json"),pretty=TRUE,auto_unbox=TRUE,digits=NA)
