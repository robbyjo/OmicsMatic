# Independent survival::coxph and cox.zph(transform="log") fixtures.
# Run Rscript study-survival-reference.R NEW_OUTPUT_DIRECTORY
library(survival)
args <- commandArgs(trailingOnly=TRUE)
stopifnot(length(args)==1L, !dir.exists(args[1]))
dir.create(args[1], recursive=TRUE)
i <- 0:79
age <- ((i*7) %% 31)/31
x <- sin(i*.53)
entry <- (i %% 3)*.05
event_time <- -log(((i*37) %% 101+.5)/101)/exp(.3*x+.2*age)
censor_time <- .8+((i*11) %% 19)/19
stop <- ceiling(4*(entry+pmin(event_time,censor_time)))/4
event <- as.integer(event_time<=censor_time)
base <- data.frame(row=i,start=entry,stop=stop,event=event,stratum=i%%2,age=age,x=x,offset=.1*cos(i*.31))
all_inputs <- all_results <- NULL
for (name in c("right-efron","entry-strata-efron","entry-strata-breslow")) {
    d <- base
    if (name=="right-efron") {d$start <- 0; d$stratum <- 0}
    method <- if(grepl("breslow",name)) "breslow" else "efron"
    fit <- coxph(Surv(start,stop,event)~age+x+strata(stratum)+offset(offset),d,ties=method,x=TRUE,y=TRUE,control=coxph.control(iter.max=100,eps=1e-10))
    null <- coxph(Surv(start,stop,event)~age+strata(stratum)+offset(offset),d,ties=method,control=coxph.control(iter.max=100,eps=1e-10))
    ph <- cox.zph(fit,transform="log",terms=FALSE)
    results <- data.frame(case=name,term=names(coef(fit)),beta=coef(fit),se=sqrt(diag(vcov(fit))),log_likelihood=as.numeric(logLik(fit)),null_log_likelihood=as.numeric(logLik(null)),
        ph_statistic=ph$table[1:2,"chisq"],ph_p_value=ph$table[1:2,"p"],global_ph_statistic=ph$table["GLOBAL","chisq"],global_ph_p_value=ph$table["GLOBAL","p"],
        lrt=2*(as.numeric(logLik(fit))-as.numeric(logLik(null))))
    all_inputs <- rbind(all_inputs,cbind(case=name,d));all_results <- rbind(all_results,results)
}
write.table(all_inputs,file.path(args[1],"inputs.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
write.table(all_results,file.path(args[1],"expected.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
writeLines(c(R.version.string,paste0("survival ",packageVersion("survival"))),file.path(args[1],"versions.txt"))
