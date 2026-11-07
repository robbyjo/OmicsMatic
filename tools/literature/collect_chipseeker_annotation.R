# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Execute the installed original package; no replacement of original function bodies.
args <- commandArgs(TRUE)
stopifnot(length(args)==4L)
plan_path <- args[[1]]; fixture <- args[[2]]; out <- args[[3]]
.libPaths(c(strsplit(args[[4]],.Platform$path.sep,fixed=TRUE)[[1]],.Library),include.site=FALSE)
plan <- jsonlite::fromJSON(plan_path,simplifyVector=FALSE)
if(!requireNamespace("ChIPseeker",quietly=TRUE) || as.character(packageVersion("ChIPseeker"))!=plan$version)
    stop("Exact original ChIPseeker edition cannot load; skip dependent annotation")
ns <- asNamespace("ChIPseeker")
definitions <- list()
for(file in list.files(plan$source_R,pattern="[.]R$",full.names=TRUE)) for(expression in parse(file,keep.source=FALSE)) {
    if(is.call(expression)&&length(expression)==3L&&identical(expression[[1]],as.name("<-"))&&
       is.symbol(expression[[2]])&&is.call(expression[[3]])&&identical(expression[[3]][[1]],as.name("function")))
        definitions[[as.character(expression[[2]])]] <- expression[[3]]
}
guard_names <- c("annotatePeak","getNearestFeatureIndicesAndDistances","getGenomicAnnotation",
    "getGenomicAnnotation.internal","updateGenomicAnnotation","getGene",".ChIPseekerEnv",
    "get_exonList","get_intronList","TXID2EG","TXID2EGID","getFirstHitIndex","getAllFlankingGene",
    "getGenomicAnnoStat","loadTxDb")
