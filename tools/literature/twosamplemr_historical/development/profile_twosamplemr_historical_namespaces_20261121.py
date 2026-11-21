from pathlib import Path
import json,os,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/mr-5';B=T/'build/twosamplemr-historical-1';O=B/'namespace-profiles-1';O.mkdir();I=R/'build/twosamplemr-historical-intake-20261121-1';runtime=R/'build/twosamplemr-historical-runtime-20261121-1';script=O/'profile.R'
script.write_text(r'''args<-commandArgs(TRUE);out<-args[[1]];source<-args[[2]];version<-args[[3]];lib<-args[[4]];extra<-args[[5]];baseline<-args[[6]]
.libPaths(c(lib,extra,baseline,.Library),include.site=FALSE);stopifnot(requireNamespace('jsonlite',quietly=TRUE),requireNamespace('digest',quietly=TRUE))
warnings<-messages<-character()
withCallingHandlers({stopifnot(requireNamespace('TwoSampleMR',lib.loc=lib,quietly=TRUE))},warning=function(w){warnings<<-c(warnings,conditionMessage(w));invokeRestart('muffleWarning')},message=function(m){messages<<-c(messages,conditionMessage(m));invokeRestart('muffleMessage')})
ns<-asNamespace('TwoSampleMR');desc<-utils::packageDescription('TwoSampleMR',lib.loc=lib);stopifnot(desc$Version==version)
definitions<-list();for(file in list.files(file.path(source,'R'),pattern='[.]R$',full.names=TRUE))for(e in parse(file,keep.source=FALSE))if(is.call(e)&&length(e)==3L&&as.character(e[[1]])%in%c('<-','=')&&is.symbol(e[[2]])&&is.call(e[[3]])&&identical(e[[3]][[1]],as.name('function')))definitions[[as.character(e[[2]])]]<-list(file=basename(file),expression=e[[3]])
fingerprint<-function(fun)digest::digest(serialize(list(formals=formals(fun),body=body(fun)),NULL,version=3),serialize=FALSE,algo='sha256')
guards<-list()
for(name in names(definitions)){
 original<-get(name,ns,inherits=FALSE);reference<-eval(definitions[[name]]$expression,envir=ns)
 if(!identical(body(original),body(reference))||!identical(formals(original),formals(reference)))stop(paste('Installed original body/formals differ:',name))
 guards[[name]]<-list(file=definitions[[name]]$file,sha256=fingerprint(reference))
}
method.list<-ns$mr_method_list();parameters<-ns$default_parameters();saveRDS(list(methods=method.list,parameters=parameters),'defaults.rds',version=3)
write.table(method.list,file=file.path(out,'METHODS.tsv'),sep='\t',row.names=FALSE,quote=FALSE,na='NA')
saveRDS(list(description=desc,R=R.version,session=sessionInfo(),external=extSoftVersion()),file.path(out,'runtime.rds'),version=3)
capture.output(sessionInfo(),file=file.path(out,'session.txt'));writeLines(warnings,file.path(out,'warnings.txt'));writeLines(messages,file.path(out,'messages.txt'))
functions<-lapply(definitions,function(row)as.list(names(row$expression[[2]])))
result<-list(status='PASS_ALL_SOURCE_FUNCTION_BODIES_FORMALS_EXACT',version=version,installed_namespace=find.package('TwoSampleMR',lib.loc=lib),R=as.character(getRversion()),source_functions=length(guards),guards=guards,formals=functions,methods=unname(split(method.list,seq_len(nrow(method.list)))),default_parameters=parameters,namespace_rewritten=FALSE,package_build_variant=if(version=='0.4.9')'private-documentation-only-no-man' else 'unmodified-source-install',package_execution_commit_from_installed_description=if(is.null(desc$RemoteSha))NULL else desc$RemoteSha)
writeLines(jsonlite::toJSON(result,pretty=TRUE,auto_unbox=TRUE,null='null',na='string',digits=17),file.path(out,'PROFILE.json'),useBytes=TRUE)
''',encoding='utf-8',newline='\n')
results={}
for version in ['0.4.9','0.5.6','0.5.7','0.6.8']:
    out=O/version;out.mkdir();lib=runtime/'versions'/('0.4.9-documentation-only-attempt-3' if version=='0.4.9' else version)/'library';command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(out),str(I/version/'source'),version,str(lib),str(runtime/'library'),str(R/'build/twosamplemr-runtime-intake-20261121-1/library')]
    (out/'COMMAND.json').write_text(json.dumps(command)+'\n',encoding='utf-8')
    with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=out,env=dict(os.environ,LANG='C',LC_ALL='C'),stdout=stdout,stderr=stderr,timeout=180)
    (out/'PROCESS.json').write_text(json.dumps(dict(exit_code=p.returncode))+'\n',encoding='utf-8');results[version]=dict(exit_code=p.returncode)
    if p.returncode==0:
        profile=json.loads((out/'PROFILE.json').read_bytes());results[version].update(source_functions=profile['source_functions'],methods=len(profile['methods']),default_parameters=profile['default_parameters']);print(version,results[version],flush=True)
    else:print(version,'FAIL', (out/'stderr').read_text(errors='replace')[-1200:],flush=True)
(O/'RESULT.json').write_text(json.dumps(dict(versions=results,all_pass=all(v['exit_code']==0 for v in results.values()),scientific_models_executed=False,new_catalog_credit=0,whole_goal_complete=False),indent=2)+'\n',encoding='utf-8',newline='\n')
