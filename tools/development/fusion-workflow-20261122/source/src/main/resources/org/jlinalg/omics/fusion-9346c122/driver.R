# Original FUSION supervisor: metadata/ACK, unchanged child scripts, exact exports.
# The child runs seed-runner.R; original q() calls retain their real exit behavior.
hex <- function(x) paste(sprintf("%02x",as.integer(charToRaw(enc2utf8(x)))),collapse="")
unhex <- function(x) rawToChar(as.raw(strtoi(substring(x,seq(1,nchar(x),2),seq(2,nchar(x),2)),16L)))
outcome <- function(status,detail) {
  writeLines(c("status\tdetail_hex",paste(status,hex(detail),sep="\t")),"outcome.tsv",useBytes=TRUE)
}
fail <- function(message,dependency=FALSE) {
  outcome(if(dependency) "SKIP3" else "FAIL2",message)
  cat(if(dependency) "SKIP3: DEPENDENT ANALYSIS SKIPPED: " else "FAIL2: NO COMPLETE RESULT: ",message,"\n",file=stderr(),sep="")
  quit(status=if(dependency) 3L else 2L,save="no")
}
tryCatch({
  libraries <- vapply(readLines("libraries.hex",warn=FALSE,encoding="UTF-8"),unhex,"")
  .libPaths(unique(c(libraries,.Library)))
  if(!requireNamespace("jsonlite",quietly=TRUE)) fail("Missing jsonlite in declared libraries; install privately where permitted or retain the skipped analysis",TRUE)
  cfg <- jsonlite::read_json("request.json",simplifyVector=FALSE)
  writeJSON <- function(x,file) jsonlite::write_json(x,file,auto_unbox=TRUE,null="null",na="string",digits=NA,pretty=TRUE)
  if(as.character(getRversion())!=cfg$expected_R) fail(paste("Expected R",cfg$expected_R,"but resolved",getRversion()),TRUE)
  for(package in names(cfg$expected_packages)) {
    if(!requireNamespace(package,quietly=TRUE))fail(paste("Missing exact permitted R package",package,"version",cfg$expected_packages[[package]]),TRUE)
    if(as.character(packageVersion(package))!=cfg$expected_packages[[package]])fail(paste("Wrong package edition:",package,"expected",cfg$expected_packages[[package]],"resolved",packageVersion(package)),TRUE)
  }
  for(name in names(cfg$binaries)) if(!file.exists(cfg$binaries[[name]]))fail(paste("Missing declared original tool:",name,cfg$binaries[[name]]),TRUE)
  sourceFile <- switch(cfg$operation,ASSOCIATE="FUSION.assoc_test.R",TRAIN="FUSION.compute_weights.R",POSTPROCESS="FUSION.post_process.R",stop("Unknown operation"))
  script <- parse(sourceFile,keep.source=TRUE)
  definition <- Filter(function(x)is.call(x)&&as.character(x[[1]])[1] %in% c("=","<-")&&identical(x[[2]],as.name("option_list")),as.list(script))
  stopifnot(length(definition)==1L)
  parser <- new.env(parent=baseenv());parser$make_option <- optparse::make_option
  eval(definition[[1]],parser)
  originalArgs <- commandArgs(TRUE)
  originalOptions <- optparse::parse_args(optparse::OptionParser(option_list=parser$option_list),args=originalArgs)
  prefix <- if(cfg$operation=="TRAIN")cfg$inputs$bfile else paste0(cfg$inputs$ref_ld_prefix,originalOptions$chr)
  bim <- read.table(paste0(prefix,".bim"),header=FALSE,colClasses=c("integer","character","numeric","integer","character","character"))
  fam <- read.table(paste0(prefix,".fam"),header=FALSE,colClasses=c("character","character","character","character","integer","numeric"))
  if(nrow(bim)<1L||nrow(fam)<3L||nrow(bim)>cfg$limits$maximum_variants||nrow(fam)>cfg$limits$maximum_samples||as.double(nrow(bim))*nrow(fam)>cfg$limits$maximum_cells)stop("Original PLINK sample/variant/cell capacity exceeded")
  con <- file(paste0(prefix,".bed"),"rb");magic <- readBin(con,"raw",3L);close(con)
  if(!identical(magic,as.raw(c(0x6c,0x1b,0x01)))||file.info(paste0(prefix,".bed"))$size!=3+ceiling(nrow(fam)/4)*nrow(bim))stop("SNP-major BED magic/length differs from BIM/FAM population")
  writeJSON(list(samples=nrow(fam),variants=nrow(bim),cells=as.double(nrow(fam))*nrow(bim)),"input-dimensions.json")
  saveRDS(originalOptions,"expected-original-options.rds")
  writeJSON(as.list(originalArgs),"original-arguments.json")
  featureTable <- NULL;needed <- list();postRows <- integer()
  absolute <- function(x) grepl("^(/|[A-Za-z]:[/\\\\]|\\\\\\\\)",x)
  portable <- function(x) {
    if(is.na(x)||!nzchar(x)||grepl("\\",x,fixed=TRUE)||grepl(":",x,fixed=TRUE)||any(utf8ToInt(x)<32L|utf8ToInt(x)==127L)||startsWith(x,"/")||any(strsplit(x,"/",fixed=TRUE)[[1]] %in% c("",".","..")))stop("Nonportable selected WGT name; preserve the input and supply safe relative weight-file addresses")
    x
  }
  if(cfg$operation=="ASSOCIATE") {
    featureTable <- read.table(cfg$inputs$weights,header=TRUE,as.is=TRUE)
    stopifnot(all(c("WGT","ID","CHR","P0","P1") %in% names(featureTable)))
    featureTable <- featureTable[as.character(featureTable$CHR)==as.character(originalOptions$chr),,drop=FALSE]
    for(i in seq_len(nrow(featureTable))) {
      name <- portable(as.character(featureTable$WGT[i]))
      needed[[i]] <- list(row=i,source=paste0(cfg$input_origins$weights_dir,"/",name),target=paste0("inputs/weights/",name))
    }
  }
  if(cfg$operation=="POSTPROCESS") {
    featureTable <- read.table(cfg$inputs$association,header=TRUE,as.is=TRUE)
    stopifnot(all(c("FILE","ID","CHR","TWAS.P") %in% names(featureTable)))
    postRows <- which(featureTable$CHR==originalOptions$chr & featureTable$TWAS.P<originalOptions$minp_input & !is.na(featureTable$TWAS.P))
    for(i in seq_along(postRows)) {
      name <- as.character(featureTable$FILE[postRows[i]])
      if(is.na(name)||!nzchar(name))stop("Selected post-process FILE address is missing")
      path <- if(absolute(name))name else paste0(cfg$input_origins$weights_base,"/",name)
      needed[[i]] <- list(row=postRows[i],source=path,target=sprintf("inputs/weights/%08d.RDat",i))
    }
  }
  if(length(needed)>cfg$limits$maximum_features)stop("Selected feature capacity exceeded")
  saveRDS(featureTable,"parsed-input-features.rds")
  writeJSON(needed,"needed-files.json")
  writeJSON(list(R=as.character(getRversion()),R_home=R.home(),platform=R.version$platform,
                 libraries=as.list(.libPaths()),packages=lapply(sort(loadedNamespaces()),function(p)list(name=p,version=as.character(packageVersion(p)),path=find.package(p))),
                 dlls=lapply(getLoadedDLLs(),function(d)list(name=d[["name"]],path=d[["path"]]))),"runtime.json")
  writeLines("FUSION_PREFLIGHT_9346C122_V1","READY.pending",useBytes=TRUE)
  stopifnot(file.rename("READY.pending","READY"))
  started <- proc.time()[["elapsed"]]
  while(!file.exists("ACK")) {
    if(proc.time()[["elapsed"]]-started>cfg$limits$timeout_seconds)stop("Preflight ACK timed out; original analysis was not started")
    Sys.sleep(.025)
  }
  if(!identical(readLines("ACK",warn=FALSE),"FUSION_ACK_9346C122_V1"))stop("Invalid pre-call ACK")
  weightObjects <- list()
  for(i in seq_along(needed)) {
    model <- new.env(parent=baseenv())
    names <- load(paste0("../",needed[[i]]$target),envir=model)
    if(!all(c("wgt.matrix","snps","cv.performance") %in% names))stop("Selected original weight file omits required objects")
    if(!is.matrix(model$wgt.matrix)||!is.matrix(model$cv.performance)||nrow(model$wgt.matrix)!=nrow(model$snps)||ncol(model$wgt.matrix)!=ncol(model$cv.performance))stop("Selected weight/model dimensions differ")
    if(length(model$wgt.matrix)>cfg$limits$maximum_cells||nrow(model$wgt.matrix)>cfg$limits$maximum_variants)stop("Selected weight/model capacity exceeded")
    weightObjects[[i]] <- list(target=needed[[i]]$target,names=as.list(names),variants=nrow(model$wgt.matrix),models=as.list(colnames(model$wgt.matrix)))
  }
  writeJSON(weightObjects,"weight-objects.json")
  if(cfg$operation=="POSTPROCESS") {
    transported <- featureTable
    for(i in seq_along(postRows))transported$FILE[postRows[i]] <- paste0("../",needed[[i]]$target)
    encoded <- transported
    for(name in names(encoded)) if(is.double(encoded[[name]]))encoded[[name]] <- ifelse(is.na(encoded[[name]]),"NA",sprintf("%.17e",encoded[[name]]))
    write.table(encoded,file="post-input.tsv",sep="\t",quote=TRUE,row.names=FALSE,na="NA")
    restored <- read.table("post-input.tsv",header=TRUE,as.is=TRUE)
    if(!identical(restored[,setdiff(names(restored),"FILE"),drop=FALSE],featureTable[,setdiff(names(featureTable),"FILE"),drop=FALSE]))stop("Post-process path transport changed scientific values/types; no analysis started")
    if(!identical(as.character(restored$FILE[postRows]),vapply(needed,function(x)paste0("../",x$target),"")))stop("Post-process FILE transport differs")
    saveRDS(list(original=featureTable,transported=restored,selected_rows=postRows),"post-path-transport.rds")
  }
  status <- system2(cfg$rscript,c("--vanilla","seed-runner.R",vapply(originalArgs,shQuote,"")),
                    stdout="original.stdout.log",stderr="original.stderr.log",wait=TRUE)
  writeJSON(list(exit_code=as.integer(status),arguments=as.list(originalArgs),source=sourceFile),"original-process.json")
  if(status!=0L)stop(paste("Original FUSION child exited",status,"; retain original stdout/stderr and partial files"))
  exports <- list.files("out",recursive=TRUE,full.names=TRUE,all.files=FALSE)
  exports <- exports[!dir.exists(exports)]
  parsed <- list();summary <- list(operation=cfg$operation,original_exit_code=as.integer(status),
    selected_features=if(cfg$operation=="ASSOCIATE")nrow(featureTable) else if(cfg$operation=="POSTPROCESS")length(postRows) else 1L,
    finite_result_rows=0L,no_result_rows=0L,missing_requested_coloc_rows=0L,original_p_zero=0L,
    original_export_files=as.list(exports),source_revision="9346c1222bffbb34499fa7a8e23c1b701b55cb05")
  if(cfg$operation=="ASSOCIATE") {
    required <- "out/result"
    if(!file.exists(required))stop("Original association exited without its required output")
    tables <- lapply(c(required,if(file.exists(paste0(required,".MHC")))paste0(required,".MHC")),function(p)read.table(p,header=TRUE,as.is=TRUE,check.names=FALSE))
    results <- do.call(rbind,tables)
    if(nrow(results)!=nrow(featureTable))stop("Original association row population differs from selected weight rows")
    expected <- paste(as.character(featureTable$ID),paste0(cfg$inputs$weights_dir,"/",as.character(featureTable$WGT)),sep="\t")
    observed <- paste(as.character(results$ID),as.character(results$FILE),sep="\t")
    if(!identical(sort(expected),sort(observed)))stop("Original association ID/weight-file identities differ")
    finite <- is.finite(results$TWAS.Z)&is.finite(results$TWAS.P)&results$TWAS.P>=0&results$TWAS.P<=1
    summary$finite_result_rows <- sum(finite);summary$no_result_rows <- sum(!finite)
    summary$original_p_zero <- sum(results$TWAS.P==0,na.rm=TRUE)
    if(!is.na(originalOptions$coloc_P)) {
      fields <- paste0("COLOC.PP",0:4);if(!all(fields %in% names(results)))stop("Requested original COLOC fields absent")
      values <- as.matrix(results[,fields,drop=FALSE]);if(any(is.finite(values)&(values<0|values>1)))stop("Original COLOC probability outside [0,1]")
      eligible <- finite&results$TWAS.P<originalOptions$coloc_P
      summary$missing_requested_coloc_rows <- sum(eligible & rowSums(!is.finite(values))>0)
    }
    parsed$association <- results
  } else if(cfg$operation=="TRAIN") {
    path <- "out/result.wgt.RDat"
    if(file.exists(path)) {
      model <- new.env(parent=baseenv());load(path,envir=model)
      if(!all(c("wgt.matrix","snps","cv.performance","hsq","hsq.pv","N.tot") %in% ls(model)))stop("Original weight output omits required model objects")
      if(!is.matrix(model$wgt.matrix)||nrow(model$wgt.matrix)!=nrow(model$snps)||ncol(model$wgt.matrix)!=ncol(model$cv.performance))stop("Original predictive model dimensions differ")
      if(any(!is.finite(model$wgt.matrix)))stop("Original trained weights are nonfinite")
      summary$finite_result_rows <- 1L;summary$trained_models <- as.list(colnames(model$wgt.matrix))
      parsed$model <- as.list(model)
    } else {
      summary$no_result_rows <- 1L
      if(!file.exists("out/result.hsq"))stop("Original training exited without model or requested heritability output")
    }
  } else {
    required <- if(isTRUE(originalOptions$ldsc))c("out/result.l2.ldscore.gz","out/result.l2.M_5_50") else if(isTRUE(originalOptions$omnibus)||!is.na(originalOptions$omnibus_corr))c("out/result.CORR.dat",if(is.na(originalOptions$omnibus_corr))"out/result.omnibus.pv") else c("out/result.joint_included.dat","out/result.joint_dropped.dat")
    if(!all(file.exists(required)))stop(paste("Original post-processing omitted required exports:",paste(setdiff(required,exports),collapse=",")))
    parsed$postprocess <- lapply(required,function(p)if(grepl("M_5_50$",p))readLines(p,warn=FALSE) else read.table(p,header=TRUE,as.is=TRUE,check.names=FALSE))
    names(parsed$postprocess) <- required
    for(table in parsed$postprocess) if(is.data.frame(table)) {
      pField <- intersect(c("JOINT.P","COND.P","OMNIBUS.P"),names(table))
      if(length(pField)) {
        values <- table[[pField[1]]]
        valid <- is.finite(values)&values>=0&values<=1
        summary$finite_result_rows <- summary$finite_result_rows+sum(valid)
        summary$no_result_rows <- summary$no_result_rows+sum(!valid)
      } else if("CORR" %in% names(table)) {
        valid <- is.finite(table$CORR)&abs(table$CORR)<=1+1e-8
        summary$finite_result_rows <- summary$finite_result_rows+sum(valid)
        summary$no_result_rows <- summary$no_result_rows+sum(!valid)
      } else summary$finite_result_rows <- summary$finite_result_rows+nrow(table)
    }
  }
  if(summary$selected_features==0L && !isTRUE(cfg$admission$allow_empty))stop("Complete empty original selection requires explicit allow_empty")
  if(summary$finite_result_rows+summary$no_result_rows==0L && !isTRUE(cfg$admission$allow_empty))stop("Original exports contain no scientific result rows; require explicit allow_empty")
  if((summary$no_result_rows>0L||summary$missing_requested_coloc_rows>0L)&&!isTRUE(cfg$admission$allow_no_result_rows))stop("Original no-result/failed COLOC rows require explicit allow_no_result_rows; retained raw outputs are unadmitted")
  saveRDS(list(summary=summary,original_options=originalOptions,original_exports=parsed),"audit.rds")
  writeJSON(summary,"summary.json")
  outcome("COMPLETED","Original FUSION exports and parsed result relationships checked; read source warnings/no-results")
},error=function(e)fail(conditionMessage(e)))
