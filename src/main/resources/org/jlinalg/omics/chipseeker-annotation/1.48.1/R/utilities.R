#' @title env function for ChIPseeker
#' @param TxDb txdb object
#' @param item item name
#' @param force force to update txdb item in cache or not.
#' @importFrom yulab.utils get_cache_item
#' @importFrom yulab.utils update_cache_item
#' @importFrom yulab.utils rm_cache_item
#' @importFrom yulab.utils initial_cache_item
#' @importFrom S4Vectors metadata
.ChIPseekerEnv <- function(TxDb, item = "ChIPseekerEnv", force = FALSE) {
    
    # get cache item
    # it will create a list if there is no a cache item
    cache_item <- get_cache_item(item)

    # if there is no TXDB cached, write in cache
    if (is.null(cache_item$TXDB)) {
        update_cache_item(item = item, list(TXDB = TxDb))
        cat(">> Using Genome:", get_env_genome(),"...\n")
        return(invisible(NULL))
    }

    # force to update item
    if(force){
        cat(">> Force to update txdb in cache...\n")
        rm_cache_item(item)           
        initial_cache_item(item)      
        update_cache_item(item, list(TXDB = TxDb))  
        cat(">> Using Genome:", get_env_genome(),"...\n")
    }

    # if exist TXDB
    TXDB <- cache_item$TXDB
    m1 <- tryCatch(unlist(metadata(TXDB)), error = function(e) NULL)
    m2 <- tryCatch(unlist(metadata(TxDb)),  error = function(e) NULL)
    if (!is.null(m1)) m1 <- m1[!is.na(m1)]
    if (!is.null(m2)) m2 <- m2[!is.na(m2)]

    txdb_flag <- is.character(all.equal(TXDB, TxDb))

    if (is.null(m1) || is.null(m2) || length(m1) != length(m2) || any(m1 != m2) || txdb_flag) {
        cat(">> Update txdb in cache...\n")
        rm_cache_item(item)           
        initial_cache_item(item)      
        update_cache_item(item, list(TXDB = TxDb))  
    }

    cat(">> Using Genome:", get_env_genome(),"...\n")

    invisible(NULL)

    # pos <- 1
    # envir <- as.environment(pos)
    # if (!exists("ChIPseekerEnv", envir=.GlobalEnv)) {
    #     assign("ChIPseekerEnv", new.env(), envir = envir)
    # }

    # ChIPseekerEnv <- get("ChIPseekerEnv", envir=.GlobalEnv)
    # if (!exists("TXDB", envir=ChIPseekerEnv, inherits=FALSE)) {
    #     ## first run
    #     assign("TXDB", TxDb, envir=ChIPseekerEnv)
    # } else {
    #     TXDB <- get("TXDB", envir=ChIPseekerEnv)
    #     m1 <- tryCatch(unlist(metadata(TXDB)), error=function(e) NULL)

    #     m2 <- unlist(metadata(TxDb))

    #     if (!is.null(m1)) {
    #         m1 <- m1[!is.na(m1)]
    #     }
    #     m2 <- m2[!is.na(m2)]

    #     if ( is.null(m1) || length(m1) != length(m2) || any(m1 != m2) ) {
    #         rm(ChIPseekerEnv)
    #         assign("ChIPseekerEnv", new.env(), envir = envir)
    #         ChIPseekerEnv <- get("ChIPseekerEnv", envir=.GlobalEnv)
    #         assign("TXDB", TxDb, envir=ChIPseekerEnv)
    #     }
    # }
}


##' @importFrom GenomicFeatures exonsBy
##' @importFrom yulab.utils get_cache_element
##' @importFrom yulab.utils update_cache_item
get_exonList <- function(item = "ChIPseekerEnv") {
    # TxDb <- get("TXDB", envir=ChIPseekerEnv)
    TxDb <- get_cache_element(item = item, elements = "TXDB")

    exonList <- get_cache_element(item = item, elements = "exonList")

    if(is.null(exonList)){
        exonList <- exonsBy(TxDb)
        update_cache_item(item = item, list("exonList" = exonList))
    }

    # if ( exists("exonList", envir=ChIPseekerEnv, inherits=FALSE) ) {
    #     exonList <- get("exonList", envir=ChIPseekerEnv)
    # } else {
    #     exonList <- exonsBy(TxDb)
    #     assign("exonList", exonList, envir=ChIPseekerEnv)
    # }
    return(exonList)
}

