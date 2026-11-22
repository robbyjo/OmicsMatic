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
  matrixCapacity <- function(a,b,role) {
    if(as.double(a)*as.double(b)>cfg$limits$maximum_matrix_cells)stop(paste(role,"matrix-cell capacity exceeded before original allocation; preserve inputs and explicitly raise the limit only with sufficient memory"))
  }
  if(cfg$operation=="TRAIN") {
    sourceModels <- strsplit(originalOptions$models,",",fixed=TRUE)[[1]]
    if(is.na(originalOptions$hsq_set)||any(sourceModels %in% c("blup","bslmm")))matrixCapacity(nrow(fam),nrow(fam),"Training sample/GRM")
    matrixCapacity(nrow(bim),length(sourceModels),"Training weight")
    if(originalOptions$crossval>0)matrixCapacity(nrow(fam),length(sourceModels),"Cross-validation prediction")
  }
  if(cfg$operation=="ASSOCIATE"&&!is.na(originalOptions$perm)&&originalOptions$perm>0)matrixCapacity(1,originalOptions$perm,"Permutation vector")
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
      # The original derives panel labels from dirname(FILE); flattening changes results.
      label <- unlist(lapply(strsplit(dirname(name),"/"),tail,1))
      if(length(label)!=1L)stop("Original FILE has no unique panel-directory label")
      label <- portable(label);filename <- portable(basename(name))
      if(grepl("/",label,fixed=TRUE)||grepl("/",filename,fixed=TRUE))stop("Selected panel/basename is not one portable component")
      needed[[i]] <- list(row=postRows[i],source=path,target=sprintf("inputs/weights/%08d/%s/%s",i,label,filename),panel_label=label)
    }
  }
  if(length(needed)>cfg$limits$maximum_features)stop("Selected feature capacity exceeded")
  if(cfg$operation=="POSTPROCESS") {
    matrixCapacity(length(postRows),length(postRows),"Predicted-feature correlation")
    matrixCapacity(nrow(fam),length(postRows),"Predicted-feature sample")
    if(isTRUE(originalOptions$ldsc))matrixCapacity(nrow(bim),length(postRows),"LDSC SNP-feature")
  }
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
    matched <- sum(!is.na(match(model$snps[,2],bim[,2])))
    if(cfg$operation=="ASSOCIATE")matrixCapacity(matched,matched,"Reference-matched weight LD")
    weightObjects[[i]] <- list(target=needed[[i]]$target,names=as.list(names),variants=nrow(model$wgt.matrix),matched_reference_variants=matched,models=as.list(colnames(model$wgt.matrix)))
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
    referenceLabels <- function(x)unlist(lapply(strsplit(dirname(x),"/"),tail,1))
    if(!identical(referenceLabels(featureTable$FILE[postRows]),referenceLabels(restored$FILE[postRows])))stop("Post-process path transport changed original derived reference labels")
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
    tables <- lapply(c(required,if(file.exists(paste0(required,".MHC")))paste0(required,".MHC")),function(p)read.table(p,header=TRUE,sep="\t",quote="",comment.char="",strip.white=TRUE,as.is=TRUE,check.names=FALSE))
    results <- do.call(rbind,tables)
    if(nrow(results)!=nrow(featureTable))stop("Original association row population differs from selected weight rows")
    expected <- paste(as.character(featureTable$ID),paste0(cfg$inputs$weights_dir,"/",as.character(featureTable$WGT)),sep="\t")
    observed <- paste(as.character(results$ID),as.character(results$FILE),sep="\t")
    if(!identical(sort(expected),sort(observed)))stop("Original association ID/weight-file identities differ")
    if(!file.exists("source-association-state.rds"))stop("Original association lacks its normal-return unrounded state companion")
    sourceState <- readRDS("source-association-state.rds");unrounded <- sourceState$association
    if(!is.data.frame(unrounded)||nrow(unrounded)!=nrow(results)||!identical(sourceState$source_options,originalOptions)||!is.logical(sourceState$mhc)||length(sourceState$mhc)!=nrow(unrounded)||anyNA(sourceState$mhc))stop("Original association state/options/MHC population differs")
    sameFile <- function(a,b) {
      if(file.info(a)$size!=file.info(b)$size)return(FALSE)
      left <- file(a,"rb");right <- file(b,"rb");on.exit({close(left);close(right)})
      repeat {x<-readBin(left,"raw",65536L);y<-readBin(right,"raw",65536L);if(!identical(x,y))return(FALSE);if(!length(x))return(TRUE)}
    }
    write.table(format(unrounded[!sourceState$mhc,,drop=FALSE],digits=3),"source-format-main.tsv",quote=FALSE,row.names=FALSE,sep="\t")
    if(!sameFile("out/result","source-format-main.tsv"))stop("Unrounded original state does not reconstruct the exact original association export")
    if(any(sourceState$mhc)) {
      write.table(format(unrounded[sourceState$mhc,,drop=FALSE],digits=3),"source-format-mhc.tsv",quote=FALSE,row.names=FALSE,sep="\t")
      if(!file.exists("out/result.MHC")||!sameFile("out/result.MHC","source-format-mhc.tsv"))stop("Unrounded original state does not reconstruct the exact original MHC export")
    } else if(file.exists("out/result.MHC"))stop("Unexpected original MHC export")
    tokens <- function(frame) {
      key<-paste(as.character(frame$ID),as.character(frame$FILE),sep="\t")
      paste(key,ave(seq_along(key),key,FUN=seq_along),sep="\t")
    }
    order <- match(tokens(results),tokens(unrounded));if(anyNA(order))stop("Unrounded/exported association row identities differ")
    unrounded <- unrounded[order,,drop=FALSE]
    summary$source_csv_rounding_verified <- TRUE;summary$unrounded_source_state_captured <- TRUE
    summary$coloc_rounded_gate_disagreements <- 0L;gateDisagreement <- rep(FALSE,nrow(results))
    finite <- is.finite(results$TWAS.Z)&is.finite(results$TWAS.P)&results$TWAS.P>=0&results$TWAS.P<=1
    summary$finite_result_rows <- sum(finite);summary$no_result_rows <- sum(!finite)
    summary$original_p_zero <- sum(results$TWAS.P==0,na.rm=TRUE)
    missingColoc <- rep(FALSE,nrow(results))
    if(!is.na(originalOptions$coloc_P)) {
      fields <- paste0("COLOC.PP",0:4);if(!all(fields %in% names(results)))stop("Requested original COLOC fields absent")
      values <- as.matrix(results[,fields,drop=FALSE]);if(any(is.finite(values)&(values<0|values>1)))stop("Original COLOC probability outside [0,1]")
      eligible <- finite&unrounded$TWAS.P<originalOptions$coloc_P
      gateDisagreement <- gateDisagreement | (eligible != (finite&results$TWAS.P<originalOptions$coloc_P))
      missingColoc <- missingColoc | (eligible & rowSums(!is.finite(values))>0)
    }
    if(!is.na(originalOptions$coloc_susie_P)) {
      fields <- c("COLOC.SUSIE.HIT1","COLOC.SUSIE.HIT2",paste0("COLOC.SUSIE.PP",0:4))
      if(!all(fields %in% names(results)))stop("Requested original coloc.susie fields absent")
      gate <- finite&unrounded$TWAS.P<originalOptions$coloc_susie_P
      gateDisagreement <- gateDisagreement | (gate != (finite&results$TWAS.P<originalOptions$coloc_susie_P))
      eligible <- which(gate);validSusie <- rep(FALSE,nrow(results))
      for(i in eligible) {
        values <- lapply(results[i,fields,drop=FALSE],function(x)if(is.na(x)||!nzchar(trimws(as.character(x))))character() else strsplit(as.character(x),",",fixed=TRUE)[[1]])
        if(any(lengths(values)==0L)){missingColoc[i] <- TRUE;next}
        if(length(unique(lengths(values)))!=1L)stop("Original coloc.susie hit/posterior tuple lengths differ")
        posterior <- suppressWarnings(as.double(unlist(values[3:7])))
        if(any(is.finite(posterior)&(posterior<0|posterior>1)))stop("Original coloc.susie posterior outside [0,1]")
        if(any(!is.finite(posterior))||any(is.na(unlist(values[1:2]))))missingColoc[i] <- TRUE else validSusie[i] <- TRUE
      }
      if(any(validSusie)&&!file.exists("out/result.coloc.susie.summary"))stop("Original coloc.susie tuples lack their full summary export")
    }
    summary$missing_requested_coloc_rows <- sum(missingColoc)
    summary$coloc_rounded_gate_disagreements <- sum(gateDisagreement)
    if(!is.na(originalOptions$perm)&&originalOptions$perm>0) {
      fields <- c("PERM.PV","PERM.N","PERM.ANL_PV");if(!all(fields %in% names(results)))stop("Requested original permutation fields absent")
      count <- results$PERM.N
      if(any(!is.finite(count)|count<0|count>originalOptions$perm+1|count!=floor(count)))stop("Original permutation count outside source bounds")
      tested <- count>0
      if(any(tested&(!is.finite(results$PERM.PV)|results$PERM.PV<0|results$PERM.PV>1)))stop("Original tested permutation p-value is invalid")
      summary$permutation_tests <- sum(tested)
      summary$permutation_analytical_missing_rows <- sum(tested&!is.finite(results$PERM.ANL_PV))
    }
    parsed$association <- results;parsed$unrounded_source_state <- sourceState
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
      } else if("LD" %in% names(table)) {
        if(!identical(names(table),c("CHR","SNP","BP","CM","MAF","LD"))||nrow(table)!=nrow(bim)||!identical(as.character(table$SNP),as.character(bim[,2])))stop("Original LDSC schema/SNP population differs")
        valid <- is.finite(table$LD)&is.finite(table$MAF)&table$MAF>=0&table$MAF<=1
        summary$finite_result_rows <- summary$finite_result_rows+sum(valid)
        summary$no_result_rows <- summary$no_result_rows+sum(!valid)
      } else summary$finite_result_rows <- summary$finite_result_rows+nrow(table)
    }
    if(isTRUE(originalOptions$ldsc)) {
      count <- readLines("out/result.l2.M_5_50",warn=FALSE)
      if(length(count)!=1L||trimws(count)!=as.character(length(postRows)))stop("Original LDSC annotation count differs from selected features")
    }
  }
  if(summary$selected_features==0L && !isTRUE(cfg$admission$allow_empty))stop("Complete empty original selection requires explicit allow_empty")
  if(cfg$operation=="POSTPROCESS") {
    pdfHeader <- function(path) {
      con <- file(path,"rb");on.exit(close(con));if(!identical(readBin(con,"raw",5L),charToRaw("%PDF-")))stop("Original requested plot is not a PDF export")
    }
    locusPlots <- grep("^out/result\\.loc_[0-9]+\\.pdf$",exports,value=TRUE)
    if(isTRUE(originalOptions$plot)) {
      if(!length(locusPlots)&&!isTRUE(cfg$admission$allow_empty))stop("Original requested locus plots are empty; require explicit allow_empty")
      lapply(locusPlots,pdfHeader);summary$original_locus_plot_files <- as.list(locusPlots)
    }
    if(isTRUE(originalOptions$plot_corr)) {
      if(!file.exists("out/result.corrplot.pdf"))stop("Original requested correlation plot is absent")
      pdfHeader("out/result.corrplot.pdf")
    }
    if(isTRUE(originalOptions$report)) {
      if(!file.exists("out/result.report"))stop("Original requested locus report is absent")
      report <- read.table("out/result.report",header=TRUE,sep="\t",as.is=TRUE,check.names=FALSE)
      if(!nrow(report)&&!isTRUE(cfg$admission$allow_empty))stop("Original requested locus report has no rows; require explicit allow_empty")
      summary$original_report_loci <- nrow(report);parsed$locus_report <- report
    }
    if(isTRUE(originalOptions$save_loci)) {
      loci <- grep("^out/result\\.loc_[0-9]+\\.cond$",exports,value=TRUE)
      if(!length(loci)&&!isTRUE(cfg$admission$allow_empty))stop("Original requested conditional-locus exports are empty; require explicit allow_empty")
      summary$original_conditional_locus_files <- as.list(loci)
    }
  }
  if(summary$finite_result_rows+summary$no_result_rows==0L && !isTRUE(cfg$admission$allow_empty))stop("Original exports contain no scientific result rows; require explicit allow_empty")
  if((summary$no_result_rows>0L||summary$missing_requested_coloc_rows>0L)&&!isTRUE(cfg$admission$allow_no_result_rows))stop(sprintf("Original no_result_rows=%d and missing_requested_coloc_rows=%d require explicit allow_no_result_rows to retain source absences. Restore complete/matching inputs for a new analysis or explicitly retain/report the no-results; raw outputs remain unadmitted",summary$no_result_rows,summary$missing_requested_coloc_rows))
  saveRDS(list(summary=summary,original_options=originalOptions,original_exports=parsed),"audit.rds")
  writeJSON(summary,"summary.json")
  outcome("COMPLETED","Original FUSION exports and parsed result relationships checked; read source warnings/no-results")
},error=function(e)fail(conditionMessage(e)))
