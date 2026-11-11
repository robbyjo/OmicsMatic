.write.tmp.parameters <- function(param){
  temp.file.prefix <- file.path(".",paste(".Rsubread_PARAM_cellCounts_",Sys.getpid(),sep=""))
  param$PMR_CREATE_TIME <- Sys.time()
  saveRDS(param, temp.file.prefix)
}

.retrieve.tmp.parameters <- function(){
  temp.file.prefix <- file.path(".",paste(".Rsubread_PARAM_cellCounts_",Sys.getpid(),sep=""))
  if(!file.exists(temp.file.prefix)) return (NA)
  rv <- readRDS(temp.file.prefix)
  file.remove(temp.file.prefix)
  if( Sys.time() - rv$PMR_CREATE_TIME > 5) return (NA) # the file should be created no longer than 5 seconds ago
  rv
}

.index.names.to.sheet.BAM.mode<-function(fqtab, fname){
  lines <- paste(fqtab$BAMFile, fqtab$SampleName , sep=",")
  fileConn<-file(fname)
  writeLines(c("EMFileVersion,4","[data]","BAMFile,Sample_Name",lines), fileConn)
  close(fileConn)
}

.index.names.to.sheet.FASTQ.mode<-function(fqtab, fname){
  lines <- paste(fqtab$BarcodeUMIFile, fqtab$ReadFile, fqtab$SampleName , sep=",")
  fileConn<-file(fname)
  writeLines(c("EMFileVersion,4","[data]","BarcodeUMIFile,ReadFile,Sample_Name",lines), fileConn)
  close(fileConn)
}

.index.names.to.sheet.raw.dir.mode<-function(dirname, nametab, fname, sample.name=NA, which.dual.index=NULL){
  lanes <- c()
  index.names <- c()
  sample.names <- c()
  index.seq <- c()
  is_dual_index <- c() 
  Aarr <- c()
  Barr <- c()
  if(F==is.null(which.dual.index) && is.na(which.dual.index))which.dual.index<-NULL
  for(cli in 1:nrow(nametab)){
    if(nametab$InputDirectory[cli]!=dirname)next
    if((!is.na(sample.name)) && as.character(nametab$SampleName[cli])!=sample.name)next
    seqs.list <- .convert.sample_index.id.to.seq(nametab$IndexSetName[cli])
    if(!is.null(which.dual.index)){
       seqs.list <- seqs.list[[which.dual.index]]
    }
    for(seq in unlist(seqs.list)){
      is_dual_index <- c(is_dual_index , nchar(seq)>12)
      lanes <-c(lanes, nametab$Lane[cli])
      index.names <-c(index.names, nametab$IndexSetName[cli])
      sample.names <- c(sample.names, as.character(nametab$SampleName[cli]))
      index.seq <- c(index.seq, seq)
    }
    if(any(is_dual_index) && is.null(which.dual.index)){
      Aarr <- c(Aarr, seqs.list$A)
      Barr <- c(Barr, seqs.list$B)
    }
  }
  sdf<-data.frame(Lane=lanes, Sample_ID=index.names, Sample_Name=sample.names, index=index.seq, Sample_Project=rep("cellCounts", length(lanes)), stringsAsFactors=F)
  fileConn<-file(fname)
  lines <- paste( sdf$Lane, sdf$Sample_ID, sdf$Sample_Name, sdf$index, sdf$Sample_Project ,sep=",")
  writeLines(c("EMFileVersion,4","[data]","Lane,Sample_ID,Sample_Name,index,Sample_Project",lines), fileConn)
  close(fileConn)
  if(any(is_dual_index != is_dual_index[1])) stop("Dual-index and single-index samples cannot be mixed in the sample data-frame.")
  if(is_dual_index[1]) return(list(A=Aarr, B=Barr))
  return(NULL)
}