##' @importFrom GenomicFeatures intronsByTranscript
##' @importFrom yulab.utils get_cache_element
##' @importFrom yulab.utils update_cache_item
get_intronList <- function(item = "ChIPseekerEnv") {

    # TxDb <- get("TXDB", envir=ChIPseekerEnv)
    TxDb <- get_cache_element(item = item, elements = "TXDB")

    intronList <- get_cache_element(item = item, elements = "intronList")

    if(is.null(intronList)){
        intronList <- intronsByTranscript(TxDb)
        update_cache_item(item = item, list("intronList" = intronList))
    }

    # if ( exists("intronList", envir=ChIPseekerEnv, inherits=FALSE) ) {
    #     intronList <- get("intronList", envir=ChIPseekerEnv)
    # } else {
    #     intronList <- intronsByTranscript(TxDb)
    #     assign("intronList", intronList, envir=ChIPseekerEnv)
    # }
    return(intronList)
}


getCols <- function(n) {
    col <- c("#8dd3c7", "#ffffb3", "#bebada",
             "#fb8072", "#80b1d3", "#fdb462",
             "#b3de69", "#fccde5", "#d9d9d9",
             "#bc80bd", "#ccebc5", "#ffed6f")

    col2 <- c("#1f78b4", "#ffff33", "#c2a5cf",
             "#ff7f00", "#810f7c", "#a6cee3",
             "#006d2c", "#4d4d4d", "#8c510a",
             "#d73027", "#78c679", "#7f0000",
             "#41b6c4", "#e7298a", "#54278f")

    col3 <- c("#a6cee3", "#1f78b4", "#b2df8a",
              "#33a02c", "#fb9a99", "#e31a1c",
              "#fdbf6f", "#ff7f00", "#cab2d6",
              "#6a3d9a", "#ffff99", "#b15928")

    ## colorRampPalette(brewer.pal(12, "Set3"))(n)
    col3[1:n]
}

getPalette <- function(n){
  
  palette <- c("RdBu", "RdYlGn", "Spectral",
               "RdYlBu", "PiYG", "PRGn",
               "PuOr", "BrBG", "RdGy")
  
  palette[1:n]
  
}

getSgn <- function(data, idx){
    d <- data[idx, ]
    ss <- colSums(d)
    ss <- ss / sum(ss)
    return(ss)
}
parseBootCiPerc <- function(bootCiPerc){
    bootCiPerc <- bootCiPerc$percent
    tmp <- length(bootCiPerc)
    ciLo <- bootCiPerc[tmp - 1]
    ciUp <- bootCiPerc[tmp]
    return(c(ciLo, ciUp))
}

## estimate CI using bootstraping
##' @importFrom boot boot
##' @importFrom boot boot.ci
##' @importFrom parallel detectCores
getTagCiMatrix <- function(tagMatrix, conf = 0.95, resample=500, ncpus=detectCores()-1){
    RESAMPLE_TIME <- resample
    trackLen <- ncol(tagMatrix)
    if (Sys.info()[1] == "Windows") {
        tagMxBoot <- boot(data = tagMatrix, statistic = getSgn, R = RESAMPLE_TIME)
    } else {
        tagMxBoot <- boot(data = tagMatrix, statistic = getSgn, R = RESAMPLE_TIME,
                          parallel = "multicore", ncpus = ncpus)
    }
    cat(">> Running bootstrapping for tag matrix...\t\t",
        format(Sys.time(), "%Y-%m-%d %X"), "\n")
    tagMxBootCi <- sapply(seq_len(trackLen), function(i) {
                        bootCiToken <- boot.ci(tagMxBoot, type = "perc", index = i)
                        ## parse boot.ci results
                        return(parseBootCiPerc(bootCiToken))
                        }
                    )
    row.names(tagMxBootCi) <- c("Lower", "Upper")
    return(tagMxBootCi)
}

