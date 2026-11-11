# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Typed original-package annotation only. No R expressions, installs or input RDS.
warnings_seen <- character(); messages_seen <- character(); ready <- FALSE
status <- "SKIP3_DEPENDENCY_UNAVAILABLE"; detail <- ""; error_record <- NULL; cases <- list(); guards <- character()
locale_before <- Sys.getlocale(); locale_after <- locale_before
action <- "Preserve this attempt and inspect worker-report.json, per-case warnings, stdout.log and stderr.log. Supply exact R4.6.1, the requested original ChIPseeker edition and declared dependency versions in permitted private libraries. If unavailable or installation is forbidden, skip the dependent analysis. Correct input/settings/capacity causes and retry into NEW output paths. An original error is not an empty result; do not drop queries, change directions or substitute modern labels to force success."
libraries <- readLines("runtime-paths.txt",encoding="UTF-8",warn=FALSE)
.libPaths(c(libraries,.Library),include.site=FALSE)
tokens <- function(value)vapply(value,function(x){
    if(is.nan(x))"NaN" else if(is.na(x))"NA" else if(is.infinite(x)){if(x>0)"Inf" else "-Inf"}
    else format(x,scientific=FALSE,trim=TRUE,digits=17)
},character(1))
kinds <- function(frame) lapply(frame,function(x)as.list(vapply(seq_along(x),function(i){
    v <- x[[i]]
    if(is.numeric(v)&&is.nan(v))"NaN" else if(is.na(v))"NA" else
    if(is.numeric(v)&&is.infinite(v)){if(v>0)"positive-infinity" else "negative-infinity"} else "value"
},character(1))))
write_json <- function(value,path,unbox=TRUE)jsonlite::write_json(value,path,pretty=TRUE,auto_unbox=unbox,
    dataframe="rows",na="string",null="null",digits=NA)
original_args <- function(case,peaks,db)list(peak=peaks,TxDb=db,tssRegion=as.integer(unlist(case$tss_region)),
    level=case$level,assignGenomicAnnotation=TRUE,genomicAnnotationPriority=as.character(unlist(case$annotation_priority)),
    annoDb=NULL,addFlankGeneInfo=case$add_flank_gene_info,flankDistance=case$flank_distance,
    sameStrand=case$same_strand,ignoreOverlap=case$ignore_overlap,ignoreUpstream=case$ignore_upstream,
    ignoreDownstream=case$ignore_downstream,overlap=case$overlap,verbose=FALSE)