.convert.sample_index.id.to.seq <- function(spid){
  seqs <- .one.convert.sample_index.id.to.seq(spid)
  if(any(is.na(seqs))){
    spid <- gsub("-","_", spid)
    seqs <- .one.convert.sample_index.id.to.seq(spid)
  }
  if(any(is.na(seqs)))stop(paste("Unable to find sample index for", spid))
  seqs
}
.one.convert.sample_index.id.to.seq <- function(spid){
  spseqs <- NA
  if(spid == "SI_001") spseqs <- c('TCGCCATA', 'GTATACAC', 'AATGGTGG', 'CGCATGCT')
  if(spid == "SI_002") spseqs <- c('TATCCTCG', 'GCGAGGTC', 'CGCTTCAA', 'ATAGAAGT')
  if(spid == "SI_003") spseqs <- c('TGACGTCG', 'CTTGTGTA', 'ACGACCGT', 'GACTAAAC')
  if(spid == "SI_004") spseqs <- c('ATCTAGCT', 'GAGCGTAC', 'TCAGCCTG', 'CGTATAGA')
  if(spid == "SI_005") spseqs <- c('CCGTTCCC', 'ATACAGTT', 'TGTAGTAA', 'GACGCAGG')
  if(spid == "SI_006") spseqs <- c('TCAATTGG', 'AGTTAGAA', 'GAGCGCTT', 'CTCGCACC')
  if(spid == "SI_007") spseqs <- c('CTGCCTTG', 'ACTAGCCC', 'GGCTAGAT', 'TAAGTAGA')
  if(spid == "SI_008") spseqs <- c('GGCAGAAA', 'ACGGTTCT', 'CATTCGTC', 'TTACACGG')
  if(spid == "SI_P01_A1") spseqs <- c('TTGTAAGA', 'GGCGTTTC', 'CCTACCAT', 'AAACGGCG')
  if(spid == "SI_P01_B1") spseqs <- c('CTAGCTGT', 'GCCAACAA', 'AGGCTACC', 'TATTGGTG')
  if(spid == "SI_P01_C1") spseqs <- c('GATGCAGT', 'AGACTTTC', 'TTCTACAG', 'CCGAGGCA')
  if(spid == "SI_P01_D1") spseqs <- c('AGCTGCGT', 'GTGGAGCA', 'TCTATTAG', 'CAACCATC')
  if(spid == "SI_P01_E1") spseqs <- c('CGCCCGTA', 'GTTTGCCT', 'TAAGTTAG', 'ACGAAAGC')
  if(spid == "SI_P01_F1") spseqs <- c('TGACTAGT', 'GATAGGTA', 'CCGTACAG', 'ATCGCTCC')
  if(spid == "SI_P01_G1") spseqs <- c('CATATGCG', 'ATGCGATT', 'TCCGCTAC', 'GGATACGA')
  if(spid == "SI_P01_H1") spseqs <- c('TCTTGTCC', 'CGGGAGTA', 'GTCACAGG', 'AAACTCAT')
  if(spid == "SI_P01_A2") spseqs <- c('AGCCCTTT', 'TCTTAGGC', 'GTGAGAAG', 'CAAGTCCA')
  if(spid == "SI_P01_B2") spseqs <- c('GGTCGAGC', 'TTAGATTG', 'CCCACCCA', 'AAGTTGAT')
  if(spid == "SI_P01_C2") spseqs <- c('CCGAGAAC', 'TGCTCTGT', 'GTAGTGCG', 'AATCACTA')
  if(spid == "SI_P01_D2") spseqs <- c('ACATTCCG', 'GACACAAT', 'CTGCGGTA', 'TGTGATGC')
  if(spid == "SI_P01_E2") spseqs <- c('TCATCAAG', 'GTTGGTCC', 'AGGCTGGT', 'CACAACTA')
  if(spid == "SI_P01_F2") spseqs <- c('TGCCCGCT', 'GCAAACGC', 'CATTGATA', 'ATGGTTAG')
  if(spid == "SI_P01_G2") spseqs <- c('CGGTGAGC', 'ATAACCTA', 'TCCGAGCG', 'GATCTTAT')
  if(spid == "SI_P01_H2") spseqs <- c('CCGAACTC', 'AACGGTCA', 'TTATTGGT', 'GGTCCAAG')
  if(spid == "SI_P01_A3") spseqs <- c('AAAGCATA', 'GCCTTTAT', 'CTGCAGCC', 'TGTAGCGG')
  if(spid == "SI_P01_B3") spseqs <- c('TCATCCTT', 'ATTGGACG', 'CAGCTTAC', 'GGCAAGGA')
  if(spid == "SI_P01_C3") spseqs <- c('ACGTTACA', 'TTACCTAC', 'GACGACGG', 'CGTAGGTT')
  if(spid == "SI_P01_D3") spseqs <- c('GAGCACGC', 'CGAAGTTG', 'TTCGTGAA', 'ACTTCACT')
  if(spid == "SI_P01_E3") spseqs <- c('TCTGCAGG', 'CGGCTCCA', 'AACAAGTC', 'GTATGTAT')
  if(spid == "SI_P01_F3") spseqs <- c('TTAGGACC', 'AGTCTGTA', 'GCCTCCGT', 'CAGAATAG')
  if(spid == "SI_P01_G3") spseqs <- c('TACGAGTT', 'ATGTCCAG', 'GCTATAGC', 'CGACGTCA')
  if(spid == "SI_P01_H3") spseqs <- c('TTGGGCTT', 'GACAAACC', 'ACACCTAA', 'CGTTTGGG')
  if(spid == "SI_P01_A4") spseqs <- c('CATGGCAG', 'AGAACGCC', 'GTCTTTGA', 'TCGCAATT')
  if(spid == "SI_P01_B4") spseqs <- c('GACAGGCT', 'CCTCTAAC', 'AGGGACTG', 'TTATCTGA')
  if(spid == "SI_P01_C4") spseqs <- c('ACATTGGC', 'GAGCCCAT', 'CTTAGTCA', 'TGCGAATG')
  if(spid == "SI_P01_D4") spseqs <- c('AAATCGTC', 'GCGATCGG', 'CTTCGAAT', 'TGCGATCA')
  if(spid == "SI_P01_E4") spseqs <- c('GTAAACAT', 'TATCCTGA', 'AGCTGACG', 'CCGGTGTC')
  if(spid == "SI_P01_F4") spseqs <- c('GCATGATA', 'CGTCCTCT', 'AACGACAC', 'TTGATGGG')
  if(spid == "SI_P01_G4") spseqs <- c('CCTGCGGT', 'GTACAACG', 'AGCTTCTC', 'TAGAGTAA')
  if(spid == "SI_P01_H4") spseqs <- c('TTCCATCT', 'ACTGGAGC', 'CGGTCGTG', 'GAAATCAA')
  if(spid == "SI_P01_A5") spseqs <- c('CAGTCTGG', 'TCACACTC', 'ATTGGGAA', 'GGCATACT')
  if(spid == "SI_P01_B5") spseqs <- c('TCATGCGA', 'ATCGTACT', 'CATCAGTG', 'GGGACTAC')
  if(spid == "SI_P01_C5") spseqs <- c('TCAGTCAA', 'CACTGACT', 'ATGCATTC', 'GGTACGGG')
  if(spid == "SI_P01_D5") spseqs <- c('GTCGACTC', 'AGACGGAT', 'CCTTTAGA', 'TAGACTCG')
  if(spid == "SI_P01_E5") spseqs <- c('CCGTTGAA', 'TATGCTCT', 'ATCCAAGG', 'GGAAGCTC')
  if(spid == "SI_P01_F5") spseqs <- c('TCTGACTA', 'GTACGGCT', 'CGGTTTAG', 'AACACAGC')
  if(spid == "SI_P01_G5") spseqs <- c('ATGAAGTA', 'GAAGCTCG', 'TCTTTCGT', 'CGCCGAAC')
  if(spid == "SI_P01_H5") spseqs <- c('ATAGTATG', 'TATAAGGA', 'GGCTCCAC', 'CCGCGTCT')
  if(spid == "SI_P01_A6") spseqs <- c('CTTTCGAC', 'ACGGGACT', 'TGCATCTG', 'GAACATGA')
  if(spid == "SI_P01_B6") spseqs <- c('GCGCACCT', 'AACGCGAA', 'CTATTTGG', 'TGTAGATC')
  if(spid == "SI_P01_C6") spseqs <- c('CGCTCAGG', 'GAGGTTTA', 'ACTCAGAC', 'TTAAGCCT')
  if(spid == "SI_P01_D6") spseqs <- c('GAAGTCTT', 'TGCAGGGC', 'ATGCCAAA', 'CCTTATCG')
  if(spid == "SI_P01_E6") spseqs <- c('TGATGGCT', 'GCCATTTG', 'ATTGAAAC', 'CAGCCCGA')
  if(spid == "SI_P01_F6") spseqs <- c('GACTTCCT', 'TGAGGAAG', 'ATGCCGGC', 'CCTAATTA')
  if(spid == "SI_P01_G6") spseqs <- c('GTGCGACA', 'TCAGTGTT', 'AGCACTGG', 'CATTACAC')
  if(spid == "SI_P01_H6") spseqs <- c('AAGCATAA', 'CCCATCGC', 'TTAGCGCT', 'GGTTGATG')
  if(spid == "SI_P01_A7") spseqs <- c('CTCATCAT', 'TAACGTCC', 'AGGTCATA', 'GCTGAGGG')
  if(spid == "SI_P01_B7") spseqs <- c('TCCACACG', 'CTTCTGTT', 'GAATGCAC', 'AGGGATGA')
  if(spid == "SI_P01_C7") spseqs <- c('GGCGGAAT', 'ACACCGGG', 'CATAATCC', 'TTGTTCTA')
  if(spid == "SI_P01_D7") spseqs <- c('CCGGATCC', 'GGTCGCAT', 'TTAACGTG', 'AACTTAGA')
  if(spid == "SI_P01_E7") spseqs <- c('AAGACGTG', 'CCATGTGT', 'GTTCACAA', 'TGCGTACC')
  if(spid == "SI_P01_F7") spseqs <- c('GGTTAGAC', 'CAAACTTT', 'ACCCGAGA', 'TTGGTCCG')
  if(spid == "SI_P01_G7") spseqs <- c('GCCGGTAA', 'TGACTGCC', 'ATTACCGG', 'CAGTAATT')
  if(spid == "SI_P01_H7") spseqs <- c('TGGCACGA', 'AACGGGTG', 'CTAATTCT', 'GCTTCAAC')
  if(spid == "SI_P01_A8") spseqs <- c('GACTGTTC', 'ATGATACG', 'CCACAGAA', 'TGTGCCGT')
  if(spid == "SI_P01_B8") spseqs <- c('ACGTTCAC', 'TGCCCAGA', 'CAAGGTCT', 'GTTAAGTG')
  if(spid == "SI_P01_C8") spseqs <- c('TTTATCCC', 'GCACGTTT', 'CAGGAAGA', 'AGCTCGAG')
  if(spid == "SI_P01_D8") spseqs <- c('AATCTTTG', 'GGATGAGT', 'CTCAAGAC', 'TCGGCCCA')
  if(spid == "SI_P01_E8") spseqs <- c('GCTTACAT', 'TAGGGTGC', 'AGCCTATG', 'CTAACGCA')
  if(spid == "SI_P01_F8") spseqs <- c('AGTTGGGA', 'TACATTCT', 'CCAGAAAG', 'GTGCCCTC')
  if(spid == "SI_P01_G8") spseqs <- c('AAGTACTC', 'GGAACTCT', 'TCCCTGAG', 'CTTGGAGA')
  if(spid == "SI_P01_H8") spseqs <- c('AAGAGCGG', 'TCATAGCA', 'GGCCCATC', 'CTTGTTAT')
  if(spid == "SI_P01_A9") spseqs <- c('GAGTGCGT', 'CTCCAACA', 'ACAACTTG', 'TGTGTGAC')
  if(spid == "SI_P01_B9") spseqs <- c('AAGCGTGT', 'CTTGACCG', 'TGATTAAC', 'GCCACGTA')
  if(spid == "SI_P01_C9") spseqs <- c('AGATCGGT', 'CATCGTCG', 'GTCATATA', 'TCGGACAC')
  if(spid == "SI_P01_D9") spseqs <- c('CAAGGGAC', 'ACCTACTG', 'GGGACACA', 'TTTCTTGT')
  if(spid == "SI_P01_E9") spseqs <- c('AGTAAGCA', 'TACCGCGG', 'CCGGTAAT', 'GTATCTTC')
  if(spid == "SI_P01_F9") spseqs <- c('AGTTAGTT', 'GTACTTAA', 'CACGCACG', 'TCGAGCGC')
  if(spid == "SI_P01_G9") spseqs <- c('TTGACTTC', 'GCCGAAGT', 'CAATGGCA', 'AGTCTCAG')
  if(spid == "SI_P01_H9") spseqs <- c('GGAATATG', 'ACCTGCCA', 'CTTCATAC', 'TAGGCGGT')
  if(spid == "SI_P01_A10") spseqs <- c('ACAGCAAC', 'TTTCGCGA', 'CGCAATTT', 'GAGTTGCG')
  if(spid == "SI_P01_B10") spseqs <- c('ACCATTAA', 'CTGGACGT', 'GAACGGTC', 'TGTTCACG')
  if(spid == "SI_P01_C10") spseqs <- c('CGTGCTAA', 'TCACTCCT', 'ATCTGATC', 'GAGAAGGG')
  if(spid == "SI_P01_D10") spseqs <- c('CTTATTTG', 'GCGGGCAT', 'AGATAACA', 'TACCCGGC')
  if(spid == "SI_P01_E10") spseqs <- c('GCACCAGT', 'CGCAGGAG', 'TAGTACCA', 'ATTGTTTC')
  if(spid == "SI_P01_F10") spseqs <- c('TCGACAAT', 'GAATACTG', 'ATTCGTGC', 'CGCGTGCA')
  if(spid == "SI_P01_G10") spseqs <- c('CGGAGACT', 'TCCTATGA', 'ATACTGAG', 'GATGCCTC')
  if(spid == "SI_P01_H10") spseqs <- c('GACCGCCA', 'TCGAATTG', 'ATTTCAGC', 'CGAGTGAT')
  if(spid == "SI_P01_A11") spseqs <- c('CTTTCCTT', 'TAGGTAAA', 'ACCAGTCC', 'GGACAGGG')
  if(spid == "SI_P01_B11") spseqs <- c('TCCAGATA', 'GATTCGCT', 'CGACATAG', 'ATGGTCGC')
  if(spid == "SI_P01_C11") spseqs <- c('GTTTGTGG', 'ACCGAACA', 'TAGACGAC', 'CGACTCTT')
  if(spid == "SI_P01_D11") spseqs <- c('GCTACTTC', 'CACCTCAG', 'ATATGAGA', 'TGGGAGCT')
  if(spid == "SI_P01_E11") spseqs <- c('ATCGCCAT', 'TCACGGTA', 'GGGTTTCC', 'CATAAAGG')
  if(spid == "SI_P01_F11") spseqs <- c('GAACCCGG', 'AGCAGTTA', 'TCGTAGAT', 'CTTGTACC')
  if(spid == "SI_P01_G11") spseqs <- c('AGGGCGTT', 'CTATACGC', 'TACATAAG', 'GCTCGTCA')
  if(spid == "SI_P01_H11") spseqs <- c('TCTCGACT', 'AGGATCGA', 'CACGATTC', 'GTATCGAG')
  if(spid == "SI_P01_A12") spseqs <- c('TTATGGAA', 'ACTACTGT', 'CGGGAACG', 'GACCTCTC')
  if(spid == "SI_P01_B12") spseqs <- c('GAAAGACA', 'CGCTACAT', 'ACGCTTGG', 'TTTGCGTC')
  if(spid == "SI_P01_C12") spseqs <- c('TAAGCCAC', 'CCGTTATG', 'GGTAATGT', 'ATCCGGCA')
  if(spid == "SI_P01_D12") spseqs <- c('GCTGTGTA', 'AGAAACGT', 'CACTCAAC', 'TTGCGTCG')
  if(spid == "SI_P01_E12") spseqs <- c('CGCTATCC', 'ACGCGGAA', 'TAAATCGT', 'GTTGCATG')
  if(spid == "SI_P01_F12") spseqs <- c('AATTGAAC', 'TGGACCCT', 'CCAGTGGA', 'GTCCATTG')
  if(spid == "SI_P01_G12") spseqs <- c('CATGCGTA', 'ACCCGCAC', 'TGATATCG', 'GTGATAGT')
  if(spid == "SI_P01_H12") spseqs <- c('TGTGTATA', 'GTCCAGGC', 'CAATCCCT', 'ACGAGTAG')
  
  if(spid == "SI_3A_A1" || spid == "SI_P03_A1") spseqs <- c('AAACGGCG', 'CCTACCAT', 'GGCGTTTC', 'TTGTAAGA')
  if(spid == "SI_3A_B1" || spid == "SI_P03_B1") spseqs <- c('AGGCTACC', 'CTAGCTGT', 'GCCAACAA', 'TATTGGTG')
  if(spid == "SI_3A_C1" || spid == "SI_P03_C1") spseqs <- c('AGACTTTC', 'CCGAGGCA', 'GATGCAGT', 'TTCTACAG')
  if(spid == "SI_3A_D1" || spid == "SI_P03_D1") spseqs <- c('AGCTGCGT', 'CAACCATC', 'GTGGAGCA', 'TCTATTAG')
  if(spid == "SI_3A_E1" || spid == "SI_P03_E1") spseqs <- c('ACGAAAGC', 'CGCCCGTA', 'GTTTGCCT', 'TAAGTTAG')
  if(spid == "SI_3A_F1" || spid == "SI_P03_F1") spseqs <- c('ATCGCTCC', 'CCGTACAG', 'GATAGGTA', 'TGACTAGT')
  if(spid == "SI_3A_G1" || spid == "SI_P03_G1") spseqs <- c('ATGCGATT', 'CATATGCG', 'GGATACGA', 'TCCGCTAC')
  if(spid == "SI_3A_H1" || spid == "SI_P03_H1") spseqs <- c('AAACTCAT', 'CGGGAGTA', 'GTCACAGG', 'TCTTGTCC')
  if(spid == "SI_3A_A2" || spid == "SI_P03_A2") spseqs <- c('AGCCCTTT', 'CAAGTCCA', 'GTGAGAAG', 'TCTTAGGC')
  if(spid == "SI_3A_B2" || spid == "SI_P03_B2") spseqs <- c('AAGTTGAT', 'CCCACCCA', 'GGTCGAGC', 'TTAGATTG')
  if(spid == "SI_3A_C2" || spid == "SI_P03_C2") spseqs <- c('AATCACTA', 'CCGAGAAC', 'GTAGTGCG', 'TGCTCTGT')
  if(spid == "SI_3A_D2" || spid == "SI_P03_D2") spseqs <- c('ACATTCCG', 'CTGCGGTA', 'GACACAAT', 'TGTGATGC')
  if(spid == "SI_3A_E2" || spid == "SI_P03_E2") spseqs <- c('AGGCTGGT', 'CACAACTA', 'GTTGGTCC', 'TCATCAAG')
  if(spid == "SI_3A_F2" || spid == "SI_P03_F2") spseqs <- c('ATGGTTAG', 'CATTGATA', 'GCAAACGC', 'TGCCCGCT')
  if(spid == "SI_3A_G2" || spid == "SI_P03_G2") spseqs <- c('ATAACCTA', 'CGGTGAGC', 'GATCTTAT', 'TCCGAGCG')
  if(spid == "SI_3A_H2" || spid == "SI_P03_H2") spseqs <- c('AACGGTCA', 'CCGAACTC', 'GGTCCAAG', 'TTATTGGT')
  if(spid == "SI_3A_A3" || spid == "SI_P03_A3") spseqs <- c('AAAGCATA', 'CTGCAGCC', 'GCCTTTAT', 'TGTAGCGG')
  if(spid == "SI_3A_B3" || spid == "SI_P03_B3") spseqs <- c('ATTGGACG', 'CAGCTTAC', 'GGCAAGGA', 'TCATCCTT')
  if(spid == "SI_3A_C3" || spid == "SI_P03_C3") spseqs <- c('ACGTTACA', 'CGTAGGTT', 'GACGACGG', 'TTACCTAC')
  if(spid == "SI_3A_D3" || spid == "SI_P03_D3") spseqs <- c('ACTTCACT', 'CGAAGTTG', 'GAGCACGC', 'TTCGTGAA')
  if(spid == "SI_3A_E3" || spid == "SI_P03_E3") spseqs <- c('AACAAGTC', 'CGGCTCCA', 'GTATGTAT', 'TCTGCAGG')
  if(spid == "SI_3A_F3" || spid == "SI_P03_F3") spseqs <- c('AGTCTGTA', 'CAGAATAG', 'GCCTCCGT', 'TTAGGACC')
  if(spid == "SI_3A_G3" || spid == "SI_P03_G3") spseqs <- c('ATGTCCAG', 'CGACGTCA', 'GCTATAGC', 'TACGAGTT')
  if(spid == "SI_3A_H3" || spid == "SI_P03_H3") spseqs <- c('ACACCTAA', 'CGTTTGGG', 'GACAAACC', 'TTGGGCTT')
  if(spid == "SI_3A_A4" || spid == "SI_P03_A4") spseqs <- c('AGAACGCC', 'CATGGCAG', 'GTCTTTGA', 'TCGCAATT')
  if(spid == "SI_3A_B4" || spid == "SI_P03_B4") spseqs <- c('AGGGACTG', 'CCTCTAAC', 'GACAGGCT', 'TTATCTGA')
  if(spid == "SI_3A_C4" || spid == "SI_P03_C4") spseqs <- c('ACATTGGC', 'CTTAGTCA', 'GAGCCCAT', 'TGCGAATG')
  if(spid == "SI_3A_D4" || spid == "SI_P03_D4") spseqs <- c('AAATCGTC', 'CTTCGAAT', 'GCGATCGG', 'TGCGATCA')
  if(spid == "SI_3A_E4" || spid == "SI_P03_E4") spseqs <- c('AGCTGACG', 'CCGGTGTC', 'GTAAACAT', 'TATCCTGA')
  if(spid == "SI_3A_F4" || spid == "SI_P03_F4") spseqs <- c('AACGACAC', 'CGTCCTCT', 'GCATGATA', 'TTGATGGG')
  if(spid == "SI_3A_G4" || spid == "SI_P03_G4") spseqs <- c('AGCTTCTC', 'CCTGCGGT', 'GTACAACG', 'TAGAGTAA')
  if(spid == "SI_3A_H4" || spid == "SI_P03_H4") spseqs <- c('ACTGGAGC', 'CGGTCGTG', 'GAAATCAA', 'TTCCATCT')
  if(spid == "SI_3A_A5" || spid == "SI_P03_A5") spseqs <- c('ATTGGGAA', 'CAGTCTGG', 'GGCATACT', 'TCACACTC')
  if(spid == "SI_3A_B5" || spid == "SI_P03_B5") spseqs <- c('ATCGTACT', 'CATCAGTG', 'GGGACTAC', 'TCATGCGA')
  if(spid == "SI_3A_C5" || spid == "SI_P03_C5") spseqs <- c('ATGCATTC', 'CACTGACT', 'GGTACGGG', 'TCAGTCAA')
  if(spid == "SI_3A_D5" || spid == "SI_P03_D5") spseqs <- c('AGACGGAT', 'CCTTTAGA', 'GTCGACTC', 'TAGACTCG')
  if(spid == "SI_3A_E5" || spid == "SI_P03_E5") spseqs <- c('ATCCAAGG', 'CCGTTGAA', 'GGAAGCTC', 'TATGCTCT')
  if(spid == "SI_3A_F5" || spid == "SI_P03_F5") spseqs <- c('AACACAGC', 'CGGTTTAG', 'GTACGGCT', 'TCTGACTA')
  if(spid == "SI_3A_G5" || spid == "SI_P03_G5") spseqs <- c('ATGAAGTA', 'CGCCGAAC', 'GAAGCTCG', 'TCTTTCGT')
  if(spid == "SI_3A_H5" || spid == "SI_P03_H5") spseqs <- c('ATAGTATG', 'CCGCGTCT', 'GGCTCCAC', 'TATAAGGA')
  if(spid == "SI_3A_A6" || spid == "SI_P03_A6") spseqs <- c('ACGGGACT', 'CTTTCGAC', 'GAACATGA', 'TGCATCTG')
  if(spid == "SI_3A_B6" || spid == "SI_P03_B6") spseqs <- c('AACGCGAA', 'CTATTTGG', 'GCGCACCT', 'TGTAGATC')
  if(spid == "SI_3A_C6" || spid == "SI_P03_C6") spseqs <- c('ACTCAGAC', 'CGCTCAGG', 'GAGGTTTA', 'TTAAGCCT')
  if(spid == "SI_3A_D6" || spid == "SI_P03_D6") spseqs <- c('ATGCCAAA', 'CCTTATCG', 'GAAGTCTT', 'TGCAGGGC')
  if(spid == "SI_3A_E6" || spid == "SI_P03_E6") spseqs <- c('ATTGAAAC', 'CAGCCCGA', 'GCCATTTG', 'TGATGGCT')
  if(spid == "SI_3A_F6" || spid == "SI_P03_F6") spseqs <- c('ATGCCGGC', 'CCTAATTA', 'GACTTCCT', 'TGAGGAAG')
  if(spid == "SI_3A_G6" || spid == "SI_P03_G6") spseqs <- c('AGCACTGG', 'CATTACAC', 'GTGCGACA', 'TCAGTGTT')
  if(spid == "SI_3A_H6" || spid == "SI_P03_H6") spseqs <- c('AAGCATAA', 'CCCATCGC', 'GGTTGATG', 'TTAGCGCT')
  if(spid == "SI_3A_A7" || spid == "SI_P03_A7") spseqs <- c('AGGTCATA', 'CTCATCAT', 'GCTGAGGG', 'TAACGTCC')
  if(spid == "SI_3A_B7" || spid == "SI_P03_B7") spseqs <- c('AGGGATGA', 'CTTCTGTT', 'GAATGCAC', 'TCCACACG')
  if(spid == "SI_3A_C7" || spid == "SI_P03_C7") spseqs <- c('ACACCGGG', 'CATAATCC', 'GGCGGAAT', 'TTGTTCTA')
  if(spid == "SI_3A_D7" || spid == "SI_P03_D7") spseqs <- c('AACTTAGA', 'CCGGATCC', 'GGTCGCAT', 'TTAACGTG')
  if(spid == "SI_3A_E7" || spid == "SI_P03_E7") spseqs <- c('AAGACGTG', 'CCATGTGT', 'GTTCACAA', 'TGCGTACC')
  if(spid == "SI_3A_F7" || spid == "SI_P03_F7") spseqs <- c('ACCCGAGA', 'CAAACTTT', 'GGTTAGAC', 'TTGGTCCG')
  if(spid == "SI_3A_G7" || spid == "SI_P03_G7") spseqs <- c('ATTACCGG', 'CAGTAATT', 'GCCGGTAA', 'TGACTGCC')
  if(spid == "SI_3A_H7" || spid == "SI_P03_H7") spseqs <- c('AACGGGTG', 'CTAATTCT', 'GCTTCAAC', 'TGGCACGA')
  if(spid == "SI_3A_A8" || spid == "SI_P03_A8") spseqs <- c('ATGATACG', 'CCACAGAA', 'GACTGTTC', 'TGTGCCGT')
  if(spid == "SI_3A_B8" || spid == "SI_P03_B8") spseqs <- c('ACGTTCAC', 'CAAGGTCT', 'GTTAAGTG', 'TGCCCAGA')
  if(spid == "SI_3A_C8" || spid == "SI_P03_C8") spseqs <- c('AGCTCGAG', 'CAGGAAGA', 'GCACGTTT', 'TTTATCCC')
  if(spid == "SI_3A_D8" || spid == "SI_P03_D8") spseqs <- c('AATCTTTG', 'CTCAAGAC', 'GGATGAGT', 'TCGGCCCA')
  if(spid == "SI_3A_E8" || spid == "SI_P03_E8") spseqs <- c('AGCCTATG', 'CTAACGCA', 'GCTTACAT', 'TAGGGTGC')
  if(spid == "SI_3A_F8" || spid == "SI_P03_F8") spseqs <- c('AGTTGGGA', 'CCAGAAAG', 'GTGCCCTC', 'TACATTCT')
  if(spid == "SI_3A_G8" || spid == "SI_P03_G8") spseqs <- c('AAGTACTC', 'CTTGGAGA', 'GGAACTCT', 'TCCCTGAG')
  if(spid == "SI_3A_H8" || spid == "SI_P03_H8") spseqs <- c('AAGAGCGG', 'CTTGTTAT', 'GGCCCATC', 'TCATAGCA')
  if(spid == "SI_3A_A9" || spid == "SI_P03_A9") spseqs <- c('ACAACTTG', 'CTCCAACA', 'GAGTGCGT', 'TGTGTGAC')
  if(spid == "SI_3A_B9" || spid == "SI_P03_B9") spseqs <- c('AAGCGTGT', 'CTTGACCG', 'GCCACGTA', 'TGATTAAC')
  if(spid == "SI_3A_C9" || spid == "SI_P03_C9") spseqs <- c('AGATCGGT', 'CATCGTCG', 'GTCATATA', 'TCGGACAC')
  if(spid == "SI_3A_D9" || spid == "SI_P03_D9") spseqs <- c('ACCTACTG', 'CAAGGGAC', 'GGGACACA', 'TTTCTTGT')
  if(spid == "SI_3A_E9" || spid == "SI_P03_E9") spseqs <- c('AGTAAGCA', 'CCGGTAAT', 'GTATCTTC', 'TACCGCGG')
  if(spid == "SI_3A_F9" || spid == "SI_P03_F9") spseqs <- c('AGTTAGTT', 'CACGCACG', 'GTACTTAA', 'TCGAGCGC')
  if(spid == "SI_3A_G9" || spid == "SI_P03_G9") spseqs <- c('AGTCTCAG', 'CAATGGCA', 'GCCGAAGT', 'TTGACTTC')
  if(spid == "SI_3A_H9" || spid == "SI_P03_H9") spseqs <- c('ACCTGCCA', 'CTTCATAC', 'GGAATATG', 'TAGGCGGT')
  if(spid == "SI_3A_A10" || spid == "SI_P03_A10") spseqs <- c('ACAGCAAC', 'CGCAATTT', 'GAGTTGCG', 'TTTCGCGA')
  if(spid == "SI_3A_B10" || spid == "SI_P03_B10") spseqs <- c('ACCATTAA', 'CTGGACGT', 'GAACGGTC', 'TGTTCACG')
  if(spid == "SI_3A_C10" || spid == "SI_P03_C10") spseqs <- c('ATCTGATC', 'CGTGCTAA', 'GAGAAGGG', 'TCACTCCT')
  if(spid == "SI_3A_D10" || spid == "SI_P03_D10") spseqs <- c('AGATAACA', 'CTTATTTG', 'GCGGGCAT', 'TACCCGGC')
  if(spid == "SI_3A_E10" || spid == "SI_P03_E10") spseqs <- c('ATTGTTTC', 'CGCAGGAG', 'GCACCAGT', 'TAGTACCA')
  if(spid == "SI_3A_F10" || spid == "SI_P03_F10") spseqs <- c('ATTCGTGC', 'CGCGTGCA', 'GAATACTG', 'TCGACAAT')
  if(spid == "SI_3A_G10" || spid == "SI_P03_G10") spseqs <- c('ATACTGAG', 'CGGAGACT', 'GATGCCTC', 'TCCTATGA')
  if(spid == "SI_3A_H10" || spid == "SI_P03_H10") spseqs <- c('ATTTCAGC', 'CGAGTGAT', 'GACCGCCA', 'TCGAATTG')
  if(spid == "SI_3A_A11" || spid == "SI_P03_A11") spseqs <- c('ACCAGTCC', 'CTTTCCTT', 'GGACAGGG', 'TAGGTAAA')
  if(spid == "SI_3A_B11" || spid == "SI_P03_B11") spseqs <- c('ATGGTCGC', 'CGACATAG', 'GATTCGCT', 'TCCAGATA')
  if(spid == "SI_3A_C11" || spid == "SI_P03_C11") spseqs <- c('ACCGAACA', 'CGACTCTT', 'GTTTGTGG', 'TAGACGAC')
  if(spid == "SI_3A_D11" || spid == "SI_P03_D11") spseqs <- c('ATATGAGA', 'CACCTCAG', 'GCTACTTC', 'TGGGAGCT')
  if(spid == "SI_3A_E11" || spid == "SI_P03_E11") spseqs <- c('ATCGCCAT', 'CATAAAGG', 'GGGTTTCC', 'TCACGGTA')
  if(spid == "SI_3A_F11" || spid == "SI_P03_F11") spseqs <- c('AGCAGTTA', 'CTTGTACC', 'GAACCCGG', 'TCGTAGAT')
  if(spid == "SI_3A_G11" || spid == "SI_P03_G11") spseqs <- c('AGGGCGTT', 'CTATACGC', 'GCTCGTCA', 'TACATAAG')
  if(spid == "SI_3A_H11" || spid == "SI_P03_H11") spseqs <- c('AGGATCGA', 'CACGATTC', 'GTATCGAG', 'TCTCGACT')
  if(spid == "SI_3A_A12" || spid == "SI_P03_A12") spseqs <- c('ACTACTGT', 'CGGGAACG', 'GACCTCTC', 'TTATGGAA')
  if(spid == "SI_3A_B12" || spid == "SI_P03_B12") spseqs <- c('ACGCTTGG', 'CGCTACAT', 'GAAAGACA', 'TTTGCGTC')
  if(spid == "SI_3A_C12" || spid == "SI_P03_C12") spseqs <- c('ATCCGGCA', 'CCGTTATG', 'GGTAATGT', 'TAAGCCAC')
  if(spid == "SI_3A_D12" || spid == "SI_P03_D12") spseqs <- c('AGAAACGT', 'CACTCAAC', 'GCTGTGTA', 'TTGCGTCG')
  if(spid == "SI_3A_E12" || spid == "SI_P03_E12") spseqs <- c('ACGCGGAA', 'CGCTATCC', 'GTTGCATG', 'TAAATCGT')
  if(spid == "SI_3A_F12" || spid == "SI_P03_F12") spseqs <- c('AATTGAAC', 'CCAGTGGA', 'GTCCATTG', 'TGGACCCT')
  if(spid == "SI_3A_G12" || spid == "SI_P03_G12") spseqs <- c('ACCCGCAC', 'CATGCGTA', 'GTGATAGT', 'TGATATCG')
  if(spid == "SI_3A_H12" || spid == "SI_P03_H12") spseqs <- c('ACGAGTAG', 'CAATCCCT', 'GTCCAGGC', 'TGTGTATA')
  
  
  if(spid == "SI_T2_1") spseqs <- c('GGGTGATC', 'TTACCGAT', 'AATGACGA', 'CCCATTCG')
  if(spid == "SI_T2_2") spseqs <- c('GGGTCGAA', 'ATCCGCCC', 'TCTATAGT', 'CAAGATTG')
  if(spid == "SI_T2_3") spseqs <- c('GCTGATAT', 'TGCCGAGC', 'AAATTGCG', 'CTGACCTA')
  if(spid == "SI_T2_4") spseqs <- c('ACTTCTGA', 'TTCATCTT', 'CGACGACG', 'GAGGAGAC')
  if(spid == "SI_T2_5") spseqs <- c('GAATACAA', 'AGCATACC', 'TCGGGTTT', 'CTTCCGGG')
  if(spid == "SI_T2_6") spseqs <- c('TATTGAGA', 'GTAGTCAG', 'CGCCATTC', 'ACGACGCT')
  if(spid == "SI_T2_7") spseqs <- c('AAATCTGT', 'GTCCAACC', 'TCTGGCTG', 'CGGATGAA')
  if(spid == "SI_T2_8") spseqs <- c('CCTTGAAC', 'GAAATCGG', 'TGGCCTCT', 'ATCGAGTA')
  
  
  if(spid == "SI_GA_A1" || spid == "SI_P2_A1") spseqs <- c('GGTTTACT', 'CTAAACGG', 'TCGGCGTC', 'AACCGTAA')
  if(spid == "SI_GA_A2" || spid == "SI_P2_A2") spseqs <- c('TTTCATGA', 'ACGTCCCT', 'CGCATGTG', 'GAAGGAAC')
  if(spid == "SI_GA_A3" || spid == "SI_P2_A3") spseqs <- c('CAGTACTG', 'AGTAGTCT', 'GCAGTAGA', 'TTCCCGAC')
  if(spid == "SI_GA_A4" || spid == "SI_P2_A4") spseqs <- c('TATGATTC', 'CCCACAGT', 'ATGCTGAA', 'GGATGCCG')
  if(spid == "SI_GA_A5" || spid == "SI_P2_A5") spseqs <- c('CTAGGTGA', 'TCGTTCAG', 'AGCCAATT', 'GATACGCC')
  if(spid == "SI_GA_A6" || spid == "SI_P2_A6") spseqs <- c('CGCTATGT', 'GCTGTCCA', 'TTGAGATC', 'AAACCGAG')
  if(spid == "SI_GA_A7" || spid == "SI_P2_A7") spseqs <- c('ACAGAGGT', 'TATAGTTG', 'CGGTCCCA', 'GTCCTAAC')
  if(spid == "SI_GA_A8" || spid == "SI_P2_A8") spseqs <- c('GCATCTCC', 'TGTAAGGT', 'CTGCGATG', 'AACGTCAA')
  if(spid == "SI_GA_A9" || spid == "SI_P2_A9") spseqs <- c('TCTTAAAG', 'CGAGGCTC', 'GTCCTTCT', 'AAGACGGA')
  if(spid == "SI_GA_A10" || spid == "SI_P2_A10") spseqs <- c('GAAACCCT', 'TTTCTGTC', 'CCGTGTGA', 'AGCGAAAG')
  if(spid == "SI_GA_A11" || spid == "SI_P2_A11") spseqs <- c('GTCCGGTC', 'AAGATCAT', 'CCTGAAGG', 'TGATCTCA')
  if(spid == "SI_GA_A12" || spid == "SI_P2_A12") spseqs <- c('AGTGGAAC', 'GTCTCCTT', 'TCACATCA', 'CAGATGGG')
  if(spid == "SI_GA_B1" || spid == "SI_P2_B1") spseqs <- c('GTAATCTT', 'TCCGGAAG', 'AGTTCGGC', 'CAGCATCA')
  if(spid == "SI_GA_B2" || spid == "SI_P2_B2") spseqs <- c('TACTCTTC', 'CCTGTGCG', 'GGACACGT', 'ATGAGAAA')
  if(spid == "SI_GA_B3" || spid == "SI_P2_B3") spseqs <- c('GTGTATTA', 'TGTGCGGG', 'ACCATAAC', 'CAACGCCT')
  if(spid == "SI_GA_B4" || spid == "SI_P2_B4") spseqs <- c('ACTTCATA', 'GAGATGAC', 'TGCCGTGG', 'CTAGACCT')
  if(spid == "SI_GA_B5" || spid == "SI_P2_B5") spseqs <- c('AATAATGG', 'CCAGGGCA', 'TGCCTCAT', 'GTGTCATC')
  if(spid == "SI_GA_B6" || spid == "SI_P2_B6") spseqs <- c('CGTTAATC', 'GCCACGCT', 'TTACTCAG', 'AAGGGTGA')
  if(spid == "SI_GA_B7" || spid == "SI_P2_B7") spseqs <- c('AAACCTCA', 'GCCTTGGT', 'CTGGACTC', 'TGTAGAAG')
  if(spid == "SI_GA_B8" || spid == "SI_P2_B8") spseqs <- c('AAAGTGCT', 'GCTACCTG', 'TGCTGTAA', 'CTGCAAGC')
  if(spid == "SI_GA_B9" || spid == "SI_P2_B9") spseqs <- c('CTGTAACT', 'TCTAGCGA', 'AGAGTGTG', 'GACCCTAC')
  if(spid == "SI_GA_B10" || spid == "SI_P2_B10") spseqs <- c('ACCGTATG', 'GATTAGAT', 'CTGACTGA', 'TGACGCCC')
  if(spid == "SI_GA_B11" || spid == "SI_P2_B11") spseqs <- c('GTTCCTCA', 'AGGTACGC', 'TAAGTATG', 'CCCAGGAT')
  if(spid == "SI_GA_B12" || spid == "SI_P2_B12") spseqs <- c('TACCACCA', 'CTAAGTTT', 'GGGTCAAG', 'ACTGTGGC')
  if(spid == "SI_GA_C1" || spid == "SI_P2_C1") spseqs <- c('CCACTTAT', 'AACTGGCG', 'TTGGCATA', 'GGTAACGC')
  if(spid == "SI_GA_C2" || spid == "SI_P2_C2") spseqs <- c('CCTAGACC', 'ATCTCTGT', 'TAGCTCTA', 'GGAGAGAG')
  if(spid == "SI_GA_C3" || spid == "SI_P2_C3") spseqs <- c('TCAGCCGT', 'CAGAGGCC', 'GGTCAATA', 'ATCTTTAG')
  if(spid == "SI_GA_C4" || spid == "SI_P2_C4") spseqs <- c('ACAATTCA', 'TGCGCAGC', 'CATCACTT', 'GTGTGGAG')
  if(spid == "SI_GA_C5" || spid == "SI_P2_C5") spseqs <- c('CGACTTGA', 'TACAGACT', 'ATTGCGTG', 'GCGTACAC')
  if(spid == "SI_GA_C6" || spid == "SI_P2_C6") spseqs <- c('ATTACTTC', 'TGCGAACT', 'GCATTCGG', 'CAGCGGAA')
  if(spid == "SI_GA_C7" || spid == "SI_P2_C7") spseqs <- c('GTCTCTCG', 'AATCTCTC', 'CGGAGGGA', 'TCAGAAAT')
  if(spid == "SI_GA_C8" || spid == "SI_P2_C8") spseqs <- c('GTTGAGAA', 'AGATCTGG', 'TCGATACT', 'CACCGCTC')
  if(spid == "SI_GA_C9" || spid == "SI_P2_C9") spseqs <- c('GCGCAGAA', 'ATCTTACC', 'TATGGTGT', 'CGAACCTG')
  if(spid == "SI_GA_C10" || spid == "SI_P2_C10") spseqs <- c('TCTCAGTG', 'GAGACTAT', 'CGCTTAGC', 'ATAGGCCA')
  if(spid == "SI_GA_C11" || spid == "SI_P2_C11") spseqs <- c('GAGGATCT', 'AGACCATA', 'TCCTGCGC', 'CTTATGAG')
  if(spid == "SI_GA_C12" || spid == "SI_P2_C12") spseqs <- c('TCTCGTTT', 'GGCTAGCG', 'ATGACCGC', 'CAAGTAAA')
  if(spid == "SI_GA_D1" || spid == "SI_P2_D1") spseqs <- c('CACTCGGA', 'GCTGAATT', 'TGAAGTAC', 'ATGCTCCG')
  if(spid == "SI_GA_D2" || spid == "SI_P2_D2") spseqs <- c('TAACAAGG', 'GGTTCCTC', 'ATCATGCA', 'CCGGGTAT')
  if(spid == "SI_GA_D3" || spid == "SI_P2_D3") spseqs <- c('ACATTACT', 'TTTGGGTA', 'CAGCCCAC', 'GGCAATGG')
  if(spid == "SI_GA_D4" || spid == "SI_P2_D4") spseqs <- c('CCCTAACA', 'ATTCCGAT', 'TGGATTGC', 'GAAGGCTG')
  if(spid == "SI_GA_D5" || spid == "SI_P2_D5") spseqs <- c('CTCGTCAC', 'GATCAGCA', 'ACAACAGG', 'TGGTGTTT')
  if(spid == "SI_GA_D6" || spid == "SI_P2_D6") spseqs <- c('CATGCGAT', 'TGATATTC', 'GTGATCGA', 'ACCCGACG')
  if(spid == "SI_GA_D7" || spid == "SI_P2_D7") spseqs <- c('ATTTGCTA', 'TAGACACC', 'CCACAGGG', 'GGCGTTAT')
  if(spid == "SI_GA_D8" || spid == "SI_P2_D8") spseqs <- c('GCAACAAA', 'TAGTTGTC', 'CGCCATCG', 'ATTGGCGT')
  if(spid == "SI_GA_D9" || spid == "SI_P2_D9") spseqs <- c('AGGAGATG', 'GATGTGGT', 'CTACATCC', 'TCCTCCAA')
  if(spid == "SI_GA_D10" || spid == "SI_P2_D10") spseqs <- c('CAATACCC', 'TGTCTATG', 'ACCACGAA', 'GTGGGTGT')
  if(spid == "SI_GA_D11" || spid == "SI_P2_D11") spseqs <- c('CTTTGCGG', 'TGCACAAA', 'AAGCAGTC', 'GCAGTTCT')
  if(spid == "SI_GA_D12" || spid == "SI_P2_D12") spseqs <- c('GCACAATG', 'CTTGGTAC', 'TGCACCGT', 'AAGTTGCA')
  if(spid == "SI_GA_E1" || spid == "SI_P2_E1") spseqs <- c('TGGTAAAC', 'GAAAGGGT', 'ACTGCTCG', 'CTCCTCTA')
  if(spid == "SI_GA_E2" || spid == "SI_P2_E2") spseqs <- c('GTGGTACC', 'TACTATAG', 'ACAAGGTA', 'CGTCCCGT')
  if(spid == "SI_GA_E3" || spid == "SI_P2_E3") spseqs <- c('AGGTATTG', 'CTCCTAGT', 'TCAAGGCC', 'GATGCCAA')
  if(spid == "SI_GA_E4" || spid == "SI_P2_E4") spseqs <- c('TTCGCCCT', 'GGATGGGC', 'AATCAATG', 'CCGATTAA')
  if(spid == "SI_GA_E5" || spid == "SI_P2_E5") spseqs <- c('CATTAGCG', 'TTCGCTGA', 'ACAAGAAT', 'GGGCTCTC')
  if(spid == "SI_GA_E6" || spid == "SI_P2_E6") spseqs <- c('CTGCGGCT', 'GACTCAAA', 'AGAAACTC', 'TCTGTTGG')
  if(spid == "SI_GA_E7" || spid == "SI_P2_E7") spseqs <- c('CACGCCTT', 'GTATATAG', 'TCTCGGGC', 'AGGATACA')
  if(spid == "SI_GA_E8" || spid == "SI_P2_E8") spseqs <- c('ATAGTTAC', 'TGCTGAGT', 'CCTACGTA', 'GAGCACCG')
  if(spid == "SI_GA_E9" || spid == "SI_P2_E9") spseqs <- c('TTGTTTCC', 'GGAGGAGG', 'CCTAACAA', 'AACCCGTT')
  if(spid == "SI_GA_E10" || spid == "SI_P2_E10") spseqs <- c('AAATGTGC', 'GGGCAAAT', 'TCTATCCG', 'CTCGCGTA')
  if(spid == "SI_GA_E11" || spid == "SI_P2_E11") spseqs <- c('AAGCGCTG', 'CGTTTGAT', 'GTAGCACA', 'TCCAATGC')
  if(spid == "SI_GA_E12" || spid == "SI_P2_E12") spseqs <- c('ACCGGCTC', 'GAGTTAGT', 'CGTCCTAG', 'TTAAAGCA')
  if(spid == "SI_GA_F1" || spid == "SI_P2_F1") spseqs <- c('GTTGCAGC', 'TGGAATTA', 'CAATGGAG', 'ACCCTCCT')
  if(spid == "SI_GA_F2" || spid == "SI_P2_F2") spseqs <- c('TTTACATG', 'CGCGATAC', 'ACGCGGGT', 'GAATTCCA')
  if(spid == "SI_GA_F3" || spid == "SI_P2_F3") spseqs <- c('TTCAGGTG', 'ACGGACAT', 'GATCTTGA', 'CGATCACC')
  if(spid == "SI_GA_F4" || spid == "SI_P2_F4") spseqs <- c('CCCAATAG', 'GTGTCGCT', 'AGAGTCGC', 'TATCGATA')
  if(spid == "SI_GA_F5" || spid == "SI_P2_F5") spseqs <- c('GACTACGT', 'CTAGCGAG', 'TCTATATC', 'AGGCGTCA')
  if(spid == "SI_GA_F6" || spid == "SI_P2_F6") spseqs <- c('CGGAGCAC', 'GACCTATT', 'ACTTAGGA', 'TTAGCTCG')
  if(spid == "SI_GA_F7" || spid == "SI_P2_F7") spseqs <- c('CGTGCAGA', 'AACAAGAT', 'TCGCTTCG', 'GTATGCTC')
  if(spid == "SI_GA_F8" || spid == "SI_P2_F8") spseqs <- c('CATGAACA', 'TCACTCGC', 'AGCTGGAT', 'GTGACTTG')
  if(spid == "SI_GA_F9" || spid == "SI_P2_F9") spseqs <- c('CAAGCTCC', 'GTTCACTG', 'TCGTGAAA', 'AGCATGGT')
  if(spid == "SI_GA_F10" || spid == "SI_P2_F10") spseqs <- c('GCTTGGCT', 'AAACAAAC', 'CGGGCTTA', 'TTCATCGG')
  if(spid == "SI_GA_F11" || spid == "SI_P2_F11") spseqs <- c('GCGAGAGT', 'TACGTTCA', 'AGTCCCAC', 'CTATAGTG')
  if(spid == "SI_GA_F12" || spid == "SI_P2_F12") spseqs <- c('TGATGCAT', 'GCTACTGA', 'CACCTGCC', 'ATGGAATG')
  if(spid == "SI_GA_G1" || spid == "SI_P2_G1") spseqs <- c('ATGAATCT', 'GATCTCAG', 'CCAGGAGC', 'TGCTCGTA')
  if(spid == "SI_GA_G2" || spid == "SI_P2_G2") spseqs <- c('TGATTCTA', 'ACTAGGAG', 'CAGCCACT', 'GTCGATGC')
  if(spid == "SI_GA_G3" || spid == "SI_P2_G3") spseqs <- c('CCTCATTC', 'AGCATCCG', 'GTGGCAAT', 'TAATGGGA')
  if(spid == "SI_GA_G4" || spid == "SI_P2_G4") spseqs <- c('GCGATGTG', 'AGATACAA', 'TTTCCACT', 'CACGGTGC')
  if(spid == "SI_GA_G5" || spid == "SI_P2_G5") spseqs <- c('GAGCAAGA', 'TCTGTGAT', 'CGCAGTTC', 'ATATCCCG')
  if(spid == "SI_GA_G6" || spid == "SI_P2_G6") spseqs <- c('CTGACGCG', 'GGTCGTAC', 'TCCTTCTT', 'AAAGAAGA')
  if(spid == "SI_GA_G7" || spid == "SI_P2_G7") spseqs <- c('GGTATGCA', 'CTCGAAAT', 'ACACCTTC', 'TAGTGCGG')
  if(spid == "SI_GA_G8" || spid == "SI_P2_G8") spseqs <- c('TATGAGCT', 'CCGATAGC', 'ATACCCAA', 'GGCTGTTG')
  if(spid == "SI_GA_G9" || spid == "SI_P2_G9") spseqs <- c('TAGGACGT', 'ATCCCACA', 'GGAATGTC', 'CCTTGTAG')
  if(spid == "SI_GA_G10" || spid == "SI_P2_G10") spseqs <- c('TCGCCAGC', 'AATGTTAG', 'CGATAGCT', 'GTCAGCTA')
  if(spid == "SI_GA_G11" || spid == "SI_P2_G11") spseqs <- c('TTATCGTT', 'AGCAGAGC', 'CATCTCCA', 'GCGGATAG')
  if(spid == "SI_GA_G12" || spid == "SI_P2_G12") spseqs <- c('ATTCTAAG', 'CCCGATTA', 'TGGAGGCT', 'GAATCCGC')
  if(spid == "SI_GA_H1" || spid == "SI_P2_H1") spseqs <- c('GTATGTCA', 'TGTCAGAC', 'CACGTCGG', 'ACGACATT')
  if(spid == "SI_GA_H2" || spid == "SI_P2_H2") spseqs <- c('TAATGACC', 'ATGCCTTA', 'GCCGAGAT', 'CGTATCGG')
  if(spid == "SI_GA_H3" || spid == "SI_P2_H3") spseqs <- c('CCAAGATG', 'AGGCCCGA', 'TACGTGAC', 'GTTTATCT')
  if(spid == "SI_GA_H4" || spid == "SI_P2_H4") spseqs <- c('GCCATTCC', 'CAAGAATT', 'TTGCCGGA', 'AGTTGCAG')
  if(spid == "SI_GA_H5" || spid == "SI_P2_H5") spseqs <- c('CCACTACA', 'GATTCTGG', 'TGCGGCTT', 'ATGAAGAC')
  if(spid == "SI_GA_H6" || spid == "SI_P2_H6") spseqs <- c('TAGGATAA', 'CCTTTGTC', 'GTACGCGG', 'AGCACACT')
  if(spid == "SI_GA_H7" || spid == "SI_P2_H7") spseqs <- c('AGCTATCA', 'CATATAAC', 'TCAGGGTG', 'GTGCCCGT')
  if(spid == "SI_GA_H8" || spid == "SI_P2_H8") spseqs <- c('TTGTTGAT', 'GCTCAACC', 'CAAAGTGG', 'AGCGCCTA')
  if(spid == "SI_GA_H9" || spid == "SI_P2_H9") spseqs <- c('ACACTGTT', 'CAGGATGG', 'GGCTGAAC', 'TTTACCCA')
  if(spid == "SI_GA_H10" || spid == "SI_P2_H10") spseqs <- c('GTAATTGC', 'AGTCGCTT', 'CACGAGAA', 'TCGTCACG')
  if(spid == "SI_GA_H11" || spid == "SI_P2_H11") spseqs <- c('GGCGAGTA', 'ACTTCTAT', 'CAAATACG', 'TTGCGCGC')
  if(spid == "SI_GA_H12" || spid == "SI_P2_H12") spseqs <- c('GACAGCAT', 'TTTGTACA', 'AGGCCGTG', 'CCATATGC')

  dualseqs <- NA

  if(spid == "SI-TT-A1" || spid == "SI_TT_A1") dualseqs <- c('GTAACATGCG','AGTGTTACCT','AGGTAACACT')
  if(spid == "SI-TT-A2" || spid == "SI_TT_A2") dualseqs <- c('GTGGATCAAA','GCCAACCCTG','CAGGGTTGGC')
  if(spid == "SI-TT-A3" || spid == "SI_TT_A3") dualseqs <- c('CACTACGAAA','TTAGACTGAT','ATCAGTCTAA')
  if(spid == "SI-TT-A4" || spid == "SI_TT_A4") dualseqs <- c('CTCTAGCGAG','TATCTTCATC','GATGAAGATA')
  if(spid == "SI-TT-A5" || spid == "SI_TT_A5") dualseqs <- c('GTAGCCCTGT','GAGCATCTAT','ATAGATGCTC')
  if(spid == "SI-TT-A6" || spid == "SI_TT_A6") dualseqs <- c('TAACGCGTGA','CCCTAACTTC','GAAGTTAGGG')
  if(spid == "SI-TT-A7" || spid == "SI_TT_A7") dualseqs <- c('TCCCAAGGGT','TACTACCTTT','AAAGGTAGTA')
  if(spid == "SI-TT-A8" || spid == "SI_TT_A8") dualseqs <- c('CGAAGTATAC','GAACTTGGAG','CTCCAAGTTC')
  if(spid == "SI-TT-A9" || spid == "SI_TT_A9") dualseqs <- c('AAGTGGAGAG','TTCCTGTTAC','GTAACAGGAA')
  if(spid == "SI-TT-A10" || spid == "SI_TT_A10") dualseqs <- c('CGTGACATGC','ATGGTCTAAA','TTTAGACCAT')
  if(spid == "SI-TT-A11" || spid == "SI_TT_A11") dualseqs <- c('CGGAACCCAA','GATTCGAGGA','TCCTCGAATC')
  if(spid == "SI-TT-A12" || spid == "SI_TT_A12") dualseqs <- c('CACCGCACCA','GACTGTCAAT','ATTGACAGTC')
  if(spid == "SI-TT-B1" || spid == "SI_TT_B1") dualseqs <- c('ACAGTAACTA','ACAGTTCGTT','AACGAACTGT')
  if(spid == "SI-TT-B2" || spid == "SI_TT_B2") dualseqs <- c('TCTACCATTT','CGGGAGAGTC','GACTCTCCCG')
  if(spid == "SI-TT-B3" || spid == "SI_TT_B3") dualseqs <- c('CACGGTGAAT','GTTCGTCACA','TGTGACGAAC')
  if(spid == "SI-TT-B4" || spid == "SI_TT_B4") dualseqs <- c('GTAGACGAAA','CTAGTGTGGT','ACCACACTAG')
  if(spid == "SI-TT-B5" || spid == "SI_TT_B5") dualseqs <- c('TCGGCTCTAC','CCGATGGTCT','AGACCATCGG')
  if(spid == "SI-TT-B6" || spid == "SI_TT_B6") dualseqs <- c('AATGCCATGA','TACGTAATGC','GCATTACGTA')
  if(spid == "SI-TT-B7" || spid == "SI_TT_B7") dualseqs <- c('GCCTTCGGTA','CCAACGATTT','AAATCGTTGG')
  if(spid == "SI-TT-B8" || spid == "SI_TT_B8") dualseqs <- c('GCACTGAGAA','TATGCGTGAA','TTCACGCATA')
  if(spid == "SI-TT-B9" || spid == "SI_TT_B9") dualseqs <- c('TATTGAGGCA','CAGGTAAGTG','CACTTACCTG')
  if(spid == "SI-TT-B10" || spid == "SI_TT_B10") dualseqs <- c('GCCCGATGGA','AATCGTCTAG','CTAGACGATT')
  if(spid == "SI-TT-B11" || spid == "SI_TT_B11") dualseqs <- c('TCTTACTTGC','TGACCTCTAG','CTAGAGGTCA')
  if(spid == "SI-TT-B12" || spid == "SI_TT_B12") dualseqs <- c('CGTCAAGGGC','TAGGTCACTC','GAGTGACCTA')
  if(spid == "SI-TT-C1" || spid == "SI_TT_C1") dualseqs <- c('TGCGCGGTTT','CAAGGATAAA','TTTATCCTTG')
  if(spid == "SI-TT-C2" || spid == "SI_TT_C2") dualseqs <- c('CAATCCCGAC','CCGAGTAGTA','TACTACTCGG')
  if(spid == "SI-TT-C3" || spid == "SI_TT_C3") dualseqs <- c('ATGGCTTGTG','GAATGTTGTG','CACAACATTC')
  if(spid == "SI-TT-C4" || spid == "SI_TT_C4") dualseqs <- c('TTCTCGATGA','TGTCGGGCAC','GTGCCCGACA')
  if(spid == "SI-TT-C5" || spid == "SI_TT_C5") dualseqs <- c('TCCGTTGGAT','ACGTTCTCGC','GCGAGAACGT')
  if(spid == "SI-TT-C6" || spid == "SI_TT_C6") dualseqs <- c('ACGACTACCA','ACGACCCTAA','TTAGGGTCGT')
  if(spid == "SI-TT-C7" || spid == "SI_TT_C7") dualseqs <- c('CGCGCACTTA','CCTGTATTCT','AGAATACAGG')
  if(spid == "SI-TT-C8" || spid == "SI_TT_C8") dualseqs <- c('GCTACAAAGC','CACGTGCCCT','AGGGCACGTG')
  if(spid == "SI-TT-C9" || spid == "SI_TT_C9") dualseqs <- c('TATCAGCCTA','GTTTCGTCCT','AGGACGAAAC')
  if(spid == "SI-TT-C10" || spid == "SI_TT_C10") dualseqs <- c('AGAATGGTTT','GAGGGTGGGA','TCCCACCCTC')
  if(spid == "SI-TT-C11" || spid == "SI_TT_C11") dualseqs <- c('ATGGGTGAAA','CTTGGGAATT','AATTCCCAAG')
  if(spid == "SI-TT-C12" || spid == "SI_TT_C12") dualseqs <- c('TCGTCAAGAT','GCAACTCAGG','CCTGAGTTGC')
  if(spid == "SI-TT-D1" || spid == "SI_TT_D1") dualseqs <- c('TGCAATGTTC','GCTTGTCGAA','TTCGACAAGC')
  if(spid == "SI-TT-D2" || spid == "SI_TT_D2") dualseqs <- c('TTAATACGCG','CACCTCGGGT','ACCCGAGGTG')
  if(spid == "SI-TT-D3" || spid == "SI_TT_D3") dualseqs <- c('CCTTCTAGAG','AATACAACGA','TCGTTGTATT')
  if(spid == "SI-TT-D4" || spid == "SI_TT_D4") dualseqs <- c('GCAGTATAGG','TTCCGTGCAC','GTGCACGGAA')
  if(spid == "SI-TT-D5" || spid == "SI_TT_D5") dualseqs <- c('TGGTTCGGGT','GTGGCAGGAG','CTCCTGCCAC')
  if(spid == "SI-TT-D6" || spid == "SI_TT_D6") dualseqs <- c('CCCAGCTTCT','GACACCAAAC','GTTTGGTGTC')
  if(spid == "SI-TT-D7" || spid == "SI_TT_D7") dualseqs <- c('CCTGTCAGGG','AGCCCGTAAC','GTTACGGGCT')
  if(spid == "SI-TT-D8" || spid == "SI_TT_D8") dualseqs <- c('CGCTGAAATC','AGGTGTCTGC','GCAGACACCT')
  if(spid == "SI-TT-D9" || spid == "SI_TT_D9") dualseqs <- c('TGGTCCCAAG','CCTCTGGCGT','ACGCCAGAGG')
  if(spid == "SI-TT-D10" || spid == "SI_TT_D10") dualseqs <- c('ATGCGAATGG','ACAAGTGTCG','CGACACTTGT')
  if(spid == "SI-TT-D11" || spid == "SI_TT_D11") dualseqs <- c('CGAATATTCG','CTGGAAGCAA','TTGCTTCCAG')
  if(spid == "SI-TT-D12" || spid == "SI_TT_D12") dualseqs <- c('GAATTGGTTA','ACTCTAGTAG','CTACTAGAGT')
  if(spid == "SI-TT-E1" || spid == "SI_TT_E1") dualseqs <- c('TTATTCGAGG','CTGTCCTGCT','AGCAGGACAG')
  if(spid == "SI-TT-E2" || spid == "SI_TT_E2") dualseqs <- c('ATGGAGGGAG','ATAACCCATT','AATGGGTTAT')
  if(spid == "SI-TT-E3" || spid == "SI_TT_E3") dualseqs <- c('ACCAGACAAC','AGGAACTAGG','CCTAGTTCCT')
  if(spid == "SI-TT-E4" || spid == "SI_TT_E4") dualseqs <- c('AACCACGCAT','ATTCAGGTTA','TAACCTGAAT')
  if(spid == "SI-TT-E5" || spid == "SI_TT_E5") dualseqs <- c('CGCGGTAGGT','CAGGATGTTG','CAACATCCTG')
  if(spid == "SI-TT-E6" || spid == "SI_TT_E6") dualseqs <- c('TTGAGAGTCA','AACCTGGTAG','CTACCAGGTT')
  if(spid == "SI-TT-E7" || spid == "SI_TT_E7") dualseqs <- c('GTCCTTCGGC','TCATGCACAG','CTGTGCATGA')
  if(spid == "SI-TT-E8" || spid == "SI_TT_E8") dualseqs <- c('GAGCAAGGGC','ATTGACTTGG','CCAAGTCAAT')
  if(spid == "SI-TT-E9" || spid == "SI_TT_E9") dualseqs <- c('TGTCCCAACG','TCGATGTCCA','TGGACATCGA')
  if(spid == "SI-TT-E10" || spid == "SI_TT_E10") dualseqs <- c('CACAATCCCA','ATATCCACAA','TTGTGGATAT')
  if(spid == "SI-TT-E11" || spid == "SI_TT_E11") dualseqs <- c('TCCGGGACAA','GTGAATGCCA','TGGCATTCAC')
  if(spid == "SI-TT-E12" || spid == "SI_TT_E12") dualseqs <- c('CGTCCACCTG','CATTCATGAC','GTCATGAATG')
  if(spid == "SI-TT-F1" || spid == "SI_TT_F1") dualseqs <- c('AAGATTGGAT','AGCGGGATTT','AAATCCCGCT')
  if(spid == "SI-TT-F2" || spid == "SI_TT_F2") dualseqs <- c('AAGGGCCGCA','CTGATTCCTC','GAGGAATCAG')
  if(spid == "SI-TT-F3" || spid == "SI_TT_F3") dualseqs <- c('GAGAGGATAT','TTGAAATGGG','CCCATTTCAA')
  if(spid == "SI-TT-F4" || spid == "SI_TT_F4") dualseqs <- c('CCCACCACAA','ACCTCCGCTT','AAGCGGAGGT')
  if(spid == "SI-TT-F5" || spid == "SI_TT_F5") dualseqs <- c('CGGCTGGATG','TGATAAGCAC','GTGCTTATCA')
  if(spid == "SI-TT-F6" || spid == "SI_TT_F6") dualseqs <- c('TTGCCCGTGC','GCGTGAGATT','AATCTCACGC')
  if(spid == "SI-TT-F7" || spid == "SI_TT_F7") dualseqs <- c('AATGTATCCA','AATGAGCTTA','TAAGCTCATT')
  if(spid == "SI-TT-F8" || spid == "SI_TT_F8") dualseqs <- c('CTCCTTTAGA','GACATAGCTC','GAGCTATGTC')
  if(spid == "SI-TT-F9" || spid == "SI_TT_F9") dualseqs <- c('GTCCCATCAA','CGAACGTGAC','GTCACGTTCG')
  if(spid == "SI-TT-F10" || spid == "SI_TT_F10") dualseqs <- c('CCGGCAACTG','CGGTTTAACA','TGTTAAACCG')
  if(spid == "SI-TT-F11" || spid == "SI_TT_F11") dualseqs <- c('TTCACACCTT','TAGTGTACAC','GTGTACACTA')
  if(spid == "SI-TT-F12" || spid == "SI_TT_F12") dualseqs <- c('GAGACGCACG','CTATGAACAT','ATGTTCATAG')
  if(spid == "SI-TT-G1" || spid == "SI_TT_G1") dualseqs <- c('TGTAGTCATT','CTTGATCGTA','TACGATCAAG')
  if(spid == "SI-TT-G2" || spid == "SI_TT_G2") dualseqs <- c('CATGTGGGTT','GATTCCTTTA','TAAAGGAATC')
  if(spid == "SI-TT-G3" || spid == "SI_TT_G3") dualseqs <- c('ATGACGTCGC','AGGTCAGGAT','ATCCTGACCT')
  if(spid == "SI-TT-G4" || spid == "SI_TT_G4") dualseqs <- c('GCGCTTATGG','GCCTGGCTAG','CTAGCCAGGC')
  if(spid == "SI-TT-G5" || spid == "SI_TT_G5") dualseqs <- c('ATAGGGCGAG','TGCATCGAGT','ACTCGATGCA')
  if(spid == "SI-TT-G6" || spid == "SI_TT_G6") dualseqs <- c('GCGGGTAAGT','TAGCACTAAG','CTTAGTGCTA')
  if(spid == "SI-TT-G7" || spid == "SI_TT_G7") dualseqs <- c('GTTTCACGAT','TTCGGCCAAA','TTTGGCCGAA')
  if(spid == "SI-TT-G8" || spid == "SI_TT_G8") dualseqs <- c('TAAGCAACTG','CTATACTCAA','TTGAGTATAG')
  if(spid == "SI-TT-G9" || spid == "SI_TT_G9") dualseqs <- c('CCGGAGGAAG','TGCGGATGTT','AACATCCGCA')
  if(spid == "SI-TT-G10" || spid == "SI_TT_G10") dualseqs <- c('ACTTTACGTG','TGAACGCCCT','AGGGCGTTCA')
  if(spid == "SI-TT-G11" || spid == "SI_TT_G11") dualseqs <- c('GATAACCTGC','CATTAGAAAC','GTTTCTAATG')
  if(spid == "SI-TT-G12" || spid == "SI_TT_G12") dualseqs <- c('CTTGCATAAA','ATCAGGGCTT','AAGCCCTGAT')
  if(spid == "SI-TT-H1" || spid == "SI_TT_H1") dualseqs <- c('ACAATGTGAA','CGTACCGTTA','TAACGGTACG')
  if(spid == "SI-TT-H2" || spid == "SI_TT_H2") dualseqs <- c('TAGCATAGTG','CGGCTCTGTC','GACAGAGCCG')
  if(spid == "SI-TT-H3" || spid == "SI_TT_H3") dualseqs <- c('CCCGTTCTCG','GACGGATTGG','CCAATCCGTC')
  if(spid == "SI-TT-H4" || spid == "SI_TT_H4") dualseqs <- c('AGTTTCCTGG','TGCCACACAG','CTGTGTGGCA')
  if(spid == "SI-TT-H5" || spid == "SI_TT_H5") dualseqs <- c('AGCAAGAAGC','TTGTGTTTCT','AGAAACACAA')
  if(spid == "SI-TT-H6" || spid == "SI_TT_H6") dualseqs <- c('CCTATCCTCG','GAATACTAAC','GTTAGTATTC')
  if(spid == "SI-TT-H7" || spid == "SI_TT_H7") dualseqs <- c('ACCTCGAGCT','TGTGTTCGAT','ATCGAACACA')
  if(spid == "SI-TT-H8" || spid == "SI_TT_H8") dualseqs <- c('ATAAGGATAC','ATAGATAGGG','CCCTATCTAT')
  if(spid == "SI-TT-H9" || spid == "SI_TT_H9") dualseqs <- c('AGAACTTAGA','CGAGTCCTTT','AAAGGACTCG')
  if(spid == "SI-TT-H10" || spid == "SI_TT_H10") dualseqs <- c('TTATCTAGGG','AAAGGCTCTA','TAGAGCCTTT')
  if(spid == "SI-TT-H11" || spid == "SI_TT_H11") dualseqs <- c('ACAATCGATC','TGACGGAATG','CATTCCGTCA')
  if(spid == "SI-TT-H12" || spid == "SI_TT_H12") dualseqs <- c('TGATGATTCA','GTAGGAGTCG','CGACTCCTAC')

  if(spid == "SI-NN-A1" || spid == "SI_NN_A1") dualseqs <- c('GCCTTGTCAA','GATCCAATCA','TGATTGGATC')
  if(spid == "SI-NN-A2" || spid == "SI_NN_A2") dualseqs <- c('AACTTCTTTG','ATAAGCGGTA','TACCGCTTAT')
  if(spid == "SI-NN-A3" || spid == "SI_NN_A3") dualseqs <- c('TGGTTATAGA','GAGCTCATAA','TTATGAGCTC')
  if(spid == "SI-NN-A4" || spid == "SI_NN_A4") dualseqs <- c('GTAGTGCATC','ATTTGACGGT','ACCGTCAAAT')
  if(spid == "SI-NN-A5" || spid == "SI_NN_A5") dualseqs <- c('GCTTGATTTA','CGGGTGTGAC','GTCACACCCG')
  if(spid == "SI-NN-A6" || spid == "SI_NN_A6") dualseqs <- c('GAAGTTTCGC','TTTCAAAGCA','TGCTTTGAAA')
  if(spid == "SI-NN-A7" || spid == "SI_NN_A7") dualseqs <- c('GCAGTTGTTT','ACACTACTTT','AAAGTAGTGT')
  if(spid == "SI-NN-A8" || spid == "SI_NN_A8") dualseqs <- c('GCCGCAAGTA','AAAGGTCCGC','GCGGACCTTT')
  if(spid == "SI-NN-A9" || spid == "SI_NN_A9") dualseqs <- c('AAGTTGTATG','TGTACAATGC','GCATTGTACA')
  if(spid == "SI-NN-A10" || spid == "SI_NN_A10") dualseqs <- c('AGCAACCTTT','TTCAGCGCTA','TAGCGCTGAA')
  if(spid == "SI-NN-A11" || spid == "SI_NN_A11") dualseqs <- c('GACGAGGGTT','TTACTTTGAC','GTCAAAGTAA')
  if(spid == "SI-NN-A12" || spid == "SI_NN_A12") dualseqs <- c('TGAACAGTCG','ATTTCTCAGA','TCTGAGAAAT')
  if(spid == "SI-NN-B1" || spid == "SI_NN_B1") dualseqs <- c('CGAAAGGAGT','ACACCAAACA','TGTTTGGTGT')
  if(spid == "SI-NN-B2" || spid == "SI_NN_B2") dualseqs <- c('GAATGACTTG','TGAGCTCGCT','AGCGAGCTCA')
  if(spid == "SI-NN-B3" || spid == "SI_NN_B3") dualseqs <- c('GCCAGGGATT','CAAGTTTAGG','CCTAAACTTG')
  if(spid == "SI-NN-B4" || spid == "SI_NN_B4") dualseqs <- c('CTTTGTCCGC','GCTGTCCCGT','ACGGGACAGC')
  if(spid == "SI-NN-B5" || spid == "SI_NN_B5") dualseqs <- c('AGCTTGGGTG','AGCGTATAGT','ACTATACGCT')
  if(spid == "SI-NN-B6" || spid == "SI_NN_B6") dualseqs <- c('TCGCACCGGT','TAGCCTCAGG','CCTGAGGCTA')
  if(spid == "SI-NN-B7" || spid == "SI_NN_B7") dualseqs <- c('TTTAGGTAGG','ACTGAGGGAC','GTCCCTCAGT')
  if(spid == "SI-NN-B8" || spid == "SI_NN_B8") dualseqs <- c('GTTCGAGCGG','CTGTGGGTTT','AAACCCACAG')
  if(spid == "SI-NN-B9" || spid == "SI_NN_B9") dualseqs <- c('AGGGTATGGC','GAGTCCGACT','AGTCGGACTC')
  if(spid == "SI-NN-B10" || spid == "SI_NN_B10") dualseqs <- c('TACCCACTAT','TGGAACATAT','ATATGTTCCA')
  if(spid == "SI-NN-B11" || spid == "SI_NN_B11") dualseqs <- c('TTCAGGCTAC','CTGGGTATTG','CAATACCCAG')
  if(spid == "SI-NN-B12" || spid == "SI_NN_B12") dualseqs <- c('AGGAATAGAT','TGCGGTCACG','CGTGACCGCA')
  if(spid == "SI-NN-C1" || spid == "SI_NN_C1") dualseqs <- c('ACAGCGCTGT','GAGGGTCATC','GATGACCCTC')
  if(spid == "SI-NN-C2" || spid == "SI_NN_C2") dualseqs <- c('GACGTACGGA','ACCTCGCTTT','AAAGCGAGGT')
  if(spid == "SI-NN-C3" || spid == "SI_NN_C3") dualseqs <- c('CTTAGATGCA','CTATTCGACT','AGTCGAATAG')
  if(spid == "SI-NN-C4" || spid == "SI_NN_C4") dualseqs <- c('ACTCCTCAAC','ATTCACGGGA','TCCCGTGAAT')
  if(spid == "SI-NN-C5" || spid == "SI_NN_C5") dualseqs <- c('TCCATACTGT','GAGCGACTTC','GAAGTCGCTC')
  if(spid == "SI-NN-C6" || spid == "SI_NN_C6") dualseqs <- c('GCCAGAATGG','GAATTGTATC','GATACAATTC')
  if(spid == "SI-NN-C7" || spid == "SI_NN_C7") dualseqs <- c('ATTGCAAGAC','CGGTGACCAT','ATGGTCACCG')
  if(spid == "SI-NN-C8" || spid == "SI_NN_C8") dualseqs <- c('CAACATATCG','AACTAAAGGC','GCCTTTAGTT')
  if(spid == "SI-NN-C9" || spid == "SI_NN_C9") dualseqs <- c('CAGGGACCTC','AATAGCAGCA','TGCTGCTATT')
  if(spid == "SI-NN-C10" || spid == "SI_NN_C10") dualseqs <- c('ATTTGGGAAT','TCAGCCGAGT','ACTCGGCTGA')
  if(spid == "SI-NN-C11" || spid == "SI_NN_C11") dualseqs <- c('TGGATAATTG','TTTGTGATCA','TGATCACAAA')
  if(spid == "SI-NN-C12" || spid == "SI_NN_C12") dualseqs <- c('CAGATTATGA','GTGCCTGTAC','GTACAGGCAC')
  if(spid == "SI-NN-D1" || spid == "SI_NN_D1") dualseqs <- c('AATGGTAAGC','CTGTGAAGTT','AACTTCACAG')
  if(spid == "SI-NN-D2" || spid == "SI_NN_D2") dualseqs <- c('CATCCCTCGG','AGGGTAGGCG','CGCCTACCCT')
  if(spid == "SI-NN-D3" || spid == "SI_NN_D3") dualseqs <- c('CTGGTCCGTC','ACGAATAAGG','CCTTATTCGT')
  if(spid == "SI-NN-D4" || spid == "SI_NN_D4") dualseqs <- c('GTCGTAAATT','GTAGTAGGCT','AGCCTACTAC')
  if(spid == "SI-NN-D5" || spid == "SI_NN_D5") dualseqs <- c('CAACACTGAT','CAAACCAGGG','CCCTGGTTTG')
  if(spid == "SI-NN-D6" || spid == "SI_NN_D6") dualseqs <- c('CAGACTGAAT','TGCCTAACCG','CGGTTAGGCA')
  if(spid == "SI-NN-D7" || spid == "SI_NN_D7") dualseqs <- c('CGGACACGCT','TCACGATATA','TATATCGTGA')
  if(spid == "SI-NN-D8" || spid == "SI_NN_D8") dualseqs <- c('CTCGGCTCTC','TATACTTCCA','TGGAAGTATA')
  if(spid == "SI-NN-D9" || spid == "SI_NN_D9") dualseqs <- c('AAGCCTATCA','GTAGTGAACA','TGTTCACTAC')
  if(spid == "SI-NN-D10" || spid == "SI_NN_D10") dualseqs <- c('ACTGTGCCAA','TAACCGTTAT','ATAACGGTTA')
  if(spid == "SI-NN-D11" || spid == "SI_NN_D11") dualseqs <- c('CTACTCCCAT','TACGTTGCGG','CCGCAACGTA')
  if(spid == "SI-NN-D12" || spid == "SI_NN_D12") dualseqs <- c('AGACTCTAAC','GTGTCGATGT','ACATCGACAC')
  if(spid == "SI-NN-E1" || spid == "SI_NN_E1") dualseqs <- c('TGAGCCGGCA','TGCTGCATGT','ACATGCAGCA')
  if(spid == "SI-NN-E2" || spid == "SI_NN_E2") dualseqs <- c('TAGCTTGCGT','GCTGCTAAAG','CTTTAGCAGC')
  if(spid == "SI-NN-E3" || spid == "SI_NN_E3") dualseqs <- c('GTACACCGGG','AACAGTCGCG','CGCGACTGTT')
  if(spid == "SI-NN-E4" || spid == "SI_NN_E4") dualseqs <- c('CGCGTAGACG','AAAGTAGCTG','CAGCTACTTT')
  if(spid == "SI-NN-E5" || spid == "SI_NN_E5") dualseqs <- c('CCGATATATT','AACTATCCGA','TCGGATAGTT')
  if(spid == "SI-NN-E6" || spid == "SI_NN_E6") dualseqs <- c('TTGGGCGGGA','ATGTATCTGT','ACAGATACAT')
  if(spid == "SI-NN-E7" || spid == "SI_NN_E7") dualseqs <- c('GTTTATGAGG','GCCAAACAGC','GCTGTTTGGC')
  if(spid == "SI-NN-E8" || spid == "SI_NN_E8") dualseqs <- c('TTGGATACTC','CGTACTGAAG','CTTCAGTACG')
  if(spid == "SI-NN-E9" || spid == "SI_NN_E9") dualseqs <- c('GCTATCTATC','AAAGATGGAT','ATCCATCTTT')
  if(spid == "SI-NN-E10" || spid == "SI_NN_E10") dualseqs <- c('ATGTGATCGC','TGACCTACCT','AGGTAGGTCA')
  if(spid == "SI-NN-E11" || spid == "SI_NN_E11") dualseqs <- c('TCCTCACATG','AACAGATTCA','TGAATCTGTT')
  if(spid == "SI-NN-E12" || spid == "SI_NN_E12") dualseqs <- c('GCCTCCTAAT','CTCCTCCTGT','ACAGGAGGAG')
  if(spid == "SI-NN-F1" || spid == "SI_NN_F1") dualseqs <- c('CGAGGCTGAT','TCGACTTTCT','AGAAAGTCGA')
  if(spid == "SI-NN-F2" || spid == "SI_NN_F2") dualseqs <- c('CTCATGACAT','TGTTCCGCAC','GTGCGGAACA')
  if(spid == "SI-NN-F3" || spid == "SI_NN_F3") dualseqs <- c('AACGCGGCAA','GCCCGTTTAC','GTAAACGGGC')
  if(spid == "SI-NN-F4" || spid == "SI_NN_F4") dualseqs <- c('AACACCTCTC','GATTGTATGA','TCATACAATC')
  if(spid == "SI-NN-F5" || spid == "SI_NN_F5") dualseqs <- c('AAATAACGCG','ATAGAGGAGC','GCTCCTCTAT')
  if(spid == "SI-NN-F6" || spid == "SI_NN_F6") dualseqs <- c('GAAATCTTGT','TAGACGCCAC','GTGGCGTCTA')
  if(spid == "SI-NN-F7" || spid == "SI_NN_F7") dualseqs <- c('TAGATGGACT','CCAGGGATAT','ATATCCCTGG')
  if(spid == "SI-NN-F8" || spid == "SI_NN_F8") dualseqs <- c('CAATCTGTAT','TCCCATCGTT','AACGATGGGA')
  if(spid == "SI-NN-F9" || spid == "SI_NN_F9") dualseqs <- c('GCGAAAGTAT','GTGTCACAGA','TCTGTGACAC')
  if(spid == "SI-NN-F10" || spid == "SI_NN_F10") dualseqs <- c('CCCAGAGCTT','ATGCTGTAAT','ATTACAGCAT')
  if(spid == "SI-NN-F11" || spid == "SI_NN_F11") dualseqs <- c('GACTACAGGA','AGTGACAAAG','CTTTGTCACT')
  if(spid == "SI-NN-F12" || spid == "SI_NN_F12") dualseqs <- c('CAATTCATGG','ACGTAGTTCG','CGAACTACGT')
  if(spid == "SI-NN-G1" || spid == "SI_NN_G1") dualseqs <- c('TGGGAAGGGC','ACTAACGATG','CATCGTTAGT')
  if(spid == "SI-NN-G2" || spid == "SI_NN_G2") dualseqs <- c('TACTTCGCAT','AACGGTGTCG','CGACACCGTT')
  if(spid == "SI-NN-G3" || spid == "SI_NN_G3") dualseqs <- c('TAAACTGGCA','CCGACCTCTT','AAGAGGTCGG')
  if(spid == "SI-NN-G4" || spid == "SI_NN_G4") dualseqs <- c('TTGAAGAACT','CAACACCAGG','CCTGGTGTTG')
  if(spid == "SI-NN-G5" || spid == "SI_NN_G5") dualseqs <- c('TCCCTCGTCA','ATCAGGTGTG','CACACCTGAT')
  if(spid == "SI-NN-G6" || spid == "SI_NN_G6") dualseqs <- c('ACTGGCCTAA','CGCTCCCTTG','CAAGGGAGCG')
  if(spid == "SI-NN-G7" || spid == "SI_NN_G7") dualseqs <- c('TCATAATGCA','GATGAGGCGA','TCGCCTCATC')
  if(spid == "SI-NN-G8" || spid == "SI_NN_G8") dualseqs <- c('CGCCACGTTA','CTTTCATAGG','CCTATGAAAG')
  if(spid == "SI-NN-G9" || spid == "SI_NN_G9") dualseqs <- c('TGGACTAAGC','CTTAACATCG','CGATGTTAAG')
  if(spid == "SI-NN-G10" || spid == "SI_NN_G10") dualseqs <- c('AAGCCCGGCA','GAGTTTCCGT','ACGGAAACTC')
  if(spid == "SI-NN-G11" || spid == "SI_NN_G11") dualseqs <- c('ATAGTGGCGG','TACTGTTGTT','AACAACAGTA')
  if(spid == "SI-NN-G12" || spid == "SI_NN_G12") dualseqs <- c('CCAACCGGAC','GTTCTATCTA','TAGATAGAAC')
  if(spid == "SI-NN-H1" || spid == "SI_NN_H1") dualseqs <- c('TTACAGAGGG','GTTTATGGCA','TGCCATAAAC')
  if(spid == "SI-NN-H2" || spid == "SI_NN_H2") dualseqs <- c('TCCAACAACG','GTCCGGGTTT','AAACCCGGAC')
  if(spid == "SI-NN-H3" || spid == "SI_NN_H3") dualseqs <- c('AAAGCATGTC','AGATACTACT','AGTAGTATCT')
  if(spid == "SI-NN-H4" || spid == "SI_NN_H4") dualseqs <- c('AGAAGTCGTT','AGGACATTTA','TAAATGTCCT')
  if(spid == "SI-NN-H5" || spid == "SI_NN_H5") dualseqs <- c('GCTAGCGTTC','TCGCGTGGTG','CACCACGCGA')
  if(spid == "SI-NN-H6" || spid == "SI_NN_H6") dualseqs <- c('CAACTAGCTT','TCAACTAATC','GATTAGTTGA')
  if(spid == "SI-NN-H7" || spid == "SI_NN_H7") dualseqs <- c('AGGGCCAGTT','TAGGGCAAAT','ATTTGCCCTA')
  if(spid == "SI-NN-H8" || spid == "SI_NN_H8") dualseqs <- c('CTCTATGCGT','CCTCTGATGC','GCATCAGAGG')
  if(spid == "SI-NN-H9" || spid == "SI_NN_H9") dualseqs <- c('GTCCATACAA','AGCCACTCAC','GTGAGTGGCT')
  if(spid == "SI-NN-H10" || spid == "SI_NN_H10") dualseqs <- c('AGGGATGCAA','CTCATATCCT','AGGATATGAG')
  if(spid == "SI-NN-H11" || spid == "SI_NN_H11") dualseqs <- c('TTCAAGTCCT','CAGCAGCTAA','TTAGCTGCTG')
  if(spid == "SI-NN-H12" || spid == "SI_NN_H12") dualseqs <- c('CTCCAACCTA','GCACCGCATC','GATGCGGTGC')


  if(spid == "SI-NT-A1" || spid == "SI_NT_A1") dualseqs <- c('ATTTACCGCA','GACAATAAAG','CTTTATTGTC')
  if(spid == "SI-NT-A2" || spid == "SI_NT_A2") dualseqs <- c('TTGTCGTAGA','CAATGTAGCA','TGCTACATTG')
  if(spid == "SI-NT-A3" || spid == "SI_NT_A3") dualseqs <- c('AGTCCTGCGG','TGACACAAGT','ACTTGTGTCA')
  if(spid == "SI-NT-A4" || spid == "SI_NT_A4") dualseqs <- c('TTGTTCATGT','TGACAGCTGA','TCAGCTGTCA')
  if(spid == "SI-NT-A5" || spid == "SI_NT_A5") dualseqs <- c('TCAGGAAGGA','GAACGTGCTT','AAGCACGTTC')
  if(spid == "SI-NT-A6" || spid == "SI_NT_A6") dualseqs <- c('CTGTTAGAGG','CCACGCTTCG','CGAAGCGTGG')
  if(spid == "SI-NT-A7" || spid == "SI_NT_A7") dualseqs <- c('AGATGAGAAT','GTCGACGGGT','ACCCGTCGAC')
  if(spid == "SI-NT-A8" || spid == "SI_NT_A8") dualseqs <- c('CCAAAGCCGG','ACCGTGCACA','TGTGCACGGT')
  if(spid == "SI-NT-A9" || spid == "SI_NT_A9") dualseqs <- c('AGTCATAATG','AGGCTTGAAA','TTTCAAGCCT')
  if(spid == "SI-NT-A10" || spid == "SI_NT_A10") dualseqs <- c('TTCATCAGAG','TTGTCGTCTC','GAGACGACAA')
  if(spid == "SI-NT-A11" || spid == "SI_NT_A11") dualseqs <- c('GAAGCGCGAA','CAGCGAAATT','AATTTCGCTG')
  if(spid == "SI-NT-A12" || spid == "SI_NT_A12") dualseqs <- c('ATCCGCCGAA','GCTACAGAAT','ATTCTGTAGC')
  if(spid == "SI-NT-B1" || spid == "SI_NT_B1") dualseqs <- c('CTTATTGTGG','AGCTGTGGGT','ACCCACAGCT')
  if(spid == "SI-NT-B2" || spid == "SI_NT_B2") dualseqs <- c('ATTCGTTGGG','CATCAGGAGC','GCTCCTGATG')
  if(spid == "SI-NT-B3" || spid == "SI_NT_B3") dualseqs <- c('GTGGCCTCAT','TCGAAAGTGA','TCACTTTCGA')
  if(spid == "SI-NT-B4" || spid == "SI_NT_B4") dualseqs <- c('CTATGGCATC','CTCTGAGCGC','GCGCTCAGAG')
  if(spid == "SI-NT-B5" || spid == "SI_NT_B5") dualseqs <- c('AAACCACAGT','CCGCAAATGG','CCATTTGCGG')
  if(spid == "SI-NT-B6" || spid == "SI_NT_B6") dualseqs <- c('ATGTATCCAC','CAGGCTGAGG','CCTCAGCCTG')
  if(spid == "SI-NT-B7" || spid == "SI_NT_B7") dualseqs <- c('GATGAGTCTG','TATGTAACCG','CGGTTACATA')
  if(spid == "SI-NT-B8" || spid == "SI_NT_B8") dualseqs <- c('CTCACAATAA','CCGTGTTTAA','TTAAACACGG')
  if(spid == "SI-NT-B9" || spid == "SI_NT_B9") dualseqs <- c('TCTGTCGCAA','ATCCGAACTG','CAGTTCGGAT')
  if(spid == "SI-NT-B10" || spid == "SI_NT_B10") dualseqs <- c('CATCGAGAAG','GTGCATCCGC','GCGGATGCAC')
  if(spid == "SI-NT-B11" || spid == "SI_NT_B11") dualseqs <- c('ACGTATTGGG','CAATCGCAAA','TTTGCGATTG')
  if(spid == "SI-NT-B12" || spid == "SI_NT_B12") dualseqs <- c('CTTACGCGAC','CCTGTCGGAT','ATCCGACAGG')
  if(spid == "SI-NT-C1" || spid == "SI_NT_C1") dualseqs <- c('GATAGTGAAG','CGTTCGCCAG','CTGGCGAACG')
  if(spid == "SI-NT-C2" || spid == "SI_NT_C2") dualseqs <- c('CTGTGCAATG','ATGGTGCTTT','AAAGCACCAT')
  if(spid == "SI-NT-C3" || spid == "SI_NT_C3") dualseqs <- c('AAAGCTGAGC','CAGGAACGAG','CTCGTTCCTG')
  if(spid == "SI-NT-C4" || spid == "SI_NT_C4") dualseqs <- c('GTCCAAGTCG','GTCATGGCAC','GTGCCATGAC')
  if(spid == "SI-NT-C5" || spid == "SI_NT_C5") dualseqs <- c('GATTTCCATC','CTTATGGTCA','TGACCATAAG')
  if(spid == "SI-NT-C6" || spid == "SI_NT_C6") dualseqs <- c('CTAAATGGAT','AATCACATAC','GTATGTGATT')
  if(spid == "SI-NT-C7" || spid == "SI_NT_C7") dualseqs <- c('ATTTCAGTTC','GCAGCATTAA','TTAATGCTGC')
  if(spid == "SI-NT-C8" || spid == "SI_NT_C8") dualseqs <- c('TTGCGACGTC','TTAATCCACA','TGTGGATTAA')
  if(spid == "SI-NT-C9" || spid == "SI_NT_C9") dualseqs <- c('GTACTGTCCA','TTTGTTGGAA','TTCCAACAAA')
  if(spid == "SI-NT-C10" || spid == "SI_NT_C10") dualseqs <- c('AAACGGTTTA','TCTTCGTTAC','GTAACGAAGA')
  if(spid == "SI-NT-C11" || spid == "SI_NT_C11") dualseqs <- c('CGATAGTTCT','GCGATTTCCT','AGGAAATCGC')
  if(spid == "SI-NT-C12" || spid == "SI_NT_C12") dualseqs <- c('TACTTTAGCT','GAGTTATTTG','CAAATAACTC')
  if(spid == "SI-NT-D1" || spid == "SI_NT_D1") dualseqs <- c('CTGGGATTAA','AGTAAAGTTC','GAACTTTACT')
  if(spid == "SI-NT-D2" || spid == "SI_NT_D2") dualseqs <- c('TACACTATTC','ATTGCAACGT','ACGTTGCAAT')
  if(spid == "SI-NT-D3" || spid == "SI_NT_D3") dualseqs <- c('AGAGCTATTT','CTGGCACCCA','TGGGTGCCAG')
  if(spid == "SI-NT-D4" || spid == "SI_NT_D4") dualseqs <- c('AGGTCGTTAT','AGGTTATCCA','TGGATAACCT')
  if(spid == "SI-NT-D5" || spid == "SI_NT_D5") dualseqs <- c('GCTGGACAGG','TAATCTCTGT','ACAGAGATTA')
  if(spid == "SI-NT-D6" || spid == "SI_NT_D6") dualseqs <- c('ACTTAGATGG','AATGTTGAGT','ACTCAACATT')
  if(spid == "SI-NT-D7" || spid == "SI_NT_D7") dualseqs <- c('AGGCGCGAGT','TGTGTCTATA','TATAGACACA')
  if(spid == "SI-NT-D8" || spid == "SI_NT_D8") dualseqs <- c('GAGGTCGTAC','CAACTGCGGG','CCCGCAGTTG')
  if(spid == "SI-NT-D9" || spid == "SI_NT_D9") dualseqs <- c('CCCAATGAGC','AGTTTCTGAT','ATCAGAAACT')
  if(spid == "SI-NT-D10" || spid == "SI_NT_D10") dualseqs <- c('ACATTCAGGG','GTCCTGAGAG','CTCTCAGGAC')
  if(spid == "SI-NT-D11" || spid == "SI_NT_D11") dualseqs <- c('AGAAACGGTG','TCACCCAGCA','TGCTGGGTGA')
  if(spid == "SI-NT-D12" || spid == "SI_NT_D12") dualseqs <- c('AGTTGCAAGT','ATACTTCATG','CATGAAGTAT')
  if(spid == "SI-NT-E1" || spid == "SI_NT_E1") dualseqs <- c('GCCTAGAAAT','ACTACTCCGG','CCGGAGTAGT')
  if(spid == "SI-NT-E2" || spid == "SI_NT_E2") dualseqs <- c('TACAAAGATG','CCGATGACGT','ACGTCATCGG')
  if(spid == "SI-NT-E3" || spid == "SI_NT_E3") dualseqs <- c('GCAGGCTTAG','CGTCTATTGC','GCAATAGACG')
  if(spid == "SI-NT-E4" || spid == "SI_NT_E4") dualseqs <- c('CAGCTCCAAT','TAGGAGGGTG','CACCCTCCTA')
  if(spid == "SI-NT-E5" || spid == "SI_NT_E5") dualseqs <- c('TTTGGAGAAA','CAGCCCTTGA','TCAAGGGCTG')
  if(spid == "SI-NT-E6" || spid == "SI_NT_E6") dualseqs <- c('TGTCGCTTTC','CCTAAAGGGT','ACCCTTTAGG')
  if(spid == "SI-NT-E7" || spid == "SI_NT_E7") dualseqs <- c('GTGTTGCGAG','CACTTTGAGC','GCTCAAAGTG')
  if(spid == "SI-NT-E8" || spid == "SI_NT_E8") dualseqs <- c('GTCTGTTCTG','TCTGATGCTT','AAGCATCAGA')
  if(spid == "SI-NT-E9" || spid == "SI_NT_E9") dualseqs <- c('TCGCTCAATC','GCTTACGTGA','TCACGTAAGC')
  if(spid == "SI-NT-E10" || spid == "SI_NT_E10") dualseqs <- c('CGTTACTAGC','AATGGCCATT','AATGGCCATT')
  if(spid == "SI-NT-E11" || spid == "SI_NT_E11") dualseqs <- c('GTGCAGATTT','TAAAGGCGAT','ATCGCCTTTA')
  if(spid == "SI-NT-E12" || spid == "SI_NT_E12") dualseqs <- c('GTTACCGAAA','CGTTTGAATC','GATTCAAACG')
  if(spid == "SI-NT-F1" || spid == "SI_NT_F1") dualseqs <- c('CGGTATGGAA','TGCAACTGGT','ACCAGTTGCA')
  if(spid == "SI-NT-F2" || spid == "SI_NT_F2") dualseqs <- c('TCGGTTTAGT','TAAAGTGTTC','GAACACTTTA')
  if(spid == "SI-NT-F3" || spid == "SI_NT_F3") dualseqs <- c('CTTTGCTTTG','ACATTCATTC','GAATGAATGT')
  if(spid == "SI-NT-F4" || spid == "SI_NT_F4") dualseqs <- c('ATGATCGCAC','GCGGTATGTA','TACATACCGC')
  if(spid == "SI-NT-F5" || spid == "SI_NT_F5") dualseqs <- c('CGGCGTGTTA','TGAGACGCGG','CCGCGTCTCA')
  if(spid == "SI-NT-F6" || spid == "SI_NT_F6") dualseqs <- c('TCATTTCCGC','ACAGCTGTTG','CAACAGCTGT')
  if(spid == "SI-NT-F7" || spid == "SI_NT_F7") dualseqs <- c('TTGTGCCCAG','TTGCGAGTTG','CAACTCGCAA')
  if(spid == "SI-NT-F8" || spid == "SI_NT_F8") dualseqs <- c('AGCCAACGTG','TCAAAGGAAC','GTTCCTTTGA')
  if(spid == "SI-NT-F9" || spid == "SI_NT_F9") dualseqs <- c('GATCCATACT','ATCGAGAGAA','TTCTCTCGAT')
  if(spid == "SI-NT-F10" || spid == "SI_NT_F10") dualseqs <- c('TTTCCATGAA','CGGTTCTAAC','GTTAGAACCG')
  if(spid == "SI-NT-F11" || spid == "SI_NT_F11") dualseqs <- c('TCTTTATCGG','CCCAATAGCG','CGCTATTGGG')
  if(spid == "SI-NT-F12" || spid == "SI_NT_F12") dualseqs <- c('GAACCCGATG','TCTATCCGGG','CCCGGATAGA')
  if(spid == "SI-NT-G1" || spid == "SI_NT_G1") dualseqs <- c('GATGTCTGTG','CGTGCTACCG','CGGTAGCACG')
  if(spid == "SI-NT-G2" || spid == "SI_NT_G2") dualseqs <- c('TGTTAGACTA','TCATGACGAC','GTCGTCATGA')
  if(spid == "SI-NT-G3" || spid == "SI_NT_G3") dualseqs <- c('TAGAACGCTT','GTCGCAAACG','CGTTTGCGAC')
  if(spid == "SI-NT-G4" || spid == "SI_NT_G4") dualseqs <- c('AATTTGACGT','AGAAGTGAGG','CCTCACTTCT')
  if(spid == "SI-NT-G5" || spid == "SI_NT_G5") dualseqs <- c('GAGTGGCCAC','GATGCACAGT','ACTGTGCATC')
  if(spid == "SI-NT-G6" || spid == "SI_NT_G6") dualseqs <- c('ATCAGTTACG','TACAATCTCT','AGAGATTGTA')
  if(spid == "SI-NT-G7" || spid == "SI_NT_G7") dualseqs <- c('TGTAAGCTTT','CGGACTAGTC','GACTAGTCCG')
  if(spid == "SI-NT-G8" || spid == "SI_NT_G8") dualseqs <- c('GCTTCGGTCT','CGCGGGTTAG','CTAACCCGCG')
  if(spid == "SI-NT-G9" || spid == "SI_NT_G9") dualseqs <- c('AGTCTAAGAC','CTGAAGTGAA','TTCACTTCAG')
  if(spid == "SI-NT-G10" || spid == "SI_NT_G10") dualseqs <- c('TGAAACATTC','CCTCCTGCCT','AGGCAGGAGG')
  if(spid == "SI-NT-G11" || spid == "SI_NT_G11") dualseqs <- c('CCTCACGAAA','TGCCCTGTGC','GCACAGGGCA')
  if(spid == "SI-NT-G12" || spid == "SI_NT_G12") dualseqs <- c('CTACGGGCTT','AGGCGTCCCT','AGGGACGCCT')
  if(spid == "SI-NT-H1" || spid == "SI_NT_H1") dualseqs <- c('GCGGCGTAAA','CACGCGATCA','TGATCGCGTG')
  if(spid == "SI-NT-H2" || spid == "SI_NT_H2") dualseqs <- c('TCTCCTTCGG','TGTCCGTCGC','GCGACGGACA')
  if(spid == "SI-NT-H3" || spid == "SI_NT_H3") dualseqs <- c('CCTTACCCAT','TCCCGGCAAC','GTTGCCGGGA')
  if(spid == "SI-NT-H4" || spid == "SI_NT_H4") dualseqs <- c('AAGGAACATC','TATCTGTGGG','CCCACAGATA')
  if(spid == "SI-NT-H5" || spid == "SI_NT_H5") dualseqs <- c('GTGGGAACTT','GCACTTACAG','CTGTAAGTGC')
  if(spid == "SI-NT-H6" || spid == "SI_NT_H6") dualseqs <- c('ACCGCACCAC','TCGGATTATA','TATAATCCGA')
  if(spid == "SI-NT-H7" || spid == "SI_NT_H7") dualseqs <- c('ACCTTTATCT','CGAGTGCCAA','TTGGCACTCG')
  if(spid == "SI-NT-H8" || spid == "SI_NT_H8") dualseqs <- c('AGTAGCCCGT','TTCGGGACCT','AGGTCCCGAA')
  if(spid == "SI-NT-H9" || spid == "SI_NT_H9") dualseqs <- c('AGATAGCATA','CATTTCCGGA','TCCGGAAATG')
  if(spid == "SI-NT-H10" || spid == "SI_NT_H10") dualseqs <- c('TGGCGTTAAA','TGTTAAGATG','CATCTTAACA')
  if(spid == "SI-NT-H11" || spid == "SI_NT_H11") dualseqs <- c('TCAGGCGAAA','TCTCTGGAAG','CTTCCAGAGA')
  if(spid == "SI-NT-H12" || spid == "SI_NT_H12") dualseqs <- c('AAGACATAGC','ATGTGGACGT','ACGTCCACAT')



  if(any(is.na(spseqs)) && !any(is.na(dualseqs))){
    #for(i in 1:10)cat("WARNING: ONLY #1 and #3 index sequences were used!\n")
    #return(c(paste0(dualseqs[1], dualseqs[3])))
    return(list(A=paste0(dualseqs[1], dualseqs[2]), B=paste0(dualseqs[1], dualseqs[3])))
  }else{
    if(!any(is.na(spseqs))) return(as.list(spseqs)) else return(NA)
  }
}

