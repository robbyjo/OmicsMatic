# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Private CellChat dependency/package installation only. Original source and libraries remain unchanged.
args <- commandArgs(TRUE)
stopifnot(length(args) >= 9L)
lib <- args[[1]]; report <- args[[2]]; original <- args[[3]]
stopifnot(!dir.exists(lib), dir.create(lib))
.libPaths(c(lib,args[-(1:5)],.Library),include.site=FALSE)
overrides <- jsonlite::read_json(args[[4]],simplifyVector=FALSE)
options(timeout=180,HTTPUserAgent="Mozilla/5.0",Ncpus=1L)
dir.create(file.path(report,"downloads"))
warnings_seen <- character(); detail <- ""; status <- "SKIP3_ORIGINAL_CELLCHAT_EDITION_UNAVAILABLE"
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
    stopifnot(identical(unname(desc[1,"Package"]),"CellChat"),identical(unname(desc[1,"Version"]),args[[5]]))
    for(field in c("Depends","Imports","LinkingTo")) for(dep in requirements(desc[1,field])) ensure(dep$name,dep$condition)
    install.packages(original,lib=lib,repos=NULL,type="source",dependencies=FALSE,INSTALL_opts=c("--preclean","--clean","--no-multiarch"))
    stopifnot(requireNamespace("CellChat",quietly=TRUE),identical(unname(packageDescription("CellChat",fields="Version")),args[[5]]))
    future::plan(future::sequential)
    adjacency <- CellChat:::ComputeSNN(matrix(c(1,2,2,3,3,1),nrow=3,byrow=TRUE),0)
    stopifnot(identical(dim(adjacency),c(3L,3L)),max(abs(as.matrix(adjacency)-(diag(3)*2/3+matrix(1/3,3,3))))<1e-12)
    pruned <- CellChat:::ComputeSNN(matrix(c(1,2,2,3,3,1),nrow=3,byrow=TRUE),0.4)
    stopifnot(identical(as.matrix(pruned),diag(3)))
    expression <- matrix(seq_len(48L)/48,nrow=4L,dimnames=list(c("TGFB1","TGFBR1","TGFBR2","CD44"),paste0("cell",1:12)))
    meta <- data.frame(labels=factor(rep(c("A","B","C"),each=4L)),samples=factor(rep(c("s1","s2"),6L)),row.names=colnames(expression))
    constructor <- list(object=expression,meta=meta,group.by="labels")
    if(compareVersion(args[[5]],"2.0.0")>=0L) constructor$datatype <- "RNA"
    object <- do.call(CellChat::createCellChat,constructor)
    stopifnot(identical(rownames(object@data),rownames(expression)),identical(colnames(object@data),colnames(expression)),identical(levels(object@idents),c("A","B","C")))
    saveRDS(object,file.path(report,"health-original-cellchat.rds"))
    jsonlite::write_json(list(genes=nrow(object@data),cells=ncol(object@data),native_ComputeSNN_checked=TRUE,constructor_checked=TRUE,full_communication_workflow=FALSE),file.path(report,"health.json"),auto_unbox=TRUE,pretty=TRUE)
    status <- "INSTALLED_ORIGINAL_CELLCHAT_EDITION_ONLY"
},warning=function(w){warnings_seen <<- c(warnings_seen,conditionMessage(w))}),
error=function(e){detail <<- conditionMessage(e)})
ip <- installed.packages(lib.loc=.libPaths())
write.table(ip[,c("Package","Version","LibPath","Built"),drop=FALSE],file.path(report,"installed-packages.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
writeLines(capture.output(sessionInfo()),file.path(report,"session-info.txt"))
jsonlite::write_json(list(status=status,detail=detail,warnings=as.list(warnings_seen),
                         r_version=R.version.string,private_library=lib,
                         exact_CellChat=args[[5]],analysis_admitted=FALSE),
                     file.path(report,"R-RESULT.json"),auto_unbox=TRUE,pretty=TRUE,null="null")
quit(status=if(status=="INSTALLED_ORIGINAL_CELLCHAT_EDITION_ONLY")0L else 3L)