tryCatch(withCallingHandlers({
    if(!requireNamespace("jsonlite",quietly=TRUE))stop("Required jsonlite package unavailable")
    p <- jsonlite::fromJSON("parameters.json",simplifyVector=FALSE)
    profile <- jsonlite::fromJSON("../source/SOURCE-PROFILE.json",simplifyVector=FALSE)
    # RSQLite normalizes the path against getwd(). Under C character encoding,
    # a Windows Unicode working path cannot be opened even with a relative
    # ASCII SQLite filename. Change only character encoding; numeric and
    # collation settings stay C. No input filename or ID is rewritten.
    ctype <- if(.Platform$OS.type=="windows")"English_United States.utf8" else "C.UTF-8"
    Sys.setlocale("LC_CTYPE",ctype)
    if(!isTRUE(l10n_info()[["UTF-8"]]))stop("Required UTF-8 character locale unavailable: ",ctype,". Skip dependent annotation; provision this locale only where permitted.")
    if(Sys.getlocale("LC_COLLATE")!="C"||Sys.getlocale("LC_NUMERIC")!="C")stop("Reference annotation requires C collation and numeric locale")
    locale_after <- Sys.getlocale()
    if(as.character(getRversion())!=profile$r_version)stop("Exact R version required: ",profile$r_version)
    if(!p$version %in% names(profile$editions))stop("Unsupported original ChIPseeker edition")
    for(name in names(profile$required_packages)) {
        if(!requireNamespace(name,quietly=TRUE)||unname(packageDescription(name,fields="Version"))!=profile$required_packages[[name]]||
           as.character(getNamespaceVersion(name))!=profile$required_packages[[name]])
            stop("Required exact dependency unavailable: ",name," ",profile$required_packages[[name]])
    }
    if(!requireNamespace("ChIPseeker",quietly=TRUE)||unname(packageDescription("ChIPseeker",fields="Version"))!=p$version||
       as.character(getNamespaceVersion("ChIPseeker"))!=p$version)
        stop("Exact original ChIPseeker edition unavailable: ",p$version)
    ns <- asNamespace("ChIPseeker"); definitions <- list()
    for(file in list.files(file.path("../source",p$version,"R"),pattern="[.]R$",full.names=TRUE))for(expression in parse(file,keep.source=FALSE)){
        if(is.call(expression)&&length(expression)==3L&&identical(expression[[1]],as.name("<-"))&&
            is.symbol(expression[[2]])&&is.call(expression[[3]])&&identical(expression[[3]][[1]],as.name("function")))
            definitions[[as.character(expression[[2]])]] <- expression[[3]]
    }
    names_to_guard <- c("annotatePeak","getNearestFeatureIndicesAndDistances","getGenomicAnnotation",
        "getGenomicAnnotation.internal","updateGenomicAnnotation","getGene",".ChIPseekerEnv","get_exonList",
        "get_intronList","TXID2EG","TXID2EGID","getFirstHitIndex","getAllFlankingGene","getGenomicAnnoStat",
        "loadTxDb","as.GRanges","as.data.frame.csAnno")
    if(!is.null(definitions[["get_env_genome"]]))names_to_guard <- c(names_to_guard,"get_env_genome")
    for(name in names_to_guard){
        if(is.null(definitions[[name]])||!exists(name,ns,inherits=FALSE))stop("Original function unavailable: ",name)
        original <- eval(definitions[[name]],envir=ns);loaded <- get(name,ns,inherits=FALSE)
        if(!identical(body(original),body(loaded))||!identical(formals(original),formals(loaded)))
            stop("Loaded original body/formals mismatch: ",name)
        guards <- c(guards,name)
    }
    ready <- TRUE
    input <- jsonlite::fromJSON("peaks.json",simplifyVector=FALSE)
    peaks <- GenomicRanges::GRanges(seqnames=vapply(input,`[[`,character(1),"contig"),
        ranges=IRanges::IRanges(start=vapply(input,function(x)as.integer(x$start),integer(1)),
                              end=vapply(input,function(x)as.integer(x$end),integer(1))),
        strand=vapply(input,`[[`,character(1),"strand"))
    S4Vectors::mcols(peaks)$input_ordinal <- seq_along(input)
    S4Vectors::mcols(peaks)$peak_id <- vapply(input,`[[`,character(1),"peak_id")
    db <- AnnotationDbi::loadDb("../inputs/txdb.sqlite")
    if(!methods::is(db,"TxDb"))stop("Only a caller-supplied TxDb SQLite database is admitted; no default genome/database substitution")
    write.table(AnnotationDbi::metadata(db),"txdb-metadata.tsv",sep="\t",quote=TRUE,row.names=FALSE,fileEncoding="UTF-8")
    write_json(as.data.frame(GenomeInfoDb::seqinfo(db)),"txdb-sequence-info.json")
    dir.create("cases")
    for(case in p$analyses){
        target <- file.path("cases",case$id);dir.create(target)
        case_warnings <- character();case_messages <- character();failure <- NULL
        returned <- tryCatch(withCallingHandlers({
            options(ChIPseeker.downstreamDistance=case$downstream_distance)
            features <- get("getGene",ns)(db,by=case$level)
            if(length(features)>p$maximum_features)stop("Declared maximum_features exceeded before annotation")
            value <- do.call(ChIPseeker::annotatePeak,original_args(case,peaks,db))
            nearest <- get("getNearestFeatureIndicesAndDistances",ns)(peaks,features,
                sameStrand=case$same_strand,ignoreOverlap=case$ignore_overlap,ignoreUpstream=case$ignore_upstream,
                ignoreDownstream=case$ignore_downstream,overlap=case$overlap)
            frame <- as.data.frame(value)
            if(!identical(as.integer(frame$input_ordinal),as.integer(S4Vectors::mcols(nearest$peak)$input_ordinal)))
                stop("Original annotatePeak and original nearest returned different ordered populations")
            if(!identical(tokens(frame$distanceToTSS),tokens(nearest$distance)))stop("Original annotation/nearest distance disagreement")
            saveRDS(value,file.path(target,"original-csAnno.rds"));saveRDS(nearest,file.path(target,"original-nearest.rds"))
            saveRDS(features,file.path(target,"original-ordered-features.rds"))
            write.table(frame,file.path(target,"original-annotations.tsv"),sep="\t",quote=TRUE,row.names=FALSE,na="NA",fileEncoding="UTF-8")
            write.table(value@detailGenomicAnnotation,file.path(target,"original-detail.tsv"),sep="\t",quote=TRUE,row.names=FALSE,na="NA",fileEncoding="UTF-8")
            write.table(value@annoStat,file.path(target,"original-distribution.tsv"),sep="\t",quote=TRUE,row.names=FALSE,na="NA",fileEncoding="UTF-8")
            write_json(list(annotations=frame,detail=value@detailGenomicAnnotation,distribution=value@annoStat,
                tss_region=value@tssRegion,level=value@level,peak_num=value@peakNum),file.path(target,"original-output.json"),FALSE)
            write_json(list(retained_input_ordinals=as.list(S4Vectors::mcols(nearest$peak)$input_ordinal),
                index_tokens=as.list(tokens(nearest$index)),distance_tokens=as.list(tokens(nearest$distance)),
                original_feature_names=as.list(names(features)),features=as.data.frame(features,row.names=seq_along(features))),
                file.path(target,"original-nearest.json"))
            write_json(list(annotations=kinds(frame),detail=kinds(value@detailGenomicAnnotation),distribution=kinds(value@annoStat),
                annotation_column_classes=lapply(frame,class)),file.path(target,"original-cell-kinds.json"))
            list(input_peaks=length(peaks),returned_rows=nrow(frame),features=length(features))
        },warning=function(w){case_warnings <<- c(case_warnings,conditionMessage(w));invokeRestart("muffleWarning")},
          message=function(m){case_messages <<- c(case_messages,conditionMessage(m));invokeRestart("muffleMessage")}),
        error=function(e){failure <<- list(class=class(e),message=conditionMessage(e),call=paste(deparse(conditionCall(e)),collapse="\n"));NULL})
        entry <- list(id=case$id,status=if(is.null(failure))"original-returned" else "FAIL2_ORIGINAL_ERROR",
            admission_authority="Java analysis-status.json; original source return alone is not admission",original_error=failure,
            warnings=as.list(case_warnings),messages=as.list(case_messages),population=returned)
        write_json(entry,file.path(target,"source-status.json"));cases[[length(cases)+1L]] <- entry
        # Persist after each case so cancellation never loses completed diagnostics.
        write_json(list(status="INCOMPLETE_NO_RESULT_ADMITTED",cases=cases),"worker-progress.json")
    }
    if(any(vapply(cases,function(x)!is.null(x$original_error),logical(1)))){
        status <- "FAIL2_ORIGINAL_ERROR";detail <- "At least one original annotation failed; this atomic batch admits no result. Inspect every case's source-status.json."
    }else{status <- "completed-with-warnings";detail <- "Original annotation returned; Java must validate captured identities and native audit before admission."}
    write_json(list(status="SOURCE_EXECUTION_TERMINAL_JAVA_ADMISSION_PENDING",cases=cases),"worker-progress.json")
},warning=function(w){warnings_seen <<- c(warnings_seen,conditionMessage(w));invokeRestart("muffleWarning")},
  message=function(m){messages_seen <<- c(messages_seen,conditionMessage(m));invokeRestart("muffleMessage")}),
error=function(e){status <<- if(ready)"FAIL2_ORIGINAL_ERROR" else "SKIP3_DEPENDENCY_UNAVAILABLE";detail <<- conditionMessage(e)
    error_record <<- list(class=class(e),message=conditionMessage(e),call=paste(deparse(conditionCall(e)),collapse="\n"))})
