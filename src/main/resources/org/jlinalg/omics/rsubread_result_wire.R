# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Literal scalar-value bridge. Original complete RDS remains the object archive.
# Does not install packages, execute user expressions or modify a namespace.
rsubread_wire_hex <- function(x) paste(sprintf("%02x", as.integer(x)), collapse="")
rsubread_wire_name <- function(x) if (is.na(x)) "NA" else paste0("S", rsubread_wire_hex(charToRaw(enc2utf8(x))))

rsubread_write_frame <- function(x, path) {
    stopifnot(is.data.frame(x), identical(class(x), "data.frame"),
              setequal(names(attributes(x)), c("names", "class", "row.names")), !file.exists(path))
    kind <- if (.row_names_info(x, 1L) < 0L || nrow(x) == 0L) "AUTOMATIC" else "EXPLICIT"
    con <- file(path, "wb"); on.exit(close(con))
    emit <- function(lines) writeLines(lines, con, useBytes=TRUE)
    emit(paste("RSUBREAD_FRAME_V1", nrow(x), ncol(x), kind, sep="\t"))
    if (nrow(x)) emit(paste0("R\t", vapply(row.names(x), rsubread_wire_name, "")))
    for (i in seq_along(x)) {
        value <- x[[i]]; storage <- typeof(value)
        stopifnot(is.null(attributes(value)), storage %in% c("character", "integer", "double", "logical"))
        emit(paste("C", toupper(storage), rsubread_wire_name(names(x)[i]), sep="\t"))
        if (length(value)) {
            tokens <- if (storage == "character") vapply(value, rsubread_wire_name, "") else
                if (storage == "double") vapply(value, function(z) rsubread_wire_hex(writeBin(z, raw(), size=8L, endian="big")), "") else
                vapply(as.integer(value), function(z) rsubread_wire_hex(writeBin(z, raw(), size=4L, endian="big")), "")
            emit(paste0("V\t", tokens))
        }
    }
}

rsubread_write_result <- function(x, directory) {
    stopifnot(is.list(x), !dir.exists(directory), !file.exists(directory))
    expected <- if ("counts_junction" %in% names(x)) c("counts", "counts_junction", "annotation", "targets", "stat") else c("counts", "annotation", "targets", "stat")
    stopifnot(identical(names(x), expected), is.null(attributes(x$targets)), is.character(x$targets),
              is.matrix(x$counts), typeof(x$counts) %in% c("integer", "double"),
              identical(rownames(x$counts), as.character(x$annotation$GeneID)), identical(colnames(x$counts), x$targets))
    stopifnot(dir.create(directory))
    con <- file(file.path(directory, "counts.matrix"), "wb")
    tryCatch({
        writeLines(paste("RSUBREAD_MATRIX_V1", toupper(typeof(x$counts)), nrow(x$counts), ncol(x$counts), sep="\t"), con, useBytes=TRUE)
        if (nrow(x$counts)) writeLines(paste0("R\t", vapply(rownames(x$counts), rsubread_wire_name, "")), con, useBytes=TRUE)
        writeLines(paste0("C\t", vapply(colnames(x$counts), rsubread_wire_name, "")), con, useBytes=TRUE)
        if (length(x$counts)) writeLines(paste0("D\t", vapply(as.double(x$counts), function(z) rsubread_wire_hex(writeBin(z, raw(), size=8L, endian="big")), "")), con, useBytes=TRUE)
    }, finally=close(con))
    for (member in c("annotation", "stat", if ("counts_junction" %in% names(x)) "counts_junction"))
        rsubread_write_frame(x[[member]], file.path(directory, paste0(member, ".frame")))
    targets <- structure(list(x$targets), names="target", class="data.frame", row.names=seq_along(x$targets))
    rsubread_write_frame(targets, file.path(directory, "targets.frame"))
    writeLines(expected, file.path(directory, "members.txt"), useBytes=TRUE)
    invisible(directory)
}
