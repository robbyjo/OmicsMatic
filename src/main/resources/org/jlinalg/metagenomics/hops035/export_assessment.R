#!/usr/bin/env Rscript
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Read, never rewrite, the original AMPS workspace. Preserve every double bit.
args <- commandArgs(trailingOnly=TRUE)
if (length(args) != 2L) stop("Require original analysis.RData and NEW export JSON")
if (as.character(getRversion()) != "4.0.5") stop("Qualified R4.0.5 required")
if (file.exists(args[2])) stop("Export output must be NEW")
workspace <- new.env(parent=baseenv())
load(args[1], envir=workspace)
required <- c("data", "res", "red.res", "unq.spec", "all.inds", "red.res.tab", "maltex.mode")
if (!all(required %in% ls(workspace))) stop("Incomplete original AMPS workspace")
if (!identical(workspace$maltex.mode, c("default", "ancient"))) stop("Require original def_anc workspace")
hex <- function(x) {
    if (length(x) != 1L || !is.numeric(x)) stop("Require one original numeric cell")
    if (is.nan(x)) return("NaN")
    if (is.na(x)) return("NA")
    if (is.infinite(x)) return(if (x > 0) "Inf" else "-Inf")
    paste(sprintf("%02x", as.integer(writeBin(as.double(x), raw(), size=8L, endian="big"))), collapse="")
}
text <- function(x) {
    if (length(x) != 1L || !is.character(x)) stop("Require one original character cell")
    if (is.na(x)) NULL else x
}
stats <- function(row, prefix) {
    fields <- c(node="node", dr6="dr6", n6="n6", dr4="dr4", n4="n4",
                maximum_terminal_damage="mapDam", unique_per_reference="rd")
    result <- lapply(names(fields), function(name) {
        value <- row[[paste(prefix, fields[[name]], sep=".")]]
        if (name == "node") text(value) else hex(value)
    })
    names(result) <- names(fields)
    result
}
data <- workspace$data
expected <- c("id", "spec", "def.node", "def.dr6", "def.n6", "def.dr4", "def.n4", "def.mapDam", "def.rd",
              "anc.node", "anc.dr6", "anc.n6", "anc.dr4", "anc.n4", "anc.mapDam", "anc.rd")
if (!identical(colnames(data), expected)) stop("Original scientific column identities differ")
records <- lapply(seq_len(nrow(data)), function(i) {
    row <- data[i, , drop=FALSE]
    list(sample=text(row$id), taxon=text(row$spec), default=stats(row, "def"), ancient=stats(row, "anc"))
})
matrix.export <- function(x) {
    if (!is.matrix(x) || is.null(rownames(x)) || is.null(colnames(x))) stop("Require named complete original matrix")
    rows <- lapply(seq_len(nrow(x)), function(i) setNames(as.list(as.character(x[i, ])), colnames(x)))
    setNames(rows, rownames(x))
}
table <- workspace$red.res.tab
table.rows <- lapply(seq_len(nrow(table)), function(i) unname(as.list(as.character(table[i, ]))))
packages <- c("getopt", "gridBase", "gridExtra", "jsonlite", "gtable")
document <- list(schema="HOPS035_RDATA_SCIENTIFIC_EXPORT_V1", R=as.character(getRversion()),
    packages=setNames(lapply(packages, function(x) as.character(packageVersion(x))), packages),
    samples=unname(as.list(workspace$all.inds)), taxa=unname(as.list(workspace$unq.spec)),
    data=records, res=matrix.export(workspace$res), red_res=matrix.export(workspace$red.res),
    table_columns=unname(as.list(colnames(table))), table_rows=table.rows)
jsonlite::write_json(document, args[2], auto_unbox=TRUE, null="null", na="null", pretty=TRUE)