.rstest <- function(r, coef) r * (1 + 1/r)^(1 + coef)

.averaging_transform <- function(r, nr){
    d <- c(1, r[ 2:length(r) ] - r[1:(length(r)-1)])
    dr <- c( 0.5 * (d[2:length(d)] + d[1:(length(d)-1)]),d[ length(d) ])
       nr/dr
}


.do.EXACT.DEBUG <- FALSE 
#.do.EXACT.DEBUG <- TRUE 
.simple.Good.Turing.Freq<-function( raw.freq ){
  n_zero <- sum(raw.freq == 0)
  times <- table(raw.freq[raw.freq>0])
  obs <- sort(as.numeric(names(times)))
  times <- times[as.character(obs)]

  nobs <- length(obs)
  if(nobs < .min_mySGT_input_size){
    cat("Note: not enough cells in the data for performing cell rescuing.\n")
    return(NA)
  }else{
      if(nobs != length(times)) stop("The oservation array doesn't match the frequency array.")
      total_observed <- sum(obs * times)
      tval <- matrix(0, ncol=2, nrow=nobs)
      ks <- c(obs[2:nobs], 2*obs[nobs] - obs[nobs-1])
      tval[,1] <- log(obs)
      tval[,2] <- log( .averaging_transform(obs,times))
      meanx <- mean(tval[,1])
      meany <- mean(tval[,2])
      slope <- sum( (tval[,1]-meanx) * (tval[,2]-meany) ) / sum((tval[,1]-meanx) ^2)
      intercept <- meany - slope * meanx
  
      obs.in.next <- (obs+1) %in% obs
      SD <- ( 1+(1:nobs) ) / times *(c(times[2:nobs], 0)* (1+c(times[2:nobs],0) /times))^.5
      SD [!obs.in.next] <- 1
  
      GT <- (obs+1)/obs * c(times[2:nobs],0)/times
      GT [!obs.in.next] <- 0
      y <- .rstest(obs,slope)
      LGT <- y / obs
  
      xy.sim <- ( abs(GT-LGT) * (1:nobs) / SD ) > 1.65
      min.no.turing <- min(which(!xy.sim))
      r <- GT
      if(any(!xy.sim))r[min.no.turing:nobs] <- LGT[min.no.turing:nobs]
      p_zero <- times[1] / total_observed
      rs <- (1-p_zero) * obs * r / sum(r*times*obs/total_observed)
      total_rs <- sum(rs * times)
      puniq <- (1-p_zero) * rs / total_rs
      pres <- rep(p_zero / n_zero, length(raw.freq))
      pres [raw.freq>0] <- puniq[match( raw.freq[raw.freq>0], obs )]
      ret <- list(p0=p_zero, p=pres)

      if(.do.EXACT.DEBUG){
        print("DEBUG_EXACT")
        skf <- "/tmp/del4-YangLiao-GoodTuring-Pr-vals.txt"
        if(file.exists(skf))file.remove(skf)
        sink(skf)
        for(rr in pres){
          cat(sprintf("%.20g\n",rr))
        }
        sink()
      }

      ret
  }
}

