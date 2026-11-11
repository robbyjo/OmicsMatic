.R_param_splitor <- "\027"
.R_flist_splitor <- "\026"
.filepath_maximum_len <- 990L

.stop_quietly <- function() {
  opt <- options(show.error.messages = FALSE)
  on.exit(options(opt))
  stop()
}

.is.64bit.system <- function(){
    if(grepl("[-_]64",Sys.info()[["machine"]]))return(TRUE)
    if(grepl("aarch64",Sys.info()[["machine"]]))return(TRUE)
    if(grepl("arm64",Sys.info()[["machine"]]))return(TRUE)
    
    return(FALSE)
}

.check_string_param <- function(argu, opt=NULL){
  if(!is.null(argu)){
    if(any(grepl(.R_param_splitor,  argu))){
      stop(paste0("Error: the argument for ",opt," contains the internal splitor of featureCounts (\\027). The \\027 character is unallowd in this parameter."))
    }
  }
}

.check_and_NormPath<- function(files, mustWork=F, opt=NULL){
  if(any(is.na(files)) || any(is.null(files)) || any(class(files) != "character")){
    if( is.null(opt) ){
      if(any(is.na(files)) || any(is.null(files))){
        stop("Error: the file name is NA or NULL.")
      }else{
        stop(paste0("Error: the file name must be a character vector. The current input is ",class(files)))
      }
    }else{
      if(any(is.na(files)) || any(is.null(files))){
        stop(paste0("Error: the argument to '",opt,"' is NA or NULL."))
      }else{
        stop(paste0("Error: the argument to '",opt,"' must be a character vector. The current input is ",class(files)))
      }
    }
  }
  rv <- normalizePath(files, mustWork = mustWork)
  if(any(grepl(.R_param_splitor, rv))){
    stop(paste0("Error: the file path to '",opt,"' contains the internal splitor of featureCounts (\\027). The \\027 character is unallowd in the file names or in the paths."))
  }

  if(any(grepl("\t", rv))){
    stop(paste0("Error: the file path to '",opt,"' contains <TAB> character(s). The TAB character is unallowd in the file names or in the paths."))
  }

  if(any(grepl(.R_flist_splitor, rv))){
    stop(paste0("Error: the file path to '",opt,"' contains the internal splitor of featureCounts (\\026). The \\026 character is unallowd in the file names or in the paths."))
  }

  if(any(nchar(rv)>=.filepath_maximum_len)){
    errv <- rv[ nchar(rv)>=.filepath_maximum_len ]
    stop(paste0("Error: the file name and path of ",opt," is longer than ",.filepath_maximum_len," bytes. Beaware that all the file names are normalized to include the full path to the original file. The value is '", errv[1],"'"))
  }
  return(rv)
}

.flatten.and.numeric <- function(logicarr){
    paste0(as.numeric(logicarr),collapse="")
}

.get.annotation.file <- function(annot.inbuilt, annot.ext, isGTFAnnotationFile){
    delete.annot.file <- FALSE
    annot.screen.output <- 'R data.frame'
    if(is.null(annot.ext)){
      switch(tolower(as.character(annot.inbuilt)),
        mm9={
          ann <- system.file("annot","mm9_RefSeq_exon.txt",package="Rsubread")
          annot.screen.output <- 'inbuilt (mm9)'
          cat("NCBI RefSeq annotation for mm9 (build 37.2) is used.\n")
        },
        mm10={
          ann <- system.file("annot","mm10_RefSeq_exon.txt",package="Rsubread")
          annot.screen.output <- 'inbuilt (mm10)'
          cat("NCBI RefSeq annotation for mm10 (build 38.1) is used.\n")
         },
        mm39={
          ann <- system.file("annot","mm39_RefSeq_exon.txt.gz",package="Rsubread")
          annot.screen.output <- 'inbuilt (mm39)'
          cat("NCBI RefSeq annotation for mm39 (build 39) is used.\n")
         },
        hg19={
          ann <- system.file("annot","hg19_RefSeq_exon.txt",package="Rsubread")
          annot.screen.output <- 'inbuilt (hg19)'
          cat("NCBI RefSeq annotation for hg19 (build 37.2) is used.\n")
           },
        hg38={
          ann <- system.file("annot","hg38_RefSeq_exon.txt",package="Rsubread")
          annot.screen.output <- 'inbuilt (hg38)'
          cat("NCBI RefSeq annotation for hg38 (build 38.2) is used.\n")
           },
           {
        stop("In-built annotation for ", annot.inbuilt, " is not available.\n")
           }
      ) # end switch
    }
    else{
      if(is.character(annot.ext)){
        annot.ext <- .check_and_NormPath(annot.ext, mustWork=T, opt="external annotation")
        ann <- annot.ext
        annot.screen.output <- paste0(basename(ann), " (", ifelse(isGTFAnnotationFile, "GTF", "SAF"), ")");
      }
      else{
        annot_df <- as.data.frame(annot.ext,stringsAsFactors=FALSE)
        if(sum(c("geneid","chr","start","end", "strand") %in% tolower(colnames(annot_df))) != 5)
          stop("One or more required columns are missing in the provided annotation data. Please refer to help page for annotation format.\n")
        colnames(annot_df) <- tolower(colnames(annot_df))
        annot_df <- data.frame(geneid=annot_df$geneid,chr=annot_df$chr,start=annot_df$start,end=annot_df$end,strand=annot_df$strand,stringsAsFactors=FALSE)
        annot_df$chr <- as.character(annot_df$chr)
        fout_annot <- file.path(".",paste(".Rsubread_UserProvidedAnnotation_pid",Sys.getpid(),sep=""))
        oldScipen <- options(scipen=999)
        write.table(x=annot_df,file=fout_annot,sep="\t",row.names=FALSE,quote=FALSE)
        options(oldScipen)
        ann <- fout_annot
        delete.annot.file <- TRUE
      }
    }
    return(list(ann=ann, screen=annot.screen.output, delete=delete.annot.file))
}