getTagCount <- function(tagMatrix, xlim, conf, ...) {
    ss <- colSums(tagMatrix)
    ss <- ss/sum(ss)
    ## plot(1:length(ss), ss, type="l", xlab=xlab, ylab=ylab)
    pos <- value <- NULL
    dd <- data.frame(pos=c(xlim[1]:xlim[2]), value=ss)
    if (!(missingArg(conf) || is.na(conf))){
        tagCiMx <- getTagCiMatrix(tagMatrix, conf = conf, ...)
        dd$Lower <- tagCiMx["Lower", ]
        dd$Upper <- tagCiMx["Upper", ]
    }
    return(dd)
}


TXID2EG <- function(txid, geneIdOnly=FALSE) {
    txid <- as.character(txid)
    if (geneIdOnly == TRUE) {
        res <- TXID2EGID(txid)
    } else {
        res <- TXID2TXEG(txid)
    }
    return(res)
}

##' @importFrom GenomicFeatures transcripts
##' @importFrom yulab.utils get_cache_element
##' @importFrom yulab.utils update_cache_item
TXID2TXEG <- function(txid) {
    # ChIPseekerEnv <- get("ChIPseekerEnv", envir=.GlobalEnv)

    txid2geneid <- get_cache_element(item = ChIPseekerCache, elements = "txid2geneid")

    if(is.null(txid2geneid)){
        txdb <- get_cache_element(item = ChIPseekerCache, elements = "TXDB")
        txidinfo <- transcripts(txdb, columns=c("tx_id", "tx_name", "gene_id"))
        idx <- which(sapply(txidinfo$gene_id, length) == 0)
        txidinfo[idx,]$gene_id <- txidinfo[idx,]$tx_name
        txid2geneid <- paste(mcols(txidinfo)[["tx_name"]],
                             mcols(txidinfo)[["gene_id"]],
                             sep="/")
        txid2geneid <- sub("/NA", "", txid2geneid)

        names(txid2geneid) <- mcols(txidinfo)[["tx_id"]]
        update_cache_item(item = ChIPseekerCache, list("txid2geneid" = txid2geneid))
    }

    # if (exists("txid2geneid", envir=ChIPseekerEnv, inherits=FALSE)) {
    #     txid2geneid <- get("txid2geneid", envir=ChIPseekerEnv)
    # } else {
    #     txdb <- get("TXDB", envir=ChIPseekerEnv)
    #     txidinfo <- transcripts(txdb, columns=c("tx_id", "tx_name", "gene_id"))
    #     idx <- which(sapply(txidinfo$gene_id, length) == 0)
    #     txidinfo[idx,]$gene_id <- txidinfo[idx,]$tx_name
    #     txid2geneid <- paste(mcols(txidinfo)[["tx_name"]],
    #                          mcols(txidinfo)[["gene_id"]],
    #                          sep="/")
    #     txid2geneid <- sub("/NA", "", txid2geneid)

    #     names(txid2geneid) <- mcols(txidinfo)[["tx_id"]]
    #     assign("txid2geneid", txid2geneid, envir=ChIPseekerEnv)
    # }
    return(as.character(txid2geneid[txid]))
}

##' @importFrom yulab.utils get_cache_element
##' @importFrom yulab.utils update_cache_item
TXID2EGID <- function(txid) {
    # ChIPseekerEnv <- get("ChIPseekerEnv", envir=.GlobalEnv)

    txid2geneid <- get_cache_element(item = ChIPseekerCache, elements = "txid2eg")

    if(is.null(txid2geneid)){
        txdb <- get_cache_element(item = ChIPseekerCache, elements = "TXDB")
        txidinfo <- transcripts(txdb, columns=c("tx_id", "tx_name", "gene_id"))
        idx <- which(sapply(txidinfo$gene_id, length) == 0)
        txidinfo[idx,]$gene_id <- txidinfo[idx,]$tx_name
        txid2geneid <- as.character(mcols(txidinfo)[["gene_id"]])

        names(txid2geneid) <- mcols(txidinfo)[["tx_id"]]
        update_cache_item(item = ChIPseekerCache, list("txid2eg" = txid2geneid))
    }

    # if (exists("txid2eg", envir=ChIPseekerEnv, inherits=FALSE)) {
    #     txid2geneid <- get("txid2eg", envir=ChIPseekerEnv)
    # } else {
    #     txdb <- get("TXDB", envir=ChIPseekerEnv)
    #     txidinfo <- transcripts(txdb, columns=c("tx_id", "tx_name", "gene_id"))
    #     idx <- which(sapply(txidinfo$gene_id, length) == 0)
    #     txidinfo[idx,]$gene_id <- txidinfo[idx,]$tx_name
    #     txid2geneid <- as.character(mcols(txidinfo)[["gene_id"]])

    #     names(txid2geneid) <- mcols(txidinfo)[["tx_id"]]
    #     assign("txid2eg", txid2geneid, envir=ChIPseekerEnv)
    # }
    return(as.character(txid2geneid[txid]))
}

