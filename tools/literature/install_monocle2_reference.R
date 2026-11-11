# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Private Monocle2 dependency installation only. No original library is updated.
args <- commandArgs(TRUE)
stopifnot(length(args) >= 8L)
lib <- args[[1]]; report <- args[[2]]; original <- args[[3]]
stopifnot(!dir.exists(lib), dir.create(lib))
.libPaths(c(lib,args[-(1:4)],.Library),include.site=FALSE)
overrides <- jsonlite::read_json(args[[4]],simplifyVector=FALSE)
options(timeout=180,HTTPUserAgent="Mozilla/5.0",Ncpus=1L)
dir.create(file.path(report,"downloads"))
warnings_seen <- character(); detail <- ""; status <- "SKIP3_MONOCLE2_REFERENCE_UNAVAILABLE"
installed_now <- list(); visiting <- character(); resolved <- character()
repositories <- c(BioCsoft="https://bioconductor.org/packages/3.23/bioc",
                  CRAN="https://cloud.r-project.org")
source_repositories <- c(repositories,BioCann="https://bioconductor.org/packages/3.23/data/annotation",BioCexp="https://bioconductor.org/packages/3.23/data/experiment")
requirements <- function(text) {
    if (is.na(text) || !nzchar(text)) return(list())
    lapply(strsplit(text,",",fixed=TRUE)[[1]],function(piece) {
        piece <- trimws(piece)
        name <- sub("[[:space:](].*$","",piece)
        condition <- if(grepl("(",piece,fixed=TRUE)) sub("^.*\\((.*)\\).*$","\\1",piece) else ""
        list(name=name,condition=trimws(condition))
    })
}
satisfies <- function(version,condition) {
    if(!nzchar(condition)) return(TRUE)
    bits <- strsplit(condition,"[[:space:]]+")[[1]]
    if(length(bits)!=2L) stop("Unsupported dependency constraint: ",condition)
    cmp <- compareVersion(version,bits[[2]])
    switch(bits[[1]],">="=cmp>=0L,">"=cmp>0L,"<="=cmp<=0L,"<"=cmp<0L,"=="=cmp==0L,
           stop("Unsupported dependency operator: ",bits[[1]]))
}
tryCatch(withCallingHandlers({
    binary <- available.packages(contriburl=contrib.url(repositories,type="win.binary"),type="win.binary")
    source <- available.packages(contriburl=contrib.url(source_repositories,type="source"),type="source")
    write.table(binary,file.path(report,"available-binaries.tsv"),sep="\t",quote=TRUE,col.names=NA)
    write.table(source,file.path(report,"available-sources.tsv"),sep="\t",quote=TRUE,col.names=NA)
    ensure <- function(name,condition="") {
        if(name=="R") {
            if(!satisfies(as.character(getRversion()),condition)) stop("R dependency not met: ",condition)
            return(invisible(NULL))
        }
        present <- tryCatch(as.character(packageVersion(name)),error=function(e)NULL)
        if(!is.null(present) && satisfies(present,condition) && (is.null(overrides[[name]]) || !is.null(installed_now[[name]]))) return(invisible(NULL))
        if(name %in% visiting) stop("Dependency cycle at ",name)
        visiting <<- c(visiting,name)
        candidates <- list()
        if(name %in% rownames(binary)) candidates[["win.binary"]] <- binary[name,,drop=FALSE]
        if(name %in% rownames(source)) candidates[["source"]] <- source[name,,drop=FALSE]
        override <- overrides[[name]]
        if(!is.null(override)) {
            desc <- read.dcf(override$description)
            stopifnot(identical(unname(desc[1,"Package"]),name),identical(unname(desc[1,"Version"]),override$version))
            row <- matrix(NA_character_,nrow=1L,ncol=ncol(source),dimnames=list(name,colnames(source)))
            fields <- intersect(colnames(source),colnames(desc));row[1,fields] <- desc[1,fields]
            row[1,"Repository"] <- dirname(override$url)
            candidates <- list();candidates[[override$type]] <- row
        }
        chosen <- NULL; type <- NULL
        for(candidate_type in names(candidates)) {
            row <- candidates[[candidate_type]]
            if(!satisfies(row[1,"Version"],condition)) next
            chosen <- row; type <- candidate_type; break
        }
        if(is.null(chosen)) stop("No supported binary or declared non-compiling source for dependency ",name," ",condition)
        fields <- intersect(c("Depends","Imports","LinkingTo"),colnames(chosen))
        for(field in fields) for(dep in requirements(chosen[1,field])) ensure(dep$name,dep$condition)
        extension <- if(type=="source")".tar.gz" else ".zip"
        filename <- paste0(name,"_",chosen[1,"Version"],extension)
        destination <- file.path(report,"downloads",filename)
        url <- paste0(chosen[1,"Repository"],"/",filename)
        record <- list(package=name,version=chosen[1,"Version"],type=type,url=url,
                       repository_row=as.list(chosen[1,]),path=destination)
        jsonlite::write_json(record,file.path(report,"downloads",paste0(filename,".json")),auto_unbox=TRUE,pretty=TRUE,na="null")
        if(!is.null(override)) {stopifnot(file.copy(override$archive,destination));record$declared_author_override <- override} else download.file(url,destination,mode="wb",method="libcurl",quiet=FALSE)
        expected_md5 <- chosen[1,"MD5sum"]
        if(!is.na(expected_md5) && nzchar(expected_md5) && tools::md5sum(destination)!=expected_md5)
            stop("Repository MD5 mismatch: ",name)
        install.packages(destination,lib=lib,repos=NULL,type=type,dependencies=FALSE,
                         INSTALL_opts=if(type=="source")"--no-multiarch" else NULL)
        # DESCRIPTION preserves upstream spelling (e.g. 0.5-1); packageVersion
        # normalizes '-' to '.' and is used only for dependency comparisons.
        if(!identical(unname(packageDescription(name,fields="Version")),unname(chosen[1,"Version"]))) stop("Exact installed version mismatch: ",name)
        if(!requireNamespace(name,quietly=TRUE)) stop("Installed namespace does not load: ",name)
        installed_now[[name]] <<- record
        jsonlite::write_json(installed_now,file.path(report,"installed-during-setup.json"),auto_unbox=TRUE,pretty=TRUE,na="null")
        visiting <<- setdiff(visiting,name)
        resolved <<- c(resolved,name)
        invisible(NULL)
    }
    desc <- read.dcf(file.path(original,"DESCRIPTION"))
    stopifnot(identical(unname(desc[1,"Package"]),"monocle"),identical(unname(desc[1,"Version"]),"2.40.0"))
    for(field in c("Depends","Imports","LinkingTo")) for(dep in requirements(desc[1,field])) ensure(dep$name,dep$condition)
    install.packages(original,lib=lib,repos=NULL,type="source",dependencies=FALSE,INSTALL_opts="--no-multiarch")
    stopifnot(requireNamespace("monocle",quietly=TRUE),identical(as.character(packageVersion("monocle")),"2.40.0"))
    counts <- matrix(as.integer(seq_len(240L)%%11L+1L),nrow=12L,dimnames=list(paste0("gene-",1:12),paste0("cell-",1:20)))
    cds <- monocle::newCellDataSet(counts,
        phenoData=methods::new("AnnotatedDataFrame",data=data.frame(batch=rep(c("a","b"),each=10L),row.names=colnames(counts))),
        featureData=methods::new("AnnotatedDataFrame",data=data.frame(gene_short_name=rownames(counts),row.names=rownames(counts))),
        lowerDetectionLimit=0.1,expressionFamily=VGAM::negbinomial.size())
    # ExpressionSet dimensions carry Features/Samples names; verify values
    # separately without changing the object or either identifier axis.
    stopifnot(identical(unname(dim(cds)),dim(counts)),identical(as.numeric(Biobase::exprs(cds)),as.numeric(counts)),identical(rownames(cds),rownames(counts)),identical(colnames(cds),colnames(counts)))
    saveRDS(cds,file.path(report,"health-original-cell-data-set.rds"))
    jsonlite::write_json(list(genes=nrow(cds),cells=ncol(cds),source_dimension_names=as.list(names(dim(cds))),differential_test_performed=FALSE,analysis_admitted=FALSE),file.path(report,"health.json"),auto_unbox=TRUE,pretty=TRUE)
    status <- "INSTALLED_ORIGINAL_MONOCLE2_REFERENCE_ONLY"
},warning=function(w){warnings_seen <<- c(warnings_seen,conditionMessage(w))}),
error=function(e){detail <<- conditionMessage(e)})
ip <- installed.packages(lib.loc=.libPaths())
write.table(ip[,c("Package","Version","LibPath","Built"),drop=FALSE],file.path(report,"installed-packages.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
writeLines(capture.output(sessionInfo()),file.path(report,"session-info.txt"))
jsonlite::write_json(list(status=status,detail=detail,warnings=as.list(warnings_seen),analysis_admitted=FALSE,exact_Monocle2="2.40.0"),file.path(report,"R-RESULT.json"),auto_unbox=TRUE,pretty=TRUE)
quit(status=if(status=="INSTALLED_ORIGINAL_MONOCLE2_REFERENCE_ONLY")0L else 3L)