featureCounts <- function(files,annot.inbuilt="mm39",annot.ext=NULL,isGTFAnnotationFile=FALSE,GTF.featureType="exon",GTF.attrType="gene_id",GTF.attrType.extra=NULL,chrAliases=NULL,useMetaFeatures=TRUE,allowMultiOverlap=FALSE,minOverlap=1,fracOverlap=0,fracOverlapFeature=0,largestOverlap=FALSE,nonOverlap=NULL,nonOverlapFeature=NULL,readShiftType="upstream",readShiftSize=0,readExtension5=0,readExtension3=0,read2pos=NULL,countMultiMappingReads=TRUE,fraction=FALSE,isLongRead=FALSE,minMQS=0,splitOnly=FALSE,nonSplitOnly=FALSE,primaryOnly=FALSE,ignoreDup=FALSE,strandSpecific=0,juncCounts=FALSE,genome=NULL,isPairedEnd=FALSE,countReadPairs=TRUE,requireBothEndsMapped=FALSE,checkFragLength=FALSE,minFragLength=50,maxFragLength=600,countChimericFragments=TRUE,autosort=TRUE,nthreads=1,byReadGroup=FALSE,reportReads=NULL,reportReadsPath=NULL,tmpDir=".",verbose=FALSE)
{
    delete.annot.file <- FALSE
    if(!.is.64bit.system()) warning("your system seems to be 32-bit. Rsubread supports 32-bit systems to a limited level only.\nWe recommend that Rsubread be run on 64-bit systems to avoid any possible problems.\n\n",call.=FALSE)

    extra.params <- .retrieve.tmp.parameters()
    sampleSheet <- NULL
    generate.scRNA.BAM <- TRUE
    cellBarcodeList <- NULL
    umi.cutoff <- NULL
    BAM_is_ScRNA_Fastq <- FALSE
    BAM_is_ScRNA_BAM <- FALSE
    BAM_is_Rerun_Persample <- FALSE
    Is_Dual_Index <- FALSE
    BAM_file_no <- 0

    if("sampleSheet" %in% names(extra.params)) sampleSheet <- extra.params[["sampleSheet"]]
    if("generate.scRNA.BAM" %in% names(extra.params)) generate.scRNA.BAM <- extra.params[["generate.scRNA.BAM"]]
    if("cellBarcodeList" %in% names(extra.params)) cellBarcodeList <- extra.params[["cellBarcodeList"]]
    if("BAM_is_ScRNA_Fastq" %in% names(extra.params)) BAM_is_ScRNA_Fastq <- extra.params[["BAM_is_ScRNA_Fastq"]]
    if("BAM_is_ScRNA_BAM" %in% names(extra.params)) BAM_is_ScRNA_BAM <- extra.params[["BAM_is_ScRNA_BAM"]]
    if("BAM_is_Rerun_Persample" %in% names(extra.params)) BAM_is_Rerun_Persample <- extra.params[["BAM_is_Rerun_Persample"]]
    if("umi.cutoff" %in% names(extra.params)) umi.cutoff <- extra.params[["umi.cutoff"]]
    if("Is_Dual_Index" %in% names(extra.params)) Is_Dual_Index <- extra.params[["Is_Dual_Index"]]
    if("BAM_file_no" %in% names(extra.params)) BAM_file_no <- extra.params[["BAM_file_no"]]

    .check_string_param(annot.inbuilt, "annot.inbuilt")
    .check_string_param(GTF.featureType, "GTF.featureType")
    .check_string_param(GTF.attrType, "GTF.attrType")
    .check_string_param(GTF.attrType.extra, "GTF.attrType.extra")
    .check_string_param(readShiftType, "readShiftType")
    .check_string_param(sampleSheet, "sampleSheet")
    .check_string_param(cellBarcodeList, "cellBarcodeList")

    if(length(countReadPairs)>1){
        if(length(countReadPairs) != length(files))stop("The argumet for countReadPairs is a vector, but it has a different length to the files vector.")
        if(length(isPairedEnd)>1 && length(countReadPairs)!=length(isPairedEnd))stop("The argumets for countReadPairs and isPairedEnd are both vectors, but they have different lengths.")
    }
    if(length(isPairedEnd)>1){
        if(length(isPairedEnd) != length(files))stop("The argumet for isPairedEnd is a vector, but it has a different length to the files vector.")
        countReadPairs <- ifelse( isPairedEnd, countReadPairs, F )
    }else{
        if(!isPairedEnd) countReadPairs <- F
    }

    if(length(GTF.featureType)>1) GTF.featureType<-paste(GTF.featureType, collapse=",")
    out.base.names <- basename(files)
    if(any(duplicated(out.base.names))){
      out.col.names <- files
    }else{
      out.col.names <- out.base.names
    }
    #out.col.names <-gsub("[[:punct:]]+", ".", out.col.names)
    #out.col.names <-gsub(" ", ".", out.col.names)

    if(!is.character(files)) stop("files must be a character vector of file paths")
    files <- .check_and_NormPath(files, mustWork=T, opt="files")
    if(!is.null(annot.ext) && is.character(annot.ext)) annot.ext <- .check_and_NormPath(annot.ext, mustWork=T, opt="annot.ext")
    if(!is.null(chrAliases))chrAliases <- .check_and_NormPath(chrAliases, mustWork=T, opt="chrAliases")
    if(!is.null(genome)) genome <- .check_and_NormPath(genome, mustWork=T, opt="genome")
    if(!is.null(cellBarcodeList)) cellBarcodeList <- .check_and_NormPath(cellBarcodeList, mustWork=T, opt="cellBarcodeList")
    if(!is.null(sampleSheet)) sampleSheet <- .check_and_NormPath(sampleSheet, mustWork=T, opt="sampleSheet")
    if(!is.null(reportReadsPath)){
        reportReadsPath <- .check_and_NormPath(reportReadsPath, mustWork=T, opt="reportReadsPath")
    }else reportReadsPath <- ' '
    strandSpecific<-as.character(strandSpecific)
    strandSpecific<-paste(strandSpecific, collapse=".")
    strandSpecific<-gsub(",", ".", strandSpecific)

    annlist <- .get.annotation.file(annot.inbuilt, annot.ext, isGTFAnnotationFile)
    ann <- annlist$ann
    annot.screen.output <- annlist$screen
    delete.annot.file <- annlist$delete

    if(readShiftSize < 0){
        stop("The value of the readShiftSize parameter should not be negative.")
    }

    readShiftType <- match.arg(readShiftType, c("upstream","downstream","left","right"))

    if(readExtension5 < 0){
        stop("The value of the readExtension5 parameter should not be negative.")
    }
    if(readExtension3 < 0){
        stop("The value of the readExtension3 parameter should not be negative.")
    }

    fout <- file.path(".",paste(".Rsubread_featureCounts_pid",Sys.getpid(),sep=""))

    files_C <- paste(files,collapse=.R_flist_splitor)
    
    if(nchar(files_C) == 0) stop("No read files provided!")

    genome_C <- genome
    if(is.null(genome))
      genome_C <- " "
    
    chrAliases_C <- chrAliases
    if(is.null(chrAliases))
      chrAliases_C <- " "

    read2pos_C <- read2pos
    if(is.null(read2pos)) read2pos_C <- 0

    split_C <- 0
    if(splitOnly) split_C <- 1
    if(nonSplitOnly) split_C <- 2

    PE_orientation <- "fr"
    
    if(is.null(reportReads)) {
        reportReads_C <- 0
    } else if(reportReads == "CORE") {
        reportReads_C <- 10
    } else if(reportReads == "SAM") {
        reportReads_C <- 50
    } else if(reportReads == "BAM") {
        reportReads_C <- 500
    } else
        stop("Invalid value was provided for reportReads parameter.")

    do_detection_calls <- FALSE
    max_missing_bases_in_read <- -1
    max_missing_bases_in_feature <- -1
    GTF.attrType.extra_str <- " "
    if(!is.null(nonOverlap)) max_missing_bases_in_read <- nonOverlap
    if(!is.null(nonOverlapFeature)) max_missing_bases_in_feature <- nonOverlapFeature
    if(!is.null(GTF.attrType.extra))GTF.attrType.extra_str <- paste(GTF.attrType.extra, collapse="\t")
    if(is.null(sampleSheet)) sampleSheet<-' '
    if(is.null(cellBarcodeList)) cellBarcodeList<-' '
      

    maxMOp <- 65536
    #print(.flatten.and.numeric(countReadPairs))
    #print(.flatten.and.numeric(isPairedEnd))
    cmd <- paste("readSummary",ann,files_C,fout,.flatten.and.numeric(countReadPairs),minFragLength,maxFragLength,0,as.numeric(allowMultiOverlap),as.numeric(useMetaFeatures),nthreads,as.numeric(isGTFAnnotationFile),strandSpecific,reportReads_C,as.numeric(requireBothEndsMapped),as.numeric(!countChimericFragments),as.numeric(checkFragLength),GTF.featureType,GTF.attrType,minMQS,as.numeric(countMultiMappingReads),chrAliases_C," ",as.numeric(FALSE),14,readExtension5,readExtension3,minOverlap,split_C,read2pos_C," ",as.numeric(ignoreDup),as.numeric(!autosort),as.numeric(fraction),as.numeric(largestOverlap),PE_orientation,as.numeric(juncCounts),genome_C,maxMOp,0,as.numeric(fracOverlap),as.character(tmpDir),"0",as.numeric(byReadGroup),as.numeric(isLongRead),as.numeric(verbose),as.numeric(fracOverlapFeature), as.numeric(do_detection_calls), as.numeric(max_missing_bases_in_read), as.numeric(max_missing_bases_in_feature), as.numeric(primaryOnly), reportReadsPath, GTF.attrType.extra_str, annot.screen.output, readShiftType,readShiftSize, sampleSheet, cellBarcodeList ,.flatten.and.numeric(isPairedEnd), as.numeric(generate.scRNA.BAM), ifelse(BAM_is_ScRNA_Fastq, 4, ifelse(BAM_is_ScRNA_BAM, 5,3)), as.numeric(BAM_is_Rerun_Persample), ifelse(is.null(umi.cutoff), -1, umi.cutoff),ifelse(Is_Dual_Index,"Dual-Index","Single-Index"),as.numeric(BAM_file_no),sep=.R_param_splitor)
    #print(cmd)
    n <- length(unlist(strsplit(cmd, .R_param_splitor )))
    C_args <- .C("R_readSummary_wrapper",as.integer(n),as.character(cmd),PACKAGE="Rsubread")

    if(file.exists(fout)){
        add_attr_numb <- 0
        if(!is.null(GTF.attrType.extra)) add_attr_numb <- length(GTF.attrType.extra)

        x <- read.delim(fout,stringsAsFactors=FALSE, sep="\t")
        colnames(x)[1:6] <- c("GeneID","Chr","Start","End","Strand","Length")
        colnames(x)[(7 + add_attr_numb ): (add_attr_numb + 6+length(out.col.names)) ] <- out.col.names
        x_summary <- read.delim(paste(fout,".summary",sep=""), stringsAsFactors=FALSE, sep="\t")
        colnames(x_summary)[2:ncol(x_summary)] <- out.col.names

        if(juncCounts){
            x_jcounts <- read.delim(paste(fout,".jcounts",sep=""), stringsAsFactors=FALSE, sep="\t")
            colnames(x_jcounts)[-(1:14)]  <- out.col.names
        }

        file.remove(fout)
        file.remove(paste(fout,".summary",sep=""))

        if(juncCounts)
            file.remove(paste(fout,".jcounts",sep=""))
        
        if(delete.annot.file) 
          file.remove(ann)

        if(ncol(x) <= (6 + add_attr_numb)){
          cat("No count data were generated.\n")
          .stop_quietly()
        }
        
        y <- as.matrix(x[,-c(1:(6 + add_attr_numb))])
        colnames(y) <- colnames(x)[-c(1:(6 + add_attr_numb))]
        rownames(y) <- x$GeneID

        if(juncCounts)
            z <- list(counts=y,counts_junction=x_jcounts,annotation=x[,1:(6 + add_attr_numb)],targets=out.col.names,stat=x_summary)
        else
            z <- list(counts=y,annotation=x[,1:(6 + add_attr_numb)],targets=out.col.names,stat=x_summary)    
        z    
    }else{
        cat("No counts were generated.\n")
        .stop_quietly()
    }
}