for(name in guard_names) {
    stopifnot(!is.null(definitions[[name]]),exists(name,envir=ns,inherits=FALSE))
    ref <- eval(definitions[[name]],envir=ns); loaded <- get(name,envir=ns,inherits=FALSE)
    if(!identical(body(ref),body(loaded))||!identical(formals(ref),formals(loaded)))
        stop("Loaded original body/formals differ for ",name)
}
db <- AnnotationDbi::loadDb(file.path(fixture,"synthetic-txdb.sqlite"))
table <- read.delim(file.path(fixture,"peaks.tsv"),check.names=FALSE,colClasses=c("character","character","integer","integer"))
peaks <- GenomicRanges::GRanges(seqnames=table$contig,ranges=IRanges::IRanges(table$start,table$end),strand="*")
S4Vectors::mcols(peaks)$input_ordinal <- seq_len(nrow(table))
S4Vectors::mcols(peaks)$peak_id <- table$peak_id
cases <- list()
tokens <- function(value) vapply(value,function(x) {
    if(is.na(x)){if(is.nan(x))"NaN" else "NA"} else if(is.infinite(x)){if(x>0)"Inf" else "-Inf"}
    else format(x,scientific=FALSE,trim=TRUE,digits=17)
},character(1))
for(case in plan$cases) {
    warnings <- character();messages <- character();error <- NULL
    nearest_warnings <- character()
    value <- tryCatch(withCallingHandlers({
        options(ChIPseeker.downstreamDistance=case$downstream_distance)
        ChIPseeker::annotatePeak(peaks,TxDb=db,tssRegion=as.integer(unlist(case$tss_region)),
            level=case$level,assignGenomicAnnotation=TRUE,
            genomicAnnotationPriority=as.character(unlist(case$priority)),annoDb=NULL,
            addFlankGeneInfo=case$add_flank_gene_info,flankDistance=case$flank_distance,
            sameStrand=FALSE,ignoreOverlap=case$ignore_overlap,ignoreUpstream=case$ignore_upstream,
            ignoreDownstream=case$ignore_downstream,overlap=case$overlap,verbose=FALSE)
    },warning=function(w){warnings <<- c(warnings,conditionMessage(w));invokeRestart("muffleWarning")},
      message=function(m){messages <<- c(messages,conditionMessage(m));invokeRestart("muffleMessage")}),
    error=function(e){error <<- list(class=class(e),message=conditionMessage(e));NULL})
    if(!is.null(value)) {
        target <- file.path(out,case$id);dir.create(target)
        saveRDS(value,file.path(target,"original-csAnno.rds"))
        write.table(as.data.frame(value),file.path(target,"original-annotations.tsv"),sep="\t",quote=TRUE,row.names=FALSE,na="NA",fileEncoding="UTF-8")
        write.table(value@detailGenomicAnnotation,file.path(target,"original-detail.tsv"),sep="\t",quote=TRUE,row.names=FALSE,na="NA")
        write.table(value@annoStat,file.path(target,"original-distribution.tsv"),sep="\t",quote=TRUE,row.names=FALSE,na="NA")
        jsonlite::write_json(list(annotations=as.data.frame(value),detail=value@detailGenomicAnnotation,
            distribution=value@annoStat,tss_region=value@tssRegion,level=value@level,peak_num=value@peakNum),
            file.path(target,"original-output.json"),dataframe="rows",auto_unbox=FALSE,pretty=TRUE,na="string",null="null",digits=NA)
        features <- get("getGene",ns)(db,by=case$level)
        nearest <- withCallingHandlers(get("getNearestFeatureIndicesAndDistances",ns)(peaks,features,sameStrand=FALSE,
            ignoreOverlap=case$ignore_overlap,ignoreUpstream=case$ignore_upstream,
            ignoreDownstream=case$ignore_downstream,overlap=case$overlap),
            warning=function(w){nearest_warnings <<- c(nearest_warnings,conditionMessage(w));invokeRestart("muffleWarning")})
        # These tables expose exact TxDb extraction/order for a separate native
        # component comparison. Transport IDs are ordinals, not inferred genes.
        prepared_features <- data.frame(feature_id=paste0("source-feature-",seq_along(features)),
            contig=as.character(GenomicRanges::seqnames(features)),start=BiocGenerics::start(features),
            end=BiocGenerics::end(features),strand=as.character(GenomicRanges::strand(features)))
        write.table(prepared_features,file.path(target,"prepared-features.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
        saveRDS(features,file.path(target,"original-ordered-features.rds"))
        saveRDS(nearest,file.path(target,"original-nearest.rds"))
        jsonlite::write_json(list(retained_input_ordinals=as.list(S4Vectors::mcols(nearest$peak)$input_ordinal),
            index_tokens=as.list(tokens(nearest$index)),distance_tokens=as.list(tokens(nearest$distance)),
            original_feature_names=as.list(names(features)),
            features=as.data.frame(features,row.names=seq_along(features))),file.path(target,"original-nearest.json"),
            dataframe="rows",auto_unbox=TRUE,pretty=TRUE,na="string",null="null",digits=NA)
    }
    cases[[length(cases)+1L]] <- list(id=case$id,source_error=error,warnings=as.list(warnings),
        additional_nearest_warnings=as.list(nearest_warnings),messages=as.list(messages),
        input_peaks=length(peaks),returned_rows=if(is.null(value))NULL else nrow(as.data.frame(value)))
}
versions <- installed.packages(lib.loc=.libPaths())
write.table(versions[,c("Package","Version","LibPath","Built"),drop=FALSE],file.path(out,"installed-packages.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"))
jsonlite::write_json(list(status="original-package-annotation-controls-not-adapter-admission",version=plan$version,
    guarded_source_functions=as.list(guard_names),cases=cases,historical_dependency_environment=FALSE),
    file.path(out,"ORIGINAL.json"),auto_unbox=TRUE,pretty=TRUE,null="null")
warning_lines <- unlist(lapply(cases,function(x)c(paste0("Case: ",x$id),
    if(!is.null(x$source_error))paste0("ORIGINAL ERROR: ",x$source_error$message),
    unlist(x$warnings),unlist(x$additional_nearest_warnings),"")))
writeLines(c("# ORIGINAL ANNOTATION CONTROL WARNINGS", "",
    "Reference controls only. Original warnings and errors are retained; returned rows do not admit a project adapter or biological conclusion.",
    "Required action: inspect the named case and raw original outputs. Do not repair coordinates, substitute settings or relabel missing/dummy states to force agreement.",
    "",warning_lines),file.path(out,"WARNINGS.md"),useBytes=TRUE)
