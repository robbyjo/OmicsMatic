# Read the retained original binary weights; JSON numeric views can round doubles.
args <- commandArgs(trailingOnly=TRUE);stopifnot(length(args)==5L)
out <- args[1];.libPaths(args[2:3],include.site=FALSE);original <- args[5]
stopifnot(as.character(getRversion())=="4.3.3")
result <- list()
for(label in sort(list.dirs(file.path(original,"cases"),recursive=FALSE,full.names=FALSE))){
 path <- file.path(original,"cases",label,"original-listw.rds")
 if(!file.exists(path))next
 lw <- readRDS(path);stopifnot(inherits(lw,"listw"),identical(lw$style,"W"))
 result[[label]] <- lapply(lw$weights,function(row)as.list(sprintf("%.17g",unname(row))))
}
stopifnot(length(result)==25L)
jsonlite::write_json(result,file.path(out,"weights-exact-tokens.json"),pretty=TRUE,auto_unbox=TRUE,digits=NA)
writeLines(capture.output(sessionInfo()),file.path(out,"session-info.txt"))
cat("PASS exact17-digit exports from25 retained original binary listw objects\n")
