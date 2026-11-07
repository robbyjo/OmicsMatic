# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Private dependency installation only. No original library is updated.
args <- commandArgs(TRUE)
stopifnot(length(args) >= 7L)
lib <- args[[1]]; report <- args[[2]]; original <- args[[3]]
stopifnot(!dir.exists(lib), dir.create(lib))
.libPaths(c(lib,args[-(1:3)],.Library),include.site=FALSE)
options(timeout=180,HTTPUserAgent="Mozilla/5.0",Ncpus=1L)
dir.create(file.path(report,"downloads"))
warnings_seen <- character(); detail <- ""; status <- "SKIP3_CHIPSEEKER_REFERENCE_UNAVAILABLE"
installed_now <- list(); visiting <- character(); resolved <- character()
repositories <- c(BioCsoft="https://bioconductor.org/packages/3.23/bioc",
                  CRAN="https://cloud.r-project.org")
source_repositories <- c(repositories,BioCann="https://bioconductor.org/packages/3.23/data/annotation")
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
        if(!is.null(present) && satisfies(present,condition)) return(invisible(NULL))
        if(name %in% visiting) stop("Dependency cycle at ",name)
        visiting <<- c(visiting,name)
        candidates <- list()
        if(name %in% rownames(binary)) candidates[["win.binary"]] <- binary[name,,drop=FALSE]
        if(name %in% rownames(source)) candidates[["source"]] <- source[name,,drop=FALSE]
        chosen <- NULL; type <- NULL
        for(candidate_type in names(candidates)) {
            row <- candidates[[candidate_type]]
            if(!satisfies(row[1,"Version"],condition)) next
            if(candidate_type=="source" && !identical(unname(row[1,"NeedsCompilation"]),"no")) next
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
        download.file(url,destination,mode="wb",method="libcurl",quiet=FALSE)
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
    desc_dir <- file.path(report,"source-description")
    dir.create(desc_dir)
    untar(original,files="ChIPseeker/DESCRIPTION",exdir=desc_dir,tar="internal")
    desc <- read.dcf(file.path(desc_dir,"ChIPseeker","DESCRIPTION"))
    stopifnot(identical(unname(desc[1,"Package"]),"ChIPseeker"),identical(unname(desc[1,"Version"]),"1.48.1"),
              identical(unname(desc[1,"NeedsCompilation"]),"no"))
    for(field in c("Depends","Imports")) for(dep in requirements(desc[1,field])) ensure(dep$name,dep$condition)
    ensure("txdbmaker")
    install.packages(original,lib=lib,repos=NULL,type="source",dependencies=FALSE,INSTALL_opts="--no-multiarch")
    stopifnot(requireNamespace("ChIPseeker",quietly=TRUE),
              identical(as.character(packageVersion("ChIPseeker")),"1.48.1"))
    db <- get("TxDb.Hsapiens.UCSC.hg19.knownGene",asNamespace("TxDb.Hsapiens.UCSC.hg19.knownGene"))
    # annotatePeak uses gene-associated transcriptsBy, not every transcript()
    # row. The retained health-fixture-audit-1 captures the excluded chr1
    # transcript (-751) and the source-associated chr8 TSS (0) unchanged.
    tx <- get("getGene",asNamespace("ChIPseeker"))(db,by="transcript")
    eligible <- which(as.character(GenomicRanges::strand(tx))=="+" & BiocGenerics::start(tx)>10000L)
    stopifnot(length(eligible)>0L)
    first <- eligible[[1]]
    peak <- GenomicRanges::GRanges(seqnames=as.character(GenomicRanges::seqnames(tx)[first]),
        ranges=IRanges::IRanges(start=BiocGenerics::start(tx)[first],width=1L),strand="*")
    write.table(as.data.frame(tx[first]),file.path(report,"health-selected-feature.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
    health <- ChIPseeker::annotatePeak(peak,TxDb=db,level="transcript",verbose=FALSE)
    frame <- as.data.frame(health)
    write.table(frame,file.path(report,"health-annotation.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
    saveRDS(health,file.path(report,"health-annotation.rds"))
    stopifnot(nrow(frame)==1L,frame$distanceToTSS[[1]]==0L,startsWith(frame$annotation[[1]],"Promoter"))
    writeLines(capture.output(txdbmaker::makeTxDb),file.path(report,"loaded-makeTxDb-function.R"))
    writeLines(capture.output(ChIPseeker::annotatePeak),file.path(report,"loaded-annotatePeak-function.R"))
    status <- "INSTALLED_ORIGINAL_CHIPSEEKER_REFERENCE_ONLY"
},warning=function(w){warnings_seen <<- c(warnings_seen,conditionMessage(w))}),
error=function(e){detail <<- conditionMessage(e)})
ip <- installed.packages(lib.loc=.libPaths())
write.table(ip[,c("Package","Version","LibPath","Built"),drop=FALSE],file.path(report,"installed-packages.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
writeLines(capture.output(sessionInfo()),file.path(report,"session-info.txt"))
jsonlite::write_json(list(status=status,detail=detail,warnings=as.list(warnings_seen),
                         r_version=R.version.string,private_library=lib,
                         exact_ChIPseeker="1.48.1",analysis_admitted=FALSE),
                     file.path(report,"R-RESULT.json"),auto_unbox=TRUE,pretty=TRUE,null="null")
quit(status=if(status=="INSTALLED_ORIGINAL_CHIPSEEKER_REFERENCE_ONLY")0L else 3L)
