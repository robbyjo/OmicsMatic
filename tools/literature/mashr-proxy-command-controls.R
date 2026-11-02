# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
# Calls the reviewed original standalone function without changing its source.
args <- commandArgs(trailingOnly=TRUE)
stopifnot(length(args)==3L)
source(args[1], local=TRUE)
bundle <- args[2]
out <- args[3]
stopifnot(!dir.exists(out))
dir.create(out)
conditions <- readLines(file.path(bundle, 'native/execution/conditions.tsv'), encoding='UTF-8')
features <- readLines(file.path(bundle, 'native/execution/target-ids.tsv'), encoding='UTF-8')
stopifnot(length(features)>0L, length(conditions)>0L)
lfsr <- as.matrix(read.table(file.path(bundle,'native/execution/posterior/lfsr.tsv'), header=FALSE, check.names=FALSE))
stopifnot(identical(dim(lfsr), c(length(features),length(conditions))), all(is.finite(lfsr)), all(lfsr>=0 & lfsr<=1))
colnames(lfsr) <- conditions
read_chars <- function(name) read.delim(file.path(bundle,'request',name), colClasses='character', check.names=FALSE, na.strings=NULL, quote='', comment.char='')
patterns <- read_chars('patterns.tsv')
selectors <- read_chars('selectors.tsv')
settings <- read_chars('settings.tsv')
threshold <- as.numeric(settings$value[settings$key=='score_threshold'])
boundary <- settings$value[settings$key=='score_boundary']
stopifnot(length(threshold)==1L, is.finite(threshold), threshold>=0, threshold<=1, length(boundary)==1L, boundary %in% c('strict','inclusive'))
scores <- matrix(NA_real_, nrow=length(features), ncol=nrow(patterns))
for (k in seq_len(nrow(patterns))) {
    s <- selectors[selectors$pattern==patterns$pattern[k],,drop=FALSE]
    include <- s$condition[s$role=='include']
    exclude <- s$condition[s$role=='exclude']
    test <- if(patterns$mode[k]=='all') 'all' else 'at least 1'
    # Public matrix path and internal helper must agree exactly.
    a <- compositePosteriorTest(1-lfsr, include=include, exclude=exclude, test=test)
    b <- .compositePosteriorTest(1-lfsr, include=include, exclude=exclude, test=test)
    stopifnot(identical(a,b), length(a)==length(features))
    scores[,k] <- a
}
recorded <- read.delim(file.path(bundle,'results/independent-marginal-proxies.tsv'), colClasses='character', check.names=FALSE, na.strings=NULL, quote='', comment.char='')
stopifnot(nrow(recorded)==length(features)*nrow(patterns))
control <- vector('list', nrow(recorded))
for (r in seq_along(features)) for(k in seq_len(nrow(patterns))) {
    i <- (r-1L)*nrow(patterns)+k
    original <- scores[r,k]
    native <- as.numeric(recorded$source_score[i])
    decision <- if(boundary=='strict') original>threshold else original>=threshold
    stopifnot(recorded$feature[i]==features[r], recorded$pattern[i]==patterns$pattern[k], is.finite(native),
              abs(original-native)<=1e-15+2e-15*abs(original),
              recorded$passes_threshold[i]==tolower(as.character(decision)))
    control[[i]] <- data.frame(feature=features[r], pattern=patterns$pattern[k], original_score=original, native_score=native,
                              difference=native-original, original_passes=decision, native_passes=recorded$passes_threshold[i], stringsAsFactors=FALSE)
}
options(digits=17)
write.table(do.call(rbind,control),file.path(out,'original-vs-native.tsv'),sep='\t',quote=FALSE,row.names=FALSE,na='NA')
writeLines('ORIGINAL_PROXY_COMMAND_CONTROLS_COMPLETE',file.path(out,'STATUS.txt'))
cat('Compared ',nrow(recorded),' source scores and exact threshold decisions against unchanged original public/internal helpers\n',sep='')
