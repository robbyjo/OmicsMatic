# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
args <- commandArgs(TRUE);out <- args[1];stopifnot(!dir.exists(out),dir.create(out,recursive=TRUE))
format <- function(x)formatC(x,digits=17,format='g')
write <- function(d,name)write.table(d,file.path(out,name),sep='\t',quote=TRUE,row.names=FALSE,qmethod='double')
set.seed(923);cases <- list(list(x=c(0,0,1),y=c(0,2,2,4)),list(x=c(-0,0,0),y=rep(0,4)),list(x=c(1e-300,2e-300,3e-300),y=c(1e300,2e300,3e300)),list(x=c(1,2,3),y=c(4,5,6,7)))
for(i in 1:96)cases[[length(cases)+1L]] <- list(x=sample(0:19,sample(2:15,1),replace=TRUE)/4,y=sample(0:19,sample(2:17,1),replace=TRUE)/4)
rows <- lapply(seq_along(cases),function(i){x <- cases[[i]]$x;y <- cases[[i]]$y;fit <- suppressWarnings(wilcox.test(x,y,exact=NULL));ties <- table(c(x,y));data.frame(id=paste0('rank-',i),control=paste(format(x),collapse=','),treated=paste(format(y),collapse=','),statistic=format(unname(fit$statistic)),tied_blocks=sum(ties>1),tied_observations=sum(ties[ties>1]))})
write(do.call(rbind,rows),'ranks.tsv')
d <- data.frame(id=paste0('contrast-',1:10),control=c(0,0,0,0.25,0.25,0.25,1,2,3,3),treated=c(0.25,0.25+.Machine$double.eps,0.25-.Machine$double.eps,0,0.25,0.5,2,1,3,4),threshold=c(rep(0.25,6),0,0,0,1))
delta <- d$treated-d$control;d$positive <- delta>d$threshold;d$negative <- delta< -d$threshold;d$absolute <- abs(delta)>d$threshold
for(column in c('control','treated','threshold'))d[[column]] <- format(d[[column]])
write(d,'contrasts.tsv');jsonlite::write_json(list(status='PASS_ORIGINAL_R_PREPARED_STRENGTH_REFERENCES',ranks=length(cases),contrasts=nrow(d),pvalues_claimed=FALSE),file.path(out,'COMPLETED.json'),auto_unbox=TRUE,pretty=TRUE)
