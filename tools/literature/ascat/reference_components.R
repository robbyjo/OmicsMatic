args<-commandArgs(TRUE);stopifnot(length(args)==1)
edition<-args[[1]];options(warn=1,digits=17,bitmapType='cairo');library(ASCAT)
stopifnot(as.character(packageVersion('ASCAT'))==edition)
segment<-get('make_segments',asNamespace('ASCAT'));distance<-get('create_distance_matrix',asNamespace('ASCAT'))
hex<-function(x)vapply(x,function(v)paste(sprintf('%02x',as.integer(writeBin(as.double(v),raw(),size=8,endian='big'))),collapse=''),character(1))
tab<-function(x,file){write.table(x,file,sep='\t',quote=FALSE,row.names=FALSE,col.names=TRUE,na='NA')}
cases<-list(
 basic=list(r=c(-.55,-.55,0,0,0,.321730625396636,.321730625396636),b=c(1,1,.5,.5,.5,.75,.75),gamma=.55),
 missing=list(r=c(0,NA,0,.2,NaN,.2,.2,-.1),b=c(.5,.9,.5,.6,.2,.6,NA,.4),gamma=1),
 zero=list(r=c(-0,0,-0,0,0),b=c(.5,.5,.5,0,-0),gamma=.55),
 global_allele=list(r=c(-.2,rep(.3,15),.7,.7,.7,0),b=c(.1,rep(.8,15),.6,.6,.6,.5),gamma=1),
 close_balance=list(r=c(.2,.2,.2,0,0,-.55,-.55,.32),b=c(.5,.5,.5+.Machine$double.eps,.5-.Machine$double.eps,.5,1,1,.72),gamma=.55),
 negative_copy=list(r=c(-8,-8,-2,2,2,0),b=c(.2,.2,.9,.6,.6,.5),gamma=1))
input<-output<-cells<-NULL
for(name in names(cases)) {
 c<-cases[[name]];s<-segment(c$r,c$b)
 input<-rbind(input,data.frame(case=name,ordinal=seq_along(c$r),logR_bits=hex(c$r),BAF_bits=hex(c$b)))
 output<-rbind(output,data.frame(case=name,ordinal=seq_len(nrow(s)),logR_bits=hex(s[,'r']),BAF_bits=hex(s[,'b']),length=as.integer(s[,'length'])))
 d<-distance(s,c$gamma)
 psi<-seq(1,6,.05);rho<-seq(.1,1.05,.01);stopifnot(identical(dim(d),c(length(psi),length(rho))))
 cells<-rbind(cells,data.frame(case=name,psi_bits=rep(hex(psi),each=length(rho)),rho_bits=rep(hex(rho),times=length(psi)),gamma_bits=hex(c$gamma),distance_bits=hex(as.vector(t(d)))))
 cat('ORIGINAL_COMPONENT_CASE',name,nrow(s),'segments',length(d),'complete objective cells\n')
}
tab(input,'input.tsv');tab(output,'segments.tsv');tab(cells,'distance.tsv')
refusals<-lapply(list(empty=list(r=numeric(),b=numeric()),all_missing=list(r=c(NA,NaN),b=c(.5,.6))),function(v)tryCatch({segment(v$r,v$b);list(refused=FALSE)},error=function(e)list(refused=TRUE,message=conditionMessage(e))))
stopifnot(all(vapply(refusals,function(v)v$refused,logical(1))));dput(refusals,file='REFUSALS.R')
dput(list(status='PASS_COMPLETE_ORIGINAL_NAMESPACE_COMPONENT_REFERENCES',edition=edition,cases=length(cases),input_rows=nrow(input),segments=nrow(output),cells=nrow(cells)),file='RESULT.R')
writeLines(capture.output(sessionInfo()),'SESSION.txt')
