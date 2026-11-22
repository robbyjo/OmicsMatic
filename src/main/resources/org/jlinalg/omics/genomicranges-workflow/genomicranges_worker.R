# Copyright (C) 2026 JLinAlg contributors
# SPDX-License-Identifier: GPL-3.0-or-later
# Calls original GenomicRanges functions; no function replacement or patching.
args <- commandArgs(TRUE)
stopifnot(length(args)==1L)
root <- normalizePath(args[1L],winslash="/",mustWork=TRUE)
work <- file.path(root,"worker")
options(warn=1)
hex <- function(s) paste(sprintf("%02x",as.integer(charToRaw(enc2utf8(s)))),collapse="")
unhex <- function(s) {
    if (!grepl("^([0-9a-f]{2})*$",s)) stop("Invalid literal UTF-8 hexadecimal transport")
    if (!nzchar(s)) return("")
    starts <- seq.int(1L,nchar(s),by=2L)
    ans <- rawToChar(as.raw(strtoi(substring(s,starts,starts+1L),16L)))
    Encoding(ans) <- "UTF-8";ans
}
table_out <- function(path,header,rows=character()) {
    con <- file(path,"wb");on.exit(close(con))
    writeBin(charToRaw(paste0(paste(c(header,rows),collapse="\n"),"\n")),con)
}
table_in <- function(path,header) {
    x <- readLines(path,warn=FALSE,encoding="UTF-8")
    if (!length(x)||!identical(x[1L],header)) stop("Invalid literal transport header: ",path)
    lapply(x[-1L],function(s) {
        fields <- strsplit(paste0(s,"\tEND"),"\t",fixed=TRUE)[[1L]]
        fields[-length(fields)]
    })
}
canonical <- function(x) paste(deparse(x,width.cutoff=500L,control="all"),collapse="\n")
unavailable <- function(s) stop(structure(list(message=s,call=NULL),class=c("genomicranges_unavailable","error","condition")))
check <- function(ok,message) if (!isTRUE(ok)) stop(message)
stage <- "setup";conditions <- list();calls <- 0L;checked <- 0L
query_n <- NA_integer_;subject_n <- NA_integer_;result_n <- NA_integer_;kind <- "NONE"
detail <- "Worker did not complete";status <- "FAIL2_NO_COMPLETE_RESULT"
observe <- function(c) {
    conditions[[length(conditions)+1L]] <<- list(stage=stage,classes=class(c),message=conditionMessage(c),call=conditionCall(c))
}
final <- function() {
    saveRDS(conditions,file.path(work,"conditions.rds"),version=2)
    table_out(file.path(work,"conditions.tsv"),"stage_hex\tclasses_hex\tmessage_hex\tcall_hex",vapply(conditions,function(c)paste(hex(c$stage),hex(paste(c$classes,collapse=",")),hex(c$message),hex(canonical(c$call)),sep="\t"),""))
    table_out(file.path(work,"status.tsv"),"status\tdetail_hex\tcalls_completed\tsource_functions_checked\tresult_kind\tresult_length\tquery_length\tsubject_length",
        paste(status,hex(detail),calls,checked,kind,result_n,query_n,subject_n,sep="\t"))
}
tryCatch(withCallingHandlers({
    control_rows <- table_in(file.path(root,"control.tsv"),"key\tvalue_hex")
    check(all(vapply(control_rows,length,0L)==2L),"Invalid control table width")
    keys <- vapply(control_rows,`[[`,"",1L);check(!anyDuplicated(keys),"Duplicate control key")
    control <- setNames(lapply(control_rows,function(r)unhex(r[2L])),keys)
    expected_keys <- c("operation","profile","r_version","package_version","subject_mode","maximum_rows","maximum_pairs","ready_timeout_seconds")
    check(setequal(keys,expected_keys),"Unknown/missing control keys")
    stage <- "dependencies"
    if (.Platform$OS.type!="windows"||.Machine$sizeof.pointer!=8L||!grepl("^x86_64-w64-mingw32",R.version$platform)) unavailable("This profile requires qualified Windows x86_64;no platform substitution")
    if (!identical(as.character(getRversion()),control$r_version)) unavailable("Wrong R engine; supply the exact declared profile Rscript")
    library_rows <- table_in(file.path(root,"libraries.tsv"),"path_hex")
    check(length(library_rows)>0L&&all(vapply(library_rows,length,0L)==1L),"Invalid library paths")
    libraries <- vapply(library_rows,function(r)unhex(r[1L]),"")
    if (!all(dir.exists(libraries))) unavailable("Missing declared private R library; install the exact allowed profile and retry NEW output")
    .libPaths(c(normalizePath(libraries,winslash="/",mustWork=TRUE),.Library))
    package_rows <- table_in(file.path(root,"source","packages.tsv"),"package\tversion")
    check(all(vapply(package_rows,length,0L)==2L),"Invalid packaged dependency profile")
    for (r in package_rows) {
        if (!requireNamespace(r[1L],quietly=TRUE)) unavailable(paste("Required permitted package is unavailable:",r[1L],r[2L]))
        if (!identical(unname(packageDescription(r[1L],fields="Version")),r[2L])) unavailable(paste("Wrong package edition; supply",r[1L],r[2L]))
    }
    library(GenomicRanges)
    if (!identical(as.character(packageVersion("GenomicRanges")),control$package_version)) unavailable("Wrong GenomicRanges edition; no nearby-version substitution")
    stage <- "source"
    ns <- asNamespace("GenomicRanges")
    source_rows <- table_in(file.path(root,"source","source-functions.tsv"),"name_hex\tformals_hex\tbody_hex")
    check(length(source_rows)==160L&&all(vapply(source_rows,length,0L)==3L),"Changed source reference population")
    for (r in source_rows) {
        name <- unhex(r[1L]);check(exists(name,ns,inherits=FALSE),paste("Missing original source function:",name))
        fun <- get(name,ns,inherits=FALSE)
        check(is.function(fun)&&identical(hex(canonical(formals(fun))),r[2L])&&identical(hex(canonical(body(fun))),r[3L]),paste("Changed original source function:",name))
        checked <- checked+1L
    }
    # The Java caller fingerprints these resolved files before ACK, then again
    # after exit. This is identity/integrity evidence, not binary attestation.
    loaded <- sort(loadedNamespaces())
    paths <- vapply(loaded,function(p)normalizePath(system.file(package=p),winslash="/",mustWork=TRUE),"")
    table_out(file.path(work,"runtime-packages.tsv"),"package\tversion\tpath_hex",vapply(loaded,function(p)paste(p,unname(packageDescription(p,fields="Version")),hex(paths[[p]]),sep="\t"),""))
    dlls <- getLoadedDLLs()
    table_out(file.path(work,"runtime-dlls.tsv"),"name_hex\tpath_hex",vapply(names(dlls),function(n)paste(hex(n),hex(dlls[[n]][["path"]]),sep="\t"),""))
    saveRDS(list(session=sessionInfo(),packages=lapply(loaded,packageDescription),library_paths=.libPaths(),source_functions_checked=checked,profile=control$profile),file.path(work,"runtime.rds"),version=2)
    table_out(file.path(work,"READY.pending"),"GENOMICRANGES_PREFLIGHT_V1",control$profile)
    check(file.rename(file.path(work,"READY.pending"),file.path(work,"READY")),"Could not atomically publish runtime-ready marker")
    wait <- as.double(control$ready_timeout_seconds);check(is.finite(wait)&&wait>=1&&wait<=3600,"Invalid ACK wait capacity")
    deadline <- proc.time()[["elapsed"]]+wait
    while (!file.exists(file.path(work,"ACK"))) {
        if (proc.time()[["elapsed"]]>deadline) stop("Runtime fingerprint ACK timed out; no operation called")
        Sys.sleep(0.025)
    }
    check(identical(readLines(file.path(work,"ACK"),warn=FALSE),c("GENOMICRANGES_ACK_V1",control$profile)),"Changed runtime ACK")
    stage <- "input"
    objects <- readRDS(file.path(root,"inputs","objects.rds"))
    binary <- control$operation%in%c("find-overlaps","subset-overlaps","nearest","distance-to-nearest")
    check(control$subject_mode%in%c("provided","self","unused"),"Invalid explicit subject mode")
    check(binary||control$subject_mode=="unused","Unary operation cannot ignore a supplied subject")
    check(!binary||control$subject_mode!="unused","Binary operation requires explicit provided/self mode")
    expected <- if(binary&&control$subject_mode=="provided")c("query","subject") else "query"
    check(is.list(objects)&&identical(names(objects),expected),"Input RDS requires exactly ordered query/subject fields for the declared mode")
    for (obj in objects) {
        check(identical(class(obj),structure("GRanges",package="GenomicRanges")),"Require original GRanges objects; no implicit coercion or metadata repair")
        validObject(obj)
    }
    q <- objects$query;s <- if(control$subject_mode=="provided")objects$subject else q
    query_n <- length(q);subject_n <- if(binary)length(s) else 0L
    max_rows <- as.double(control$maximum_rows);max_pairs <- as.double(control$maximum_pairs)
    check(is.finite(max_rows)&&max_rows>=0&&max_rows==floor(max_rows)&&max_rows<=2147483647,"Invalid row capacity")
    check(is.finite(max_pairs)&&max_pairs>=0&&max_pairs==floor(max_pairs)&&max_pairs<=2^53,"Invalid candidate-pair capacity")
    check(query_n<=max_rows&&subject_n<=max_rows,"Input object exceeds declared row capacity")
    check(!binary||as.double(query_n)*as.double(subject_n)<=max_pairs,"Candidate-pair capacity exceeded before original operation")
    option_rows <- table_in(file.path(root,"options.tsv"),"key\ttype\tvalue_hex")
    check(all(vapply(option_rows,length,0L)==3L),"Invalid option table width")
    option_names <- vapply(option_rows,`[[`,"",1L);check(!anyDuplicated(option_names),"Duplicate operation option")
    opts <- setNames(lapply(option_rows,function(r) {
        value <- unhex(r[3L])
        if(r[2L]=="boolean") {check(value%in%c("TRUE","FALSE"),"Invalid literal boolean");return(value=="TRUE")}
        if(r[2L]=="string") return(value)
        if(r[2L]=="numeric") {
            if(value=="") return(numeric())
            cells <- strsplit(value,",",fixed=TRUE)[[1L]]
            check(all(grepl("^[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][+-]?[0-9]+)?$",cells)),"Invalid finite numeric option transport")
            values <- as.double(cells)
            check(all(is.finite(values)),"Nonfinite numeric option transport;no repair")
            return(values)
        }
        check(r[2L]=="integer","Unknown option type")
        if(value=="") return(integer())
        cells <- strsplit(value,",",fixed=TRUE)[[1L]]
        check(all(grepl("^(0|-?[1-9][0-9]*)$",cells)),"Invalid literal R integer vector")
        values <- as.double(cells)
        check(all(is.finite(values)&values>=-2147483647&values<=2147483647),"Option exceeds nonmissing R integer capacity")
        as.integer(values)
    }),option_names)
    schemas <- list(
        "find-overlaps"=c("maxgap","minoverlap","type","select","ignore.strand"),
        "subset-overlaps"=c("maxgap","minoverlap","type","ignore.strand","invert"),
        "nearest"=c("select","ignore.strand"),"distance-to-nearest"=c("select","ignore.strand"),
        "promoters"=c("upstream","downstream","use.names"),"out-of-bounds"=character(),
        "reduce"=c("drop.empty.ranges","min.gapwidth","with.revmap","with.inframe.attrib","ignore.strand"),
        "disjoin"=c("with.revmap","ignore.strand"))
    check(control$operation%in%names(schemas)&&setequal(names(opts),schemas[[control$operation]]),"Unknown operation or missing/extra explicit options")
    names_map <- c("find-overlaps"="findOverlaps","subset-overlaps"="subsetByOverlaps","nearest"="nearest","distance-to-nearest"="distanceToNearest","promoters"="promoters","out-of-bounds"="get_out_of_bound_index","reduce"="reduce","disjoin"="disjoin")
    fun <- get(names_map[[control$operation]],ns,inherits=TRUE)
    call_args <- list(q)
    if(binary&&control$subject_mode=="provided") call_args <- c(call_args,list(s))
    stage <- "operation"
    result <- do.call(fun,c(call_args,opts));calls <- calls+1L
    stage <- "output"
    result_n <- length(result)
    check(result_n<=max_rows,"Original result exceeds row capacity; raw partial attempt remains unadmitted")
    output <- file.path(work,"raw-result");dir.create(output)
    saveRDS(result,file.path(output,"result.rds"),version=2)
    check(identical(result,readRDS(file.path(output,"result.rds"))),"Serialized result is not identical to the original result")
    if (is(result,"Hits")) {
        validObject(result);kind <- "HITS"
        check(queryLength(result)==query_n&&subjectLength(result)==subject_n,"Original hit node counts differ from input")
        check(all(queryHits(result)>=1L&queryHits(result)<=query_n)&all(subjectHits(result)>=1L&subjectHits(result)<=subject_n),"Original hit indices outside source object")
        distance <- if("distance"%in%names(mcols(result)))mcols(result)$distance else rep(NA_integer_,result_n)
        check(is.integer(distance)&&length(distance)==result_n,"Unexpected source distance column type/length")
        table_out(file.path(output,"rows.tsv"),"row\tquery_index\tsubject_index\tdistance",vapply(seq_len(result_n),function(i)paste(i,queryHits(result)[i],subjectHits(result)[i],distance[i],sep="\t"),""))
    } else if (is(result,"GRanges")) {
        validObject(result);kind <- "GRANGES"
        rn <- names(result)
        table_out(file.path(output,"rows.tsv"),"row\tseqname_hex\tstart\tend\twidth\tstrand\tname_state\tname_hex",vapply(seq_len(result_n),function(i)paste(i,hex(as.character(seqnames(result))[i]),start(result)[i],end(result)[i],width(result)[i],as.character(strand(result))[i],if(is.null(rn))"NULL" else if(is.na(rn[i]))"NA" else "VALUE",if(is.null(rn)||is.na(rn[i]))"-" else hex(rn[i]),sep="\t"),""))
        si <- seqinfo(result);lev <- seqlevels(si)
        table_out(file.path(output,"sequences.tsv"),"seqname_hex\tlength\tcircular\tgenome_state\tgenome_hex",vapply(seq_along(lev),function(i)paste(hex(lev[i]),seqlengths(si)[i],isCircular(si)[i],if(is.na(genome(si)[i]))"NA" else "VALUE",if(is.na(genome(si)[i]))"-" else hex(genome(si)[i]),sep="\t"),""))
    } else {
        logical_selection <- is.logical(result)&&all(is.na(result))&&control$operation=="nearest"&&length(s)==0L
        check(is.integer(result)||logical_selection,"Unexpected original result type")
        kind <- if(control$operation=="out-of-bounds")"INTEGER_INDICES" else if(logical_selection)"LOGICAL_SELECTION" else "INTEGER_SELECTION"
        if(kind=="INTEGER_INDICES") check(!anyNA(result)&&all(result>=1L&result<=query_n)&&!anyDuplicated(result),"Unexpected original out-of-bound index result")
        else check(result_n==query_n&&all(is.na(result)|result>=1L&result<=subject_n),"Unexpected original nearest/overlap selection vector")
        table_out(file.path(output,"rows.tsv"),"row\tindex",vapply(seq_len(result_n),function(i)paste(i,result[i],sep="\t"),""))
    }
    status <- "COMPLETED_WITH_WARNINGS";detail <- "Original operation completed; full result.rds preserves metadata/seqinfo. TSV is a core-field view. Inspect conditions and dependency/runtime profile; Java admission is separate."
},warning=observe,message=observe),error=function(e) {
    observe(e);status <<- if(inherits(e,"genomicranges_unavailable"))"SKIP3_DEPENDENT_ANALYSIS_SKIPPED" else "FAIL2_NO_COMPLETE_RESULT"
    detail <<- conditionMessage(e)
})
final()
if(status=="SKIP3_DEPENDENT_ANALYSIS_SKIPPED") quit(status=3L)
if(status!="COMPLETED_WITH_WARNINGS") quit(status=2L)
