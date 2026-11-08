# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Install only the exact captured original package into the explicitly NEW library.
args <- commandArgs(TRUE)
stopifnot(length(args)>=6L)
lib <- args[[1]]; home <- args[[2]]; archive <- args[[3]]; source <- args[[4]]; version <- args[[5]]
stopifnot(!dir.exists(lib),dir.create(lib))
.libPaths(c(lib,args[-seq_len(5L)],.Library),include.site=FALSE)
options(timeout=120)
warnings_seen <- character(); detail <- ""; bodies <- list(); status <- "SKIP3_BIOMART_REFERENCE_UNAVAILABLE"
tryCatch(withCallingHandlers({
    install.packages(archive,lib=lib,repos=NULL,type="win.binary",dependencies=FALSE)
    stopifnot(requireNamespace("biomaRt",quietly=TRUE),identical(as.character(packageVersion("biomaRt")),version))
    stopifnot(identical(normalizePath(find.package("biomaRt"),winslash="/"),normalizePath(file.path(lib,"biomaRt"),winslash="/")))
    files <- list.files(file.path(source,"R"),pattern="\\.R$",full.names=TRUE)
    expressions <- unlist(lapply(files,function(p)as.list(parse(file=p))),recursive=FALSE)
    functions <- c("getBM","getLDS",".setResultColNames",".splitValues",".createFilterXMLchunk",".generateFilterXML",".processResults",".submitQueryXML")
    for(name in functions){
        matches <- Filter(function(e)is.call(e)&&length(e)==3L&&as.character(e[[1L]])%in%c("<-","=")&&identical(e[[2L]],as.name(name)),expressions)
        stopifnot(length(matches)==1L)
        expected <- eval(matches[[1L]][[3L]],new.env(parent=baseenv()))
        loaded <- getFromNamespace(name,"biomaRt")
        stopifnot(identical(body(expected),body(loaded),ignore.srcref=TRUE),identical(formals(expected),formals(loaded),ignore.srcref=TRUE))
        dput(loaded,file=file.path(home,paste0("loaded-",gsub("^\\.","internal-",name),".R")))
        bodies[[name]] <- list(body_matches_original_source=TRUE,formals_match_original_source=TRUE)
    }
    attributes <- data.frame(name=c("gene","symbol"),description=c("Gene ID","Gene symbol"),fullDescription=c("",""),page=c("feature_page","feature_page"),stringsAsFactors=FALSE)
    filters <- data.frame(name=c("gene","with_probe"),description=c("Gene IDs","Probe inclusion"),options=c("",""),fullDescription=c("",""),filters=c("",""),type=c("text","boolean_list"),operation=c("=","="),stringsAsFactors=FALSE)
    mart <- methods::new("Mart",biomart="synthetic_mart",host="http://127.0.0.1:1/martservice",dataset="synthetic_dataset",attributes=attributes,filters=filters)
    generator <- getFromNamespace(".generateFilterXML","biomaRt")
    text <- generator("gene",c("A","B"),mart)
    included <- generator("with_probe",TRUE,mart)
    excluded <- generator("with_probe",FALSE,mart)
    stopifnot(grepl('A,B',text[[1L]],fixed=TRUE),grepl('excluded = "0"',included[[1L]],fixed=TRUE),grepl('excluded = "1"',excluded[[1L]],fixed=TRUE))
    status <- "PASS_ORIGINAL_BIOMART_REFERENCE_PACKAGE_ONLY"
},warning=function(w){warnings_seen <<- c(warnings_seen,conditionMessage(w))}),error=function(e){detail <<- conditionMessage(e)})
ip <- installed.packages(lib.loc=.libPaths())
write.table(ip[,c("Package","Version","LibPath","Built"),drop=FALSE],file.path(home,"installed-packages.tsv"),sep="\t",quote=TRUE,row.names=FALSE)
writeLines(capture.output(sessionInfo()),file.path(home,"session-info.txt"))
dlls <- lapply(getLoadedDLLs(),function(d)list(name=d[["name"]],path=d[["path"]]))
jsonlite::write_json(list(status=status,error=detail,version=version,R=R.version.string,libraries=.libPaths(),function_bodies=bodies,loaded_DLLs=unname(dlls),warnings=unname(warnings_seen),gene_queries_executed=FALSE,implementation_credit=FALSE),file.path(home,"R-RESULT.json"),auto_unbox=TRUE,pretty=TRUE,null="null")
quit(status=if(status=="PASS_ORIGINAL_BIOMART_REFERENCE_PACKAGE_ONLY")0L else 3L)