## according to: https://support.bioconductor.org/p/70432/#70545
## contributed by Hervé Pagès
getFirstHitIndex <- function(x) {
    ## sapply(unique(x), function(i) which(x == i)[1])
    which(!duplicated(x))
}

##' calculate the overlap matrix, which is useful for vennplot
##'
##'
##' @title overlap
##' @param Sets a list of objects
##' @return data.frame
##' @importFrom gtools permutations
##' @export
##' @author G Yu
overlap <- function(Sets) {
    ## this function is very generic.
    ## it call the getIntersectLength function to calculate
    ## the number of the intersection.
    ## if it fail, take a look at the object type were supported by getIntersectLength function.

    nn <- names(Sets)
    w <- t(apply(permutations(2,length(Sets),0:1, repeats.allowed=TRUE), 1 , rev))
    rs <- rowSums(w)
    wd <- as.data.frame(w)
    wd$n <- NA
    for (i in length(nn):0) {
        idx <- which(rs == i)
        if (i == length(nn)) {
            len <- getIntersectLength(Sets, as.logical(w[idx,]))
            wd$n[idx] <- len
        } else if (i == 0) {
            wd$n[idx] <- 0
        } else {
            for (ii in idx) {
                ##print(ii)
                len <- getIntersectLength(Sets, as.logical(w[ii,]))
                ww = w[ii,]
                jj <- which(ww == 0)
                pp <- permutations(2, length(jj), 0:1, repeats.allowed=TRUE)

                for (aa in 2:nrow(pp)) {
                    ## 1st row is all 0, abondoned
                    xx <- jj[as.logical(pp[aa,])]
                    ww[xx] =ww[xx] +1
                    bb <-  t(apply(w, 1, function(i) i == ww))
                    wd$n[rowSums(bb) == length(ww) ]
                         ww <- w[ii,]
                    len <- len - wd$n[rowSums(bb) == length(ww) ]
                    ww <- w[ii,]
                }
                wd$n[ii] <- len
            }
        }
    }
    colnames(wd) = c(names(Sets), "Weight")
    return(wd)
}


getIntersectLength <- function(Sets, idx) {
    ## only use intersect and length methods in this function
    ## works fine with GRanges object
    ## and easy to extend to other objects.
    ss= Sets[idx]
    ol <- ss[[1]]

    if (sum(idx) == 1) {
        return(length(ol))
    }

    for (j in 2:length(ss)) {
        ol <-  intersect(ol, ss[[j]])
    }
    return(length(ol))
}

loadPeak <- function(peak, verbose=FALSE) {
    if (is(peak, "GRanges")) {
        peak.gr <- peak
    } else if (file.exists(peak)) {
        if (verbose)
            cat(">> loading peak file...\t\t\t\t",
                format(Sys.time(), "%Y-%m-%d %X"), "\n")
        peak.gr <- readPeakFile(peak, as="GRanges")
    } else {
        stop("peak should be GRanges object or a peak file...")
    }
    return(peak.gr)
}

##' @importFrom TxDb.Hsapiens.UCSC.hg19.knownGene TxDb.Hsapiens.UCSC.hg19.knownGene
loadTxDb <- function(TxDb) {
    if ( is.null(TxDb) ) {
        warning(">> TxDb is not specified, use 'TxDb.Hsapiens.UCSC.hg19.knownGene' by default...")
        TxDb <- TxDb.Hsapiens.UCSC.hg19.knownGene
    }
    return(TxDb)
}

