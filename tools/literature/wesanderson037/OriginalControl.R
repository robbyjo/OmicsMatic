# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Complete original package vectors/attributes and errors; execute no Java implementation.
args <- commandArgs(TRUE)
lib <- args[1]
out <- args[2]
if (dir.exists(out)) stop("NEW output required; preserve previous controls")
dir.create(out, recursive = TRUE)
library(wesanderson, lib.loc = lib)
stopifnot(as.character(packageVersion("wesanderson")) == "0.3.7", length(wes_palettes) == 24)
capture.output(sessionInfo(), file = file.path(out, "SESSION.txt"))
capture.output(grDevices::colorRamp, grDevices::colorRampPalette, grDevices::rgb,
               stats::approx, stats::approxfun, file = file.path(out, "BASE_R_FUNCTIONS.txt"))
saveRDS(wes_palettes, file.path(out, "complete-original-palettes.rds"), compress = FALSE)
writeLines(vapply(names(wes_palettes), function(name) paste(name, paste(wes_palettes[[name]], collapse=","), sep="\t"), ""), file.path(out, "complete-original-palettes.tsv"))
cases <- list()
add <- function(name, n, type, missingN = FALSE, missingType = FALSE) {
  cases[[length(cases)+1L]] <<- list(name=name, n=n, type=type, missingN=missingN, missingType=missingType)
}
for (name in names(wes_palettes)) {
  for (type in c("DEFAULT", "discrete", "continuous")) {
    default <- identical(type, "DEFAULT")
    add(name, NA_real_, type, TRUE, default)
    for (n in c(-2,-1,0:64)) add(name,n,type,FALSE,default)
  }
  for (n in c(127,128,255,256,257,511,512,1024,4096)) add(name,n,"continuous")
  for (n in c(-0.999,-0.99999999,-0.9999999,-0.5,0.1,0.9,1.5,1.999999,1.99999999,2.00000001,3.99999999,4.00000001)) {
    add(name,n,"discrete");add(name,n,"continuous")
  }
  for (n in c(NaN,NA_real_,Inf,-Inf)) {add(name,n,"discrete");add(name,n,"continuous")}
  for (type in c("d","di","disc","c","co","cont","","Discrete","invalid")) add(name,3,type)
}
for (name in c("unknown","Zissou","rushmore","")) for(type in c("discrete","continuous","invalid")) add(name,3,type)
writeLines(as.character(length(cases)),file.path(out,"case-count.txt"))
rows <- character(length(cases));objects <- vector("list",length(cases))
hex <- function(n) paste(sprintf("%02x",as.integer(writeBin(as.numeric(n),raw(),size=8,endian="big"))),collapse="")
for (i in seq_along(cases)) {
  x <- cases[[i]];call <- list(name=x$name)
  if (!x$missingN) call$n <- x$n
  if (!x$missingType) call$type <- x$type
  result <- tryCatch(list(ok=TRUE,value=do.call(wes_palette,call)),error=function(e) list(ok=FALSE,error=conditionMessage(e),class=class(e)))
  objects[[i]] <- list(request=x,result=result)
  value <- if(result$ok) paste(as.character(result$value),collapse=",") else ""
  count <- if(result$ok) length(result$value) else -1L
  attrName <- if(result$ok) attr(result$value,"name") else ""
  attrClass <- if(result$ok) paste(class(result$value),collapse=",") else ""
  error <- if(result$ok) "" else gsub("[\t\r\n]"," ",result$error)
  rows[i] <- paste(sprintf("C%05d",i),x$name,if(x$missingN) "MISSING" else hex(x$n),if(x$missingType) "MISSING" else x$type,if(result$ok) "OK" else "ERROR",count,value,attrName,attrClass,error,sep="\t")
}
writeLines(rows,file.path(out,"complete-original-cases.tsv"))
saveRDS(objects,file.path(out,"complete-original-cases.rds"),compress=FALSE)
plots <- file.path(out,"original-plots");dir.create(plots)
for (name in names(wes_palettes)) {
  grDevices::pdf(file.path(plots,paste0(name,".pdf")),width=7,height=3)
  print(wes_palette(name))
  grDevices::dev.off()
}
writeLines(paste("PASS_COMPLETE_ORIGINAL_WESANDERSON037","cases",length(cases),"palettes",length(wes_palettes),"plots",length(wes_palettes)),file.path(out,"COMPLETED.txt"))
cat("PASS_COMPLETE_ORIGINAL_WESANDERSON037 cases=",length(cases)," palettes=24 original_plots=24\n",sep="")
