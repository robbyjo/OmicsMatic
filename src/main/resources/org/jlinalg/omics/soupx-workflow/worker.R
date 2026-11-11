# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-2.0-or-later
# Separate original SoupX execution program. Standard files/JSON are its interface to Java.
status<-"SKIP3_DEPENDENCY_UNAVAILABLE";detail<-"";current_stage<-"runtime";runtime_ready<-FALSE;warnings_seen<-messages_seen<-character();case_reports<-list();guard_names<-character();guard_original<-NULL
action<-"Preserve the complete attempt. Read analysis-status.json, WARNINGS.md, worker-report.json, each source-status.json and original logs. Supply the exact permitted R4.6.1/selected SoupX/source/private-library profile or skip dependent analysis. Analysis installs nothing and substitutes no version. FAIL2 admits no result from the atomic batch. Correct the named input/runtime/source/model/capacity cause and retry NEW paths without ID repair, omitted counts, NA/NaN/Inf replacement, silent range changes or applying an undeclared historical estimate."
.libPaths(c(readLines("runtime-paths.txt",encoding="UTF-8",warn=FALSE),.Library),include.site=FALSE)
token<-function(x){if(is.numeric(x)&&is.nan(x))"NaN" else if(is.na(x))"NA" else if(is.numeric(x)&&is.infinite(x)){if(x>0)"Inf" else "-Inf"} else if(is.double(x))sprintf("%.17g",x) else as.character(x)}
kind<-function(x){if(is.numeric(x)&&is.nan(x))"NaN" else if(is.na(x))"NA" else if(is.numeric(x)&&is.infinite(x)){if(x>0)"positive-infinity" else "negative-infinity"} else "value"}
json<-function(x,path)jsonlite::write_json(x,path,pretty=TRUE,auto_unbox=TRUE,digits=NA,na="string",null="null")
literal<-function(path){lines<-readLines(path,encoding="UTF-8",warn=FALSE);rows<-strsplit(sub("\r$","",lines),"\t",fixed=TRUE);h<-rows[[1]];rows<-rows[-1L];cols<-lapply(seq_along(h),function(j)vapply(rows,function(r)r[[j]],character(1)));names(cols)<-h;as.data.frame(cols,check.names=FALSE,stringsAsFactors=FALSE)}
table_out<-function(frame,path){
 stopifnot(is.data.frame(frame),all(vapply(frame,function(x)is.atomic(x)&&is.null(dim(x)),logical(1))));saveRDS(frame,paste0(path,".rds"),version=3)
 if(nrow(frame)>p$transport_chunk_entries){
  parts<-list();destination<-file(paste0(path,".tsv"),open="wb");on.exit(close(destination),add=TRUE)
  for(first in seq.int(1L,nrow(frame),by=p$transport_chunk_entries)){last<-min(nrow(frame),first+p$transport_chunk_entries-1L);part<-paste0(path,sprintf(".part-%06d",length(parts)+1L));table_out(frame[first:last,,drop=FALSE],part);lines<-readLines(paste0(part,".tsv"),encoding="UTF-8",warn=FALSE);if(length(parts))lines<-lines[-1L];writeLines(enc2utf8(lines),destination,useBytes=TRUE);parts[[length(parts)+1L]]<-basename(paste0(part,".json"))}
  json(list(rows=nrow(frame),parts=parts,complete_original_RDS=basename(paste0(path,".rds"))),paste0(path,".json"));return(invisible(NULL))
 }
 columns<-lapply(seq_along(frame),function(i){x<-frame[[i]];list(name=names(frame)[i],class=as.list(class(x)),type=typeof(x),levels=if(is.factor(x))as.list(levels(x)) else NULL,tokens=as.list(vapply(seq_along(x),function(j)token(x[j]),character(1))),kinds=as.list(vapply(seq_along(x),function(j)kind(x[j]),character(1))))})
 encoded<-lapply(columns,function(x)unlist(x$tokens,use.names=FALSE));destination<-file(paste0(path,".tsv"),open="wb");on.exit(close(destination),add=TRUE);writeLines(enc2utf8(paste(names(frame),collapse="\t")),destination,useBytes=TRUE)
 if(nrow(frame))writeLines(enc2utf8(vapply(seq_len(nrow(frame)),function(i)paste(vapply(encoded,function(x)x[[i]],character(1)),collapse="\t"),character(1))),destination,useBytes=TRUE)
 json(list(rows=nrow(frame),columns=columns),paste0(path,".json"))
}
sparse_out<-function(x,path){
 saveRDS(x,sub("[.]json$",".rds",path),version=3);x<-methods::as(methods::as(x,"generalMatrix"),"CsparseMatrix");s<-summary(x);stopifnot(nrow(s)<=p$maximum_intermediate_entries);parts<-list()
 if(nrow(s)>0)for(first in seq.int(1L,nrow(s),by=p$transport_chunk_entries)){
  last<-min(nrow(s),first+p$transport_chunk_entries-1L);part<-paste0(sub("[.]json$","",path),sprintf(".part-%06d.json",length(parts)+1L));json(list(entries=lapply(seq.int(first,last),function(i)list(row=as.integer(s$i[i]),column=as.integer(s$j[i]),value=token(s$x[i]),kind=kind(s$x[i])))),part);parts[[length(parts)+1L]]<-basename(part)
 }
 destination<-file(sub("[.]json$",".mtx",path),open="wt",encoding="UTF-8");on.exit(close(destination),add=TRUE);writeLines(c("%%MatrixMarket matrix coordinate real general",sprintf("%d %d %d",nrow(x),ncol(x),nrow(s))),destination,useBytes=TRUE)
 if(nrow(s)>0)for(first in seq.int(1L,nrow(s),by=p$transport_chunk_entries)){last<-min(nrow(s),first+p$transport_chunk_entries-1L);writeLines(vapply(seq.int(first,last),function(i)paste(s$i[i],s$j[i],token(s$x[i])),character(1)),destination,useBytes=TRUE)}
 json(list(row_ids=as.list(rownames(x)),column_ids=as.list(colnames(x)),entry_count=nrow(s),entry_parts=parts),path)
}
guard<-function(){for(name in guard_names){a<-get(name,asNamespace("SoupX"),inherits=FALSE);b<-get(name,guard_original,inherits=FALSE);stopifnot(identical(formals(a),formals(b)),identical(body(a),body(b)))}}
cluster_arg<-function(scope)if(scope=="cell-only")FALSE else clusters
tryCatch(withCallingHandlers({
 if(!requireNamespace("jsonlite",quietly=TRUE))stop("Required jsonlite is unavailable")
 p<-jsonlite::fromJSON("parameters.json",simplifyVector=FALSE);profile<-jsonlite::fromJSON("../source/SOURCE-PROFILE.json",simplifyVector=FALSE);edition<-profile$editions[[p$version]]
 stopifnot(as.character(getRversion())==profile$R_version,R.version$platform==profile$platform)
 for(name in names(edition$packages)){if(!requireNamespace(name,quietly=TRUE))stop(paste("Required package unavailable:",name));if(packageDescription(name)$Version!=edition$packages[[name]])stop(paste("Exact package version required:",name,edition$packages[[name]]))}
 suppressPackageStartupMessages({library(Matrix);library(SoupX)})
 guard_original<-new.env(parent=asNamespace("SoupX"));for(path in list.files("../source/original/R",pattern="[.]R$",full.names=TRUE))sys.source(path,guard_original,keep.source=FALSE)
 guard_names<-Filter(function(name)is.function(get(name,guard_original,inherits=FALSE)),ls(guard_original,all.names=TRUE));stopifnot(length(guard_names)>=10L);guard();runtime_ready<-TRUE;status<-"FAIL2_ORIGINAL_SOUPX_WORKFLOW_FAILED";current_stage<-"input"
 Sys.setlocale("LC_COLLATE","C");options(mc.cores=1);RNGkind("Mersenne-Twister","Inversion","Rejection")
 genes<-literal("../inputs/genes.tsv")[[1]];droplets<-literal("../inputs/droplets.tsv")[[1]];cells<-literal("../inputs/cells.tsv");cell_ids<-cells[[1]];clusters<-if(p$clusters=="supplied")setNames(cells$cluster,cell_ids) else NULL
 read_counts<-function(path,ids){
  if(p$matrix_format=="matrix-market")x<-methods::as(methods::as(Matrix::readMM(path),"generalMatrix"),"CsparseMatrix") else {frame<-literal(path);stopifnot(identical(frame[[1]],genes),identical(names(frame)[-1L],ids));x<-Matrix::Matrix(matrix(as.numeric(as.matrix(frame[,-1L,drop=FALSE])),nrow=length(genes)),sparse=TRUE);x<-methods::as(methods::as(x,"generalMatrix"),"CsparseMatrix")}
  stopifnot(identical(dim(x),as.integer(c(length(genes),length(ids)))));dimnames(x)<-list(genes,ids);x
 }
 tod<-read_counts("../inputs/raw-counts",droplets);toc<-read_counts("../inputs/filtered-counts",cell_ids);saveRDS(list(tod=tod,toc=toc,clusters=clusters),"input-transport.rds",version=3);sparse_out(tod,"constructed-raw.json");sparse_out(toc,"constructed-filtered.json")
 groups<-list();if(p$gene_sets_provided){frame<-literal("../inputs/gene-sets.tsv");group_names<-unique(frame$gene_set);groups<-setNames(lapply(group_names,function(g)frame$gene_id[frame$gene_set==g]),group_names)}
 supplied_mask<-NULL;if(p$mask_provided){frame<-literal("../inputs/nonexpression-mask.tsv");supplied_mask<-matrix(frame$non_expressing=="true",nrow=length(cell_ids),ncol=length(groups),byrow=TRUE,dimnames=list(cell_ids,names(groups)))}
 rho_by_cell<-NULL;if(p$rho_by_cell_provided){frame<-literal("../inputs/rho-by-cell.tsv");rho_by_cell<-setNames(as.numeric(frame$rho),frame$cell_id)}
 current_stage<-"soup-profile";soup_range<-unlist(p$soup_range,use.names=FALSE)
 if(p$version=="1.3.0")base<-SoupX::SoupChannel(tod,toc,soupRange=soup_range,keepDroplets=TRUE) else {base<-SoupX::SoupChannel(tod,toc,calcSoupProfile=FALSE);base<-SoupX::estimateSoup(base,soupRange=soup_range,keepDroplets=TRUE)}
 if(!is.null(clusters))base<-SoupX::setClusters(base,clusters)
 saveRDS(base,"constructed-channel.rds",version=3);table_out(data.frame(gene_id=rownames(base$soupProfile),base$soupProfile,check.names=FALSE),"soup-profile");table_out(data.frame(cell_id=rownames(base$metaData),base$metaData,check.names=FALSE),"constructed-metadata");stopifnot(all(is.finite(base$soupProfile$est)),sum(base$soupProfile$est)>0)
 dir.create("cases");current_stage<-"cases"
 for(a in p$analyses){
  target<-file.path("cases",a$id);dir.create(target);case_warnings<-case_messages<-character();case_stage<-"estimation";failure<-NULL;sc<-base;estimate_table<-NULL;use<-NULL;corrected<-NULL;set.seed(as.integer(a$seed));saveRDS(.Random.seed,file.path(target,"rng-before.rds"),version=3)
  result<-tryCatch(withCallingHandlers({
   e<-a$estimation
   if(e$kind=="fixed"){
    value<-if(e$rho_mode=="constant")e$rho else rho_by_cell
    sc<-if(p$version=="1.3.0")SoupX::setContaminationFraction(sc,value) else SoupX::setContaminationFraction(sc,value,forceAccept=e$force_accept)
   }else if(e$kind=="manual-global-poisson"){
    use<-if(e$mask_mode=="provided")supplied_mask else SoupX::estimateNonExpressingCells(sc,groups,clusters=cluster_arg(e$clustering_scope),maximumContamination=e$maximum_contamination,FDR=e$fdr)
    saveRDS(use,file.path(target,"nonexpression-mask-matrix.rds"),version=3);table_out(data.frame(cell_id=rownames(use),as.data.frame(use,check.names=FALSE),check.names=FALSE),file.path(target,"nonexpression-mask"))
    sc<-if(p$version=="1.3.0")SoupX::calculateContaminationFraction(sc,groups,use,verbose=FALSE,cellSpecificEstimates=e$cell_specific) else SoupX::calculateContaminationFraction(sc,groups,use,verbose=FALSE,forceAccept=e$force_accept)
   }else if(e$kind=="automatic-gamma"){
    sc<-SoupX::autoEstCont(sc,topMarkers=NULL,tfidfMin=e$tfidf_min,soupQuantile=e$soup_quantile,maxMarkers=as.integer(e$maximum_markers),contaminationRange=unlist(e$contamination_range,use.names=FALSE),rhoMaxFDR=e$rho_max_fdr,priorRho=e$prior_rho,priorRhoStdDev=e$prior_rho_sd,doPlot=FALSE,forceAccept=e$force_accept,verbose=FALSE)
   }else if(e$kind=="automatic-legacy-density"){
    d<-e$density;estimate_table<-SoupX::autoEstCont(sc,nMarks=as.integer(e$number_markers),tfidfMin=e$tfidf_min,soupMin=e$soup_min,rhoMax=e$rho_max,FDR=e$fdr,minCnts=e$minimum_counts,doPlot=FALSE,bw=d$bandwidth,adjust=d$adjust,kernel=d$kernel,n=as.integer(d$n),cut=d$cut,ext=d$ext,old.coords=d$old_coordinates)
    saveRDS(estimate_table,file.path(target,"legacy-auto-return.rds"),version=3);table_out(estimate_table,file.path(target,"legacy-auto-table"));stopifnot(is.data.frame(estimate_table))
    if(e$apply_density_mode){rho<-unique(estimate_table$rhoEst);stopifnot(length(rho)==1L,is.finite(rho));sc<-SoupX::setContaminationFraction(sc,rho)}
   }else stop("Unsupported closed estimator")
   case_stage<-"export-estimate";saveRDS(sc,file.path(target,"estimated-channel.rds"),version=3);table_out(data.frame(cell_id=rownames(sc$metaData),sc$metaData,check.names=FALSE),file.path(target,"metadata"))
   if(!is.null(sc$fit)){
    saveRDS(sc$fit,file.path(target,"original-fit.rds"),version=3)
    if(is(sc$fit,"glm")){f<-sc$fit;table_out(data.frame(observed=as.numeric(f$y),log_exposure=as.numeric(f$offset),fitted=as.numeric(f$fitted.values)),file.path(target,"glm-observations"));json(list(kind="original-global-Poisson-GLM",rho=token(exp(coef(f))[[1]]),converged=f$converged,iterations=f$iter,epsilon=f$control$epsilon,formula=paste(deparse(formula(f)),collapse="\n")),file.path(target,"fit-summary.json"))}
    else if(is.list(sc$fit)&&all(c("dd","posterior","rhoEst","markersUsed")%in%names(sc$fit))){table_out(sc$fit$dd,file.path(target,"auto-estimates"));table_out(sc$fit$markersUsed,file.path(target,"auto-markers"));table_out(data.frame(rho=seq(0,1,.001),posterior=sc$fit$posterior),file.path(target,"auto-posterior"));json(list(kind="original-current-gamma",rho=token(sc$fit$rhoEst),rho_FWHM=as.list(vapply(sc$fit$rhoFWHM,token,character(1))),prior_rho=token(sc$fit$priorRho),prior_rho_sd=token(sc$fit$priorRhoStdDev)),file.path(target,"fit-summary.json"))}
   }
   if(!is.null(a$adjustment)){
    case_stage<-"adjustment";d<-a$adjustment
    corrected<-SoupX::adjustCounts(sc,clusters=cluster_arg(d$clustering_scope),method=d$method,roundToInt=d$round_to_integer,verbose=0,tol=d$tolerance,pCut=d$p_cut)
    sparse_out(corrected,file.path(target,"corrected-counts.json"))
   }
   saveRDS(list(channel=sc,nonexpressing=use,legacy_estimate_table=estimate_table,corrected=corrected),file.path(target,"original-return.rds"),version=3);TRUE
  },warning=function(w){case_warnings<<-c(case_warnings,conditionMessage(w));cat("UPSTREAM WARNING:",conditionMessage(w),"\n",file=stderr());invokeRestart("muffleWarning")},message=function(m){case_messages<<-c(case_messages,conditionMessage(m));cat("UPSTREAM MESSAGE:",conditionMessage(m),"\n",file=stderr());invokeRestart("muffleMessage")}),error=function(err){failure<<-conditionMessage(err);FALSE})
  saveRDS(.Random.seed,file.path(target,"rng-after.rds"),version=3);guard();record<-list(id=a$id,returned=result,current_stage=case_stage,error=failure,warnings=as.list(case_warnings),messages=as.list(case_messages),estimator=e$kind,correction_requested=!is.null(a$adjustment),corrected_counts_produced=result&&!is.null(corrected),seed=a$seed,RNG=as.list(RNGkind()));json(record,file.path(target,"source-status.json"));case_reports[[length(case_reports)+1L]]<-record
 }
 guard();failed<-Filter(function(r)!isTRUE(r$returned),case_reports);if(length(failed))stop(paste(vapply(failed,function(r)paste0(r$id," [",r$current_stage,"]: ",r$error),character(1)),collapse="; "))
 status<-"completed-with-warnings";current_stage<-"complete"
},warning=function(w){warnings_seen<<-c(warnings_seen,conditionMessage(w));cat("UPSTREAM WARNING:",conditionMessage(w),"\n",file=stderr());invokeRestart("muffleWarning")},message=function(m){messages_seen<<-c(messages_seen,conditionMessage(m));cat("UPSTREAM MESSAGE:",conditionMessage(m),"\n",file=stderr());invokeRestart("muffleMessage")}),error=function(e){detail<<-conditionMessage(e);if(runtime_ready)status<<-"FAIL2_ORIGINAL_SOUPX_WORKFLOW_FAILED"})
if(requireNamespace("jsonlite",quietly=TRUE))json(list(status=status,detail=detail,current_stage=current_stage,warnings=as.list(warnings_seen),messages=as.list(messages_seen),cases=case_reports,guarded_functions=as.list(guard_names),source_bodies_and_formals_checked_before_and_after=identical(status,"completed-with-warnings"),R_version=as.character(getRversion()),platform=R.version$platform,required_action=action),"worker-report.json")
clean<-function(x)gsub("[\t\r\n]+"," ",x);writeLines(c("status\tdetail\trequired_action",paste(status,clean(detail),clean(action),sep="\t")),"worker-status.tsv",useBytes=TRUE)
quit(status=if(status=="completed-with-warnings")0L else if(status=="SKIP3_DEPENDENCY_UNAVAILABLE")3L else 2L,save="no")
