from pathlib import Path
import hashlib,json,os,subprocess
R=Path('D:/projects/JLinAlg');I=R/'build/twosamplemr-author-code-intake-20261121-1';O=I/'static-call-audit-3';O.mkdir(exist_ok=False);files=[]
for paper,repo in [('P157','japan_covid_taskforce_multi_omics'),('P177','TEMR'),('P177','TEMR_Simul')]:
    root=I/paper/repo/'files'
    for p in sorted(root.rglob('*')):
        if p.suffix.lower()=='.r' and (paper=='P177' or p.name=='MR_analysis.R'):files.append(dict(paper=paper,repository=repo,path=str(p),relative=p.relative_to(root).as_posix(),sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
(O/'INPUTS.json').write_text(json.dumps(files,indent=2)+'\n',encoding='utf-8',newline='\n')
code="""args<-commandArgs(TRUE);stopifnot(length(args)==2L)
.libPaths(c('D:/projects/JLinAlg/build/twosamplemr-historical-runtime-20261121-1/library','D:/projects/JLinAlg/build/twosamplemr-runtime-intake-20261121-1/library',.Library),include.site=FALSE)
input<-jsonlite::fromJSON(args[[1]],simplifyVector=FALSE);rows<-list()
for(i in seq_along(input)) {
    item<-input[[i]];error<-NULL;ast<-tryCatch(parse(file=item$path,keep.source=TRUE,encoding='UTF-8'),error=function(e){error<<-conditionMessage(e);NULL})
    calls<-list();assignments<-character();sources<-list()
    walk<-function(e) {
        if(missing(e))return(invisible(NULL))
        if(is.expression(e)||is.pairlist(e)){for(x in e)walk(x);return(invisible(NULL))}
        if(!is.call(e))return(invisible(NULL))
        head<-e[[1]];name<-if(is.symbol(head))as.character(head) else if(is.call(head)&&as.character(head[[1]])%in%c('::',':::'))paste0(as.character(head[[2]]),as.character(head[[1]]),as.character(head[[3]])) else '<computed>'
        if(name%in%c('<-','=','<<-')&&length(e)>=3L&&is.symbol(e[[2]]))assignments<<-c(assignments,as.character(e[[2]]))
        if(grepl('(^mr$|^mr_|::mr_|^harmonise_data$|^clump_data$|^format_data$|^TEMR$|^library$|^require$|^source$)',name))calls[[length(calls)+1L]]<<-list(name=name,call=paste(deparse(e,width.cutoff=500L),collapse=' '))
        if(name=='source')sources[[length(sources)+1L]]<<-paste(deparse(e),collapse=' ')
        if(length(e)>1L)for(j in seq.int(2L,length(e)))walk(e[[j]])
    }
    if(!is.null(ast))walk(ast)
    data<-if(is.null(ast))NULL else getParseData(ast)
    tokens<-if(is.null(data))list() else lapply(which(data$token=='SYMBOL_FUNCTION_CALL'&grepl('^(mr$|mr_|harmonise_data|clump_data|format_data|TEMR)',data$text)),function(j)list(name=data$text[j],line=data$line1[j],column=data$col1[j]))
    rows[[i]]<-c(item,list(parse_success=is.null(error),parse_error=error,calls=calls,method_tokens=tokens,assigned_mr_names=as.list(unique(assignments[grepl('^(mr$|mr_|harmonise_data|clump_data|format_data)',assignments)])),dynamic_source_calls=sources))
}
writeLines(jsonlite::toJSON(list(status='STATIC_PARSE_ONLY_NO_AUTHOR_CODE_EXECUTED',files=rows,execution_commit_established=FALSE,new_catalog_credit=0),auto_unbox=TRUE,pretty=TRUE,null='null'),args[[2]],useBytes=TRUE)
"""
(O/'audit.R').write_text(code,encoding='utf-8',newline='\n');command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(O/'audit.R'),str(O/'INPUTS.json'),str(O/'RESULT.json')]
with (O/'stdout').open('xb') as stdout,(O/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=O,env=dict(os.environ,LANG='C',LC_ALL='C'),stdout=stdout,stderr=stderr,timeout=90)
(O/'PROCESS.json').write_text(json.dumps(dict(command=command,exit_code=p.returncode),indent=2)+'\n',encoding='utf-8',newline='\n');assert p.returncode==0
result=json.loads((O/'RESULT.json').read_bytes())
for x in result['files']:print(x['paper'],x['repository'],x['relative'],'parse',x['parse_success'],'calls',sorted({y['name'] for y in x['calls']}),'sources',len(x['dynamic_source_calls']),flush=True)