##' @importFrom AnnotationDbi get
##' @importFrom GenomicFeatures genes
##' @importFrom GenomicFeatures transcriptsBy
##' @importFrom yulab.utils get_cache_element
##' @importFrom yulab.utils update_cache_item
getGene <- function(TxDb, by="gene") {
    .ChIPseekerEnv(TxDb, item = ChIPseekerCache)
    # ChIPseekerEnv <- get("ChIPseekerEnv", envir=.GlobalEnv)

    by <- match.arg(by, c("gene", "transcript"))

    if (by == "gene") {

        features <- get_cache_element(item = ChIPseekerCache, elements = "Genes")

        if(is.null(features)){
            features <- suppressMessages(genes(TxDb))
            update_cache_item(item = ChIPseekerCache, list("Genes" = features))
        }

        # if ( exists("Genes", envir=ChIPseekerEnv, inherits=FALSE) ) {
        #     features <- get("Genes", envir=ChIPseekerEnv)
        # } else {
        #     features <- suppressMessages(genes(TxDb))
        #     assign("Genes", features, envir=ChIPseekerEnv)
        # }
    } else {

        features <- get_cache_element(item = ChIPseekerCache, elements = "Transcripts")

        if(is.null(features)){
            features <- transcriptsBy(TxDb)
            features <- unlist(features)
            update_cache_item(item = ChIPseekerCache, list("Transcripts" = features))
        }

        # if ( exists("Transcripts", envir=ChIPseekerEnv, inherits=FALSE) ) {
        #     features <- get("Transcripts", envir=ChIPseekerEnv)
        # } else {
        #     features <- transcriptsBy(TxDb)
        #     features <- unlist(features)
        #     assign("Transcripts", features, envir=ChIPseekerEnv)
        # }
    }

    return(features)
}


##' get filenames of sample files
##'
##'
##' @title getSampleFiles
##' @return list of file names
##' @export
##' @author G Yu
getSampleFiles <- function() {
    dir <- system.file("extdata", "GEO_sample_data", package="ChIPseeker")
    files <- list.files(dir)
    ## protein <- sub("GSM\\d+_", "", files)
    ## protein <- sub("_.+", "", protein)
    protein <- gsub(pattern='GSM\\d+_(\\w+_\\w+)_.*', replacement='\\1',files)
    protein <- sub("_Chip.+", "", protein)
    res <- paste(dir, files, sep="/")
    res <- as.list(res)
    names(res) <- protein
    return(res)
}
## @importFrom RCurl getURL
## getDirListing <- function (url) {
##     ## from GEOquery
##     print(url)
##     a <- getURL(url)
##     b <- textConnection(a)
##     d <- read.table(b, header = FALSE)
##     close(b)
##     return(d)
## }


is.dir <- function(dir) {
    if (file.exists(dir) == FALSE)
        return(FALSE)
    return(file.info(dir)$isdir)
}


parse_targetPeak_Param <- function(targetPeak) {
    if (length(targetPeak) == 1) {
        if (is.dir(targetPeak)) {
            files <- list.files(path=targetPeak)
            idx <- unlist(sapply(c("bed", "bedGraph", "Peak"), grep, x=files))
            idx <- sort(unique(idx))
            files <- files[idx]
            targetPeak <- sub("/$", "", targetPeak)
            res <- paste(targetPeak, files, sep="/")
        } else {
            if (!file.exists(targetPeak)) {
                stop("bed file is not exists...")
            } else {
                res <- targetPeak
            }
        }
    } else {
        if (is.dir(targetPeak[1])) {
            stop("targetPeak should be a vector of bed file names or a folder containing bed files...")
        } else {
            res <- targetPeak[file.exists(targetPeak)]
            if (length(res) == 0) {
                stop("targetPeak file not exists...")
            }
        }
    }
    return(res)
}