.min_mySGT_input_size <- 5
.smoothed <- function(i,intercept,slope) 2.718281828^(intercept + slope * log(i))

# Simple Good Turing
.mySGT <- function(obs.per.spe){
    obs.tab <- table(obs.per.spe[obs.per.spe>0])
    obs <- sort( as.numeric(names(obs.tab)))
    obs.tab <- obs.tab[ as.character(obs) ]
    sgtr <- .mySGTsorted(as.numeric(names(obs.tab)), obs.tab)
    res <- obs.per.spe
    res[obs.per.spe!=0] <- sgtr$p[match(as.character(obs.per.spe[obs.per.spe!=0]), names(obs.tab))]
    n_zero <- sum(obs.per.spe==0)
    res[obs.per.spe==0] <- sgtr$p0/n_zero
    res
}

.mySGTsorted<-function( obs, times ){
    if(any(obs != sort(obs)))stop("Observations have to be sorted.")
    nobs <- length(obs)
    if(nobs < .min_mySGT_input_size) stop("Not enough element in the observation array.")
    if(nobs != length(times)) stop("The oservation array doesn't match the frequency array.")
    total_observed <- sum(obs * times)
    p_zero <- 0
    if(1 %in% obs) p_zero <- times[which(1==obs)[1]] / total_observed
    tval <- matrix(0, ncol=2, nrow=nobs)
    ks <- c(obs[2:nobs], 2*obs[nobs] - obs[nobs-1])
    tval[,1] <- log(obs)
    tval[,2] <- log(2*times / (ks - c(0, obs[1:(nobs-1)])))
    meanX <- mean(tval[,1])
    meanY <- mean(tval[,2])
    slope <- sum( (tval[,1]-meanX) * (tval[,2]-meanY) ) / sum((tval[,1]-meanX) ^2)
    intercept <- meanY - slope * meanX

    y <- (1+obs) * .smoothed(1+obs, intercept, slope ) / .smoothed(obs, intercept, slope)
    x <- (1+obs) * c((times[2:nobs] / times[1:(nobs-1)]), y[nobs])
    xy.sim <- abs(x-y) <= 1.96*( (obs+1)^2 * c(times[2:nobs], 0)/ times^2 * (1+ c(times[2:nobs], 0) / times ))^0.5
    obs.in.next <- (obs+1) %in% obs
    min.inv <- min(which( xy.sim | !obs.in.next ))
    r<-x
    if(min.inv>0)r[min.inv:nobs] <- y[min.inv:nobs]
    list(p0=p_zero, p=(1-p_zero) * r / sum(r*times))
}