writeLines(capture.output(sessionInfo()),"session-info.txt")
if(requireNamespace("jsonlite",quietly=TRUE)){
    versions <- installed.packages(lib.loc=.libPaths())
    write.table(versions[,c("Package","Version","LibPath","Built"),drop=FALSE],"installed-packages.tsv",sep="\t",quote=TRUE,row.names=FALSE)
    dlls <- lapply(getLoadedDLLs(),function(x)list(name=x[["name"]],path=x[["path"]]))
    write_json(list(status=status,detail=detail,original_error=error_record,guarded_source_functions=as.list(guards),
        warnings=as.list(warnings_seen),messages=as.list(messages_seen),cases=cases,R=R.version.string,
        platform=R.version$platform,locale_before=locale_before,locale_after=locale_after,
        registered_R_DLLs=dlls,required_action=action,analysis_installs_software=FALSE,
        historical_dependency_environment_reproduced=FALSE,native_DLL_source_authentication=FALSE),"worker-report.json")
}
clean <- function(x)gsub("[\r\n\t]"," ",x)
writeLines(c("status\tdetail\trequired_action",paste(status,clean(detail),clean(action),sep="\t")),"worker-status.tsv",useBytes=TRUE)
quit(status=if(status=="completed-with-warnings")0L else if(status=="SKIP3_DEPENDENCY_UNAVAILABLE")3L else 2L)