IDType <- function(TxDb) {
    ##
    ## IDType <- metadata(TxDb)[8,2]
    ##
    ## update: 2015-10-27
    ## now IDType change from metadata(TxDb)[8,2] to metadata(TxDb)[9,2]
    ## it may change in future too
    ##
    ## it's safe to extract via grep

    md <- metadata(TxDb)
    md[grep("Type of Gene ID", md[,1]), 2]
}

list_to_dataframe <- function(dataList) {
    if (is.null(names(dataList)))
        return(do.call('rbind', dataList))

    cn <- lapply(dataList, colnames) %>% unlist %>% unique
    cn <- c('.id', cn)
    dataList2 <- lapply(seq_along(dataList), function(i) {
        data = dataList[[i]]
        data$.id = names(dataList)[i]
        idx <- ! cn %in% colnames(data)
        if (sum(idx) > 0) {
            for (i in cn[idx]) {
                data[, i] <- NA
            }
        }
        return(data[,cn])
    })
    res <- do.call('rbind', dataList2)
    res$.id <- factor(res$.id, levels=rev(names(dataList)))
    return(res)
}

##' @importFrom GenomicRanges GRangesList
##' @export
GenomicRanges::GRangesList

## . function was from plyr package
##' capture name of variable
##'
##' @rdname dotFun
##' @export
##' @title .
##' @param ... expression
##' @param .env environment
##' @return expression
##' @examples
##' x <- 1
##' eval(.(x)[[1]])
. <- function (..., .env = parent.frame()) {
    structure(as.list(match.call()[-1]), env = .env, class = "quoted")
}


##' check upstream and downstream parameter
##' 
##' 
##' check_upstream_and_downstream
##'
##' @param upstream upstream
##' @param downstream downstream
##' @importFrom ggplot2 rel
check_upstream_and_downstream <- function(upstream, downstream){
    
    ## upstream and downstream should be the same type
    if(class(upstream) != class(downstream)){
        stop("the type of upstream and downstream should be the same...")
    }
    
    ## downstream and upstream parameter should be numeric or NULL
    if(!is.numeric(upstream) && !is.null(upstream)){
        stop("upstream and downstream parameter should be numeric or NULL...")
    }
    
    ## the value of rel object should be in (0,1)
    if(inherits(upstream, 'rel')){
        if(as.numeric(upstream) < 0 || as.numeric(upstream) >1 ){
            stop('the value of rel object should be in (0,1)...')
        }
    }
    
    ## check actual number
    if(is.numeric(upstream) && !inherits(upstream, 'rel')){
        if(upstream < 1 | downstream < 1){
            stop('if upstream or downstream is integer, the value of it should be greater than 1...')
        }
    }
}


##' @importFrom ggplot2 rel
##' 
##' @export
ggplot2::rel


##' make label for figures
##' @param by one of 'gene', 'transcript', 'exon', 'intron' , '3UTR' , '5UTR', 'UTR'
##' @param type one of "start_site", "end_site", "body"
make_label <- function(type, by){
    
    if(type == 'body'){
        if(by %in% c('gene', 'transcript', 'exon', 'intron')){
            label_SS <- paste0("T","SS")
            label_TS <- paste0("T","TS")
            label <- c(label_SS,label_TS)
        }else{
            label_SS <- paste0(by,"_SS")
            label_TS <- paste0(by,"_TS")
            label <- c(label_SS,label_TS)
        }
        
    }else if(type == "start_site"){
        if(by %in% c('gene', 'transcript', 'exon', 'intron')){
            label <- paste0("T","SS")
        }else{
            label <- paste0(by,"_SS") 
        }
        
    }else{
        if(by %in% c('gene', 'transcript', 'exon', 'intron')){
            label <- paste0("T","TS")
        }else{
            label <- paste0(by,"_TS")
        }
    }
    
    return(label)
}

##' @importFrom yulab.utils get_cache_item
get_env_genome <- function(){

    current_env <- get_cache_item(item = ChIPseekerCache)

    env_txdb <- current_env$TXDB
    env_txdb_meta <- S4Vectors::metadata(env_txdb)
    env_txdb_version <- env_txdb_meta[grep("Genome",env_txdb_meta[,1]),2]

    return(env_txdb_version)
}