.cellCounts_try_cellbarcode <- function( input.directory, sample.sheet, cell.barcode.list, nreads.testing, input.mode, result.prefix.fname){ # the three parameters can only be one string, not strings!
    if(is.null(sample.sheet)) sample.sheet<-"."
    cmd <- paste0(c(input.directory, sample.sheet, cell.barcode.list, as.character(nreads.testing), input.mode, result.prefix.fname), collapse=.R_param_splitor)
    rvs <- as.integer(rep(0,5))
    C_args <- .C("R_try_cell_barcode_wrapper",nargs=as.integer(6),argv=as.character(cmd),retv=rvs ,PACKAGE="Rsubread")
    return_val <- ifelse(C_args$retv[1]==0,"FINISHED","ERROR")
    tested_reads <- C_args$retv[2]
    good_sample <- C_args$retv[3]
    good_cell <- C_args$retv[4]
    if(return_val=="ERROR"){
        return(NA)
    }
    return(c(tested_reads, good_sample, good_cell))
}

.dataURL <- function(uri){
    sprintf("http://shilab-bioinformatics.github.io/cellCounts/Barcodes/%s",uri)
}

.get.illumina.dev <- function(lns){
  for(lnn in lns){
    if(grepl("<ApplicationName>", lnn) && grepl("</ApplicationName>", lnn))return(
      ifelse(grepl("HiSeq", lnn),"HiSeq",
      ifelse(grepl("MiSeq", lnn),"MiSeq",
      ifelse(grepl("iSeq", lnn),"iSeq",
      ifelse(grepl("NextSeq", lnn),"NextSeq",
      ifelse(grepl("NovaSeq", lnn),"NovaSeq","Unsupported"))))))
  }
  return(NA)
}
.get.illumina.ver <- function(lns){
  for(lnn in lns){
    if(grepl("<ApplicationVersion>", lnn) && grepl("</ApplicationVersion>", lnn)) {
      spns <- unlist(strsplit(lnn, "Version>"))[2]
      spns <- unlist(strsplit(spns, "<"))[1]
      return(as.numeric(unlist(strsplit(spns,"[.]"))))
    }
  }
  return(NA)
}

