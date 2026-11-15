# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE)
stopifnot(length(args)==3L)
libs <- readLines(args[[1]],warn=FALSE)
.libPaths(unique(c(libs,.Library)),include.site=FALSE)
if (!identical(as.character(getRversion()),"4.6.1")) {
  cat("SKIP3: Existing R4.6.1 is required\n");quit(status=3L)
}
locale <- "English_United States.utf8"
locale.result <- Sys.setlocale("LC_ALL",locale)
if (!nzchar(locale.result)||!all(vapply(c("LC_COLLATE","LC_CTYPE","LC_MONETARY","LC_TIME"),
    Sys.getlocale,character(1))==locale)) {
  cat("SKIP3: Explicit Windows UTF-8 locale is unavailable\n");quit(status=3L)
}
Sys.setlocale("LC_TIME","C")
for (name in c("idepGolem","DESeq2","BiocManager","jsonlite"))
  if (!requireNamespace(name,quietly=TRUE)) {
    cat("SKIP3: Missing required runtime package: ",name,"\n");quit(status=3L)
  }
if (as.character(packageVersion("idepGolem"))!="2.4.4" ||
    as.character(packageVersion("DESeq2"))!="1.52.0") {
  cat("SKIP3: Exact original iDEP2.4.4 and DESeq2 1.52.0 are required\n");quit(status=3L)
}
source(args[[3]],local=TRUE)
idep244.audit(args[[2]],"namespace-audit.rds")
installed <- installed.packages(lib.loc=.libPaths(),noCache=TRUE)
installed <- installed[!duplicated(installed[,"Package"]),,drop=FALSE]
roots <- unique(c("idepGolem","BiocManager","jsonlite",loadedNamespaces()))
dependencies <- tools::package_dependencies(roots,db=installed,
  which=c("Depends","Imports"),recursive=TRUE)
required <- sort(unique(c(roots,unlist(dependencies,use.names=FALSE))))
required <- setdiff(required,"R")
if (!all(required %in% installed[,"Package"]))
  stop("Missing transitive Depends/Imports package: ",paste(setdiff(required,installed[,"Package"]),collapse=", "))
packages <- lapply(required,function(name) {
  path <- normalizePath(find.package(name,lib.loc=.libPaths()),winslash="/",mustWork=TRUE)
  list(name=name,version=as.character(packageVersion(name,lib.loc=dirname(path))),path=path)
})
jsonlite::write_json(list(status="PASS_INSPECTED_ORIGINAL_IDEP244_NAMESPACE_AND_RUNTIME_CLOSURE",
  R_version=as.character(getRversion()),R_home=normalizePath(R.home(),winslash="/"),
  libraries=unname(.libPaths()),packages=packages,
  dependency_policy="Full selected package folders for Depends/Imports and loaded namespaces; LinkingTo-only build headers are not additional runtime dependencies",
  search_path=search(),loaded_namespaces=sort(loadedNamespaces())),
  "INSPECTED.json",auto_unbox=TRUE,pretty=TRUE,null="null")
writeLines(capture.output(sessionInfo()),"SESSION.txt")
