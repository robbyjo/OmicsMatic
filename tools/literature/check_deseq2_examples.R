# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE)
stopifnot(length(args)==7L)
.libPaths(c(args[3:7],.Library),include.site=FALSE)
stopifnot(identical(as.character(packageVersion("DESeq2")),"1.52.0"))
cases <- jsonlite::fromJSON(args[[1]],simplifyVector=FALSE)
number <- function(x)vapply(x,function(z){
    if(is.nan(z))"NaN" else if(is.na(z))"NA" else if(is.infinite(z))
        if(z>0)"Inf" else "-Inf" else formatC(z,digits=17L,format="g",decimal.mark=".")
},character(1))
results <- lapply(cases,function(c){
    m <- do.call(rbind,lapply(c$counts,function(row)as.integer(unlist(row))))
    parameters <- list(counts=m,type=c$type)
    if(!is.null(c$geometric_means)) parameters$geoMeans <- as.numeric(unlist(c$geometric_means))
    if(!is.null(c$controls)) parameters$controlGenes <- as.integer(unlist(c$controls))+1L
    sf <- do.call(DESeq2::estimateSizeFactorsForMatrix,parameters)
    normalized <- sweep(m,2L,sf,"/")
    list(name=c$name,factors=number(sf),normalized=lapply(seq_len(nrow(m)),function(i)number(normalized[i,])))
})
jsonlite::write_json(results,args[[2]],auto_unbox=FALSE,pretty=TRUE,null="null")