# It only returns T or F when PETurnaround happens exactly once and IndexRead2 happens exactly once.
# Or it returns NA. 
.get.novaseq.i2.rounded <- function(lns){
  peround.no <- NA
  lnnno <- 1
  for(lnn in lns){
    if(grepl("PETurnaround", lnn) || grepl("PairedEndTurnaround", lnn)){
      if(!is.na(peround.no))return(NA)
      peround.no <- lnnno
    }
    lnnno <- 1+lnnno
  }
  if(is.na(peround.no)) return(NA) # no "PE turnaaround" at all 

  lnnno <- 1
  ret <- NA
  for(lnn in lns){
    if(grepl("IndexRead2", lnn)){
      if(!is.na(ret))return(NA)
      if(F)cat("REV_DEBUG NovaSeq-New Idx2 line",lnnno," ; Rev line", peround.no,".\n")

      ret <- ( lnnno > peround.no ) # if IndexRead2 happend before PEturned, then I2 is stright. 
           # If PEturned is before IndexRead2, then I2 is reversed.
           # If not both IndexRead2 and PEturned were found, it is not right.
    }
    lnnno <- 1+lnnno
  }
  return(ret)
}

.get.novaseq.i2.BP14.used <- function(lns){
  looking <- F
  ret <- F
  for(lnn in lns){
    if(grepl("<ChemistryStep", lnn) && grepl('Description="Index', lnn)) looking <- T
    if(grepl("</ChemistryStep>", lnn)) looking <- F
    if(looking && grepl('ReagentName="BP14"', lnn))ret <- T
  }
  return(ret)
}

.get.illumina.flowcell <- function(lns){
  for(lnn in lns){
    if(grepl("<Flowcell>", lnn) && grepl("</Flowcell>", lnn)) {
      spns <- unlist(strsplit(lnn, "Flowcell>"))[2]
      spns <- unlist(strsplit(spns, "<"))[1]
      return(spns)
    } 
  }
  return(NA)
}

.find.DS.in.path <- function(d){
  d # assume that the dir is here.
}

.find.dual.index.I2.reversed <- function(raw.dir, verbose=F){
  raw.dir <- .find.DS.in.path(raw.dir)
  run.param.file <- paste0(raw.dir,"/RunParameters.xml")
  if(!file.exists(run.param.file))stop("No run parameter file is found in the raw dataset.")
  run.param.txt <- readLines(run.param.file ,warn=F)
  illumina.dev.name <- .get.illumina.dev(run.param.txt)
  illumina.dev.ver <- .get.illumina.ver(run.param.txt)
  if(is.na(illumina.dev.name))stop("No Illumina device info is available in the run parameter file.")

  if(illumina.dev.name %in% c("HiSeq")) {
    if(any(is.na(illumina.dev.ver)))stop("No Illumina version info is available in the run parameter file.")
    is.Rev.I2 <-(illumina.dev.ver[1] >2)

    if(verbose)cat("REV_DEBUG HiSeq had new version",illumina.dev.ver," and Rev I2 =",is.Rev.I2,".\n") 
  }else if(illumina.dev.name %in% c("MiSeq"))
    is.Rev.I2 <- F
  else if(illumina.dev.name %in% c("NextSeq","iSeq"))
    is.Rev.I2 <- T
  else if(illumina.dev.name %in% c("NovaSeq")){
    run.info.file <- paste0(raw.dir,"/RunInfo.xml")
    if(!file.exists(run.info.file))stop("No run info file is found in the raw dataset.")
    run.info.txt <- readLines(run.info.file,,warn =F)
    flowcell.id <- .get.illumina.flowcell(run.info.txt)
    if(is.na(flowcell.id))stop("No flowcell id was found in run info file.")
    recipe.new.ver <- (illumina.dev.ver[1] >1) || ( illumina.dev.ver[1] ==1 && illumina.dev.ver[2] >7)
    resp.files <- list.files(paste(raw.dir,"Recipe",sep="/"), pattern="[.]xml$", full.names=T)
    resp.file <- NA
    for(testi in 1:3){ # the last round: any file is good. 
      for(resp.file in resp.files){
        if(testi==1 && grepl(paste0("/",flowcell.id,".xml"), resp.file)) break
        if(testi==2 && grepl(flowcell.id, resp.file)) break
      }
    }
    if(is.na(resp.file))stop("No recipe file was found for this dataset.")
    recipeOne.txt <- readLines(resp.file, warn =F)
    if(recipe.new.ver) is.Rev.I2 <- .get.novaseq.i2.rounded(recipeOne.txt)
    else is.Rev.I2 <- .get.novaseq.i2.BP14.used(recipeOne.txt)
    if(is.na(is.Rev.I2)) stop("The recipe file has a unexpected format.") 
    if(verbose)cat("REV_DEBUG NovaSeq had new version = ",recipe.new.ver," and recipe file = ",resp.file,". Rev I2 = ",is.Rev.I2,"\n")
  }else stop("Wrong dev name was detected.")
  if(verbose)cat("REV_DEBUG Sample had device = ",illumina.dev.name,illumina.dev.ver,".\n")
  return(is.Rev.I2)
}

.reads.for.find.barcode <- 10000
.find_best_CellBC_and_DualIdx <- function( input.directory, result.prefix.fname, sample.sheet=NULL, input.mode="bcl", A_and_B.dual.index.list=NULL, cell.bc.sup.rate=0.6){
    barcode.database.file <- path.expand("~/.Rsubread/cellCounts/known_barcode_sets.txt")
    if(!file.exists(barcode.database.file)){
        dir.create("~/.Rsubread/cellCounts", recursive=TRUE)
        rr <- download.file(.dataURL("known_barcode_sets.txt"), barcode.database.file)
        if(rr!=0)stop("ERROR: the barcode database cannot be retrieved from the Internet. You may still run cellCounts by specifying a local barcode list file to the `cell.barcode.list` option.")
    }
    bcb.sets <- read.delim(barcode.database.file, stringsAsFactors=F, header=T)
    cat(sprintf("Found %d known cell barcode sets.\n", nrow(bcb.sets)))
    max.cell.good <- -1
    for(libf in bcb.sets$File){
        listfile <- path.expand(paste0("~/.Rsubread/cellCounts/",libf))
        if(!file.exists(listfile)){
            rr <- download.file(.dataURL(libf), listfile)
            if(rr!=0)stop("ERROR: the barcode list cannot be retrieved from the Internet. You may still run cellCounts by specifying a local barcode list file to the `cell.barcode.list` option.")
        }
        cat(sprintf("Testing the cell barcodes in %s.\n", libf))
        barcode_res <- .cellCounts_try_cellbarcode(input.directory[1], sample.sheet[1], listfile, .reads.for.find.barcode, input.mode, result.prefix.fname)
        if(length(barcode_res)<3)stop("ERROR: the input sample cannot be processed.")
	tested.all.reads <- barcode_res[1]
	dual.index.verAB <- NA
	if("bcl" == input.mode && F==any(is.null(A_and_B.dual.index.list))){
		idx.supp.tab.fn <- paste0(result.prefix.fname,".idx_verAB_sup")
		idx.supp.tab <- read.delim(idx.supp.tab.fn, header=T, stringsAsFactors=F)
		Asupp <- sum( idx.supp.tab$nSupp[ idx.supp.tab$IndexStr %in% A_and_B.dual.index.list$A ] )/tested.all.reads
		Bsupp <- sum( idx.supp.tab$nSupp[ idx.supp.tab$IndexStr %in% A_and_B.dual.index.list$B ] )/tested.all.reads
		if(Asupp > Bsupp * 2) dual.index.verAB <- "A" 
		if(Asupp * 2 < Bsupp) dual.index.verAB <- "B" 
	}

        sample.good.rate <- barcode_res[2]/tested.all.reads
        cell.good.rate <- barcode_res[3]/tested.all.reads
        max.cell.good <- max(max.cell.good, cell.good.rate)

        cat(sprintf("Cell barcode supporting rate : %.1f%%.\n", cell.good.rate*100.))

        # sample index isn't tested anymore
        # if(input.mode=="bcl" && sample.good.rate < 0.5)cat(sprintf("WARNING: there are only %.1f%% reads having known sample indices. Please check if the sample sheet is correct.\n", sample.good.rate*100.))
        if(cell.good.rate > cell.bc.sup.rate){
            meta.data.verAB <- NA
            if(!any(is.null(A_and_B.dual.index.list))){
               metadata.I2Reversed <- .find.dual.index.I2.reversed(input.directory[1]) 
               meta.data.verAB <- ifelse(metadata.I2Reversed,"B","A")
            }

            if(cell.bc.sup.rate>0)cat(sprintf("Found cell-barcode list '%s' for the input data: supported by %.1f%% reads. \n", libf, cell.good.rate*100.))

            if(F)if(!identical(dual.index.verAB, meta.data.verAB))stop("The dual index workflow versions of A/B inferred from reads and from dataset meta-data are inconsistant!")
            if(F)if(!any(is.null(A_and_B.dual.index.list)))cat(sprintf("The dual index version is likely %s by scanning the reads. The dual index version is likely %s by examining the meta-data in the dataset.\n", dual.index.verAB, meta.data.verAB))
            return(list(cell.barcode=listfile, dual.index=dual.index.verAB, meta=meta.data.verAB))
        }
    }
    stop(sprintf("ERROR: no known cell barcode set was found for the data set. The highest percentage of cell-barcode matched reads is %.1f%%\n", max.cell.good*100.))
}

.read.sparse.mat.by.genes <- function (fn, genes){
  mtxrows <- read.delim(paste0(fn,".spmtx"), skip=2, header=F, sep=' ')
  coln <- read.delim(paste0(fn, ".BCtab"), stringsAsFactors=F, header=F)$V1
  rown <- read.delim(paste0(fn, ".GENEtab"), stringsAsFactors=F, header=F)$V1
  out.gene.idx <- match(rown ,genes )
  gene.ranks <- out.gene.idx[as.numeric(mtxrows[,1])]
  cell.ranks <- as.numeric(mtxrows[,2])
  ret <- Matrix::sparseMatrix(i=gene.ranks, j=cell.ranks , x=as.numeric(mtxrows[,3]))
  rownames(ret) <- genes 
  colnames(ret) <- coln 
  ret
}
.read.sparse.mat <- function (fn){
  #cat("Loading matrix from",fn,"\n")
  mtx <- Matrix::readMM(paste0(fn, ".spmtx"))
  if(file.size(paste0(fn, ".BCtab"))>0){
    coln <- read.delim(paste0(fn, ".BCtab"), stringsAsFactors=F, header=F)$V1
    colnames(mtx) <- coln
  }
  if(file.size(paste0(fn, ".GENEtab"))>0){
    rown <- read.delim(paste0(fn, ".GENEtab"), stringsAsFactors=F, header=F)$V1
    rownames(mtx) <- rown
  }
  mtx
}

.use.DEBUG.data <- NA
# .use.DEBUG.i <- 1

.get.multi.nomial <- function(n, size, prob){
  if(.do.EXACT.DEBUG){
    .use.DEBUG.i <- 1
    ret <- read.table(paste0("/tmp/del4-YangLiao-multi-nomial-SIZE",size,"-",.use.DEBUG.i,".txt"), header=F)
    ret <- t(ret)
   #.use.DEBUG.i <<-  .use.DEBUG.i+1
   #print(ret[1:5,1:5])
    ret
  }else{
    rmultinom(n, size, prob )
  }
}

.simu.multinomial <- function(candi.mat, gene.profile.freq , times=10000){
  bcsizes <- sort(unique( colSums(candi.mat) ))
  ret.nUMI.LLH.tab <- list()
  N10000.LLH <- NA
  Old_bs_one <- NA
  log_E_GTE <- log(gene.profile.freq)

  if(.do.EXACT.DEBUG){
    tff<-"/tmp/del4-YangLiao-for-multi-nomial-steps.txt"
    if(file.exists(tff))file.remove(tff)
    sink(tff)
    for(bcsize_one in bcsizes){
      UMI_step_diff <- NA
      if(!any(is.na(Old_bs_one))) UMI_step_diff <- bcsize_one - Old_bs_one
      st1 <- NA
      if(is.na(Old_bs_one)) {
        st1 <- bcsize_one
      }else if(UMI_step_diff >= 1000){
        st1 <- UMI_step_diff
      }
    
      if(!is.na(st1))cat(st1,"\n")
      Old_bs_one <- bcsize_one
    }
    sink()
    system("python /usr/local/work/liao/subread/scripts/Cellranger-replicate/CrepPY-multi-nomial.py")
  }

  Old_bs_one <- NA
  N1000.FstLLH <- c()

  if(.do.EXACT.DEBUG) M50.gene.ids <- read.table("/tmp/del4-YangLiao-50M-gene-nos.txt", header=F)$V1
  all.steps.len <- 0

  for(bcsize_one in bcsizes){
    UMI_step_diff <- NA
    if(!is.na(Old_bs_one)) UMI_step_diff <- bcsize_one - Old_bs_one

    if(is.na(Old_bs_one)){
    }else if(UMI_step_diff >= 1000){
    }else{
      step_len <- bcsize_one - Old_bs_one
      all.steps.len <- step_len + all.steps.len
    }
    Old_bs_one <- bcsize_one
  }

  Old_bs_one <- NA
  M50.in.loop.K <- 0
  for(bcsize_one in bcsizes){
    UMI_step_diff <- NA
    if(!any(is.na(Old_bs_one))) UMI_step_diff <- bcsize_one - Old_bs_one
  
    if(any(is.na(N10000.LLH))){
      N10000 <- .get.multi.nomial(n=times, size=bcsize_one, prob=gene.profile.freq )
      #print("======== N10000 and PROF DIM =======")
      #print(dim(N10000))
      #print(length(gene.profile.freq))
      N10000.LLH <- apply(N10000, 2, function(x) dmultinom(x, prob=gene.profile.freq, log =T ))
    }else if(UMI_step_diff >= 1000){
      UMI_step_diff_N10000 <- .get.multi.nomial(n=times, size=UMI_step_diff, prob=gene.profile.freq )
      N10000 <- N10000 + UMI_step_diff_N10000
      N10000.LLH <- apply(N10000, 2, function(x) dmultinom(x, prob=gene.profile.freq, log =T ))
    }else{
      step_len <- bcsize_one - Old_bs_one
      for(curi in (Old_bs_one+1):bcsize_one){
        if(.do.EXACT.DEBUG){
          UMI_step_indices_N10000 <- M50.gene.ids[1+M50.in.loop.K + (0:9999) * all.steps.len ]
          if(curi == 588){
            tff<-"/tmp/del4-YangLiao-DEBUG-588-10KJ.txt"
            if(file.exists(tff))file.remove(tff)
            sink(tff)
            for(pi in 1:10000){
                cat(pi," ", N10000[UMI_step_indices_N10000[pi],pi],"\n")
            }
            sink()
          }
          M50.in.loop.K <- 1+M50.in.loop.K
        }else{
          UMI_step_indices_N10000 <- sample(1:nrow(candi.mat), size=times, prob=gene.profile.freq, replace=T)
        }
        for(idi in 1:times){
          N10000[ UMI_step_indices_N10000[idi] ,idi ] <- N10000[ UMI_step_indices_N10000[idi] ,idi ]+1
        }
        UMI_step_values_N10000 <- rep(0, times)
        for(idi in 1:times){
          UMI_step_values_N10000[idi] <- N10000[UMI_step_indices_N10000[idi] ,idi ]
        }
        #print(UMI_step_values_N10000)
        N10000.LLH <- N10000.LLH + log_E_GTE[ UMI_step_indices_N10000 ] + log((curi) / UMI_step_values_N10000)

        if(.do.EXACT.DEBUG && curi == 588)for(uii in 1:30){
            tff<-"/tmp/del4-YangLiao-DEBUG-1-30-E_GTE.txt"
            if(file.exists(tff))file.remove(tff)
            sink(tff)
            for(i in 1:30){
                cat(sprintf("%.5g %.5g %.5g\n", N10000.LLH[i], log_E_GTE[ UMI_step_indices_N10000 [i]],  log((curi) / UMI_step_values_N10000[i])))
            }
            sink()
        }
      }
    }
  
    ret.nUMI.LLH.tab[[ bcsize_one ]] <- N10000.LLH
    N1000.FstLLH <- c(  N1000.FstLLH, N10000.LLH[1] )
    Old_bs_one <- bcsize_one
  }
  if(.do.EXACT.DEBUG){
    tff<-"/tmp/del4-YangLiao-10K-LLH-from-R.txt"
    if(file.exists(tff))file.remove(tff)
    sink(tff)
    for(flh in N1000.FstLLH){
      cat(sprintf("%.6g\n", flh))
    }
    sink()
  }
  ret.nUMI.LLH.tab
}

.is.no.candBC <- function(fn){
  info <- file.info(fn)
  return(info$size < 2)
}

.cellCounts.rescue <- function( BAM.name, FC.gene.ids, sample.no ){
  fname <- sprintf("%s.scRNA.%03d", BAM.name, sample.no)
  nozero.anywhere.genes <- c()
  if(file.size(paste0(fname,".no0Genes"))>0) nozero.anywhere.genes <- read.delim(
     paste0(fname,".no0Genes"), stringsAsFactors=F, header=F)$V1 else return(NA)

  ambient.accumulate <- read.delim(paste0(fname,".AmbSum"), stringsAsFactors=F)
  ambient.accumulate <- ambient.accumulate[ match(FC.gene.ids , ambient.accumulate$GeneID), ]
  ambient.accumulate$UMIs[is.na(ambient.accumulate$UMIs)] <- 0
  ambient.accumulate <- ambient.accumulate$UMIs
  names(ambient.accumulate) <- FC.gene.ids 

  ambient.accumulate <- ambient.accumulate[ names(ambient.accumulate) %in%  nozero.anywhere.genes]

  resc.bc.fn <- paste0(fname,".RescCand")
  #print(summary(ambient.accumulate))
  rstfq <- .simple.Good.Turing.Freq(ambient.accumulate)
  if(any(is.na(rstfq)) || .is.no.candBC(paste0(resc.bc.fn,".BCtab"))){
    return(NA)
  }else{
    gte <- rstfq$p
    # This function returns "times" log-likelihoods.
  
    rescue.candidates <- as.matrix(.read.sparse.mat(resc.bc.fn))
    rescue.candidates <- rescue.candidates[ match(names(ambient.accumulate), rownames(rescue.candidates)), ]
    rownames(rescue.candidates) <- names(ambient.accumulate)
    rescue.candidates [is.na(rescue.candidates )] <- 0
    # Compute observed log-likelihood of barcodes being generated from ambient RNA
    # Compute the multinomial log PMF for many barcodes -- log-likelihoods
    # Only use the "non-zero anywhere" genes.
    log.like.cands <- apply(rescue.candidates, 2, function(x) dmultinom(x, prob=gte, log =T ))
  
    # Simulate log likelihoods
    # This step is to build like 10000 sets of N_GENES vectors from the multinomial distribution of "gte"
    # See what are their log-likelihoods against "gte" -- most should be very small.
  
    simu.pvalues <- .simu.multinomial( rescue.candidates, gte )
  
    # Compute p-values : the p-value is the chance of a barcode is actually from ambient RNA (ie smaller the p-value, more likely this barcode is for a real cell)
    actual.pvalues <- rep(0, ncol(rescue.candidates) )
    names(actual.pvalues) <- colnames(rescue.candidates)
    for(candi in names(log.like.cands)){
      cand_umis <- sum(rescue.candidates[,candi])
      cand.simu.pvs <- simu.pvalues[[ cand_umis ]]
      cand.actual.pv <- log.like.cands[candi]
      actual.pvalues[candi] <- (1+sum(cand.simu.pvs < cand.actual.pv))/(length(cand.simu.pvs)+1)
    }
  
    # p-value => FDR
    actual.FDR <- p.adjust(actual.pvalues, method='BH')
  
    if(.do.EXACT.DEBUG){
      tff<-"/tmp/del4-YangLiao-final-FDR.txt"
      if(file.exists(tff))file.remove(tff)
      sink(tff)
      for(candi in names(log.like.cands)) cat(sprintf("%s %.10g %.10g\n", candi, actual.pvalues[candi], actual.FDR[candi]))
      sink()
    }
    # select cells that has FDR < cutoff
    FDR.Cutoff <- 0.01
    head(actual.FDR)
    Rescured.Barcodes <- names(actual.FDR)[actual.FDR <= FDR.Cutoff]
    head(Rescured.Barcodes)
    rescue.candidates[,Rescured.Barcodes, drop=FALSE]
  }
}

.match.exons <- function(annot.tab, counts){
  annot.str <- paste(annot.tab$GeneID, annot.tab$Chr, annot.tab$Start,
    annot.tab$End, ifelse(annot.tab$Strand == "+", "P","N"), sep=":fc@R@Spl:")
  ret <- matrix(0, ncol=ncol(counts), nrow=nrow(annot.tab))
  colnames(ret) <- colnames(counts)
  rownames(ret) <- annot.str
  ret[ rownames(counts),] <- counts
  ret
}

.load.one.scSample <- function( BAM.name, FC.gene.ids, sample.no, use.meta.features, annot.tab, umi.cutoff){
  set.seed(0)
  fname <- sprintf("%s.scRNA.%03d", BAM.name, sample.no)
  cat("Perform cell rescuing for sample",sample.no,"...\n")
  highconf <- as.matrix(.read.sparse.mat(paste0(fname,".HighConf")))
  raw.fname <- paste0(fname,".RawOut.spmtx")
  if(file.exists(raw.fname)){
    rawout <- .read.sparse.mat.by.genes(paste0(fname,".RawOut"), FC.gene.ids)
  }else rawout <- NULL
  rescued <- NA
  if(is.null(umi.cutoff)) rescued <- .cellCounts.rescue(BAM.name, FC.gene.ids, sample.no)

  if(use.meta.features){
    if(!any(is.na(rescued))) rescued <- rescued[rowSums(rescued)>0,]
    ncolRescued <- 0
    if(any(is.na(rescued))){
      ret <- matrix(0,ncol=ncol(highconf), nrow=length(FC.gene.ids))
      colnames(ret) <- colnames(highconf)
    }else{
      ret <- matrix(0,ncol=ncol(highconf)+ncol(rescued), nrow=length(FC.gene.ids))
      colnames(ret) <- c( colnames(highconf), colnames(rescued) )
    }
    rownames(ret) <- FC.gene.ids 
    ret[rownames(highconf), colnames(highconf) ] <- highconf
    if(!any(is.na(rescued)))ret[rownames(rescued), colnames(rescued) ] <- rescued

    retc<- list(Counts=ret, HighConfidneceCell=colnames(ret) %in% colnames(highconf))
    if(!is.null(rawout)) retc[["ExcludedCells"]] <- rawout[,!( colnames(rawout) %in% colnames(ret) )]
  }else{
    fcmat <- .match.exons(annot.tab,highconf)
    rownames(fcmat)<-NULL
    retc<- list(Counts=fcmat, HighConfidneceCell=rep(T, ncol(highconf)))
    if(!is.null(rawout))retc[["ExcludedCells"]] <- rawout[,!( colnames(rawout) %in% colnames(fcmat) )]
  }
  return(retc)
}

.load.all.scSamples <- function( BAM.name, FC.gene.ids, use.meta.features, annot.tab, umi.cutoff){
  sum.tab <- read.delim(paste0(BAM.name,".scRNA.SampleTable"), stringsAsFactors=F)
  ret <- list()
  for(roiw in 1:nrow(sum.tab)){
    #sname <- as.character(sum.tab$SampleName[roiw])
    sid <- sum.tab$Index[roiw]
    count.tab <- .load.one.scSample(BAM.name, FC.gene.ids, sid, use.meta.features, annot.tab, umi.cutoff)
    ret[[sprintf("Sample.%d",sid)]] <- count.tab
  }
  ret[["Sample.Table"]] <- sum.tab

  ret
}

.del.temp.files  <- function(pfx){
  if(nchar(pfx)<16) stop("Prefix should be longer.")
  flist <- dir(".", "^[.]Rsubread", all.files=TRUE)
  to.delete <- flist[grepl(pfx, flist)]
  for(df in to.delete){
    if(grepl(pfx, df)){
      file.remove(df)
#cat ("Goinng to remove",df,"\n")
    }else{
      stop("Wrong selection of files to delete")
    }
  }
}

.extract.sample.table.cols <- function(rdir, smr, input.mode="bcl", umi.cutoff=NULL){
  total.cells <- c()
  hiconf.cells <- c()
  res.cells <- c()
  umis <- c()
  umi.statistics <- data.frame(MinUMI=NULL, MedianUMI=NULL, MaxUMI=NULL, MeanUMI=NULL,stringsAsFactors=F)[,0]
  for(spi in 1:nrow(smr[["Sample.Table"]])){
    samplename <- as.character(smr[["Sample.Table"]]$SampleName[spi])
    sampleno <- sprintf("Sample.%d", spi)
    total.cells <- c(total.cells,length(smr[[sampleno]][["HighConfidneceCell"]]))
    hiconf.cells <- c(hiconf.cells,sum(smr[[sampleno]][["HighConfidneceCell"]]))
    res.cells <- c(res.cells, sum(!(smr[[sampleno]][["HighConfidneceCell"]])))
    cell.umis <- colSums(smr[[sampleno]][["Counts"]])
    umis <- c(umis,sum(cell.umis))
    if(length(cell.umis)==0){
       umi.statistics <- rbind(umi.statistics, list(MinUMI=NA, MedianUMI=NA, MaxUMI=NA, MeanUMI=NA))
    }else umi.statistics <- rbind(umi.statistics, list(MinUMI=min(cell.umis), MedianUMI=median(cell.umis), MaxUMI=max(cell.umis), MeanUMI=mean(cell.umis)))
  }
  ret <- NULL
  if(is.null(umi.cutoff)){
    if(input.mode=="fastq" || input.mode=="bam")
      ret <- cbind( SampleName=as.character(smr[["Sample.Table"]]$SampleName), TotalCells=total.cells, HighConfidenceCells=hiconf.cells, RescuedCells=res.cells, TotalUMI=umis, umi.statistics, smr[["Sample.Table"]][,c("TotalReads","MappedReads","AssignedReads")],stringsAsFactors=F )
    else
      ret <- cbind( SampleName=as.character(smr[["Sample.Table"]]$SampleName), InputDirectory=rep(rdir, nrow(smr[["Sample.Table"]])), TotalCells=total.cells, HighConfidenceCells=hiconf.cells, RescuedCells=res.cells, TotalUMI=umis, umi.statistics, smr[["Sample.Table"]][,c("TotalReads","MappedReads","AssignedReads")],stringsAsFactors=F )
  }else{
    if(input.mode=="fastq" || input.mode=="bam")
      ret <- cbind( SampleName=as.character(smr[["Sample.Table"]]$SampleName), TotalCells=total.cells, TotalUMI=umis, umi.statistics, smr[["Sample.Table"]][,c("TotalReads","MappedReads","AssignedReads")], stringsAsFactors=F)
    else
      ret <- cbind( SampleName=as.character(smr[["Sample.Table"]]$SampleName), InputDirectory=rep(rdir, nrow(smr[["Sample.Table"]])), TotalCells=total.cells, TotalUMI=umis, umi.statistics, smr[["Sample.Table"]][,c("TotalReads","MappedReads","AssignedReads")], stringsAsFactors=F)
  }
}

.scan.fastq.dir <- function(dirname){
  ddf <- list.files(dirname , recursive=TRUE)
  ddf <- ddf[grepl("L[0-9][0-9][1-9]_R1_[0-9][0-9][1-9].fastq.gz$",ddf) &(!grepl("Undetermined_S",ddf)) &!grepl("/fork0/", ddf)]
  sample.sheet <- data.frame(stringsAsFactors=F)
  for(df1 in sort(ddf)){
    df1 <- file.path(dirname,df1)
    r1_part <- substr(df1, nchar(df1)-12, 9999)
    r1_pref <- substr(df1, 1, nchar(df1)-15)
    df2 <- paste0(r1_pref, "R2", r1_part)
    sample.name <- unlist(strsplit(df2, "\\/"))
    sample.name0<- sample.name
    sample.name <- sample.name[length(sample.name)]
    if(substr(sample.name, nchar(sample.name)-22, nchar(sample.name)-22)=='S') sample.name <- substr(sample.name, 1, nchar(sample.name)-24)
    else if(substr(sample.name, nchar(sample.name)-23, nchar(sample.name)-23)=='S') sample.name <- substr(sample.name, 1, nchar(sample.name)-25)
    else stop(paste("ERROR: Unable to parse the file name,",sample.name))

    if(substr(sample.name,1,1)=="_") sample.name <- substr(sample.name,2,999)
    if(substr(sample.name,1,1)=="_") sample.name <- substr(sample.name,2,999)
    #if(substr(sample.name,1,1)!="S")stop(sprintf("Unable to parse the FASTQ file name : '%s'", sample.name0))

    sample.sheet <- rbind(sample.sheet, data.frame( SampleName=as.character(sample.name), BarcodeUMIFile=df1, ReadFile=df2, stringsAsFactors=F))
  }
  if(nrow(sample.sheet) < 1) stop(paste0("ERROR: no valid FASTQ files were found in directory '",dirname,"'."))
  sample.sheet
}

.SCRNA_FASTA_SPLIT1 <- "|Rsd:cCounts:mFQs|"
.SCRNA_FASTA_SPLIT2 <- "|Rsd:cCounts:1mFQ|"

.validate.sample.sheet <- function(sheet, input.mode){
  if(any(is.na(sheet)) || is.null(sheet)) stop("A sample sheet must be provided.")
  if(input.mode == "BCL"){
    if(nrow(sheet)<1) stop("The sample sheet cannot be empty.")
    if(!("Lane" %in% colnames(sheet) && "IndexSetName" %in% colnames(sheet) && "SampleName" %in% colnames(sheet) && "InputDirectory" %in% colnames(sheet)))
      stop("The sample sheet must contain four columns with column headers named InputDirectory, Lane, SampleName and IndexSetName.")
    files.to.test <- sheet[, "InputDirectory"]
    for(f in files.to.test)
      if(!dir.exists(f))stop(paste0("ERROR: input directory '",f,"' does not exist."))
  }else if(input.mode == "FASTQ"){
    if(nrow(sheet)<1) stop("The sample sheet cannot be empty")
    if(!("BarcodeUMIFile"  %in% colnames(sheet) && "ReadFile"  %in% colnames(sheet) && "SampleName"  %in% colnames(sheet) ))
      stop("The sample sheet must contain three columns with column headers named BarcodeUMIFile, ReadFile and SampleName.")
    files.to.test <-  c(sheet[,"BarcodeUMIFile"], sheet[,"ReadFile"])
    for(f in files.to.test)
      if(!file.exists(f))stop(paste0("ERROR: fastq file '",f,"' does not exist."))
  } # The "FASTQ-dir" mode should always be correct unless no FASTQ files are found. The error message is given in the dir-scan function.
  return(sheet)
}

cellCounts <- function( index, sample, input.mode = "BCL", cell.barcode = NULL, nsubreads = 15, minVotes = 1, maxMismatches = 10, minMappedLength = 1, annot.inbuilt = "mm39", annot.ext = NULL, isGTFAnnotationFile = FALSE, GTF.featureType = "exon", GTF.attrType = "gene_id", useMetaFeatures = TRUE, detectJunctions = FALSE, umi.cutoff = NULL, nthreads = 10, nBestLocations = 1, uniqueMapping = FALSE, reportExcludedBarcodes = FALSE, barcoding.mode="Chromium", cell.level.junctions=NULL, cell.cluster.map=NULL, enableSoftClipping = TRUE){
  if(F)if(!(grepl("[/]chr1$", index)||file.exists("~/PubDB/welcome.bash"))){
     stop("The devel version is not for general use. Please install the released version.")
     return(NULL)
  }
  maxDiffToTopVotes=2
  onlyDetectBarcode=F
  has.error <- FALSE
  maxMismatchBases <- maxMismatches
  minVotesPerRead <- minVotes
  subreadsPerRead <- nsubreads
  unique.mapping <- uniqueMapping

  index <- .check_and_NormPath(index, mustWork=F, opt="index name")
  index.file.1 <- paste0(index, ".00.b.array")
  if(!file.exists(index.file.1))stop(sprintf("Error: index '%s' is not found.", index))
  if(!is.null(umi.cutoff)){
    umi.cutoff <- as.numeric(umi.cutoff)
    if(umi.cutoff < 0.0) stop("UMI cutoff must be a positive number.")
  }

  sample.info.idx <- .validate.sample.sheet(sample, input.mode)
  fc <- list()

  temp.file.prefix <- file.path(".",paste(".Rsubread_cCounts_Tmp_for_Pid_",Sys.getpid(),"_Rproc",sep=""))
  raw.fc.annot <- NA
  df.sample.info <- data.frame(stringsAsFactors=F)
  cc.sample.sheet.path <- paste0(temp.file.prefix,".samplesheet")

  annlist <- .get.annotation.file(annot.inbuilt, annot.ext, isGTFAnnotationFile)
  ann <- annlist$ann
  annot.screen.output <- annlist$screen
  delete.annot.file <- annlist$delete
  readAssignmentFile <- NULL
 
  if(input.mode=="FASTQ" || input.mode == "FASTQ-dir"){
    if(input.mode == "FASTQ-dir") sample.info.idx <- .scan.fastq.dir(sample)
    #print(sample.info.idx)
    if(!("BarcodeUMIFile" %in%  colnames(sample.info.idx) && "ReadFile" %in%  colnames(sample.info.idx) )) stop("You need to provide BC+UMI and Genomic sequence files")
    combined.fastq.names <- ""
    for(rowi in 1:nrow(sample.info.idx)){
      df1 <- as.character(sample.info.idx[rowi,"BarcodeUMIFile"])
      df2 <- as.character(sample.info.idx[rowi,"ReadFile"])
      R1.file.name <- .check_and_NormPath(df1, mustWork=TRUE, "The barcode FASTQ file in sample.info.idx")
      R2.file.name <- .check_and_NormPath(df2, mustWork=TRUE, "The genomic read FASTQ file in sample.info.idx")
      combined.fastq.names <- paste0( combined.fastq.names,.SCRNA_FASTA_SPLIT1, R1.file.name, .SCRNA_FASTA_SPLIT2,".",.SCRNA_FASTA_SPLIT2, R2.file.name)
    }

    combined.fastq.names <- substr(combined.fastq.names, nchar(.SCRNA_FASTA_SPLIT1)+1, 9999999)
    if(is.null(cell.barcode)){
      guess.cellbc.and.idx <- .find_best_CellBC_and_DualIdx(combined.fastq.names, temp.file.prefix, "N/A", input.mode="fastq", cell.bc.sup.rate=ifelse(is.null(cell.barcode),.6,-1))
      cell.barcode <- guess.cellbc.and.idx$cell.barcode
    }else{
      cell.barcode <- .check_and_NormPath(cell.barcode, mustWork=T, opt="cell.barcode")
    }

    if(onlyDetectBarcode){
      cat("Barcode is",cell.barcode,"\n")
      return(NA)
    }

     .index.names.to.sheet.FASTQ.mode(sample.info.idx, cc.sample.sheet.path)
    opt <- c("--inputMode","FASTQ","--cellBarcodeFile", cell.barcode,"--reportExcludedBarcodes",as.numeric(reportExcludedBarcodes),"--dataset", combined.fastq.names, "--sampleSheetFile", cc.sample.sheet.path, "--index", index, "--annotation", ann, "--geneIdColumn", GTF.attrType, "--annotationType", GTF.featureType, "--threads", nthreads, "--output", temp.file.prefix, "--maxMismatch", maxMismatchBases, "--minVotesPerRead", minVotesPerRead, "--subreadsPerRead", subreadsPerRead, "--reportedAlignmentsPerRead", nBestLocations, "--maxDiffToTopVotes", maxDiffToTopVotes, "--minMappedLength", minMappedLength, "--umiCutoff",  ifelse(is.null(umi.cutoff), -999, umi.cutoff))
    if(detectJunctions)opt <- c(opt, "--junctionDetection")
    if(enableSoftClipping) opt <- c(opt, "--enableSoftClipping")
    if(isGTFAnnotationFile)opt <- c(opt, "--isGTFannotation")
    if(!unique.mapping)opt <- c(opt, "--reportMultiMappingReads")
    if(barcoding.mode=="VisiumHD") opt <- c(opt, "--VisiumHD_barcode")
    if(!is.null(cell.level.junctions)) opt <- c(opt, "--cluster_junctions", cell.level.junctions)
    if(!is.null(cell.cluster.map)) opt <- c(opt, "--cluster_map", cell.cluster.map)
    env.readAssignmentFile <- Sys.getenv("CELLCOUNTS_DETAIL_OUT_FILENAME")
    if(!identical(env.readAssignmentFile, "")) opt <- c(opt, "--readAssignmentFile", env.readAssignmentFile)
 

    cmd <- paste(opt,collapse=.R_param_splitor)
    n <- length(unlist(strsplit(cmd,.R_param_splitor)))
    C_args <- .C("R_cellCounts",as.integer(n),as.character(cmd),PACKAGE="Rsubread")
 
    annot.file <- paste0(temp.file.prefix,".Annot")
    if(file.exists(annot.file)){
      bam.for.FC <- c()
      raw.fc.annot<-read.delim(annot.file, header=T, stringsAsFactors=F)
      some.results <- .load.all.scSamples(temp.file.prefix, as.character(raw.fc.annot$GeneID), useMetaFeatures, raw.fc.annot, umi.cutoff)

      fc[["counts"]] <- list()
      if(reportExcludedBarcodes)fc[["counts.excluded.barcodes"]] <- list()
      for(spi in 1:nrow(some.results[["Sample.Table"]])){
        samplename <- as.character(some.results[["Sample.Table"]][["SampleName"]][spi])
        fc[["counts"]][[samplename]] <- some.results[[sprintf("Sample.%d", spi)]][["Counts"]] # only one sample.
        if(reportExcludedBarcodes)fc[["counts.excluded.barcodes"]][[samplename]] <- some.results[[sprintf("Sample.%d", spi)]][["ExcludedCells"]] # only one sample.
        if(is.null(umi.cutoff))fc[["cell.confidence"]][[samplename]] <- some.results[[sprintf("Sample.%d", spi)]][["HighConfidneceCell"]]
      }
      df.sample.info <- .extract.sample.table.cols(NA,some.results,input.mode="fastq", umi.cutoff=umi.cutoff)
    } else has.error <- T
  }else if(input.mode=="BCL"){
    sample.info.idx$SampleName <- as.character(sample.info.idx$SampleName)
    if('IndexSetName' %in% colnames(sample.info.idx))sample.info.idx$IndexSetName <- as.character(sample.info.idx$IndexSetName)

    dirs <- unique( sample.info.idx$InputDirectory )
    fc[["counts"]] <- list()
    if(reportExcludedBarcodes)fc[["counts.excluded.barcodes"]] <- list()
  
    for(dirname in dirs) .check_and_NormPath(dirname, mustWork=TRUE, "InputDirectory in sample.info.idx") # check files before the slow mapping/counting step.

    dirno <- 1
    for(dirname in dirs){
      if(has.error) break
      unique.samples <- unique( as.character(sample.info.idx$SampleName[ sample.info.idx$InputDirectory == dirname ] ))
  
      dual.index.ABlist <- .index.names.to.sheet.raw.dir.mode(dirname, sample.info.idx, cc.sample.sheet.path)
      guess.cellbc.and.idx <- .find_best_CellBC_and_DualIdx(dirname, temp.file.prefix, cc.sample.sheet.path, A_and_B.dual.index.list=dual.index.ABlist, cell.bc.sup.rate=ifelse(is.null(cell.barcode),.6,-1))
      if(is.null(cell.barcode)){
        cell.barcode <- guess.cellbc.and.idx$cell.barcode
      }else{
        cell.barcode <- .check_and_NormPath(cell.barcode, mustWork=T, opt="cell.barcode")
      }
      
      if(onlyDetectBarcode){
        cat("Barcode is",cell.barcode,"\n")
        return(NA)
      }
  
      full_dirname <- .check_and_NormPath(dirname, mustWork=TRUE, "InputDirectory in sample.info.idx")
      is_dual_index <- .index.names.to.sheet.raw.dir.mode(dirname, sample.info.idx, cc.sample.sheet.path, which.dual.index=guess.cellbc.and.idx$dual.index)
      is_dual_index <- (F==any(is.null(is_dual_index)))
      generate.scRNA.BAM <- TRUE

      opt <- c("--cellBarcodeFile", cell.barcode,"--reportExcludedBarcodes",as.numeric(reportExcludedBarcodes),"--dataset", dirname, "--sampleSheetFile", cc.sample.sheet.path, "--index", index, "--annotation", ann, "--geneIdColumn", GTF.attrType, "--annotationType", GTF.featureType, "--threads", nthreads, "--output", temp.file.prefix, "--maxMismatch", maxMismatchBases, "--minVotesPerRead", minVotesPerRead, "--subreadsPerRead", subreadsPerRead, "--maxDiffToTopVotes",maxDiffToTopVotes, "--minMappedLength", minMappedLength, "--umiCutoff", ifelse(is.null(umi.cutoff), -999, umi.cutoff))
      if(isGTFAnnotationFile)opt <- c(opt, "--isGTFannotation")
      if(!unique.mapping)opt <- c(opt, "--reportMultiMappingReads")
      if(detectJunctions)opt <- c(opt, "--junctionDetection")
      if(enableSoftClipping) opt <- c(opt, "--enableSoftClipping")
      if(barcoding.mode=="VisiumHD") opt <- c(opt, "--VisiumHD_barcode")
      if(!is.null(cell.level.junctions)) opt <- c(opt, "--cluster_junctions", cell.level.junctions)
      if(!is.null(cell.cluster.map)) opt <- c(opt, "--cluster_map", cell.cluster.map)

      cmd <- paste(opt,collapse=.R_param_splitor)
      n <- length(unlist(strsplit(cmd,.R_param_splitor)))
      #print(cmd)
      C_args <- .C("R_cellCounts",as.integer(n),as.character(cmd),PACKAGE="Rsubread")

      annot.file <- paste0(temp.file.prefix,".Annot")
      dirno <- dirno +1
      if(file.exists(annot.file)){
        if(any(is.na(raw.fc.annot))) raw.fc.annot<-read.delim(annot.file, header=T, stringsAsFactors=F)
        some.results <- .load.all.scSamples(temp.file.prefix, as.character(raw.fc.annot$GeneID), useMetaFeatures, raw.fc.annot, umi.cutoff)
        for(spi in 1:nrow(some.results[["Sample.Table"]])){
          samplename <- as.character(some.results[["Sample.Table"]][["SampleName"]][spi])
          fc[["counts"]][[samplename]] <- some.results[[sprintf("Sample.%d", spi)]][["Counts"]] # only one sample.
          if(reportExcludedBarcodes)fc[["counts.excluded.barcodes"]][[samplename]] <- some.results[[sprintf("Sample.%d", spi)]][["ExcludedCells"]] # only one sample.
          if(is.null(umi.cutoff))fc[["cell.confidence"]][[samplename]] <- some.results[[sprintf("Sample.%d", spi)]][["HighConfidneceCell"]]
        }
        stt <- .extract.sample.table.cols(full_dirname,some.results, umi.cutoff=umi.cutoff)
        df.sample.info <- rbind(df.sample.info, stt)
      } else has.error <-T
    }
  } else if(input.mode == "BAM"){
    unique.samples <- unique(as.character(sample.info.idx$SampleName))
    if(is.null(cell.barcode)){
      guess.cellbc.and.idx <- .find_best_CellBC_and_DualIdx(sample$BAMFile, temp.file.prefix, "N/A", input.mode="bam", cell.bc.sup.rate=ifelse(is.null(cell.barcode),.6,-1))
      cell.barcode <- guess.cellbc.and.idx$cell.barcode
    }else{
      cell.barcode <- .check_and_NormPath(cell.barcode, mustWork=T, opt="cell.barcode")
    }

    unique.samples <- unique(as.character(sample.info.idx$SampleName))
    for(this.sample in unique.samples){
      if(has.error) break
      .index.names.to.sheet.BAM.mode(data.frame(BAMFile="COMBINED.INPUT",SampleName=as.character(this.sample),stringsAsFactors=F), cc.sample.sheet.path)
      BAM.names <- paste(sample.info.idx$BAMFile[ as.character(sample.info.idx$SampleName) == this.sample ], collapse=.SCRNA_FASTA_SPLIT1)
      generate.scRNA.BAM <- TRUE

      opt <- c("--inputMode","BAM","--cellBarcodeFile", cell.barcode, "--reportExcludedBarcodes",as.numeric(reportExcludedBarcodes),"--dataset", BAM.names, "--sampleSheetFile", cc.sample.sheet.path, "--index", index, "--annotation", ann, "--geneIdColumn", GTF.attrType, "--annotationType", GTF.featureType, "--threads", nthreads, "--output", temp.file.prefix, "--maxMismatch", maxMismatchBases, "--minVotesPerRead", minVotesPerRead, "--subreadsPerRead", subreadsPerRead, "--maxDiffToTopVotes",maxDiffToTopVotes, "--minMappedLength", minMappedLength, "--umiCutoff", ifelse(is.null(umi.cutoff), -999, umi.cutoff))
      if(isGTFAnnotationFile)opt <- c(opt, "--isGTFannotation")
      if(!unique.mapping)opt <- c(opt, "--reportMultiMappingReads")
      if(detectJunctions)opt <- c(opt, "--junctionDetection")
      if(enableSoftClipping) opt <- c(opt, "--enableSoftClipping")
      if(barcoding.mode=="VisiumHD")  c(opt, "--VisiumHD_barcode")

      cmd <- paste(opt,collapse=.R_param_splitor)
      n <- length(unlist(strsplit(cmd,.R_param_splitor)))
      #print(cmd)
      C_args <- .C("R_cellCounts",as.integer(n),as.character(cmd),PACKAGE="Rsubread")

      annot.file <- paste0(temp.file.prefix,".Annot")
      if(file.exists(annot.file)){
        fc[["counts"]] <- list()
        if(reportExcludedBarcodes)fc[["counts.excluded.barcodes"]] <- list()

        raw.fc.annot<-read.delim(annot.file, header=T, stringsAsFactors=F)
        some.results <- .load.all.scSamples(temp.file.prefix, as.character(raw.fc.annot$GeneID), useMetaFeatures, raw.fc.annot, umi.cutoff)
        for(spi in 1:nrow(some.results[["Sample.Table"]])){
          samplename <- as.character(some.results[["Sample.Table"]][["SampleName"]][spi])
          fc[["counts"]][[samplename]] <- some.results[[sprintf("Sample.%d", spi)]][["Counts"]] # only one sample.
          if(reportExcludedBarcodes)fc[["counts.excluded.barcodes"]][[samplename]] <- some.results[[sprintf("Sample.%d", spi)]][["ExcludedCells"]] # only one sample.
          if(is.null(umi.cutoff))fc[["cell.confidence"]][[samplename]] <- some.results[[sprintf("Sample.%d", spi)]][["HighConfidneceCell"]]
        }
        df.sample.info <- rbind(df.sample.info,.extract.sample.table.cols(NA,some.results, input.mode="bam", umi.cutoff=umi.cutoff))
      }else has.error<-T
    }
  }
  if(detectJunctions){
    fc[["junction.counts"]] <- read.delim(paste0(temp.file.prefix,".jcounts"), sep="\t")
    ncoln <- ncol(fc[["junction.counts"]]) - length(some.results[["Sample.Table"]][["SampleName"]])*2 +1
    for(spi in some.results[["Sample.Table"]][["SampleName"]]){
        colnames(fc[["junction.counts"]])[ncoln] <- paste0("Supporting_Reads_", spi)
        colnames(fc[["junction.counts"]])[ncoln +1] <- paste0("Nonsupporting_Reads_", spi)
        ncoln <- ncoln+2
    }
  }
  if(F&&grepl("scRNA-sample-human-COPD-non_small.BAMs",getwd())) warning("NOT DELETING TEMP FILES !!!!") else  .del.temp.files(substr(temp.file.prefix,4,99)) 

  fc[["annotation"]] <- raw.fc.annot
  fc[["sample.info"]] <- df.sample.info
  if(has.error) { 
    cat("No results were generated.\n\n")
    return(NULL)
  } else {
    cat("\nThe cellCounts program has finished successfully.\n\n")
    return(fc)
  }
}

